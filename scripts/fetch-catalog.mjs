// Fetches a SAMPLE of the LEGO catalog from Brickset and writes supabase/seed.sql
// (idempotent upserts into public.sets + public.set_prices). This is a prototype of
// the future monthly ingestion Edge Function (Architecture Decision 5).
//
// Run:  node --env-file=supabase/.env.local scripts/fetch-catalog.mjs
// Then: supabase db reset      (applies migrations + this seed)
//
// Keys come from env ONLY (see supabase/.env.example). Never hard-code them.

import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { createHash, createHmac } from "node:crypto";

const API = "https://brickset.com/api/v3.asmx";
const OUT = resolve(dirname(fileURLToPath(import.meta.url)), "../supabase/seed.sql");

const {
  BRICKSET_API_KEY,
  BRICKSET_USER_HASH,
  BRICKSET_USERNAME,
  BRICKSET_PASSWORD,
  // Rebrickable — source for a set's minifig list (populates the minifigs + set_minifigs tables).
  // Optional: if unset, the seed is generated without minifigs.
  REBRICKABLE_API_KEY,
  BRICKSET_THEMES = "Architecture,Batman,Icons,Technic,Ninjago,Star Wars",
  BRICKSET_PAGESIZE = "20",
  // Optional Brickset year filter, comma-separated (e.g. "2024,2025,2026"). Empty = all years.
  BRICKSET_YEAR = "",
  // Optional subtheme filter (e.g. "Batman" within the "DC Comics Super Heroes" theme). Empty = all.
  BRICKSET_SUBTHEME = "",
  // "1"/"true" = merge onto the existing seed.sql (dedupe by set_id, newly fetched rows win)
  // instead of overwriting it, so a fetch adds to the current catalog rather than replacing it.
  BRICKSET_APPEND = "",
  // Themes pulled COMPLETELY — every set across all pages, any availability (retired included), not
  // just the newest few. Use to bring a whole theme into the catalog (e.g. all DC Comics Super
  // Heroes sets). Runs on every fetch, so these themes stay fully covered "from now on".
  BRICKSET_FULL_THEMES = "DC Comics Super Heroes",
  BRICKSET_FULL_PAGESIZE = "100",
  // "1" = also pull Rebrickable minifigs for the full-theme sets. A whole theme is many sets, so the
  // minifig fetch is slow/rate-limited — off by default (full-theme sets are catalog-only); the
  // primary newest-N pass always fetches minifigs.
  BRICKSET_FULL_MINIFIGS = "0",
  // Translate-at-ingest: machine-translate each set's English `notes` to Vietnamese (stored in
  // sets.notes_vi, shown on the detail page when the app language is VI). "0" disables. Uses the free
  // MyMemory API (no key); TRANSLATE_EMAIL is optional and raises its daily quota.
  TRANSLATE_NOTES = "1",
  TRANSLATE_EMAIL = "",
  // Extra pass: also pull OLD RETIRED sets (for testing the retired badge/value flows). These go into
  // the sets + set_prices catalog but are NOT sent through the Rebrickable minifig fetch. "0" disables.
  BRICKSET_RETIRED = "1",
  BRICKSET_RETIRED_YEARS = "2014,2015,2016,2017,2018,2019,2020",
  BRICKSET_RETIRED_THEMES = "Star Wars,Technic,City,Ninjago,Creator Expert,Architecture,Ideas,Marvel Super Heroes,Harry Potter,Speed Champions",
  BRICKSET_RETIRED_PAGESIZE = "40",
  BRICKSET_RETIRED_MAX = "200",
  // "Full picture" coverage — fetch a small SAMPLE (BRICKSET_ALL_THEMES_SAMPLE, default 1) of sets for
  // EVERY Brickset theme (via getThemes), so all ~173 themes appear in the theme browse. Runs on every
  // fetch so new themes stay covered; catalog-only (no minifig fetch). "0" disables.
  BRICKSET_ALL_THEMES = "1",
  BRICKSET_ALL_THEMES_SAMPLE = "1",
  // Fast focused mode: fetch ONLY the theme samples and merge them into the existing seed (skips the
  // primary/full/retired/minifig/translate passes). Implies append — fills in missing themes without
  // re-running the whole ingest.
  BRICKSET_SAMPLES_ONLY = "",
  // "1" = pull the ENTIRE catalog: full-pull EVERY Brickset theme (all pages), not just the curated
  // sample — the prod set-preload. Skips the primary/retired/theme-sample passes (subsumed) and implies
  // append (so the existing box_image_url + notes_vi blocks are preserved). Minifigs stay gated by
  // BRICKSET_FULL_MINIFIGS (off = sets only, ~minutes; on = every set's minifigs, ~hours). seed.sql is
  // gitignored, so the big file stays local — apply it to prod with psql, don't commit it.
  BRICKSET_FULL_ALL = "",
  // ---- Box image re-hosting (see rehostBoxImages) ----
  // "1" = download each set's BrickLink box once and upload it to a Cloudflare R2 bucket (free egress),
  // writing the public URL to sets.box_image_url. Off by default — a normal ingest skips this pass.
  REHOST_BOX_IMAGES = "",
  // Cloudflare R2 (S3-compatible). Account id + an R2 API token's Access Key ID / Secret (R2 → Manage
  // API Tokens). The secret stays in the gitignored .env only. R2_BUCKET is the bucket name; R2_PUBLIC_
  // BASE is the public read origin (a custom domain like https://img.brickwares.app, or the bucket's
  // r2.dev URL) — this is what gets stored in box_image_url and served to the app.
  R2_ACCOUNT_ID = "",
  R2_ACCESS_KEY_ID = "",
  R2_SECRET_ACCESS_KEY = "",
  R2_BUCKET = "set-box-images",
  R2_PUBLIC_BASE = "",
  // Delay (ms) between BrickLink downloads. Deliberately generous — BrickLink rate-limits img.bricklink
  // .com per IP and 403-bans a busy one. Raise it if you still see 403s.
  BOX_FETCH_DELAY_MS = "3000",
  // Cap NEW downloads per run (empty = no cap). Run in batches across sessions to stay well under the
  // limit and build coverage gradually — already-hosted boxes are skipped without touching BrickLink.
  BOX_MAX = "",
  // "1" = ONLY re-host boxes for the sets already in seed.sql, then rewrite just the box_image_url
  // block — no Brickset/Rebrickable fetch, no other blocks touched. The right way to build box coverage
  // for the whole existing catalog without a slow full re-fetch (and without risking losing sets).
  BOX_ONLY = "",
} = process.env;

