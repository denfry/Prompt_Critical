package dev.denfry.promptcritical.sim.uranium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.Test;

/**
 * Передел урана от руды до UF₆ против литературы: Benedict, Pigford, Levi, «Nuclear Chemical Engineering» (1981,
 * гл. 5); Скороваров и др. (1979); Merritt (1971); Амелин (контактный процесс); Rudge (фтор).
 */
class UraniumFrontEndTest {
    @Test
    void contactProcessEquilibriumAndCatalystIgnition() {
        // равновесие для 8 % SO₂ / 11 % O₂: ≈ 98–99 % при 430 °C, ≈ 75–85 % при 580 °C (Амелин, рис. «x_р–t»)
        assertThat(ContactAcidPlant.equilibrium(703.15)).isBetween(0.975, 0.995);
        assertThat(ContactAcidPlant.equilibrium(853.15)).isBetween(0.70, 0.88);
        assertThat(ContactAcidPlant.conversion(663.15)).isLessThan(ContactAcidPlant.conversion(703.15) * 0.5);
        ContactAcidPlant p = new ContactAcidPlant();
        p.step(0.032); // 1 моль S/с
        // 1 моль S → 0,98·x моль H₂SO₄; 98 %-я кислота
        assertThat(p.acidKgS()).isCloseTo(0.98 * p.conversion() * 0.999 * 0.098079 / 0.98, within(1e-3));
        assertThat(p.so2StackKgS()).isPositive();
        // ≈ 3 т кислоты на тонну серы (стехиометрия 3,06 при полном превращении)
        assertThat(p.acidKgS() / p.sulfurKgS()).isBetween(2.9, 3.1);
    }

    @Test
    void hfKilnStoichiometry() {
        HfKiln k = new HfKiln();
        k.step(0.04);
        // 0,512 кг HF на кг CaF₂ по стехиометрии, 97 % выход
        assertThat(k.hfKgS() / k.caf2KgS()).isCloseTo(2 * 20.006 / 78.075 * 0.97, within(1e-9));
        assertThat(HfKiln.acidPerCaf2()).isCloseTo(1.256 * 1.07, within(0.01));
        assertThat(HfKiln.hfYield(400)).isLessThan(0.3);
    }

    @Test
    void leachingNeedsAnOxidantForUraninite() {
        // руда 0,4 % U порциями по 50 кг каждые 0,25 ч → 200 кг/ч, τ = 2,5 ч на бак, 10 ч на каскад
        UraniumLeach with = new UraniumLeach(), without = new UraniumLeach();
        for (int i = 0; i < 2000; i++) {
            with.feed(50, 0.004);
            with.step(0.25, 1, 1);
            without.feed(50, 0.004);
            without.step(0.25, 1, 0);
        }
        assertThat(with.ledger().recovery()).isBetween(0.88, 0.95);
        assertThat(without.ledger().recovery()).isBetween(0.25, 0.40);
        // баланс: вход = продукт + потери + запас
        UraniumLedger l = with.ledger();
        assertThat(l.inKg()).isCloseTo(l.outKg() + l.lossKg() + with.solidUKg(), within(1e-9));
        // при E = 40 кДж/моль 70 °C против 30 °C ускоряют растворение в 6,4 раза
        assertThat(UraniumLeach.rate(343.15) / UraniumLeach.rate(303.15)).isBetween(5.0, 8.0);
    }

    @Test
    void ionExchangeCarouselLoadsElutesAndBreaksThrough() {
        IonExchangeColumn c = new IonExchangeColumn();
        c.setResinL(400);
        assertThat(c.capacityKg()).isCloseTo(20, within(1e-9));
        assertThat(c.bedCapacityKg()).isCloseTo(10, within(1e-9));
        // первая колонна до 90 %: до 85 % почти без проскока, дальше — около процента
        double leak = 0;
        while (c.activeBed() == 0) leak += c.load(0.1);
        assertThat(c.loading(0)).isGreaterThanOrEqualTo(IonExchangeColumn.ELUTE_AT);
        assertThat(c.mode(0)).isEqualTo(IonExchangeColumn.Mode.ELUTE);
        assertThat(leak / c.ledger().inKg()).isBetween(0.002, 0.02);
        // раствор пошёл на вторую, первая элюируется
        assertThat(c.sorbing()).isTrue();
        double eluted = 0;
        while (c.mode(0) == IonExchangeColumn.Mode.ELUTE) eluted += c.elute(c.elutable(1));
        assertThat(eluted).isCloseTo(c.ledger().outKg(), within(1e-9));
        assertThat(c.loading(0)).isLessThanOrEqualTo(IonExchangeColumn.LOAD_AT);
        // если обе заняты, раствор идёт мимо смолы
        IonExchangeColumn busy = new IonExchangeColumn();
        busy.setResinL(400);
        while (busy.activeBed() == 0) busy.load(0.5);
        while (busy.mode(1) == IonExchangeColumn.Mode.LOAD) busy.load(0.5);
        assertThat(busy.load(1)).isCloseTo(1, within(1e-12));
        // элюат 10 г/л: концентрирование в 10 раз против продуктивного раствора 1 г/л
        assertThat(1 / IonExchangeColumn.ELUATE_L_PER_KG * 1000).isCloseTo(10, within(1e-9));
    }

