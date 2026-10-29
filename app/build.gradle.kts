import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

// Reutiliza el .env de SyncPass-Web si existe, para no duplicar credenciales.
val webEnv = Properties().apply {
    listOf("../SyncPass-Web/.env", "../SyncPass-Web/.env.local").forEach { path ->
        val file = rootProject.file(path)
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }
}

fun supabaseValue(key: String): String {
    val keys = buildList {
        add(key)
        add("VITE_$key")
        add("NEXT_PUBLIC_$key")
        if (key == "SUPABASE_ANON_KEY") add("SUPABASE_PUBLISHABLE_KEY")
    }
    val value = keys.firstNotNullOfOrNull { candidate ->
        localProperties.getProperty(candidate)
            ?: webEnv.getProperty(candidate)
            ?: System.getenv(candidate)
    }
    return (value ?: "").trim().trim('"')
}

android {
    namespace = "cl.kcorp.syncpass"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cl.kcorp.syncpass"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${supabaseValue("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseValue("SUPABASE_ANON_KEY")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")

    // Sincronización en segundo plano de la cola offline
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // SDK principal de AWS para Android y Cognito Identity Provider
    implementation("com.amazonaws:aws-android-sdk-core:2.77.0")
    implementation("com.amazonaws:aws-android-sdk-cognitoidentityprovider:2.77.0")

    // Retrofit & Gson (para hablar con EC2)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Room Database (Persistencia Local para Offline-First)
    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Red y JSON para consumir la API REST de Supabase
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Cámara y lectura de códigos QR
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Generación de códigos QR
    implementation("com.google.zxing:core:3.5.3")
}