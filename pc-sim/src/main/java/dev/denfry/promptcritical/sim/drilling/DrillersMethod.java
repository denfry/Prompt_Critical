package dev.denfry.promptcritical.sim.drilling;

/**
 * Глушение методом бурильщика в два цикла циркуляции (IADC WellSharp; API RP 59, §6.4; Bourgoyne et al., §4.11):
 *
 * <ol>
 * <li>После закрытия и стабилизации давлений записываются SIDPP и SICP и составляется {@link KillSheet}.</li>
 * <li>Первый цикл: насос выводят на скорость глушения, дросселем держат давление в трубах равным ICP, пока приток не
 * выйдет через штуцер и сепаратор (не меньше объёма колонны и затрубья). Забойное давление при этом остаётся не
 * ниже пластового.</li>
 * <li>Насос останавливают, скважина закрыта; раствор в ёмкостях утяжеляют баритом до плотности глушения.</li>
 * <li>Второй цикл: давление в трубах ведут по графику от ICP до FCP, пока раствор глушения идёт к долоту, затем держат
 * FCP, пока он не выйдет на устье; вытесняемый старый раствор уходит в запасную ёмкость.</li>
 * <li>Насос останавливают, захваченное давление стравливают через штуцер: при SIDPP = SICP = 0 скважина заглушена,
 * превентор можно открыть и проверить перелив.</li>
 * </ol>
 *
 * Скорость глушения — один насос на 30 ходах в минуту: обычно треть–половина рабочей подачи (IADC).
 */
public final class DrillersMethod {
    public enum Stage { IDLE, FIRST_CIRCULATION, WEIGHT_UP, SECOND_CIRCULATION, DONE }

    public static final double KILL_SPM = 30;
    /** Стравливание захваченного давления после второго цикла: расход через штуцер, м³/с, и порог, Па. */
    public static final double BLEED_M3S = 1e-3, TRAPPED_PA = 20e3;
    /** Запас по плотности утяжелённого раствора, кг/м³: в ёмкостях не меньше ρ_k минус это. */
    public static final double DENSITY_TOLERANCE_KGM3 = 2;

    private final double m3PerStroke;
    private Stage stage = Stage.IDLE;
    private KillSheet sheet;
    private double strokes;

    /** {@code m3PerStroke} — подача насоса за ход. */
    public DrillersMethod(double m3PerStroke) {
        if (!(m3PerStroke > 0)) throw new IllegalArgumentException("pump displacement must be positive");
        this.m3PerStroke = m3PerStroke;
    }

    public double killRateM3s() {
        return KILL_SPM / 60 * m3PerStroke;
    }

    public Stage stage() {
        return stage;
    }

    public KillSheet sheet() {
        return sheet;
    }

    public double strokes() {
        return strokes;
    }

    /** Подача, которую насос должен давать на этой стадии, м³/с; NaN — насос в руках бурильщика. */
    public double requiredPumpM3s() {
        return switch (stage) {
            case FIRST_CIRCULATION, SECOND_CIRCULATION -> killRateM3s();
            case WEIGHT_UP, DONE -> 0;
            case IDLE -> Double.NaN;
        };
    }

    /**
     * Следующий шаг бурильщика: из закрытой скважины — замеры и первый цикл; после утяжеления — второй цикл.
     *
     * @return {@code false} — шаг сейчас невозможен (скважина не закрыта, насос работает, раствор не утяжелён)
     */
    public boolean advance(Well w) {
        switch (stage) {
            case IDLE, DONE -> {
                if (!w.bopClosed() || w.pumpRateM3s() > 0 || w.blowout()) return false;
                double tvd = w.holeDepthM();
                sheet = KillSheet.of(tvd, w.bitMudDensityKgM3(), w.drillPipePressurePa(), w.casingPressurePa(),
                        w.circulatingLossPa(killRateM3s()), w.stringCapacityM3(), w.annulusCapacityM3(), m3PerStroke,
                        w.geometry().shoeDepthM(), w.geometry().shoeFractureKgM3());
                strokes = 0;
                stage = Stage.FIRST_CIRCULATION;
                w.setDrillPipeTargetPa(sheet.icpPa());
                return true;
            }
            case WEIGHT_UP -> {
                if (w.pitDensityKgM3() < sheet.killMudKgM3() - DENSITY_TOLERANCE_KGM3) return false;
                strokes = 0;
                stage = Stage.SECOND_CIRCULATION;
                w.setDrillPipeTargetPa(sheet.icpPa());
                w.setDivertReturns(true);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Прервать глушение (бурильщик взял управление). */
    public void abort(Well w) {
        stage = Stage.IDLE;
        w.setDrillPipeTargetPa(Double.NaN);
        w.setDivertReturns(false);
    }

    /** После шага скважины: счёт ходов, уставка дросселя, переход стадий. */
    public void track(Well w, double dt) {
        if (w.blowout()) {
            if (stage != Stage.IDLE) abort(w);
            return;
        }
        strokes += w.pumpRateM3s() * dt / m3PerStroke;
        switch (stage) {
            case FIRST_CIRCULATION -> {
                w.setDrillPipeTargetPa(sheet.icpPa());
                if (strokes >= sheet.totalStrokes() && !w.hasInflux()) {
                    stage = Stage.WEIGHT_UP;
                    w.setDrillPipeTargetPa(Double.NaN);
                }
            }
            case SECOND_CIRCULATION -> {
                w.setDrillPipeTargetPa(sheet.drillPipePressurePa(strokes));
                double ret = w.returnsDensityKgM3();
                if (strokes >= sheet.totalStrokes() && ret >= sheet.killMudKgM3() - DENSITY_TOLERANCE_KGM3) {
                    stage = Stage.DONE;
                    w.setDrillPipeTargetPa(Double.NaN);
                    w.setDivertReturns(false);
                }
            }
            case DONE -> {
                // насос встал, скважина закрыта: давление, оставшееся на устье, стравливают малыми порциями
                if (w.bopClosed() && w.pumpRateM3s() <= 0 && w.casingPressurePa() > TRAPPED_PA) w.bleed(BLEED_M3S * dt);
            }
            default -> {
            }
        }
    }
}
