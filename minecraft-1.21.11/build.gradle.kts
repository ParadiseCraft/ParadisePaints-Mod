plugins {
    id("net.fabricmc.fabric-loom-remap")
}

version = rootProject.version
group = rootProject.group
base { archivesName = "paradisepaints-fabric" }

loom {
    splitEnvironmentSourceSets()
    mods {
        create("paradisepaints") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.11")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.141.6+1.21.11")
}

// Mechanical API adapters keep drawing and protocol logic identical on both versions.
// All transformed sources are generated under build/; edit the canonical client sources.
val adaptClient = tasks.register<Sync>("adaptClient") {
    inputs.file(project.buildFile)
    from(rootProject.file("src/client/java"))
    into(layout.buildDirectory.dir("generated/client"))
    filteringCharset = "UTF-8"
    filter { line: String ->
        line.replace("GuiGraphicsExtractor", "GuiGraphics")
            .replace("extractRenderState", "render")
            .replace("g.text(", "g.drawString(")
            .replace(".gui.setScreen(", ".setScreen(")
            .replace(".gui.screen()", ".screen")
            .replace("clientboundPlay()", "playS2C()")
            .replace("serverboundPlay()", "playC2S()")
    }
}
sourceSets.main {
    java.srcDir(rootProject.file("src/main/java"))
    resources.srcDir(rootProject.file("src/main/resources"))
}
sourceSets["client"].java.srcDir(adaptClient)
java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}
tasks.processResources {
    val values = mapOf("version" to project.version, "minecraft_version" to "1.21.11", "loader_version" to "0.19.5")
    inputs.properties(values)
    filesMatching("fabric.mod.json") { expand(values) }
}
tasks.withType<Jar>().configureEach { archiveVersion = "${project.version}+mc1.21.11" }
tasks.remapJar { archiveVersion = "${project.version}+mc1.21.11" }
