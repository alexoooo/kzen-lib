plugins {
    kotlin("multiplatform") version kotlinVersion apply false
    id("com.google.devtools.ksp") version kspVersion apply false
}

subprojects {
    group = "tech.kzen.lib"
    version = "0.30.0-SNAPSHOT"

    repositories {
        mavenCentral()
        mavenLocal()
    }
}
