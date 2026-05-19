rootProject.name = "polysit"

pluginManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		gradlePluginPortal()
	}
	plugins {
		id("net.fabricmc.fabric-loom") version System.getProperty("loomVersion")!!
		id("com.modrinth.minotaur") version System.getProperty("minotaurVersion")!!
	}
}
