plugins { id("net.fabricmc.fabric-loom") }
version = rootProject.version
group = rootProject.group
val game = "26.1.2"
val api = "0.155.2+26.1.2"
base { archivesName = "paradisepaints-fabric" }
layout.buildDirectory = layout.projectDirectory.dir("build/$game")
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
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    minecraft("com.mojang:minecraft:$game")
    implementation("net.fabricmc:fabric-loader:0.19.5")
    implementation("net.fabricmc.fabric-api:fabric-api:$api")
}
val adaptClient = tasks.register<Sync>("adaptClient") {
    inputs.file(project.buildFile)
    from(rootProject.file("src/client/java"))
    into(layout.buildDirectory.dir("generated/client"))
    filteringCharset = "UTF-8"
    filter { line: String ->
        line.replace(".gui.setScreen(", ".setScreen(")
            .replace(".gui.screen()", ".screen")
    }
}
sourceSets.main {
    java.srcDir(rootProject.file("src/main/java"))
    resources.srcDir(rootProject.file("src/main/resources"))
}
sourceSets["client"].java.srcDir(adaptClient)
sourceSets.test {
    java.srcDir(rootProject.file("src/test/java"))
    compileClasspath += sourceSets["client"].output + sourceSets["client"].compileClasspath
    runtimeClasspath += sourceSets["client"].output + sourceSets["client"].runtimeClasspath
}
tasks.test { useJUnitPlatform() }
java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release = 25 }
tasks.processResources {
    val values = mapOf("version" to project.version, "minecraft_version" to game, "loader_version" to "0.19.5")
    inputs.properties(values)
    filesMatching("fabric.mod.json") { expand(values) }
}
tasks.withType<Jar>().configureEach { archiveVersion = "${project.version}+mc$game" }
