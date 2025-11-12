plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.regionrental"
version = "1.0.0"

repositories {
    mavenCentral()

    // Paper Repository
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    // Vault Repository
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }

    // WorldGuard/WorldEdit Repository
    maven {
        name = "enginehub"
        url = uri("https://maven.enginehub.org/repo/")
    }

    // LuckPerms Repository
    maven {
        name = "sonatype-snapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.3-R0.1-SNAPSHOT")

    // Vault API
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")

    // WorldGuard
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.14")

    // WorldEdit
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.6")
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.6")

    // LuckPerms API
    compileOnly("net.luckperms:api:5.4")

    // Lombok for cleaner code
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(
            "version" to project.version,
            "name" to project.name,
            "group" to project.group
        )
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("${project.name}-${project.version}.jar")

    // Minimize JAR by removing unused classes
    minimize()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// Set default tasks
defaultTasks("clean", "build")
