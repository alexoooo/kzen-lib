plugins {
    kotlin("multiplatform")
    `maven-publish`
}


kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion))
    }

    jvm {
        @Suppress("UNUSED_VARIABLE")
        val main by compilations.getting {
            kotlinOptions {
                jvmTarget = jvmTargetVersion
            }
        }
    }

    js {
        browser {
            testTask {
                testLogging {
                    showExceptions = true
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    showCauses = true
                    showStackTraces = true
                }
            }
        }
    }

    sourceSets {
        @Suppress("UNUSED_VARIABLE")
        val commonMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }


        @Suppress("UNUSED_VARIABLE")
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("reflect"))
                implementation("com.github.andrewoma.dexx:collection:$dexxVersion")
                implementation("com.google.guava:guava:$guavaVersion")
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val jvmTest by getting {
            dependencies {}
        }


        @Suppress("UNUSED_VARIABLE")
        val jsMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:$coroutinesVersion")
                implementation(npm("immutable", immutaleJsVersion))
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val jsTest by getting {
            dependencies {}
        }
    }
}


publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("common") {
            from(components["kotlin"])
        }
    }
}