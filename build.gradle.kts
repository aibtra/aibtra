import java.util.Properties
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.gradle.internal.os.OperatingSystem

val os = OperatingSystem.current()
val iconPath: String = when {
    os.isWindows -> "src/main/resources/images/logo.ico"
    os.isMacOsX -> "src/main/resources/images/logo.icns"
    os.isLinux -> "src/main/resources/images/logo.png"
    else -> throw Exception("Unsupported operating system")
}

val APP_NAME = "aibtra"

plugins {
    kotlin("jvm") version "1.9.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20"
    application
    id("org.beryx.runtime") version "1.12.7"
}

group = "dev.${APP_NAME}"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.5.1")
    implementation("com.googlecode.json-simple:json-simple:1.1.1")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("de.regnis.q.sequence:sequence-library:1.0.4")
    implementation("com.formdev:flatlaf:3.1.1")
    implementation("com.miglayout:miglayout:3.7.4")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.0")
}

kotlin {
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
    sourceSets["test"].kotlin.srcDirs("src/test/kotlin")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

application {
    mainClass.set("MainKt")
}

task("generateBuildProperties") {
    doLast {
        val props = Properties()
        val commitSha = System.getenv("GITHUB_SHA") ?: "dev"
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC)
        val buildTime = formatter.format(Instant.now())

        props["sha"] = commitSha
        props["time"] = buildTime

        file("${layout.buildDirectory.get().asFile}/resources/main").mkdirs()
        props.store(file("${layout.buildDirectory.get().asFile}/resources/main/build.properties").outputStream(), null)
    }
}

tasks.jar {
    dependsOn("generateBuildProperties")
}

tasks["generateBuildProperties"].dependsOn("processResources")
tasks["jpackage"].dependsOn("generateBuildProperties")

runtime {
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))

    modules.set(listOf("java.base", "java.desktop", "java.logging", "jdk.crypto.ec"))

    jpackage {
        imageName = APP_NAME
        imageOptions = listOf("--icon", iconPath)
        skipInstaller = true
    }
}