const SAMPLES_ONLY = BRICKSET_SAMPLES_ONLY === "1" || BRICKSET_SAMPLES_ONLY.toLowerCase() === "true";
const FULL_ALL = BRICKSET_FULL_ALL === "1" || BRICKSET_FULL_ALL.toLowerCase() === "true";
// FULL_ALL implies append so the whole-catalog pull merges onto (not wipes) the existing seed — keeping
// the already-harvested box_image_url + notes_vi blocks.
const APPEND = SAMPLES_ONLY || FULL_ALL || BRICKSET_APPEND === "1" || BRICKSET_APPEND.toLowerCase() === "true";

if (!BRICKSET_API_KEY) {
  console.error("Missing BRICKSET_API_KEY — put it in supabase/.env.local (see .env.example).");
  process.exit(1);
}

async function post(path, params) {
  const res = await fetch(`${API}/${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams(params),
  });
  if (!res.ok) throw new Error(`${path} HTTP ${res.status}`);
  return res.json();
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function rbGet(path, attempt = 0) {
  const res = await fetch(`https://rebrickable.com/api/v3/lego/${path}`, {
    headers: { Authorization: `key ${REBRICKABLE_API_KEY}`, Accept: "application/json" },
  });
  if (res.status === 404) return null;
  // Rebrickable throttles the free tier — back off (honoring Retry-After) and retry on 429.
  if (res.status === 429 && attempt < 5) {
    const retryAfter = Number(res.headers.get("retry-after"));
    const wait = retryAfter > 0 ? retryAfter * 1000 : 1000 * 2 ** attempt; // 1s, 2s, 4s, 8s, 16s
    console.error(`  rate limited — waiting ${wait}ms then retrying...`);
    await sleep(wait);
    return rbGet(path, attempt + 1);
  }
  if (!res.ok) throw new Error(`Rebrickable ${path} HTTP ${res.status}`);
  return res.json();
}

// For each fetched set that has minifigs, pull its fig list from Rebrickable → dedup into the
// `minifigs` catalog + the `set_minifigs` join. Rebrickable set numbers are "{number}-{variant}".
// (In the set→minifigs response Rebrickable returns each fig's id under `set_num` and its name
// under `set_name`.) num_parts is left null here (would need a per-fig call); not needed for browse.
async function fetchMinifigs(sets) {
  const figs = new Map();       // fig_num -> { name, image_url }
  const setFigs = [];           // { setId, figNum, quantity }
  const seen = new Set();       // "setId|figNum" — the (set_id, fig_num) PK dedup
  const withFigs = sets.filter((s) => (s.minifigs || 0) > 0);
  console.log(`Fetching minifigs for ${withFigs.length} sets from Rebrickable...`);
  let ok = 0;
  for (const s of withFigs) {
    const setNum = `${s.number}-${variantOf(s)}`;
    let j = null;
    try { j = await rbGet(`sets/${setNum}/minifigs/?page_size=100`); }
    catch (e) { console.error(`  ${setNum}: ${e.message}`); }
    if (j?.results?.length) {
      ok++;
      for (const r of j.results) {
        const figNum = r.set_num;
        if (!figNum) continue;
        if (!figs.has(figNum)) figs.set(figNum, { name: r.set_name, image_url: r.set_img_url });
        const key = `${s.setID}|${figNum}`;
        if (!seen.has(key)) { seen.add(key); setFigs.push({ setId: s.setID, figNum, quantity: r.quantity || 1 }); }
      }
    }
    await sleep(800); // ~1 req/s — stay under Rebrickable's free-tier throttle (429s otherwise)
  }
  console.log(`  ${ok}/${withFigs.length} sets had minifig data -> ${figs.size} unique figs, ${setFigs.length} links.`);
  return { figs, setFigs };
}

async function getUserHash() {
  if (BRICKSET_USER_HASH) return BRICKSET_USER_HASH;
  if (!BRICKSET_USERNAME || !BRICKSET_PASSWORD) {
    console.error("Provide BRICKSET_USER_HASH, or BRICKSET_USERNAME + BRICKSET_PASSWORD to log in.");
    process.exit(1);
  }
  const j = await post("login", {
    apiKey: BRICKSET_API_KEY,
    username: BRICKSET_USERNAME,
    password: BRICKSET_PASSWORD,
  });
  if (j.status !== "success") {
    console.error("Brickset login failed:", j.message);
    process.exit(1);
  }
  return j.hash;
}

