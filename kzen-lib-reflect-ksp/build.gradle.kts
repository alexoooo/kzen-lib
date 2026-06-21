import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    kotlin("jvm")
    `maven-publish`
}


// KSP processor JARs are loaded into the KSP worker JVM (which lags the main toolchain), so we
// compile this module against Java 17 to stay compatible regardless of what KSP's worker runs on.
val kspProcessorJavaVersion = 17

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion))
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(kspProcessorJavaVersion.toString()))
    }
}


dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:$kspVersion")

    testImplementation(kotlin("test"))
}


tasks.compileJava {
    options.release.set(kspProcessorJavaVersion)
}


val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}


publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("jvm") {
            from(components["java"])
            artifact(sourcesJar.get())
        }
    }
}
