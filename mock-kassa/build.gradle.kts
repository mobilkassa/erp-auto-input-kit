import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    application
}

group = "uz.mobilkassa.kit"
version = "1.0.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core:3.6.1")
    implementation("io.ktor:ktor-server-cio:3.6.1")
    implementation("com.google.code.gson:gson:2.15.0")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
    testImplementation(kotlin("test"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
}

application {
    mainClass.set("uz.mobilkassa.kit.mock.MainKt")
}

// One code table for the guide and the mock; the examples are the replay tests' input.
tasks.processResources { from("../guide/error-codes.json") }
tasks.processTestResources { from("../guide/examples") { into("examples") } }

tasks.test {
    useJUnitPlatform()
}

// A Windows app image (mock-kassa.exe with its own runtime), so a 1C developer installs nothing.
// jpackage comes from a JDK 21 toolchain: the IDE's runtime usually has none.
val jpackageJdk = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }.map { it.metadata.installationPath }

val jpackageApp by tasks.registering(Exec::class) {
    dependsOn(tasks.installDist)
    val lib = layout.buildDirectory.dir("install/mock-kassa/lib")
    val dest = layout.buildDirectory.dir("package")
    inputs.dir(lib)
    outputs.dir(dest)
    doFirst {
        dest.get().asFile.deleteRecursively()
        val tool = jpackageJdk.get().file("bin/jpackage.exe").asFile
        check(tool.exists()) { "No jpackage in ${tool.parent}: install a full JDK 21" }
        executable = tool.absolutePath
    }
    args(
        "--type", "app-image", "--name", "mock-kassa", "--app-version", version.toString(),
        "--input", lib.get().asFile.absolutePath, "--main-jar", "mock-kassa-$version.jar",
        "--main-class", "uz.mobilkassa.kit.mock.MainKt", "--win-console",
        "--dest", dest.get().asFile.absolutePath,
    )
}

val packageApp by tasks.registering(Zip::class) {
    dependsOn(jpackageApp)
    from(layout.buildDirectory.dir("package"))
    archiveFileName.set("mock-kassa-$version-windows.zip")
    destinationDirectory.set(layout.buildDirectory)
}
