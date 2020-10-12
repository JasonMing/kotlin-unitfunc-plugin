rootProject.name = "kotlin-unitfunc-plugin"

buildscript { buildscript }
extensions.findByName("scan")?.apply {
    scan.enabled = false
}

include(":kotlin-unitfunc-compiler-plugin")
include(":kotlin-unitfunc")
include(":kotlin-maven-unitfunc")

project(":kotlin-unitfunc-compiler-plugin").apply {
    projectDir = file("$rootDir/unitfunc-cli")
}
project(":kotlin-unitfunc").apply {
    projectDir = file("$rootDir/gradle-plugin-wrapper")
}
project(":kotlin-maven-unitfunc").apply {
    projectDir = file("$rootDir/maven-plugin-wrapper")
}