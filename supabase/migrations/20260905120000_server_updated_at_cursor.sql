-- Sync pull cursor moves to a SERVER-stamped column (code review 2026-09-05; refines Arch Decision 10).
--
-- `updated_at` is client-supplied — when the edit happened, even offline — and is the LWW basis; per the
-- init_schema note it must NOT be overwritten by a trigger. But the incremental pull also filtered on it,
-- which loses rows: device B edits offline at T1, device A syncs at T2 > T1, B comes online at T3 and
-- pushes the row with updated_at = T1 — A's cursor is already past T1, so A never fetches that row.
--
-- `server_updated_at` is stamped by the DB when a row actually lands (insert or update) and is used
-- ONLY as the pull cursor ("what has this device not fetched yet"); conflicts still resolve on the
-- client `updated_at`. Clients never set it — the BEFORE trigger overwrites whatever they send.

alter table public.collection_copies add column server_updated_at timestamptz;
alter table public.wishlist_items    add column server_updated_at timestamptz;
alter table public.sales             add column server_updated_at timestamptz;

-- Backfill from each row's own updated_at (distinct per row) rather than one shared now(): with a single
-- stamp on every existing row, a page capped by PostgREST max-rows could never resume past it, because
-- the cursor filter is a strict `>`.
update public.collection_copies set server_updated_at = updated_at where server_updated_at is null;
update public.wishlist_items    set server_updated_at = updated_at where server_updated_at is null;
update public.sales             set server_updated_at = updated_at where server_updated_at is null;

alter table public.collection_copies
    alter column server_updated_at set not null,
    alter column server_updated_at set default now();
alter table public.wishlist_items
    alter column server_updated_at set not null,
    alter column server_updated_at set default now();
alter table public.sales
    alter column server_updated_at set not null,
    alter column server_updated_at set default now();

create or replace function public.touch_server_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    new.server_updated_at = now();
    return new;
end $$;

create trigger collection_copies_touch_server_updated_at
    before insert or update on public.collection_copies
    for each row execute function public.touch_server_updated_at();
create trigger wishlist_items_touch_server_updated_at
    before insert or update on public.wishlist_items
    for each row execute function public.touch_server_updated_at();
create trigger sales_touch_server_updated_at
    before insert or update on public.sales
    for each row execute function public.touch_server_updated_at();

-- The pull is "my rows (RLS on user_id) stamped after my cursor", ordered by the stamp.
create index collection_copies_user_server_updated_idx on public.collection_copies (user_id, server_updated_at);
create index wishlist_items_user_server_updated_idx    on public.wishlist_items    (user_id, server_updated_at);
create index sales_user_server_updated_idx             on public.sales             (user_id, server_updated_at);
