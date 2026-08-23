-- Polymorphic collectible model (Arch Decision 11).
-- Adds the minifig catalog tables (Rebrickable) + the minifig query cache, and converts the
-- user-data tables so an owned/wanted/sold/valued item can reference EITHER a `sets` row
-- (normal sets AND CMF products, which Brickset catalogs as sets with item_type='minifig')
-- OR a `minifigs` row (in-set figures). `item_kind` is denormalized at add-time so the UI
-- Set/Minifig filter is a trivial WHERE with no join.

-- ---------------------------------------------------------------------------
-- MINIFIG CATALOG (public read; written only by the lazy Rebrickable ingest via service_role).
-- ---------------------------------------------------------------------------

create table public.minifigs (
    fig_num        text primary key,               -- Rebrickable "fig-013993"
    name           text,
    num_parts      int,
    image_url      text,
    last_synced_at timestamptz default now()
);

-- Which minifigs come in which set (the set-detail grid; also the add-to-collection source).
create table public.set_minifigs (
    set_id   bigint not null references public.sets (set_id) on delete cascade,
    fig_num  text   not null references public.minifigs (fig_num) on delete cascade,
    quantity int    not null default 1,
    primary key (set_id, fig_num)
);
create index set_minifigs_fig_idx on public.set_minifigs (fig_num);

-- Short-TTL cache of normalized minifig searches (Rebrickable), used only server-side.
create table public.search_cache (
    query_norm text primary key,                   -- normalized query string
    result_ids text[],                             -- matched fig_nums
    expires_at timestamptz not null
);

-- ---------------------------------------------------------------------------
-- POLYMORPHIC REFERENCE on the user-data tables.
-- set_id XOR fig_num (exactly one), plus a denormalized item_kind for the filter.
-- Tables are empty at this point, so the NOT NULL item_kind default is safe.
-- ---------------------------------------------------------------------------

-- collection_copies
alter table public.collection_copies alter column set_id drop not null;
alter table public.collection_copies add column fig_num text references public.minifigs (fig_num);
alter table public.collection_copies add column item_kind text not null default 'set'
    check (item_kind in ('set', 'minifig'));
alter table public.collection_copies add constraint collection_copies_one_ref
    check ((set_id is not null) <> (fig_num is not null));

-- wishlist_items
alter table public.wishlist_items alter column set_id drop not null;
alter table public.wishlist_items add column fig_num text references public.minifigs (fig_num);
alter table public.wishlist_items add column item_kind text not null default 'set'
    check (item_kind in ('set', 'minifig'));
alter table public.wishlist_items add constraint wishlist_items_one_ref
    check ((set_id is not null) <> (fig_num is not null));
-- Only one *live* wishlist row per referenced item (set OR fig) — replace the set-only unique index.
drop index if exists public.wishlist_items_user_set_uniq;
create unique index wishlist_items_user_set_uniq
    on public.wishlist_items (user_id, set_id) where set_id is not null and deleted = false;
create unique index wishlist_items_user_fig_uniq
    on public.wishlist_items (user_id, fig_num) where fig_num is not null and deleted = false;

-- sales
alter table public.sales alter column set_id drop not null;
alter table public.sales add column fig_num text references public.minifigs (fig_num);
alter table public.sales add column item_kind text not null default 'set'
    check (item_kind in ('set', 'minifig'));
alter table public.sales add constraint sales_one_ref
    check ((set_id is not null) <> (fig_num is not null));

-- set_value_contributions (crowdsourced value applies to minifigs too, Decision 8)
alter table public.set_value_contributions alter column set_id drop not null;
alter table public.set_value_contributions add column fig_num text references public.minifigs (fig_num);
alter table public.set_value_contributions add column item_kind text not null default 'set'
    check (item_kind in ('set', 'minifig'));
alter table public.set_value_contributions add constraint set_value_contributions_one_ref
    check ((set_id is not null) <> (fig_num is not null));

-- ---------------------------------------------------------------------------
-- RLS + GRANTS for the new catalog tables.
-- ---------------------------------------------------------------------------

alter table public.minifigs     enable row level security;
alter table public.set_minifigs enable row level security;
alter table public.search_cache enable row level security;

create policy "catalog minifigs are public"     on public.minifigs     for select using (true);
create policy "catalog set_minifigs are public"  on public.set_minifigs for select using (true);
-- search_cache: no client policy — service_role (ingest) bypasses RLS; clients get nothing.

-- Base grants (RLS still applies on top); mirrors the sets/set_prices grant.
grant select on public.minifigs     to anon, authenticated;
grant select on public.set_minifigs to anon, authenticated;

-- ---------------------------------------------------------------------------
-- Update the owner-gate for contributions to accept ownership of EITHER the set or the fig.
-- ---------------------------------------------------------------------------

drop policy "owners may contribute" on public.set_value_contributions;
create policy "owners may contribute" on public.set_value_contributions
    for insert with check (
        auth.uid() = user_id
        and exists (
            select 1 from public.collection_copies c
            where c.user_id = auth.uid()
              and c.deleted = false
              and (
                  (set_value_contributions.set_id is not null and c.set_id = set_value_contributions.set_id)
                  or (set_value_contributions.fig_num is not null and c.fig_num = set_value_contributions.fig_num)
              )
        )
    );
