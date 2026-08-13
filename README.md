# BrickWaresAndroid

Android app for managing a LEGO collection — set details, prices, release/retirement
status, and personal collection statistics. Vietnam-first, with English support.

## Tech
- Kotlin + Jetpack Compose (Material 3), MVVM (ViewModel + StateFlow + immutable UI state)
- Build flavors: `dev` (local Supabase) / `prod`, each × `debug`/`release`
- Backend (planned): Supabase (Postgres, Auth, Edge Functions)

## Status
Early development — Home screen built on mock data; other tabs are placeholders.

## Build
```bash
./gradlew :app:assembleDevDebug
```
