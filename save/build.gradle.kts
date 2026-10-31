// A month in progress as a short record (the seed, the room, the guess and each day's choice), and the month rebuilt
// from it by playing those choices again through core's rules. No browser and no storage, so the JVM tests it.
plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(21)
    jvm()
    js {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
