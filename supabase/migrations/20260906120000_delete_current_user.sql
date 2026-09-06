-- Self-serve account deletion (right to erasure — the privacy policy promises it). The client only
-- holds the anon key and a user JWT, neither of which can touch auth.users, so deletion goes through a
-- SECURITY DEFINER RPC that removes the CALLER's own auth user. Every user-data table references
-- auth.users (id) ON DELETE CASCADE (init_schema), so collection_copies / wishlist_items / sales /
-- set_value_contributions are removed with it — and auth.identities / sessions / refresh_tokens cascade
-- inside the auth schema. auth.uid() scopes it to the caller: a user can only ever delete themselves.
--
-- ⚠️ Apply locally with `supabase db reset` (or `supabase migration up`); run `supabase db push`
-- against prod before a prod build calls it, or the RPC 404s and deletion fails (non-fatally).

create or replace function public.delete_current_user() returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception 'must be signed in to delete the account';
    end if;
    delete from auth.users where id = auth.uid();
end $$;

-- Signed-in callers only — never anon/public.
revoke all on function public.delete_current_user() from public, anon;
grant execute on function public.delete_current_user() to authenticated;
