@file:Suppress("UnstableApiUsage")

val modId = project.property("mod_id") as String

provided("org.jetbrains", "annotations")
provided("commons-io", "commons-io")
// Youer provides these on its own classpath - don't bundle them
provided("net.kyori", "adventure-api")
provided("net.kyori", "adventure-key")
provided("net.kyori", "adventure-text-serializer-gson")
provided("net.kyori", "adventure-text-serializer-json")
provided("net.kyori", "adventure-text-serializer-legacy")
provided("net.kyori", "examination-api")
provided("net.kyori", "examination-string")

architectury {
    platformSetupLoomIde()
    neoForge()
}

val common: Configuration by configurations.creating
// Without this, the mixin config isn't read properly with the runServer neoforge task
val developmentNeoForge: Configuration = configurations.getByName("developmentNeoForge")
val includeTransitive: Configuration = configurations.getByName("includeTransitive")

configurations {
    compileClasspath.get().extendsFrom(configurations["common"])
    runtimeClasspath.get().extendsFrom(configurations["common"])
    developmentNeoForge.extendsFrom(configurations["common"])
}

dependencies {
    // See https://github.com/google/guava/issues/6618
    modules {
        module("com.google.guava:listenablefuture") {
            replacedBy("com.google.guava:guava", "listenablefuture is part of guava")
        }
    }

    common(project(":shared", configuration = "namedElements")) { isTransitive = false }
    neoForge(libs.neoforge)
    compileOnly(libs.geyser.api)

    shadow(project(path = ":shared", configuration = "transformProductionNeoForge")) { isTransitive = false }
    // JiJ (jar-in-jar) instead of shadow: NeoForge deduplicates identical JiJ jars
    // across mods, so Floodgate and Hydraulic can share events/lmbda/etc.
    include(libs.geyser.api)
    include("org.geysermc.api:base-api:1.0.3") // Geyser 2.11 API dependency (org.geysermc.api)
    include("org.geysermc.event:events:1.1-SNAPSHOT") // Geyser 2.11 event bus
    include("org.lanternpowered:lmbda:2.0.0") // LambdaFactory (was provided by Geyser mod)
    include("org.cloudburstmc.math:immutable:2.0") // Vector3f etc. for GeyserEntityDataTypes
    include("org.geysermc.cumulus:cumulus:1.1.2-SNAPSHOT") // shared with Floodgate (forms API)

    // TODO fix neoforge runServer task
    modRuntimeOnly(libs.pack.converter)
    includeTransitive(libs.pack.converter)
}

tasks {
    remapJar {
        dependsOn(shadowJar)
        inputFile.set(shadowJar.get().archiveFile)
        archiveBaseName.set("${modId}-neoforge")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    shadowJar {
        archiveClassifier.set("dev-shadow")
    }

    jar {
        archiveClassifier.set("dev")
    }
}

sourceSets {
    main {
        resources {
            srcDirs(project(":shared").sourceSets["main"].resources.srcDirs)
        }
    }
}