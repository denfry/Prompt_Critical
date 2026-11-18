package dev.denfry.promptcritical.sim.nitrogen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** Азотная химия: термодинамика, риформинг, очистка, синтез NH₃ и азотная кислота против справочных данных. */
class NitrogenTest {
    private static ReformerTrain loadedTrain() {
        ReformerTrain t = new ReformerTrain();
        t.loadPrimaryCatalyst();
        t.loadHts();
        t.loadLts();
        return t;
    }

    // --- термодинамика ---

    /** Энтальпии образования при 298,15 К (JANAF / Burcat): CH₄ −74,6, NH₃ −45,9, NO 91,3, NO₂ 34,2 кДж/моль; c_p по JANAF. */
    @Test
    void formationEnthalpiesMatchTables() {
        assertEquals(-74.6, Species.CH4.enthalpy(298.15) / 1e3, 0.3);
        assertEquals(-45.9, Species.NH3.enthalpy(298.15) / 1e3, 0.3);
        assertEquals(91.3, Species.NO.enthalpy(298.15) / 1e3, 0.5);
        assertEquals(34.2, Species.NO2.enthalpy(298.15) / 1e3, 1.2);
        assertEquals(35.7, Species.CH4.cp(298.15), 0.5);
        assertEquals(35.6, Species.NH3.cp(298.15), 0.3);
        for (Species s : Species.values())
            assertEquals(s.enthalpy(999.999), s.enthalpy(1000.001), 1.0, s + ": polynomial ranges join at 1000 K");
    }

    /** K₁ и K₂ при 1000 К против ΔG° по JANAF: 27,0 бар² и 1,42. */
    @Test
    void reformingEquilibriumConstantsMatchJanaf() {
        assertEquals(27.0, Reforming.k1(1000), 27.0 * 0.03);
        assertEquals(1.42, Reforming.k2(1000), 1.42 * 0.03);
    }

    // --- риформинг и конверсия CO ---

    /** Трубчатая печь при 3,2 МПа, 800 °C, S/C 3,5: метана 10–12 % в сухом газе (Appl, табл. 4.4), близко к равновесию. */
    @Test
    void primaryReformerApproachesEquilibrium() {
        ReformerTrain.Solution s = loadedTrain().solution();
        assertTrue(s.primaryCh4Dry() > 0.08 && s.primaryCh4Dry() < 0.13, "primary CH4 dry " + s.primaryCh4Dry());
        double[] feed = Species.flow();
        feed[Species.CH4.ordinal()] = 10;
        feed[Species.H2O.ordinal()] = 35;
        feed[Species.H2.ordinal()] = 0.3;
        double eq = Species.dryFraction(Reforming.equilibrium(feed, 1073.15, 32, 0), Species.CH4);
        assertTrue(s.primaryCh4Dry() >= eq - 1e-3 && s.primaryCh4Dry() - eq < 0.03, "approach: " + s.primaryCh4Dry() + " vs eq " + eq);
        // много катализатора — до равновесия
        Reforming.Result big = Reforming.primary(feed, 32, 773.15, 1073.15, 100 * ReformerTrain.CATALYST_KG, 1);
        assertEquals(eq, big.dry(Species.CH4), 0.004);
        // без катализатора — без превращения
        Reforming.Result none = Reforming.primary(feed, 32, 773.15, 1073.15, ReformerTrain.CATALYST_KG, 0);
        assertEquals(10, none.n()[Species.CH4.ordinal()], 1e-9);
    }

