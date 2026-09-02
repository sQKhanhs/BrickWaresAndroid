# BrickWaresAndroid

Android app for managing a LEGO collection — set details, prices, release/retirement
status, and personal collection statistics. Vietnam-first, with English support.

## Tech
- Kotlin + Jetpack Compose (Material 3), MVVM (ViewModel + StateFlow + immutable UI state)
- Build flavors: `dev` (local Supabase) / `prod` (cloud), each × `debug`/`release` — daily dev uses **devDebug**
- Backend: Supabase (Postgres + RLS, Auth incl. Google, Postgrest catalog reads); Room offline-first for user data with two-way sync
- Catalog data from Brickset (sets) + Rebrickable (minifigs), currency-converted to ₫

## Status
Active development. Built: Collection / Wishlist / Search / Set + Minifig detail / Sales, offline-first sync,
email + Google auth, localized (EN/VI), availability badges, and a crowdsourced current-value engine. See the
progress + architecture-decision docs (kept in OneDrive, not in-repo) for the full snapshot.

## Build
```bash
./gradlew :app:assembleDevDebug
```
Local backend: `supabase start`, then generate/apply the seed via `scripts/fetch-catalog.mjs` + `supabase db reset`
(needs `supabase/.env.local` — see `supabase/.env.example`).
