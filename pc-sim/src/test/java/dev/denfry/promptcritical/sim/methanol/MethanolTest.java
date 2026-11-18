package dev.denfry.promptcritical.sim.methanol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.nitrogen.Species;
import dev.denfry.promptcritical.sim.thermo.if97.If97;
import org.junit.jupiter.api.Test;

/** Синтез метанола: равновесие Graaf, кинетика Vanden Bussche–Froment, контур, модуль S, спекание и ректификация. */
class MethanolTest {
    private static final int CO = Species.CO.ordinal(), CO2 = Species.CO2.ordinal(), H2 = Species.H2.ordinal(),
            H2O = Species.H2O.ordinal(), MEOH = Species.CH3OH.ordinal(), CH4 = Species.CH4.ordinal(), N2 = Species.N2.ordinal();

    private static double[] gas(double co, double co2, double h2, double ch4, double n2, double molS) {
        double[] n = Species.flow();
        n[CO] = co * molS;
        n[CO2] = co2 * molS;
        n[H2] = h2 * molS;
        n[CH4] = ch4 * molS;
        n[N2] = n2 * molS;
        return n;
    }

    // --- термодинамика ---

    /** Энтальпия образования CH₃OH(г) −201,0 кДж/моль, c_p 44,1 Дж/(моль·К) (JANAF); полиномы стыкуются при 1000 К. */
    @Test
    void methanolVapourPropertiesMatchJanaf() {
        assertEquals(-201.0, Species.CH3OH.enthalpy(298.15) / 1e3, 0.3);
        assertEquals(44.1, Species.CH3OH.cp(298.15), 0.3);
        assertEquals(Species.CH3OH.enthalpy(999.999), Species.CH3OH.enthalpy(1000.001), 1.0);
    }

    /** K₁ = K₂/K₃ у Graaf тождественно; теплоты реакций по таблицам −90,6 и −49,5 кДж/моль. */
    @Test
    void graafConstantsAreConsistentWithReactionHeats() {
        for (double t = 473; t <= 573; t += 10)
            assertEquals(1, MethanolSynthesis.kCo(t) / (MethanolSynthesis.kCo2(t) / MethanolSynthesis.kRwgs(t)), 1e-9);
        double t0 = 298.15;
        double dh1 = Species.CH3OH.enthalpy(t0) - Species.CO.enthalpy(t0) - 2 * Species.H2.enthalpy(t0);
        double dh2 = Species.CH3OH.enthalpy(t0) + Species.H2O.enthalpy(t0) - Species.CO2.enthalpy(t0) - 3 * Species.H2.enthalpy(t0);
        assertEquals(-90.6, dh1 / 1e3, 0.6);
        assertEquals(-49.5, dh2 / 1e3, 0.6);
        // вант-Гофф: наклон lg K₁ по 1/T у Graaf — средняя теплота реакции в рабочем интервале (≈ 523 К)
        double t = 523.15;
        double dh1t = Species.CH3OH.enthalpy(t) - Species.CO.enthalpy(t) - 2 * Species.H2.enthalpy(t);
        double dh3t = Species.CO.enthalpy(t) + Species.H2O.enthalpy(t) - Species.CO2.enthalpy(t) - Species.H2.enthalpy(t);
        assertEquals(dh1t, -Species.R * Math.log(10) * 5139, 0.03 * Math.abs(dh1t));
        assertEquals(dh3t, -Species.R * Math.log(10) * -2073, 0.03 * Math.abs(dh3t));
    }

    /** T10.7 GDD: равновесная конверсия CO при H₂/CO = 2 (идеальный газ). */
    @Test
    void equilibriumCoConversionMatchesT10_7() {
        assertEquals(0.6415, MethanolSynthesis.equilibriumCoConversion(523, 80, 2), 0.002);
        assertEquals(0.5003, MethanolSynthesis.equilibriumCoConversion(523, 50, 2), 0.002);
        assertEquals(0.4514, MethanolSynthesis.equilibriumCoConversion(553, 80, 2), 0.002);
    }

