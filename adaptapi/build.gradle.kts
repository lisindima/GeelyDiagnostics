plugins {
    id("com.android.library")
}

android {
    namespace = "com.ecarx.xui.adaptapi"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(project(":ecarx_car"))
    compileOnly(project(":ecarx_fw"))
    compileOnly("androidx.annotation:annotation:1.9.1")
}
