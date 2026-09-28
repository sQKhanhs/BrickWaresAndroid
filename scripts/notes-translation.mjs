// Hand-translation round trip for the Vietnamese set notes (sets.notes_vi) — an Excel workbook out, the
// filled workbook back in. No dependencies: an .xlsx is a zip of XML parts, written/read with node:zlib.
//
//   export  node --env-file=supabase/.env.local scripts/notes-translation.mjs export [--out <dir>]
//           Reads every English note that has no Vietnamese translation (via ENRICH_API_URL/KEY — the
//           catalog is public-read), one row per DISTINCT note, most-used first, and writes
//           <dir>/BrickWares_notes_vi_<date>.xlsx + a .source.json beside it (the exact English per ID —
//           keep the two files together).
//   import  node scripts/notes-translation.mjs import <file.xlsx> [--source <file.source.json>]
//           Reads the filled Vietnamese column (Notes sheet, column D) and writes
//           supabase/notes-vi-<date>-<n>.sql — each ONE statement, since `supabase db query` accepts only
//           one — to apply with `supabase db query --linked -f <file>`.
//
// Rows are matched by ID against the .source.json, never by the English in the sheet: 759 notes contain
// line breaks and 132 have edge whitespace, which Excel may alter on save. One translation fills every set
// sharing that English note; a set whose note changed since the export is simply not matched.

import { readFile, writeFile, mkdir } from "node:fs/promises";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { crc32, deflateRawSync, inflateRawSync } from "node:zlib";

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const UNREVEALED_NAME = "{?}";
const HTML_TAG = /<[a-zA-Z/][^>]*>/;
const IMPORT_BATCH = 400; // notes per SQL statement (~100 KB) — well under any request-size limit

const today = () => new Date().toISOString().slice(0, 10);
const arg = (name) => {
  const i = process.argv.indexOf(name);
  return i > 0 ? process.argv[i + 1] : undefined;
};

// ---------------------------------------------------------------------------------------------------
// Export
// ---------------------------------------------------------------------------------------------------

async function fetchUntranslated() {
  const { ENRICH_API_URL = "", ENRICH_API_KEY = "" } = process.env;
  if (!ENRICH_API_URL || !ENRICH_API_KEY) {
    console.error("Set ENRICH_API_URL + ENRICH_API_KEY (run with --env-file=supabase/.env.local).");
    process.exit(1);
  }
  const base = ENRICH_API_URL.replace(/\/$/, "");
  const cols = "set_id,set_number,number_variant,name,theme,year,notes";
  const rows = [];
  // PostgREST caps a response at 1000 rows — page with a stable order so nothing is skipped/duplicated.
  for (let offset = 0; ; offset += 1000) {
    const url = `${base}/rest/v1/sets?select=${cols}&notes=not.is.null&notes_vi=is.null&order=set_id.asc&limit=1000&offset=${offset}`;
    const res = await fetch(url, { headers: { apikey: ENRICH_API_KEY } });
    if (!res.ok) throw new Error(`sets HTTP ${res.status}: ${(await res.text()).slice(0, 200)}`);
    const page = await res.json();
    rows.push(...page);
    if (page.length < 1000) break;
  }
  return { base, rows: rows.filter((r) => r.name !== UNREVEALED_NAME && r.notes && r.notes.trim()) };
}

function groupByNote(rows) {
  const byNote = new Map();
  for (const r of rows) {
    const g = byNote.get(r.notes) ?? { en: r.notes, sets: [] };
    g.sets.push(r);
    byNote.set(r.notes, g);
  }
  const groups = [...byNote.values()].sort((a, b) => b.sets.length - a.sets.length || a.en.localeCompare(b.en));
  groups.forEach((g, i) => {
    g.id = `N${String(i + 1).padStart(4, "0")}`;
    g.examples = g.sets
      .slice()
      .sort((a, b) => (b.year ?? 0) - (a.year ?? 0) || String(a.set_number).localeCompare(String(b.set_number)))
      .slice(0, 3)
      .map((s) => {
        const meta = [s.theme, s.year].filter((x) => x !== null && x !== undefined && x !== "").join(", ");
        return `${s.set_number}-${s.number_variant ?? 1} ${s.name ?? ""}${meta ? ` (${meta})` : ""}`;
      })
      .join("\n");
    g.html = HTML_TAG.test(g.en);
  });
  return groups;
}

