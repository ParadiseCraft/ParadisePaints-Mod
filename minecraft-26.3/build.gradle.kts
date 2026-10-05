plugins { id("net.fabricmc.fabric-loom") }
version = rootProject.version
group = rootProject.group
val game = "26.3"
val loader = providers.gradleProperty("loader_version").get()
val api = "0.161.0+26.3"
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
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    minecraft("com.mojang:minecraft:$game")
    implementation("net.fabricmc:fabric-loader:$loader")
    implementation("net.fabricmc.fabric-api:fabric-api:$api")
}
sourceSets.main {
    java.srcDir(rootProject.file("src/main/java"))
    resources.srcDir(rootProject.file("src/main/resources"))
}
sourceSets["client"].java.srcDir(rootProject.file("src/client/java"))
sourceSets.test {
    java.srcDir(rootProject.file("src/test/java"))
    compileClasspath += sourceSets["client"].output + sourceSets["client"].compileClasspath
    runtimeClasspath += sourceSets["client"].output + sourceSets["client"].runtimeClasspath
}
tasks.test { useJUnitPlatform() }
java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release = 25 }
tasks.processResources {
    val values = mapOf("version" to project.version, "minecraft_version" to game, "loader_version" to loader)
    inputs.properties(values)
    filesMatching("fabric.mod.json") { expand(values) }
}
tasks.withType<Jar>().configureEach { archiveVersion = "${project.version}+mc$game" }
