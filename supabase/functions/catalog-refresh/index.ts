// catalog-refresh — the daily Brickset -> catalog refresh (Architecture Decision 5). Design notes live in
// supabase/migrations/20260926120000_catalog_refresh.sql.
//
// Invoked by pg_cron through public.catalog_refresh_invoke(): POST with an `x-cron-secret` header. It answers
// 202 at once and works in the background; POST {"wait": true} runs inline and returns the summary instead
// (manual runs, local testing).
//
// One run:
//   1. take the lock (catalog_refresh_begin) — a second concurrent run just exits
//   2. Brickset getSets?updatedSince=<cursor - 1 day> (overlap is harmless: upserts are idempotent)
//   3. upsert through catalog_apply_brickset_sets, which also fills the minifig / enrichment queues
//   4. Rebrickable, best effort within a time budget: render pins for new shared-number sets, then the
//      minifig queue — anything unfinished stays queued for tomorrow
//   5. email the owner only when there are new sets / changed notes for the local enrichment pass
//   6. release the lock; the cursor advances only on success (a failure is re-covered next run) and a
//      failure sends at most one email a day
//
// Secrets (`supabase secrets set …`):
//   CATALOG_CRON_SECRET   required — must equal the Vault secret `catalog_refresh_secret`
//   BRICKSET_API_KEY      required — getSets works with a blank userHash, so no Brickset login is stored
//   REBRICKABLE_API_KEY   minifigs + render pins (skipped, with a warning, without it)
//   RESEND_API_KEY, CATALOG_REPORT_TO   emails; without them the email is logged instead (dry run)
//   CATALOG_REPORT_FROM   optional sender override
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are injected by the platform.

import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { type BricksetSet, chunk, normName, type SetRow, toSetRows, UNREVEALED_NAME } from "./transform.ts";
import { digestEmail, type Email, errorEmail, needsDigest, type ReportSet } from "./report.ts";

const BRICKSET_API = "https://brickset.com/api/v3.asmx";
const PAGE_SIZE = 500; // Brickset's maximum
const MAX_PAGES = 10; // 5,000 updated sets in a day is ~1,000x normal — stop and report instead
const APPLY_BATCH = 500;
const REBRICKABLE_GAP_MS = 800; // ~1 req/s, under Rebrickable's free-tier throttle
const REBRICKABLE_BUDGET_MS = 90_000; // headroom under the 150 s wall clock
const QUEUE_WINDOW_DAYS = 60; // give up on a set Rebrickable still doesn't list after this
const ERROR_EMAIL_GAP_MS = 20 * 60 * 60 * 1000;
const DEFAULT_FROM = "BrickWares Catalog <no-reply@mail.brickwares.app>";

const env = (k: string) => Deno.env.get(k) ?? "";
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const isoDay = (d: Date) => d.toISOString().slice(0, 10);
const addDays = (day: string, n: number) => isoDay(new Date(Date.parse(`${day}T00:00:00Z`) + n * 86_400_000));
const windowStart = () => new Date(Date.now() - QUEUE_WINDOW_DAYS * 86_400_000).toISOString();

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

function errText(e: unknown): string {
  if (e instanceof Error) return e.message;
  if (e && typeof e === "object" && "message" in e) return String((e as { message: unknown }).message);
  return String(e);
}

/** Constant-time string comparison for the shared secret. */
function safeEqual(a: string, b: string): boolean {
  const x = new TextEncoder().encode(a);
  const y = new TextEncoder().encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

interface LockRow {
  run_id: number;
  cursor_date: string;
  last_success_at: string | null;
  last_error_email_at: string | null;
}

interface ApplyResult {
  upserted: number;
  inserted: number[];
  notes_changed: number[];
  revealed: number[];
}

interface Summary {
  ok: boolean;
  skipped?: string;
  since?: string;
  fetched: number;
  upserted: number;
  inserted: number;
  placeholders: number;
  revealed: number;
  notesChanged: number;
  rendersPinned: number;
  minifigsFetched: number;
  minifigsPending: number;
  digest: string;
  errorEmail?: string;
  error?: string;
  warnings: string[];
}

const emptySummary = (): Summary => ({
  ok: false, fetched: 0, upserted: 0, inserted: 0, placeholders: 0, revealed: 0, notesChanged: 0,
  rendersPinned: 0, minifigsFetched: 0, minifigsPending: 0, digest: "none", warnings: [],
});

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method not allowed" }, 405);
  const secret = env("CATALOG_CRON_SECRET");
  if (!secret || !safeEqual(req.headers.get("x-cron-secret") ?? "", secret)) return json({ error: "unauthorized" }, 401);

  const body = await req.json().catch(() => ({}));
  if (body && typeof body === "object" && (body as { wait?: unknown }).wait === true) {
    try {
      return json(await run());
    } catch (e) {
      return json({ ok: false, error: errText(e) }, 500);
    }
  }
  EdgeRuntime.waitUntil(run().catch((e) => console.error("catalog-refresh: crashed —", errText(e))));
  return json({ accepted: true }, 202);
});