    /**
     * Вторичный риформинг на воздухе под H₂:N₂ = 3: выход 950–1050 °C, метана меньше 0,6 %; ВТК — 2–4 % CO при
     * 420–450 °C, НТК — 0,2–0,4 % CO («Справочник азотчика», т. 1, табл. 3.12, 4.3).
     */
    @Test
    void secondaryReformerAndShiftHitTheirTargets() {
        ReformerTrain.Solution s = loadedTrain().solution();
        assertTrue(s.secondaryK() > 950 + 273.15 && s.secondaryK() < 1050 + 273.15, "secondary " + (s.secondaryK() - 273.15));
        assertTrue(s.secondaryCh4Dry() < 0.006, "secondary CH4 " + s.secondaryCh4Dry());
        assertEquals(3.10, s.ratioOut(), 0.01);
        assertTrue(s.airMolS() > 1.1 && s.airMolS() < 1.7, "process air per mol CH4 " + s.airMolS());
        assertTrue(s.htsCoDry() > 0.02 && s.htsCoDry() < 0.04, "HTS CO " + s.htsCoDry());
        assertTrue(s.htsOutK() > 420 + 273.15 && s.htsOutK() < 460 + 273.15, "HTS out " + (s.htsOutK() - 273.15));
        assertTrue(s.ltsCoDry() > 0.002 && s.ltsCoDry() < 0.004, "LTS CO " + s.ltsCoDry());
        ReformerTrain blank = loadedTrain();
        blank.load(state(t -> { }));
        assertEquals(0, blank.primaryActivity(), "fresh state carries no catalyst");
    }

    /** Мало пара — углерод на никеле: активность падает; с паром — не падает. */
    @Test
    void lowSteamCokesTheNickel() {
        ReformerTrain t = loadedTrain();
        t.step(3600, 10);
        assertEquals(1, t.primaryActivity(), 1e-12);
        t.setSteamToCarbon(2.0);
        for (int i = 0; i < 10; i++) t.step(3600, 10);
        assertTrue(t.primaryActivity() < 0.6, "activity " + t.primaryActivity());
    }

    /** Расход газа на тонну NH₃ (сырьё + топливо печи): агрегаты 1970-х — 30–38 ГДж/т. */
    @Test
    void specificEnergyIsThatOfASeventiesPlant() {
        ReformerTrain t = loadedTrain();
        ReformerTrain.Solution s = t.solution();
        double[] gas = s.shifted().clone();
        for (int i = 0; i < gas.length; i++) gas[i] *= ReformerTrain.RATED_CH4_MOL_S;
        Purification.Result r = new Purification().step(gas, s.processHeatW() * ReformerTrain.RATED_CH4_MOL_S);
        double nh3MolS = r.syngas()[Species.N2.ordinal()] * 2 * 0.95; // 5 % — продувка
        double gasW = ReformerTrain.RATED_CH4_MOL_S * (SynthesisLoop.LHV_CH4 + t.fuelPerMolW());
        double gjPerT = gasW / (nh3MolS * Species.NH3.molarMass()) / 1e6;
        assertTrue(gjPerT > 30 && gjPerT < 38, "GJ/t " + gjPerT);
    }

    // --- очистка ---

    @Test
    void meaAndMethanatorMakeCleanSyngas() {
        ReformerTrain.Solution s = loadedTrain().solution();
        double[] gas = s.shifted().clone();
        for (int i = 0; i < gas.length; i++) gas[i] *= ReformerTrain.RATED_CH4_MOL_S;
        Purification p = new Purification();
        Purification.Result r = p.step(gas, s.processHeatW() * ReformerTrain.RATED_CH4_MOL_S);
        assertTrue(r.co2SlipDry() <= 3e-4, "CO2 after MEA " + r.co2SlipDry());
        assertTrue(r.oxygenates() <= Purification.MAX_OXYGENATES, "CO + CO2 " + r.oxygenates());
        assertTrue(r.onSpec(), "syngas on spec");
        assertTrue(r.ratio() > 2.95 && r.ratio() < 3.1, "H2:N2 " + r.ratio());
        double perKg = r.regenW() / (r.co2VentMolS() * Species.CO2.molarMass());
        assertTrue(perKg > 3.5e6 && perKg < 6.5e6, "regeneration MJ/kg CO2 " + perKg / 1e6);
        // нет циркуляции — CO₂ проходит, метанатор перегревается, блокировка
        p.setCirculation(0.2);
        Purification.Result bad = p.step(gas, s.processHeatW() * ReformerTrain.RATED_CH4_MOL_S);
        assertTrue(bad.co2SlipDry() > 0.01, "slip " + bad.co2SlipDry());
        assertTrue(p.tripped() && !bad.onSpec(), "methanator trip");
        p.setCirculation(1);
        assertFalse(p.step(gas, s.processHeatW() * ReformerTrain.RATED_CH4_MOL_S).onSpec(), "trip latches");
        p.resetTrip();
        assertTrue(p.step(gas, s.processHeatW() * ReformerTrain.RATED_CH4_MOL_S).onSpec());
    }

