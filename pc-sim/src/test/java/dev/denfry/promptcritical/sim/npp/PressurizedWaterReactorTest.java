package dev.denfry.promptcritical.sim.npp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.protection.TripReason;
import dev.denfry.promptcritical.sim.reactor.pwr.CoolantPump;
import dev.denfry.promptcritical.sim.reactor.pwr.SecondarySide;
import org.junit.jupiter.api.Test;

/** Стартовый блок T2a (ВВЭР, 100 МВт(т)) против литературы и GDD 01 §1.7–1.14. */
class PressurizedWaterReactorTest {
    static final double DT = PressurizedWaterReactor.DT;

    /** Блок со своим вторым контуром: ПГ кипит при 6,4 МПа, клапан турбины — нагрузка. */
    static final class Unit {
        final PressurizedWaterReactor r = Pwrs.nominal();
        final SecondarySide sg = new SecondarySide(Pwrs.W, PressurizedWaterReactor.NOMINAL_W, 20_000);
        double maxP, minP = 1e9, maxLevel, minLevel = 1, minDnbr = 1e9;

        void run(double seconds) {
            for (int i = 0; i < Math.round(seconds / DT); i++) {
                r.setSecondary(sg.temperatureK(), sg.level(), 2);
                r.step(0);
                sg.step(DT, r.steamGeneratorW());
                maxP = Math.max(maxP, r.pressure());
                minP = Math.min(minP, r.pressure());
                maxLevel = Math.max(maxLevel, r.pressurizerLevel());
                minLevel = Math.min(minLevel, r.pressurizerLevel());
                minDnbr = Math.min(minDnbr, r.mdnbr());
            }
        }

        void resetExtremes() {
            maxP = maxLevel = 0;
            minP = minDnbr = 1e9;
            minLevel = 1;
        }
    }

    @Test
    void holdsNominalWithMargins() {
        Unit u = new Unit();
        u.run(120);
        PressurizedWaterReactor r = u.r;
        assertEquals(1.0, r.power(), 0.02);
        assertEquals(PressurizedWaterReactor.P_NOMINAL, r.pressure(), 0.05e6);
        assertEquals(PressurizedWaterReactor.PZR_LEVEL, r.pressurizerLevel(), 0.01);
        assertEquals(320, r.hotLegK() - 273.15, 1.5);
        assertEquals(290, r.coldLegK() - 273.15, 1.5);
        // 01 §1.4.4: проектный предел W-3 — 1,30; вычислитель защиты консервативнее, но не срабатывает на номинале
        assertTrue(r.mdnbr() > 1.3, "MDNBR " + r.mdnbr());
        assertTrue(r.calculatedDnbr() > 1.45 && r.calculatedDnbr() < r.mdnbr(), "calc DNBR " + r.calculatedDnbr());
        assertFalse(r.protection().isTripped(), "no trip: " + r.protection().firstOut());
        // ≈ 41 кВт/кг U — нормальная удельная мощность PWR (R5)
        double uKg = 0;
        for (int i = 0; i < PressurizedWaterReactor.POSITIONS; i++) uKg += r.fuel(i).uraniumKg();
        assertEquals(41.7, PressurizedWaterReactor.NOMINAL_W / uKg / 1e3, 3.0);
    }

    /** 01 §1.7.5: Доплер ВВЭР −2…−4 pcm/К; бор −7…−10 pcm/ppm — на ВВЭР ≈ −1,5·10⁻² Δk/k на г/кг H₃BO₃. */
    @Test
    void reactivityCoefficientsMatchVver() {
        PressurizedWaterReactor r = Pwrs.nominal();
        double doppler = r.dopplerCoefficient() * 1e5;
        assertTrue(doppler > -4 && doppler < -2, "Doppler " + doppler);
        double boron = r.boronCoefficient() * 1e5;
        assertTrue(boron > -10 && boron < -7, "boron pcm/ppm " + boron);
        double perGkg = r.boronCoefficient() * PressurizedWaterReactor.PPM_B_PER_G_KG;
        assertEquals(-1.5e-2, perGkg, 0.25e-2);
        // свежая зона на мощности держится бором 5–10 г/кг (первая загрузка ВВЭР)
        assertTrue(r.boronGkg() > 5 && r.boronGkg() < 10, "critical boron " + r.boronGkg());
        // βeff и Λ — строка T2a таблицы 01 §1.7.5
        assertEquals(0.0068, r.betaEff(), 0.0008);
        assertTrue(r.generationTime() > 1e-5 && r.generationTime() < 3e-5, "Λ " + r.generationTime());
    }

