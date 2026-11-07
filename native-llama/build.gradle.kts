plugins { id("com.android.library") }

android {
    namespace = "org.freethinkers.transcriber.nativellama"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
        externalNativeBuild { cmake { cppFlags += listOf("-std=c++17") } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
}