    /** Подъём температуры в метанаторе: ≈ 74 К на 1 % CO и ≈ 60 К на 1 % CO₂ (Appl, §4.4). */
    @ParameterizedTest
    @CsvSource({"CO, 74", "CO2, 60"})
    void methanatorTemperatureRise(String oxide, double kPerPercent) {
        double[] gas = Species.flow();
        gas[Species.H2.ordinal()] = 74;
        gas[Species.N2.ordinal()] = 25;
        gas[Species.valueOf(oxide).ordinal()] = 1;
        Purification p = new Purification();
        p.setCirculation(0);
        Purification.Result r = p.step(gas, 0);
        assertEquals(kPerPercent, r.methanatorOutK() - Purification.METHANATOR_IN_K, 8);
    }

    // --- синтез аммиака ---

    /** Равновесное содержание NH₃ в смеси 3:1, % — таблица Ларсона–Доджа (Larson, JACS 46 (1924) 367). */
    @ParameterizedTest
    @CsvSource({"400, 10, 3.85", "400, 100, 25.12", "400, 300, 47.00", "450, 10, 2.04", "450, 100, 16.36", "450, 300, 35.50",
            "500, 100, 10.61", "500, 300, 25.80"})
    void equilibriumMatchesLarsonDodge(double c, double atm, double percent) {
        assertEquals(percent, 100 * AmmoniaSynthesis.equilibriumFraction(c + 273.15, atm * 1.01325), 0.8);
    }

    /** Давление пара аммиака: −33,3 °C — 1,013 бар, 20 °C — 8,57 бар. */
    @Test
    void ammoniaVaporPressure() {
        assertEquals(1.013, AmmoniaSynthesis.vaporPressureBar(239.8), 0.03);
        assertEquals(8.57, AmmoniaSynthesis.vaporPressureBar(293.15), 0.15);
    }

    /** Адиабатический слой: 13–17 К на каждый процент NH₃ (Appl, §3.6), выход не выше равновесия. */
    @Test
    void adiabaticBedRisesAboutFifteenKelvinPerPercent() {
        double[] in = Species.flow();
        in[Species.H2.ordinal()] = 0.7;
        in[Species.N2.ordinal()] = 0.233;
        in[Species.NH3.ordinal()] = 0.03;
        in[Species.CH4.ordinal()] = 0.037;
        AmmoniaSynthesis.Bed b = AmmoniaSynthesis.bed(in, 673.15, 250, 0.05, 1);
        double dy = 100 * (Species.fraction(b.n(), Species.NH3) - Species.fraction(in, Species.NH3));
        assertTrue(dy > 3, "conversion in the bed " + dy);
        double perPercent = (b.outK() - 673.15) / dy;
        assertTrue(perPercent > 13 && perPercent < 17, "K per % NH3 " + perPercent);
        double eq = Species.fraction(AmmoniaSynthesis.equilibrium(in, b.outK(), 250), Species.NH3);
        assertTrue(Species.fraction(b.n(), Species.NH3) <= eq + 1e-6, "above equilibrium");
    }

    private static SynthesisLoop runLoop(double purge, int steps) {
        SynthesisLoop loop = new SynthesisLoop();
        loop.setRunning(true);
        loop.setPurgeMolS(purge);
        for (int i = 0; i < steps; i++) loop.step(3.6, 45 * 3.6, 1e9);
        return loop;
    }

