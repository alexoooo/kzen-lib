plugins {
    id("org.jetbrains.kotlin.js")
    `maven-publish`
}


kotlin {
    js {
        useCommonJs()
        browser()
    }
}


dependencies {
//    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-js", version = kotlinVersion)
    testImplementation(group = "org.jetbrains.kotlin", name = "kotlin-test-js", version = kotlinVersion)

    implementation(project(":kzen-lib-common"))

    implementation(npm("core-js", coreJsVersion))
}


publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("js") {
//            println("Components: " + components.asMap.keys)
            from(components["kotlin"])
        }
    }
}