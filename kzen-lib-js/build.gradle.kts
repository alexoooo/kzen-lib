plugins {
    id("org.jetbrains.kotlin.js")
}


kotlin {
    target {
        useCommonJs()
        browser()
    }
}


dependencies {
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-js", version = kotlinVersion)
    testImplementation(group = "org.jetbrains.kotlin", name = "kotlin-test-js", version = kotlinVersion)

    implementation(project(":kzen-lib-common"))

    implementation(npm("core-js", coreJsVersion))
}


//run {}


//tasks.withType<KotlinCompile> {
//    kotlinOptions {
//        freeCompilerArgs = listOf("-Xjsr305=strict")
//        jvmTarget = "11"
//    }
//}