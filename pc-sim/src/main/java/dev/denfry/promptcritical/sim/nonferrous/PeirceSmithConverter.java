package dev.denfry.promptcritical.sim.nonferrous;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.nonferrous.SulfideSmelting.Metal;

/**
 * Горизонтальный конвертер Пирса–Смита {@code peirce_smith_converter} (08 §8.8.3) — тот же блок ведёт и огневое
 * рафинирование. Плавка идёт по фазам: шлаковый период (2FeS + 3O₂ → 2FeO + 2SO₂, FeO ошлаковывается кварцем),
 * медный период (Cu₂S + O₂ → 2Cu + SO₂) — черновая медь {@value #BLISTER_CU} (§8.8.3: 98,5–99,5 %); затем окисление
 * примесей и дразнение восстановителем — анодная медь {@value #ANODE_CU} (§8.8.3: 99,5 %). Восстановитель — природный
 * газ, {@value #POLING_NM3_PER_KG} нм³/кг Cu [BALANCE: 5–15 м³/т].
 *
 * <p>Дутьё — воздух от воздуходувки цеха, {@value #BLOW_O2_KG_S} кг O₂/с физ. [BALANCE]; процесс автогенный, топливо
 * не нужно. Термохимия — SLOW.
 */
public final class PeirceSmithConverter {
    public enum Phase { EMPTY, SLAG_BLOW, COPPER_BLOW, BLISTER, REFINING, ANODE }

    public static final double BLISTER_CU = 0.99, ANODE_CU = 0.995;
    public static final double BLOW_O2_KG_S = 1.0, POLING_NM3_PER_KG = 0.01, REFINE_KG_S = 2.0;
    /** Загрузка штейна, кг Cu на плавку [BALANCE]. */
    public static final double CHARGE_CU_KG = 1100;
    /** На кг Fe штейна в шлаковый период: кислород, сера, кварц. */
    public static final double O2_PER_FE = 1.5 * SulfideSmelting.M_O2 / SulfideSmelting.M_FE, S_PER_FE = SulfideSmelting.M_S / SulfideSmelting.M_FE;

    private Phase phase = Phase.EMPTY;
    private double cuKg, feKg, sKg;
    private boolean refine = true;
    private double lastSo2KgS, lastO2KgS;
    private double totalSo2Kg;

    public Phase phase() {
        return phase;
    }

    public double cuKg() {
        return cuKg;
    }

    public double feKg() {
        return feKg;
    }

    public double sKg() {
        return sKg;
    }

    public boolean refine() {
        return refine;
    }

    public void setRefine(boolean refine) {
        this.refine = refine;
    }

    /** Место под медь штейна в нынешней плавке, кг Cu; загружать можно до начала медного периода. */
    public double roomCuKg() {
        return phase == Phase.EMPTY || phase == Phase.SLAG_BLOW ? Math.max(0, CHARGE_CU_KG - cuKg) : 0;
    }

    /** Залить штейн: {@code cuKg} меди в штейне линии {@code m}. */
    public void chargeMatte(Metal m, double cu) {
        if (cu <= 0 || roomCuKg() <= 0) return;
        cuKg += cu;
        feKg += cu * m.feInMatte();
        sKg += cu * m.sInMatte();
        phase = Phase.SLAG_BLOW;
    }

    /** Залить черновую медь на огневое рафинирование. */
    public boolean chargeBlister(double cu) {
        if (phase != Phase.EMPTY || cu <= 0) return false;
        cuKg = cu;
        phase = Phase.BLISTER;
        return true;
    }

    /** Шаг на {@code dtS} физ. секунд. Возвращает расход флюса, кг; газа — {@link #gasUse}. */
    public double step(double dtS, double fluxAvailKg, double gasAvailNm3) {
        double o2 = BLOW_O2_KG_S * dtS, flux = 0, so2 = 0;
        gasUse = 0;
        switch (phase) {
            case SLAG_BLOW -> {
                double fe = Math.min(feKg, o2 / O2_PER_FE);
                fe = Math.min(fe, Math.max(0, fluxAvailKg) / SulfideSmelting.SIO2_PER_FE);
                feKg -= fe;
                double s = Math.min(sKg, fe * S_PER_FE);
                sKg -= s;
                so2 = s * SulfideSmelting.M_SO2 / SulfideSmelting.M_S;
                flux = fe * SulfideSmelting.SIO2_PER_FE;
                o2 = fe * O2_PER_FE;
                if (feKg <= 1e-9) {
                    feKg = 0;
                    phase = Phase.COPPER_BLOW;
                }
            }
            case COPPER_BLOW -> {
                double s = Math.min(sKg, o2 * SulfideSmelting.M_S / SulfideSmelting.M_O2);
                sKg -= s;
                so2 = s * SulfideSmelting.M_SO2 / SulfideSmelting.M_S;
                o2 = s * SulfideSmelting.M_O2 / SulfideSmelting.M_S;
                if (sKg <= 1e-9) {
                    sKg = 0;
                    phase = Phase.BLISTER;
                }
            }
            case BLISTER -> {
                o2 = 0;
                if (refine && gasAvailNm3 >= cuKg * POLING_NM3_PER_KG) {
                    gasUse = cuKg * POLING_NM3_PER_KG;
                    refineLeftKg = cuKg;
                    phase = Phase.REFINING;
                }
            }
            case REFINING -> {
                o2 = 0;
                refineLeftKg -= REFINE_KG_S * dtS;
                if (refineLeftKg <= 0) phase = Phase.ANODE;
            }
            default -> o2 = 0;
        }
        lastSo2KgS = dtS > 0 ? so2 / dtS : 0;
        lastO2KgS = dtS > 0 ? o2 / dtS : 0;
        totalSo2Kg += so2;
        return flux;
    }

    private double gasUse, refineLeftKg;

    /** Газ, взятый на дразнение в последнем шаге, нм³. */
    public double gasUse() {
        return gasUse;
    }

    /** Слить готовый металл: черновую ({@code BLISTER}) или анодную ({@code ANODE}) медь, кг Cu. */
    public double tapCu() {
        if (phase != Phase.BLISTER && phase != Phase.ANODE) return 0;
        double cu = cuKg;
        cuKg = feKg = sKg = 0;
        phase = Phase.EMPTY;
        return cu;
    }

    public double lastSo2KgS() {
        return lastSo2KgS;
    }

    public double lastO2KgS() {
        return lastO2KgS;
    }

    public double totalSo2Kg() {
        return totalSo2Kg;
    }

    public void save(StateWriter out) {
        out.putString("phase", phase.name());
        out.putDouble("cu", cuKg);
        out.putDouble("fe", feKg);
        out.putDouble("s", sKg);
        out.putDouble("left", refineLeftKg);
        out.putBoolean("refine", refine);
        out.putDouble("so2", totalSo2Kg);
    }

    public void load(StateReader in) {
        try {
            phase = Phase.valueOf(in.has("phase") ? in.getString("phase") : Phase.EMPTY.name());
        } catch (IllegalArgumentException e) {
            phase = Phase.EMPTY;
        }
        cuKg = in.optDouble("cu", 0);
        feKg = in.optDouble("fe", 0);
        sKg = in.optDouble("s", 0);
        refineLeftKg = in.optDouble("left", 0);
        refine = in.optInt("refine", 1) != 0;
        totalSo2Kg = in.optDouble("so2", 0);
    }
}
