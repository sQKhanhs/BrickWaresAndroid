-- Base table privileges for the user-data tables. RLS (owner-only, from init_schema) is the security
-- layer, but Postgrest still needs the coarse GRANT — new public tables are NOT auto-exposed in this
-- project (same reason the catalog needed 20260822022835_grant_catalog_read). Without these, an
-- authenticated client gets "permission denied for table ...".

grant select, insert, update, delete on public.collection_copies to authenticated;
grant select, insert, update, delete on public.wishlist_items    to authenticated;
grant select, insert, update, delete on public.sales             to authenticated;

-- Crowdsourced values: public read (aggregated in the app later), owner-only insert (RLS-gated).
grant select on public.set_value_contributions to anon, authenticated;
grant insert on public.set_value_contributions to authenticated;
