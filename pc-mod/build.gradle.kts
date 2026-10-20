import java.awt.Color
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula

// Формулы справочника рисует JLaTeXMath прямо в сборке (11 §11.12), TeX в системе не нужен.
buildscript {
    repositories { mavenCentral() }
    dependencies { classpath(libs.jlatexmath) }
}

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
        // ./gradlew :pc-mod:runFilm -PquickPlay=<мир> — съёмка роликов по film.json (FilmRecorder) в своём каталоге run-film
        register("film") {
            client()
            sourceSet = gametest
            gameDirectory = project.file("run-film")
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

// Формулы справочника (11 §11.1.2, §11.12): src/main/formulas/<имя>.tex → PNG в generated, вдвое крупнее, чем на
// листе. Страница formula ссылается на <мод>:<имя>; формула без исходника — ошибка сборки.
val formulaSrc = layout.projectDirectory.dir("src/main/formulas")
val formulaOut = layout.projectDirectory.dir("src/generated/resources/assets/$modId/textures/gui/handbook/formula")
val handbookEntries = layout.projectDirectory.dir("src/main/resources/data/$modId/handbook/entries")
val renderHandbookFormulas by tasks.registering {
    group = "build"
    description = "Renders handbook formula pages from LaTeX to PNG"
    inputs.dir(formulaSrc)
    inputs.dir(handbookEntries)
    outputs.dir(formulaOut)
    doLast {
        System.setProperty("java.awt.headless", "true")
        val src = formulaSrc.asFile
        val out = formulaOut.asFile
        val refs = handbookEntries.asFile.walk().filter { it.extension == "json" }
            .flatMap { Regex("\"formula\"\\s*:\\s*\"[a-z0-9_.-]+:([a-z0-9_/.-]+)\"").findAll(it.readText()).map { m -> m.groupValues[1] } }
            .toSet()
        val missing = refs.filter { !src.resolve("$it.tex").isFile }
        if (missing.isNotEmpty()) throw GradleException("handbook formulas without .tex: $missing")
        out.deleteRecursively()
        src.walk().filter { it.extension == "tex" }.forEach { f ->
            val name = f.relativeTo(src).invariantSeparatorsPath.removeSuffix(".tex")
            val icon = TeXFormula(f.readText().trim())
                .createTeXIcon(TeXConstants.STYLE_DISPLAY, 36f)
            icon.insets = Insets(2, 2, 2, 2)
            icon.setForeground(Color(0x2B, 0x2B, 0x2B))
            val img = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            icon.paintIcon(null, g, 0, 0)
            g.dispose()
            val target = out.resolve("$name.png")
            target.parentFile.mkdirs()
            ImageIO.write(img, "png", target)
        }
    }
}
tasks.processResources { dependsOn(renderHandbookFormulas) }

// pc-sim и pc-api упаковываются классами в jar мода: один мод, один classloader (05 §5.1.1).
tasks.jar {
    from(simMain.output)
    from(apiMain.output)
}

// Переводы приходят pull request'ами (CONTRIBUTING.md, «Translations»): проверка в `check`, отчёт — translationStatus.
val langDirs = listOf(
    layout.projectDirectory.dir("src/generated/resources/assets/$modId/lang"),
    layout.projectDirectory.dir("src/main/resources/assets/$modId/lang"),
)
val handbookLangDir = layout.projectDirectory.dir("src/main/resources/data/$modId/handbook/lang")
val handbookPagesDir = layout.projectDirectory.dir("src/main/resources/data/$modId/handbook/pages")

/** Плоская таблица ключ → строка; не-строковое значение — ошибка. */
fun readLangTable(f: File): Map<String, String> {
    @Suppress("UNCHECKED_CAST")
    val m = groovy.json.JsonSlurper().parse(f, "UTF-8") as? Map<String, Any?>
        ?: throw GradleException("${f.name}: expected a JSON object")
    return m.mapValues { (k, v) -> v as? String ?: throw GradleException("${f.path}: value of $k is not a string") }
}

/** Таблицы по языкам: assets/lang (datagen + ручные) и handbook/lang. */
fun langTables(dirs: List<File>): Map<String, Map<String, String>> {
    val out = sortedMapOf<String, MutableMap<String, String>>()
    for (d in dirs) d.listFiles { f -> f.extension == "json" }?.forEach { f ->
        out.getOrPut(f.nameWithoutExtension) { linkedMapOf() }.putAll(readLangTable(f))
    }
    return out
}

val placeholder = Regex("""%(\d+\$)?[sdf%]""")
val mdLink = Regex("""\]\(([^)\s]+)\)""")

val checkTranslations by tasks.registering {
    group = "verification"
    description = "Checks translation files against English: no unknown keys, same placeholders and page links"
    val assetDirs = langDirs.map { it.asFile }
    val bookLang = handbookLangDir.asFile
    val pages = handbookPagesDir.asFile
    inputs.files(assetDirs + listOf(bookLang, pages)).optional()
    doLast {
        val problems = mutableListOf<String>()
        for ((name, tables) in listOf("assets/lang" to langTables(assetDirs), "handbook/lang" to langTables(listOf(bookLang)))) {
            val en = tables["en_us"] ?: throw GradleException("$name: no en_us")
            for ((lang, t) in tables) {
                if (lang == "en_us") continue
                if (!Regex("[a-z]{2,3}_[a-z]{2,4}").matches(lang)) problems += "$name/$lang.json: file name must be a lowercase Minecraft locale code"
                for ((k, v) in t) {
                    val src = en[k]
                    if (src == null) problems += "$name/$lang.json: unknown key $k"
                    else if (placeholder.findAll(src).map { it.value }.sorted().toList()
                        != placeholder.findAll(v).map { it.value }.sorted().toList())
                        problems += "$name/$lang.json: placeholders of $k differ from English"
                }
            }
        }
        val enPages = pages.resolve("en_us")
        pages.listFiles { f -> f.isDirectory && f.name != "en_us" }?.forEach { dir ->
            dir.walk().filter { it.extension == "md" }.forEach { f ->
                val rel = f.relativeTo(dir).invariantSeparatorsPath
                val en = enPages.resolve(rel)
                if (!en.isFile) problems += "pages/${dir.name}/$rel: no English page"
                else if (mdLink.findAll(en.readText()).map { it.groupValues[1] }.sorted().toList()
                    != mdLink.findAll(f.readText()).map { it.groupValues[1] }.sorted().toList())
                    problems += "pages/${dir.name}/$rel: link targets differ from English"
            }
        }
        if (problems.isNotEmpty()) throw GradleException("translation problems:\n  " + problems.joinToString("\n  "))
    }
}
tasks.named("check") { dependsOn(checkTranslations) }

val translationStatus by tasks.registering {
    group = "help"
    description = "Prints how much of each language is translated"
    val assetDirs = langDirs.map { it.asFile }
    val bookLang = handbookLangDir.asFile
    val pages = handbookPagesDir.asFile
    doLast {
        val strings = langTables(assetDirs + listOf(bookLang))
        val en = strings["en_us"] ?: emptyMap()
        val enPages = pages.resolve("en_us").walk().filter { it.extension == "md" }
            .map { it.relativeTo(pages.resolve("en_us")).invariantSeparatorsPath }.toSet()
        val langs = (strings.keys + (pages.listFiles { f -> f.isDirectory }?.map { it.name } ?: emptyList())).toSortedSet()
        println("language   strings            handbook pages")
        for (lang in langs) {
            if (lang == "en_us") continue
            val t = strings[lang] ?: emptyMap()
            val done = en.keys.count { it in t }
            val dir = pages.resolve(lang)
            val p = enPages.count { dir.resolve(it).isFile }
            println("%-10s %5d / %-5d %3d %%   %3d / %d".format(lang, done, en.size, if (en.isEmpty()) 0 else done * 100 / en.size, p, enPages.size))
        }
    }
}
