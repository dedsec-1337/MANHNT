import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// ---- Build-time configuration: no deployment URLs or secrets hardcoded in Kotlin sources ----
val manhuntLocalProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/** Resolution order: -P<key>, environment variable, android/local.properties. */
fun manhuntConfig(key: String, envKey: String): String? =
    (project.providers.gradleProperty(key).orNull
        ?: System.getenv(envKey)
        ?: manhuntLocalProps.getProperty(key))
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/** Last-resort source: the RTDB URL Firebase itself writes into google-services.json. */
fun manhuntUrlFromGoogleServices(): String? {
    val file = project.file("google-services.json")
    if (!file.exists()) return null
    val parsed = groovy.json.JsonSlurper().parse(file) as? Map<*, *> ?: return null
    return ((parsed["project_info"] as? Map<*, *>)?.get("firebase_url") as? String)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

val manhuntDatabaseUrl: String = run {
    val configured = manhuntConfig("manhunt.firebaseDatabaseUrl", "MANHUNT_FIREBASE_DATABASE_URL")
        ?: manhuntUrlFromGoogleServices()
        ?: throw GradleException(
            "Firebase Realtime Database URL is not configured. Set manhunt.firebaseDatabaseUrl in " +
                "android/local.properties, export MANHUNT_FIREBASE_DATABASE_URL, or add " +
                "app/google-services.json. See android/README.md."
        )
    require(Regex("^https://[A-Za-z0-9.-]+/?\$").matches(configured)) {
        "manhunt.firebaseDatabaseUrl must look like https://<db>.firebaseio.com/ " +
            "or https://<db>-default-rtdb.<region>.firebasedatabase.app/ (got: $configured)"
    }
    if (configured.endsWith("/")) configured else "$configured/"
}

android {
    namespace = "com.manhunt.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.manhunt.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.2.0"

        buildConfigField("String", "FIREBASE_DATABASE_URL", "\"$manhuntDatabaseUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
        }
    }

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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core & Lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Jetpack Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // OSMDroid (OpenStreetMap for Android)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Accompanist Permissions (Location, Background Location, Notifications)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Google Play Services Location (FusedLocationProviderClient)
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // Firebase (Auth for RTDB rules; RTDB still via REST)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth-ktx")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Preferences DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")

    // Retrofit 2 & OkHttp 3 for Firebase Realtime Database REST API
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // QR (local tag proof)
    implementation("com.google.zxing:core:3.5.3")

    // CameraX (Apache-2.0, AOSP): preview + frame analysis for scanning the Hider's QR.
    // Decoding is done locally by ZXing core above. 1.3.x matches compileSdk 34.
    implementation("androidx.camera:camera-core:1.3.3")
    implementation("androidx.camera:camera-camera2:1.3.3")
    implementation("androidx.camera:camera-lifecycle:1.3.3")
    implementation("androidx.camera:camera-view:1.3.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
