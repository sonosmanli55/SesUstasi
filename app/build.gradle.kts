plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI bu değerleri verir: derleme numarası sürüm kodu olur, depo adı güncelleme kaynağıdır.
val buildNo = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
val repoSlug = (project.findProperty("repoSlug") as String?) ?: ""
val keystorePath: String? = System.getenv("KEYSTORE_PATH")
val hasKeystore = keystorePath != null && File(keystorePath).exists()

android {
    namespace = "com.aile.sesustasi"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aile.sesustasi"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNo
        versionName = "1.$buildNo"
        buildConfigField("String", "REPO_SLUG", "\"$repoSlug\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = File(keystorePath!!)
                storeType = "pkcs12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sabit anahtar yoksa debug imzası: kurulur ama sonraki güncellemeler imza uyuşmazlığı verebilir.
            signingConfig = if (hasKeystore) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
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
    testImplementation("junit:junit:4.13.2")
}
