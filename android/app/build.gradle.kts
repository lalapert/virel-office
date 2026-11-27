plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 웹 UI는 레포 루트의 index.html · assets/ · data/ 를 그대로 앱에 넣는다(웹판과 같은 파일 하나).
val webRoot = rootProject.projectDir.parentFile
val webOut = layout.buildDirectory.dir("generated/webassets")
val copyWebAssets by tasks.registering(Sync::class) {
    from(webRoot) {
        include("index.html", "assets/**", "data/**")
        exclude("assets/office-map.png") // 앱 화면은 webp/jpg만 씀(1.9MB 절약)
    }
    into(webOut.map { it.dir("www") })
}

android {
    namespace = "studio.virel.office"
    compileSdk = 35

    defaultConfig {
        applicationId = "studio.virel.office"
        minSdk = 26
        targetSdk = 35
        // GitHub Actions는 실행 번호를 넣는다(VERSION_CODE). 로컬 빌드는 1.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("VERSION_CODE") ?: "0")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 로컬 빌드와 GitHub Actions 빌드의 서명을 같게 맞춰, 어느 쪽 APK든 덮어 설치(업데이트)가 되도록 한다.
    // 공개 레포의 키라서 '배포 서명'이 아니라 '개인 설치용 공용 서명'이다.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/virel-shared.keystore")
            storePassword = "virel-office"
            keyAlias = "virel"
            keyPassword = "virel-office"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    sourceSets["main"].assets.srcDir(webOut)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.all {
            it.systemProperty("notion.live", System.getProperty("notion.live") ?: "")
        }
    }
}

tasks.named("preBuild") { dependsOn(copyWebAssets) }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
