plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}


subprojects {
    group = "tech.kzen.lib"
    version = "0.13.7-SNAPSHOT"

    repositories {
        mavenLocal()
        jcenter()
    }
}
