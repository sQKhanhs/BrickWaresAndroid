-- Hardening batch — the remaining LOW/MEDIUM items of the 2026-09-16 security audit (handoff §0aq),
-- closed 2026-09-22 (handoff §0ba).
--
--  1. Sanity CHECKs on the user-data tables. RLS keeps users inside their own rows, but a modified client
--     could still write a megabyte "note" or a negative quantity into its OWN rows, which then sync to
--     every device of that user. Bounds mirror the app's input caps (4-digit quantity, 2000-char notes;
--     prices in the row's own minor unit, capped at 1e12 like set_value_contributions).
--  2. Table privileges. Supabase's default privileges for the `postgres` owner hand `anon` and
--     `authenticated` TRUNCATE, REFERENCES, TRIGGER and MAINTAIN on every new public table (and UPDATE on
--     sequences). PostgREST exposes none of them, so nothing was exploitable — but TRUNCATE for `anon`
--     is a loaded gun. Revoked on existing tables AND removed from the defaults so new tables are clean.
--  3. `touch_server_updated_at` pinned to `search_path = ''` like every other function.
--  4. feedback.ip_hash: was unsalted md5 (the IPv4 space brute-forces in minutes, so it was not the
--     pseudonymisation the column claims). Now HMAC-SHA256 with a per-project random pepper kept in
--     Supabase Vault — created HERE with a random value, so the secret never appears in a migration file
--     and nothing has to be pasted into the dashboard. The IP itself is taken from Cloudflare's
--     `cf-connecting-ip` header, or failing that the LAST hop of `x-forwarded-for` (Cloudflare appends
--     the true connecting address; the FIRST hop is whatever the client chose to send).
--  5. Retention: `purge_feedback_identifiers()` (20260920120000) is now scheduled with pg_cron, daily at
--     03:17 UTC, so the 180-day promise in the privacy policy is kept without anyone remembering to.
--
-- NOT done on purpose: `force row level security`. On Supabase the `postgres` role (table owner, and the
-- role every SECURITY DEFINER function here runs as) has BYPASSRLS, so FORCE would change nothing for it
-- — and it would break the owner-run paths (contribute_value, the prune trigger, delete_current_user)
-- on any stack where it does not. Verified: `select rolbypassrls from pg_roles where rolname='postgres'`.
--
-- ⚠️ `supabase migration up` locally; `supabase db push` to prod after a dump. The CHECKs validate
-- existing rows — the migration fails (and rolls back) if prod holds a violating row; fix the row, re-push.

-- ---------------------------------------------------------------------------
-- 1. Sanity constraints on user data
-- ---------------------------------------------------------------------------
alter table public.collection_copies
    add constraint collection_copies_quantity_sane   check (quantity between 0 and 9999),
    add constraint collection_copies_price_paid_sane check (price_paid is null or (price_paid >= 0 and price_paid <= 1000000000000)),
    add constraint collection_copies_notes_len       check (notes is null or char_length(notes) <= 2000);

alter table public.sales
    add constraint sales_quantity_sane   check (quantity between 0 and 9999),
    add constraint sales_price_paid_sane check (price_paid is null or (price_paid >= 0 and price_paid <= 1000000000000)),
    add constraint sales_sale_price_sane check (sale_price >= 0 and sale_price <= 1000000000000),
    add constraint sales_notes_len       check (notes is null or char_length(notes) <= 2000);

-- ---------------------------------------------------------------------------
-- 2. Privileges the API roles never needed
-- ---------------------------------------------------------------------------
revoke truncate, references, trigger, maintain on all tables in schema public from anon, authenticated;
revoke update on all sequences in schema public from anon, authenticated;

alter default privileges for role postgres in schema public
    revoke truncate, references, trigger, maintain on tables from anon, authenticated;
alter default privileges for role postgres in schema public
    revoke update on sequences from anon, authenticated;

-- ---------------------------------------------------------------------------
-- 3. search_path hygiene for the last unpinned function
-- ---------------------------------------------------------------------------
create or replace function public.touch_server_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.server_updated_at = now();
    return new;
end $$;

revoke all on function public.touch_server_updated_at() from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 4. Keyed IP hash for feedback rate limiting
-- ---------------------------------------------------------------------------
-- One random pepper per project, created on first run only (re-running never rotates it).
do $$
begin
    if not exists (select 1 from vault.secrets where name = 'feedback_ip_pepper') then
        perform vault.create_secret(
            encode(extensions.gen_random_bytes(32), 'hex'),
            'feedback_ip_pepper',
            'HMAC key for public.feedback.ip_hash — rate limiting only. Rotating it just resets the per-IP window.'
        );
    end if;
end $$;

