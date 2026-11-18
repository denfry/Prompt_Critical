package dev.denfry.promptcritical.sim.automation.twin;

import dev.denfry.promptcritical.sim.automation.twin.Fault.ForcedInput;
import dev.denfry.promptcritical.sim.automation.twin.Fault.SensorFault;
import dev.denfry.promptcritical.sim.automation.twin.Fault.SensorMode;
import dev.denfry.promptcritical.sim.automation.twin.Fault.StuckActuator;
import dev.denfry.promptcritical.sim.automation.twin.IoMap.Cmp;
import dev.denfry.promptcritical.sim.automation.twin.IoMap.Di;
import dev.denfry.promptcritical.sim.automation.twin.IoMap.Do;
import dev.denfry.promptcritical.sim.automation.twin.IoMap.DoMode;
import dev.denfry.promptcritical.sim.automation.twin.Scenario.Loop;
import dev.denfry.promptcritical.sim.automation.twin.Scenario.Margin;
import dev.denfry.promptcritical.sim.automation.twin.Scenario.Timed;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioAction.Inject;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioAction.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Каталог сценариев тренажёра (11 §11.3.4) и испытаний двойника (03d §3d.9.1 FAT). Уроки — инженерные, без названий
 * объектов и стран (риск K12); каждый связан с механикой мода. Из каталога 11 §11.3.4 здесь те, чьи эталонные
 * установки есть в двойнике: {@code tank_overfill}, {@code forced_bit}, {@code out_of_phase_sync}; плюс вводный урок
 * котла I0 {@code boiler_low_water} (упуск воды, легкоплавкая пробка 07 §7.9.4). Времена и уровни [BALANCE]. FAT
 * компрессорной станции и станции смешения — сценарии задач наладчика {@link ChallengeCatalog}.
 */
public final class ScenarioCatalog {
    /** Испытания FAT по шаблону установки: программа игрока должна их пройти, прежде чем её загрузят в мир. */
    public static final Map<String, Scenario> FAT = new LinkedHashMap<>();
    /** Уроки тренажёра в порядке выбора. */
    public static final Map<String, Scenario> LESSONS = new LinkedHashMap<>();

    /** Программа ПАЗ резервуара: сигнализатор верхнего уровня снимает разрешение с приёмной задвижки. */
    public static final String TANK_INTERLOCK = """
            (* ПАЗ резервуара: LSH снимает разрешение на приём *)
            LDN %IX0.0
            ST %QX0.0
            """;

    static {
        String t = TankTwinPlant.ID, b = BoilerTwinPlant.ID, g = SyncTwinPlant.ID;
        lesson(new Scenario("tank_overfill", t, "half", 900, false, null, null,
                List.of(new Timed(20, new Inject(new SensorFault(TankTwinPlant.LEVEL, SensorMode.FROZEN, 0))),
                        new Timed(20, new Inject(new SensorFault(TankTwinPlant.LSH, SensorMode.FROZEN, 0))),
                        new Timed(40, new Set(TankTwinPlant.INLET, 1.0))),
                conds("spilled_m3 <= 0", "net_m3h <= 0"), List.of(), new Margin(TankTwinPlant.LEVEL, TankTwinPlant.HEIGHT_M), null,
                0.3, 0.7));
        lesson(new Scenario("forced_bit", t, "high", 600, false, TANK_INTERLOCK,
                new IoMap(List.of(new Di(0, TankTwinPlant.LSH, Cmp.GT, 0.5)), List.of(),
                        List.of(new Do(0, TankTwinPlant.INLET, DoMode.PERMISSIVE, 1, 0)), List.of()),
                List.of(new Timed(0, new Inject(new ForcedInput(0, false, 0))),
                        new Timed(30, new Set(TankTwinPlant.PUMP, 0))),
                conds("spilled_m3 <= 0", "net_m3h <= 0"), List.of(), new Margin(TankTwinPlant.LEVEL, TankTwinPlant.HEIGHT_M), null,
                0.3, 0.7));
        lesson(new Scenario("out_of_phase_sync", g, "slip", 300, false, null, null, List.of(),
                conds("breaker_closed >= 1"), List.of(), null, null, 1, 0));
        lesson(new Scenario("boiler_low_water", b, "low", 1500, false, null, null,
                List.of(new Timed(30, new Inject(StuckActuator.trip(BoilerTwinPlant.FEED_PUMP)))),
                conds("level_above_crown_m > 0"), List.of(), new Margin(BoilerTwinPlant.LEVEL_ABOVE_CROWN, 0), null, 0.3, 0.7));

        fat(new Scenario("fat_tank_farm", t, "half", 900, true, null, null,
                List.of(new Timed(300, new Inject(StuckActuator.trip(TankTwinPlant.PUMP))),
                        new Timed(600, new Inject(new SensorFault(TankTwinPlant.LSH, SensorMode.FROZEN, 0)))),
                conds("level_m > 3", "level_m < 7", "spilled_m3 <= 0"), conds("level_m < 1"),
                new Margin(TankTwinPlant.LEVEL, TankTwinPlant.HEIGHT_M), new Loop(TankTwinPlant.LEVEL, 5, TankTwinPlant.INLET), 0.5, 0.5));
        fat(new Scenario("fat_boiler_i0", b, "low", 1500, true, null, null,
                List.of(new Timed(30, new Inject(StuckActuator.trip(BoilerTwinPlant.FEED_PUMP)))),
                conds("level_above_crown_m > 0"), List.of(), new Margin(BoilerTwinPlant.LEVEL_ABOVE_CROWN, 0),
                new Loop(BoilerTwinPlant.PRESSURE, 10, BoilerTwinPlant.FIRING), 0.3, 0.7));
        fat(new Scenario("fat_generator_sync", g, "slip", 300, true, null, null, List.of(),
                conds("breaker_closed >= 1"), List.of(), null, null, 1, 0));
        // компрессор и смешение: испытание FAT — то же, что задача наладчика 03d §3d.11.6
        fat(ChallengeCatalog.get("compressor_startup_no_surge").scenario());
        fat(ChallengeCatalog.get("gasoline_blending_lp").scenario());
    }

    private ScenarioCatalog() {
    }

    private static List<Condition> conds(String... s) {
        return java.util.Arrays.stream(s).map(Condition::parse).toList();
    }

    private static void lesson(Scenario s) {
        LESSONS.put(s.id(), s);
    }

    private static void fat(Scenario s) {
        FAT.put(s.plant(), s);
    }

    /** Урок или испытание по id; {@code null} — нет. */
    public static Scenario get(String id) {
        Scenario s = LESSONS.get(id);
        if (s != null) return s;
        for (Scenario f : FAT.values()) if (f.id().equals(id)) return f;
        return null;
    }
}
