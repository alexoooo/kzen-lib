plugins {
    kotlin("multiplatform")
//    id("kotlinx-serialization")
}



//apply plugin: 'kotlin-platform-common'
//
//dependencies {
//    compile group: 'org.jetbrains.kotlin', name: 'kotlin-stdlib-common', version: kotlinVersion
//    compile group: 'org.jetbrains.kotlinx', name: 'kotlinx-io', version: kotlinxIoVersion
//
//    testCompile group: 'org.jetbrains.kotlin', name: 'kotlin-test-common', version: kotlinVersion
//    testCompile group: 'org.jetbrains.kotlin', name: 'kotlin-test-annotations-common', version: kotlinVersion
//}


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
                implementation(kotlin("stdlib-common"))
//                implementation("org.jetbrains:kotlin-css:1.0.0-$kotlin_version")
//                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$kotlinx_serialization_version")
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
                implementation(kotlin("stdlib-jdk8"))
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
                implementation(kotlin("stdlib-js"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:$coroutinesVersion")
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