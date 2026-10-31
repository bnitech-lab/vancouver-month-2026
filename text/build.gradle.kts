// The Korean wording both screens share: amounts, dates, actions, events and endings. No layout, and no
// JVM-only APIs, so the terminal and the web screen read the same lines.
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
