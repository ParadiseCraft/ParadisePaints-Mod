import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("net.fabricmc.fabric-loom") version "1.17.20"
    `maven-publish`
}

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val loaderVersion = providers.gradleProperty("loader_version").get()
val fabricVersion = providers.gradleProperty("fabric_version").get()
val modVersion = providers.gradleProperty("mod_version").get()
val mavenGroup = providers.gradleProperty("maven_group").get()
val archivesBaseName = providers.gradleProperty("archives_base_name").get()

version = modVersion
group = mavenGroup

base {
    archivesName = archivesBaseName
}

loom {
    splitEnvironmentSourceSets()

    mods {
        create("paradisepaints") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential Maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
}

dependencies {
    // To change the versions see the gradle.properties file.
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")

    implementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("loader_version", loaderVersion)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to minecraftVersion,
            "loader_version" to loaderVersion,
        )
    }
}

val targetJavaVersion = 25

tasks.withType<JavaCompile>().configureEach {
    // Ensure that the encoding is set to UTF-8, no matter what the system default is.
    options.encoding = "UTF-8"
    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release = targetJavaVersion
    }
}

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
    // Loom automatically attaches sourcesJar to a RemapSourcesJar task and to the build task.
    withSourcesJar()
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_$archivesBaseName" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = archivesBaseName
            from(components["java"])
        }
    }

    // Add repositories to publish artifacts to here.
    repositories {
    }
}
