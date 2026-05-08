plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.apktest"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.apktest"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(layout.buildDirectory.dir("nativeLibs"))
        }
    }
}

val gdxNatives: Configuration by configurations.creating

tasks.register<Copy>("copyAndroidNatives") {
    val outDir = layout.buildDirectory.dir("nativeLibs")
    into(outDir)
    gdxNatives.resolve().forEach { jar ->
        val abi = when {
            jar.name.endsWith("natives-arm64-v8a.jar") -> "arm64-v8a"
            jar.name.endsWith("natives-armeabi-v7a.jar") -> "armeabi-v7a"
            jar.name.endsWith("natives-x86_64.jar") -> "x86_64"
            jar.name.endsWith("natives-x86.jar") -> "x86"
            else -> return@forEach
        }
        from(zipTree(jar)) {
            include("*.so")
            into(abi)
        }
    }
}

tasks.named("preBuild") {
    dependsOn("copyAndroidNatives")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("com.badlogicgames.gdx:gdx:1.13.1")
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.13.1")
    gdxNatives("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-arm64-v8a")
    gdxNatives("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-armeabi-v7a")
    gdxNatives("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-x86")
    gdxNatives("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-x86_64")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
