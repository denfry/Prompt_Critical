plugins {
    `java-library`
    alias(libs.plugins.moddev)
}

evaluationDependsOn(":pc-sim")
evaluationDependsOn(":pc-api")

val modId: String = providers.gradleProperty("mod_id").get()
val simMain: SourceSet = project(":pc-sim").sourceSets.main.get()
val apiMain: SourceSet = project(":pc-api").sourceSets.main.get()

base { archivesName = modId }

repositories {
    maven("https://maven.createmod.net") { name = "Create / Ponder / Flywheel" }
    maven("https://maven.ithundxr.dev/snapshots") { name = "Registrate" }
    maven("https://maven.squiddev.cc") {
        name = "CC: Tweaked"
        content { includeGroup("cc.tweaked") }
    }
}

val gametest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[gametest.compileClasspathConfigurationName].extendsFrom(configurations.compileClasspath.get())
configurations[gametest.runtimeClasspathConfigurationName].extendsFrom(configurations.runtimeClasspath.get())

neoForge {
    version = libs.versions.neoforge.get()
    parchment {
        minecraftVersion = libs.versions.minecraft.get()
        mappingsVersion = libs.versions.parchment.get()
    }
    addModdingDependenciesTo(gametest)

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(gametest)
            sourceSet(simMain) // pc-sim и pc-api — часть мода в dev-запусках (05 §5.1.1)
            sourceSet(apiMain)
        }
    }

    runs {
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.INFO
        }
        register("client") {
            client()
            sourceSet = gametest
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            // ./gradlew :pc-mod:runClient -PquickPlay=<мир> — сразу войти в мир из run/saves
            providers.gradleProperty("quickPlay").orNull?.let { programArguments.addAll("--quickPlaySingleplayer", it) }
        }
        register("server") {
            server()
            programArgument("--nogui")
        }
        register("gameTestServer") {
            type = "gameTestServer"
            sourceSet = gametest
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        register("data") {
            data()
            programArguments.addAll(
                "--mod", modId, "--all",
                "--output", file("src/generated/resources").absolutePath,
                "--existing", file("src/main/resources").absolutePath,
            )
        }
    }
}

sourceSets.main.get().resources.srcDir("src/generated/resources")

dependencies {
    implementation(project(":pc-sim"))
    implementation(project(":pc-api"))

    // Create 6: внешний jar содержит Ponder/Flywheel/Registrate в jar-in-jar,
    // в dev-окружении их нужно подключать явно.
    implementation(libs.create) { isTransitive = false }
    implementation(libs.ponder)
    implementation(libs.registrate)
    compileOnly(libs.flywheel.api)
    runtimeOnly(libs.flywheel)

    // CC: Tweaked — опциональная интеграция (05 §5.1.2): компилируемся против API,
    // сам мод нужен только в dev-запусках.
    compileOnly(libs.cctweaked.core.api)
    compileOnly(libs.cctweaked.common.api)
    compileOnly(libs.cctweaked.forge.api)
    runtimeOnly(libs.cctweaked)
}

tasks.processResources {
    val props = mapOf(
        "mod_id" to modId,
        "mod_name" to providers.gradleProperty("mod_name").get(),
        "mod_license" to providers.gradleProperty("mod_license").get(),
        "mod_version" to project.version.toString(),
        "mod_authors" to providers.gradleProperty("mod_authors").get(),
        "mod_description" to providers.gradleProperty("mod_description").get(),
        "neoforge_version" to libs.versions.neoforge.get(),
        "minecraft_version" to libs.versions.minecraft.get(),
        "create_version" to libs.versions.create.get().substringBefore('-'),
        "cctweaked_version" to libs.versions.cctweaked.get(),
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
}

// pc-sim и pc-api упаковываются классами в jar мода: один мод, один classloader (05 §5.1.1).
tasks.jar {
    from(simMain.output)
    from(apiMain.output)
}
