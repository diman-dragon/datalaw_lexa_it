import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Версия приложения — из файла version.properties (имя версии растёт на 1 с каждой итерацией).
val versionProps = Properties().apply { file("../version.properties").inputStream().use { load(it) } }
val appVersionName: String = versionProps.getProperty("versionName")

// Android ставит APK поверх установленного, только если подпись та же и versionCode больше.
// CI: номер запуска workflow; локально: -PVERSION_CODE=<больше установленного> или переменная окружения.
val appVersionCode: Int = (System.getenv("VERSION_CODE") ?: System.getenv("GITHUB_RUN_NUMBER") ?: findProperty("VERSION_CODE") as String?)
    ?.toIntOrNull() ?: versionProps.getProperty("versionCode").toInt()

// Откуда читается словарь, пока он не скачан на устройство (ветка `dictionary` публичного репозитория).
val dataUrl: String = (System.getenv("DATA_URL") ?: findProperty("DATA_URL") as String?)
    ?: "https://raw.githubusercontent.com/diman-dragon/datalaw_lexa_it/dictionary/"

android {
    namespace = "app.datalaw.lexa"
    compileSdk = 35

    defaultConfig {
        // другой applicationId — итальянская версия ставится рядом с англо-русской, а не поверх неё
        applicationId = "app.datalaw.lexa.it"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "DATA_URL", "\"$dataUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Постоянный публичный ключ (см. README): обновления ставятся поверх установленной версии.
    signingConfigs {
        create("lexa") {
            storeFile = file("../signing/datalaw_lexa.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "datalaw_lexa"
            keyAlias = System.getenv("KEY_ALIAS") ?: "datalaw_lexa"
            keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD") ?: "datalaw_lexa"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("lexa")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("lexa")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