    @Test
    void aduPrecipitationNeedsAmmonia() {
        AduPrecipitation p = new AduPrecipitation();
        assertThat(p.step(10, 10 * AduPrecipitation.NH3_KG_PER_KG_U)).isCloseTo(9.96, within(1e-9));
        assertThat(AduPrecipitation.recovery(0.7)).isLessThan(0.4);
        // ДУА: 1,31 кг на кг U
        assertThat(AduPrecipitation.ADU_KG_PER_KG_U).isCloseTo(1.311, within(0.002));
    }

    @Test
    void conversionReagentsMatchStoichiometry() {
        // HF на кг U: 0,336 (4·20/238); F₂: 0,160; H₂: 0,0085 (Benedict–Pigford–Levi, табл. 5.x)
        assertThat(DryConversion.Step.HYDROFLUORINATE.reagentKgPerKgU).isCloseTo(0.336, within(0.001));
        assertThat(DryConversion.Step.FLUORINATE.reagentKgPerKgU).isCloseTo(0.1596, within(0.001));
        assertThat(DryConversion.Step.REDUCE.reagentKgPerKgU).isCloseTo(0.00847, within(0.0001));
        // UF₆ на кг U и баллон 5A (24,95 кг UF₆)
        assertThat(UraniumLedger.M_UF6 / UraniumLedger.M_U).isCloseTo(1.479, within(0.001));
        DryConversion hot = new DryConversion(DryConversion.Step.REDUCE);
        hot.setTempK(1100);
        assertThat(DryConversion.efficiency(DryConversion.Step.REDUCE, hot.tempK())).isLessThan(0.1);
        DryConversion d = new DryConversion(DryConversion.Step.FLUORINATE);
        double cap = d.capacityKg(1, 1, 0);
        assertThat(cap).isCloseTo(Math.min(DryConversion.Step.FLUORINATE.capacityKgS, 1 / (0.1596 * 1.15)), within(0.01));
    }

    @Test
    void fluorineCellVoltageEnergyAndAnodeEffect() {
        double u = FluorineCellRoom.cellVoltage(1200);
        assertThat(u).isBetween(8.5, 10.5);
        // ≈ 13–15 кВт·ч постоянного тока на кг F₂
        double kwh = FluorineCellRoom.specificEnergyJPerKg(u, 0.93) / 3.6e6;
        assertThat(kwh).isBetween(12.5, 16.0);
        FluorineCellRoom c = new FluorineCellRoom();
        c.setOn(true);
        assertThat(c.hfFraction()).isCloseTo(0.408, within(0.001));
        c.step(1, 1e9);
        assertThat(c.anodeEffect()).isFalse();
        assertThat(c.hfUseKgS() / c.f2KgS()).isCloseTo(2 * 20.006 / 37.997, within(1e-9));
        double f2 = c.f2KgS();
        // без подпитки HF расплав беднеет до анодного эффекта
        int s = 0;
        while (!c.anodeEffect() && s++ < 1_000_000) c.step(10, 1e9);
        assertThat(c.hfFraction()).isLessThan(FluorineCellRoom.HF_ANODE_EFFECT);
        assertThat(c.voltageV()).isGreaterThan(u + 15);
        assertThat(c.f2KgS()).isLessThan(f2 * 0.1);
        c.addHf(2000);
        c.step(1, 1e9);
        assertThat(c.anodeEffect()).isFalse();
    }

    @Test
    void endToEndUraniumBalanceFromOreToUf6() {
        // руда 0,4 % U → выщелачивание → сорбция → ДУА → UO₃ → UO₂ → UF₄ → UF₆; извлечение по сквозному балансу
        UraniumLeach leach = new UraniumLeach();
        IonExchangeColumn ix = new IonExchangeColumn();
        ix.setResinL(400);
        AduPrecipitation adu = new AduPrecipitation();
        DryConversion[] dry = {new DryConversion(DryConversion.Step.CALCINE), new DryConversion(DryConversion.Step.REDUCE),
                new DryConversion(DryConversion.Step.HYDROFLUORINATE), new DryConversion(DryConversion.Step.FLUORINATE)};
        double uf6U = 0;
        for (int i = 0; i < 4000; i++) {
            leach.feed(50, 0.004);
            double pls = leach.step(0.25, 1, 1);
            ix.load(pls);
            double e = ix.elute(ix.elutable(0.25));
            double u = adu.step(e, e * AduPrecipitation.NH3_KG_PER_KG_U);
            for (DryConversion d : dry) u = d.process(u, 1);
            uf6U += u;
        }
        double ore = leach.ledger().inKg();
        double overall = uf6U / ore;
        // сквозное извлечение от руды в UF₆ — 85–92 % (Скороваров: гидрометаллургия 88–93 %, конверсия 99,4 %)
        assertThat(overall).isBetween(0.82, 0.93);
        // баланс сходится: всё, что вошло, — в продукте, в потерях или в запасе аппаратов
        double losses = leach.ledger().lossKg() + ix.ledger().lossKg() + adu.ledger().lossKg();
        double holdup = leach.solidUKg() + ix.loadedKg();
        for (DryConversion d : dry) losses += d.ledger().lossKg();
        assertThat(uf6U + losses + holdup).isCloseTo(ore, within(1e-6));
        // конверсия теряет меньше 1 %
        double conv = 1;
        for (DryConversion d : dry) conv *= d.ledger().recovery();
        assertThat(conv).isBetween(0.99, 0.996);
    }
}
