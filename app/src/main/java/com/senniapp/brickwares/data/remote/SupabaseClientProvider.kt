package com.senniapp.brickwares.data.remote

import com.senniapp.brickwares.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Single app-wide Supabase client, configured from the active flavor's BuildConfig
 * (dev → local Docker Supabase, prod → hosted project). Postgrest is installed for
 * catalog reads, Auth for Google sign-in (session is persisted + restored automatically
 * on Android via the plugin's default SharedPreferences-backed session manager).
 */
object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
