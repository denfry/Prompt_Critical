package dev.denfry.promptcritical.sim.metallurgy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Литьё (08 §8.9): Чворинов, дефекты, вагранка, тигель; станки (08 §8.10): P_c и Тейлор. */
class FoundryTest {
    private final Casting casting = new Casting(MetallurgyConfig.defaults());

    private static Casting.Pour pour(CastAlloy alloy, MoldKind mold, double riser, double moisture, double superheat,
            boolean deox, double thickness) {
        return new Casting.Pour(CastingKind.VALVE_BODY, alloy, mold, 1.5, riser, moisture, superheat, deox, thickness,
                100);
    }

    private static Casting.Pour good(CastAlloy alloy) {
        return pour(alloy, MoldKind.SAND, 2.0, 0.04, 100, true, 1.5);
    }

    /** Корпус DN 100, модуль 1,5 см, сталь в песке: t_s = 2,5·1,5² = 5,6 мин; кокиль — в 7 раз быстрее. */
    @Test
    void chvorinovExample() {
        assertThat(CastAlloy.CARBON_STEEL.chvorinovFactor(100)).isEqualTo(1);
        Casting.Result r = casting.pour(good(CastAlloy.CARBON_STEEL));
        assertThat(r.solidificationS() / 60).isCloseTo(5.625, within(0.01));
        assertThat(r.defect()).isEqualTo(CastingDefect.NONE);
        assertThat(casting.solidificationS(CastAlloy.CARBON_STEEL, MoldKind.PERMANENT, 1.5, 100))
                .isCloseTo(r.solidificationS() / 7, within(1e-9));
        // модуль вдвое — время вчетверо
        assertThat(casting.solidificationS(CastAlloy.CARBON_STEEL, MoldKind.SAND, 3, 100))
                .isCloseTo(4 * r.solidificationS(), within(1e-9));
        // B других сплавов — того же порядка (вывод Чворинова)
        for (CastAlloy a : CastAlloy.values()) assertThat(a.chvorinovFactor(100)).isBetween(0.5, 2.5);
    }

    /** Каждая причина даёт свой дефект; самый опасный — первым. */
    @Test
    void defectRules() {
        CastAlloy st = CastAlloy.CARBON_STEEL;
        assertThat(casting.pour(pour(st, MoldKind.SAND, Double.NaN, 0.04, 100, true, 1)).defect())
                .isEqualTo(CastingDefect.SHRINKAGE_CAVITY);
        assertThat(casting.pour(pour(st, MoldKind.SAND, 1.79, 0.04, 100, true, 1)).defect())
                .isEqualTo(CastingDefect.SHRINKAGE_CAVITY);
        assertThat(casting.pour(pour(st, MoldKind.SAND, 1.8, 0.04, 100, true, 1)).defect())
                .isEqualTo(CastingDefect.NONE);
        assertThat(casting.pour(pour(st, MoldKind.SAND, 2, 0.04, 100, true, 4)).defect())
                .isEqualTo(CastingDefect.HOT_TEAR);
        assertThat(casting.pour(pour(CastAlloy.BRONZE, MoldKind.SAND, 2, 0.04, 100, true, 4)).defect())
                .isEqualTo(CastingDefect.NONE);
        assertThat(casting.pour(pour(st, MoldKind.SAND, 2, 0.04, 40, true, 1)).defect())
                .isEqualTo(CastingDefect.COLD_SHUT);
        assertThat(casting.pour(pour(st, MoldKind.SAND, 2, 0.07, 100, true, 1)).defect())
                .isEqualTo(CastingDefect.GAS_POROSITY);
        assertThat(casting.pour(pour(st, MoldKind.PERMANENT, 2, 0.07, 100, true, 1)).defect())
                .isEqualTo(CastingDefect.NONE);
        assertThat(casting.pour(pour(st, MoldKind.SAND, 2, 0.04, 100, false, 1)).defect())
                .isEqualTo(CastingDefect.GAS_POROSITY);
        assertThat(casting.pour(pour(CastAlloy.GRAY_IRON, MoldKind.PERMANENT, 2, 0, 100, false, 1)).defect())
                .isEqualTo(CastingDefect.CHILL);
        assertThat(casting.pour(good(CastAlloy.GRAY_IRON)).defect()).isEqualTo(CastingDefect.NONE);
        assertThat(casting.pour(new Casting.Pour(CastingKind.GEAR_BLANK, CastAlloy.GRAY_IRON, MoldKind.SAND, 0.3, 1,
                0.04, 100, false, 1, 0)).defect()).isEqualTo(CastingDefect.CHILL);
    }

    /** Контроль: раковину видит радиография, на гидроиспытании корпус течёт; отбел контролем не виден. */
    @Test
    void inspectionRevealsDefects() {
        Casting.Pour p = pour(CastAlloy.CARBON_STEEL, MoldKind.SAND, Double.NaN, 0.04, 100, true, 1);
        CastingSpec spec = casting.pour(p).spec(p, 7);
        assertThat(spec.defectFound()).isFalse();
        assertThat(spec.inspected(CastingSpec.RT).defectFound()).isTrue();
        assertThat(spec.passesHydrotest()).isFalse();
        assertThat(spec.kind().pressureRetaining()).isTrue();
        CastingSpec chill = new CastingSpec(CastingKind.WHEEL, "gray_iron", 0, CastingDefect.CHILL, 0, 1);
        assertThat(chill.inspected(CastingSpec.RT | CastingSpec.UT | CastingSpec.HYDRO).defectFound()).isFalse();
        CastingSpec gas = new CastingSpec(CastingKind.WHEEL, "carbon_steel", 0, CastingDefect.GAS_POROSITY, 0, 2);
        assertThat(gas.inspected(CastingSpec.UT).defectFound()).isFalse();
        assertThat(gas.inspected(CastingSpec.RT).defectFound()).isTrue();
        assertThat(MoldKind.PERMANENT.pourLife(CastAlloy.ALUMINIUM)).isBetween(500, 5000);
        assertThat(MoldKind.PERMANENT.pourLife(CastAlloy.GRAY_IRON)).isBetween(50, 300);
    }

