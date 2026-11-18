plugins {
    id("com.android.application") version "8.5.2"
}

android {
    namespace = "com.yg.hidecard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yg.hidecard"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 编译期占位，运行期由 LSPosed 注入真实实现
    compileOnly("de.robv.android.xposed:api:82")
}
