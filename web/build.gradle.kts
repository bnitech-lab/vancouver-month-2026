// The phone screen: draws the game in a web page with Kotlin/JS. The rules stay in core, the wording in text.
plugins {
    kotlin("multiplatform")
}

kotlin {
    js {
        // ES modules let webpack leave out the parts of Three.js the town does not use, about a quarter of it.
        useEsModules()
        browser {
            commonWebpackConfig {
                outputFileName = "vancouver-month.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":text"))
            // Draws the town in 3D. An exact version, so every build bundles the same code.
            implementation(npm("three", "0.169.0"))
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
