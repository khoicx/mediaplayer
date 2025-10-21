import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinx.serialization)
}

// Load local properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.khoicx.mediaplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.khoicx.mediaplayer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Create build config fields from local.properties
        buildConfigField("String", "API_USER", "\"${localProperties.getProperty("api.user", "")}\"")
        buildConfigField("String", "API_PASS", "\"${localProperties.getProperty("api.pass", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true // Enable BuildConfig generation
    }
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Ktor for networking
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.ktor.client.cio)
    implementation(libs.ktor.ktor.client.content.negotiation)
    implementation(libs.ktor.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.ktor.client.auth)
    implementation(libs.ktor.ktor.client.logging)
    implementation(libs.logback.classic)

}