async function exportCmd() {
  const outDir = resolve(arg("--out") ?? join(REPO, "..", "BrickWares-translations"));
  const { base, rows } = await fetchUntranslated();
  const groups = groupByNote(rows);
  const stem = `BrickWares_notes_vi_${today()}`;
  await mkdir(outDir, { recursive: true });

  const xlsx = buildWorkbook(groups, rows.length);
  await writeFile(join(outDir, `${stem}.xlsx`), xlsx);
  await writeFile(
    join(outDir, `${stem}.source.json`),
    JSON.stringify({ exported: new Date().toISOString(), source: base, sets: rows.length,
      notes: groups.map((g) => ({ id: g.id, en: g.en, sets: g.sets.length })) }, null, 1),
    "utf8",
  );
  console.log(`${groups.length} distinct notes (used by ${rows.length} sets) -> ${join(outDir, stem)}.xlsx (+ .source.json)`);
}

// ---------------------------------------------------------------------------------------------------
// Workbook (SpreadsheetML) — two sheets: Instructions, Notes
// ---------------------------------------------------------------------------------------------------

const xmlText = (s) =>
  String(s)
    .replace(/\r\n?/g, "\n")
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, " ")
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
const str = (ref, text, s) => `<c r="${ref}" t="inlineStr" s="${s}"><is><t xml:space="preserve">${xmlText(text)}</t></is></c>`;
const num = (ref, n, s) => `<c r="${ref}" s="${s}"><v>${n}</v></c>`;
const blank = (ref, s) => `<c r="${ref}" s="${s}"/>`;

// Style ids (cellXfs order in STYLES below).
const S = { header: 1, text: 2, fill: 3, count: 4, title: 5, muted: 6, wrap: 7, bold: 8, green: 9 };

const STYLES = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="4">
<font><sz val="10"/><name val="Arial"/><family val="2"/></font>
<font><b/><sz val="10"/><name val="Arial"/><family val="2"/></font>
<font><b/><sz val="14"/><name val="Arial"/><family val="2"/></font>
<font><sz val="10"/><color rgb="FF595959"/><name val="Arial"/><family val="2"/></font>
</fonts>
<fills count="5">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFD9D9D9"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFF2CC"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFE2EFDA"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color rgb="FFBFBFBF"/></left><right style="thin"><color rgb="FFBFBFBF"/></right><top style="thin"><color rgb="FFBFBFBF"/></top><bottom style="thin"><color rgb="FFBFBFBF"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="10">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="top"/></xf>
<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="0" fontId="3" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="4" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
<dxfs count="1"><dxf><fill><patternFill patternType="solid"><bgColor rgb="FFE2EFDA"/></patternFill></fill></dxf></dxfs>
</styleSheet>`;

// Wrapped rows need an explicit height (a generated file has none for Excel to auto-fit from).
function lineCount(text, perLine) {
  return String(text).split(/\r?\n/).reduce((n, seg) => n + Math.max(1, Math.ceil(seg.length / perLine)), 0);
}
const rowHeight = (lines) => Math.min(409, Math.max(15, lines * 12.75 + 3));

function worksheet({ cols, rows, freezeRow = 0, selected = false, filterRef, extra = "" }) {
  const pane = freezeRow
    ? `<pane ySplit="${freezeRow}" topLeftCell="A${freezeRow + 1}" activePane="bottomLeft" state="frozen"/><selection pane="bottomLeft" activeCell="D${freezeRow + 1}" sqref="D${freezeRow + 1}"/>`
    : "";
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheetViews><sheetView workbookViewId="0"${selected ? ' tabSelected="1"' : ""}>${pane}</sheetView></sheetViews>
<sheetFormatPr defaultRowHeight="15"/>
<cols>${cols.map((w, i) => `<col min="${i + 1}" max="${i + 1}" width="${w}" customWidth="1"/>`).join("")}</cols>
<sheetData>${rows.join("")}</sheetData>
${filterRef ? `<autoFilter ref="${filterRef}"/>` : ""}${extra}
<pageMargins left="0.5" right="0.5" top="0.75" bottom="0.75" header="0.3" footer="0.3"/>
</worksheet>`;
}

