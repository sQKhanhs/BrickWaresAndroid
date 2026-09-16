-- Decision 16: the client moves from a full in-memory catalog to server-side queries. Indexes to make
-- substring search / theme filtering fast at ~23.5k sets, plus a theme-counts view for the browse list.
create extension if not exists pg_trgm;

-- Case-insensitive substring search (ILIKE '%q%') on set number + name — the search bar / suggestions.
create index if not exists sets_name_trgm   on public.sets using gin (name gin_trgm_ops);
create index if not exists sets_number_trgm on public.sets using gin (set_number gin_trgm_ops);
-- Theme filter + ordering (theme browse, theme-detail list, Set Detail "related same-theme").
create index if not exists sets_theme_idx    on public.sets (theme);
create index if not exists sets_theme_sub_idx on public.sets (theme, subtheme);

-- Theme browse list: each theme + how many sets it has. Matches the current in-memory grouping over
-- ALL sets rows (CMF rows, item_type='minifig', included — same as today). Public read like `sets`.
create or replace view public.catalog_theme_counts as
    select theme, count(*)::int as set_count
    from public.sets
    where theme is not null and theme <> ''
    group by theme;

grant select on public.catalog_theme_counts to anon, authenticated;
