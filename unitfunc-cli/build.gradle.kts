plugins {
    kotlin("jvm")
    kotlin("kapt")
    `maven-publish`
}

dependencies {
    compileOnly(gradleApi()) // WORKAROUND: fix a bug in IDEA what is missing method hints in kotlin-stdlib but compile ok
    compileOnly(kotlin("compiler-embeddable"))
    kapt("com.google.auto.service", "auto-service", "1.0-rc7")
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["java"])
        }
    }
}

java {
    @Suppress("UnstableApiUsage")
    withSourcesJar()
}