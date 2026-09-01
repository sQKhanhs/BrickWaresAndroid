-- Arch Decision 17: crowdsourced current-value engine — paid-price bootstrap.
-- One contribution per user per item, UPSERTED (never stacked). "X contributions" therefore =
-- X distinct users, and a user restating their paid price only moves their single point.
--
-- The table (from init_schema + polymorphic_user_items) already has: public SELECT, an owner-gated
-- INSERT policy, and the set_id XOR fig_num polymorphic shape. This migration adds what the upsert
-- needs: the per-user partial unique indexes (the ON CONFLICT target for each polymorphic branch),
-- an UPDATE policy + grant so an owner can move their own point, and a set/fig-aware RPC.

-- ---------------------------------------------------------------------------
-- One live point per (user, item). Partial indexes so the set branch and the fig branch each get
-- their own unique constraint without the NULL side of the polymorphic reference colliding.
-- ---------------------------------------------------------------------------
create unique index set_value_contributions_user_set_uniq
    on public.set_value_contributions (user_id, set_id) where set_id is not null;
create unique index set_value_contributions_user_fig_uniq
    on public.set_value_contributions (user_id, fig_num) where fig_num is not null;

-- Read path also filters/sorts by fig_num; the set side already has set_value_contributions_set_idx.
create index set_value_contributions_fig_idx on public.set_value_contributions (fig_num);

-- ---------------------------------------------------------------------------
-- Owners may move their own contribution — the DO UPDATE half of the upsert.
-- (INSERT stays gated by the existing "owners may contribute" ownership policy.)
-- ---------------------------------------------------------------------------
create policy "owners may update their contribution" on public.set_value_contributions
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

grant update on public.set_value_contributions to authenticated;

-- ---------------------------------------------------------------------------
-- Set/fig-aware upsert in one round trip. SECURITY INVOKER so RLS still applies: the existing
-- INSERT owner-gate proves the caller owns the item (a matching non-deleted collection_copies row
-- must already be synced), and the UPDATE policy above restricts the DO UPDATE to the caller's own
-- row. The partial-index predicate in ON CONFLICT lets Postgres pick the right unique index.
-- ---------------------------------------------------------------------------
create or replace function public.contribute_value(
    p_set_id  bigint  default null,
    p_fig_num text    default null,
    p_value   numeric default null
) returns void
language plpgsql
security invoker
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'must be signed in to contribute a value';
    end if;
    if p_value is null or p_value <= 0 then
        raise exception 'value must be positive';
    end if;
    if (p_set_id is not null) = (p_fig_num is not null) then
        raise exception 'exactly one of p_set_id / p_fig_num is required';
    end if;

    if p_set_id is not null then
        insert into public.set_value_contributions (set_id, user_id, value, item_kind, submitted_at)
        values (p_set_id, auth.uid(), p_value, 'set', now())
        on conflict (user_id, set_id) where set_id is not null
        do update set value = excluded.value, submitted_at = excluded.submitted_at;
    else
        insert into public.set_value_contributions (fig_num, user_id, value, item_kind, submitted_at)
        values (p_fig_num, auth.uid(), p_value, 'minifig', now())
        on conflict (user_id, fig_num) where fig_num is not null
        do update set value = excluded.value, submitted_at = excluded.submitted_at;
    end if;
end $$;

grant execute on function public.contribute_value(bigint, text, numeric) to authenticated;
