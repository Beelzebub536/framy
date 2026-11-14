plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun prop(name: String): String? = (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }

android {
    namespace = "app.framy"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.framy"
        minSdk = 26
        targetSdk = 35
        versionCode = prop("framyVersionCode")?.toInt() ?: 1
        versionName = prop("framyVersionName") ?: "0.1.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "CHAT_URL", "\"${prop("framyChatUrl") ?: ""}\"")
    }

    signingConfigs {
        // Set framyStoreFile/framyStorePassword/framyKeyAlias/framyKeyPassword in ~/.gradle/gradle.properties
        // (or ORG_GRADLE_PROJECT_* env vars in CI). Without them the release APK is built unsigned.
        prop("framyStoreFile")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = prop("framyStorePassword")
                keyAlias = prop("framyKeyAlias")
                keyPassword = prop("framyKeyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint { abortOnError = true }
    packaging { resources.excludes += setOf("META-INF/LICENSE.md", "META-INF/NOTICE.md", "META-INF/mimetypes.default", "META-INF/mailcap.default", "META-INF/javamail.*") }
}

kotlin { jvmToolchain(17) }

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.8")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    androidTestImplementation(bom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
