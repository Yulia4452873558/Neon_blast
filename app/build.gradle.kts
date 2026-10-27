plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ru.yulia.neonblast"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ru.yulia.neonblast"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("key_store")
            storePassword = "xTf49sjAFFoAbYdJWm1"
            keyAlias = "key_neon_blast"
            keyPassword = "xTf49sjAFFoAbYdJWm1"
        }
    }

    flavorDimensions += "store"
    productFlavors {
        // RuStore build: production ad unit ids of the Yandex Advertising Network.
        // Debug builds ignore them and use demo units (see ads/AdUnits.kt).
        create("rustore") {
            dimension = "store"
            buildConfigField("String", "AD_UNIT_BANNER", "\"R-M-20182160-2\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"R-M-20182160-1\"")
            buildConfigField("String", "AD_UNIT_REWARDED", "\"R-M-20182160-4\"")
            buildConfigField("String", "AD_UNIT_APP_OPEN", "\"R-M-20182160-5\"")
        }
        create("huawei") {
            dimension = "store"
            buildConfigField("String", "AD_UNIT_BANNER", "\"R-M-20239011-2\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"R-M-20239011-3\"")
            buildConfigField("String", "AD_UNIT_REWARDED", "\"R-M-20239011-1\"")
            buildConfigField("String", "AD_UNIT_APP_OPEN", "\"R-M-20239011-4\"")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
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
    implementation(libs.yandex.mobileads)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}