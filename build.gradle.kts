plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}


subprojects {
    group = "tech.kzen.lib"
    version = "0.13.5"

    repositories {
        mavenLocal()
        jcenter()
    }
}
