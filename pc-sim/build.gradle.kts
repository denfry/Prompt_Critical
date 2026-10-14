plugins {
    `java-library`
    alias(libs.plugins.jmh)
}

dependencies {
    // НИКАКИХ runtime-зависимостей (05 §5.1.2). Только тесты.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jqwik)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform { includeEngines("junit-jupiter", "jqwik", "archunit") }
}

// Таблицы IAPWS-IF97 строятся при сборке из эталонной реализации (01 §1.3.1).
// Генератору нужны только классы sim.thermo.if97 и формат файла: копируем их
// отдельно, чтобы правки остального pc-sim не перезапускали генерацию.
val waterTableGenClasses = tasks.register<Sync>("stageWaterTableGenerator") {
    from(sourceSets.main.get().output.classesDirs) {
        include("dev/denfry/promptcritical/sim/thermo/if97/**", "dev/denfry/promptcritical/sim/thermo/WaterTableFormat*")
    }
    into(layout.buildDirectory.dir("water-table-generator"))
}

val waterTablesDir = layout.buildDirectory.dir("generated/water-tables")

val generateWaterTables = tasks.register<JavaExec>("generateWaterTables") {
    group = "build"
    description = "Generates IF97 property tables (data/promptcritical/physics/if97_tables.bin)."
    classpath(waterTableGenClasses)
    mainClass = "dev.denfry.promptcritical.sim.thermo.if97.WaterTableGenerator"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    val out = waterTablesDir.map { it.file("data/promptcritical/physics/if97_tables.bin") }
    outputs.file(out)
    outputs.cacheIf { true }
    argumentProviders.add(CommandLineArgumentProvider { listOf(out.get().asFile.absolutePath) })
}

sourceSets.main {
    resources.srcDir(files(waterTablesDir).builtBy(generateWaterTables))
}
