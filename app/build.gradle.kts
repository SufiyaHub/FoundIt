plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.foundit.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.foundit.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            buildConfigField("Boolean", "WEBVIEW_DEBUGGING", "true")
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "false"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("Boolean", "WEBVIEW_DEBUGGING", "false")
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "true"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-messaging")

    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
}
