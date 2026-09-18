import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Firebase (Crashlytics + Analytics, Arch Decision 13) is OPTIONAL until the console config exists: the
// google-services plugin fails the build without app/google-services.json, so both plugins are applied
// only once it's there. The JSON must register BOTH app ids — the dev flavor's com.senniapp.brickwares.dev
// and prod's com.senniapp.brickwares — or the flavor without one fails. Runtime use is guarded the same
// way (util/Observability.kt), so a build without the file still logs (Timber) and just skips Firebase.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
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

// Release signing (the Play UPLOAD key — Play App Signing re-signs with the real app key). All four
// values come from local.properties (gitignored) or same-named environment variables, never source:
//   BRICKWARES_KEYSTORE_FILE=C:/Users/<you>/keys/brickwares-upload.jks   (absolute, or relative to app/)
//   BRICKWARES_KEYSTORE_PASSWORD=…
//   BRICKWARES_KEY_ALIAS=upload
//   BRICKWARES_KEY_PASSWORD=…
// Create it once with keytool (choose your own passwords; back the .jks up OUTSIDE the repo — losing it
// means a new upload key via Play Console support):
//   keytool -genkeypair -v -keystore brickwares-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
// When these are NOT set, prodRelease stays UNSIGNED (assembles, can't be installed) — never signed with
// the debug key by accident — while devRelease falls back to the debug key so an R8 build can be Run.
fun secret(name: String): String? =
    localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
val releaseKeystoreFile: String? = secret("BRICKWARES_KEYSTORE_FILE")
val hasReleaseKeystore = releaseKeystoreFile != null &&
    secret("BRICKWARES_KEYSTORE_PASSWORD") != null &&
    secret("BRICKWARES_KEY_ALIAS") != null &&
    secret("BRICKWARES_KEY_PASSWORD") != null
// Fail at configuration time with the actual cause instead of AGP's late "Keystore file … not found".
// The classic mistake: backslashes in local.properties are ESCAPES (`C:\Users` reads as `C:Users`, a
// relative path under app/). Use forward slashes: C:/Users/<you>/…/brickwares-upload.jks
if (hasReleaseKeystore && !file(releaseKeystoreFile!!).isFile) {
    throw GradleException(
        "BRICKWARES_KEYSTORE_FILE points to '${file(releaseKeystoreFile)}' which does not exist. " +
            "Use an absolute path with FORWARD slashes in local.properties (backslashes are escape " +
            "characters there), e.g. C:/Users/<you>/…/brickwares-upload.jks, and make sure the file " +
            "name ends in .jks.",
    )
}

// Cloudflare Turnstile SITE key (PUBLIC — it ships in the APK; the matching secret lives only in the
// Supabase dashboard / supabase/.env). Prod = the brickwares.app "BrickWares" widget, Managed mode.
// Dev = blank → the in-app gate is skipped (local captcha is off and the emulator can't reach
// challenges.cloudflare.com). Override for EITHER flavor with BRICKWARES_TURNSTILE_SITE_KEY in
// local.properties/env — e.g. Cloudflare's test keys: 1x00000000000000000000AA always passes invisibly,
// 3x00000000000000000000FF always forces the checkbox, 2x00000000000000000000AB always fails.
val turnstileSiteKeyOverride: String? = secret("BRICKWARES_TURNSTILE_SITE_KEY")
val prodTurnstileSiteKey = "0x4AAAAAAE4T5dPOi1S4zDrZ"

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

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseKeystoreFile!!)
                storePassword = secret("BRICKWARES_KEYSTORE_PASSWORD")
                keyAlias = secret("BRICKWARES_KEY_ALIAS")
                keyPassword = secret("BRICKWARES_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Signing: the upload key when configured (see the BRICKWARES_KEYSTORE_* block above). A build
            // type's signingConfig wins over a flavor's, so with the keystore present BOTH release variants
            // use it; without it this stays null and the dev flavor's debug-key fallback (below) applies —
            // prodRelease is then unsigned on purpose.
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else null
            // R8: shrink + optimize + obfuscate code, and shrink resources. Project keep rules live in
            // src/main/keepRules/*.keep (AGP 9 picks that source set up automatically); the default
            // Android rules come from proguard-android-optimize.txt. Both release variants get it —
            // devRelease is the R8 smoke test, a debug build is never obfuscated. The Crashlytics plugin
            // uploads mapping.txt on assemble/bundle of release variants by default
            // (mappingFileUploadEnabled = true), so release stack traces stay readable.
            // NOTE: the AGP 9.2 `optimization { enable = true }` DSL is the experimental "gradual R8"
            // mode (needs android.r8.gradual.support) — not the switch for this. Revisit on AGP 9.3+.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
            // devRelease is the R8 smoke test: sign it with the debug key so Android Studio can Run it
            // without an upload keystore on the machine. Only takes effect when the release build type has
            // no signingConfig of its own (no keystore configured) — see buildTypes.release.
            signingConfig = signingConfigs.getByName("debug")
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
            // No captcha locally unless overridden (see turnstileSiteKeyOverride).
            buildConfigField("String", "TURNSTILE_SITE_KEY", "\"${turnstileSiteKeyOverride ?: ""}\"")
        }
        create("prod") {
            dimension = "environment"
            resValue("string", "app_name", "BrickWares")
            buildConfigField("String", "SUPABASE_URL", "\"https://thntvdpsixepidwvrxxj.supabase.co\"")
            // Real prod publishable key is injected from local.properties / env (not committed). Blank →
            // the preProd*Build guard below fails the build instead of shipping a key-less APK.
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"$prodSupabaseAnonKey\"")
            buildConfigField("String", "TURNSTILE_SITE_KEY", "\"${turnstileSiteKeyOverride ?: prodTurnstileSiteKey}\"")
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
    // Observability (Decision 13): Timber logging; Firebase Crashlytics + Analytics (opt-in — see Observability.kt).
    implementation(libs.timber)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
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