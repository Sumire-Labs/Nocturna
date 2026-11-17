plugins {
    base
    id("com.cleanroommc.versioning")
}


tasks.assemble { dependsOn(":modern:1.20.1-forge:packageJar") }
tasks.check { dependsOn(":common:test", ":modern:1.20.1-forge:check") }
tasks.register("packageJar") { dependsOn(":modern:1.20.1-forge:packageJar") }
tasks.register("runClient") { dependsOn(":modern:1.20.1-forge:runClient") }
