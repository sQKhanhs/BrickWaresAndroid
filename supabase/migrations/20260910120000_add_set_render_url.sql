-- Authoritative Rebrickable render URL, captured from the Rebrickable API at ingest for shared-number
-- (multi-variant) sets. The app normally reconstructs a set's image from
-- cdn.rebrickable.com/media/sets/<number>-<number_variant>.jpg, which is reliable for single-variant
-- sets but can resolve to the WRONG image for shared numbers (CMF, comic-con exclusives, promo
-- sub-models) when Rebrickable's variant indexing differs from Brickset's number_variant. When set,
-- this column overrides that guess with the exact URL Rebrickable serves for the set; the app derives
-- the card thumbnail from it. Null = reconstruct (the default, and always fine for single-variant sets).
-- Populated by scripts/fetch-catalog.mjs (name-matched, shared-number sets only); read in
-- SupabaseCatalogRepository.toCatalogSet.
alter table public.sets add column if not exists render_url text;
