import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm")
    `maven-publish`
}


dependencies {
//    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8", version = kotlinVersion)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = coroutinesVersion)

    implementation(group = "com.google.guava", name = "guava", version = guavaVersion)
    implementation(group = "com.github.andrewoma.dexx", name = "collection", version = dexxVersion)

    testImplementation(group = "org.jetbrains.kotlin", name = "kotlin-test", version = kotlinVersion)
    testImplementation(group = "org.jetbrains.kotlin", name = "kotlin-test-junit", version = kotlinVersion)

    implementation(project(":kzen-lib-common"))
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
//        jvmTarget = "11"
//        jvmTarget = "13"
        jvmTarget = "15"
    }
}


// https://stackoverflow.com/questions/61432006/building-an-executable-jar-that-can-be-published-to-maven-local-repo-with-publi
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

// http://bastienpaul.fr/wordpress/2019/02/08/publish-a-kotlin-lib-with-gradle-kotlin-dsl/
// https://stackoverflow.com/questions/52596968/build-source-jar-with-gradle-kotlin-dsl/
//tasks {
//    val sourcesJar by creating(Jar::class) {
//        archiveClassifier.set("sources")
//        from(sourceSets.getByName("main").allSource)
//    }
//
//    artifacts {
//        archives(sourcesJar)
//        archives(jar)
//    }
//}

//tasks.withType<Jar> {
//    archiveClassifier.set("sources")
//    from(sourceSets.getByName("main").allSource)
//}


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