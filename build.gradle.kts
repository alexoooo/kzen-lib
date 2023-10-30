plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}


subprojects {
    group = "tech.kzen.lib"
    version = "0.27.1-SNAPSHOT"

    repositories {
        mavenCentral()
        mavenLocal()
    }
}
