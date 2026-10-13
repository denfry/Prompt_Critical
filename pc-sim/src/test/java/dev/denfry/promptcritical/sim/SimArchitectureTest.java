package dev.denfry.promptcritical.sim;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Правила зависимостей {@code pc-sim} (05 §5.1.2, T-05-01). */
@AnalyzeClasses(packages = "dev.denfry.promptcritical.sim", importOptions = ImportOption.DoNotIncludeTests.class)
public class SimArchitectureTest {
    @ArchTest
    static final ArchRule noMinecraft = noClasses().that().resideInAPackage("..sim..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "net.minecraft..", "com.mojang..", "net.neoforged..", "dan200.computercraft..", "com.simibubi..");

    @ArchTest
    static final ArchRule noUnseededRandom = noClasses().that().resideInAPackage("..sim..")
            .should().callMethod(Math.class, "random")
            .orShould().callConstructor(java.util.Random.class)
            .because("детерминизм §5.2.4: только SplittableRandom с выведенным зерном");

    @ArchTest
    static final ArchRule noWallClock = noClasses().that().resideInAPackage("..sim..")
            .and().resideOutsideOfPackage("..sim.core.profile..")
            .should().callMethod(System.class, "currentTimeMillis")
            .orShould().callMethod(System.class, "nanoTime")
            .because("время симуляции — только SimClock");

    @ArchTest
    static final ArchRule threadsOnlyInExec = noClasses().that().resideInAPackage("..sim..")
            .and().resideOutsideOfPackage("..sim.core.exec..")
            .should().callConstructor(Thread.class);

    // Слои физики и систем появятся с M1; пока они пусты, поэтому слои опциональны.
    @ArchTest
    static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("core").definedBy("..sim.core..")
            .layer("physics").definedBy("..sim.neutronics..", "..sim.thermal..", "..sim.thermo..",
                    "..sim.hydraulics..", "..sim.reactor..", "..sim.nuclide..")
            .layer("systems").definedBy("..sim.protection..", "..sim.instrument..", "..sim.alarm..",
                    "..sim.acoustics..", "..sim.accident..", "..sim.radiation..", "..sim.chem..",
                    "..sim.enrichment..", "..sim.consequence..", "..sim.journal..")
            .whereLayer("physics").mayOnlyAccessLayers("core") // физика не знает о приборах и сигнализации
            .whereLayer("physics").mayOnlyBeAccessedByLayers("systems", "core");
}