async function run(): Promise<Summary> {
  const db = createClient(env("SUPABASE_URL"), env("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const s = emptySummary();

  const { data: lockRows, error: lockErr } = await db.rpc("catalog_refresh_begin");
  if (lockErr) throw new Error(`catalog_refresh_begin: ${lockErr.message}`);
  const lock = (lockRows as LockRow[] | null)?.[0];
  if (!lock) {
    console.log("catalog-refresh: another run holds the lock — skipped");
    return { ...s, ok: true, skipped: "locked" };
  }

  const started = new Date();
  s.since = addDays(lock.cursor_date, -1);
  let emailedError = false;
  try {
    await refresh(db, s);
    s.ok = true;
  } catch (e) {
    s.error = errText(e);
    console.error("catalog-refresh: failed —", s.error);
    const lastEmail = lock.last_error_email_at ? Date.parse(lock.last_error_email_at) : 0;
    if (Date.now() - lastEmail > ERROR_EMAIL_GAP_MS) {
      s.errorEmail = await sendEmail(errorEmail({ when: started.toISOString(), error: s.error, lastSuccessAt: lock.last_success_at }));
      emailedError = s.errorEmail === "sent";
    }
  } finally {
    const { error } = await db.rpc("catalog_refresh_finish", {
      p_run_id: lock.run_id,
      p_ok: s.ok,
      p_cursor_date: s.ok ? isoDay(started) : null,
      p_summary: s,
      p_error: s.error ?? null,
      p_error_emailed: emailedError,
    });
    if (error) console.error("catalog-refresh: catalog_refresh_finish failed —", error.message);
  }
  console.log("catalog-refresh:", JSON.stringify(s));
  return s;
}

async function refresh(db: SupabaseClient, s: Summary): Promise<void> {
  const apiKey = env("BRICKSET_API_KEY");
  if (!apiKey) throw new Error("BRICKSET_API_KEY is not set");

  // 1-2. Pull + upsert. Any failure here is fatal for the run (the cursor stays put).
  const rows = toSetRows(await fetchUpdatedSets(apiKey, s.since!));
  s.fetched = rows.length;
  const inserted: number[] = [];
  const notesChanged: number[] = [];
  const revealed: number[] = [];
  for (const batch of chunk(rows, APPLY_BATCH)) {
    const { data, error } = await db.rpc("catalog_apply_brickset_sets", { p_sets: batch });
    if (error) throw new Error(`catalog_apply_brickset_sets: ${error.message}`);
    const r = data as ApplyResult;
    s.upserted += r.upserted;
    inserted.push(...r.inserted);
    notesChanged.push(...r.notes_changed);
    revealed.push(...r.revealed);
  }
  // New '{?}' placeholders are hidden in the app and have nothing to enrich — counted, not listed.
  const byId = new Map<number, SetRow>(rows.map((r) => [r.set_id, r]));
  const insertedNamed = inserted.filter((id) => byId.get(id)?.name !== UNREVEALED_NAME);
  s.inserted = insertedNamed.length;
  s.placeholders = inserted.length - insertedNamed.length;
  s.revealed = revealed.length;
  s.notesChanged = notesChanged.length;

  // 3. Rebrickable — best effort; the queues carry anything unfinished to the next run.
  const rbKey = env("REBRICKABLE_API_KEY");
  if (rbKey) {
    const rb = new Rebrickable(rbKey, Date.now() + REBRICKABLE_BUDGET_MS);
    try {
      s.rendersPinned = await pinRenders(db, rb);
    } catch (e) {
      s.warnings.push(`render pins: ${errText(e)}`);
    }
    try {
      const m = await drainMinifigs(db, rb);
      s.minifigsFetched = m.fetched;
      s.minifigsPending = m.pending;
    } catch (e) {
      s.warnings.push(`minifigs: ${errText(e)}`);
    }
  } else {
    s.warnings.push("REBRICKABLE_API_KEY not set — minifigs and render pins skipped");
  }

  // 4. Digest — only on days that leave the owner something to do. The backlog in the DB is the real
  //    worklist (the local pass reads it), so a failed send loses nothing.
  const report = (id: number): ReportSet => {
    const r = byId.get(id);
    return {
      set_id: id, set_number: r?.set_number ?? String(id), number_variant: r?.number_variant ?? 1,
      name: r?.name ?? null, theme: r?.theme ?? null, year: r?.year ?? null, notes: r?.notes ?? null,
    };
  };
  const found = {
    inserted: insertedNamed.map(report),
    revealed: revealed.map(report),
    notesChanged: notesChanged.map(report),
  };
  if (needsDigest(found)) {
    s.digest = await sendEmail(digestEmail({
      runDate: isoDay(new Date()),
      ...found,
      placeholders: s.placeholders,
      backlog: await countBacklog(db),
      upserted: s.upserted,
      minifigsFetched: s.minifigsFetched,
      minifigsPending: s.minifigsPending,
      rendersPinned: s.rendersPinned,
    }));
    if (s.digest.startsWith("failed")) s.warnings.push(`digest email ${s.digest}`);
  }
}

async function fetchUpdatedSets(apiKey: string, since: string): Promise<BricksetSet[]> {
  const out: BricksetSet[] = [];
  for (let page = 1; page <= MAX_PAGES; page++) {
    // extendedData is REQUIRED for notes — without it every set would read as "note removed".
    const params = JSON.stringify({ updatedSince: since, pageSize: PAGE_SIZE, pageNumber: page, extendedData: true });
    const res = await fetch(`${BRICKSET_API}/getSets`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ apiKey, userHash: "", params }),
    });
    if (!res.ok) throw new Error(`Brickset getSets HTTP ${res.status}`);
    const j = await res.json();
    if (j?.status !== "success") throw new Error(`Brickset getSets: ${j?.message ?? j?.status ?? "no status"}`);
    const sets = (j.sets ?? []) as BricksetSet[];
    out.push(...sets);
    if (sets.length < PAGE_SIZE) return out;
  }
  throw new Error(
    `Brickset reports more than ${MAX_PAGES * PAGE_SIZE} sets updated since ${since} — nothing applied; run the local full ingest`,
  );
}

