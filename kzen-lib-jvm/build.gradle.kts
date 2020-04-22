import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// TODO: include sources jar file

plugins {
    kotlin("jvm")
}

dependencies {
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8", version = kotlinVersion)
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
        jvmTarget = "11"
    }
}