    /** Равновесие двух реакций удовлетворяет обеим константам и сохраняет атомы. */
    @Test
    void twoReactionEquilibriumSatisfiesBothConstantsAndBalances() {
        double[] in = gas(0.10, 0.05, 0.70, 0.10, 0.05, 1);
        double t = 513.15, p = 75;
        double[] e = MethanolSynthesis.equilibrium(in, t, p);
        double tot = Species.total(e);
        double k1 = e[MEOH] * tot * tot / (e[CO] * e[H2] * e[H2] * p * p);
        double k3 = e[CO] * e[H2O] / (e[CO2] * e[H2]);
        assertEquals(1, k1 / MethanolSynthesis.kCo(t), 1e-3);
        assertEquals(1, k3 / MethanolSynthesis.kRwgs(t), 1e-3);
        assertEquals(in[CO] + in[CO2], e[CO] + e[CO2] + e[MEOH], 1e-9);
        assertEquals(in[CO] + 2 * in[CO2], e[CO] + 2 * e[CO2] + e[MEOH] + e[H2O], 1e-9);
        assertEquals(2 * in[H2], 2 * e[H2] + 4 * e[MEOH] + 2 * e[H2O], 1e-9);
    }

    /** Давление пара метанола в нормальной точке кипения 337,8 К — 1,013 бар. */
    @Test
    void methanolBoilsAtItsNormalBoilingPoint() {
        assertEquals(1.01325, MethanolSynthesis.methanolVaporPressureBar(337.8), 0.02);
    }

    // --- кинетика ---

    /**
     * Газ контура при 250 °C, 80 бар: на 1,5 т катализатора выход близок к равновесию, на 10× меньшей загрузке —
     * кинетический режим; удельная производительность 0,3–1,5 кг/(кг·ч) (Lurgi: 0,6–1,0 кг/(л·ч), Hansen, Højlund
     * Nielsen, 2008).
     */
    @Test
    void kineticsApproachEquilibriumAtIndustrialSpaceTimeYield() {
        double[] in = gas(0.035, 0.03, 0.75, 0.13, 0.05, 220);
        in[MEOH] = 0.004 * 220;
        in[H2O] = 0.0005 * 220;
        double t = 523.15, p = 80;
        double[] full = MethanolSynthesis.reactor(in, t, p, MethanolLoop.CATALYST_KG, 1);
        double[] thin = MethanolSynthesis.reactor(in, t, p, MethanolLoop.CATALYST_KG / 10, 1);
        double[] eq = MethanolSynthesis.equilibrium(in, t, p);
        double made = full[MEOH] - in[MEOH], possible = eq[MEOH] - in[MEOH];
        assertTrue(made > 0.85 * possible, "near equilibrium: " + made + " of " + possible);
        assertTrue(made <= possible * 1.001, "not past equilibrium");
        assertTrue(thin[MEOH] - in[MEOH] < 0.8 * made, "thin bed is kinetic: " + (thin[MEOH] - in[MEOH]));
        double sty = made * Species.CH3OH.molarMass() * 3600 / MethanolLoop.CATALYST_KG;
        assertTrue(sty > 0.3 && sty < 1.5, "space-time yield " + sty + " kg/(kg h)");
        // атомы сохраняются
        assertEquals(in[CO] + in[CO2] + in[MEOH], full[CO] + full[CO2] + full[MEOH], 1e-9);
    }

    /** Без отвода тепла слой (газ входит при 250 °C) уходит выше 300 °C — туда, где медь спекается. */
    @Test
    void uncooledBedRunsPastTheSinteringLimit() {
        double[] in = gas(0.06, 0.04, 0.72, 0.13, 0.05, 1);
        double tad = MethanolSynthesis.adiabaticEquilibriumK(in, 523.15, 80);
        assertTrue(tad > MethanolSynthesis.SINTER_K, "adiabatic equilibrium " + (tad - 273.15) + " °C");
        assertTrue(tad < 523.15 + 300, "bounded by equilibrium");
    }

    // --- печь и контур ---

