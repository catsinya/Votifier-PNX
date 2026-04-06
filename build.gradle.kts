plugins {
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "io.catsinya"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.powernukkitx.org/releases")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(files("/home/now/Documents/Zombies/powernukkitx.jar"))
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
