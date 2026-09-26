-- Daily catalog refresh (Architecture Decision 5, design settled 2026-09-16, built 2026-09-26).
--
-- A pg_cron job pings the `catalog-refresh` Edge Function once a day (19:30 UTC = 02:30 Vietnam). The
-- function pulls Brickset `getSets?updatedSince=` (incremental; ~6 sets/day), upserts sets + prices
-- through catalog_apply_brickset_sets(), fetches minifigs + render pins from Rebrickable for new sets,
-- and emails the owner (Resend) when new sets need the LOCAL enrichment pass (BrickLink box images +
-- Vietnamese notes — neither can run server-side: BrickLink 403-bans servers, MyMemory is quota'd).
--
-- Work queues are nullable timestamps on public.sets, so rows from the preload / seed.sql (all null)
-- never enter a queue and nothing needs backfilling:
--   first_seen_at       set when the refresh job first inserts a set; null = came from the preload/seed.
--   minifigs_queued_at  a Rebrickable minifig fetch is pending since then. Set on insert, and re-set when
--                       Brickset's minifig count changes; cleared once fetched. Given up after 60 days
--                       (Rebrickable lists new sets weeks after Brickset does — so it retries daily).
--   enrich_queued_at    the local box/VI-notes pass is pending since then. Set on insert (except '{?}'
--                       placeholders), when a placeholder is revealed, and when the English note changes
--                       (its translation is dropped). Cleared by the local pass
--                       (scripts/fetch-catalog.mjs ENRICH_PENDING=1).
--
-- Secrets never appear here. After `supabase db push`, create the two Vault entries the cron job reads
-- (until they exist the job is a logged no-op — which is also what keeps the LOCAL stack quiet):
--   select vault.create_secret('https://<ref>.supabase.co/functions/v1/catalog-refresh', 'catalog_refresh_url');
--   select vault.create_secret('<same value as the function secret CATALOG_CRON_SECRET>', 'catalog_refresh_secret');

-- ---------------------------------------------------------------------------
-- 1. pg_net — lets pg_cron make the HTTP call to the Edge Function
-- ---------------------------------------------------------------------------
create extension if not exists pg_net with schema extensions;

-- ---------------------------------------------------------------------------
-- 2. Queue markers on the catalog
-- ---------------------------------------------------------------------------
alter table public.sets
    add column if not exists first_seen_at      timestamptz,
    add column if not exists minifigs_queued_at timestamptz,
    add column if not exists enrich_queued_at   timestamptz;

comment on column public.sets.first_seen_at is
    'When the daily catalog-refresh job first inserted this set. NULL = loaded by the preload / seed.sql.';
comment on column public.sets.minifigs_queued_at is
    'Rebrickable minifig fetch pending since (catalog-refresh). NULL = nothing pending. Given up after 60 days.';
comment on column public.sets.enrich_queued_at is
    'Local enrichment pass (box image + Vietnamese notes) pending since. NULL = nothing pending. Cleared by fetch-catalog.mjs ENRICH_PENDING.';

-- ---------------------------------------------------------------------------
-- 3. Job state (cursor + lock) and a run log — service role only
-- ---------------------------------------------------------------------------
create table if not exists public.catalog_sync_state (
    source              text primary key,
    cursor_date         date not null,          -- next run queries updatedSince = cursor_date - 1 day
    last_success_at     timestamptz,
    running_since       timestamptz,            -- the lock; a crashed run's lock goes stale after 15 min
    last_error_email_at timestamptz             -- failure emails are throttled to one a day
);

-- Seeded to TODAY so the very first run pulls one day of updates, never the whole catalog (that is the
-- local full seed's job).
insert into public.catalog_sync_state (source, cursor_date)
values ('brickset', current_date)
on conflict (source) do nothing;

create table if not exists public.catalog_refresh_runs (
    id          bigint generated always as identity primary key,
    started_at  timestamptz not null default now(),
    finished_at timestamptz,
    ok          boolean,
    summary     jsonb,
    error       text
);

alter table public.catalog_sync_state   enable row level security;
alter table public.catalog_refresh_runs enable row level security;
-- No policies: only the service role (which bypasses RLS) touches these. Belt and braces on the grants
-- Supabase's default privileges hand anon/authenticated on every new public table.
revoke all on public.catalog_sync_state   from anon, authenticated;
revoke all on public.catalog_refresh_runs from anon, authenticated;

-- ---------------------------------------------------------------------------
-- 4. Batch upsert of Brickset rows
-- ---------------------------------------------------------------------------
-- One Brickset set as the Edge Function sends it (snake_case, already transformed; `prices` is an array
-- of {region, retail_price, date_first_available, date_last_available}).
do $$
begin
    if not exists (select 1 from pg_type t join pg_namespace n on n.oid = t.typnamespace
                    where n.nspname = 'public' and t.typname = 'catalog_set_input') then
        create type public.catalog_set_input as (
            set_id bigint, set_number text, number_variant int, name text, year int, theme text,
            theme_group text, subtheme text, category text, pieces int, minifigs int, age_min int,
            released boolean, availability text, image_url text, thumbnail_url text, brickset_url text,
            rating numeric, review_count int, notes text, launch_date date, exit_date date, prices jsonb
        );
    end if;
end $$;

-- Upserts sets + set_prices in one transaction and returns what the email needs. Unlike the old seed
-- upsert it also refreshes number_variant / age_min / availability. It never touches the columns the
-- other passes own (box_image_url, render_url, render_image_url), and keeps notes_vi unless the English
-- note changed.
--
-- Unrevealed sets: Brickset lists future sets as name '{?}' placeholders (the app hides them —
-- SupabaseCatalogRepository.UNREVEALED_NAME — until they're named). A placeholder has nothing to enrich,
-- so it is NOT queued for the local pass on insert; the reveal ('{?}' -> a real name) queues it instead.
-- That also covers the ~250 placeholders the preload loaded, which were never queued.
create or replace function public.catalog_apply_brickset_sets(p_sets jsonb)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
    v_inserted      bigint[];
    v_notes_changed bigint[];
    v_revealed      bigint[];
    v_upserted      int;
begin
    if p_sets is null or jsonb_typeof(p_sets) <> 'array' then
        raise exception 'catalog_apply_brickset_sets: p_sets must be a JSON array';
    end if;

    -- Classify against the pre-upsert state: brand-new sets, named sets whose note now needs a
    -- (re-)translation, and placeholders that just got their real name.
    select coalesce(array_agg(i.set_id) filter (where s.set_id is null), '{}'),
           coalesce(array_agg(i.set_id) filter (where s.set_id is not null
                                                  and s.name is distinct from '{?}'
                                                  and i.name is distinct from '{?}'
                                                  and i.notes is not null
                                                  and s.notes is distinct from i.notes), '{}'),
           coalesce(array_agg(i.set_id) filter (where s.name = '{?}'
                                                  and i.name is not null
                                                  and i.name <> '{?}'), '{}')
      into v_inserted, v_notes_changed, v_revealed
      from (select distinct on (r.set_id) r.*
              from jsonb_populate_recordset(null::public.catalog_set_input, p_sets) r
             where r.set_id is not null and r.set_number is not null
             order by r.set_id) i
      left join public.sets s on s.set_id = i.set_id;

    insert into public.sets as s
        (set_id, set_number, number_variant, name, year, theme, theme_group, subtheme, category, item_type,
         pieces, minifigs, age_min, released, availability, image_url, thumbnail_url, brickset_url, rating,
         review_count, notes, launch_date, exit_date, last_synced_at,
         first_seen_at, minifigs_queued_at, enrich_queued_at)
    select distinct on (r.set_id)
           r.set_id, r.set_number, coalesce(r.number_variant, 1), r.name, r.year, r.theme, r.theme_group,
           r.subtheme, r.category, 'set', r.pieces, r.minifigs, r.age_min, r.released, r.availability,
           r.image_url, r.thumbnail_url, r.brickset_url, r.rating, r.review_count, r.notes, r.launch_date,
           r.exit_date, now(),
           now(),
           case when coalesce(r.minifigs, 0) > 0 then now() end,
           case when r.name is distinct from '{?}' then now() end
      from jsonb_populate_recordset(null::public.catalog_set_input, p_sets) r
     where r.set_id is not null and r.set_number is not null
     order by r.set_id
    on conflict (set_id) do update set
        set_number = excluded.set_number, number_variant = excluded.number_variant, name = excluded.name,
        year = excluded.year, theme = excluded.theme, theme_group = excluded.theme_group,
        subtheme = excluded.subtheme, category = excluded.category, pieces = excluded.pieces,
        minifigs = excluded.minifigs, age_min = excluded.age_min, released = excluded.released,
        availability = excluded.availability, image_url = excluded.image_url,
        thumbnail_url = excluded.thumbnail_url, brickset_url = excluded.brickset_url,
        rating = excluded.rating, review_count = excluded.review_count, notes = excluded.notes,
        launch_date = excluded.launch_date, exit_date = excluded.exit_date, last_synced_at = now(),
        -- A changed English note invalidates its translation: drop it. Queue the local pass when the set
        -- was just revealed, or has a new note to translate — but never while it is still a placeholder.
        notes_vi = case when s.notes is distinct from excluded.notes then null else s.notes_vi end,
        enrich_queued_at = case
            when excluded.name = '{?}' then s.enrich_queued_at
            when s.name = '{?}' then now()
            when s.notes is distinct from excluded.notes and excluded.notes is not null then now()
            else s.enrich_queued_at end,
        -- A changed minifig count (e.g. 0 -> 4 once Brickset fills it in) re-queues the Rebrickable fetch
        -- with a fresh 60-day window.
        minifigs_queued_at = case when s.minifigs is distinct from excluded.minifigs
                                       and coalesce(excluded.minifigs, 0) > 0
                                  then now() else s.minifigs_queued_at end;
    get diagnostics v_upserted = row_count;

    insert into public.set_prices as p (set_id, region, retail_price, date_first_available, date_last_available)
    select distinct on (r.set_id, x.region)
           r.set_id, x.region, x.retail_price, x.date_first_available, x.date_last_available
      from jsonb_populate_recordset(null::public.catalog_set_input, p_sets) r
     cross join lateral jsonb_to_recordset(coalesce(r.prices, '[]'::jsonb))
           as x(region text, retail_price numeric, date_first_available date, date_last_available date)
     where r.set_id is not null and r.set_number is not null and x.region is not null
     order by r.set_id, x.region
    on conflict (set_id, region) do update set
        retail_price         = excluded.retail_price,
        date_first_available = excluded.date_first_available,
        date_last_available  = excluded.date_last_available;

    return jsonb_build_object(
        'upserted',      v_upserted,
        'inserted',      to_jsonb(v_inserted),
        'notes_changed', to_jsonb(v_notes_changed),
        'revealed',      to_jsonb(v_revealed)
    );
end $$;

-- A set's minifig list from Rebrickable (authoritative: links it no longer lists are dropped). An empty
-- list is ignored so a transient miss never wipes a set's figures; the set simply stays queued.
create or replace function public.catalog_apply_set_minifigs(p_set_id bigint, p_figs jsonb)
returns int
language plpgsql
security invoker
set search_path = ''
as $$
declare
    v_links int;
begin
    if p_figs is null or jsonb_typeof(p_figs) <> 'array' or jsonb_array_length(p_figs) = 0 then
        return 0;
    end if;

    insert into public.minifigs as m (fig_num, name, image_url, last_synced_at)
    select distinct on (f.fig_num) f.fig_num, f.name, f.image_url, now()
      from jsonb_to_recordset(p_figs) as f(fig_num text, name text, image_url text)
     where f.fig_num is not null
     order by f.fig_num
    on conflict (fig_num) do update set
        name           = coalesce(excluded.name, m.name),
        image_url      = coalesce(excluded.image_url, m.image_url),
        last_synced_at = now();

    delete from public.set_minifigs sm
     where sm.set_id = p_set_id
       and sm.fig_num not in (select f.fig_num from jsonb_to_recordset(p_figs) as f(fig_num text)
                               where f.fig_num is not null);

    insert into public.set_minifigs as sm (set_id, fig_num, quantity)
    select p_set_id, f.fig_num, max(greatest(coalesce(f.quantity, 1), 1))
      from jsonb_to_recordset(p_figs) as f(fig_num text, quantity int)
     where f.fig_num is not null
     group by f.fig_num
    on conflict (set_id, fig_num) do update set quantity = excluded.quantity;
    get diagnostics v_links = row_count;

    update public.sets s set minifigs_queued_at = null where s.set_id = p_set_id;
    return v_links;
end $$;

-- ---------------------------------------------------------------------------
-- 5. Run bookkeeping: take the lock + open a run row / close it + move the cursor
-- ---------------------------------------------------------------------------
-- Returns no row when another run holds a live lock.
create or replace function public.catalog_refresh_begin()
returns table (run_id bigint, cursor_date date, last_success_at timestamptz, last_error_email_at timestamptz)
language plpgsql
security invoker
set search_path = ''
as $$
#variable_conflict use_column
declare
    v_state public.catalog_sync_state;
    v_run   bigint;
begin
    update public.catalog_sync_state s
       set running_since = now()
     where s.source = 'brickset'
       and (s.running_since is null or s.running_since < now() - interval '15 minutes')
    returning s.* into v_state;
    if not found then
        return;
    end if;

    insert into public.catalog_refresh_runs (started_at) values (now()) returning id into v_run;
    return query select v_run, v_state.cursor_date, v_state.last_success_at, v_state.last_error_email_at;
end $$;

-- Always called (success or failure) to release the lock. The cursor only ever moves forward, and only
-- on success — a failed day (Brickset quota, network) is simply re-covered by the next run.
create or replace function public.catalog_refresh_finish(
    p_run_id         bigint,
    p_ok             boolean,
    p_cursor_date    date,
    p_summary        jsonb,
    p_error          text,
    p_error_emailed  boolean
)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
    update public.catalog_refresh_runs r
       set finished_at = now(), ok = p_ok, summary = p_summary, error = left(p_error, 2000)
     where r.id = p_run_id;

    update public.catalog_sync_state s
       set running_since       = null,
           cursor_date         = case when p_ok and p_cursor_date is not null
                                      then greatest(s.cursor_date, p_cursor_date) else s.cursor_date end,
           last_success_at     = case when p_ok then now() else s.last_success_at end,
           last_error_email_at = case when p_error_emailed then now() else s.last_error_email_at end
     where s.source = 'brickset';

    delete from public.catalog_refresh_runs r where r.started_at < now() - interval '180 days';
end $$;

-- ---------------------------------------------------------------------------
-- 6. The cron hook: POST to the Edge Function with the shared secret from Vault
-- ---------------------------------------------------------------------------
-- A no-op (with a notice) until both Vault entries exist, so a fresh local stack never errors. Returns the
-- pg_net request id; the response lands in net._http_response (the function answers 202 immediately and
-- works in the background).
create or replace function public.catalog_refresh_invoke()
returns bigint
language plpgsql
security invoker
set search_path = ''
as $$
declare
    v_url    text;
    v_secret text;
begin
    select s.decrypted_secret into v_url
      from vault.decrypted_secrets s where s.name = 'catalog_refresh_url' limit 1;
    select s.decrypted_secret into v_secret
      from vault.decrypted_secrets s where s.name = 'catalog_refresh_secret' limit 1;
    if v_url is null or v_secret is null then
        raise notice 'catalog-refresh: Vault secrets catalog_refresh_url / catalog_refresh_secret not set — skipped';
        return null;
    end if;

    return net.http_post(
        url                  := v_url,
        body                 := '{}'::jsonb,
        headers              := jsonb_build_object('Content-Type', 'application/json', 'x-cron-secret', v_secret),
        timeout_milliseconds := 10000
    );
end $$;

-- ---------------------------------------------------------------------------
-- 7. Privileges
-- ---------------------------------------------------------------------------
-- The Edge Function works as service_role (the RPCs are SECURITY INVOKER). Grant exactly what it needs,
-- explicitly: prod's (older) default privileges already give service_role full access to public tables,
-- but newer stacks — including the local CLI image — give it none, so without these the job would work on
-- one environment and fail on the other.
grant select, insert, update         on public.sets, public.set_prices, public.minifigs to service_role;
grant select, insert, update, delete on public.set_minifigs                          to service_role;
grant select, update                 on public.catalog_sync_state                    to service_role;
grant select, insert, update, delete on public.catalog_refresh_runs                  to service_role;

-- None of the functions are callable from the Data API roles.
revoke all on function public.catalog_apply_brickset_sets(jsonb)                          from public, anon, authenticated;
revoke all on function public.catalog_apply_set_minifigs(bigint, jsonb)                   from public, anon, authenticated;
revoke all on function public.catalog_refresh_begin()                                     from public, anon, authenticated;
revoke all on function public.catalog_refresh_finish(bigint, boolean, date, jsonb, text, boolean) from public, anon, authenticated;
revoke all on function public.catalog_refresh_invoke()                                    from public, anon, authenticated;

grant execute on function public.catalog_apply_brickset_sets(jsonb)                          to service_role;
grant execute on function public.catalog_apply_set_minifigs(bigint, jsonb)                   to service_role;
grant execute on function public.catalog_refresh_begin()                                     to service_role;
grant execute on function public.catalog_refresh_finish(bigint, boolean, date, jsonb, text, boolean) to service_role;

-- ---------------------------------------------------------------------------
-- 8. Schedule: daily 19:30 UTC (02:30 in Vietnam). Upsert by job name.
-- ---------------------------------------------------------------------------
select cron.schedule(
    'catalog-refresh',
    '30 19 * * *',
    $$select public.catalog_refresh_invoke();$$
);
