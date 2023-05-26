buildscript {
    val kotlinVersion = "1.8.0"

    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.0.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
        classpath("com.vanniktech:gradle-maven-publish-plugin:0.19.0")
        classpath("app.cash.paparazzi:paparazzi-gradle-plugin:1.2.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
