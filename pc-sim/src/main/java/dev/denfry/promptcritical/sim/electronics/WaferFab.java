package dev.denfry.promptcritical.sim.electronics;

/**
 * Прогон пластины через планарный процесс (11 §11.5.3): подзатворное окисление, литография, загонка фосфора в
 * сток-исток, алюминиевая разводка. Окисел и переход проверяются окном изделия ({@link ChipDesign#window}), годные
 * кристаллы — пуассоновским выходом от чистоты комнаты и размера элемента.
 *
 * @param design       изделие
 * @param litho        способ литографии
 * @param gapUm        зазор маска — пластина при бесконтактной печати, мкм
 * @param ambient      сухое или влажное окисление
 * @param oxTempC      температура окисления, °C
 * @param oxHours      время окисления, ч
 * @param diffTempC    температура загонки фосфора, °C
 * @param diffHours    время загонки, ч
 * @param substrateCm3 легирование подложки (бор), см⁻³
 */
public record WaferFab(ChipDesign design, Litho litho, double gapUm, PlanarProcess.Ambient ambient, double oxTempC, double oxHours,
        double diffTempC, double diffHours, double substrateCm3) {

    public enum Litho { CONTACT, PROXIMITY, G_LINE }

    /** Итог прогона. */
    public record Result(double cdUm, double oxideNm, double junctionUm, double dieMm2, int dies, double d0, double yield, int good,
            double clockMhz, ChipDesign.Reject reject) {
        public boolean ok() {
            return reject == null;
        }
    }

    /** Размер элемента выбранной литографии, мкм. */
    public double cdUm() {
        return switch (litho) {
            case CONTACT -> PlanarProcess.proximityCdUm(0);
            case PROXIMITY -> PlanarProcess.proximityCdUm(gapUm);
            case G_LINE -> PlanarProcess.gLineCdUm();
        };
    }

    /** Время процесса на пластину (окисление и загонка; литография и металлизация — меньше часа), ч. */
    public double processHours() {
        return oxHours + diffHours + 1;
    }

    /**
     * Прогон пластины в комнате с концентрацией {@code perM3At05} частиц ≥ 0,5 мкм.
     *
     * @param waferMm диаметр пластины
     */
    public Result run(double perM3At05, double waferMm) {
        double cd = cdUm();
        double ox = PlanarProcess.oxideUm(ambient, oxTempC, oxHours, true) * 1000;
        double xj = PlanarProcess.junctionDepthUm(PlanarProcess.Dopant.PHOSPHORUS, diffTempC, diffHours, substrateCm3);
        double area = design.dieAreaMm2(cd);
        int dies = Cleanroom.diesPerWafer(waferMm, area);
        double d0 = Cleanroom.defectDensity(perM3At05, cd, litho == Litho.CONTACT);
        double y = Cleanroom.poissonYield(area / 100, d0);
        ChipDesign.Reject reject = cd > design.maxCdUm ? ChipDesign.Reject.TOO_LARGE : ChipDesign.window(cd, ox, xj);
        int good = reject == null ? (int) Math.floor(dies * y) : 0;
        return new Result(cd, ox, xj, area, dies, d0, reject == null ? y : 0, good, ChipDesign.clockMhz(cd), reject);
    }
}
