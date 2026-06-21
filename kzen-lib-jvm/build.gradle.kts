import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    `maven-publish`
}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion))
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(jvmTargetVersion))
    }
}


dependencies {
    implementation(project(":kzen-lib-common"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    implementation("com.google.guava:guava:$guavaVersion")
    implementation("com.github.andrewoma.dexx:collection:$dexxVersion")

    kspTest(project(":kzen-lib-reflect-ksp"))

    testImplementation(kotlin("test"))
}


ksp {
    arg("kzen.reflect.moduleClassName", "tech.kzen.lib.server.codegen.KzenLibJvmTestModule")
}


tasks.compileJava {
    options.release.set(javaVersion)
}


// https://stackoverflow.com/questions/61432006/building-an-executable-jar-that-can-be-published-to-maven-local-repo-with-publi
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