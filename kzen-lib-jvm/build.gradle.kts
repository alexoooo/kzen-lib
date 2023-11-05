import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm")
    `maven-publish`
}


kotlin {
    jvmToolchain(jvmToolchainVersion)
}


dependencies {
    implementation(project(":kzen-lib-common"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    implementation("com.google.guava:guava:$guavaVersion")
    implementation("com.github.andrewoma.dexx:collection:$dexxVersion")

    testImplementation(kotlin("test"))
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = jvmTargetVersion
    }
}


tasks.compileJava {
    options.release.set(javaVersion)
}


// https://stackoverflow.com/questions/61432006/building-an-executable-jar-that-can-be-published-to-maven-local-repo-with-publi
val sourcesJar by tasks.registering(Jar::class) {
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