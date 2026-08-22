import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Read secrets from local.properties (gitignored) so keys aren't hardcoded in source.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Prod publishable (anon) key — put BRICKWARES_PROD_ANON_KEY=... in local.properties.
val prodSupabaseAnonKey: String = localProperties.getProperty("BRICKWARES_PROD_ANON_KEY", "")

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
            // Local Supabase (CLI + Docker). 10.0.2.2 is the Android emulator's alias for the
            // host machine's localhost; a physical device would use the host's LAN IP instead.
            buildConfigField("String", "SUPABASE_URL", "\"http://10.0.2.2:54321\"")
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
            // Real prod publishable key is injected from local.properties (not committed).
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    // Supabase (Postgrest for catalog reads) + Ktor engine for Android.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}