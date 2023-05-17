// https://youtrack.jetbrains.com/issue/KT-46019

plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}


subprojects {
    group = "tech.kzen.lib"
    version = "0.27.0-SNAPSHOT"

    repositories {
        mavenCentral()
        mavenLocal()
    }
}
