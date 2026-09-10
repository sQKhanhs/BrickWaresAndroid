-- Test seed: LEGO 71050 "Minifigure Series / Spider-Man: Across the Spider-Verse" (2025).
-- A 12-variant collectible-minifigure (CMF) series that all SHARE set number "71050"
-- (71050-1 … 71050-12) — the shared-number / number_variant edge case (like CMF + SDCC
-- exclusives). All retired (released 2025-09, retired 2025-10), $4.99 each, item_type = minifig.
--
-- IMPORTANT — variant order matters: the app builds the Rebrickable image URL from number_variant
-- (cdn.rebrickable.com/media/sets/71050-<v>.jpg), so each name MUST match its variant's real image.
-- The variant→figure order below is Brickset's (via BrickEconomy 2026-09-10), verified against the
-- actual Rebrickable images (e.g. -2 = Spider-Punk with a guitar, -4 = white-hooded Spider-Gwen).
--
-- Synthetic set_ids (710500001-12) sit far above the real Brickset ids (max 53315) so they never
-- collide. Community values are NOT seeded here (separate `set_value_contributions` system).
--
-- Apply (local dev stack):
--   docker exec -i supabase_db_BrickWares psql -U postgres -d postgres < scripts/seed-71050-spiderverse.sql
-- Re-runnable (deletes its own rows first). To REMOVE the test data, run just the DELETE block below.

begin;

-- Idempotent: clear any prior run of this seed (child → parent order).
delete from public.set_minifigs where set_id between 710500001 and 710500012;
delete from public.set_prices  where set_id between 710500001 and 710500012;
delete from public.sets        where set_id between 710500001 and 710500012;
delete from public.minifigs    where fig_num like 'fig-71050-%';

-- Minifigs (one per figure; variant -10 carries two figs — the 2-minifig-grid edge case).
insert into public.minifigs (fig_num, name, num_parts, image_url, last_synced_at) values
  ('fig-71050-01', 'Miles Morales / Spider-Man',            3, null, now()),
  ('fig-71050-02', 'Hobie Brown / Spider-Punk',             4, null, now()),
  ('fig-71050-03', 'Miles G. Morales / Prowler',           10, null, now()),
  ('fig-71050-04', 'Gwen Stacy / Spider-Gwen',              4, null, now()),
  ('fig-71050-05', 'Miguel O''Hara / Spider-Man 2099',      2, null, now()),
  ('fig-71050-06', 'Pavitr Prabhakar / Spider-Man India',   4, null, now()),
  ('fig-71050-07', 'Petra Parker / Cyborg Spider-Woman',    1, null, now()),
  ('fig-71050-08', 'Charlotte Webber / Sun-Spider',         3, null, now()),
  ('fig-71050-09', 'Margo Kess / Spider-Byte',              3, null, now()),
  ('fig-71050-10', 'Peter B. Parker / Spider-Man',          1, null, now()),
  ('fig-71050-10b','May ''Mayday'' Parker',                 1, null, now()),
  ('fig-71050-11', 'Patrick O''Hara / Web-Slinger',         3, null, now()),
  ('fig-71050-12', 'Peter Parker / Werewolf Spider-Man',    1, null, now());

-- Sets: 12 variants sharing set_number '71050'. Retired (launch 2025-09-01, exit 2025-10-31).
-- image_url/thumbnail_url mirror the Rebrickable CDN (the app recomputes these from number+variant
-- anyway); box_image_url is null so cards use the Rebrickable render (CMF has no BrickLink box shot).
insert into public.sets
  (set_id, set_number, number_variant, name, year, theme, subtheme, item_type, pieces, minifigs,
   age_min, released, availability, image_url, thumbnail_url, brickset_url, launch_date, exit_date,
   box_image_url, last_synced_at)
