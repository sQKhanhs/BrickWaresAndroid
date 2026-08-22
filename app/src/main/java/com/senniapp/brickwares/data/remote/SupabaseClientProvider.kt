package com.senniapp.brickwares.data.remote

import com.senniapp.brickwares.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Single app-wide Supabase client, configured from the active flavor's BuildConfig
 * (dev → local Docker Supabase, prod → hosted project). Postgrest is installed for
 * catalog reads; Auth/Storage will be added as those slices land.
 */
object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Postgrest)
        }
    }
}
