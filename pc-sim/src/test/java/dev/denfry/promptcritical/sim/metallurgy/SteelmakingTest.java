package dev.denfry.promptcritical.sim.metallurgy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.sim.consequence.DamageRequest;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Конвертер (08 §8.6.1) T8.10, тепловой баланс и лом; ковш, доводка, изложница; расплав в воду T8.19. */
class SteelmakingTest {
    private static final SteelmakingConfig CFG = SteelmakingConfig.defaults();
    private static final double HM_K = 1623.15;

    private static SteelMelt hotMetal(double kg, double c, double si, double mn, double p) {
        Map<AlloyElement, Double> chem = new EnumMap<>(AlloyElement.class);
        chem.put(AlloyElement.C, c);
        chem.put(AlloyElement.SI, si);
        chem.put(AlloyElement.MN, mn);
        chem.put(AlloyElement.P, p);
        chem.put(AlloyElement.S, 0.0003);
        return new SteelMelt(kg, HM_K, chem);
    }

    /** Продувка до C ≤ {@code endC}; возвращает время, с. */
    private static double blowTo(Converter bof, double endC, double dt, double lance) {
        double t = 0;
        while (bof.carbonFrac() > endC && t < 3600) {
            bof.stepSlow(dt, 0.5, lance);
            t += dt;
        }
        return t;
    }

    @Test
    void t8_10_blowDownTakesSeventeenMinutes() {
        Converter bof = new Converter(CFG);
        bof.charge(hotMetal(10_000, 0.045, 0, 0, 0), 0, 0);
        bof.stepSlow(60, 0.5, 1.0);
        assertThat(450 - bof.carbonFrac() * bof.metalKg()).as("плато, кг C за минуту").isCloseTo(0.4555 * 60, within(0.6));
        double t = 60 + blowTo(bof, 0.0005, 1, 1.0);
        assertThat(t / 60).as("продувка, мин физ.").isCloseTo(17, within(1.5));
        assertThat(bof.carbonFrac() * 100).isCloseTo(0.05, within(0.01));
        assertThat(bof.phase()).isEqualTo(ConverterPhase.BLOWING);
        bof.stepSlow(1, 0, 3);
        assertThat(bof.phase()).isEqualTo(ConverterPhase.BLOWN);
    }

    @Test
    void coarseSlowStepMatchesFineStep() {
        Converter fine = new Converter(CFG), coarse = new Converter(CFG);
        fine.charge(hotMetal(10_000, 0.045, 0, 0, 0), 0, 0);
        coarse.charge(hotMetal(10_000, 0.045, 0, 0, 0), 0, 0);
        for (int k = 0; k < 1008; k++) fine.stepSlow(1, 0.5, 1.0);
        for (int k = 0; k < 14; k++) coarse.stepSlow(72, 0.5, 1.0);
        assertThat(coarse.carbonFrac()).isCloseTo(fine.carbonFrac(), within(0.05 * fine.carbonFrac()));
        assertThat(coarse.carbonFrac()).isPositive();
    }

