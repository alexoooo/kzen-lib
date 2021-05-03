plugins {
    kotlin("multiplatform")
    `maven-publish`
}


kotlin {
    jvm {}

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
//                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }


        @Suppress("UNUSED_VARIABLE")
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-reflect")
                implementation("com.github.andrewoma.dexx:collection:$dexxVersion")
                implementation("com.google.guava:guava:$guavaVersion")
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }


        @Suppress("UNUSED_VARIABLE")
        val jsMain by getting {
            dependencies {
//                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:$coroutinesVersion")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:$coroutinesVersion")
                implementation(npm("immutable", immutaleJsVersion))
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}


publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("common") {
//            println("Components: " + components.asMap.keys)
            from(components["kotlin"])
//            artifact(sourcesJar.get())
        }
    }
}