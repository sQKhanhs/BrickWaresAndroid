-- Settings → Send feedback (2026-09-13).
--
-- Free-text feedback from the app lands in public.feedback. The app can only WRITE it through the
-- submit_feedback() RPC below: the table has RLS on with NO policies, so the public anon key can
-- neither read anyone's messages nor insert around the validation + rate limits in the function.
-- The owner reads/triages rows in Studio or SQL (status: new → read → done).
create table public.feedback (
    id            bigint generated always as identity primary key,
    user_id       uuid references auth.users (id) on delete set null,
    message       text not null check (char_length(message) between 5 and 2000),
    contact_email text check (contact_email is null or char_length(contact_email) <= 254),
    app_version   text,
    os_version    text,
    device        text,
    locale        text,
    -- Random id the app generates once per install: the anonymous (signed-out) rate-limit key.
    install_id    text,
    -- md5 of the caller's IP, stamped server-side from the request headers — never the raw IP.
    ip_hash       text,
    status        text not null default 'new' check (status in ('new', 'read', 'done')),
    created_at    timestamptz not null default now()
);
create index feedback_user_created_idx    on public.feedback (user_id, created_at desc);
create index feedback_install_created_idx on public.feedback (install_id, created_at desc);
create index feedback_ip_created_idx      on public.feedback (ip_hash, created_at desc);
create index feedback_created_idx         on public.feedback (created_at desc);
alter table public.feedback enable row level security;
-- Intentionally no policies: clients never touch the table directly.

-- Validates, rate-limits and inserts one feedback row on behalf of the caller (signed in or not).
-- Raises 'feedback_invalid' / 'feedback_rate_limited' (the app maps those to toasts).
--
-- Rate limits — spam protection for the database, not just politeness:
--   signed-in : 5 per hour and 20 per day per account
--   anonymous : 3 per hour and 10 per day per install id (an install id is required when signed out)
--   any       : 20 per hour per IP, and a GLOBAL circuit breaker of 300 per hour across everyone —
--               a flood from many devices/IPs still can't fill the table faster than that.
create or replace function public.submit_feedback(
    p_message       text,
    p_contact_email text default null,
    p_app_version   text default null,
    p_os_version    text default null,
    p_device        text default null,
    p_locale        text default null,
    p_install_id    text default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid     uuid := auth.uid();
    v_msg     text := btrim(coalesce(p_message, ''));
    v_email   text := nullif(btrim(coalesce(p_contact_email, '')), '');
    v_install text := nullif(btrim(coalesce(p_install_id, '')), '');
    v_headers json;
    v_ip      text;
    v_ip_hash text;
begin
    if char_length(v_msg) < 5 or char_length(v_msg) > 2000 then
        raise exception 'feedback_invalid' using errcode = 'P0001';
    end if;
    if v_email is not null
       and (char_length(v_email) > 254 or v_email !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$') then
        raise exception 'feedback_invalid' using errcode = 'P0001';
    end if;

    -- Caller IP: PostgREST exposes the request headers; hashed, and only for rate limiting.
    begin
        v_headers := current_setting('request.headers', true)::json;
        v_ip := split_part(coalesce(v_headers ->> 'cf-connecting-ip', v_headers ->> 'x-forwarded-for', ''), ',', 1);
    exception when others then
        v_ip := null;
    end;
    v_ip_hash := case when nullif(btrim(coalesce(v_ip, '')), '') is null then null else md5(btrim(v_ip)) end;

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

    insert into public.feedback
        (user_id, message, contact_email, app_version, os_version, device, locale, install_id, ip_hash)
    values
        (v_uid, v_msg, v_email, left(p_app_version, 40), left(p_os_version, 40), left(p_device, 80),
         left(p_locale, 20), left(v_install, 64), v_ip_hash);
end $$;

revoke all on function public.submit_feedback(text, text, text, text, text, text, text) from public;
grant execute on function public.submit_feedback(text, text, text, text, text, text, text) to anon, authenticated;
