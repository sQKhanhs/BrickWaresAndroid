-- Brickset extendedData.notes: a short curated note about a set's availability/sourcing
-- (e.g. "[NA] Available from Target and Kohl's", GWP conditions). Present on ~11% of sets —
-- mostly promos / GWP / retailer-exclusives. Shown on the detail page's Pricing card when present.
alter table public.sets add column if not exists notes text;