function notesSheet(groups) {
  const rows = [
    `<row r="1" ht="30" customHeight="1">${[
      str("A1", "ID", S.header),
      str("B1", "Sets", S.header),
      str("C1", "English note (don't edit)", S.header),
      str("D1", "Vietnamese — type here", S.header),
      str("E1", "Example sets (context)", S.header),
      str("F1", "Contains HTML", S.header),
    ].join("")}</row>`,
  ];
  groups.forEach((g, i) => {
    const r = i + 2;
    const lines = Math.max(lineCount(g.en, 68), Math.ceil(lineCount(g.en, 68) * 1.25), lineCount(g.examples, 52));
    rows.push(
      `<row r="${r}" ht="${rowHeight(lines)}" customHeight="1">` +
        str(`A${r}`, g.id, S.count) +
        num(`B${r}`, g.sets.length, S.count) +
        str(`C${r}`, g.en, S.text) +
        blank(`D${r}`, S.fill) +
        str(`E${r}`, g.examples, S.muted) +
        (g.html ? str(`F${r}`, "yes — keep the <…> tags", S.muted) : blank(`F${r}`, S.muted)) +
        `</row>`,
    );
  });
  const last = groups.length + 1;
  // A filled Vietnamese cell turns green, so progress is visible at a glance.
  const cf = `<conditionalFormatting sqref="D2:D${last}"><cfRule type="expression" dxfId="0" priority="1"><formula>LEN(TRIM(D2))&gt;0</formula></cfRule></conditionalFormatting>`;
  return worksheet({ cols: [8, 6, 60, 60, 46, 14], rows, freezeRow: 1, filterRef: `A1:F${last}`, extra: cf });
}

