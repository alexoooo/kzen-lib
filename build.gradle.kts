plugins {
    kotlin("multiplatform") version kotlinVersion apply false
    id("com.google.devtools.ksp") version kspVersion apply false
}

subprojects {
    group = "tech.kzen.lib"
    version = "0.29.1-SNAPSHOT"

    repositories {
        mavenCentral()
        mavenLocal()
    }
}
