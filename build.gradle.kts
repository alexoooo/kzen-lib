plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}


subprojects {
    group = "tech.kzen.lib"
    version = "0.17.1"

    repositories {
        mavenLocal()
        jcenter()
    }
}
