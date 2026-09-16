-- Feedback: store the account email in contact_email for signed-in users (2026-09-16).
--
-- The app hides the email field when signed in (the user is reachable on their account), so those
-- rows had contact_email = null and triage needed a join to auth.users. Populate it from the
-- account so the address shows directly in the feedback table. Signed-out rows keep the typed email.
-- Only this one line changes; the rest is the migration-20260913120000 body, unchanged.
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

    -- Signed-in: record the account email (ignoring any client-sent value); signed-out keeps the typed one.
    if v_uid is not null then
        select u.email into v_email from auth.users u where u.id = v_uid;
    end if;

    insert into public.feedback
        (user_id, message, contact_email, app_version, os_version, device, locale, install_id, ip_hash)
    values
        (v_uid, v_msg, v_email, left(p_app_version, 40), left(p_os_version, 40), left(p_device, 80),
         left(p_locale, 20), left(v_install, 64), v_ip_hash);
end $$;

revoke all on function public.submit_feedback(text, text, text, text, text, text, text) from public;
grant execute on function public.submit_feedback(text, text, text, text, text, text, text) to anon, authenticated;
