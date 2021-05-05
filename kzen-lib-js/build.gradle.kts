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
    implementation(project(":kzen-lib-common"))

    implementation(npm("core-js", coreJsVersion))

    testImplementation(kotlin("test"))
}


publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("js") {
            from(components["kotlin"])
        }
    }
}