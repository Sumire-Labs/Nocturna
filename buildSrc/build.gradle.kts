import java.util.Properties

plugins {
    `kotlin-dsl`
    `java-library`
    id("java-gradle-plugin") // so we can assign and ID to our plugin
}

val dependencyVersions = Properties().apply {
    file("../gradle.properties").inputStream().use { load(it) }
}

dependencies {
    implementation("com.cleanroommc:tokenenvoy:${dependencyVersions.getProperty("tokenenvoy_version")}")
    implementation("com.google.guava:guava:${dependencyVersions.getProperty("build_guava_version")}")
    implementation("org.ow2.asm:asm:${dependencyVersions.getProperty("build_asm_version")}")
    implementation("org.ow2.asm:asm-tree:${dependencyVersions.getProperty("build_asm_version")}")
    implementation("org.ow2.asm:asm-commons:${dependencyVersions.getProperty("build_asm_version")}")
    implementation("com.google.code.gson:gson:${dependencyVersions.getProperty("build_gson_version")}")
    implementation("org.apache.commons:commons-compress:${dependencyVersions.getProperty("build_commons_compress_version")}")
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1")
    implementation("dev.kikugie:stonecutter:${dependencyVersions.getProperty("stonecutter_version")}")
}

repositories {
    mavenCentral()
    maven("https://maven.cleanroommc.com") {
        content { includeGroupAndSubgroups("com.cleanroommc") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.fabricmc.net/") }
        filter {
            includeGroupAndSubgroups("net.fabricmc")
        }
    }
    exclusiveContent {
        forRepositories(
            maven("https://maven.kikugie.dev/releases"),
            maven("https://maven.kikugie.dev/snapshots")
        )
        filter {
            includeGroupAndSubgroups("dev.kikugie")
        }
    }
}

gradlePlugin {
    plugins {
        register("embeddium-fabric-module-finder") {
            id = "embeddium-fabric-module-finder"
            implementationClass = "org.embeddedt.embeddium.gradle.fabric.remapper.ModuleFinderPlugin"
        }
    }
}