// --- SQL literal helpers (escape single quotes; map empty -> null) ---
const q = (v) => (v === null || v === undefined || v === "" ? "null" : `'${String(v).replace(/'/g, "''")}'`);
const num = (v) => (v === null || v === undefined || v === "" || Number.isNaN(Number(v)) ? "null" : Number(v));
const bool = (v) => (v === true ? "true" : v === false ? "false" : "null");
const date = (v) => (v ? `'${String(v).slice(0, 10)}'` : "null"); // ISO datetime -> YYYY-MM-DD

// Brickset's numberVariant, preserving a real 0 (a series "random pack" is e.g. 42233-0), defaulting
// to 1 only when it's genuinely missing. A plain `x || 1` coerced variant 0 into 1, colliding with the
// real variant 1 (same number_variant) — two rows then share id "42233-1" and crash the app's list.
function variantOf(s) {
  if (s.numberVariant === null || s.numberVariant === undefined || s.numberVariant === "") return 1;
  const n = Number(s.numberVariant);
  return Number.isInteger(n) ? n : 1;
}

function setRow(s) {
  return `(${num(s.setID)}, ${q(s.number)}, ${variantOf(s)}, ${q(s.name)}, ${num(s.year)}, ` +
    `${q(s.theme)}, ${q(s.themeGroup)}, ${q(s.subtheme)}, ${q(s.category)}, 'set', ${num(s.pieces)}, ` +
    `${num(s.minifigs)}, ${num(s.ageRange?.min)}, ${bool(s.released)}, ${q(s.availability)}, ${q(s.image?.imageURL)}, ` +
    `${q(s.image?.thumbnailURL)}, ${q(s.bricksetURL)}, ${num(s.rating)}, ${num(s.reviewCount)}, ` +
    `${q(s.extendedData?.notes)}, ${date(s.launchDate)}, ${date(s.exitDate)})`;
}

function priceRows(s) {
  const rows = [];
  const lego = s.LEGOCom || {};
  for (const region of ["US", "UK", "CA", "DE"]) {
    const r = lego[region];
    if (r && (r.retailPrice != null || r.dateFirstAvailable || r.dateLastAvailable)) {
      rows.push(`(${num(s.setID)}, '${region}', ${num(r.retailPrice)}, ${date(r.dateFirstAvailable)}, ${date(r.dateLastAvailable)})`);
    }
  }
  return rows;
}

// Pull the comma+newline-separated "(...)" value rows out of an existing generated block.
function extractRows(sql, blockRe) {
  const m = sql.match(blockRe);
  if (!m) return [];
  return m[1].split(/,\n {2}/).map((r) => r.trim().replace(/,\s*$/, "")).filter(Boolean);
}

// ---- Box image re-hosting -------------------------------------------------------------------------
// BrickLink has each set's box packaging photo at a deterministic URL, but RATE-LIMITS img.bricklink
// .com per IP: a burst of requests earns a 403 ban for that IP (the whole reason we can't hotlink it
// live to users). This pass downloads each box ONCE and uploads it to a Cloudflare R2 bucket (free
// egress) — after which the app loads box art from our CDN (R2_PUBLIC_BASE), never BrickLink.
//
// To avoid getting the INGEST machine banned it is deliberately gentle:
//   • a generous, jittered delay before EVERY BrickLink request (BOX_FETCH_DELAY_MS, default 3s),
//   • it BACKS OFF 60s on a 403 and STOPS entirely after 3 in a row (never hammers a limiter),
//   • it is idempotent — a set already hosted on R2 (its box_image_url already points at R2_PUBLIC_BASE)
//     is skipped WITHOUT touching BrickLink; a leftover URL from another host is re-hosted to R2, so
//     switching hosts migrates automatically. Use BOX_MAX to build coverage in small batches.
const REHOST = REHOST_BOX_IMAGES === "1" || REHOST_BOX_IMAGES.toLowerCase() === "true";
const R2_HOST = `${R2_ACCOUNT_ID}.r2.cloudflarestorage.com`;
const R2_BASE = R2_PUBLIC_BASE.replace(/\/$/, "");
const boxPath = (number, variant) => `sets/${String(number).toLowerCase()}-${variant}.png`;
const boxSourceUrl = (number, variant) => `https://img.bricklink.com/ItemImage/ON/0/${String(number).toLowerCase()}-${variant}.png`;
const publicBoxUrl = (number, variant) => `${R2_BASE}/${boxPath(number, variant)}`;

// Minimal AWS SigV4 (S3) for R2 PutObject — no SDK, just node:crypto.
const sha256hex = (data) => createHash("sha256").update(data).digest("hex");
const hmac = (key, data) => createHmac("sha256", key).update(data).digest();
const signingKey = (secret, day, region, svc) => hmac(hmac(hmac(hmac(`AWS4${secret}`, day), region), svc), "aws4_request");

