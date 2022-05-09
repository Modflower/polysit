rootProject.name = "polysit"

pluginManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		maven {
			name = "Quilt"
			url = uri("https://maven.quiltmc.org/repository/release/")
			content {
				includeGroup("org.quiltmc")
			}
		}
		gradlePluginPortal()
	}
	plugins {
		id("fabric-loom") version System.getProperty("loomVersion")!!
		id("com.diffplug.spotless") version System.getProperty("spotlessVersion")!!
		id("com.modrinth.minotaur") version System.getProperty("minotaurVersion")!!
		id("io.github.juuxel.loom-quiltflower") version System.getProperty("quiltflowerVersion")!!
	}
}
