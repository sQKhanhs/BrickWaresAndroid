-- Contributions: hide user_id from the public read path and make the RPC the only write path
-- (security audit 2026-09-16 — Arch Decision 21).
--
-- Before:
--   * "contributions are public" (select using (true)) + `grant select ... to anon` exposed EVERY row,
--     user_id included — a per-user "owns / sold X, paid Y" profile dumpable with the publishable key.
--   * The UPDATE policy checked only `auth.uid() = user_id`, and the grant covered every column. One
--     legit contribution (a cheap owned set) could then be PATCHed to ANY set_id / fig_num, any value,
--     and a future submitted_at — the INSERT owner-gate never re-ran, so the anti-gaming design
--     (Decision 8) was not actually enforced. Direct inserts also skipped the RPC's value checks.
--
-- After:
--   * Identity is hidden by COLUMN privileges, not by a policy: anon / authenticated may SELECT every
--     column except id and user_id. Postgres refuses `select *` (and `?select=user_id`) outright for
--     those roles, so no read path can reach user_id — the SELECT policy stays `using (true)` because
--     the rows themselves are meant to be public (Decision 17).
--   * The client reads the view `set_value_points` (security_invoker = true, so it runs with the
--     caller's column privileges) — the same rows minus id / user_id, and `select *` works on it.
--   * Clients get NO insert / update / delete on the table. The only write path is contribute_value,
--     now SECURITY DEFINER: it checks the caller is signed in, validates the value / currency / source,
--     proves ownership (helper `owns_item`: a live collection copy or a sale of that item), and
--     upserts the caller's single row. Nothing a client sends can set user_id, set_id / fig_num of
--     someone else's row, or a future submitted_at — the function stamps all of them itself.
--     (An invoker RPC can't do this under column privileges: ON CONFLICT must read user_id.)
--   * CHECK on value: positive and sane, whichever path writes the row.
--
-- The Supabase linter will list contribute_value under "signed-in users can execute a SECURITY
-- DEFINER function" (0029, WARN) — same as delete_current_user / has_password; intentional.
--
-- Client: ValueContributionRepository reads `set_value_points` instead of the table. The RPC
-- signature and the SyncCoordinator calls are unchanged.
--
-- ⚠️ `supabase migration up` locally; `supabase db push` to prod before shipping the client change
-- (a client on this build 404s on the view against an un-pushed prod and shows no values).

-- ---------------------------------------------------------------------------
-- Ownership helper: does the caller hold (or have sold) this set / fig? Same predicate the INSERT
-- policy carried inline since 20260903130000. Called only from contribute_value (runs as the table
-- owner there), so it is granted to nobody; search_path pinned.
-- ---------------------------------------------------------------------------
create or replace function public.owns_item(p_set_id bigint, p_fig_num text) returns boolean
language sql
stable
security invoker
set search_path = ''
as $$
    select exists (
        select 1 from public.collection_copies c
        where c.user_id = auth.uid()
          and c.deleted = false
          and (
              (p_set_id is not null and c.set_id = p_set_id)
              or (p_fig_num is not null and c.fig_num = p_fig_num)
          )
    )
    or exists (
        select 1 from public.sales s
        where s.user_id = auth.uid()
          and s.deleted = false
          and (
              (p_set_id is not null and s.set_id = p_set_id)
              or (p_fig_num is not null and s.fig_num = p_fig_num)
          )
    );
$$;

revoke all on function public.owns_item(bigint, text) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- The single write path. Same signature as 20260905130000 (named args from the client), now
-- SECURITY DEFINER with the ownership proof inside instead of in an RLS policy.
-- ---------------------------------------------------------------------------
create or replace function public.contribute_value(
    p_set_id   bigint  default null,
    p_fig_num  text    default null,
    p_value    numeric default null,
    p_currency text    default 'USD',
    p_source   text    default 'paid'
) returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then
        raise exception 'must be signed in to contribute a value';
    end if;
    if p_value is null or p_value <= 0 then
        raise exception 'value must be positive';
    end if;
    if (p_set_id is not null) = (p_fig_num is not null) then
        raise exception 'exactly one of p_set_id / p_fig_num is required';
    end if;
    if p_currency is null or p_currency not in ('USD', 'VND') then
        p_currency := 'USD';
    end if;
    if p_source is null or p_source not in ('paid', 'sale') then
        p_source := 'paid';
    end if;
    if not public.owns_item(p_set_id, p_fig_num) then
        raise exception 'you can only contribute a value for an item you own or have sold';
    end if;

    if p_set_id is not null then
        insert into public.set_value_contributions (set_id, user_id, value, currency, item_kind, source, submitted_at)
        values (p_set_id, v_uid, p_value, p_currency, 'set', p_source, now())
        on conflict (user_id, set_id) where set_id is not null
        do update set value = excluded.value, currency = excluded.currency, source = excluded.source, submitted_at = excluded.submitted_at;
    else
        insert into public.set_value_contributions (fig_num, user_id, value, currency, item_kind, source, submitted_at)
        values (p_fig_num, v_uid, p_value, p_currency, 'minifig', p_source, now())
        on conflict (user_id, fig_num) where fig_num is not null
        do update set value = excluded.value, currency = excluded.currency, source = excluded.source, submitted_at = excluded.submitted_at;
    end if;
end $$;

revoke all on function public.contribute_value(bigint, text, numeric, text, text) from public, anon;
grant execute on function public.contribute_value(bigint, text, numeric, text, text) to authenticated;

-- ---------------------------------------------------------------------------
-- Policies: the public SELECT policy from init_schema stays. The INSERT / UPDATE policies are gone
-- with the grants they gated — no client role can write the table directly any more.
-- ---------------------------------------------------------------------------
drop policy if exists "owners may contribute"                on public.set_value_contributions;
drop policy if exists "owners may update their contribution" on public.set_value_contributions;

-- ---------------------------------------------------------------------------
-- Grants: column-level SELECT only (no id / user_id), for everyone. No insert / update / delete.
-- ---------------------------------------------------------------------------
revoke all on public.set_value_contributions from anon, authenticated;
grant select (set_id, fig_num, item_kind, value, currency, source, submitted_at)
    on public.set_value_contributions to anon, authenticated;

-- ---------------------------------------------------------------------------
-- Value sanity at the table level too (the RPC refuses <= 0; this holds for any future write path).
-- The upper bound only rejects garbage/overflow — the aggregator's outlier guard does the real
-- filtering. 1e12 in the row's own minor unit = $10B in USD cents / ~$40M in ₫.
-- ---------------------------------------------------------------------------
alter table public.set_value_contributions
    add constraint set_value_contributions_value_sane
    check (value > 0 and value <= 1000000000000);

-- ---------------------------------------------------------------------------
-- Public read view: every contribution point, no identity. Runs as the caller (their column grants
-- cover exactly these columns), so `select *` on it works where `select *` on the table is refused.
-- ---------------------------------------------------------------------------
create view public.set_value_points
with (security_invoker = true)
as
    select set_id, fig_num, item_kind, value, currency, source, submitted_at
    from public.set_value_contributions;

comment on view public.set_value_points is
    'Crowdsourced value points without user identity (Decision 17/21). The client reads this, never the table.';

grant select on public.set_value_points to anon, authenticated;
