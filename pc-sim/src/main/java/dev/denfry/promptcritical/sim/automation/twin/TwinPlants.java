package dev.denfry.promptcritical.sim.automation.twin;

import dev.denfry.promptcritical.sim.automation.twin.IoMap.Ai;
import dev.denfry.promptcritical.sim.automation.twin.IoMap.Ao;
import dev.denfry.promptcritical.sim.automation.twin.IoMap.Cmp;
import dev.denfry.promptcritical.sim.automation.twin.IoMap.Di;
import dev.denfry.promptcritical.sim.automation.twin.IoMap.Do;
import dev.denfry.promptcritical.sim.automation.twin.IoMap.DoMode;
import java.util.List;

/**
 * Эталонные установки двойника и тренажёра (11 §11.3.2) и их привязка ввода-вывода ПЛК по умолчанию — то, к чему
 * игрок подключает свою программу на испытаниях FAT. Установка — сериализуемая копия домена, а не структура
 * Minecraft; здесь — построенная кодом с начальным состоянием {@code preset}.
 */
public final class TwinPlants {
    /** Порядок выбора на станции. */
    public static final List<String> IDS = List.of(TankTwinPlant.ID, BoilerTwinPlant.ID, SyncTwinPlant.ID, CompressorTwinPlant.ID,
            BlendTwinPlant.ID);

    private TwinPlants() {
    }

    /** Сигналы шаблона без постройки установки (подписи пульта на клиенте). */
    public static List<String> signals(String template) {
        return switch (template) {
            case TankTwinPlant.ID -> TankTwinPlant.SIGNALS;
            case BoilerTwinPlant.ID -> BoilerTwinPlant.SIGNALS;
            case SyncTwinPlant.ID -> SyncTwinPlant.SIGNALS;
            case CompressorTwinPlant.ID -> CompressorTwinPlant.SIGNALS;
            case BlendTwinPlant.ID -> BlendTwinPlant.SIGNALS;
            default -> List.of();
        };
    }

    /** Механизмы шаблона без постройки установки. */
    public static List<String> actuators(String template) {
        return switch (template) {
            case TankTwinPlant.ID -> TankTwinPlant.ACTUATORS;
            case BoilerTwinPlant.ID -> BoilerTwinPlant.ACTUATORS;
            case SyncTwinPlant.ID -> SyncTwinPlant.ACTUATORS;
            case CompressorTwinPlant.ID -> CompressorTwinPlant.ACTUATORS;
            case BlendTwinPlant.ID -> BlendTwinPlant.ACTUATORS;
            default -> List.of();
        };
    }

    /**
     * Новая установка. Пресеты: резервуар — {@code half} (5 м), {@code high} (6 м); котёл — {@code hot} (10 бар) и {@code low} (уровень
     * 0,09 м над жаровой трубой);
     * генератор — {@code slip} (скольжение 0,25 Гц); компрессор — стоит с открытым рециклом; смешение — пустой
     * резервуар.
     */
    public static TwinPlant create(String template, String preset, long seed) {
        return switch (template) {
            case TankTwinPlant.ID -> new TankTwinPlant("high".equals(preset) ? 6.0 : 5.0, 0.55, true);
            case BoilerTwinPlant.ID -> new BoilerTwinPlant(10, "low".equals(preset) ? 0.09 : Double.NaN, seed);
            case SyncTwinPlant.ID -> new SyncTwinPlant(0.25);
            case CompressorTwinPlant.ID -> new CompressorTwinPlant();
            case BlendTwinPlant.ID -> new BlendTwinPlant();
            default -> throw new IllegalArgumentException("no twin plant " + template);
        };
    }