values
  (710500001,'71050', 1,'Miles Morales / Spider-Man',           2025,'Minifigure Series','Spider-Man: Across the Spider-Verse','minifig', 3,1,5,true,'Retired','https://cdn.rebrickable.com/media/sets/71050-1.jpg','https://cdn.rebrickable.com/media/thumbs/sets/71050-1.jpg/320x320p.jpg','https://brickset.com/sets/71050-1','2025-09-01','2025-10-31',null,now()),
  (710500002,'71050', 2,'Hobie Brown / Spider-Punk',            2025,'Minifigure Series','Spider-Man: Across the Spider-Verse','minifig', 4,1,5,true,'Retired','https://cdn.rebrickable.com/media/sets/71050-2.jpg','https://cdn.rebrickable.com/media/thumbs/sets/71050-2.jpg/320x320p.jpg','https://brickset.com/sets/71050-2','2025-09-01','2025-10-31',null,now()),
  (710500003,'71050', 3,'Miles G. Morales / Prowler',           2025,'Minifigure Series','Spider-Man: Across the Spider-Verse','minifig',10,1,5,true,'Retired','https://cdn.rebrickable.com/media/sets/71050-3.jpg','https://cdn.rebrickable.com/media/thumbs/sets/71050-3.jpg/320x320p.jpg','https://brickset.com/sets/71050-3','2025-09-01','2025-10-31',null,now()),
  (710500004,'71050', 4,'Gwen Stacy / Spider-Gwen',             2025,'Minifigure Series','Spider-Man: Across the Spider-Verse','minifig', 4,1,5,true,'Retired','https://cdn.rebrickable.com/media/sets/71050-4.jpg','https://cdn.rebrickable.com/media/thumbs/sets/71050-4.jpg/320x320p.jpg','https://brickset.com/sets/71050-4','2025-09-01','2025-10-31',null,now()),
  (710500005,'71050', 5,'Miguel O''Hara / Spider-Man 2099',     2025,'Minifigure Series','Spider-Man: Across the Spider-Verse','minifig', 2,1,5,true,'Retired','https://cdn.rebrickable.com/media/sets/71050-5.jpg','https://cdn.rebrickable.com/media/thumbs/sets/71050-5.jpg/320x320p.jpg','https://brickset.com/sets/71050-5','2025-09-01','2025-10-31',null,now()),
  (710500006,'71050', 6,'Pavitr Prabhakar / Spider-Man India',  2025,'Minifigure Series','Spider-Man: Across the Spider-Verse','minifig', 4,1,5,true,'Retired','https://cdn.rebrickable.com/media/sets/71050-6.jpg','https://cdn.rebrickable.com/media/thumbs/sets/71050-6.jpg/320x320p.jpg','https://brickset.com/sets/71050-6','2025-09-01','2025-10-31',null,now()),
  (710500007,'71050', 7,'Petra Parker / Cyborg Spider-Woman',   2025,'Minifigure Series','Spider-Man: Across the Spider-Verse','minifig', 1,1,5,true,'Retired','https://cdn.rebrickable.com/media/sets/71050-7.jpg','https://cdn.rebrickable.com/media/thumbs/sets/71050-7.jpg/320x320p.jpg','https://brickset.com/sets/71050-7','2025-09-01','2025-10-31',null,now()),
  (710500008,'71050', 8,'Charlotte Webber / Sun-Spider',        2025,'Minifigure Series','Spider-Man: Across the Spider-Verse','minifig', 3,1,5,true,'Retired','https://cdn.rebrickable.com/media/sets/71050-8.jpg','https://cdn.rebrickable.com/media/thumbs/sets/71050-8.jpg/320x320p.jpg','https://brickset.com/sets/71050-8','2025-09-01','2025-10-31',null,now()),
  (710500009,'71050', 9,'Margo Kess / Spider-Byte',             2025,'Minifigure Series','Spider-Man: Across the Spider-Verse','minifig', 3,1,5,true,'Retired','https://cdn.rebrickable.com/media/sets/71050-9.jpg','https://cdn.rebrickable.com/media/thumbs/sets/71050-9.jpg/320x320p.jpg','https://brickset.com/sets/71050-9','2025-09-01','2025-10-31',null,now()),
  (710500010,'71050',10,'Peter B. Parker / Spider-Man & May ''Mayday'' Parker',2025,'Minifigure Series','Spider-Man: Across the Spider-Verse','minifig', 1,2,5,true,'Retired','https://cdn.rebrickable.com/media/sets/71050-10.jpg','https://cdn.rebrickable.com/media/thumbs/sets/71050-10.jpg/320x320p.jpg','https://brickset.com/sets/71050-10','2025-09-01','2025-10-31',null,now()),
  (710500011,'71050',11,'Patrick O''Hara / Web-Slinger',        2025,'Minifigure Series','Spider-Man: Across the Spider-Verse','minifig', 3,1,5,true,'Retired','https://cdn.rebrickable.com/media/sets/71050-11.jpg','https://cdn.rebrickable.com/media/thumbs/sets/71050-11.jpg/320x320p.jpg','https://brickset.com/sets/71050-11','2025-09-01','2025-10-31',null,now()),
  (710500012,'71050',12,'Peter Parker / Werewolf Spider-Man',   2025,'Minifigure Series','Spider-Man: Across the Spider-Verse','minifig', 1,1,5,true,'Retired','https://cdn.rebrickable.com/media/sets/71050-12.jpg','https://cdn.rebrickable.com/media/thumbs/sets/71050-12.jpg/320x320p.jpg','https://brickset.com/sets/71050-12','2025-09-01','2025-10-31',null,now());

-- Retail price ($4.99, US) with the release/retire window (drives the app's release month + retired status).
insert into public.set_prices (set_id, region, retail_price, date_first_available, date_last_available)
select set_id, 'US', 4.99, date '2025-09-01', date '2025-10-31'
from public.sets where set_id between 710500001 and 710500012;

-- set ↔ minifig join (1 fig each; set -10 has two).
insert into public.set_minifigs (set_id, fig_num, quantity) values
  (710500001,'fig-71050-01',1),
  (710500002,'fig-71050-02',1),
  (710500003,'fig-71050-03',1),
  (710500004,'fig-71050-04',1),
  (710500005,'fig-71050-05',1),
  (710500006,'fig-71050-06',1),
  (710500007,'fig-71050-07',1),
  (710500008,'fig-71050-08',1),
  (710500009,'fig-71050-09',1),
  (710500010,'fig-71050-10',1),
  (710500010,'fig-71050-10b',1),
  (710500011,'fig-71050-11',1),
  (710500012,'fig-71050-12',1);

-- Authoritative Rebrickable render URL (sets.render_url). For this series it equals the app's
-- number+variant reconstruction (71050 is correctly indexed on Rebrickable), but setting it exercises
-- the app's render_url-override path — the fix that protects misindexed multi-variant sets.
update public.sets set render_url = 'https://cdn.rebrickable.com/media/sets/71050-' || number_variant || '.jpg'
where set_id between 710500001 and 710500012;

commit;

-- Sanity check.
select 'sets' t, count(*) n from public.sets where set_id between 710500001 and 710500012
union all select 'set_prices', count(*) from public.set_prices where set_id between 710500001 and 710500012
union all select 'minifigs', count(*) from public.minifigs where fig_num like 'fig-71050-%'
union all select 'set_minifigs', count(*) from public.set_minifigs where set_id between 710500001 and 710500012;