async function r2Put(key, body, contentType) {
  const region = "auto", svc = "s3";
  const amzDate = new Date().toISOString().replace(/[:-]|\.\d{3}/g, ""); // YYYYMMDDTHHMMSSZ
  const day = amzDate.slice(0, 8);
  const payloadHash = sha256hex(body);
  const uri = "/" + [R2_BUCKET, ...key.split("/")].map(encodeURIComponent).join("/");
  const canonicalHeaders = `host:${R2_HOST}\nx-amz-content-sha256:${payloadHash}\nx-amz-date:${amzDate}\n`;
  const signedHeaders = "host;x-amz-content-sha256;x-amz-date";
  const canonicalRequest = ["PUT", uri, "", canonicalHeaders, signedHeaders, payloadHash].join("\n");
  const scope = `${day}/${region}/${svc}/aws4_request`;
  const stringToSign = ["AWS4-HMAC-SHA256", amzDate, scope, sha256hex(canonicalRequest)].join("\n");
  const signature = createHmac("sha256", signingKey(R2_SECRET_ACCESS_KEY, day, region, svc)).update(stringToSign).digest("hex");
  const auth = `AWS4-HMAC-SHA256 Credential=${R2_ACCESS_KEY_ID}/${scope}, SignedHeaders=${signedHeaders}, Signature=${signature}`;
  return fetch(`https://${R2_HOST}${uri}`, {
    method: "PUT",
    headers: { Authorization: auth, "x-amz-date": amzDate, "x-amz-content-sha256": payloadHash, "Content-Type": contentType },
    body,
  });
}

// Returns Map<setID, publicBoxUrl> = the boxes we now have on R2 (carrying forward [existingBySetId],
// re-hosting any not-yet-on-R2 URL). No-op unless enabled + configured.
async function rehostBoxImages(sets, existingBySetId = new Map()) {
  const byId = new Map(existingBySetId);
  if (!REHOST) return byId;
  if (!R2_ACCOUNT_ID || !R2_ACCESS_KEY_ID || !R2_SECRET_ACCESS_KEY || !R2_BASE) {
    console.warn("REHOST_BOX_IMAGES set but R2_ACCOUNT_ID / R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY / R2_PUBLIC_BASE missing — skipping box re-host.");
    return byId;
  }
  const delay = Number(BOX_FETCH_DELAY_MS) || 3000;
  const maxNew = BOX_MAX ? Number(BOX_MAX) : Infinity;
  const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
  console.log(`Box re-host -> R2 bucket "${R2_BUCKET}"; new downloads ${maxNew === Infinity ? "uncapped" : `capped at ${maxNew}`}, ~${delay}ms apart.`);

  let got = 0, skipped = 0, missing = 0, streak403 = 0;
  for (const s of sets) {
    const number = s.number;
    if (!number) continue;
    const variant = variantOf(s);
    const already = existingBySetId.get(s.setID);
    if (already && already.startsWith(`${R2_BASE}/`)) { skipped++; continue; } // already on R2 → no BrickLink hit
    if (got >= maxNew) continue; // batch cap reached — leave the rest for the next run

    await sleep(delay + Math.floor(Math.random() * 1000)); // jittered throttle BEFORE every request
    let res;
    try {
      res = await fetch(boxSourceUrl(number, variant), { headers: { "User-Agent": UA, Referer: "https://www.bricklink.com/" } });
    } catch (e) { console.warn(`  ${number}-${variant}: fetch error ${e.message}`); continue; }

    if (res.status === 403) {
      streak403++;
      console.warn(`  ${number}-${variant}: 403 rate-limited — backing off 60s (${streak403}/3).`);
      if (streak403 >= 3) { console.error("  BrickLink keeps 403'ing — STOPPING the box re-host to avoid a hard ban. Re-run later to continue where it left off."); break; }
      await sleep(60_000);
      continue; // this set is retried on the next run (still not on R2)
    }
    streak403 = 0;
    if (res.status === 404) { missing++; continue; } // no box on BrickLink → leave as-is (render fallback)
    if (!res.ok) { console.warn(`  ${number}-${variant}: HTTP ${res.status}`); continue; }

    const bytes = Buffer.from(await res.arrayBuffer());
    if (bytes.length < 1000) { missing++; continue; } // a placeholder / error body, not a real image
    try {
      const up = await r2Put(boxPath(number, variant), bytes, "image/png");
      if (!up.ok) { console.warn(`  ${number}-${variant}: R2 upload HTTP ${up.status}: ${(await up.text()).slice(0, 140)}`); continue; }
      byId.set(s.setID, publicBoxUrl(number, variant));
      if (++got % 20 === 0) console.log(`  ...${got} boxes uploaded`);
    } catch (e) { console.warn(`  ${number}-${variant}: ${e.message}`); }
  }
  console.log(`Box re-host: ${got} downloaded+uploaded, ${skipped} already on R2, ${missing} with no box on BrickLink.`);
  return byId;
}

// Merge existing + fresh row strings, keyed by keyOf. Existing order is preserved; a fresh row
// with the same key overwrites the existing one, and brand-new fresh rows are appended.
function mergeRows(existingRows, freshRows, keyOf) {
  const map = new Map();
  for (const r of existingRows) map.set(keyOf(r) ?? r, r);
  for (const r of freshRows) map.set(keyOf(r) ?? r, r);
  return [...map.values()];
}

// A set counts as "retired" the way the app derives it: its exit date (or latest LEGO.com
// date-last-available) is in the past. Promotional / magazine gifts are excluded — the app never
// marks those RETIRED, so they wouldn't exercise the retired flows.
function retirementDate(s) {
  if (s.exitDate) return s.exitDate.slice(0, 10);
  const lego = s.LEGOCom || {};
  const dates = ["US", "UK", "CA", "DE"].map((r) => lego[r]?.dateLastAvailable).filter(Boolean).map((d) => d.slice(0, 10));
  return dates.length ? dates.sort().pop() : null;
}
function isRetired(s, today) {
  const av = (s.availability || "").toLowerCase();
  if (av === "promotional" || av === "magazine gift") return false;
  const rd = retirementDate(s);
  return rd != null && rd < today;
}

