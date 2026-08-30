-- Brickset set-level launch/exit dates (the "Launch/Exit" shown on brickset.com) — the canonical
-- release + retirement dates. Preferred over per-region LEGOCom date_first/last_available, which can
-- differ: LEGO.com availability often starts ~2 weeks before the official launch (VIP early access),
-- so date_first_available was showing e.g. Aug for a set that officially released in Sep.
alter table public.sets add column if not exists launch_date date;
alter table public.sets add column if not exists exit_date date;