function instructionsSheet(noteCount, setCount) {
  const lines = [
    ["title", "BrickWares — Vietnamese set notes to translate"],
    ["wrap", `Exported ${today()} from the live catalog: ${noteCount.toLocaleString("en-US")} English notes (used by ${setCount.toLocaleString("en-US")} sets) that have no Vietnamese translation yet. In Vietnamese mode the app shows these in English.`],
    [],
    ["bold", "How to fill it in"],
    ["wrap", "1. On the Notes sheet, type the Vietnamese in column D (yellow). A filled cell turns green."],
    ["wrap", "2. Leave a cell blank to skip it. Translate as much as you like, in any order — only filled cells are imported, and the rest can be exported again later."],
    ["wrap", "3. Rows are sorted by how many sets use the note (column B), so the top rows help the most sets. One translation fills every set that shares that note."],
    ["wrap", "4. Keep unchanged: set numbers, set and product names, store and brand names (LEGO.com, Walmart, Target…), people's names, and web links. Rows marked in column F contain HTML — keep everything inside < > exactly as it is and translate only the words between the tags."],
    ["wrap", "5. Don't change column A (ID); the import uses it. Sorting, filtering and resizing are fine."],
    ["wrap", "6. Save as .xlsx and tell Claude where the file is — Claude writes the translations to the database. Keep the .source.json file next to this workbook: the import needs it."],
    [],
    ["bold", "Legend"],
  ];
  const rows = [];
  let r = 1;
  for (const [kind, text] of lines) {
    if (!kind) { r++; continue; }
    const ht = kind === "title" ? 24 : rowHeight(lineCount(text, 130));
    rows.push(`<row r="${r}" ht="${ht}" customHeight="1">${str(`B${r}`, text, S[kind])}</row>`);
    r++;
  }
  rows.push(`<row r="${r}">${str(`A${r}`, "", S.fill)}${str(`B${r}`, "Yellow = type the Vietnamese here", S.wrap)}</row>`); r++;
  rows.push(`<row r="${r}">${str(`A${r}`, "", S.green)}${str(`B${r}`, "Green = translated", S.wrap)}</row>`); r += 2;
  rows.push(`<row r="${r}">${str(`B${r}`, "Examples (format only — these rows are not imported)", S.bold)}</row>`); r++;
  rows.push(`<row r="${r}" ht="30" customHeight="1">${str(`B${r}`, "English note (column C)", S.header)}${str(`C${r}`, "Vietnamese (column D)", S.header)}</row>`); r++;
  const examples = [
    ["USA only.", "Chỉ bán tại Mỹ."],
    ["Exclusive to LEGO VIP Program members.", "Độc quyền cho thành viên chương trình LEGO VIP."],
    ["Instructions are available at <a href='https://www.lego.com'>LEGO.com</a>.", "Hướng dẫn lắp ráp có tại <a href='https://www.lego.com'>LEGO.com</a>."],
  ];
  for (const [en, vi] of examples) {
    rows.push(`<row r="${r}" ht="30" customHeight="1">${str(`B${r}`, en, S.text)}${str(`C${r}`, vi, S.green)}</row>`);
    r++;
  }
  return worksheet({ cols: [6, 70, 70], rows, selected: true });
}

function buildWorkbook(groups, setCount) {
  const last = groups.length + 1;
  const parts = {
    "[Content_Types].xml": `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>`,
    "_rels/.rels": `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>`,
    "xl/workbook.xml": `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<bookViews><workbookView activeTab="0"/></bookViews>
<sheets><sheet name="Instructions" sheetId="1" r:id="rId1"/><sheet name="Notes" sheetId="2" r:id="rId2"/></sheets>
<definedNames><definedName name="_xlnm._FilterDatabase" localSheetId="1" hidden="1">Notes!$A$1:$F$${last}</definedName></definedNames>
</workbook>`,
    "xl/_rels/workbook.xml.rels": `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>`,
    "xl/styles.xml": STYLES,
    "xl/worksheets/sheet1.xml": instructionsSheet(groups.length, setCount),
    "xl/worksheets/sheet2.xml": notesSheet(groups),
  };
  return zip(Object.entries(parts).map(([name, text]) => ({ name, data: Buffer.from(text, "utf8") })));
}

// ---------------------------------------------------------------------------------------------------
// Minimal ZIP (deflate) writer / reader
// ---------------------------------------------------------------------------------------------------

