-- Account deletion must leave NO personal data behind — feedback was the exception (privacy review,
-- 2026-09-20).
--
-- `feedback.user_id` is `on delete set null` (20260913120000), so a deleted user's feedback rows
-- survived with everything else intact: the ACCOUNT EMAIL (copied into contact_email for signed-in
-- submissions since 20260916120000), the install id, the hashed IP, and the device model / OS / locale.
-- The hosted delete-account page and the privacy policy both promise that deletion removes the user's
-- personal data, so that was simply untrue.
--
-- Fix: delete_current_user() first strips identity from the caller's feedback rows, THEN deletes the
-- auth user (the FK nulls user_id as before). What remains is the message text, its category, the app
-- version and the timestamp — a bug report with nobody attached, which is what we want to keep.
-- The message itself is free text; the policy tells users not to put personal details in it.
--
-- Signed-OUT feedback is not linked to an account and is unaffected (its typed contact email stays
-- until the retention clean-up below).
--
-- Retention: feedback carries identifiers (email, install id, ip hash, device) that are only useful
-- while a report is being handled. `purge_feedback_identifiers()` blanks them on rows older than
-- 180 days; run it from pg_cron or by hand (`select public.purge_feedback_identifiers();`). The
-- privacy policy states the 180-day figure — keep the two in sync.
--
-- ⚠️ `supabase migration up` locally; `supabase db push` to prod (the policy goes live with it).

create or replace function public.delete_current_user() returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then
        raise exception 'must be signed in to delete the account';
    end if;

    -- Detach the person from anything they sent us before the account row goes.
    update public.feedback
       set contact_email = null,
           install_id    = null,
           ip_hash       = null,
           device        = null,
           os_version    = null,
           locale        = null
     where user_id = v_uid;

    delete from auth.users where id = v_uid;
end $$;

revoke all on function public.delete_current_user() from public, anon;
grant execute on function public.delete_current_user() to authenticated;

-- ---------------------------------------------------------------------------
-- Retention clean-up for identifiers on old feedback (signed-in or not). Service-side only.
-- ---------------------------------------------------------------------------
create or replace function public.purge_feedback_identifiers(p_older_than interval default interval '180 days')
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_rows integer;
begin
    update public.feedback
       set contact_email = null,
           install_id    = null,
           ip_hash       = null,
           device        = null,
           os_version    = null,
           locale        = null
     where created_at < now() - p_older_than
       and (contact_email is not null or install_id is not null or ip_hash is not null
            or device is not null or os_version is not null or locale is not null);
    get diagnostics v_rows = row_count;
    return v_rows;
end $$;

revoke all on function public.purge_feedback_identifiers(interval) from public, anon, authenticated;
