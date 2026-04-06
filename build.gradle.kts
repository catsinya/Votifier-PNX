plugins {
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "io.catsinya"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.powernukkitx.org/releases")
    maven("https://repo.opencollab.dev/maven-releases")
    maven("https://repo.opencollab.dev/maven-snapshots")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("org.powernukkitx:server:2.0.0-SNAPSHOT")
    implementation("com.google.code.gson:gson:2.13.1")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("Votifier-PNX-${project.version}.jar")
    }

    build {
        dependsOn(shadowJar)
    }
}
