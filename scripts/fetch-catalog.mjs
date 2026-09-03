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
} = process.env;

const APPEND = BRICKSET_APPEND === "1" || BRICKSET_APPEND.toLowerCase() === "true";

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
    const setNum = `${s.number}-${s.numberVariant || 1}`;
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

function setRow(s) {
  return `(${num(s.setID)}, ${q(s.number)}, ${num(s.numberVariant) || 1}, ${q(s.name)}, ${num(s.year)}, ` +
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
  const themes = BRICKSET_FULL_THEMES.split(",").map((t) => t.trim()).filter(Boolean);
  if (themes.length === 0) return [];
  const pageSize = Number(BRICKSET_FULL_PAGESIZE);
  console.log(`Fetching ALL sets (every page) for: ${themes.join(", ")}`);
  const out = [];
  for (const theme of themes) {
    let total = 0;
    for (let page = 1; page <= 50; page++) {
      const params = JSON.stringify({ theme, pageSize, pageNumber: page, orderBy: "YearFromDESC", extendedData: true });
      const j = await post("getSets", { apiKey: BRICKSET_API_KEY, userHash, params });
      if (j.status !== "success") { console.error(`  full ${theme} p${page}: ${j.message}`); break; }
      const batch = j.sets || [];
      out.push(...batch);
      total += batch.length;
      if (batch.length < pageSize) break; // last page
    }
    console.log(`  ${theme}: ${total} sets (all pages)`);
  }
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

async function main() {
  const userHash = await getUserHash();
  const themes = BRICKSET_THEMES.split(",").map((t) => t.trim()).filter(Boolean);
  const fullThemes = BRICKSET_FULL_THEMES.split(",").map((t) => t.trim()).filter(Boolean);
  const pageSize = Number(BRICKSET_PAGESIZE);

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
  const primarySets = [...new Map(collected.map((s) => [s.setID, s])).values()];

  // Full-theme pass — every set in BRICKSET_FULL_THEMES (all pages, retired included).
  const fullSets = await fetchFullThemes(userHash);

  // Extra retired-sets pass (catalog only — NOT sent to the Rebrickable minifig fetch, to avoid its
  // rate limits). For exercising the retired badge/value flows across a spread of themes/years.
  const enableRetired = BRICKSET_RETIRED !== "0" && BRICKSET_RETIRED.toLowerCase() !== "false";
  const retiredSets = enableRetired ? await fetchRetiredSets(userHash) : [];

  // Sets that go through the Rebrickable minifig fetch: always the primary pass; the full-theme pass
  // too only when BRICKSET_FULL_MINIFIGS is on (a whole theme is many sets → slow, so catalog-only by
  // default). Retired-test sets never fetch minifigs.
  const fullMinifigs = BRICKSET_FULL_MINIFIGS === "1" || BRICKSET_FULL_MINIFIGS.toLowerCase() === "true";
  const minifigSets = fullMinifigs
    ? [...new Map([...primarySets, ...fullSets].map((s) => [s.setID, s])).values()]
    : primarySets;

  // Full catalog written to seed.sql (primary + full-theme + retired), deduped; a primary/full set
  // wins a set_id tie over the retired pass.
  const catalogSets = [...new Map([...retiredSets, ...fullSets, ...primarySets].map((s) => [s.setID, s])).values()];
  if (catalogSets.length === 0) {
    console.error("No sets returned — check your key/hash and theme names.");
    process.exit(1);
  }
  console.log(`Catalog: ${primarySets.length} primary + ${fullSets.length} full-theme + ${retiredSets.length} retired -> ${catalogSets.length} unique.`);

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
  const noteViMap = await translateNotes(catalogSets.map((s) => s.extendedData?.notes));
  let noteViRowList = catalogSets
    .filter((s) => s.extendedData?.notes && noteViMap.has(s.extendedData.notes.trim()))
    .map((s) => `(${num(s.setID)}, ${q(noteViMap.get(s.extendedData.notes.trim()))})`);

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
      setRowList = mergeRows(exSets, setRowList, (r) => r.match(/^\((\d+),/)?.[1]);
      priceRowList = mergeRows(exPrices, priceRowList, (r) => r.match(/^\((\d+), '([A-Z]+)'/)?.slice(1, 3).join("-"));
      minifigRowList = mergeRows(exFigs, minifigRowList, (r) => r.match(/^\('([^']+)'/)?.[1]);
      setMinifigRowList = mergeRows(exSetFigs, setMinifigRowList, (r) => r.match(/^\((\d+), '([^']+)'/)?.slice(1, 3).join("-"));
      noteViRowList = mergeRows(exNoteVi, noteViRowList, (r) => r.match(/^\((\d+),/)?.[1]);
      console.log(`Append: ${exSets.length} existing sets -> ${setRowList.length}; minifigs ${exFigs.length} -> ${minifigRowList.length}; notes_vi ${exNoteVi.length} -> ${noteViRowList.length}.`);
    }
  }

  const setValues = setRowList.join(",\n  ");
  const priceValues = priceRowList.join(",\n  ");
  const minifigValues = minifigRowList.join(",\n  ");
  const setMinifigValues = setMinifigRowList.join(",\n  ");
  const noteViValues = noteViRowList.join(",\n  ");

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
