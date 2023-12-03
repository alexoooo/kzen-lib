
plugins {
    kotlin("multiplatform")
    `maven-publish`
}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion))
    }

    jvm {
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
        commonMain {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }


        jvmMain {
            dependencies {
                implementation(kotlin("reflect"))
                implementation("com.github.andrewoma.dexx:collection:$dexxVersion")
                implementation("com.google.guava:guava:$guavaVersion")
            }
        }

        jvmTest {
            dependencies {}
        }


        jsMain {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:$coroutinesVersion")
                implementation(npm("immutable", immutableJsVersion))
            }
        }

        jsTest {
            dependencies {}
        }
    }
}


publishing {
    repositories {
        mavenLocal()
    }
}