/** Paced Rebrickable GET within a shared time budget. 404 -> null; 429 backs off (honouring Retry-After). */
class Rebrickable {
  private next = 0;
  private readonly key: string;
  private readonly deadline: number;

  constructor(key: string, deadline: number) {
    this.key = key;
    this.deadline = deadline;
  }

  hasTime(): boolean {
    return Math.max(Date.now(), this.next) + REBRICKABLE_GAP_MS < this.deadline;
  }

  async get(path: string): Promise<any | null> {
    for (let attempt = 0; ; attempt++) {
      const wait = this.next - Date.now();
      if (wait > 0) await sleep(wait);
      this.next = Date.now() + REBRICKABLE_GAP_MS;
      const res = await fetch(`https://rebrickable.com/api/v3/lego/${path}`, {
        headers: { Authorization: `key ${this.key}`, Accept: "application/json" },
      });
      if (res.status === 404) {
        await res.body?.cancel();
        return null;
      }
      if (res.status === 429 && attempt < 3) {
        await res.body?.cancel();
        const retryAfter = Number(res.headers.get("retry-after"));
        const backoff = retryAfter > 0 ? retryAfter * 1000 : 2000 * 2 ** attempt;
        if (Date.now() + backoff > this.deadline) throw new Error("rate limited and out of time");
        this.next = Date.now() + backoff;
        continue;
      }
      if (!res.ok) {
        await res.body?.cancel();
        throw new Error(`Rebrickable ${path} HTTP ${res.status}`);
      }
      return await res.json();
    }
  }
}

/**
 * Authoritative render URLs for NEW sets that share a number with another row (a new CMF series, promo
 * sub-models): the app's number+variant guess can grab a different set's image when Rebrickable indexes
 * the variants differently, so match by name as the preload does. Only groups that gained a set in the
 * queue window and still lack a pin are looked up — one call per group, retried daily until it matches.
 */
