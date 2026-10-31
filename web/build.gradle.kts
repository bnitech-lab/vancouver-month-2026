// The phone screen: draws the game in a web page with Kotlin/JS. The rules stay in core, the wording in text.
plugins {
    kotlin("multiplatform")
}

kotlin {
    js {
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
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