    /** Вагранка: 12 % кокса → ≈ 1450 °C, больше кокса — горячее; дутьё задаёт производительность. */
    @Test
    void cupolaMeltsIronByBlast() {
        assertThat(CupolaFurnace.tapTemperatureK(0.12) - 273.15).isCloseTo(1445, within(10.0));
        assertThat(CupolaFurnace.tapTemperatureK(0.10)).isLessThan(CupolaFurnace.tapTemperatureK(0.12));
        assertThat(CupolaFurnace.tapTemperatureK(0.14)).isGreaterThan(CupolaFurnace.tapTemperatureK(0.12));

        CupolaFurnace c = new CupolaFurnace(2000);
        c.charge(1000, 120);
        for (int i = 0; i < 600; i++) c.stepSlow(1, 0.2);
        double rate = 0.2 / CupolaFurnace.AIR_PER_KG_COKE / 0.12;
        assertThat(c.wellKg()).isCloseTo(600 * rate, within(1.0));
        assertThat(c.superheatK()).isGreaterThan(200);
        assertThat(c.coNm3S()).isGreaterThan(0);
        assertThat(c.tap(50)).isEqualTo(50);
        assertThat(c.stackCokeKg() / c.stackIronKg()).isCloseTo(0.12, within(1e-9));
    }

    /** Мало кокса — плавится доля шихты, металл у ликвидуса: при заливке холодный спай. */
    @Test
    void cupolaStarvedOfCokeGivesColdIron() {
        CupolaFurnace c = new CupolaFurnace(2000);
        c.charge(1000, 50);
        for (int i = 0; i < 300; i++) c.stepSlow(1, 0.2);
        double full = 300 * 0.2 / CupolaFurnace.AIR_PER_KG_COKE / 0.05;
        assertThat(c.wellKg()).isGreaterThan(0).isLessThan(0.5 * full);
        assertThat(c.superheatK()).isLessThan(Casting.MIN_SUPERHEAT_K);
    }

    /** Тигель: 100 кг Al, 50 кВт без потерь — до 1000 К за H(1000)·m/Q. */
    @Test
    void crucibleMeltsAluminium() {
        CrucibleFurnace f = new CrucibleFurnace(200, 0);
        assertThat(f.charge(CastAlloy.ALUMINIUM, 100)).isEqualTo(100);
        assertThat(f.charge(CastAlloy.COPPER, 10)).isZero();
        double t = CastAlloy.ALUMINIUM.enthalpy(1000) * 100 / 50e3;
        assertThat(f.pour(10)).isZero();
        for (int i = 0; i < (int) t; i++) f.stepSlow(1, 50e3);
        f.stepSlow(t - (int) t, 50e3);
        assertThat(f.temperatureK()).isCloseTo(1000, within(0.5));
        assertThat(f.superheatK()).isCloseTo(67, within(0.5));
        assertThat(f.pour(100)).isEqualTo(100);
        assertThat(f.alloy()).isNull();
    }

    /** P_c = k_c·a_p·f·v_c; Тейлор n = 0,125, C_T = 70: 35 м/мин → 256 мин, 70 → 1 мин. */
    @Test
    void machiningPowerAndToolLife() {
        Machining m = new Machining(MetallurgyConfig.defaults());
        assertThat(Machining.cuttingPowerW(Machining.WorkMaterial.STEEL_45, 2, 0.2, 35)).isCloseTo(466.7, within(0.1));
        assertThat(Machining.cuttingSpeed(100, 111.4)).isCloseTo(35, within(0.01));
        assertThat(m.toolLifeMin(Machining.ToolMaterial.HSS, Machining.WorkMaterial.STEEL_45, 35))
                .isCloseTo(256, within(0.01));
        assertThat(m.toolLifeMin(Machining.ToolMaterial.HSS, Machining.WorkMaterial.STEEL_45, 70))
                .isCloseTo(1, within(1e-9));

        Machining.CuttingTool tool = new Machining.CuttingTool(Machining.ToolMaterial.HSS);
        double rpm = 70 * 1000 / (Math.PI * 100);
        for (int i = 0; i < 30; i++) m.cut(1, tool, Machining.WorkMaterial.STEEL_45, 100, rpm, 2, 0.2);
        assertThat(tool.wear()).isCloseTo(0.5, within(1e-9));
        for (int i = 0; i < 31; i++) m.cut(1, tool, Machining.WorkMaterial.STEEL_45, 100, rpm, 2, 0.2);
        assertThat(tool.dull()).isTrue();
        assertThat(m.cut(1, tool, Machining.WorkMaterial.STEEL_45, 100, rpm, 2, 0.2)).isZero();
        tool.regrind();
        assertThat(m.cut(1, tool, Machining.WorkMaterial.ALUMINIUM, 100, rpm, 2, 0.2))
                .isCloseTo(700 * 2 * 0.2 * 70 / 60.0, within(1e-6));
    }
}
