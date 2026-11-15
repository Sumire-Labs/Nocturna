import dev.kikugie.stonecutter.settings.tree.TreeBuilder

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases") {
            content {
                includeGroup("net.neoforged")
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
        exclusiveContent {
            forRepository { maven("https://maven.fabricmc.net/") }
            filter {
                includeGroupAndSubgroups("net.fabricmc")
                includeGroup("fabric-loom")
            }
        }
        exclusiveContent {
            forRepository { maven("https://maven.ornithemc.net/releases") }
            filter {
                includeModule("io.github.gaming32", "signature-changer")
                includeGroupAndSubgroups("net.ornithemc")
                includeGroup("ploceus")
            }
        }
        exclusiveContent {
            forRepository { maven("https://maven.wagyourtail.xyz/snapshots") }
            forRepository { maven("https://maven.wagyourtail.xyz/releases") }
            filter {
                includeGroupAndSubgroups("xyz.wagyourtail")
            }
        }
        maven("https://maven.taumc.org/releases") {
            content {
                includeGroupAndSubgroups("org.taumc")
            }
        }
        exclusiveContent {
            forRepository { maven("https://nexus.gtnewhorizons.com/repository/public/") }
            filter {
                includeGroupAndSubgroups("com.gtnewhorizons")
            }
        }
        maven("https://maven.minecraftforge.net/") {
            content {
                includeGroupAndSubgroups("net.minecraftforge")
            }
        }
        maven {
            name = "sponge"
            url = uri("https://repo.spongepowered.org/maven-public/")
            content {
                includeGroupAndSubgroups("org.spongepowered")
            }
        }
    }

    plugins {
        id("org.taumc.gradle.versioning") version(extra["taugradle_version"].toString())
        id("org.taumc.gradle.publishing") version(extra["taugradle_version"].toString())
        // TauMC fork of ModDevGradle, which carries the 1.16.5 support.
        id("org.taumc.moddev") version(extra["moddevgradle_version"].toString())
        id("org.taumc.moddev.legacyforge") version(extra["moddevgradle_version"].toString())

        id("net.fabricmc.fabric-loom-remap") version(extra["loom_version"].toString())

        id("com.gtnewhorizons.retrofuturagradle") version(extra["rfg_version"].toString())
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
    id("dev.kikugie.stonecutter") version(extra["stonecutter_version"].toString())
}

rootProject.name = "embeddium-continuous"

includeBuild("plugins/celeritas-mdg-plugin")
include("common", "modern")

// The sole supported runtime target. Upstream preprocessing is retained for source parity.
stonecutter {
    create(":modern") {
        version("1.20.1-forge", "1.20.1").buildscript = "build.mdg.gradle.kts"
    }
}
