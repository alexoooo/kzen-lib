import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

plugins {
    kotlin("multiplatform")
    `maven-publish`
}


kotlin {
    js {
        useCommonJs()
        browser()
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(project(":kzen-lib-common"))
                implementation(npm("core-js", coreJsVersion))
            }
        }

        jsTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}


publishing {
    repositories {
        mavenLocal()
    }
}


// https://youtrack.jetbrains.com/issue/KT-52578/KJS-Gradle-KotlinNpmInstallTask-gradle-task-produces-unsolvable-warning-ignored-scripts-due-to-flag.
yarn.ignoreScripts = false