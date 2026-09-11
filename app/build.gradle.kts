import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Read secrets from local.properties (gitignored) so keys aren't hardcoded in source.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Prod publishable (anon) key — BRICKWARES_PROD_ANON_KEY=... in local.properties, or an environment
// variable of the same name (CI / another build machine). It is the HOSTED project's key (dashboard →
// Project Settings → API Keys), NOT the local `sb_publishable_ACJW…` default, which only the Docker
// stack accepts. Not a secret (it ships in the APK; RLS is the security layer) — just per-machine.
val prodSupabaseAnonKey: String =
    localProperties.getProperty("BRICKWARES_PROD_ANON_KEY")?.takeIf { it.isNotBlank() }
        ?: System.getenv("BRICKWARES_PROD_ANON_KEY")?.takeIf { it.isNotBlank() }
        ?: ""

// Dev (local Supabase) URL. Defaults to 10.0.2.2 — the Android EMULATOR's alias for the host's
// localhost. To test on a PHYSICAL device on the same Wi-Fi, override with
// BRICKWARES_DEV_SUPABASE_URL=http://<your-PC-LAN-IP>:54321 in local.properties (or env); the phone
// can't reach 10.0.2.2. Requires the local stack to bind 0.0.0.0 (config.toml [api].host) and the PC
// firewall to allow TCP 54321. The emulator works with either value, so leaving it unset is fine.
val devSupabaseUrl: String =
    localProperties.getProperty("BRICKWARES_DEV_SUPABASE_URL")?.takeIf { it.isNotBlank() }
        ?: System.getenv("BRICKWARES_DEV_SUPABASE_URL")?.takeIf { it.isNotBlank() }
        ?: "http://10.0.2.2:54321"

android {
    namespace = "com.senniapp.brickwares"
    compileSdk {
        // core:1.19.0 requires compiling against API 37+. targetSdk stays at 36 (runtime behaviour).
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.senniapp.brickwares"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google "Web" OAuth client id — the serverClientId handed to Credential Manager for native
        // Google sign-in. Shared by both flavors (the app's ID token audience). An OAuth client id is
        // NOT a secret (it ships in the APK and is visible on the wire), so it's committed here; only
        // the client *secret* stays out of git (it lives in Supabase + supabase/.env, never the app).
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"1057688172135-4273me46h3nepbgc9qgt3onul3k55k4r.apps.googleusercontent.com\"",
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    // Environment axis (orthogonal to debug/release). Yields devDebug/devRelease/prodDebug/prodRelease.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            // Distinct package + label so dev and prod can be installed side by side.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "BrickWares Dev")
            // Local Supabase (CLI + Docker). Defaults to the emulator's host alias (10.0.2.2); set
            // BRICKWARES_DEV_SUPABASE_URL in local.properties to the host LAN IP for a physical device.
            buildConfigField("String", "SUPABASE_URL", "\"$devSupabaseUrl\"")
            // Local dev publishable key is a SHARED default (identical on every machine, printed by
            // `supabase start`) — not a secret, safe to hardcode for zero-config dev.
            buildConfigField(
                "String",
                "SUPABASE_ANON_KEY",
                "\"sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH\"",
            )
        }
        create("prod") {
            dimension = "environment"
            resValue("string", "app_name", "BrickWares")
            buildConfigField("String", "SUPABASE_URL", "\"https://thntvdpsixepidwvrxxj.supabase.co\"")
            // Real prod publishable key is injected from local.properties / env (not committed). Blank →
            // the preProd*Build guard below fails the build instead of shipping a key-less APK.
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"$prodSupabaseAnonKey\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

// Fail fast instead of shipping a prod build with no API key: an empty key compiles fine and then EVERY
// Supabase call fails at runtime with "No API key found in request" — sign-in included (cost an hour on
// 2026-09-05). Hooks only the prod variants' preBuild, so a fresh clone still builds devDebug.
if (prodSupabaseAnonKey.isBlank()) {
    val message = "BRICKWARES_PROD_ANON_KEY is not set — add it to local.properties (or export it as an " +
        "environment variable). Prod variants need the HOSTED project's publishable key; without it every " +
        "Supabase request fails at runtime with \"No API key found in request\"."
    tasks.matching { it.name.startsWith("preProd") && it.name.endsWith("Build") }.configureEach {
        doFirst { throw GradleException(message) }
    }
}

// Room writes each @Database version's schema to app/schemas/<db class>/<version>.json at compile
// time (exportSchema = true on BrickWaresDatabase). Commit those files: the JSON is the baseline that
// AutoMigration diffs against and MigrationTestHelper rebuilds the old-version DB from — without v1's
// file, neither can work once v1 is on users' phones. Both flavors emit identical JSON, so one shared
// directory is fine.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    // Lets Coil load remote http(s) images (Brickset catalog images); mock assets were local.
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Chrome Custom Tabs: in-app browser for the Settings Privacy / Terms links.
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    // Retirement alerts: daily background check (WorkManager) + foreground/background detection.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    // Supabase (Postgrest for catalog reads, Auth for Google sign-in) + Ktor engine for Android.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.android)
    // Native Google sign-in: Credential Manager + its Play Services backend + Google ID token parsing.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)
    // Room = offline-first source of truth for user data; DataStore = sync state.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}