// Extra pass — old retired sets (across BRICKSET_RETIRED_THEMES × BRICKSET_RETIRED_YEARS), for testing
// the retired badge + retired price-column/value flows. Deduped and capped at BRICKSET_RETIRED_MAX.
async function fetchRetiredSets(userHash) {
  const today = new Date().toISOString().slice(0, 10);
  const themes = BRICKSET_RETIRED_THEMES.split(",").map((t) => t.trim()).filter(Boolean);
  const pageSize = Number(BRICKSET_RETIRED_PAGESIZE);
  console.log(`Fetching retired test sets (years ${BRICKSET_RETIRED_YEARS}) for: ${themes.join(", ")}`);
  const out = [];
  for (const theme of themes) {
    const params = JSON.stringify({ theme, year: BRICKSET_RETIRED_YEARS, pageSize, orderBy: "YearFromDESC", extendedData: true });
    const j = await post("getSets", { apiKey: BRICKSET_API_KEY, userHash, params });
    if (j.status !== "success") { console.error(`  retired ${theme}: ${j.message}`); continue; }
    const retired = (j.sets || []).filter((s) => isRetired(s, today));
    console.log(`  ${theme}: ${j.sets?.length || 0} fetched, ${retired.length} retired`);
    out.push(...retired);
  }
  const unique = [...new Map(out.map((s) => [s.setID, s])).values()];
  return unique.slice(0, Number(BRICKSET_RETIRED_MAX));
}

// Full-theme pass — pull EVERY set in each BRICKSET_FULL_THEMES theme (all pages, any availability,
// retired included), so an entire theme lands in the catalog rather than just its newest page.
async function fetchFullThemes(userHash) {
  // FULL_ALL: full-pull EVERY Brickset theme (whole catalog) via getThemes; else just BRICKSET_FULL_THEMES.
  let themes;
  if (FULL_ALL) {
    const tj = await post("getThemes", { apiKey: BRICKSET_API_KEY });
    themes = tj.status === "success" ? (tj.themes || []).map((t) => t.theme).filter(Boolean) : [];
  } else {
    themes = BRICKSET_FULL_THEMES.split(",").map((t) => t.trim()).filter(Boolean);
  }
  if (themes.length === 0) return [];
  const pageSize = Number(BRICKSET_FULL_PAGESIZE);
  console.log(`Fetching ALL sets (every page) for: ${FULL_ALL ? `all ${themes.length} themes` : themes.join(", ")}`);
  const out = [];
  for (const theme of themes) {
    let total = 0;
    for (let page = 1; page <= 200; page++) {
      const params = JSON.stringify({ theme, pageSize, pageNumber: page, orderBy: "YearFromDESC", extendedData: true });
      const j = await post("getSets", { apiKey: BRICKSET_API_KEY, userHash, params });
      if (j.status !== "success") { console.error(`  full ${theme} p${page}: ${j.message}`); break; }
      const batch = j.sets || [];
      out.push(...batch);
      total += batch.length;
      if (batch.length < pageSize) break; // last page
    }
    console.log(`  ${theme}: ${total} sets (all pages)`);
    if (FULL_ALL) await sleep(150); // gentle pacing across all ~173 themes
  }
  return out;
}

// Full-picture pass — fetch a small sample (sampleSize) of sets for EVERY Brickset theme (getThemes),
// so every theme (~173) has at least one set and shows up in the theme browse. Catalog-only (deduped
// downstream by setID, and a richer pass wins the tie). getThemes needs only the apiKey (public data).
async function fetchThemeSamples(userHash, sampleSize) {
  const tj = await post("getThemes", { apiKey: BRICKSET_API_KEY });
  if (tj.status !== "success") { console.error(`  getThemes: ${tj.message}`); return []; }
  const allThemes = (tj.themes || []).map((t) => t.theme).filter(Boolean);
  console.log(`Fetching ${sampleSize} sample set(s) for each of ${allThemes.length} themes (full-picture coverage)...`);
  const out = [];
  for (const theme of allThemes) {
    const params = JSON.stringify({ theme, pageSize: sampleSize, orderBy: "YearFromDESC", extendedData: true });
    const j = await post("getSets", { apiKey: BRICKSET_API_KEY, userHash, params });
    if (j.status !== "success") { console.error(`  sample ${theme}: ${j.message}`); continue; }
    out.push(...(j.sets || []));
    await sleep(120); // gentle pacing across ~173 themes
  }
  console.log(`  theme samples: ${out.length} sets across ${allThemes.length} themes.`);
  return out;
}

