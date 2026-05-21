import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
    `maven-publish`
}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion))
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(jvmTargetVersion))
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


dependencies {
    add("kspCommonMainMetadata", project(":kzen-lib-reflect-ksp"))
}


ksp {
    arg("kzen.reflect.moduleClassName", "tech.kzen.lib.common.codegen.KzenLibCommonModule")
}


// KSP commonMain generates into build/generated/ksp/metadata/commonMain/kotlin, but the per-target
// compile tasks don't pick it up automatically. Add the dir to commonMain and gate every consumer
// of the source set (Kotlin compiles + sources-jar packaging) on the KSP run by name (deferred so
// the KSP task can be registered later in the configuration phase).
kotlin.sourceSets.commonMain.configure {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
tasks.matching { it.name == "sourcesJar" || it.name.endsWith("SourcesJar") }
    .configureEach { dependsOn("kspCommonMainKotlinMetadata") }


publishing {
    repositories {
        mavenLocal()
    }
}
