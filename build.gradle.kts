plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}


subprojects {
    group = "tech.kzen.lib"
    version = "0.14.2"

    repositories {
        mavenLocal()
        jcenter()
    }
}
