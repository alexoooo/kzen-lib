plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}

subprojects {
    group = "tech.kzen.lib"
    version = "0.29.0-SNAPSHOT"

    repositories {
        mavenCentral()
        mavenLocal()
    }
}
