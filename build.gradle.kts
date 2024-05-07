plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}

subprojects {
    group = "tech.kzen.lib"
    version = "0.28.0"

    repositories {
        mavenCentral()
        mavenLocal()
    }
}
