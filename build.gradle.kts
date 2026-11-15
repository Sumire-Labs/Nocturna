import org.embeddedt.embeddium.gradle.versioning.ProjectVersioner

plugins { base }

version = ProjectVersioner.computeVersion(projectDir, properties)
println("Embeddium Continuous (Celeritas base): $version")

tasks.assemble { dependsOn(":modern:1.20.1-forge:packageJar") }
tasks.check { dependsOn(":common:test", ":modern:1.20.1-forge:check") }
tasks.register("packageJar") { dependsOn(":modern:1.20.1-forge:packageJar") }
tasks.register("runClient") { dependsOn(":modern:1.20.1-forge:runClient") }