function zip(files) {
  const d = new Date();
  const time = (d.getHours() << 11) | (d.getMinutes() << 5) | (d.getSeconds() >> 1);
  const date = ((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate();
  const local = [];
  const central = [];
  let offset = 0;
  for (const f of files) {
    const name = Buffer.from(f.name, "utf8");
    const packed = deflateRawSync(f.data);
    const crc = crc32(f.data) >>> 0;
    const lh = Buffer.alloc(30);
    lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(20, 4); lh.writeUInt16LE(0x0800, 6); lh.writeUInt16LE(8, 8);
    lh.writeUInt16LE(time, 10); lh.writeUInt16LE(date, 12); lh.writeUInt32LE(crc, 14);
    lh.writeUInt32LE(packed.length, 18); lh.writeUInt32LE(f.data.length, 22); lh.writeUInt16LE(name.length, 26); lh.writeUInt16LE(0, 28);
    local.push(lh, name, packed);
    const ch = Buffer.alloc(46);
    ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(20, 4); ch.writeUInt16LE(20, 6); ch.writeUInt16LE(0x0800, 8); ch.writeUInt16LE(8, 10);
    ch.writeUInt16LE(time, 12); ch.writeUInt16LE(date, 14); ch.writeUInt32LE(crc, 16);
    ch.writeUInt32LE(packed.length, 20); ch.writeUInt32LE(f.data.length, 24); ch.writeUInt16LE(name.length, 28);
    ch.writeUInt32LE(offset, 42);
    central.push(ch, name);
    offset += 30 + name.length + packed.length;
  }
  const dir = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(files.length, 8); end.writeUInt16LE(files.length, 10);
  end.writeUInt32LE(dir.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...local, dir, end]);
}

/** name -> Buffer for every entry, read through the central directory (robust to data descriptors). */
function unzip(buf) {
  let eocd = buf.length - 22;
  while (eocd >= 0 && buf.readUInt32LE(eocd) !== 0x06054b50) eocd--;
  if (eocd < 0) throw new Error("not a zip/xlsx file");
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  const out = new Map();
  for (let i = 0; i < count; i++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error("bad zip central directory");
    const method = buf.readUInt16LE(p + 10);
    const size = buf.readUInt32LE(p + 20);
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    const localAt = buf.readUInt32LE(p + 42);
    const name = buf.toString("utf8", p + 46, p + 46 + nameLen);
    const dataAt = localAt + 30 + buf.readUInt16LE(localAt + 26) + buf.readUInt16LE(localAt + 28);
    const raw = buf.subarray(dataAt, dataAt + size);
    out.set(name, method === 8 ? inflateRawSync(raw) : Buffer.from(raw));
    p += 46 + nameLen + extraLen + commentLen;
  }
  return out;
}

// ---------------------------------------------------------------------------------------------------
// Import
// ---------------------------------------------------------------------------------------------------

/** XML text -> string: entities, then Excel's _xHHHH_ escapes (e.g. _x000D_ for a carriage return). */
function unxml(s) {
  return s
    .replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&apos;/g, "'")
    .replace(/&#x([0-9a-fA-F]+);/g, (_, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(Number(d)))
    .replace(/&amp;/g, "&")
    .replace(/_x([0-9a-fA-F]{4})_/g, (_, h) => String.fromCharCode(parseInt(h, 16)));
}

/** All <t> text of a string item, skipping phonetic (<rPh>) runs. */
const itemText = (xml) => [...xml.replace(/<rPh\b[\s\S]*?<\/rPh>/g, "").matchAll(/<t\b[^>]*>([\s\S]*?)<\/t>|<t\b[^>]*\/>/g)]
  .map((m) => unxml(m[1] ?? "")).join("");

function readSheet(files, sheetName) {
  const wb = files.get("xl/workbook.xml")?.toString("utf8") ?? "";
  const sheet = [...wb.matchAll(/<sheet\b([^>]*)\/?>/g)].map((m) => m[1]).find((a) => new RegExp(`name="${sheetName}"`).test(a));
  if (!sheet) throw new Error(`no "${sheetName}" sheet in the workbook`);
  const rid = sheet.match(/r:id="([^"]+)"/)[1];
  const rels = files.get("xl/_rels/workbook.xml.rels").toString("utf8");
  const target = [...rels.matchAll(/<Relationship\b([^>]*)\/?>/g)].map((m) => m[1]).find((a) => a.includes(`Id="${rid}"`)).match(/Target="([^"]+)"/)[1];
  const path = target.startsWith("/") ? target.slice(1) : `xl/${target}`;
  const shared = [...(files.get("xl/sharedStrings.xml")?.toString("utf8") ?? "").matchAll(/<si>([\s\S]*?)<\/si>/g)].map((m) => itemText(m[1]));

  const rows = new Map(); // row number -> { col letter -> string }
  const xml = files.get(path).toString("utf8");
  for (const c of xml.matchAll(/<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
    const attrs = c[1];
    const ref = attrs.match(/\br="([A-Z]+)(\d+)"/);
    if (!ref || !c[2]) continue;
    const type = attrs.match(/\bt="([^"]+)"/)?.[1];
    const v = c[2].match(/<v>([\s\S]*?)<\/v>/)?.[1];
    let value;
    if (type === "s") value = shared[Number(v)] ?? "";
    else if (type === "inlineStr") value = itemText(c[2]);
    else value = v !== undefined ? unxml(v) : "";
    const row = rows.get(Number(ref[2])) ?? {};
    row[ref[1]] = value;
    rows.set(Number(ref[2]), row);
  }
  return rows;
}