create or replace function public.submit_feedback(
    p_message       text,
    p_contact_email text default null,
    p_app_version   text default null,
    p_os_version    text default null,
    p_device        text default null,
    p_locale        text default null,
    p_install_id    text default null,
    p_category      text default 'other'
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid      uuid := auth.uid();
    v_msg      text := btrim(coalesce(p_message, ''));
    v_email    text := nullif(btrim(coalesce(p_contact_email, '')), '');
    v_install  text := nullif(btrim(coalesce(p_install_id, '')), '');
    v_category text := case when p_category in ('bug', 'feature', 'other') then p_category else 'other' end;
    v_headers  json;
    v_xff      text[];
    v_ip       text;
    v_pepper   text;
    v_ip_hash  text;
begin
    if char_length(v_msg) < 5 or char_length(v_msg) > 2000 then
        raise exception 'feedback_invalid' using errcode = 'P0001';
    end if;
    if v_email is not null
       and (char_length(v_email) > 254 or v_email !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$') then
        raise exception 'feedback_invalid' using errcode = 'P0001';
    end if;

    -- Caller IP, trusted headers only: Cloudflare's cf-connecting-ip, else the LAST x-forwarded-for hop
    -- (appended by the proxy in front of us; the first hop is client-controlled). Used ONLY as a keyed
    -- hash for rate limiting; the address itself is never stored.
    begin
        v_headers := current_setting('request.headers', true)::json;
        v_ip := nullif(btrim(coalesce(v_headers ->> 'cf-connecting-ip', '')), '');
        if v_ip is null then
            v_xff := string_to_array(coalesce(v_headers ->> 'x-forwarded-for', ''), ',');
            if coalesce(array_length(v_xff, 1), 0) > 0 then
                v_ip := nullif(btrim(v_xff[array_length(v_xff, 1)]), '');
            end if;
        end if;
    exception when others then
        v_ip := null;
    end;
    if v_ip is not null then
        select s.decrypted_secret into v_pepper
          from vault.decrypted_secrets s
         where s.name = 'feedback_ip_pepper'
         limit 1;
        v_ip_hash := case when v_pepper is null then null
                          else encode(extensions.hmac(v_ip, v_pepper, 'sha256'), 'hex') end;
    end if;

    if v_uid is not null then
        if (select count(*) from public.feedback
             where user_id = v_uid and created_at > now() - interval '1 hour') >= 5
        or (select count(*) from public.feedback
             where user_id = v_uid and created_at > now() - interval '1 day') >= 20 then
            raise exception 'feedback_rate_limited' using errcode = 'P0001';
        end if;
    else
        if v_install is null then
            raise exception 'feedback_invalid' using errcode = 'P0001';
        end if;
        if (select count(*) from public.feedback
             where install_id = v_install and created_at > now() - interval '1 hour') >= 3
        or (select count(*) from public.feedback
             where install_id = v_install and created_at > now() - interval '1 day') >= 10 then
            raise exception 'feedback_rate_limited' using errcode = 'P0001';
        end if;
    end if;
    if v_ip_hash is not null
       and (select count(*) from public.feedback
             where ip_hash = v_ip_hash and created_at > now() - interval '1 hour') >= 20 then
        raise exception 'feedback_rate_limited' using errcode = 'P0001';
    end if;
    if (select count(*) from public.feedback where created_at > now() - interval '1 hour') >= 300 then
        raise exception 'feedback_rate_limited' using errcode = 'P0001';
    end if;

    -- Signed-in: record the account email (ignoring any client-sent value); signed-out keeps the typed one.
    if v_uid is not null then
        select u.email into v_email from auth.users u where u.id = v_uid;
    end if;

    insert into public.feedback
        (user_id, message, contact_email, app_version, os_version, device, locale, install_id, ip_hash, category)
    values
        (v_uid, v_msg, v_email, left(p_app_version, 40), left(p_os_version, 40), left(p_device, 80),
         left(p_locale, 20), left(v_install, 64), v_ip_hash, v_category);
end $$;

revoke all on function public.submit_feedback(text, text, text, text, text, text, text, text) from public;
grant execute on function public.submit_feedback(text, text, text, text, text, text, text, text) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- 5. Scheduled retention clean-up (pg_cron)
-- ---------------------------------------------------------------------------
create extension if not exists pg_cron with schema pg_catalog;
grant usage on schema cron to postgres;

-- Upsert by job name (pg_cron >= 1.4). Runs as postgres in this database.
select cron.schedule(
    'purge-feedback-identifiers',
    '17 3 * * *',
    $$select public.purge_feedback_identifiers();$$
);

-- ---------------------------------------------------------------------------
-- 6. Catalog count views run as the caller (linter 0010 "security definer view", ERROR ×4)
-- ---------------------------------------------------------------------------
-- The four catalog_*_counts views (20260917130000 / 20260917140000) were created without
-- security_invoker, so they read as their owner and the linter flags them permanently red. They read
-- only public.sets / public.set_minifigs, which anon and authenticated may SELECT anyway (public
-- catalog policies), so running them as the caller changes nothing for the app and removes the finding.
alter view public.catalog_theme_counts            set (security_invoker = true);
alter view public.catalog_subtheme_counts         set (security_invoker = true);
alter view public.catalog_minifig_theme_counts    set (security_invoker = true);
alter view public.catalog_minifig_subtheme_counts set (security_invoker = true);
