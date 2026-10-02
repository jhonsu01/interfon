import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProps = rootProject.file("../.secrets/keystore.properties")
val signingReady = keystoreProps.exists()

android {
    namespace = "com.jhonsu.interfon"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jhonsu.interfon"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "1.0.3"
    }

    if (signingReady) {
        val kp = Properties().apply { load(keystoreProps.inputStream()) }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(kp.getProperty("storeFile"))
                storePassword = kp.getProperty("storePassword")
                keyAlias = kp.getProperty("keyAlias")
                keyPassword = kp.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (signingReady) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
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
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
