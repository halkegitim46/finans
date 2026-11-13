import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.kisisel.finans"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kisisel.finans"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += "tr"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }   // SQLCipher yerel kütüphaneleri
    }

    // Sabit imza anahtarı (CI'da GitHub Secrets'tan gelir). Aynı anahtar = her sürüm eskisinin üstüne veri kaybı olmadan kurulur.
    signingConfigs {
        create("shared") {
            val keystorePath = System.getenv("KFT_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KFT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KFT_KEY_ALIAS")
                keyPassword = System.getenv("KFT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (System.getenv("KFT_KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("shared")
        }
        release {
            // R8 küçültme varsayılan açık; sorun çıkarsa -Pkft.minify=false ile kapatılabilir.
            val minify = (project.findProperty("kft.minify") as String?) != "false"
            isMinifyEnabled = minify
            isShrinkResources = minify
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("KFT_KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }

    buildFeatures { compose = true }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime)
    implementation(libs.datastore)
    implementation(libs.biometric)
    implementation(libs.androidx.fragment)
    implementation("net.zetetic:sqlcipher-android:${libs.versions.sqlcipher.get()}@aar")
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    // Cihaz/emülatör testleri (gerçek SQLCipher ve Android Keystore): "Cihaz testleri" workflow'u veya connectedDebugAndroidTest
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.coroutines.android)
}

// Başarısız testlerin ayrıntısı (hata mesajı ve yığın) CI log'unda görünsün.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = true
    }
}
