package dev.denfry.promptcritical.sim.automation.twin;

import dev.denfry.promptcritical.sim.automation.twin.Challenge.Metric;
import dev.denfry.promptcritical.sim.automation.twin.Challenge.Source;
import dev.denfry.promptcritical.sim.automation.twin.Scenario.Timed;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioAction.Set;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Каталог задач наладчика (03d §3d.11.6). Здесь — те, чьи установки есть в двойнике:
 *
 * <ul>
 *   <li>№ 2 {@code compressor_startup_no_surge} (09, A3 {@code PC_ANTISURGE}): пуск центробежного компрессора, выход
 *       на газопровод и провал отбора до 0,2 хода линейного клапана без единого цикла помпажа. Метрики — время пуска
 *       (первые 90 % расчётного расхода в газопровод) и газ, ушедший на рецикл.</li>
 *   <li>№ 3 {@code generator_synchronization} (07, A1 или A3): включить генератор в сеть синхронно. Метрики — толчок
 *       момента при включении и время до включения.</li>
 *   <li>№ 15 {@code gasoline_blending_lp} (09, A5): партия АИ-92 не меньше {@value #BLEND_BATCH_M3} м³ за 15 минут по
 *       рецепту ЛП с коррекцией по анализатору. Метрика — превышение себестоимости над оптимумом ЛП, %; критерий
 *       достижения {@code refinery_lp} (11) — не больше 5 %.</li>
 * </ul>
 *
 * Эталоны {@code par} — метрики решений из справочника ({@link #REFERENCE_SOLUTIONS}) со стандартными настройками
 * блоков; их проверяет {@code ChallengeCatalogTest}. Времена и возмущения [BALANCE].
 */
public final class ChallengeCatalog {
    public static final double BLEND_BATCH_M3 = 2.2;
    public static final Map<String, Challenge> ALL = new LinkedHashMap<>();
    /** Решения из справочника: задача → программа ПЛК. */
    public static final Map<String, String> REFERENCE_SOLUTIONS = new LinkedHashMap<>();

    /** Пуск и антипомпаж стандартным блоком: привод в ход, наклон линии помпажа из паспорта, настройки по умолчанию. */
    public static final String COMPRESSOR_REFERENCE = """
            PROGRAM Startup
            VAR As : PC_ANTISURGE; END_VAR
            %QX0.0 := TRUE;
            As(DPO := %IW0, PS := %IW1, PD := %IW2, KS := 2.489);
            %QW0 := As.Y;
            """;

    /**
     * Рецепт ЛП с запасом 1,5 пункта по ОЧМ на неточность смешения: LSR 0,0701; риформат 2,3579; FCC 5,0; бутан 0,3171;
     * алкилат 1,2548 м³/ч — в вольтах 0…10 = 0…A_i.
     */
    public static final String BLEND_REFERENCE = """
            PROGRAM Blend
            %QW0 := 0.1753;
            %QW1 := 3.9299;
            %QW2 := 10.0;
            %QW3 := 3.1710;
            %QW4 := 6.2742;
            """;

    /** Синхронизация: скольжение к +0,05 Гц задатчиком, включение у угла −3° (опережение на время включения). */
    public static final String SYNC_REFERENCE = """
            PROGRAM Sync
            %QX0.1 := %IW0 < 5.2;
            %QX0.2 := %IW0 > 5.3;
            IF %IW0 > 5.15 AND %IW0 < 5.35 AND %IW1 > 4.86 AND %IW1 < 4.97 THEN
                %QX0.0 := TRUE;
            END_IF;
            """;

    static {
        add(new Challenge("compressor_startup_no_surge", "09", "A3",
                new Scenario("compressor_startup_no_surge", CompressorTwinPlant.ID, "stopped", 300, true, null, null,
                        List.of(new Timed(40, new Set(CompressorTwinPlant.LINE, 0.85)),
                                new Timed(150, new Set(CompressorTwinPlant.LINE, 0.2)),
                                new Timed(250, new Set(CompressorTwinPlant.LINE, 0.85))),
                        conds("line_flow_m3s > 0.9", "recycle_flow_m3s < 0.1"), conds("surge_cycles >= 1"), null, null, 1, 0),
                List.of(new Metric("startup_s", Source.SIGNAL, CompressorTwinPlant.ON_STREAM_S, 93.45),
                        new Metric("recycled_m3", Source.SIGNAL, CompressorTwinPlant.RECYCLED, 91.87)),
                Double.NaN), COMPRESSOR_REFERENCE);
        add(new Challenge("generator_synchronization", "07", "A1",
                new Scenario("generator_synchronization", SyncTwinPlant.ID, "slip", 300, true, null, null, List.of(),
                        conds("breaker_closed >= 1"), conds("shaft_damage > 0"), null, null, 1, 0),
                List.of(new Metric("torque_pu", Source.SIGNAL, SyncTwinPlant.TORQUE, 0.404),
                        new Metric("time_s", Source.SETTLED_S, null, 15.6)),
                Double.NaN), SYNC_REFERENCE);
        add(new Challenge("gasoline_blending_lp", "09", "A5",
                new Scenario("gasoline_blending_lp", BlendTwinPlant.ID, "empty", 900, true, null, null, List.of(),
                        conds("volume_m3 >= " + BLEND_BATCH_M3, "on_spec >= 1"), List.of(), null, null, 1, 0),
                List.of(new Metric("cost_premium_pct", Source.SIGNAL, BlendTwinPlant.COST_PREMIUM, 1.96)),
                5.0), BLEND_REFERENCE);
    }

    private ChallengeCatalog() {
    }

    private static void add(Challenge c, String reference) {
        ALL.put(c.id(), c);
        REFERENCE_SOLUTIONS.put(c.id(), reference);
    }

    private static List<Condition> conds(String... s) {
        return Arrays.stream(s).map(Condition::parse).toList();
    }

    public static Challenge get(String id) {
        return ALL.get(id);
    }

    /** Задача на шаблоне установки (у каждой установки двойника — не больше одной). */
    public static Challenge forPlant(String template) {
        for (Challenge c : ALL.values()) if (c.scenario().plant().equals(template)) return c;
        return null;
    }
}
