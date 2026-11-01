// The phone screen: draws the game in a web page with Kotlin/JS. The rules stay in core, the wording in text.
plugins {
    kotlin("multiplatform")
}

kotlin {
    js {
        // ES modules let webpack leave out the parts of Three.js the town does not use, about a quarter of it.
        useEsModules()
        // JavaScript classes and arrow functions, which every browser that runs WebGL 2 has, write the page smaller.
        compilerOptions {
            target.set("es2015")
            // A lambda that captures values is written where it is used, not as a function apart that makes it.
            freeCompilerArgs.add("-Xir-generate-inline-anonymous-functions")
        }
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
            implementation(project(":save"))
            // Draws the town in 3D. An exact version, so every build bundles the same code.
            implementation(npm("three", "0.169.0"))
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// The page bundles Three.js, so the distribution carries its MIT licence in full, next to the script.
tasks.named<Sync>("jsBrowserDistribution") {
    from(rootProject.layout.buildDirectory.file("js/node_modules/three/LICENSE")) {
        rename { "three-LICENSE.txt" }
    }
}
