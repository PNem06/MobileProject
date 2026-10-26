
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "com.example.mobileproject"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.mobileproject"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        }
    }
}

// Host-side UI tests do not contact Maps or require the production API key.
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.hostTests.values.forEach { hostTest ->
            hostTest.manifestPlaceholders.put("MAPS_API_KEY", "unit-test-placeholder")
        }
    }
}

// Đặt secrets bên ngoài android
secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "local.defaults.properties"
}


dependencies {

    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    implementation("androidx.recyclerview:recyclerview:1.4.0")

    implementation(
        platform("com.google.firebase:firebase-bom:35.0.0")
    )

    implementation("com.google.android.gms:play-services-maps:19.2.0")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.android.libraries.places:places:4.3.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.core:core-splashscreen:1.2.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.7.2")
    annotationProcessor("androidx.room:room-compiler:2.7.2")

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.16.1")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
