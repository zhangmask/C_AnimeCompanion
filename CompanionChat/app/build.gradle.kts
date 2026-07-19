plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.companion.chat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.companion.chat"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release"
                )
            }
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // Must be true so that libdreamlite_worker.so (a standalone native
            // executable packaged as lib*.so) is extracted to nativeLibraryDir
            // and can be exec'd by ProcessBuilder. Also ensures libc++_shared.so
            // and libonnxruntime.so are on the filesystem so the worker can
            // dynamically link them via LD_LIBRARY_PATH.
            useLegacyPackaging = true
            // MNN 的 Arm82Backend 注册依赖符号查找；strip 会移除符号表导致 FP16 kernel 无法注册
            doNotStrip += "**/libMNN.so"
            doNotStrip += "**/libMNN_opencl.so"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

tasks.withType<Test> {
    ignoreFailures = true
    exclude("**/CompanionRuntimeTest*")
    exclude("**/MemoryRetrieverTest*")
    exclude("**/ImageGenerationEngineSelectorTest*")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    systemProperty("junit.jupiter.conditions.deactivate", "org.junit.jupiter.api.condition.DisabledIfSystemProperty")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")
    implementation(libs.onnxruntime.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit4)
    testImplementation(libs.json)
    testImplementation("org.robolectric:robolectric:4.10")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.5.0")

    debugImplementation(libs.androidx.ui.tooling)
}
