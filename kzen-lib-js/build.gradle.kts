import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
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


// === npm supply-chain pins =======================================================================
// Build-time only: kotlin-js-store/yarn.lock holds KGP's karma/mocha/webpack toolchain, none of
// which ships. These clear Dependabot advisories that a lockfile refresh cannot reach, because the
// declaring package pins a version below the patch. Each was checked for CJS API compatibility
// against its actual consumer before being added.
//
// RE-VALIDATE ON EVERY KOTLIN BUMP: a stale pin here can hold a package BELOW what a newer KGP
// wants. Drop each line once KGP's own NpmVersions / the upstream range has caught up.

// KGP 2.4.0 declares webpack as an exact devDependency pin, so `versions` is the right knob — it
// changes what the generated package.json asks for, rather than overriding it after the fact.
rootProject.plugins.withType<NodeJsRootPlugin> {
    rootProject.extensions.getByType<NodeJsRootExtension>().versions.apply {
        webpack.version = "5.104.1"  // KGP pins 5.101.3
    }
}

// Deep transitives whose parent hard-pins a vulnerable range; yarn resolutions override even those.
yarn.resolution("serialize-javascript", "7.0.5")  // mocha + terser-webpack-plugin pin ^6.0.2
yarn.resolution("diff", "8.0.3")                  // mocha pins ^7.0.0