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
  BRICKSET_THEMES = "Architecture,Batman,Icons,Technic,Ninjago,Star Wars",
  BRICKSET_PAGESIZE = "20",
  // Optional Brickset year filter, comma-separated (e.g. "2024,2025,2026"). Empty = all years.
  BRICKSET_YEAR = "",
  // "1"/"true" = merge onto the existing seed.sql (dedupe by set_id, newly fetched rows win)
  // instead of overwriting it, so a fetch adds to the current catalog rather than replacing it.
  BRICKSET_APPEND = "",
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
    `${q(s.image?.thumbnailURL)}, ${q(s.bricksetURL)}, ${num(s.rating)}, ${num(s.reviewCount)})`;
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

async function main() {
  const userHash = await getUserHash();
  const themes = BRICKSET_THEMES.split(",").map((t) => t.trim()).filter(Boolean);
  const pageSize = Number(BRICKSET_PAGESIZE);

  console.log(`Fetching newest ${pageSize} sets each for: ${themes.join(", ")}`);
  const collected = [];
  for (const theme of themes) {
    // Brickset's orderBy field is "YearFrom" (not "Year"); "YearDESC" is silently ignored and you
    // get default (set-number) order. "YearFromDESC" gives genuinely newest-first.
    const p = { theme, pageSize, orderBy: "YearFromDESC" };
    if (BRICKSET_YEAR) p.year = BRICKSET_YEAR;
    const params = JSON.stringify(p);
    const j = await post("getSets", { apiKey: BRICKSET_API_KEY, userHash, params });
    if (j.status !== "success") {
      console.error(`  getSets(${theme}) failed: ${j.message}`);
      continue;
    }
    console.log(`  ${theme}: ${j.sets?.length ?? 0}`);
    collected.push(...(j.sets || []));
  }

  // Dedupe by setID (a set can match more than one query).
  const sets = [...new Map(collected.map((s) => [s.setID, s])).values()];
  if (sets.length === 0) {
    console.error("No sets returned — check your key/hash and theme names.");
    process.exit(1);
  }

  // Row strings for the freshly fetched sets.
  let setRowList = sets.map(setRow);
  let priceRowList = sets.flatMap(priceRows);

  // Append mode: merge onto the existing seed.sql. Existing rows are kept; a freshly fetched row
  // for the same key overwrites the old one. Keys: set_id for sets, "set_id-region" for prices.
  if (APPEND) {
    const existing = await readFile(OUT, "utf8").catch(() => null);
    if (existing) {
      const exSets = extractRows(existing, /insert into public\.sets[\s\S]*?\nvalues\n {2}([\s\S]*?)\non conflict \(set_id\)/);
      const exPrices = extractRows(existing, /insert into public\.set_prices[\s\S]*?\nvalues\n {2}([\s\S]*?)\non conflict \(set_id, region\)/);
      setRowList = mergeRows(exSets, setRowList, (r) => r.match(/^\((\d+),/)?.[1]);
      priceRowList = mergeRows(exPrices, priceRowList, (r) => r.match(/^\((\d+), '([A-Z]+)'/)?.slice(1, 3).join("-"));
      console.log(`Append: ${exSets.length} existing + ${sets.length} fetched -> ${setRowList.length} sets after merge.`);
    }
  }

  const setValues = setRowList.join(",\n  ");
  const priceValues = priceRowList.join(",\n  ");

  const sql =
`-- Generated by scripts/fetch-catalog.mjs from Brickset. Sample catalog for LOCAL dev.
-- Regenerate: node --env-file=supabase/.env.local scripts/fetch-catalog.mjs
-- ${setRowList.length} sets total${APPEND ? ` (merged: +${sets.length} newly fetched)` : ""}. Latest fetch themes: ${themes.join(", ")}${BRICKSET_YEAR ? `; years ${BRICKSET_YEAR}` : ""}

insert into public.sets
  (set_id, set_number, number_variant, name, year, theme, theme_group, subtheme, category,
   item_type, pieces, minifigs, age_min, released, availability, image_url, thumbnail_url,
   brickset_url, rating, review_count)
values
  ${setValues}
on conflict (set_id) do update set
  set_number = excluded.set_number, name = excluded.name, year = excluded.year,
  theme = excluded.theme, theme_group = excluded.theme_group, subtheme = excluded.subtheme,
  category = excluded.category, pieces = excluded.pieces, minifigs = excluded.minifigs,
  released = excluded.released, image_url = excluded.image_url,
  thumbnail_url = excluded.thumbnail_url, brickset_url = excluded.brickset_url,
  rating = excluded.rating, review_count = excluded.review_count,
  last_synced_at = now();

${priceValues ? `insert into public.set_prices
  (set_id, region, retail_price, date_first_available, date_last_available)
values
  ${priceValues}
on conflict (set_id, region) do update set
  retail_price = excluded.retail_price,
  date_first_available = excluded.date_first_available,
  date_last_available = excluded.date_last_available;` : "-- (no retail prices in this sample)"}
`;

  await writeFile(OUT, sql, "utf8");
  console.log(`\nWrote ${setRowList.length} sets -> supabase/seed.sql`);
  console.log("Next: supabase db reset");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
