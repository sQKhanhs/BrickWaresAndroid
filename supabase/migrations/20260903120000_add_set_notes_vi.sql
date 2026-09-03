-- Vietnamese translation of sets.notes (Brickset's English availability note). Populated at ingest
-- time by scripts/fetch-catalog.mjs (translate-at-ingestion), shown on the detail page's Pricing card
-- when the app language is Vietnamese; otherwise the original English `notes` is shown.
alter table public.sets add column if not exists notes_vi text;
