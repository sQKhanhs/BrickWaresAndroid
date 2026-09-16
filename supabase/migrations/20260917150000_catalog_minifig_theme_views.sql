-- Decision 16: the minifig browse groups figs by theme, where theme comes from the sets a fig appears
-- in (set_minifigs -> sets). Bounded count views so the browse doesn't need all ~16k minifigs in memory.
-- Blank subtheme becomes 'General', matching the app's current in-memory grouping.
create or replace view public.catalog_minifig_theme_counts as
    select s.theme, count(distinct sm.fig_num)::int as minifig_count
    from public.set_minifigs sm
    join public.sets s on s.set_id = sm.set_id
    where s.theme is not null and s.theme <> ''
    group by s.theme;

create or replace view public.catalog_minifig_subtheme_counts as
    select s.theme,
           coalesce(nullif(s.subtheme, ''), 'General') as subtheme,
           count(distinct sm.fig_num)::int as minifig_count
    from public.set_minifigs sm
    join public.sets s on s.set_id = sm.set_id
    where s.theme is not null and s.theme <> ''
    group by s.theme, coalesce(nullif(s.subtheme, ''), 'General');

grant select on public.catalog_minifig_theme_counts to anon, authenticated;
grant select on public.catalog_minifig_subtheme_counts to anon, authenticated;