    /**
     * Температурный коэффициент теплоносителя отрицательный. У малой высокой зоны (R = 0,34 м при H = 2,42 м,
     * M²B² ≈ 0,26) он втрое сильнее, чем у ПР-300: плотность воды меняет длину миграции, а с ней утечку, — это
     * больше вклада ячейки (−10…−60 pcm/К у крупных зон, 01 §1.7.5).
     */
    @Test
    void moderatorAndPowerCoefficientsAreNegative() {
        PressurizedWaterReactor r = Pwrs.nominal();
        double mtc = r.moderatorTemperatureCoefficient() * 1e5;
        assertTrue(mtc < -10 && mtc > -200, "MTC " + mtc);
        double rho = r.evaluateReactivity();
        r.setThermalState(r.fuelK() + 10, PressurizedWaterReactor.OUTLET_K + 1, PressurizedWaterReactor.INLET_K + 1);
        assertTrue(r.evaluateReactivity() < rho, "hotter core is less reactive");
    }

    /**
     * Сброс нагрузки 50 % без БРУ: второй контур греется, теплоноситель расширяется в КД — давление и уровень растут,
     * впрыск давление сбивает, ИПУ (17,0 МПа) не открывается; мощность уходит вниз за счёт отрицательного MTC
     * (Kerlin, Upadhyaya, «Dynamics and Control of Nuclear Reactors», 2019, гл. 9).
     */
    @Test
    void loadRejectionRaisesPressureAndLevel() {
        Unit u = new Unit();
        u.run(20);
        u.resetExtremes();
        u.sg.setValve(0.5);
        u.run(300);
        PressurizedWaterReactor r = u.r;
        assertTrue(u.maxP > PressurizedWaterReactor.P_NOMINAL + 0.05e6, "pressure rose: " + u.maxP);
        assertTrue(u.maxP < PressurizedWaterReactor.RELIEF_PA, "no relief lift: " + u.maxP);
        assertTrue(u.maxLevel > PressurizedWaterReactor.PZR_LEVEL + 0.02, "insurge: " + u.maxLevel);
        assertTrue(r.power() < 0.7 && r.power() > 0.4, "power follows steam: " + r.power());
        assertEquals(0, r.pressurizer().relievedKg(), 1e-6);
        assertFalse(r.protection().isTripped(), "no trip: " + r.protection().firstOut());
    }

    /** Наброс нагрузки 10 %: теплоноситель остывает и сжимается — давление и уровень КД падают, мощность растёт. */
    @Test
    void loadIncreaseDropsPressureAndLevel() {
        Unit u = new Unit();
        u.run(20);
        u.resetExtremes();
        u.sg.setValve(1.1);
        u.run(120);
        assertTrue(u.minP < PressurizedWaterReactor.P_NOMINAL - 0.03e6, "pressure fell: " + u.minP);
        assertTrue(u.minLevel < PressurizedWaterReactor.PZR_LEVEL - 0.01, "outsurge: " + u.minLevel);
        assertTrue(u.r.power() > 1.04 && u.r.power() < 1.15, "power " + u.r.power());
        assertFalse(u.r.protection().isTripped(), "no trip: " + u.r.protection().firstOut());
    }

    /** Выбег ГЦН (R2, 01 §1.2.7): J = 300 кг·м², 1500 об/мин, 617 кВт → τ = 12 с, n(t) = 1/(1 + t/τ). */
    @Test
    void pumpCoastsDownOnFlywheel() {
        CoolantPump p = new CoolantPump(CoolantPump.INERTIA_KGM2, 1500, 617e3);
        assertEquals(12.0, p.coastdownTimeConstant(), 0.1);
        p.setSpeed(1);
        double t = 0;
        for (double check : new double[] {6, 12, 24}) {
            while (t < check - 1e-9) {
                p.step(DT, 0);
                t += DT;
            }
            assertEquals(1 / (1 + check / 12), p.speed(), 0.005, "t = " + check);
        }
    }

