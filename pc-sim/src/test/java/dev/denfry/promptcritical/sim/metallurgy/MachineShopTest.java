package dev.denfry.promptcritical.sim.metallurgy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.metallurgy.Machining.WorkMaterial;
import dev.denfry.promptcritical.sim.metallurgy.ProductionToolUnit.Kind;
import dev.denfry.promptcritical.sim.metallurgy.ProductionToolUnit.Reject;
import dev.denfry.promptcritical.sim.metallurgy.ProductionToolUnit.Result;
import dev.denfry.promptcritical.sim.metallurgy.ProductionToolUnit.Workpiece;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Станочный парк: фрезерный 6Р82, строгальный 7305, плоскошлифовальный 3Г71 на валу Create. */
class MachineShopTest {
    private WorkerPool pool;
    private SimulationWorld world;
    private FoundrySubsystem shop;

    @BeforeEach
    void setUp() {
        pool = WorkerPool.direct();
        world = newWorld();
        shop = world.subsystem(FoundrySubsystem.class);
    }

    @AfterEach
    void tearDown() {
        world.close();
        pool.close();
    }

    private SimulationWorld newWorld() {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new FoundrySubsystem());
        return w;
    }

    private ProductionToolUnit add(String type, int x) {
        SimId id = world.topology().addDevice(type, new GridPos(x, 64, 0));
        return (ProductionToolUnit) shop.unit(world.topology(), id);
    }

    /** {@code seconds} с валом {@code rpm} у всех станков. */
    private void run(double seconds, double rpm, ProductionToolUnit... units) {
        int ticks = (int) Math.round(seconds * 20);
        for (int i = 0; i < ticks; i++) {
            for (ProductionToolUnit u : units) u.shaft.sample(rpm, true, world.clock().tick());
            world.tick(ExternalInputs.NONE);
        }
    }

    // --- физика по формулам ---

    @Test
    void kienzleThinChipsCutHarder() {
        // k_c = k_c1.1 · h^-mc: при h = 1 мм — сама k_c1.1, при h = 0,1 мм — в 10^0,25 ≈ 1,78 раза больше у чугуна
        assertThat(Machining.kienzleKc(WorkMaterial.STEEL_45, 1)).isEqualTo(1450);
        assertThat(Machining.kienzleKc(WorkMaterial.CAST_IRON, 0.1) / 1020).isCloseTo(Math.pow(10, 0.25), within(1e-9));
        assertThat(Machining.wearForceFactor(1)).isEqualTo(1.3);
    }

    @Test
    void millingMeanChipThicknessFollowsSandvik() {
        // a_e = 11,25 мм фрезой Ø80: угол контакта 44,04°, h_m = 360·0,08·11,25/(π·80·44,04) ≈ 0,0293 мм
        assertThat(Math.toDegrees(Milling.engagementAngle(11.25, 80))).isCloseTo(44.04, within(0.01));
        assertThat(Milling.meanChipThicknessMm(0.08, 11.25, 80)).isCloseTo(0.0293, within(1e-4));
        assertThat(Milling.engagementShare(80, 80)).isCloseTo(0.5, within(1e-12));   // паз во весь диаметр — пол-оборота
    }

    @Test
    void roughnessGrowsAsTheSquareOfFeedAndMapsToIt() {
        // строгание r = 1 мм: f = 0,5 → R_t = 31,25 мкм, Ra ≈ 7,8; вдвое меньше подача — вчетверо глаже
        assertThat(SurfaceFinish.singlePointRaUm(0.5, 1, 0)).isCloseTo(7.8125, within(1e-9));
        assertThat(SurfaceFinish.singlePointRaUm(0.8, 1, 0) / SurfaceFinish.singlePointRaUm(0.4, 1, 0)).isCloseTo(4, within(1e-9));
        assertThat(SurfaceFinish.singlePointRaUm(0.05, 1, 0)).isEqualTo(SurfaceFinish.RA_FLOOR_UM);   // тоньше станок не даст
        assertThat(SurfaceFinish.singlePointRaUm(0.5, 1, 1)).isCloseTo(1.5 * 7.8125, within(1e-9));   // тупой резец рвёт
        assertThat(SurfaceFinish.itGrade(0.4)).isEqualTo(6);
        assertThat(SurfaceFinish.itGrade(1.6)).isEqualTo(8);
        assertThat(SurfaceFinish.itGrade(7.8)).isEqualTo(12);
        assertThat(SurfaceFinish.itGrade(40)).isEqualTo(SurfaceFinish.IT_ROUGH);
    }

    @Test
    void grindingBurnsWhenTheCutIsDeepOrTheWheelIsLoaded() {
        double vs = Grinding.wheelSpeedMS(2688);
        assertThat(vs).isCloseTo(35.19, within(0.01));
        // свежий круг, a = 0,01: u ≈ 13,8 + 1,87 = 15,7 Дж/мм³ при пределе ≈ 23,3 — без прижога
        assertThat(Grinding.specificEnergy(0.01, vs, 10, 0)).isCloseTo(15.67, within(0.02));
        assertThat(Grinding.burnLimit(0.01, 10)).isCloseTo(23.34, within(0.02));
        // a = 0,04 — предел падает до ≈ 12,3, ниже одной энергии стружкообразования: прижог и свежим кругом
        assertThat(Grinding.burnLimit(0.04, 10)).isLessThan(Grinding.U_CHIP);
        // засаленный круг на чистовой подаче — прижог
        assertThat(Grinding.specificEnergy(0.01, vs, 10, 1)).isGreaterThan(Grinding.burnLimit(0.01, 10));
        // Ra 3Г71 по паспорту 0,32–0,63
        assertThat(Grinding.raUm(0.01, vs, 10)).isBetween(0.32, 0.63);
    }

    // --- станки в мире ---

    @Test
    void millingCutsAGearInAboutAMinuteAndTheCutterBurnsWhenOversped() {
        ProductionToolUnit mill = add(FoundrySubsystem.MILLING_MACHINE, 0);
        assertThat(mill.kind()).isEqualTo(Kind.MILLING);
        mill.insertTool(0);
        Workpiece gear = new Workpiece(MachinedPart.SPUR_GEAR, WorkMaterial.CAST_IRON, true);
        assertThat(mill.load(new Workpiece(MachinedPart.MACHINE_SLIDEWAY, WorkMaterial.CAST_IRON, true))).isFalse();
        assertThat(mill.load(gear)).isTrue();
        // вал 64 → шпиндель 128, v_c 32,2 м/мин; f_z 0,08: v_f 122,9 мм/мин, Q = 11,25·7,85·122,9/60 ≈ 181 мм³/с → 66 с
        run(60, 64, mill);
        assertThat(mill.speed()).isEqualTo(128);
        assertThat(mill.cuttingSpeed()).isCloseTo(32.17, within(0.01));
        double q = 11.25 * 7.85 * 0.08 * 12 * 128 / 60;
        double kc = 1020 * Math.pow(Milling.meanChipThicknessMm(0.08, 11.25, 80), -0.25);
        assertThat(mill.powerW()).isCloseTo(q * kc / 1000 + 7_500 * 0.02, within(1.0));
        assertThat(mill.done()).isFalse();
        run(7, 64, mill);
        assertThat(mill.done()).isTrue();
        Result r = mill.unload();
        assertThat(r.accepted()).isTrue();
        assertThat(r.raUm()).isCloseTo(SurfaceFinish.RA_FLOOR_UM, within(0.01));   // предел станка, резец почти новый
        assertThat(r.itGrade()).isEqualTo(8);
        // T = (63/32,2)^8 ≈ 216 мин, зуб режет 12 % оборота: за деталь износ < 0,1 %
        assertThat(mill.toolWear()).isBetween(0.0003, 0.001);

        ProductionToolUnit fast = add(FoundrySubsystem.MILLING_MACHINE, 1);
        fast.insertTool(0);
        fast.load(gear);
        run(20, 256, fast);                                          // v_c 129 м/мин: стойкость доли минуты
        assertThat(fast.toolWear()).isEqualTo(1.0);
        assertThat(fast.running()).isFalse();
        assertThat(fast.done()).isFalse();
    }

    @Test
    void roughMillingFeedScrapsAGearButIsFineForAKeyway() {
        ProductionToolUnit mill = add(FoundrySubsystem.MILLING_MACHINE, 0);
        mill.insertTool(0);
        mill.setFeedStep(3);                                          // f_z 0,20: f_o 2,4 мм, Ra ≈ 4,5 мкм
        mill.load(new Workpiece(MachinedPart.SPUR_GEAR, WorkMaterial.CAST_IRON, true));
        run(40, 64, mill);
        Result gear = mill.unload();
        assertThat(gear.reject()).isEqualTo(Reject.ROUGH);
        assertThat(gear.raUm()).isEqualTo(4.5);

        mill.load(new Workpiece(MachinedPart.KEYED_SHAFT, WorkMaterial.STEEL_45, true));
        run(20, 64, mill);
        Result shaft = mill.unload();
        assertThat(shaft.accepted()).isTrue();                        // паз допускает Ra 6,3
        assertThat(shaft.itGrade()).isEqualTo(11);
    }

    @Test
    void shaperPlanesOnTheCuttingStrokeAndFeedDecidesTheFinish() {
        ProductionToolUnit shaper = add(FoundrySubsystem.SHAPING_MACHINE, 0);
        shaper.insertTool(0);
        shaper.load(new Workpiece(MachinedPart.MACHINE_SLIDEWAY, WorkMaterial.CAST_IRON, true));
        // вал 64 → 32 дв. хода/мин, v_c = 450·32/600 = 24 м/мин; f 0,3, a_p 2: съём 144 мм³/с → 15 000 за ≈ 104 с
        run(10, 64, shaper);
        assertThat(shaper.speed()).isEqualTo(32);
        assertThat(shaper.cuttingSpeed()).isCloseTo(24, within(1e-9));
        double force = Shaping.cuttingForceN(WorkMaterial.CAST_IRON, 0.3, 2, shaper.toolWear());
        assertThat(force).isCloseTo(901, within(2.0));
        assertThat(shaper.powerW()).isCloseTo(force * 24 / 60 * 0.6 + 4_000 * 0.02, within(0.5));
        run(95, 64, shaper);
        Result way = shaper.unload();
        assertThat(way).isNotNull();
        assertThat(way.accepted()).isTrue();                          // Ra 2,8 ≤ 3,2
        assertThat(way.raUm()).isEqualTo(2.81);

        shaper.setFeedStep(2);                                        // f 0,5: Ra 7,8 — для направляющей груба
        shaper.load(new Workpiece(MachinedPart.MACHINE_SLIDEWAY, WorkMaterial.CAST_IRON, true));
        run(65, 64, shaper);
        assertThat(shaper.unload().reject()).isEqualTo(Reject.ROUGH);
        shaper.load(new Workpiece(MachinedPart.PLANED_PLATE, WorkMaterial.STEEL_45, true));
        run(55, 64, shaper);
        Result plate = shaper.unload();
        assertThat(plate.accepted()).isTrue();                        // под шлифование хватит Ra 12,5
        assertThat(plate.material()).isEqualTo(WorkMaterial.STEEL_45);
    }

    @Test
    void castingDefectsComeOutAsScrap() {
        ProductionToolUnit shaper = add(FoundrySubsystem.SHAPING_MACHINE, 0);
        shaper.insertTool(0);
        shaper.setFeedStep(0);
        CastingSpec porous = new CastingSpec(CastingKind.MACHINE_BED, "gray_iron", 0, CastingDefect.GAS_POROSITY, 0, 5);
        assertThat(MachinedPart.fromCasting(porous)).isEqualTo(MachinedPart.MACHINE_SLIDEWAY);
        shaper.load(new Workpiece(MachinedPart.fromCasting(porous), WorkMaterial.of(CastAlloy.GRAY_IRON),
                porous.defect() == CastingDefect.NONE));
        run(160, 64, shaper);
        assertThat(shaper.unload().reject()).isEqualTo(Reject.DEFECT);
        // заготовку шестерни сначала точат; отбелённый чугун не режется
        CastingSpec blank = new CastingSpec(CastingKind.GEAR_BLANK, "gray_iron", 0, CastingDefect.NONE, 0, 6);
        assertThat(MachinedPart.fromCasting(blank)).isNull();
        assertThat(MachinedPart.fromCasting(blank.machined(CastingSpec.TURNED))).isEqualTo(MachinedPart.SPUR_GEAR);
        assertThat(MachinedPart.fromCasting(new CastingSpec(CastingKind.BASE_PLATE, "gray_iron", 0, CastingDefect.CHILL, 0, 7))).isNull();
    }

    @Test
    void grinderLoadsTheWheelBurnsDeepCutsAndBurstsWhenOversped() {
        ProductionToolUnit grinder = add(FoundrySubsystem.SURFACE_GRINDER, 0);
        grinder.insertTool(0);
        Workpiece plate = new Workpiece(MachinedPart.GROUND_PLATE, WorkMaterial.CAST_IRON, true);
        grinder.load(plate);
        // a 0,01: Q = 0,01·4·10 000/60 ≈ 6,67 мм³/с → 1000 мм³ за 150 с; P = u·Q ≈ 15,7·6,67 ≈ 104 Вт
        run(10, 64, grinder);
        assertThat(grinder.speed()).isEqualTo(2688);
        assertThat(grinder.powerW()).isCloseTo(15.67 * 1000.0 / 150 + 2_200 * 0.02, within(0.5));
        run(141, 64, grinder);
        Result r = grinder.unload();
        assertThat(r.accepted()).isTrue();
        assertThat(r.itGrade()).isEqualTo(6);                         // Ra ≈ 0,38
        assertThat(grinder.toolWear()).isCloseTo(0.05, within(0.002));   // 1000 из 20 000 мм³ до правки

        grinder.setFeedStep(3);                                       // a 0,04 — прижог
        grinder.load(plate);
        run(40, 64, grinder);
        assertThat(grinder.unload().reject()).isEqualTo(Reject.BURN);

        grinder.setFeedStep(1);
        grinder.load(plate);
        run(1, 100, grinder);                                         // круг 4200 об/мин, 55 м/с > 52,5 — разрыв
        assertThat(grinder.takeWheelBurst()).isTrue();
        assertThat(grinder.toolWear()).isNaN();
        assertThat(grinder.running()).isFalse();
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        ProductionToolUnit mill = add(FoundrySubsystem.MILLING_MACHINE, 0);
        ProductionToolUnit grinder = add(FoundrySubsystem.SURFACE_GRINDER, 1);
        mill.insertTool(0.25);
        mill.setFeedStep(2);
        mill.load(new Workpiece(MachinedPart.KEYED_SHAFT, WorkMaterial.STEEL_45, true));
        grinder.insertTool(0.5);
        grinder.setFeedStep(3);
        grinder.load(new Workpiece(MachinedPart.GROUND_PLATE, WorkMaterial.STEEL_45, true));
        run(5, 64, mill, grinder);
        assertThat(grinder.burnt()).isTrue();
        MutableStateTree saved = world.saveTree();
        long hash = world.stateHash();

        SimulationWorld copy = newWorld();
        copy.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        assertThat(copy.stateHash()).isEqualTo(hash);
        FoundrySubsystem shop2 = copy.subsystem(FoundrySubsystem.class);
        ProductionToolUnit mill2 = (ProductionToolUnit) shop2.unit(world.topology().deviceAt(0));
        ProductionToolUnit grinder2 = (ProductionToolUnit) shop2.unit(world.topology().deviceAt(1));
        assertThat(mill2.feedStep()).isEqualTo(2);
        assertThat(mill2.toolWear()).isEqualTo(mill.toolWear());
        assertThat(mill2.progress()).isEqualTo(mill.progress());
        assertThat(mill2.work()).isEqualTo(mill.work());
        assertThat(grinder2.burnt()).isTrue();
        assertThat(grinder2.raUm()).isEqualTo(grinder.raUm());
        copy.close();
    }
}
