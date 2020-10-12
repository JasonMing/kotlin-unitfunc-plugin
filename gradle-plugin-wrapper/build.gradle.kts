plugins {
    `kotlin-dsl`
    kotlin("kapt")
    `maven-publish`
}

dependencies {
    compileOnly(kotlin("gradle-plugin-api"))
    kapt("com.google.auto.service", "auto-service", "1.0-rc7")
}

gradlePlugin {
    plugins {
        create("unitfunc") {
            id = "ming.kotlin.unitfunc"
            implementationClass = "ming.kotlin.unitfunc.gradle.UnitFuncGradlePlugin"
        }
    }
}

java {
    @Suppress("UnstableApiUsage")
    withSourcesJar()
}