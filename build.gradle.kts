plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}


subprojects {
    group = "tech.kzen.lib"
    version = "0.21.0"

    repositories {
        mavenLocal()
        jcenter()
    }
}