const sqlText = (s) => `'${String(s).replace(/'/g, "''")}'`;

async function importCmd() {
  const file = process.argv[3];
  if (!file) { console.error("usage: notes-translation.mjs import <file.xlsx> [--source <file.source.json>]"); process.exit(1); }
  const sourcePath = arg("--source") ?? file.replace(/\.xlsx$/i, ".source.json");
  const source = JSON.parse(await readFile(sourcePath, "utf8"));
  const enById = new Map(source.notes.map((n) => [n.id, n.en]));

  const rows = readSheet(unzip(await readFile(file)), "Notes");
  const pairs = [];
  let unknownIds = 0;
  for (const [r, row] of rows) {
    if (r === 1) continue;
    const id = (row.A ?? "").trim();
    const vi = (row.D ?? "").replace(/\r\n?/g, "\n").trim();
    if (!vi) continue;
    const en = enById.get(id);
    if (!en) { unknownIds++; continue; }
    pairs.push({ id, en, vi });
  }
  console.log(`${pairs.length} translated notes found (of ${enById.size} exported)${unknownIds ? `; ${unknownIds} filled rows had an unknown ID and were skipped` : ""}.`);
  if (!pairs.length) return;

  // HTML guard: a translation must keep the English note's tags, or a link would break.
  const tags = (s) => (s.match(/<[^>]+>/g) ?? []).join("");
  const tagMismatch = pairs.filter((p) => HTML_TAG.test(p.en) && tags(p.en) !== tags(p.vi));
  for (const p of tagMismatch) console.warn(`  ${p.id}: the HTML tags differ from the English — skipped (fix the tags and re-import)`);
  const ok = pairs.filter((p) => !tagMismatch.includes(p));

  const stamp = today();
  const outFiles = [];
  for (let i = 0; i < ok.length; i += IMPORT_BATCH) {
    const batch = ok.slice(i, i + IMPORT_BATCH);
    const n = outFiles.length + 1;
    const name = `notes-vi-${stamp}-${n}.sql`;
    // ONE statement (supabase db query takes a single one). Matches by the exact English from the export,
    // so a set whose note changed since then is left alone; a human translation replaces a machine one.
    const sql =
`-- ${basename(file)} -> sets.notes_vi, batch ${n} (${batch.length} notes). Apply: supabase db query --linked -f supabase/${name}
with v(en, vi) as (values
  ${batch.map((p) => `(${sqlText(p.en)}, ${sqlText(p.vi)})`).join(",\n  ")}
), u as (
  update public.sets as s set notes_vi = v.vi from v where s.notes = v.en returning 1
)
select count(*) as sets_updated from u;
`;
    await writeFile(join(REPO, "supabase", name), sql, "utf8");
    outFiles.push(name);
  }
  console.log(`Wrote ${outFiles.length} file(s) for ${ok.length} notes:`);
  for (const f of outFiles) console.log(`  supabase db query --linked -f supabase/${f}`);
}

// ---------------------------------------------------------------------------------------------------

const cmd = process.argv[2];
if (cmd === "export") await exportCmd();
else if (cmd === "import") await importCmd();
else {
  console.error("usage: notes-translation.mjs export [--out <dir>] | import <file.xlsx> [--source <json>]");
  process.exit(1);
}
