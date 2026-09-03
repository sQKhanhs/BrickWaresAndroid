-- Arch Decision 17: record whether a contributed value came from a PAID price or a realized SALE, so
-- the outlier guard can use a different lower bound per source (a sale of an in-production set can dip
-- a bit lower than a purchase). One point per user per item still holds — the source is the source of
-- that single (last-written) point.
alter table public.set_value_contributions
    add column if not exists source text not null default 'paid'
    check (source in ('paid', 'sale'));

-- Replace the 3-arg RPC with a source-aware version (drop first — adding a param is a new signature).
drop function if exists public.contribute_value(bigint, text, numeric);

create or replace function public.contribute_value(
    p_set_id  bigint  default null,
    p_fig_num text    default null,
    p_value   numeric default null,
    p_source  text    default 'paid'
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
    if p_source is null or p_source not in ('paid', 'sale') then
        p_source := 'paid';
    end if;

    if p_set_id is not null then
        insert into public.set_value_contributions (set_id, user_id, value, item_kind, source, submitted_at)
        values (p_set_id, auth.uid(), p_value, 'set', p_source, now())
        on conflict (user_id, set_id) where set_id is not null
        do update set value = excluded.value, source = excluded.source, submitted_at = excluded.submitted_at;
    else
        insert into public.set_value_contributions (fig_num, user_id, value, item_kind, source, submitted_at)
        values (p_fig_num, auth.uid(), p_value, 'minifig', p_source, now())
        on conflict (user_id, fig_num) where fig_num is not null
        do update set value = excluded.value, source = excluded.source, submitted_at = excluded.submitted_at;
    end if;
end $$;

grant execute on function public.contribute_value(bigint, text, numeric, text) to authenticated;
