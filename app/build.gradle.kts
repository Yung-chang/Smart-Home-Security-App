import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// Load signing config from local.properties (never commit keystore credentials)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.smarthome.guardian"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smarthome.guardian"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.smarthome.guardian.HiltTestRunner"

        // Expose base URL via BuildConfig so it never lives in source
        buildConfigField("String", "API_BASE_URL", "\"${localProps["API_BASE_URL"] ?: "https://api.smarthome.local/"}\"")
        buildConfigField("String", "MQTT_BROKER_URL", "\"${localProps["MQTT_BROKER_URL"] ?: "ssl://mqtt.smarthome.local:8883"}\"")
        buildConfigField("String", "WS_BASE_URL", "\"${localProps["WS_BASE_URL"] ?: "wss://api.smarthome.local/events"}\"")

        // SQLCipher — passphrase loaded from Android Keystore at runtime, not here
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = localProps["KEYSTORE_FILE"]?.let { file(it) }
            storePassword = localProps["KEYSTORE_PASSWORD"] as? String
            keyAlias = localProps["KEY_ALIAS"] as? String
            keyPassword = localProps["KEY_PASSWORD"] as? String
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            // Never log sensitive data in debug builds
            buildConfigField("Boolean", "ENABLE_SECURITY_LOGS", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "ENABLE_SECURITY_LOGS", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Exclude conflicting META-INF files from MQTT / OkHttp
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
            )
        }
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() } // JUnit 5
        animationsDisabled = true
    }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
    }
}

dependencies {
    // ── Core ────────────────────────────────────────────────────────────────
    implementation(libs.core.ktx)
    implementation(libs.splashscreen)

    // ── Compose ─────────────────────────────────────────────────────────────
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.runtime)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // ── Lifecycle ────────────────────────────────────────────────────────────
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)

    // ── Hilt ─────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    // ── Network ──────────────────────────────────────────────────────────────
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging) // stripped in release via ProGuard

    // ── MQTT ─────────────────────────────────────────────────────────────────
    implementation(libs.paho.client)

    // ── Room + SQLCipher ─────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)

    // ── Security ─────────────────────────────────────────────────────────────
    implementation(libs.security.crypto)
    implementation(libs.biometric)

    // ── Firebase ─────────────────────────────────────────────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    // ── UI extras ────────────────────────────────────────────────────────────
    implementation(libs.coil.compose)
    implementation(libs.vico.compose)

    // ── Root detection ────────────────────────────────────────────────────────
    implementation(libs.rootbeer)

    // ── ExoPlayer / Media3 ───────────────────────────────────────────────────
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.ui)

    // ── QR Code ───────────────────────────────────────────────────────────────
    implementation(libs.zxing.core)

    // ── Paging 3 ──────────────────────────────────────────────────────────────
    implementation(libs.paging3)
    implementation(libs.paging.compose)
    implementation(libs.room.paging)

    // ── Logging ───────────────────────────────────────────────────────────────
    implementation(libs.timber)

    // ── Serialization ─────────────────────────────────────────────────────────
    implementation(libs.kotlinx.serialization.json)

    // ── Unit Tests ────────────────────────────────────────────────────────────
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.coroutines.test)

    // ── Instrumented Tests ────────────────────────────────────────────────────
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.hilt.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.navigation.testing)
}
