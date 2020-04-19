plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}


subprojects {
    group = "tech.kzen.lib"
    version = "0.13.3"

    repositories {
        mavenLocal()
        jcenter()
    }
}