async function pinRenders(db: SupabaseClient, rb: Rebrickable): Promise<number> {
  const { data: recent, error } = await db.from("sets").select("set_number")
    .is("render_url", null).gt("first_seen_at", windowStart()).limit(1000);
  if (error) throw new Error(error.message);
  const numbers = [...new Set((recent ?? []).map((r: { set_number: string }) => r.set_number))];
  if (!numbers.length) return 0;

  type Row = { set_id: number; set_number: string; name: string | null; render_url: string | null };
  const groups = new Map<string, Row[]>();
  for (const part of chunk(numbers, 100)) {
    const { data, error: e } = await db.from("sets").select("set_id,set_number,name,render_url").in("set_number", part);
    if (e) throw new Error(e.message);
    for (const r of (data ?? []) as Row[]) groups.set(r.set_number, [...(groups.get(r.set_number) ?? []), r]);
  }

  let pinned = 0;
  for (const [number, rows] of groups) {
    if (rows.length < 2 || rows.every((r) => r.render_url)) continue; // single-variant: reconstruction is right
    if (!rb.hasTime()) break;
    let results: { set_num?: string; name?: string; set_img_url?: string }[] = [];
    try {
      const j = await rb.get(`sets/?search=${encodeURIComponent(number)}&page_size=100`);
      results = j?.results ?? [];
    } catch (e) {
      console.warn(`catalog-refresh: render search ${number} — ${errText(e)}`);
      continue;
    }
    const urlByName = new Map<string, string>();
    for (const r of results) {
      if (String(r.set_num ?? "").startsWith(`${number}-`) && r.set_img_url) urlByName.set(normName(r.name), r.set_img_url);
    }
    for (const row of rows) {
      const url = row.render_url ? undefined : urlByName.get(normName(row.name));
      if (!url) continue;
      const { error: e } = await db.from("sets").update({ render_url: url }).eq("set_id", row.set_id).is("render_url", null);
      if (e) console.warn(`catalog-refresh: pin ${row.set_id} — ${e.message}`);
      else pinned++;
    }
  }
  return pinned;
}

/** Works the minifig queue oldest-first. A set Rebrickable doesn't list yet simply stays queued. */
async function drainMinifigs(db: SupabaseClient, rb: Rebrickable): Promise<{ fetched: number; pending: number }> {
  const { data: queue, error } = await db.from("sets").select("set_id,set_number,number_variant")
    .gt("minifigs", 0).gt("minifigs_queued_at", windowStart())
    .order("minifigs_queued_at", { ascending: true }).limit(200);
  if (error) throw new Error(error.message);

  let fetched = 0;
  for (const q of (queue ?? []) as { set_id: number; set_number: string; number_variant: number | null }[]) {
    if (!rb.hasTime()) break;
    let results: { set_num?: string; set_name?: string; set_img_url?: string; quantity?: number }[] = [];
    try {
      const j = await rb.get(`sets/${encodeURIComponent(`${q.set_number}-${q.number_variant ?? 1}`)}/minifigs/?page_size=100`);
      results = j?.results ?? [];
    } catch (e) {
      console.warn(`catalog-refresh: minifigs ${q.set_number} — ${errText(e)}`);
      continue;
    }
    const figs = results.filter((r) => r.set_num).map((r) => ({
      fig_num: r.set_num, name: r.set_name ?? null, image_url: r.set_img_url ?? null, quantity: r.quantity ?? 1,
    }));
    if (!figs.length) continue;
    const { error: e } = await db.rpc("catalog_apply_set_minifigs", { p_set_id: q.set_id, p_figs: figs });
    if (e) console.warn(`catalog-refresh: apply minifigs ${q.set_id} — ${e.message}`);
    else fetched++;
  }
  return { fetched, pending: (queue?.length ?? 0) - fetched };
}

async function countBacklog(db: SupabaseClient): Promise<number> {
  const { count, error } = await db.from("sets").select("set_id", { count: "exact", head: true })
    .not("enrich_queued_at", "is", null);
  if (error) throw new Error(error.message);
  return count ?? 0;
}

/** Sends via Resend; without a key/recipient it logs the email instead (dry run). Never throws. */
async function sendEmail(mail: Email): Promise<string> {
  const key = env("RESEND_API_KEY");
  const to = env("CATALOG_REPORT_TO");
  if (!key || !to) {
    console.log(`catalog-refresh: [dry run — email not configured]\nSubject: ${mail.subject}\n\n${mail.text}`);
    return "dry-run";
  }
  try {
    const res = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
      body: JSON.stringify({ from: env("CATALOG_REPORT_FROM") || DEFAULT_FROM, to: [to], subject: mail.subject, text: mail.text }),
    });
    const detail = (await res.text()).slice(0, 200);
    return res.ok ? "sent" : `failed: HTTP ${res.status} ${detail}`;
  } catch (e) {
    return `failed: ${errText(e)}`;
  }
}
