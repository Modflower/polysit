import com.modrinth.minotaur.dependencies.DependencyType
import com.modrinth.minotaur.dependencies.ModDependency
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

plugins {
	java
	`java-library`
	id("net.fabricmc.fabric-loom")
	id("com.modrinth.minotaur")
	`maven-publish`
}

val minecraftVersion: String by project
val minecraftRequired: String by project
val minecraftCompatible: String by project
val yarnMappings: String by project
val loaderVersion: String by project
val fabricApiVersion: String by project
val polymerVersion: String by project
val projectVersion: String by project
val modrinthId: String by project

val isPublish = System.getenv("GITHUB_EVENT_NAME") == "release"
val isRelease = System.getenv("BUILD_RELEASE").toBoolean()
val isActions = System.getenv("GITHUB_ACTIONS").toBoolean()
val baseVersion = "$projectVersion+mc.$minecraftVersion"

group = "gay.ampflower"
version = when {
	isRelease -> baseVersion
	isActions -> "$baseVersion-build.${System.getenv("GITHUB_RUN_NUMBER")}-commit.${System.getenv("GITHUB_SHA").substring(0, 7)}-branch.${System.getenv("GITHUB_REF")?.substring(11)?.replace('/', '.') ?: "unknown"}"
	else -> "$baseVersion-build.local"
}

java {
	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

repositories {
	mavenCentral()
	maven("https://maven.nucleoid.xyz/releases") {
		name = "NucleoidMC"
	}
}

dependencies {
	minecraft("com.mojang:minecraft:$minecraftVersion")
	implementation("net.fabricmc:fabric-loader:$loaderVersion")
	implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
	implementation("eu.pb4:polymer-core:$polymerVersion")
}

tasks {
	withType<JavaCompile> {
		options.encoding = "UTF-8"
		options.isDeprecation = true
		options.isWarnings = true
	}
	processResources {
		val map = mapOf(
			"version" to project.version,
			"project_version" to projectVersion,
			"loader_version" to loaderVersion,
			"minecraft_required" to minecraftRequired
		)
		inputs.properties(map)

		filesMatching("fabric.mod.json") {
			expand(map)
		}
	}
	withType<Jar> {
		from("LICENSE")
	}
	modrinth {
		token.set(System.getenv("MODRINTH_TOKEN"))
		projectId.set(modrinthId)
		versionType.set(System.getenv("RELEASE_OVERRIDE") ?: when {
			"alpha" in projectVersion -> "alpha"
			!isRelease || '-' in projectVersion -> "beta"
			else -> "release"
		})
		val ref = System.getenv("GITHUB_REF")
		changelog.set(
			System.getenv("CHANGELOG") ?: if (ref != null && ref.startsWith("refs/tags/")) "You may view the changelog at https://github.com/Modflower/polysit/releases/tag/${URLEncoder.encode(ref.substring(10), StandardCharsets.UTF_8)}"
			else "No changelog is available. Perhaps poke at https://github.com/Modflower/polysit for a changelog?"
		)
		uploadFile.set(jar.get())
		gameVersions.set(minecraftCompatible.split(","))
		loaders.addAll("fabric", "quilt")
		dependencies.set(mutableListOf(
			ModDependency("xGdtZczs", DependencyType.REQUIRED),
			ModDependency("P7dR8mSH", DependencyType.REQUIRED)
		))
	}
}
