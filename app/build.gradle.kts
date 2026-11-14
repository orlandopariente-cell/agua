import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// versionCode lives in version.properties so publicar.ps1 can bump it.
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionCode = versionProps.getProperty("versionCode").trim().toInt()
// GitHub repo ("user/repo") whose releases the app checks for updates.
val updateRepo = (project.findProperty("aguaUpdateRepo") as String?).orEmpty()

android {
    namespace = "app.agua"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.agua"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = "1.$appVersionCode"
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    buildTypes {
        release {
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
}