    @Test
    void twentyPercentScrapEndsNearTappingTemperature() {
        Converter bof = new Converter(CFG);
        bof.charge(hotMetal(8_000, 0.045, 0.005, 0.003, 0.001), 2_000, 500);
        List<Double> coFrac = new ArrayList<>();
        double t = 0;
        while (bof.carbonFrac() > 0.0005) {
            bof.stepSlow(5, 0.5, 1.0);
            t += 5;
            if (Math.abs(t - 600) < 1) coFrac.add(bof.offgasCoFrac());
        }
        assertThat(bof.tempK() - 273.15).as("T выпуска, °C").isBetween(1600.0, 1720.0);
        assertThat(bof.scrapKg()).as("лом расплавлен").isLessThan(0.005 * 2_000);
        assertThat(bof.siliconFrac()).isLessThan(1e-4);
        assertThat(bof.phosphorusFrac()).isLessThan(0.2 * 0.001 * 0.8);
        assertThat(bof.manganeseFrac() * bof.metalKg() / (24 + 10)).as("остаточный Mn").isBetween(0.3, 0.5);
        double steelT = bof.metalKg() / 1000;
        assertThat(bof.o2BlownNm3() / steelT).as("O₂, нм³/т").isBetween(45.0, 65.0);
        assertThat(bof.metalKg()).as("выход").isGreaterThan(0.9 * 10_000);
        assertThat(bof.slagBasicity()).isGreaterThan(3);
        assertThat(coFrac.get(0)).as("CO в отходящем газе").isGreaterThan(0.8);

        Converter lessScrap = new Converter(CFG);
        lessScrap.charge(hotMetal(9_000, 0.045, 0.005, 0.003, 0.001), 1_000, 500);
        while (lessScrap.carbonFrac() > 0.0005) lessScrap.stepSlow(5, 0.5, 1.0);
        assertThat(lessScrap.tempK() - bof.tempK()).as("меньше лома — горячее").isGreaterThan(100);
    }

    @Test
    void overblowBurnsIronIntoSlag() {
        Converter bof = new Converter(CFG);
        List<String> log = new ArrayList<>();
        bof.setEventSink(new SteelmakingEvents() {
            @Override
            public void overblown() {
                log.add("overblown");
            }
        });
        bof.charge(hotMetal(10_000, 0.045, 0.005, 0.003, 0.001), 1_500, 500);
        blowTo(bof, 0.0005, 5, 1.0);
        double metal = bof.metalKg(), feo = bof.slagFeOFrac();
        for (int k = 0; k < 24; k++) bof.stepSlow(5, 0.5, 1.0);
        assertThat(log).contains("overblown");
        assertThat(1 - bof.metalKg() / metal).as("потеря выхода за 2 мин передува").isBetween(0.01, 0.035);
        assertThat(bof.slagFeOFrac()).isGreaterThan(feo);
    }

    @Test
    void highSiliconAndLowLanceSlop() {
        Converter bof = new Converter(CFG);
        List<Double> slag = new ArrayList<>();
        bof.setEventSink(new SteelmakingEvents() {
            @Override
            public void slopping(double slagKg, double metalKg) {
                slag.add(metalKg);
            }
        });
        bof.charge(hotMetal(10_000, 0.045, 0.012, 0.003, 0.001), 0, 500);
        for (int k = 0; k < 60; k++) bof.stepSlow(5, 0.5, 0.6);
        assertThat(slag).hasSize(1);
        assertThat(slag.get(0)).isCloseTo(100, within(5.0));

        Converter soft = new Converter(CFG);
        soft.setEventSink(new SteelmakingEvents() {
            @Override
            public void slopping(double slagKg, double metalKg) {
                slag.add(metalKg);
            }
        });
        soft.charge(hotMetal(10_000, 0.045, 0.012, 0.003, 0.001), 0, 500);
        for (int k = 0; k < 60; k++) soft.stepSlow(5, 0.5, 1.2);
        assertThat(slag).hasSize(1);
    }