    /** Привязка по умолчанию: что видит и чем управляет ПЛК игрока на стенде. */
    public static IoMap defaultIo(String template) {
        return switch (template) {
            case TankTwinPlant.ID -> new IoMap(
                    List.of(new Di(0, TankTwinPlant.LSH, Cmp.GT, 0.5), new Di(1, TankTwinPlant.OUTFLOW, Cmp.GT, 1)),
                    List.of(new Ai(0, TankTwinPlant.LEVEL, 0, TankTwinPlant.HEIGHT_M),
                            new Ai(1, TankTwinPlant.INFLOW, 0, TankTwinPlant.INFLOW_MAX_M3H)),
                    List.of(new Do(0, TankTwinPlant.PUMP, DoMode.COMMAND, 1, 0)),
                    List.of(new Ao(0, TankTwinPlant.INLET, 0, 1)));
            case BoilerTwinPlant.ID -> new IoMap(
                    List.of(new Di(0, BoilerTwinPlant.LEVEL_ABOVE_CROWN, Cmp.LT, 0.07), new Di(1, BoilerTwinPlant.LIT, Cmp.GT, 0.5)),
                    List.of(new Ai(0, BoilerTwinPlant.PRESSURE, 0, 16), new Ai(1, BoilerTwinPlant.LEVEL_ABOVE_CROWN, 0, 1),
                            new Ai(2, BoilerTwinPlant.SPEED, 0, 1.5)),
                    List.of(new Do(0, BoilerTwinPlant.FEED_PUMP, DoMode.COMMAND, 1, 0),
                            new Do(1, BoilerTwinPlant.STOP_VALVE, DoMode.PERMISSIVE, 1, 0)),
                    List.of(new Ao(0, BoilerTwinPlant.FIRING, 0, 1)));
            case SyncTwinPlant.ID -> new IoMap(
                    List.of(new Di(0, SyncTwinPlant.BREAKER_CLOSED, Cmp.GT, 0.5)),
                    List.of(new Ai(0, SyncTwinPlant.SLIP, -1, 1), new Ai(1, SyncTwinPlant.ANGLE, -180, 180)),
                    List.of(new Do(0, SyncTwinPlant.BREAKER, DoMode.COMMAND, 1, 0),
                            new Do(1, SyncTwinPlant.SPEEDER, DoMode.COMMAND, 1, 0),
                            new Do(2, SyncTwinPlant.SPEEDER, DoMode.COMMAND, -1, 0)),
                    List.of());
            // антипомпаж: перепад, давления всаса и нагнетания в одной шкале 0…50 (кПа и бар) — в вольтах q²_r и R_c те же,
            // что в паспорте; пуск привода — %QX0.0, рецикл — %QW0
            case CompressorTwinPlant.ID -> new IoMap(
                    List.of(new Di(0, CompressorTwinPlant.SPEED, Cmp.GT, 0.95)),
                    List.of(new Ai(0, CompressorTwinPlant.DP, 0, 50), new Ai(1, CompressorTwinPlant.PS, 0, 50),
                            new Ai(2, CompressorTwinPlant.PD, 0, 50), new Ai(3, CompressorTwinPlant.SPEED, 0, 1)),
                    List.of(new Do(0, CompressorTwinPlant.DRIVE, DoMode.COMMAND, 1, 0)),
                    List.of(new Ao(0, CompressorTwinPlant.RECYCLE, 0, 1)));
            // смешение: анализатор качества и объём резервуара на входах, расходы компонентов 0…A_i на %QW0…%QW4
            case BlendTwinPlant.ID -> {
                List<Ao> ao = new java.util.ArrayList<>();
                for (int i = 0; i < BlendTwinPlant.ACTUATORS.size(); i++)
                    ao.add(new Ao(i, BlendTwinPlant.ACTUATORS.get(i), 0, BlendTwinPlant.INPUT.components().get(i).availableM3h()));
                yield new IoMap(List.of(),
                        List.of(new Ai(0, BlendTwinPlant.AN_RON, 80, 100), new Ai(1, BlendTwinPlant.AN_MON, 70, 90),
                                new Ai(2, BlendTwinPlant.AN_RVP, 0, 100), new Ai(3, BlendTwinPlant.VOLUME, 0, 5)),
                        List.of(), ao);
            }
            default -> throw new IllegalArgumentException("no twin plant " + template);
        };
    }
}