    /** Цикл выходит на режим: 15–30 МПa, 10–20 % NH₃ на выходе колонны, 2–4 % на входе, выработка — почти весь азот свежего газа. */
    @Test
    void synthesisLoopSettlesAtAnIndustrialPoint() {
        SynthesisLoop loop = runLoop(3, 4000);
        SynthesisLoop.Result r = loop.last();
        assertTrue(loop.pressureBar() > 150 && loop.pressureBar() < 300, "loop pressure " + loop.pressureBar());
        assertTrue(r.outletNh3() > 0.10 && r.outletNh3() < 0.20, "outlet NH3 " + r.outletNh3());
        assertTrue(r.inletNh3() > 0.015 && r.inletNh3() < 0.045, "inlet NH3 " + r.inletNh3());
        double n2In = 45 * SynthesisLoop.NOMINAL_MAKEUP[Species.N2.ordinal()];
        assertTrue(r.ammoniaMolS() > 0.9 * 2 * n2In * (1 - 3.0 / 45) && r.ammoniaMolS() < 2 * n2In, "NH3 " + r.ammoniaMolS());
        assertTrue(r.inerts() > 0.05 && r.inerts() < 0.25, "inerts " + r.inerts());
        assertTrue(r.maxBedK() < AmmoniaSynthesis.CATALYST_LIMIT_K, "bed " + (r.maxBedK() - 273.15));
        assertTrue(r.purgeFuelW() > 0);
    }

    /** Без продувки инерты копятся: давление упирается в предел, свежий газ уходит на байпас, выработка падает. */
    @Test
    void withoutPurgeInertsChokeTheLoop() {
        SynthesisLoop purged = runLoop(3, 15000), choked = runLoop(0, 15000);
        assertTrue(choked.last().inerts() > purged.last().inerts() + 0.1, "inerts " + choked.last().inerts());
        assertTrue(choked.pressureBar() > 0.95 * SynthesisLoop.MAX_BAR, "pressure " + choked.pressureBar());
        assertTrue(choked.last().ammoniaMolS() < 0.8 * purged.last().ammoniaMolS(), "production " + choked.last().ammoniaMolS());
    }

    /** Холоднее сепаратор — меньше аммиака на входе колонны, но больше мощность холодильника. */
    @Test
    void colderSeparatorCostsPower() {
        SynthesisLoop cold = new SynthesisLoop(), warm = new SynthesisLoop();
        cold.setSeparatorK(258.15);
        warm.setSeparatorK(303.15);
        for (SynthesisLoop l : new SynthesisLoop[] {cold, warm}) {
            l.setRunning(true);
            for (int i = 0; i < 3000; i++) l.step(3.6, 45 * 3.6, 1e9);
        }
        assertTrue(cold.last().inletNh3() < warm.last().inletNh3());
        assertTrue(cold.last().chillerW() > warm.last().chillerW());
    }

    @Test
    void loopStateRoundTrips() {
        SynthesisLoop a = runLoop(3, 500);
        MutableStateTree tree = new MutableStateTree();
        a.save(new TreeStateWriter(tree));
        SynthesisLoop b = new SynthesisLoop();
        b.load(new TreeStateReader(tree));
        assertEquals(a.pressureBar(), b.pressureBar(), 1e-9);
        assertEquals(a.fraction(Species.AR), b.fraction(Species.AR), 1e-12);
        assertTrue(b.running());
    }

    // --- азотная кислота ---

    private static NitricAcidPlant plant(int pressure) {
        NitricAcidPlant n = new NitricAcidPlant();
        n.setRunning(true);
        n.loadGauze();
        n.setPressureIndex(pressure);
        return n;
    }

