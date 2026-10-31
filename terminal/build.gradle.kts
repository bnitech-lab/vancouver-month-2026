// The text screen that plays the rules in a terminal.
plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":text"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("vancouvermonth.terminal.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}
