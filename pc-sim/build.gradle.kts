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

// JMH-бенчмарки горячего цикла (05 §5.7.6): ./gradlew :pc-sim:jmh. Сценарии ПР-300 и эталонной сети —
// общие с тестами (src/test), поэтому тестовые классы входят в classpath бенчмарков.
jmh {
    includeTests = true
    profilers = listOf("gc")
    resultFormat = "JSON"
}

tasks.test {
    useJUnitPlatform { includeEngines("junit-jupiter", "jqwik", "archunit") }
}

// Таблицы IAPWS-IF97 и критического расхода строятся при сборке из эталонной
// реализации (01 §1.3.1, §1.3.9). Генераторам нужны только классы sim.thermo.if97
// и форматы файлов: копируем их отдельно, чтобы правки остального pc-sim не
// перезапускали генерацию.
val waterTableGenClasses = tasks.register<Sync>("stageWaterTableGenerator") {
    from(sourceSets.main.get().output.classesDirs) {
        include(
            "dev/denfry/promptcritical/sim/thermo/if97/**",
            "dev/denfry/promptcritical/sim/thermo/WaterTableFormat*",
            "dev/denfry/promptcritical/sim/thermo/CriticalFlowFormat*",
        )
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

val criticalFlowDir = layout.buildDirectory.dir("generated/critical-flow")

val generateCriticalFlowTables = tasks.register<JavaExec>("generateCriticalFlowTables") {
    group = "build"
    description = "Generates HEM/Moody critical mass flux tables (data/promptcritical/physics/critical_flow.bin)."
    classpath(waterTableGenClasses)
    mainClass = "dev.denfry.promptcritical.sim.thermo.if97.CriticalFlowGenerator"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    val out = criticalFlowDir.map { it.file("data/promptcritical/physics/critical_flow.bin") }
    outputs.file(out)
    outputs.cacheIf { true }
    argumentProviders.add(CommandLineArgumentProvider { listOf(out.get().asFile.absolutePath) })
}

sourceSets.main {
    resources.srcDir(files(waterTablesDir).builtBy(generateWaterTables))
    resources.srcDir(files(criticalFlowDir).builtBy(generateCriticalFlowTables))
}
