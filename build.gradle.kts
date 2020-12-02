plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}


subprojects {
    group = "tech.kzen.lib"
    version = "0.20.0-SNAPSHOT"

    repositories {
        mavenLocal()
        jcenter()
    }
}