    /** Газ паровой конверсии метана для метанола: S ≈ 2,8–3,0 (GDD 10 §10.2.4), метана в сухом газе — единицы %. */
    @Test
    void reformedGasCarriesExcessHydrogen() {
        MethanolReformer r = new MethanolReformer();
        r.loadCatalyst();
        MethanolReformer.Solution s = r.solution();
        assertTrue(s.sn() > 2.7 && s.sn() < 3.1, "S = " + s.sn());
        assertTrue(s.ch4Dry() > 0.01 && s.ch4Dry() < 0.08, "methane slip " + s.ch4Dry());
        assertTrue(s.dutyW() > 0 && s.wasteHeatW() > 0);
    }

    private static MethanolLoop.Result settle(MethanolLoop loop, double[] makeup, int steps) {
        MethanolLoop.Result r = null;
        for (int i = 0; i < steps; i++) r = loop.step(5, makeup, 1e7, true);
        return r;
    }

    private static MethanolLoop runningLoop(double purgeMolS) {
        MethanolLoop loop = new MethanolLoop();
        loop.loadCatalyst();
        loop.setPurgeMolS(purgeMolS);
        loop.setRunning(true);
        return loop;
    }

    /**
     * Контур на газе печи (10 моль/с метана): давление 60–100 бар, кратность циркуляции 3–6, 3–9 % метанола на выходе
     * реактора, сырец 75–92 % метанола, сжатие ≈ 0,9 МДж/кг метанола (GDD), пар 4 МПа ≈ 1 т на тонну.
     */
    @Test
    void loopSettlesAtAnIndustrialOperatingPoint() {
        MethanolReformer ref = new MethanolReformer();
        ref.loadCatalyst();
        double[] makeup = ref.step(0, MethanolReformer.RATED_CH4_MOL_S);
        MethanolLoop loop = runningLoop(12);
        MethanolLoop.Result r = settle(loop, makeup, 1500);
        double p = loop.pressureBar();
        assertTrue(p > 60 && p < 100, "loop pressure " + p);
        assertEquals(1, r.accepted(), 1e-9, "all make-up gas taken");
        double rr = (r.circulationMolS() - r.makeupMolS()) / r.makeupMolS();
        assertTrue(rr > 3 && rr < 6, "recycle ratio " + rr);
        assertTrue(r.outletMeoh() > 0.03 && r.outletMeoh() < 0.09, "outlet methanol " + r.outletMeoh());
        assertTrue(r.crudeStrength() > 0.75 && r.crudeStrength() < 0.92, "crude " + r.crudeStrength());
        double kgS = r.methanolMolS() * Species.CH3OH.molarMass();
        double carbon = makeup[CO] + makeup[CO2];
        assertTrue(r.methanolMolS() / carbon > 0.85, "carbon efficiency " + r.methanolMolS() / carbon);
        assertTrue(kgS > 0.2 && kgS < 0.4, "methanol " + kgS + " kg/s");
        double mjPerKg = r.shaftW() / kgS / 1e6;
        assertTrue(mjPerKg > 0.5 && mjPerKg < 1.3, "compression " + mjPerKg + " MJ/kg");
        double steamPerKg = r.steamKgS() / kgS;
        assertTrue(steamPerKg > 0.6 && steamPerKg < 1.6, "steam " + steamPerKg + " t/t");
        assertEquals(If97.tsat(40e5) + MethanolSynthesis.BED_RISE_K, r.bedK(), 0.5);
    }

