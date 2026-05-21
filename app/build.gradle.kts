import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget




plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/**
 * Reads a secret from the environment first (CI), then from local.properties
 * (developer machines). Never hard-code production secrets in this file or
 * in local.properties checked into git.
 */
fun secret(name: String, fallback: String = ""): String =
    (System.getenv(name)
        ?: localProperties.getProperty(name)
        ?: fallback).trim()

val googleWebClientId: String =
    secret("GOOGLE_WEB_CLIENT_ID")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

val releaseKeystoreFile: String = secret("TYMEBOXED_KEYSTORE_FILE", secret("KEYSTORE_FILE"))
val releaseKeystorePassword: String = secret("TYMEBOXED_KEYSTORE_PASSWORD", secret("KEYSTORE_PASSWORD"))
val releaseKeyAlias: String = secret("TYMEBOXED_KEY_ALIAS", secret("KEY_ALIAS"))
val releaseKeyPassword: String = secret("TYMEBOXED_KEY_PASSWORD", secret("KEY_PASSWORD"))
val hasReleaseSigningCredentials: Boolean =
    releaseKeystoreFile.isNotBlank() &&
        releaseKeystorePassword.isNotBlank() &&
        releaseKeyAlias.isNotBlank() &&
        releaseKeyPassword.isNotBlank() &&
        rootProject.file(releaseKeystoreFile).exists()

val appVersionCode = 3
val appVersionName = "1.1.3"

android {
    namespace = "dev.ambitionsoftware.tymeboxed"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.ambitionsoftware.tymeboxed"
        minSdk = 28
        //noinspection EditedTargetSdkVersion
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField(
            "String",
            "TYMEBOXED_API_BASE_URL",
            "\"https://api.tymeboxed.app\"",
        )
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }


    signingConfigs {
        if (hasReleaseSigningCredentials) {
            create("release") {
                storeFile = rootProject.file(releaseKeystoreFile)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only attach signing config when credentials are actually available
            // (locally via local.properties / env vars, or in CI via env vars).
            // Without this guard the build fails on machines that don't have the keystore.
            if (hasReleaseSigningCredentials) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "Release signing credentials not provided (TYMEBOXED_KEYSTORE_* env vars " +
                        "or KEYSTORE_* in local.properties). The release APK will be unsigned.",
                )
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Informational "newer version available" notices — suppress to keep
        // the lint report focused on actionable issues. Dependency upgrades
        // are handled deliberately, not via lint nag.
        disable += setOf(
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "ObsoleteSdkInt",
            // Informational reminder after manually bumping targetSdk; migration
            // to API 36 was reviewed (AGP 8.11.2, edge-to-edge, specialUse FGS).
            "EditedTargetSdkVersion",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Export Room schema JSONs into app/schemas so each version's schema is
// reviewable in code review and migrations can be written against it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Hilt (DI, compiled via KSP)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room (persistence, compiled via KSP)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (theme + flags)
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Coil
    implementation(libs.coil.compose)

    // Accompanist
    implementation(libs.accompanist.permissions)

    // Gson
    implementation(libs.gson)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Google Sign-In
    implementation(libs.play.services.auth)
}