// Unit tests for the pure parts of catalog-refresh. `node:test` runs under both runtimes:
//   node --experimental-strip-types --test supabase/functions/catalog-refresh/catalog_refresh.test.ts
//   deno test supabase/functions/catalog-refresh/
import { test } from "node:test";
import assert from "node:assert/strict";
import { chunk, normName, priceRows, toSetRow, toSetRows, variantOf } from "./transform.ts";
import { digestEmail, errorEmail, LIST_LIMIT, needsDigest, type ReportSet } from "./report.ts";

const brickset = {
  setID: 51452,
  number: "71050",
  numberVariant: 3,
  name: "Spider-Man 2099",
  year: 2025,
  theme: "Collectable Minifigures",
  themeGroup: "Miscellaneous",
  subtheme: "Marvel Series 2",
  category: "Normal",
  pieces: 8,
  minifigs: 1,
  ageRange: { min: 5 },
  released: true,
  availability: "Retail",
  image: { imageURL: "https://img/x.jpg", thumbnailURL: "" },
  bricksetURL: "https://brickset.com/sets/71050-3",
  rating: 4.2,
  reviewCount: 0,
  extendedData: { notes: "Limited release." },
  launchDate: "2025-01-01T00:00:00Z",
  exitDate: null,
  LEGOCom: {
    US: { retailPrice: 4.99, dateFirstAvailable: "2025-01-01T00:00:00Z", dateLastAvailable: null },
    UK: {},
    CA: { retailPrice: null, dateFirstAvailable: null, dateLastAvailable: "2025-12-31T00:00:00Z" },
    AU: { retailPrice: 9.99 }, // not a stored region
  },
};

test("variantOf keeps a real 0 and defaults only a missing variant to 1", () => {
  assert.equal(variantOf({ numberVariant: 0 }), 0);
  assert.equal(variantOf({ numberVariant: "2" }), 2);
  assert.equal(variantOf({ numberVariant: null }), 1);
  assert.equal(variantOf({ numberVariant: "" }), 1);
  assert.equal(variantOf({}), 1);
  assert.equal(variantOf({ numberVariant: "x" }), 1);
});

test("toSetRow maps every column, slicing dates and blanking empty strings", () => {
  const row = toSetRow(brickset);
  assert.ok(row);
  assert.equal(row.set_id, 51452);
  assert.equal(row.set_number, "71050");
  assert.equal(row.number_variant, 3);
  assert.equal(row.theme_group, "Miscellaneous");
  assert.equal(row.age_min, 5);
  assert.equal(row.released, true);
  assert.equal(row.thumbnail_url, null); // "" -> null
  assert.equal(row.review_count, 0); // a real 0 survives
  assert.equal(row.notes, "Limited release.");
  assert.equal(row.launch_date, "2025-01-01");
  assert.equal(row.exit_date, null);
});

test("priceRows keeps only stored regions that carry data", () => {
  assert.deepEqual(priceRows(brickset), [
    { region: "US", retail_price: 4.99, date_first_available: "2025-01-01", date_last_available: null },
    { region: "CA", retail_price: null, date_first_available: null, date_last_available: "2025-12-31" },
  ]);
  assert.deepEqual(priceRows({}), []);
});

test("toSetRow rejects rows without an identity, and a non-boolean released becomes null", () => {
  assert.equal(toSetRow({ number: "1" }), null);
  assert.equal(toSetRow({ setID: 1 }), null);
  assert.equal(toSetRow({ setID: 1, number: "1", released: "yes" as unknown as boolean })?.released, null);
});

test("toSetRows dedupes by set_id (later wins) and drops invalid rows", () => {
  const rows = toSetRows([
    { setID: 1, number: "A", name: "first" },
    { setID: 2, number: "B" },
    { setID: 1, number: "A", name: "second" },
    { number: "no-id" },
  ]);
  assert.deepEqual(rows.map((r) => [r.set_id, r.name]), [[1, "second"], [2, null]]);
});

test("chunk and normName", () => {
  assert.deepEqual(chunk([1, 2, 3, 4, 5], 2), [[1, 2], [3, 4], [5]]);
  assert.deepEqual(chunk([], 3), []);
  assert.equal(normName("Spider-Man 2099!"), "spiderman2099");
  assert.equal(normName(null), "");
});

const set = (n: number, over: Partial<ReportSet> = {}): ReportSet => ({
  set_id: n, set_number: String(1000 + n), number_variant: 1, name: `Set ${n}`, theme: "City", year: 2027, notes: null, ...over,
});

const quiet = {
  runDate: "2026-09-27", inserted: [] as ReportSet[], revealed: [] as ReportSet[], notesChanged: [] as ReportSet[],
  placeholders: 0, backlog: 0, upserted: 0, minifigsFetched: 0, minifigsPending: 0, rendersPinned: 0,
};

test("digestEmail lists new, revealed and changed sets and the backlog", () => {
  const mail = digestEmail({
    ...quiet,
    inserted: [set(1, { notes: "x" }), set(2, { theme: null, year: null })],
    revealed: [set(5)],
    notesChanged: [set(3)],
    placeholders: 2,
    backlog: 4,
    upserted: 42,
    minifigsFetched: 1,
    minifigsPending: 2,
  });
  assert.equal(mail.subject, "BrickWares catalog: 2 new sets, 1 revealed, 1 changed note (backlog 4)");
  assert.match(mail.text, /• 1001-1 Set 1 \(City, 2027\) — has notes/);
  assert.match(mail.text, /• 1002-1 Set 2\n/);
  assert.match(mail.text, /Revealed \(1\)[^\n]*\n {2}• 1005-1 Set 5/);
  assert.match(mail.text, /Notes changed \(1\)/);
  assert.match(mail.text, /Enrichment backlog: 4 sets/);
  // A shell-neutral command: a flag, not bash's inline `VAR=1 node …` (which PowerShell rejects).
  assert.match(mail.text, /\n {2}node --env-file=supabase\/\.env\.local scripts\/fetch-catalog\.mjs --enrich\n/);
  assert.doesNotMatch(mail.text, /ENRICH_PENDING=1/);
  assert.match(mail.text,
    /42 sets updated, 1 minifig list fetched \(2 still waiting on Rebrickable\), 2 new "\{\?\}" placeholders \(hidden until named\)\./);
});

test("needsDigest: only new named sets, reveals or changed notes warrant an email", () => {
  assert.equal(needsDigest(quiet), false);
  assert.equal(needsDigest({ ...quiet, revealed: [set(1)] }), true);
  assert.equal(needsDigest({ ...quiet, notesChanged: [set(1)] }), true);
});

test("digestEmail caps a long list", () => {
  const many = Array.from({ length: LIST_LIMIT + 3 }, (_, i) => set(i));
  const mail = digestEmail({ ...quiet, inserted: many, backlog: many.length, upserted: many.length });
  assert.match(mail.text, /…and 3 more/);
  assert.equal(mail.subject, `BrickWares catalog: ${many.length} new sets (backlog ${many.length})`);
});

test("errorEmail carries the error and the last success", () => {
  const mail = errorEmail({ when: "2026-09-27T19:30:00Z", error: "Brickset getSets: API limit exceeded", lastSuccessAt: null });
  assert.equal(mail.subject, "BrickWares catalog refresh FAILED");
  assert.match(mail.text, /API limit exceeded/);
  assert.match(mail.text, /Last success: never\./);
});