// Translate-at-ingest: machine-translate English set notes to Vietnamese via MyMemory (free, no key).
// Deduped (each distinct note once); failures/quota are skipped and the app falls back to English.
// Returns a Map of englishNote -> vietnameseNote.
async function translateNotes(notes) {
  const out = new Map();
  const enabled = TRANSLATE_NOTES !== "0" && TRANSLATE_NOTES.toLowerCase() !== "false";
  if (!enabled) return out;
  const unique = [...new Set(notes.map((n) => (n || "").trim()).filter(Boolean))];
  if (unique.length === 0) return out;
  console.log(`Translating ${unique.length} unique notes -> vi (MyMemory)...`);
  let ok = 0;
  for (const n of unique) {
    const params = new URLSearchParams({ q: n, langpair: "en|vi" });
    if (TRANSLATE_EMAIL) params.set("de", TRANSLATE_EMAIL);
    try {
      const r = await fetch(`https://api.mymemory.translated.net/get?${params}`);
      const j = await r.json();
      const vi = j?.responseData?.translatedText;
      if (r.ok && Number(j.responseStatus) === 200 && vi && vi.trim() && vi !== n) {
        out.set(n, vi.trim());
        ok++;
      } else {
        console.error(`  note translate skipped (${j?.responseStatus}): ${n.slice(0, 40)}...`);
      }
    } catch (e) {
      console.error(`  note translate error: ${e.message}`);
    }
    await sleep(300); // be gentle with the free endpoint
  }
  console.log(`  translated ${ok}/${unique.length} notes.`);
  return out;
}

