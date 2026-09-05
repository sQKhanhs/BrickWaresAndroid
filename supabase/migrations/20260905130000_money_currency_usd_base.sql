-- USD-first money model (code review 2026-09-05). LEGO retail is USD, so USD is the canonical base:
-- catalog retail is stored in USD cents, and each user-entered paid/sale price is recorded in the
-- currency it was typed in (USD cents or whole VND) tagged by a `currency` column, converting FROM
-- that currency for display/aggregation — never baking a stale FX rate at input. VND is only a
-- converted view of USD.
--
-- This adds the `currency` tag to the user-money tables and to value contributions, and teaches the
-- contribute RPC to record it. NOTE: any pre-existing rows here hold the OLD ₫ values and default to
-- 'USD' — a pre-launch reseed/re-sync is expected, so those test rows are discarded, not migrated.

alter table public.collection_copies
    add column if not exists currency text not null default 'USD' check (currency in ('USD', 'VND'));
alter table public.sales
    add column if not exists currency text not null default 'USD' check (currency in ('USD', 'VND'));
alter table public.set_value_contributions
    add column if not exists currency text not null default 'USD' check (currency in ('USD', 'VND'));

-- contribute_value gains p_currency (default 'USD'); one point per user per item still holds. Args are
-- passed by name from the client, so inserting the new param mid-list is safe.
drop function if exists public.contribute_value(bigint, text, numeric, text);

create or replace function public.contribute_value(
    p_set_id   bigint  default null,
    p_fig_num  text    default null,
    p_value    numeric default null,
    p_currency text    default 'USD',
    p_source   text    default 'paid'
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
    if p_currency is null or p_currency not in ('USD', 'VND') then
        p_currency := 'USD';
    end if;
    if p_source is null or p_source not in ('paid', 'sale') then
        p_source := 'paid';
    end if;

    if p_set_id is not null then
        insert into public.set_value_contributions (set_id, user_id, value, currency, item_kind, source, submitted_at)
        values (p_set_id, auth.uid(), p_value, p_currency, 'set', p_source, now())
        on conflict (user_id, set_id) where set_id is not null
        do update set value = excluded.value, currency = excluded.currency, source = excluded.source, submitted_at = excluded.submitted_at;
    else
        insert into public.set_value_contributions (fig_num, user_id, value, currency, item_kind, source, submitted_at)
        values (p_fig_num, auth.uid(), p_value, p_currency, 'minifig', p_source, now())
        on conflict (user_id, fig_num) where fig_num is not null
        do update set value = excluded.value, currency = excluded.currency, source = excluded.source, submitted_at = excluded.submitted_at;
    end if;
end $$;

grant execute on function public.contribute_value(bigint, text, numeric, text, text) to authenticated;
