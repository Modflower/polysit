import com.modrinth.minotaur.dependencies.DependencyType
import com.modrinth.minotaur.dependencies.ModDependency

plugins {
	java
	`java-library`
	id("fabric-loom")
	id("com.diffplug.spotless")
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
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

repositories {
	mavenCentral()
	maven {
		name = "NucleoidMC"
		url = uri("https://maven.nucleoid.xyz")
	}
}

dependencies {
	minecraft("com.mojang", "minecraft", minecraftVersion)
	mappings("net.fabricmc", "yarn", yarnMappings, classifier = "v2")
	modImplementation("net.fabricmc", "fabric-loader", loaderVersion)
	modImplementation("net.fabricmc.fabric-api", "fabric-api", fabricApiVersion)
	modImplementation("eu.pb4", "polymer-core", polymerVersion)
}
spotless {
	java {
		importOrderFile(projectDir.resolve(".internal/spotless.importorder"))
		eclipse().configFile(projectDir.resolve(".internal/spotless.xml"))

		licenseHeaderFile(projectDir.resolve(".internal/license-header.java"))
	}
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
		changelog.set(System.getenv("CHANGELOG") ?: if (ref != null && ref.startsWith("refs/tags/")) "You may view the changelog at https://github.com/the-glitch-network/polysit/releases/tag/${com.google.common.net.UrlEscapers.urlFragmentEscaper().escape(ref.substring(10))}"
		else "No changelog is available. Perhaps poke at https://github.com/the-glitch-network/polysit for a changelog?")
		uploadFile.set(remapJar.get())
		gameVersions.set(minecraftCompatible.split(","))
		loaders.addAll("fabric", "quilt")
		dependencies.set(mutableListOf(
			ModDependency("xGdtZczs", DependencyType.REQUIRED),
			ModDependency("P7dR8mSH", DependencyType.REQUIRED)
		))
	}
}
