-- has_password(): does the CALLING user's account have a password? (2026-09-15)
--
-- Supabase's user object doesn't say whether a password is set, and a Google-only account that
-- later sets one via updateUser() keeps a `google`-only identity list — so the app couldn't tell
-- "Set password" (offer it) from "password already set" (hide it) after a reinstall. This exposes
-- exactly that one bit for the caller only (SECURITY DEFINER read of auth.users.encrypted_password;
-- the hash itself never leaves the DB).
create or replace function public.has_password()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(
        (select u.encrypted_password is not null and u.encrypted_password <> ''
           from auth.users u
          where u.id = auth.uid()),
        false
    );
$$;

revoke all on function public.has_password() from public, anon;
grant execute on function public.has_password() to authenticated;
