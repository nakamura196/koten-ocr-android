import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Read API key from local.properties: OPENROUTER_API_KEY=sk-...
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(FileInputStream(localPropsFile))
}
val openrouterKey: String = localProps.getProperty("OPENROUTER_API_KEY", "")

// Signing for the directly distributed APK ("direct" build). Reads the existing upload-key
// properties (storeFile / storePassword / keyAlias / keyPassword) without copying them here.
// Override the location with -PkeyProperties=/path/to/key.properties.
val keyPropsFile = file(
    (project.findProperty("keyProperties") as String?)
        ?: "${rootDir}/../koten-ocr-flutter/android/key.properties"
)
val keyProps = Properties().apply {
    if (keyPropsFile.exists()) FileInputStream(keyPropsFile).use { load(it) }
}

android {
    namespace = "com.nakamura196.kotenocr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nakamura196.koten_ocr"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "2.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keyPropsFile.exists()) {
            create("upload") {
                storeFile = file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Dev Native KotenOCR")
            buildConfigField("String", "OPENROUTER_API_KEY", "\"$openrouterKey\"")
        }
        release {
            buildConfigField("String", "OPENROUTER_API_KEY", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            // Play AAB is signed with the upload key; Play re-signs with the app signing key.
            signingConfigs.findByName("upload")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // APK handed out directly (GitHub Releases) while the Play listing is still in closed
        // testing. Play re-signs with a Google-held key, so a sideloaded APK could never update
        // to the Play build anyway; a separate application ID lets both coexist without an
        // install conflict. arm64 only to keep the download small.
        create("direct") {
            initWith(getByName("release"))
            applicationIdSuffix = ".direct"
            matchingFallbacks += listOf("release")
            signingConfigs.findByName("upload")?.let { signingConfig = it }
            ndk { abiFilters += listOf("arm64-v8a") }
        }
    }

    // Models live in the install-time asset pack (Play AAB). Plain APK builds (debug, direct)
    // don't include asset packs, so feed the same folder to them as ordinary assets.
    assetPacks += listOf(":model_pack")
    val packAssets = "${rootDir}/model_pack/src/main/assets"
    sourceSets.getByName("debug").assets.srcDir(packAssets)
    sourceSets.maybeCreate("direct").assets.srcDir(packAssets)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Activity & Lifecycle
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")

    // Splash Screen (Android 12+)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ONNX Runtime
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")

    // CameraX
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Document scanner (Google Play services; same page camera as iOS VisionKit)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // DataStore (settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Security (API key storage)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // YAML parsing
    implementation("org.yaml:snakeyaml:2.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM tests (android.jar only has stubs). Test-only.
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
