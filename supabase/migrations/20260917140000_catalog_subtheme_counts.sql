-- Decision 16: the detail-mode theme browse cards show each theme's subtheme chips. Without the full
-- catalog in memory, get them from a bounded view (all theme/subtheme pairs — small vs the 23.5k sets).
-- Loaded once for the browse, like catalog_theme_counts. Empty subtheme ('') = the "no subtheme" group,
-- matching the current in-memory grouping.
create or replace view public.catalog_subtheme_counts as
    select theme, coalesce(subtheme, '') as subtheme, count(*)::int as set_count
    from public.sets
    where theme is not null and theme <> '' and name <> '{?}'
    group by theme, coalesce(subtheme, '');

grant select on public.catalog_subtheme_counts to anon, authenticated;