    @Test
    void tappedSteelIsWildUntilDeoxidized() {
        Converter bof = new Converter(CFG);
        bof.charge(hotMetal(10_000, 0.045, 0.005, 0.003, 0.001), 2_000, 500);
        blowTo(bof, 0.0005, 5, 1.0);
        bof.stepSlow(5, 0, 3);
        SteelMelt steel = bof.tap();
        assertThat(bof.phase()).isEqualTo(ConverterPhase.EMPTY);
        assertThat(steel.frac(AlloyElement.O)).as("[C][O] = 0,0025 %²").isCloseTo(0.0005, within(0.0001));
        assertThat(steel.killed()).isFalse();
        assertThat(IngotMold.cast(steel, true).porous()).isTrue();

        SteelMelt fe = LadleStation.addAlloy(steel, Ferroalloy.FERROMANGANESE, 100);
        assertThat(fe.frac(AlloyElement.MN) - steel.frac(AlloyElement.MN) * steel.massKg() / fe.massKg())
                .isCloseTo(100 * 0.75 * 0.90 / fe.massKg(), within(1e-5));
        assertThat(fe.tempK()).isLessThan(steel.tempK());

        SteelMelt si = LadleStation.addAlloy(fe, Ferroalloy.FERROSILICON, 40);
        assertThat(si.frac(AlloyElement.O)).isCloseTo(LadleStation.O_EQ_SI, within(2e-6));
        assertThat(si.killed()).isTrue();
        SteelMelt al = LadleStation.addAlloy(si, Ferroalloy.ALUMINIUM, 10);
        assertThat(al.frac(AlloyElement.O)).isCloseTo(LadleStation.O_EQ_AL, within(1e-6));
        assertThat(al.frac(AlloyElement.AL)).isPositive();

        SteelIngot ingot = IngotMold.cast(al, true);
        assertThat(ingot.porous()).isFalse();
        assertThat(ingot.cropKg() / (ingot.cropKg() + ingot.massKg())).isCloseTo(0.12, within(1e-9));
        assertThat(IngotMold.cast(al, false).cropKg() / al.massKg()).isCloseTo(0.17, within(1e-9));
        assertThat(IngotMold.cast(al.withTemp(al.liquidusK() + 10), true).laps()).isTrue();
        assertThat(LadleStation.argonStir(al, 600).tempK()).isCloseTo(al.tempK() - 15, within(1e-9));
    }

    @Test
    void hotMetalFromBlastFurnaceTap() {
        TapResult tap = new TapResult(30_000, 9_000, 1_773, 0.045, 0.006, 0.0003, 1.2, false);
        SteelMelt hm = SteelMelt.hotMetal(tap, 10_000);
        assertThat(hm.frac(AlloyElement.C)).isEqualTo(0.045);
        assertThat(hm.frac(AlloyElement.MN)).isEqualTo(BlastFurnace.HM_MN);
        assertThat(hm.liquidusK()).isCloseTo(SteelMelt.EUTECTIC_K, within(30.0));
        assertThat(hm.chemPct().get("C")).isCloseTo(4.5f, within(1e-4f));
    }

    @Test
    void ladleMustBeDryAndHot() {
        LadleState fresh = LadleState.fresh();
        assertThat(fresh.ready()).isFalse();
        assertThat(fresh.waterContactKg(500)).isEqualTo(500);
        LadleState hot = fresh.preheated(1373.15);
        assertThat(hot.ready()).isTrue();
        assertThat(hot.waterContactKg(500)).isZero();
        assertThat(hot.pourChillK()).isEqualTo(LadleState.CHILL_BASE_K);
        assertThat(fresh.preheated(773.15).pourChillK()).isCloseTo(25 + 0.08 * 600, within(1e-9));
        LadleState worn = hot;
        for (int k = 0; k < 100; k++) worn = worn.afterHeat(1900);
        assertThat(worn.ready()).isFalse();
    }

    @Test
    void t8_19_moltenSteelIntoWater() {
        double e = MoltenWater.mechanicalEnergyJ(10, 820, 1900, 0.005);
        assertThat(e).isCloseTo(0.005 * 10 * 820 * (1900 - 373.15), within(0.01 * 62.6e3));
        assertThat(e / 1e3).isCloseTo(62.6, within(0.626));
        assertThat(MoltenWater.steelEnergyJ(10, 1900, CFG)).isEqualTo(e);
        DamageRequest r = new DamageRequest(7, SimId.NONE, null, DamageKind.BLAST, new GridPos(0, 64, 0), e,
                MoltenWater.radiusM(e), MoltenWater.PRIORITY);
        assertThat(r.kind()).isEqualTo(DamageKind.BLAST);
        assertThat(r.priority()).isEqualTo(1);
        assertThat(r.maxRadiusM()).isPositive();
        assertThat(MoltenWater.mechanicalEnergyJ(10, 820, 350, 0.005)).isZero();
    }
}
