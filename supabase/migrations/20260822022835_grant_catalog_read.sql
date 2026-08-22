-- Grant table-level read on the public catalog to the Data API roles.
-- RLS ("catalog is public" SELECT policies) decides WHICH rows are visible, but the
-- role still needs a base GRANT to read the table at all — without this, anon requests
-- fail with "permission denied for table sets" (42501). Kept in a migration (not the
-- cloud "expose new tables" toggle) so local dev and prod behave identically.

grant select on public.sets       to anon, authenticated;
grant select on public.set_prices to anon, authenticated;
