-- A community price point must not outlive the thing it came from (privacy review, 2026-09-20).
--
-- Before: deleting a set / minifig / sale (the app tombstones rows: deleted = true), or clearing its
-- price, left the user's row in set_value_contributions behind. The privacy policy had to say "a price
-- point stays after you delete the item — email us to remove it", which is a poor promise for data that
-- exists only because the user typed a price.
--
-- Now: whenever a collection copy or a sale stops backing a price — it is tombstoned, or its price is
-- cleared — and the user has NO other live priced copy and NO other live priced sale of that same item,
-- their price point for the item is deleted. If something still backs it (a second copy, a sale), the
-- point stays as it was (the next sync of that row re-contributes its own price anyway).
--
-- Server-side on purpose: clients hold no delete privilege on set_value_contributions (Decision 21), and a
-- trigger covers every path at once — single delete, "remove all copies", CSV import (which tombstones the
-- old rows before inserting the new ones), and any future client (iOS).
--
-- Ordering in a sync: the app upserts collection/sales rows FIRST (this trigger may remove the point) and
-- only then calls contribute_value for rows that still have a price — so an edit that changes a price
-- ends with the new point, and an edit that clears it ends with none.
--
-- ⚠️ `supabase migration up` locally; `supabase db push` to prod. The privacy policy / terms wording
-- ("removed when you delete the item or clear its price") goes live with it.

create or replace function public.prune_value_contribution() returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_backed boolean;
begin
    if new.set_id is null and new.fig_num is null then
        return null;
    end if;

    select exists (
               select 1 from public.collection_copies c
               where c.user_id = new.user_id
                 and c.deleted = false
                 and coalesce(c.price_paid, 0) > 0
                 and ((new.set_id is not null and c.set_id = new.set_id)
                      or (new.fig_num is not null and c.fig_num = new.fig_num))
           )
           or exists (
               select 1 from public.sales s
               where s.user_id = new.user_id
                 and s.deleted = false
                 and coalesce(s.sale_price, 0) > 0
                 and ((new.set_id is not null and s.set_id = new.set_id)
                      or (new.fig_num is not null and s.fig_num = new.fig_num))
           )
      into v_backed;

    if not v_backed then
        delete from public.set_value_contributions v
         where v.user_id = new.user_id
           and ((new.set_id is not null and v.set_id = new.set_id)
                or (new.fig_num is not null and v.fig_num = new.fig_num));
    end if;
    return null; -- AFTER trigger
end $$;

revoke all on function public.prune_value_contribution() from public, anon, authenticated;

-- Fire only for rows that have just stopped backing a price (cheap no-op for ordinary edits).
drop trigger if exists collection_copies_prune_contribution on public.collection_copies;
create trigger collection_copies_prune_contribution
    after insert or update of deleted, price_paid on public.collection_copies
    for each row
    when (new.deleted or coalesce(new.price_paid, 0) <= 0)
    execute function public.prune_value_contribution();

drop trigger if exists sales_prune_contribution on public.sales;
create trigger sales_prune_contribution
    after insert or update of deleted, sale_price on public.sales
    for each row
    when (new.deleted or coalesce(new.sale_price, 0) <= 0)
    execute function public.prune_value_contribution();

-- One-time clean-up of price points already orphaned before this migration.
delete from public.set_value_contributions v
 where not exists (
           select 1 from public.collection_copies c
           where c.user_id = v.user_id and c.deleted = false and coalesce(c.price_paid, 0) > 0
             and ((v.set_id is not null and c.set_id = v.set_id) or (v.fig_num is not null and c.fig_num = v.fig_num))
       )
   and not exists (
           select 1 from public.sales s
           where s.user_id = v.user_id and s.deleted = false and coalesce(s.sale_price, 0) > 0
             and ((v.set_id is not null and s.set_id = v.set_id) or (v.fig_num is not null and s.fig_num = v.fig_num))
       );
