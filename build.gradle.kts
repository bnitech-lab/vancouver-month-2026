plugins {
    kotlin("jvm") version "2.4.21" apply false
    kotlin("multiplatform") version "2.4.21" apply false
}

subprojects {
    repositories {
        mavenCentral()
    }

    // A multiplatform module has no `test` task of its own. This one runs its JVM and browser tests,
    // so `./gradlew test` still runs every test in every module.
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        tasks.register("test") {
            dependsOn("allTests")
        }
    }
}
