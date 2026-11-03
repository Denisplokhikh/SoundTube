plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.localplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.localplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 10203
        versionName = "1.2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Подпись приложения в лаунчере; для сборки «рядом с обычной» переопределяется.
        manifestPlaceholders["appLabel"] = "@string/app_name"

        // youtubedl-android поставляет JNI-библиотеки (ffmpeg/aria2c) только под эти ABI.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // Ключ публичных релизов живёт в GitHub secrets: в CI заданы путь к .jks и пароли
    // через переменные окружения. Локально их нет — тогда release подписывается отладочным
    // ключом, чтобы APK ставился рядом с debug-сборкой без переустановки «с нуля».
    val releaseKeystore = System.getenv("ANDROID_KEYSTORE_PATH")

    signingConfigs {
        create("local") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("upload") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // `gradlew assembleDebug -PsideBySide` — APK, который ставится рядом
            // с обычным приложением: свой applicationId, своя библиотека и свои
            // настройки, release-сборка на телефоне не затрагивается.
            if (project.hasProperty("sideBySide")) {
                applicationIdSuffix = ".debug"
                versionNameSuffix = "-dev"
                manifestPlaceholders["appLabel"] = "SoundTube dev"
            }
        }
        release {
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("local")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        // Нужен для отладочной панели (BuildConfig.DEBUG) до появления настоящего UI.
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Требуется youtubedl-android: бинарники ищутся в nativeLibraryDir.
            useLegacyPackaging = true
            pickFirsts += listOf("**/libc++_shared.so")
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    // Схемы Room коммитим: по ним будем писать миграции при синхронизации с десктопом.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // DI
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    // Фоновые задачи
    implementation(libs.androidx.work.runtime.ktx)

    // Сеть и сериализация (LAN-sync, JSON import/export)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Обложки
    implementation(libs.coil.compose)

    // yt-dlp + ffmpeg
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)
    implementation(libs.youtubedl.aria2c)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
