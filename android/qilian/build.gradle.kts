plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

android {
    namespace = "com.qilian.tv"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.text.ExperimentalTextApi",
        )
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    api(composeBom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.ui:ui-graphics")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.animation:animation")
    api("androidx.compose.material:material-icons-core")
    api("androidx.tv:tv-material:1.0.0")
    api("androidx.activity:activity-compose:1.9.3")
    api("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    api("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    api("androidx.navigation:navigation-compose:2.8.4")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugApi("androidx.compose.ui:ui-tooling")
    api("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
