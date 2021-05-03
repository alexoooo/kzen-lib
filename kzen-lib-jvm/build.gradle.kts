import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm")
    `maven-publish`
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")

//    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = coroutinesVersion)
    api(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = coroutinesVersion)

    implementation(group = "com.google.guava", name = "guava", version = guavaVersion)
    implementation(group = "com.github.andrewoma.dexx", name = "collection", version = dexxVersion)

    testImplementation(group = "org.jetbrains.kotlin", name = "kotlin-test", version = kotlinVersion)
    testImplementation(group = "org.jetbrains.kotlin", name = "kotlin-test-junit", version = kotlinVersion)

    implementation(project(":kzen-lib-common"))
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = jvmTargetVersion
    }
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