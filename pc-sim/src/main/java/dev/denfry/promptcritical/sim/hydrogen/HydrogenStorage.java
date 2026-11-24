package dev.denfry.promptcritical.sim.hydrogen;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Сосуд с водородом (17 §17.3.8): баллонная рампа 20 МПа или газгольдер низкого давления. Давление — по реальному
 * газу {@link HydrogenGas}. Температура газа — из баланса внутренней энергии: закачка вносит энтальпию c_p·T_вх и греет
 * газ (быстрая заправка баллона нагревает его на десятки градусов), отбор уносит c_p·T и охлаждает; стенки
 * выравнивают газ к наружной температуре за {@link Spec#wallTauS}.
 *
 * <p>Защита: предохранительный клапан (если есть) сбрасывает выше уставки и садится на 95 % её; разрывная мембрана
 * открывается выше своего давления и остаётся открытой, пока её не заменят. Сброс идёт в свечу — его учитывает
 * {@link #ventedMol()} для расчёта облака. Ручной клапан сброса — тоже в свечу.
 */
public final class HydrogenStorage {
    /**
     * @param volumeM3 геометрический объём
     * @param reliefPa уставка предохранительного клапана, Па абс. ({@code NaN} — клапана нет)
     * @param burstPa  давление разрыва мембраны, Па абс. ({@code NaN} — мембраны нет)
     * @param maxPa    рабочее давление (100 % шкалы), Па абс.
     * @param ventM2   проходное сечение клапана и свечи, м²
     */
    public record Spec(String name, double volumeM3, double maxPa, double reliefPa, double burstPa, double ventM2,
            double wallTauS) {
    }

    /** Рампа: девять бесшовных баллонов по 1,3 м³ (6,5 м × Ø 0,56 м), 20 МПа, мембрана на 24 МПа. */
    public static final Spec TUBE_BANK = new Spec("tube_bank", 11.7, 20e6, Double.NaN, 24e6, 2e-4, 3600);
    /** Газгольдер-ресивер 3 × 10 м перед компрессором: 70 м³, до 1,5 бар абс., клапан на 1,5 бар, мембрана на 1,8. */
    public static final Spec GASHOLDER = new Spec("gasholder", 70, 1.5e5, 1.5e5, 1.8e5, 8e-3, 7200);
    public static final double ATM_PA = 101_325;

    private final Spec spec;
    private double mol, tempK = 293.15;
    private boolean reliefOpen, burst, ventOpen;
    private double ventedMol, ventMolS;

    public HydrogenStorage(Spec spec) {
        this.spec = spec;
        mol = HydrogenGas.molarDensity(ATM_PA, tempK) * spec.volumeM3(); // заполнен водородом при атмосферном
    }

    public Spec spec() {
        return spec;
    }

    public double pressurePa() {
        return HydrogenGas.pressure(mol / spec.volumeM3(), tempK);
    }

    public double massKg() {
        return mol * HydrogenGas.M;
    }

    public double mol() {
        return mol;
    }

    public double tempK() {
        return tempK;
    }

    /** Запас в нормальных кубометрах. */
    public double nm3() {
        return HydrogenGas.nm3(mol);
    }

    /** Заполнение по давлению, 0…1 от рабочего. */
    public double fill() {
        return Math.max(0, (pressurePa() - ATM_PA) / (spec.maxPa() - ATM_PA));
    }

    /** Закачать {@code dMol} газа с температурой {@code inK}. */
    public void add(double dMol, double inK) {
        if (dMol <= 0) return;
        double u = mol * HydrogenGas.CV * tempK + dMol * HydrogenGas.CP * inK;
        mol += dMol;
        tempK = u / (mol * HydrogenGas.CV);
    }

    /**
     * Отобрать до {@code dMol}, не опуская давление ниже {@code minPa}.
     *
     * @return сколько отобрано, моль
     */
    public double take(double dMol, double minPa) {
        if (dMol <= 0) return 0;
        double floor = HydrogenGas.molarDensity(Math.max(minPa, ATM_PA), tempK) * spec.volumeM3();
        double got = Math.max(0, Math.min(dMol, mol - floor));
        if (got <= 0) return 0;
        double u = mol * HydrogenGas.CV * tempK - got * HydrogenGas.CP * tempK;
        mol -= got;
        tempK = Math.max(150, u / (mol * HydrogenGas.CV));
        return got;
    }

    public void setVentOpen(boolean open) {
        ventOpen = open;
    }

    public boolean ventOpen() {
        return ventOpen;
    }

    public boolean reliefOpen() {
        return reliefOpen;
    }

    public boolean burst() {
        return burst;
    }

    /** Поставить новую мембрану. */
    public void replaceDisc() {
        burst = false;
    }

    /** Сброс в свечу на последнем шаге, моль/с. */
    public double ventMolS() {
        return ventMolS;
    }

    /** Всего сброшено в свечу, моль; {@link #clearVented()} обнуляет счёт после учёта облака. */
    public double ventedMol() {
        return ventedMol;
    }

    public double clearVented() {
        double v = ventedMol;
        ventedMol = 0;
        return v;
    }

    /** Шаг: клапаны, мембрана, теплообмен со стенкой. */
    public void step(double dtS, double ambientK) {
        double p = pressurePa();
        if (!Double.isNaN(spec.burstPa()) && p >= spec.burstPa()) burst = true;
        if (!Double.isNaN(spec.reliefPa())) {
            if (p >= spec.reliefPa()) reliefOpen = true;
            else if (p < 0.95 * spec.reliefPa()) reliefOpen = false;
        }
        double out = 0;
        if (reliefOpen || ventOpen) out += HydrogenGas.orificeMolS(spec.ventM2(), p, ATM_PA, tempK);
        if (burst) out += HydrogenGas.orificeMolS(spec.ventM2() * 4, p, ATM_PA, tempK);
        double got = take(out * dtS, ATM_PA);
        ventMolS = dtS > 0 ? got / dtS : 0;
        ventedMol += got;
        tempK += (ambientK - tempK) * Math.min(1, dtS / spec.wallTauS());
    }

    public void save(StateWriter out) {
        out.putDouble("mol", mol);
        out.putDouble("t", tempK);
        out.putBoolean("burst", burst);
        out.putBoolean("vent", ventOpen);
        out.putDouble("vented", ventedMol);
    }

    public void load(StateReader in) {
        mol = in.optDouble("mol", mol);
        tempK = in.optDouble("t", 293.15);
        burst = in.optInt("burst", 0) != 0;
        ventOpen = in.optInt("vent", 0) != 0;
        ventedMol = in.optDouble("vented", 0);
    }
}
