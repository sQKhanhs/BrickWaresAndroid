-- Self-hosted primary images (forward-compat columns for a future app version).
--
-- Today the app's set RENDER (the built-set studio photo) and the MINIFIG image are hotlinked from
-- Brickset / Rebrickable (sets.image_url / sets.render_url, minifigs.image_url); only the set BOX shot
-- is already re-hosted to our own Cloudflare R2 bucket (sets.box_image_url -> https://img.brickwares.app/...).
-- These two columns let a later harvest pass mirror the render and the minifig image into R2 as well, so
-- the app can eventually serve EVERY catalog image from our own CDN (reliability, no third-party
-- hotlink / rate-limit / terms exposure, edge caching + on-the-fly resize).
--
-- Both are NULL until harvested; the app must keep falling back to the existing source URLs while
-- coverage is built (exactly how box_image_url rolled out). They are populated the same way as
-- box_image_url — a gated pass in scripts/fetch-catalog.mjs uploads each image to R2 once and writes the
-- public URL here. Nullable text; SELECT is already granted on these tables to anon/authenticated
-- (guest catalog browsing), so no extra grant is needed.

alter table public.sets
  add column if not exists render_image_url text;

alter table public.minifigs
  add column if not exists stored_image_url text;

comment on column public.sets.render_image_url is
  'Our R2-hosted studio render of the built set (https://img.brickwares.app/...): the self-hosted counterpart of the Rebrickable/Brickset source in render_url / image_url, parallel to box_image_url (the hosted box shot). NULL until harvested; the app falls back to the source render URL.';

comment on column public.minifigs.stored_image_url is
  'Our R2-hosted minifig image (https://img.brickwares.app/...): the self-hosted counterpart of the source hotlink in image_url. NULL until harvested; the app falls back to image_url.';
