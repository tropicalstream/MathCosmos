import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Fish Audio config (app/tools/fish_audio.config) baked into BuildConfig so the
// app can synthesize any crew line that has no bundled clip yet.
val fishCfg = Properties().apply {
    val f = file("tools/fish_audio.config")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun fishCfgValue(key: String): String = (fishCfg.getProperty(key) ?: "").trim()

android {
    namespace = "com.rayneo.mathcosmos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rayneo.mathcosmos"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-alpha"

        buildConfigField("String", "FISH_API_KEY", "\"${fishCfgValue("API_KEY")}\"")
        buildConfigField("String", "FISH_MODEL", "\"${fishCfgValue("MODEL")}\"")
        buildConfigField("String", "FISH_VOICE_NAVIGATION", "\"${fishCfgValue("NAVIGATION_VOICE_ID")}\"")
        buildConfigField("String", "FISH_VOICE_SCIENCE", "\"${fishCfgValue("SCIENCE_VOICE_ID")}\"")
        buildConfigField("String", "FISH_VOICE_ENGINEERING", "\"${fishCfgValue("ENGINEERING_VOICE_ID")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Keep the voice + sfx clips uncompressed so they can be copied and played directly.
    androidResources {
        noCompress += listOf("wav", "ogg")
    }
}

// Friendly APK name -> MathCosmos-debug.apk
base {
    archivesName.set("MathCosmos")
}

dependencies {
    // No RayNeo SDK. The two vendor .aar files the sibling projects link were never called from
    // any source file here — the only thing this app needs from the platform is the
    // com.rayneo.mercury.app manifest flag, which tells the compositor to drive both lenses, and
    // that is a string in the manifest rather than a library. Dropping them removes the one part
    // of the tree that could not be redistributed.
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
