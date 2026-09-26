// Brickset getSets rows -> the snake_case rows public.catalog_apply_brickset_sets() takes. A port of
// scripts/fetch-catalog.mjs (variantOf / setRow / priceRows), kept dependency-free so the same code runs
// in the Edge Function and under `node --experimental-strip-types --test`.

export interface BricksetRegionPrice {
  retailPrice?: number | null;
  dateFirstAvailable?: string | null;
  dateLastAvailable?: string | null;
}

export interface BricksetSet {
  setID?: number | null;
  number?: string | null;
  numberVariant?: number | string | null;
  name?: string | null;
  year?: number | null;
  theme?: string | null;
  themeGroup?: string | null;
  subtheme?: string | null;
  category?: string | null;
  pieces?: number | null;
  minifigs?: number | null;
  ageRange?: { min?: number | null } | null;
  released?: boolean | null;
  availability?: string | null;
  image?: { imageURL?: string | null; thumbnailURL?: string | null } | null;
  bricksetURL?: string | null;
  rating?: number | null;
  reviewCount?: number | null;
  extendedData?: { notes?: string | null } | null;
  launchDate?: string | null;
  exitDate?: string | null;
  LEGOCom?: Record<string, BricksetRegionPrice | undefined> | null;
}

export interface PriceRow {
  region: string;
  retail_price: number | null;
  date_first_available: string | null;
  date_last_available: string | null;
}

export interface SetRow {
  set_id: number;
  set_number: string;
  number_variant: number;
  name: string | null;
  year: number | null;
  theme: string | null;
  theme_group: string | null;
  subtheme: string | null;
  category: string | null;
  pieces: number | null;
  minifigs: number | null;
  age_min: number | null;
  released: boolean | null;
  availability: string | null;
  image_url: string | null;
  thumbnail_url: string | null;
  brickset_url: string | null;
  rating: number | null;
  review_count: number | null;
  notes: string | null;
  launch_date: string | null;
  exit_date: string | null;
  prices: PriceRow[];
}

/** Brickset's name for a not-yet-revealed future set (the app hides these — UNREVEALED_NAME there too). */
export const UNREVEALED_NAME = "{?}";

/** The LEGO.com regions the catalog stores prices for (same as the preload). */
export const PRICE_REGIONS = ["US", "UK", "CA", "DE"] as const;

const text = (v: unknown): string | null =>
  v === null || v === undefined || v === "" ? null : String(v);

const num = (v: unknown): number | null =>
  v === null || v === undefined || v === "" || Number.isNaN(Number(v)) ? null : Number(v);

/** ISO datetime -> YYYY-MM-DD (the catalog stores dates, not timestamps). */
const day = (v: unknown): string | null => (v ? String(v).slice(0, 10) : null);

/**
 * Brickset's numberVariant, preserving a real 0 (a series "random pack" is e.g. 42233-0) and defaulting
 * to 1 only when it's genuinely missing — `x || 1` would collide variant 0 with the real variant 1.
 */
export function variantOf(s: BricksetSet): number {
  const v = s.numberVariant;
  if (v === null || v === undefined || v === "") return 1;
  const n = Number(v);
  return Number.isInteger(n) ? n : 1;
}

export function priceRows(s: BricksetSet): PriceRow[] {
  const lego = s.LEGOCom ?? {};
  const rows: PriceRow[] = [];
  for (const region of PRICE_REGIONS) {
    const r = lego[region];
    if (r && (r.retailPrice != null || r.dateFirstAvailable || r.dateLastAvailable)) {
      rows.push({
        region,
        retail_price: num(r.retailPrice),
        date_first_available: day(r.dateFirstAvailable),
        date_last_available: day(r.dateLastAvailable),
      });
    }
  }
  return rows;
}

/** One Brickset set -> one catalog row, or null when it lacks the identity fields. */
export function toSetRow(s: BricksetSet): SetRow | null {
  const setId = num(s.setID);
  const number = text(s.number);
  if (setId === null || number === null) return null;
  return {
    set_id: setId,
    set_number: number,
    number_variant: variantOf(s),
    name: text(s.name),
    year: num(s.year),
    theme: text(s.theme),
    theme_group: text(s.themeGroup),
    subtheme: text(s.subtheme),
    category: text(s.category),
    pieces: num(s.pieces),
    minifigs: num(s.minifigs),
    age_min: num(s.ageRange?.min),
    released: typeof s.released === "boolean" ? s.released : null,
    availability: text(s.availability),
    image_url: text(s.image?.imageURL),
    thumbnail_url: text(s.image?.thumbnailURL),
    brickset_url: text(s.bricksetURL),
    rating: num(s.rating),
    review_count: num(s.reviewCount),
    notes: text(s.extendedData?.notes),
    launch_date: day(s.launchDate),
    exit_date: day(s.exitDate),
    prices: priceRows(s),
  };
}

/** Transform + dedupe by set_id (a later duplicate wins), dropping rows without an identity. */
export function toSetRows(sets: BricksetSet[]): SetRow[] {
  const byId = new Map<number, SetRow>();
  for (const s of sets) {
    const row = toSetRow(s);
    if (row) byId.set(row.set_id, row);
  }
  return [...byId.values()];
}

/** Split into batches of at most [size]. */
export function chunk<T>(items: T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size));
  return out;
}

/** Name key for matching a Brickset row to its Rebrickable set (same rule as the preload). */
export const normName = (x: unknown): string => String(x ?? "").toLowerCase().replace(/[^a-z0-9]/g, "");
