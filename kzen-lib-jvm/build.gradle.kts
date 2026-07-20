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

    // ReflectiveClassMirror: runtime introspection, and logging of every class the fallback serves
    // (binding supplied by consumers - kzen-auto ships logback)
    implementation(kotlin("reflect"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    kspTest(project(":kzen-lib-reflect-ksp"))

    testImplementation(kotlin("test"))

    // Without a binding slf4j-api is a no-op, which would hide fallback hits exactly where the
    // parity tests run
    testRuntimeOnly("org.slf4j:slf4j-simple:$slf4jVersion")
}


ksp {
    arg("kzen.reflect.moduleClassName", "tech.kzen.lib.server.codegen.KzenLibJvmTestModule")
}


tasks.compileJava {
    options.release.set(javaVersion)
}


tasks.compileTestJava {
    // The Java parity fixture (JavaServiceHolder) needs runtime-visible parameter names for the
    // reflective mirror; without -parameters, KParameter.name is null for Java constructors
    options.compilerArgs.add("-parameters")
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