    /**
     * УКЛ-7 (0,73 МПа): сетки 870–910 °C, выход NO 94–95 %, 58 % кислота, после колонны 0,05–0,2 % NOx, степень
     * поглощения выше 98 % («Справочник азотчика», т. 2, табл. 2.1).
     */
    @Test
    void ukl7MakesFiftyEightPercentAcid() {
        NitricAcidPlant.Result r = plant(2).step(3.6, 100, 1e5, 1e9);
        assertTrue(r.gauzeK() > 870 + 273.15 && r.gauzeK() < 910 + 273.15, "gauze " + (r.gauzeK() - 273.15));
        assertTrue(r.selectivity() > 0.935 && r.selectivity() < 0.955, "selectivity " + r.selectivity());
        assertEquals(0.58, r.strength(), 1e-6);
        assertTrue(r.tailNoxPpm() > 500 && r.tailNoxPpm() < 2000, "tail NOx ppm " + r.tailNoxPpm());
        assertTrue(r.absorption() > 0.98, "absorption " + r.absorption());
        double yield = r.hno3MolS() / r.nh3MolS();
        assertTrue(yield > 0.90 && yield < 0.95, "HNO3 per NH3 " + yield);
    }

    /** Атмосферная схема: сетки холоднее, выход NO выше, но поглощение хуже — бурый хвост. */
    @Test
    void atmosphericPlantLeaksNox() {
        NitricAcidPlant atm = plant(0);
        atm.setStrengthSet(0.47);
        NitricAcidPlant.Result a = atm.step(3.6, 100, 1e5, 1e9), h = plant(2).step(3.6, 100, 1e5, 1e9);
        assertTrue(a.selectivity() > h.selectivity());
        assertTrue(a.tailNoxPpm() > 5 * h.tailNoxPpm(), a.tailNoxPpm() + " vs " + h.tailNoxPpm());
        assertTrue(a.ptLossGS() / a.hno3MolS() < h.ptLossGS() / h.hno3MolS(), "platinum loss grows with pressure");
    }

    /** Кислота крепче предела колонны: нижние тарелки не поглощают, NOx растёт. */
    @Test
    void tooStrongAcidStarvesTheColumn() {
        NitricAcidPlant ok = plant(2), greedy = plant(2);
        greedy.setStrengthSet(0.66);
        double g = greedy.step(3.6, 100, 1e5, 1e9).tailNoxPpm(), o = ok.step(3.6, 100, 1e5, 1e9).tailNoxPpm();
        assertTrue(g > 1.5 * o, g + " vs " + o);
    }

    @Test
    void denoxCleansTheTailGas() {
        NitricAcidPlant clean = plant(2);
        clean.loadDenox();
        assertTrue(clean.step(3.6, 100, 1e5, 1e9).tailNoxPpm() < 200);
    }

    /** Смесь выше нижнего предела взрываемости вспыхивает; сетки без платины не работают. */
    @Test
    void richMixtureIgnites() {
        NitricAcidPlant n = plant(2);
        n.setAmmoniaPercent(12.5);
        assertTrue(n.step(3.6, 100, 1e5, 1e9).ignited());
        NitricAcidPlant bare = new NitricAcidPlant();
        bare.setRunning(true);
        assertEquals(0, bare.step(3.6, 100, 1e5, 1e9).hno3MolS());
    }

    // --- концентрирование ---

    @Test
    void magnesiumNitrateBreaksTheAzeotrope() {
        AcidConcentrator c = new AcidConcentrator();
        AcidConcentrator.Result r = c.run(1);
        assertTrue(r.onSpec() && r.topStrength() > 0.98, "top " + r.topStrength());
        double mjPerKg = r.heatW() / r.hno3KgS() / 1e6;
        assertTrue(mjPerKg > 2 && mjPerKg < 5, "heat MJ/kg " + mjPerKg);
        c.setSaltRatio(1.5);
        c.setReflux(0.3);
        assertFalse(c.run(1).onSpec(), "too little salt and reflux: azeotrope-like top");
    }

    private interface Fill {
        void fill(TreeStateWriter w);
    }

    private static TreeStateReader state(Fill f) {
        MutableStateTree tree = new MutableStateTree();
        f.fill(new TreeStateWriter(tree));
        return new TreeStateReader(tree);
    }
}
