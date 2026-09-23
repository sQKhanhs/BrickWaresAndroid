-- Server-side last-write-wins guard (code review 2026-09-23; reinforces Arch Decision 10 / §0w).
--
-- push() upserts dirty rows and the server has NO newer-wins guard on the UPDATE path — only
-- touch_server_updated_at, which just stamps the pull cursor. So a device that made an OFFLINE edit and
-- reconnects LATE can push a row whose client `updated_at` is OLDER than what's already on the server,
-- silently overwriting a newer edit or resurrecting a newer delete (tombstone):
--   tablet edits X at 09:30 (offline) -> phone deletes X at 10:00 (pushes the tombstone) -> tablet
--   reconnects and pushes its 09:30 row, undoing the delete -> X is alive again on the server and every
--   device that pulls it, except the phone which keeps its local delete. X is inconsistent forever.
--
-- This BEFORE UPDATE trigger enforces the same LWW rule the client uses: keep the EXISTING row when the
-- incoming `updated_at` is older. Returning NULL cancels just that row's UPDATE (the stored row is left
-- as-is and server_updated_at is NOT bumped); an equal-or-newer incoming edit proceeds normally. It
-- fires BEFORE the touch_server_updated_at trigger (name sorts first), so a rejected update never bumps
-- the cursor. It only READS updated_at (never overwrites it — see the init_schema note). The client also
-- now pulls BEFORE pushing and lets a newer remote row replace a dirty local one, so both ends resolve
-- the race consistently; this trigger is the server-side backstop for any client/order that pushes stale.

create or replace function public.reject_stale_update()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if new.updated_at < old.updated_at then
        return null; -- incoming edit is older than the stored row -> keep the stored row, skip the update
    end if;
    return new;
end $$;

create trigger collection_copies_reject_stale_update
    before update on public.collection_copies
    for each row execute function public.reject_stale_update();
create trigger wishlist_items_reject_stale_update
    before update on public.wishlist_items
    for each row execute function public.reject_stale_update();
create trigger sales_reject_stale_update
    before update on public.sales
    for each row execute function public.reject_stale_update();
