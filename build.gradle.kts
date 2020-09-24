plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}


subprojects {
    group = "tech.kzen.lib"
    version = "0.16.0"

    repositories {
        mavenLocal()
        jcenter()
    }
}