// BOX_ONLY: re-host boxes for every set already in seed.sql (no Brickset/Rebrickable fetch), then
// splice just the box_image_url UPDATE block back in — leaving all other blocks untouched.
async function boxOnlyRun() {
  const existing = await readFile(OUT, "utf8").catch(() => null);
  if (!existing) { console.error(`BOX_ONLY: ${OUT} not found — generate the seed first.`); process.exit(1); }
  const rows = extractRows(existing, /insert into public\.sets[\s\S]*?\nvalues\n {2}([\s\S]*?)\non conflict \(set_id\)/);
  const sets = rows
    .map((r) => r.match(/^\(\s*(\d+)\s*,\s*'((?:[^']|'')*)'\s*,\s*(\d+)/))
    .filter(Boolean)
    .map((m) => ({ setID: Number(m[1]), number: m[2].replace(/''/g, "'"), numberVariant: Number(m[3]) }));
  const exBox = extractRows(existing, /set box_image_url = v\.box_image_url\nfrom \(values\n {2}([\s\S]*?)\n\) as v\(set_id, box_image_url\)/);
  const existingBySetId = new Map(
    exBox.map((r) => { const m = r.match(/^\((\d+),\s*'([^']*)'/); return m ? [Number(m[1]), m[2]] : null; }).filter(Boolean),
  );
  console.log(`BOX_ONLY: ${sets.length} sets in the current seed (${existingBySetId.size} already have a box URL).`);

  // rehostBoxImages carries existing entries forward and re-hosts any not-yet-on-R2 URL, so the
  // returned map is the complete, updated box set — no separate merge needed.
  const boxUrlBySetId = await rehostBoxImages(sets, existingBySetId);
  const boxRows = [...boxUrlBySetId.entries()].map(([setId, url]) => `(${num(setId)}, ${q(url)})`);
  const block = boxRows.length
    ? `-- Re-hosted box images (Supabase Storage) -> sets.box_image_url (see fetch-catalog.mjs)
update public.sets as s set box_image_url = v.box_image_url
from (values
  ${boxRows.join(",\n  ")}
) as v(set_id, box_image_url)
where s.set_id = v.set_id;`
    : "-- (no re-hosted box images)";

  const blockRe = /-- Re-hosted box images[\s\S]*?where s\.set_id = v\.set_id;/;
  const noBoxRe = /-- \(no re-hosted box images\)/;
  const updated = blockRe.test(existing) ? existing.replace(blockRe, block)
    : noBoxRe.test(existing) ? existing.replace(noBoxRe, block)
    : `${existing.trimEnd()}\n\n${block}\n`;
  await writeFile(OUT, updated, "utf8");
  console.log(`BOX_ONLY: ${boxRows.length} box_image rows -> supabase/seed.sql`);
  console.log("Next: supabase db reset (local) / apply the box UPDATE block to prod");
}

async function main() {
  if (BOX_ONLY === "1" || BOX_ONLY.toLowerCase() === "true") { await boxOnlyRun(); return; }
  const userHash = await getUserHash();
  const themes = BRICKSET_THEMES.split(",").map((t) => t.trim()).filter(Boolean);
  const fullThemes = BRICKSET_FULL_THEMES.split(",").map((t) => t.trim()).filter(Boolean);
  const pageSize = Number(BRICKSET_PAGESIZE);
  const enableAllThemes = SAMPLES_ONLY || BRICKSET_ALL_THEMES === "1" || BRICKSET_ALL_THEMES.toLowerCase() === "true";
  const sampleSize = Math.max(1, Number(BRICKSET_ALL_THEMES_SAMPLE) || 1);

  let primarySets = [];
  let fullSets = [];
  let retiredSets = [];
  if (FULL_ALL) {
    console.log("FULL_ALL — full-pulling every Brickset theme (whole catalog); skipping the primary/retired/theme-sample passes.");
    fullSets = await fetchFullThemes(userHash); // pulls ALL themes when FULL_ALL
  } else if (!SAMPLES_ONLY) {
    console.log(`Fetching newest ${pageSize} sets each for: ${themes.join(", ")}`);
    const collected = [];
    for (const theme of themes) {
      // Brickset's orderBy field is "YearFrom" (not "Year"); "YearDESC" is silently ignored and you
      // get default (set-number) order. "YearFromDESC" gives genuinely newest-first.
      // extendedData:true is REQUIRED for Brickset to return extendedData.notes (the availability note
      // shown on the detail page). Without it the field is absent even for sets that have a note.
      const p = { theme, pageSize, orderBy: "YearFromDESC", extendedData: true };
      if (BRICKSET_YEAR) p.year = BRICKSET_YEAR;
      if (BRICKSET_SUBTHEME) p.subtheme = BRICKSET_SUBTHEME;
      const params = JSON.stringify(p);
      const j = await post("getSets", { apiKey: BRICKSET_API_KEY, userHash, params });
      if (j.status !== "success") {
        console.error(`  getSets(${theme}) failed: ${j.message}`);
        continue;
      }
      console.log(`  ${theme}: ${j.sets?.length ?? 0}`);
      collected.push(...(j.sets || []));
    }
    // Primary newest-N sets, deduped by setID (a set can match more than one query).
    primarySets = [...new Map(collected.map((s) => [s.setID, s])).values()];
    // Full-theme pass — every set in BRICKSET_FULL_THEMES (all pages, retired included).
    fullSets = await fetchFullThemes(userHash);
    // Extra retired-sets pass (catalog only — NOT sent to the Rebrickable minifig fetch, to avoid its
    // rate limits). For exercising the retired badge/value flows across a spread of themes/years.
    const enableRetired = BRICKSET_RETIRED !== "0" && BRICKSET_RETIRED.toLowerCase() !== "false";
    retiredSets = enableRetired ? await fetchRetiredSets(userHash) : [];
  } else {
    console.log("SAMPLES_ONLY — skipping primary/full/retired passes (theme samples + append only).");
  }

  // Full-picture pass — one sample set per Brickset theme so every theme shows up in the browse.
  // Skipped under FULL_ALL (which already pulls every theme in full).
  const themeSampleSets = (enableAllThemes && !FULL_ALL) ? await fetchThemeSamples(userHash, sampleSize) : [];

  // Sets that go through the Rebrickable minifig fetch: the primary pass (+ full-theme when
  // BRICKSET_FULL_MINIFIGS is on). Never the retired or theme-sample passes; none in SAMPLES_ONLY.
  const fullMinifigs = BRICKSET_FULL_MINIFIGS === "1" || BRICKSET_FULL_MINIFIGS.toLowerCase() === "true";
  const minifigSets = SAMPLES_ONLY ? [] : (fullMinifigs
    ? [...new Map([...primarySets, ...fullSets].map((s) => [s.setID, s])).values()]
    : primarySets);

  // Full catalog written to seed.sql (primary + full-theme + retired + theme-samples), deduped; a
  // richer pass wins a set_id tie over the single-sample coverage pass.
  const catalogSets = [...new Map([...themeSampleSets, ...retiredSets, ...fullSets, ...primarySets].map((s) => [s.setID, s])).values()];
  if (catalogSets.length === 0) {
    console.error("No sets returned — check your key/hash and theme names.");
    process.exit(1);
  }
  console.log(`Catalog: ${primarySets.length} primary + ${fullSets.length} full-theme + ${retiredSets.length} retired + ${themeSampleSets.length} theme-samples -> ${catalogSets.length} unique.`);

  // Box images are re-hosted via BOX_ONLY mode (which reads the whole seed); a normal run doesn't
  // touch BrickLink — it just preserves the existing box_image_url block through the append merge.
  const boxUrlBySetId = new Map();

  // Minifigs (Rebrickable): fig list per set → the `minifigs` catalog + `set_minifigs` join.
  const { figs, setFigs } = REBRICKABLE_API_KEY
    ? await fetchMinifigs(minifigSets)
    : { figs: new Map(), setFigs: [] };

  // Fresh row strings for everything just fetched.
  let setRowList = catalogSets.map(setRow);
  let priceRowList = catalogSets.flatMap(priceRows);
  let minifigRowList = [...figs.entries()].map(
    ([figNum, f]) => `(${q(figNum)}, ${q(f.name)}, null, ${q(f.image_url)})`,
  );
  let setMinifigRowList = setFigs.map((sf) => `(${num(sf.setId)}, ${q(sf.figNum)}, ${num(sf.quantity) || 1})`);

  // Vietnamese note translations (translate-at-ingest) — emitted as a separate UPDATE block keyed by
  // set_id (not a column in the sets INSERT), so it stays append-compatible with older seeds.
  const noteViMap = SAMPLES_ONLY ? new Map() : await translateNotes(catalogSets.map((s) => s.extendedData?.notes));
  let noteViRowList = catalogSets
    .filter((s) => s.extendedData?.notes && noteViMap.has(s.extendedData.notes.trim()))
    .map((s) => `(${num(s.setID)}, ${q(noteViMap.get(s.extendedData.notes.trim()))})`);

  // Re-hosted box image URLs, keyed by set_id (a separate UPDATE, append-safe like notes_vi).
  let boxImageRowList = [...boxUrlBySetId.entries()].map(([setId, url]) => `(${num(setId)}, ${q(url)})`);

  // Append mode: merge onto the existing seed.sql. Existing rows are kept; a freshly fetched row for
  // the same key overwrites the old one. Minifigs + note translations are merged too (not just
  // sets/prices), so an append never drops previously-seeded figs or notes — even when this run skips
  // the Rebrickable fetch or translation.
  if (APPEND) {
    const existing = await readFile(OUT, "utf8").catch(() => null);
    if (existing) {
      const exSets = extractRows(existing, /insert into public\.sets[\s\S]*?\nvalues\n {2}([\s\S]*?)\non conflict \(set_id\)/);
      const exPrices = extractRows(existing, /insert into public\.set_prices[\s\S]*?\nvalues\n {2}([\s\S]*?)\non conflict \(set_id, region\)/);
      const exFigs = extractRows(existing, /insert into public\.minifigs[\s\S]*?\nvalues\n {2}([\s\S]*?)\non conflict \(fig_num\)/);
      const exSetFigs = extractRows(existing, /insert into public\.set_minifigs[\s\S]*?\nvalues\n {2}([\s\S]*?)\non conflict \(set_id, fig_num\)/);
      const exNoteVi = extractRows(existing, /set notes_vi = v\.notes_vi\nfrom \(values\n {2}([\s\S]*?)\n\) as v\(set_id, notes_vi\)/);
      const exBox = extractRows(existing, /set box_image_url = v\.box_image_url\nfrom \(values\n {2}([\s\S]*?)\n\) as v\(set_id, box_image_url\)/);
      setRowList = mergeRows(exSets, setRowList, (r) => r.match(/^\((\d+),/)?.[1]);
      priceRowList = mergeRows(exPrices, priceRowList, (r) => r.match(/^\((\d+), '([A-Z]+)'/)?.slice(1, 3).join("-"));
      minifigRowList = mergeRows(exFigs, minifigRowList, (r) => r.match(/^\('([^']+)'/)?.[1]);
      setMinifigRowList = mergeRows(exSetFigs, setMinifigRowList, (r) => r.match(/^\((\d+), '([^']+)'/)?.slice(1, 3).join("-"));
      noteViRowList = mergeRows(exNoteVi, noteViRowList, (r) => r.match(/^\((\d+),/)?.[1]);
      boxImageRowList = mergeRows(exBox, boxImageRowList, (r) => r.match(/^\((\d+),/)?.[1]);
      console.log(`Append: ${exSets.length} existing sets -> ${setRowList.length}; minifigs ${exFigs.length} -> ${minifigRowList.length}; notes_vi ${exNoteVi.length} -> ${noteViRowList.length}; box_image ${exBox.length} -> ${boxImageRowList.length}.`);
    }
  }

  const setValues = setRowList.join(",\n  ");
  const priceValues = priceRowList.join(",\n  ");
  const minifigValues = minifigRowList.join(",\n  ");
  const setMinifigValues = setMinifigRowList.join(",\n  ");
  const noteViValues = noteViRowList.join(",\n  ");
  const boxImageValues = boxImageRowList.join(",\n  ");

  const sql =
`-- Generated by scripts/fetch-catalog.mjs from Brickset. Sample catalog for LOCAL dev.
-- Regenerate: node --env-file=supabase/.env.local scripts/fetch-catalog.mjs
-- ${setRowList.length} sets total${APPEND ? ` (merged: +${catalogSets.length} newly fetched)` : ""}. Themes: ${themes.join(", ")}${fullThemes.length ? `; full: ${fullThemes.join(", ")}` : ""}${BRICKSET_YEAR ? `; years ${BRICKSET_YEAR}` : ""}

insert into public.sets
  (set_id, set_number, number_variant, name, year, theme, theme_group, subtheme, category,
   item_type, pieces, minifigs, age_min, released, availability, image_url, thumbnail_url,
   brickset_url, rating, review_count, notes, launch_date, exit_date)
values
  ${setValues}
on conflict (set_id) do update set
  set_number = excluded.set_number, name = excluded.name, year = excluded.year,
  theme = excluded.theme, theme_group = excluded.theme_group, subtheme = excluded.subtheme,
  category = excluded.category, pieces = excluded.pieces, minifigs = excluded.minifigs,
  released = excluded.released, image_url = excluded.image_url,
  thumbnail_url = excluded.thumbnail_url, brickset_url = excluded.brickset_url,
  rating = excluded.rating, review_count = excluded.review_count, notes = excluded.notes,
  launch_date = excluded.launch_date, exit_date = excluded.exit_date,
  last_synced_at = now();

${priceValues ? `insert into public.set_prices
  (set_id, region, retail_price, date_first_available, date_last_available)
values
  ${priceValues}
on conflict (set_id, region) do update set
  retail_price = excluded.retail_price,
  date_first_available = excluded.date_first_available,
  date_last_available = excluded.date_last_available;` : "-- (no retail prices in this sample)"}

${noteViValues ? `-- Vietnamese note translations (translate-at-ingest; shown when the app language is VI)
update public.sets as s set notes_vi = v.notes_vi
from (values
  ${noteViValues}
) as v(set_id, notes_vi)
where s.set_id = v.set_id;` : "-- (no translated notes)"}

${boxImageValues ? `-- Re-hosted box images (Supabase Storage) -> sets.box_image_url (see fetch-catalog.mjs)
update public.sets as s set box_image_url = v.box_image_url
from (values
  ${boxImageValues}
) as v(set_id, box_image_url)
where s.set_id = v.set_id;` : "-- (no re-hosted box images)"}

${minifigValues ? `insert into public.minifigs
  (fig_num, name, num_parts, image_url)
values
  ${minifigValues}
on conflict (fig_num) do update set
  name = excluded.name, num_parts = excluded.num_parts, image_url = excluded.image_url,
  last_synced_at = now();

insert into public.set_minifigs
  (set_id, fig_num, quantity)
values
  ${setMinifigValues}
on conflict (set_id, fig_num) do update set quantity = excluded.quantity;` : "-- (no minifigs in this sample)"}
`;

  await writeFile(OUT, sql, "utf8");
  console.log(`\nWrote ${setRowList.length} sets + ${minifigRowList.length} minifigs -> supabase/seed.sql`);
  console.log("Next: supabase db reset");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
