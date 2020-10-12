plugins {
    kotlin("jvm") version embeddedKotlinVersion apply false
    kotlin("kapt") version embeddedKotlinVersion apply false
}

allprojects {
    group = "ming.kotlin"
    version = "1.0.0-SNAPSHOT"
}

subprojects {

    plugins.withId("org.jetbrains.kotlin.jvm") {
        configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            target.compilations.all {
                kotlinOptions {
                    jvmTarget = "1.8"
                    freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
                }
            }
        }
    }

    dependencies {
        plugins.withId("java") {
            "compileOnly"("com.google.auto.service:auto-service:1.0-rc7")
        }
        // plugins.withId("org.jetbrains.kotlin.jvm") {
        //     "implementation"(kotlin("stdlib"))
        //     "implementation"(kotlin("stdlib-jdk8"))
        // }
        // plugins.withId("org.jetbrains.kotlin.kapt") {
        //     afterEvaluate {
        //         "kapt"("com.google.auto.service:auto-service:1.0-rc7")
        //     }
        // }
    }
}
