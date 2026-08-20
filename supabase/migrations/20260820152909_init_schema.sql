-- BrickWares initial schema
-- Implements the architecture decisions: Brickset/Rebrickable catalog, crowdsourced
-- current value (separate from price_paid), offline-first sync columns, and RLS.
--
-- Sync model (Decision 10): user-data rows carry a client-generated UUID `id`,
-- an `updated_at` (client-supplied = when the edit happened, even offline -> LWW basis),
-- and a `deleted` tombstone. The `pending`/`dirty` flag lives only in the local Room DB,
-- NOT here. Do NOT add triggers that overwrite `updated_at`, or offline LWW breaks.

-- ---------------------------------------------------------------------------
-- CATALOG (public read; written only by the sync job via service_role, which
-- bypasses RLS). Sets and minifigs share one table via `item_type`.
-- ---------------------------------------------------------------------------

create table public.sets (
    set_id         bigint primary key,             -- Brickset setID (or Rebrickable id for minifigs)
    set_number     text   not null,                -- "43012" / "fig-013993"
    number_variant int    default 1,
    name           text,
    year           int,
    theme          text,
    theme_group    text,
    subtheme       text,
    category       text,
    item_type      text   not null default 'set' check (item_type in ('set', 'minifig')),
    pieces         int,
    minifigs       int,
    age_min        int,
    released       boolean,
    availability   text,
    image_url      text,
    thumbnail_url  text,
    brickset_url   text,
    rating         numeric,
    review_count   int,
    last_synced_at timestamptz default now()
);

create index sets_theme_idx    on public.sets (theme);
create index sets_number_idx   on public.sets (set_number);

-- Retail price is normalized per region so adding a region needs no migration.
create table public.set_prices (
    set_id               bigint not null references public.sets (set_id) on delete cascade,
    region               text   not null,          -- 'VN', 'US', 'UK', 'DE'
    retail_price         numeric,
    date_first_available date,
    date_last_available  date,                      -- null = still in production (retired = not null)
    primary key (set_id, region)
);

-- ---------------------------------------------------------------------------
-- USER DATA (RLS: owner-only). Three row types per Decision 10:
-- collection copies, wishlist items, sold items.
-- ---------------------------------------------------------------------------

-- One row per physical copy (a user may own the same set multiple times, bought
-- at different times/prices). `quantity` collapses identical copies.
create table public.collection_copies (
    id          uuid   primary key default gen_random_uuid(),  -- client-generated in practice
    user_id     uuid   not null references auth.users (id) on delete cascade,
    set_id      bigint not null references public.sets (set_id),
    quantity    int    not null default 1,
    condition   text   check (condition in ('new', 'used')),
    price_paid  numeric,                            -- HISTORICAL purchase price (NOT current value)
    acquired_on date,
    notes       text,
    deleted     boolean     not null default false, -- tombstone for sync
    updated_at  timestamptz not null default now(), -- client-supplied; LWW basis
    created_at  timestamptz not null default now()
);
create index collection_copies_user_idx on public.collection_copies (user_id);

-- Wishlist carries only catalog basics (no priority/target/note per product decision).
create table public.wishlist_items (
    id         uuid   primary key default gen_random_uuid(),
    user_id    uuid   not null references auth.users (id) on delete cascade,
    set_id     bigint not null references public.sets (set_id),
    deleted    boolean     not null default false,
    updated_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);
create index wishlist_items_user_idx on public.wishlist_items (user_id);
-- Only one *live* wishlist row per set; a soft-deleted one can be re-added.
create unique index wishlist_items_user_set_uniq
    on public.wishlist_items (user_id, set_id) where deleted = false;

-- Sold items (Sales mode). price_paid kept as cost basis for profit calc.
create table public.sales (
    id         uuid   primary key default gen_random_uuid(),
    user_id    uuid   not null references auth.users (id) on delete cascade,
    set_id     bigint not null references public.sets (set_id),
    quantity   int    not null default 1,
    condition  text   check (condition in ('new', 'used')),
    price_paid numeric,                             -- original cost basis
    sale_price numeric not null,
    sold_on    date,
    notes      text,
    deleted    boolean     not null default false,
    updated_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);
create index sales_user_idx on public.sales (user_id);

-- ---------------------------------------------------------------------------
-- CROWDSOURCED CURRENT VALUE (Decision 8). Append-only, condition-tagged,
-- separate contribution type from price_paid. Public read (values are shown to
-- everyone, aggregated as a recency-weighted median in the app / a view later).
-- ---------------------------------------------------------------------------

create table public.set_value_contributions (
    id           uuid   primary key default gen_random_uuid(),
    set_id       bigint not null references public.sets (set_id),
    user_id      uuid   not null references auth.users (id) on delete cascade,
    value        numeric not null,                  -- current market estimate
    currency     text    not null default 'VND',
    condition    text    check (condition in ('new_sealed', 'used_complete', 'used_incomplete')),
    submitted_at timestamptz not null default now()
);
create index set_value_contributions_set_idx on public.set_value_contributions (set_id);

-- ---------------------------------------------------------------------------
-- ROW LEVEL SECURITY
-- Explicit here (not relying on the cloud "automatic RLS" toggle) so local dev
-- and prod behave identically. RLS enabled = deny-all until a policy allows.
-- ---------------------------------------------------------------------------

-- Catalog: readable by anyone (incl. logged-out users); no client writes.
alter table public.sets       enable row level security;
alter table public.set_prices enable row level security;

create policy "catalog sets are public"   on public.sets       for select using (true);
create policy "catalog prices are public" on public.set_prices for select using (true);

-- User data: each user sees and edits only their own rows.
alter table public.collection_copies enable row level security;
alter table public.wishlist_items    enable row level security;
alter table public.sales             enable row level security;

create policy "own collection" on public.collection_copies
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own wishlist" on public.wishlist_items
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own sales" on public.sales
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Contributions: anyone may read the crowdsourced values; only owners of the set
-- may submit (anti-gaming, Decision 8). Append-only: no update/delete policy.
alter table public.set_value_contributions enable row level security;

create policy "contributions are public" on public.set_value_contributions
    for select using (true);
create policy "owners may contribute" on public.set_value_contributions
    for insert with check (
        auth.uid() = user_id
        and exists (
            select 1 from public.collection_copies c
            where c.user_id = auth.uid()
              and c.set_id = set_value_contributions.set_id
              and c.deleted = false
        )
    );
