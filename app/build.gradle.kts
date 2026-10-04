plugins {
    id("com.android.application")
}

android {
    namespace = "com.huy.gardenclash"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.huy.gardenclash"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