    /**
     * Модуль S: при S ≈ 2,05 водород используется почти весь; газ печи (S ≈ 2,9) сбрасывает лишний водород с продувкой;
     * газ газификатора (S ≈ 1) — водорода не хватает, углерод уходит в продувку.
     */
    @Test
    void stoichiometricNumberDecidesWhatTheLoopWastes() {
        double[] balanced = gas(0.22, 0.08, 0.69, 0.008, 0.002, 40);
        double[] lean = gas(0.40, 0.05, 0.50, 0, 0.05, 40);
        MethanolReformer ref = new MethanolReformer();
        ref.loadCatalyst();
        double[] rich = ref.step(0, MethanolReformer.RATED_CH4_MOL_S);
        assertEquals(2.03, MethanolSynthesis.stoichiometricNumber(balanced), 0.01);
        MethanolLoop.Result b = settle(runningLoop(1.5), balanced, 1500);
        MethanolLoop.Result l = settle(runningLoop(1.5), lean, 1500);
        MethanolLoop.Result r = settle(runningLoop(12), rich, 1500);
        double hB = 2 * b.methanolMolS() / (balanced[H2] * b.accepted());
        double hR = 2 * r.methanolMolS() / (rich[H2] * r.accepted());
        double cB = b.methanolMolS() / ((balanced[CO] + balanced[CO2]) * b.accepted());
        double cL = l.methanolMolS() / ((lean[CO] + lean[CO2]) * l.accepted());
        assertTrue(hB > 0.85, "hydrogen use at S 2.05: " + hB);
        assertTrue(hR < 0.80, "hydrogen use at S 2.9: " + hR);
        assertTrue(cB > 0.85, "carbon use at S 2.05: " + cB);
        assertTrue(cL < 0.70, "carbon use at S 1: " + cL);
    }

    /** Медь: при 250 °C стареет годами; без воды в межтрубье слой перегревается и спекается за часы. */
    @Test
    void copperSintersWhenTheShellRunsDry() {
        MethanolReformer ref = new MethanolReformer();
        ref.loadCatalyst();
        double[] makeup = ref.step(0, MethanolReformer.RATED_CH4_MOL_S);
        MethanolLoop loop = runningLoop(12);
        settle(loop, makeup, 720); // час
        assertTrue(loop.activity() > 0.9999, "an hour at 250 °C: " + loop.activity());
        double before = loop.activity();
        MethanolLoop.Result r = null;
        for (int i = 0; i < 720; i++) r = loop.step(5, makeup, 1e7, false); // час без охлаждения
        assertTrue(r.bedK() > MethanolSynthesis.SINTER_K, "dry shell bed " + (r.bedK() - 273.15) + " °C");
        assertTrue(loop.activity() < before - 0.05, "sintered: " + loop.activity());
        assertEquals(0, r.steamKgS(), 0, "no steam without water");
    }

    @Test
    void loopStateRoundTrips() {
        MethanolLoop loop = runningLoop(7);
        loop.setSteamBar(45);
        loop.step(5, gas(0.2, 0.08, 0.7, 0.01, 0.01, 30), 1e7, true);
        MutableStateTree tree = new MutableStateTree();
        loop.save(new TreeStateWriter(tree));
        MethanolLoop copy = new MethanolLoop();
        copy.load(new TreeStateReader(tree));
        assertEquals(loop.pressureBar(), copy.pressureBar(), 1e-9);
        assertEquals(45, copy.steamBar(), 1e-12);
        assertEquals(7, copy.purgeMolS(), 1e-12);
        assertTrue(copy.running());
        assertEquals(loop.activity(), copy.activity(), 1e-12);
    }

    // --- ректификация ---

    /** Сырец 80 %: при флегме 1,5 и тепле — марка AA (вода ≤ 0,10 %), без тепла флегмы нет — вода в продукте. */
    @Test
    void columnMakesGradeAaOnlyWithEnoughBoilUp() {
        MethanolColumn c = new MethanolColumn();
        MethanolColumn.Result ok = c.run(9, 2.5, 1e8);
        assertTrue(ok.gradeAa(), "water " + ok.waterWt());
        assertEquals(9 * (1 - MethanolColumn.TOPPING_LOSS) * MethanolColumn.RECOVERY, ok.methanolMolS(), 1e-9);
        double mjPerKg = ok.reboilerW() / ok.productKgS() / 1e6;
        assertTrue(mjPerKg > 2 && mjPerKg < 4.5, "reboiler " + mjPerKg + " MJ/kg");
        MethanolColumn.Result starved = c.run(9, 2.5, 0.5 * ok.reboilerW());
        assertTrue(starved.refluxActual() < c.reflux());
        assertFalse(starved.gradeAa(), "starved column water " + starved.waterWt());
        c.setReflux(0.3);
        assertFalse(c.run(9, 2.5, 1e8).gradeAa(), "below minimum reflux");
    }
}