    /**
     * Потеря обоих ГЦН (01 §1.14.3): АЗ по оборотам или расходу в первые 2 с, MDNBR не уходит ниже проектного предела
     * (в эталоне минимум 3,43 при 3,75 на номинале — падение ≈ 9 %), мощность делений через 5 с — единицы процентов.
     */
    @Test
    void lossOfBothPumpsTripsWithDnbMargin() {
        Unit u = new Unit();
        u.run(10);
        double nominal = u.r.mdnbr();
        u.resetExtremes();
        for (int l = 0; l < PressurizedWaterReactor.LOOPS; l++) u.r.setPumpDrive(l, 0);
        u.run(2.5);
        assertTrue(u.r.protection().isTripped(), "tripped");
        TripReason first = u.r.protection().firstOut();
        assertTrue(first == TripReason.LOSS_OF_DRIVE_POWER || first == TripReason.LOW_COOLANT_FLOW, "first-out " + first);
        u.run(2.5);
        assertTrue(u.r.power() < 0.1, "fission power " + u.r.power());
        assertTrue(u.minDnbr > 1.3 && u.minDnbr > 0.8 * nominal, "MDNBR min " + u.minDnbr + " of " + nominal);
        assertEquals(1 / (1 + 5.0 / 12), u.r.pumpSpeed(0), 0.01);
        assertEquals(0, u.r.failedAssemblies());
    }

    /** АЗ: стержни (01 §1.10.3, 0,95 с на метр) проходят 2,42 м зоны за ≈ 2,3 с; мощность — на «хвост» запаздывающих. */
    @Test
    void scramDropsRodsAndPower() {
        Unit u = new Unit();
        u.run(5);
        u.r.scram();
        u.run(0.2 + 2.4);
        assertTrue(u.r.az() > 0.999 && u.r.rg() > 0.999, "rods in: " + u.r.az() + " " + u.r.rg());
        assertTrue(u.r.power() < 0.12, "power " + u.r.power());
        assertEquals(TripReason.MANUAL, u.r.protection().firstOut());
        // сброс АЗ запрещён, пока причина держится? ручная ушла — но выдержка 10 с
        assertFalse(u.r.resetTrip());
        u.run(11);
        assertTrue(u.r.resetTrip(), "reset after delay");
    }

    /**
     * Малая течь первого контура ⌀ 50 мм в холодной нитке: подпитка не успевает, давление падает до уставки
     * 13,7 МПа — АЗ, затем сигнал САОЗ (12,5 МПа) и подача борного раствора; зона охлаждается, ТВС целы.
     */
    @Test
    void smallBreakTripsAndInjects() {
        Unit u = new Unit();
        u.run(10);
        u.r.setLeakArea(Math.PI / 4 * 0.015 * 0.015);
        double boron0 = u.r.boronGkg();
        boolean sawInjection = false;
        for (int i = 0; i < 60; i++) {
            u.run(10);
            sawInjection |= u.r.safetyInjectionKgS() > 0;
        }
        PressurizedWaterReactor r = u.r;
        assertTrue(r.protection().isTripped(), "tripped");
        assertEquals(TripReason.LOW_PRIMARY_PRESSURE, r.protection().firstOut());
        assertTrue(r.leakKgS() > 0, "leak continues");
        assertTrue(sawInjection, "SI injected");
        assertTrue(r.boronGkg() > boron0, "boron rose: " + r.boronGkg());
        assertTrue(r.power() < 1e-3, "subcritical");
        assertEquals(0, r.failedAssemblies(), "fuel intact");
    }

    /** Разбавление бора (01 §1.10.5): dC/dt = −(ṁ/M)·C — медленный, но неотвратимый ввод реактивности. */
    @Test
    void dilutionAddsReactivityAtPredictedRate() {
        PressurizedWaterReactor r = Pwrs.nominal();
        double c0 = r.boronPpm(), m = r.primaryKg();
        r.setWaterExchange(5, 0);
        Pwrs.run(r, 10);
        double rate = (r.boronPpm() - c0) / 10;
        assertEquals(-5 / m * c0, rate, Math.abs(0.15 * 5 / m * c0));
        assertTrue(r.power() > 1.0, "power creeps up: " + r.power());
    }

