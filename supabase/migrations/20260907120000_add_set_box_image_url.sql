-- Box packaging image, re-hosted at ingest to a Cloudflare R2 bucket (free egress) and served from
-- there. BrickLink has the box art at a deterministic URL but rate-limits img.bricklink.com per IP
-- (403s under the burst a scrolling list makes), Brickset's CDN is Cloudflare-bot-blocked for the app,
-- and Rebrickable's box art has no deterministic URL. So the ingest downloads each set's box once and
-- uploads it to R2, storing the public URL here. Null = no box captured (the app falls back to the
-- Rebrickable render). See scripts/fetch-catalog.mjs (REHOST_BOX_IMAGES / BOX_ONLY).
alter table public.sets add column if not exists box_image_url text;
