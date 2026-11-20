import java.util.Properties

plugins {
    id("com.android.application")
}

// 版本号由 CI 传入：-PversionCode=123 -PversionName=1.0.123
val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName = (project.findProperty("versionName") as String?) ?: "1.0.0-dev"

// 签名：优先读环境变量（GitHub Secrets），否则用仓库内置的 keystore，保证每次发布签名一致、可覆盖安装。
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val signingStoreFile = System.getenv("QILIAN_KEYSTORE_FILE")
    ?: keystoreProps.getProperty("storeFile")?.let { rootProject.file("keystore/$it").absolutePath }
val signingStorePassword = System.getenv("QILIAN_KEYSTORE_PASSWORD") ?: keystoreProps.getProperty("storePassword")
val signingKeyAlias = System.getenv("QILIAN_KEY_ALIAS") ?: keystoreProps.getProperty("keyAlias")
val signingKeyPassword = System.getenv("QILIAN_KEY_PASSWORD") ?: keystoreProps.getProperty("keyPassword")
val hasSigning = signingStoreFile != null && file(signingStoreFile).exists() &&
    signingStorePassword != null && signingKeyAlias != null && signingKeyPassword != null

android {
    namespace = "com.qilian.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.qilian.tv"
        minSdk = 21
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    androidResources {
        // 网页包里有 .wasm / .txt 等，保持原样不压缩，读取更快。
        noCompress += listOf("wasm", "js", "css", "html", "json", "svg", "png", "jpg", "txt", "md", "webmanifest")
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:!CVS:!thumbs.db:!picasa.ini:!*~"
    }
}