    /**
     * Последовательность game test {@code losingPumpDriveAtPowerTrips}: после загрузки ТВС размножение источника поднимает
     * счёт с коротким положительным периодом (≈ 3 с) — канал периода голосует, и выдержка сброса АЗ (10 с) идёт заново
     * от момента, когда период ушёл. Сброс через 12 с после загрузки отказан, после выдержки — разрешён.
     */
    @Test
    void fuelLoadingPeriodRestartsTheResetDelay() {
        PressurizedWaterReactor r = new PressurizedWaterReactor();
        r.setSecondary(Double.NaN, Double.NaN, 0);
        Pwrs.run(r, 0.1);
        for (int l = 0; l < PressurizedWaterReactor.LOOPS; l++) r.setPumpDrive(l, 1);
        while (r.pumpSpeed(0) <= 0.99) r.step(0);
        assertTrue(r.protection().isTripped() && r.protection().activeReasons().isEmpty(), "tripped, causes gone");
        r.setRods(1, 1);
        double kg = dev.denfry.promptcritical.sim.fuel.FuelDesign.VVER_440.uKgPerAssembly(0.96, 0.044);
        for (int i = 0; i < PressurizedWaterReactor.POSITIONS; i++)
            assertTrue(r.load(i, dev.denfry.promptcritical.sim.fuel.FuelDesign.VVER_440, kg, 0.044, 0.0003));
        double shortest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 40; i++) {
            r.step(0);
            if (r.periodS() > 0) shortest = Math.min(shortest, r.periodS());
        }
        assertTrue(shortest < 10, "loading gives a short positive period: " + shortest);
        assertTrue(r.power() < 1e-6, "still deeply subcritical");
        Pwrs.run(r, 10);
        assertFalse(r.resetTrip(), "12 s after loading the delay is not over");
        Pwrs.run(r, 5);
        assertTrue(r.resetTrip(), "reset once 10 s passed after the period cleared");
        // дальше — как в game test: горячий старт на 50 %, привод ГЦН петли 1 пропал
        r.setThermalState(800, 588, 563);
        r.setRods(0, 0.3);
        double lo = 0, hi = 30;
        for (int i = 0; i < 40; i++) {
            double m = (lo + hi) / 2;
            r.setBoronGkg(m);
            if (r.evaluateReactivity() > 0) lo = m;
            else hi = m;
        }
        r.equilibrate(0.5);
        Pwrs.run(r, 1);
        assertFalse(r.protection().isTripped(), "running at 50 %: " + r.protection().activeReasons());
        r.setPumpDrive(0, 0);
        Pwrs.run(r, 3);
        assertTrue(r.protection().isTripped(), "tripped");
        TripReason first = r.protection().firstOut();
        assertTrue(first == TripReason.LOSS_OF_DRIVE_POWER || first == TripReason.LOW_COOLANT_FLOW, "first-out " + first);
    }

    @Test
    void stateRoundTrips() {
        Unit u = new Unit();
        u.run(5);
        PressurizedWaterReactor a = u.r;
        MutableStateTree tree = new MutableStateTree();
        a.save(new TreeStateWriter(tree));
        PressurizedWaterReactor b = new PressurizedWaterReactor();
        b.load(new TreeStateReader(tree));
        assertEquals(a.power(), b.power(), 1e-6);
        assertEquals(a.pressure(), b.pressure(), 1e3);
        assertEquals(a.pressurizerLevel(), b.pressurizerLevel(), 1e-4);
        assertEquals(a.boronPpm(), b.boronPpm(), 0.01);
        assertEquals(a.pumpSpeed(1), b.pumpSpeed(1), 1e-9);
        assertEquals(a.loadedAssemblies(), b.loadedAssemblies());
        assertFalse(b.protection().isTripped());
        assertNull(b.protection().firstOut());
        // мир загружен на мощности: восстановленная мощность — не короткий период
        b.setSecondary(Pwrs.T_SEC, 0.5, 2);
        for (int l = 0; l < PressurizedWaterReactor.LOOPS; l++) b.setPumpDrive(l, 1);
        Pwrs.run(b, 3);
        assertFalse(b.protection().isTripped(), "no trip after reload: " + b.protection().firstOut());
        assertEquals(a.power(), b.power(), 0.03);
    }
}
