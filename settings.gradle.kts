pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        gradlePluginPortal()
    }
}

rootProject.name = "ParadisePaints"
include("minecraft-1.21.11")
include("minecraft-26.1.2")
