import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// İmza bilgileri ortam değişkenlerinden okunur. GÜNCEL adlar: KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
// Geriye uyumluluk için eski KFT_ önekli adlar da kabul edilir (KFT_KEYSTORE_PATH, ...). Tek yerde tanımlı: ad karışıklığı olmasın.
fun signingEnv(name: String): String? =
    (System.getenv(name) ?: System.getenv("KFT_$name"))?.takeIf { it.isNotBlank() }

val keystorePath: String? = signingEnv("KEYSTORE_PATH")

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
            if (keystorePath != null) {
                storeFile = file(requireNotNull(keystorePath))
                storePassword = signingEnv("KEYSTORE_PASSWORD")
                keyAlias = signingEnv("KEY_ALIAS")
                keyPassword = signingEnv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("shared")
        }
        release {
            // R8 küçültme varsayılan açık; sorun çıkarsa -Pkft.minify=false ile kapatılabilir.
            val minify = (project.findProperty("kft.minify") as String?) != "false"
            isMinifyEnabled = minify
            isShrinkResources = minify
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("shared")
        }
    }

    // Lint raporu üretilir ama release derlemesini engellemez (ayrıntı: app/build/reports).
    lint {
        abortOnError = false
        checkReleaseBuilds = false
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

// ======================================================================
// GEÇİŞLİ BAĞIMLILIK SABİTLEME
// Amaç: libs.versions.toml'de doğrudan tanımlı olmayan, ancak Hilt/Navigation/
// Compose gibi kütüphanelerin transitif olarak çektiği YENİ AndroidX sürümlerini,
// AGP 8.7.3 + compileSdk 35 ile uyumlu sürümlere sabitlemek.
//
// Bu blok OLMADAN: androidx.hilt:hilt-navigation-compose:1.4.0,
// androidx.core:core-ktx:1.18.0, androidx.activity:activity-compose:1.13.0,
// androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0,
// androidx.navigation:navigation-compose-android:2.10.2 gibi transitif sürümler
// checkDebugAarMetadata hatası verir (compileSdk 36/37 ister).
//
// compileSdk + AGP yükseltildiğinde bu kısıtlar KALDIRILABİLİR.
// ======================================================================
configurations.all {
    resolutionStrategy {
        // Core / Activity / Lifecycle: compileSdk 35 uyumlu son sürümler
        force("androidx.core:core:1.15.0")
        force("androidx.core:core-ktx:1.15.0")
        force("androidx.activity:activity:1.9.3")
        force("androidx.activity:activity-ktx:1.9.3")
        force("androidx.activity:activity-compose:1.9.3")
        force("androidx.lifecycle:lifecycle-runtime:2.8.7")
        force("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
        force("androidx.lifecycle:lifecycle-runtime-compose-android:2.8.7")
        force("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
        force("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
        force("androidx.lifecycle:lifecycle-viewmodel-compose-android:2.8.7")
        force("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

        // Navigation: 2.8.5 hattı compileSdk 35 uyumlu
        force("androidx.navigation:navigation-common:2.8.5")
        force("androidx.navigation:navigation-common-ktx:2.8.5")
        force("androidx.navigation:navigation-runtime:2.8.5")
        force("androidx.navigation:navigation-runtime-ktx:2.8.5")
        force("androidx.navigation:navigation-compose:2.8.5")
        force("androidx.navigation:navigation-compose-android:2.8.5")

        // navigationevent: 1.1.2 compileSdk 36 ister; 1.0.x hattına sabitle
        force("androidx.navigationevent:navigationevent:1.0.0")
        force("androidx.navigationevent:navigationevent-android:1.0.0")
        force("androidx.navigationevent:navigationevent-compose:1.0.0")
        force("androidx.navigationevent:navigationevent-compose-android:1.0.0")

        // Hilt Navigation Compose: 1.4.0 compileSdk 37 ister; 1.2.0 hattına sabitle
        force("androidx.hilt:hilt-navigation:1.2.0")
        force("androidx.hilt:hilt-navigation-compose:1.2.0")
        force("androidx.hilt:hilt-lifecycle-viewmodel:1.2.0")
        force("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.2.0")
        force("androidx.hilt:hilt-common:1.2.0")
        force("androidx.hilt:hilt-work:1.2.0")
        force("androidx.hilt:hilt-compiler:1.2.0")
    }
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

// Release derlemesi imza bilgisi olmadan sessizce "unsigned" APK üretmesin: açık bir hata ver.
gradle.taskGraph.whenReady {
    val wantsRelease = allTasks.any { t ->
        t.project == project && (t.name == "assembleRelease" || t.name == "bundleRelease" || t.name == "packageRelease")
    }
    if (wantsRelease && keystorePath == null) {
        throw GradleException(
            "Release imzası yok: KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS ve KEY_PASSWORD ortam değişkenleri tanımlı olmalı " +
                "(CI'da secrets'tan .github/scripts/prepare-signing.sh yazar; ayrıntı: README 'İmza anahtarı'). Debug için assembleDebug kullanın."
        )
    }
}