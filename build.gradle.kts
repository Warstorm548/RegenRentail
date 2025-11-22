plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

group = "com.regionrental"
version = "1.2.5"

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

configurations.all {
    resolutionStrategy {
        // Force specific versions to resolve conflicts
        force(
            "com.google.guava:guava:33.3.1-jre",           // Use WorldGuard's version
            "com.google.code.gson:gson:2.11.0",            // Use WorldGuard's version
            "it.unimi.dsi:fastutil:8.5.15",                // Use WorldGuard's version
            "org.apache.logging.log4j:log4j-bom:2.24.1"    // Use WorldGuard's version
        )

        // Prefer modules from Paper when conflicts arise
        preferProjectModules()
    }
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.3-R0.1-SNAPSHOT")

    // Vault API
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")

    // WorldGuard (latest version)
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.14") {
        exclude(group = "com.google.guava")
        exclude(group = "com.google.code.gson")
        exclude(group = "it.unimi.dsi")
        exclude(group = "org.apache.logging.log4j")
    }

    // WorldEdit (use compatible version with WorldGuard)
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.16") {
        exclude(group = "com.google.guava")
        exclude(group = "com.google.code.gson")
        exclude(group = "it.unimi.dsi")
        exclude(group = "org.apache.logging.log4j")
    }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.16") {
        exclude(group = "com.google.guava")
        exclude(group = "com.google.code.gson")
        exclude(group = "it.unimi.dsi")
        exclude(group = "org.apache.logging.log4j")
    }

    // LuckPerms API
    compileOnly("net.luckperms:api:5.4")
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

    // Minimize JAR by removing unused classes (optional)
    // Uncomment if smaller JAR size is needed:
    // minimize()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// Set default tasks
defaultTasks("clean", "build")
