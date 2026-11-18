package dev.denfry.promptcritical.sim.reprocessing;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.enrichment.Criticality;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.uranium.UraniumLedger;
import java.util.EnumMap;
import java.util.Map;

/**
 * Радиохимический завод PUREX на одну линию (BPL гл. 10; Long, 1967; опыт «Маяка», объект Б, 1948–1960-е):
 * <ol>
 *   <li>головные операции — рубка твэлов, растворение в кипящей HNO₃ (UO₂ + 4 HNO₃ → UO₂(NO₃)₂ + 2 NO₂ + 2 H₂O):
 *   ≈ 3 кг HNO₃ на кг ТМ с учётом подкисления питания и промывных растворов; иод отдувается в газоочистку
 *   (95 %), криптон уходит весь;</li>
 *   <li>экстракция и промывка — {@link PurexFlowsheet}, два цикла;</li>
 *   <li>разделение U/Pu — восстановление Pu(IV)→Pu(III) уранитом U(IV) или гидроксиламином: при недостатке
 *   восстановителя Pu(IV) остаётся в органике и уходит с ураном;</li>
 *   <li>Pu — осаждение оксалата Pu(IV) и прокалка в PuO₂ (потери с маточником 0,5 %); U — денитрация в UO₃;</li>
 *   <li>высокоактивные отходы — рафинат первого цикла: все продукты деления, кроме ушедших в газ, — остекловываются в
 *   боросиликатное стекло: 15 % оксидов отходов в стекле, оксиды ≈ 1,2 массы продуктов деления.</li>
 * </ol>
 * Нитрат плутония копится в буферной ёмкости перед осаждением; если осаждение стоит, масса растёт, и выше
 * минимальной критической ({@link Criticality#minCriticalPuKg}) — СЦР в растворе.
 */
public final class PurexPlant {
    public static final double ACID_KG_PER_KG_HM = 3.0, IODINE_TO_OFFGAS = 0.95, IODINE_FILTER_DF = 1000, OXALATE_LOSS = 0.005;
    public static final double WASTE_LOADING = 0.15, OXIDE_PER_FP = 1.2;
    /** Производительность по ТМ, кг/с [BALANCE]. */
    public static final double CAPACITY_KG_S = 0.1;
    /** Пределы ручек: кислотность питания, отношение восстановителя к Pu. */
    public static final double MIN_REDUCTANT = 0, MAX_REDUCTANT = 3;

    private final UraniumLedger ledger = new UraniumLedger();
    private double acidity = 3, reductant = 1.5;
    /** Буферная ёмкость нитрата Pu: масса Pu, кг, и доля Pu-240. */
    private double puTankKg, puTank240;

    public UraniumLedger ledger() {
        return ledger;
    }

    public double acidity() {
        return acidity;
    }

    public void setAcidity(double m) {
        acidity = Math.clamp(m, PurexFlowsheet.MIN_ACID, PurexFlowsheet.MAX_ACID);
    }

    public double reductant() {
        return reductant;
    }

    public void setReductant(double r) {
        reductant = Math.clamp(r, MIN_REDUCTANT, MAX_REDUCTANT);
    }

    public double puTankKg() {
        return puTankKg;
    }

    /** Ёмкость перед осаждением подошла к безопасной массе. */
    public boolean puTankAboveSafe() {
        return puTankKg > Criticality.safePuKg(puTank240);
    }

    /**
     * Итог переработки порции.
     *
     * @param uProductKg   регенерат урана, кг U
     * @param u235, u236   изотопия регенерата
     * @param puToTankKg   плутоний в буферную ёмкость, кг
     * @param puInUKg      плутоний, ушедший с ураном (недовосстановление), кг
     * @param hlw          активность высокоактивных отходов (в стекло)
     * @param glassKg      масса стекла
     * @param kr85Bq       криптон в трубу
     * @param i131ReleasedBq иод в трубу (после фильтра)
     * @param uProductDf   коэффициент очистки урана от γ-активных продуктов деления
     */
    public record Batch(double hmKg, double uProductKg, double u235, double u236, double puToTankKg, double pu239, double pu240,
            double pu241, double pu242, double uLossKg, double puLossKg, double puInUKg, ActivityInventory hlw, double glassKg,
            double kr85Bq, double i131ReleasedBq, double acidKg, double uProductDf) {
    }

    /** Доля U, ушедшая в рафинат: неизвлечённая плюс механические потери 0,1 %. */
    public double uLossFraction() {
        return Math.min(1, PurexFlowsheet.unextracted(PurexFlowsheet.Species.U6.d(acidity) * PurexFlowsheet.OA_EXTRACT,
                PurexFlowsheet.EXTRACT_STAGES) + 0.001);
    }

    /** Доля Pu, ушедшая в рафинат. */
    public double puLossFraction() {
        return Math.min(1, PurexFlowsheet.unextracted(PurexFlowsheet.Species.PU4.d(acidity) * PurexFlowsheet.OA_EXTRACT,
                PurexFlowsheet.EXTRACT_STAGES) + 0.001);
    }

    /** Доля Pu, не восстановленная до Pu(III) и ушедшая с ураном. */
    public double puUnreduced() {
        return Math.max(1e-6, 1 - Math.min(1, reductant));
    }

    /**
     * Переработать порцию топлива.
     *
     * @param hmKg         исходный тяжёлый металл, кг
     * @param comp         состав по выгоранию
     * @param u234         доля U-234 в уране
     * @param activity     активность топлива на момент переработки
     * @param iodineFilter угольный фильтр заряжен
     */
    public Batch process(double hmKg, ActinideBuildup.Composition comp, double u234, ActivityInventory activity, boolean iodineFilter) {
        double uKg = hmKg * comp.uPerHm(), puKg = hmKg * comp.puPerHm();
        ledger.in(uKg);
        ledger.puIn(puKg);
        double uLoss = uKg * uLossFraction(), puLoss = puKg * puLossFraction();
        double puExtracted = puKg - puLoss;
        double puInU = puExtracted * puUnreduced();
        double uProduct = uKg - uLoss;
        ledger.out(uProduct);
        ledger.loss(uLoss);
        ledger.puLoss(puLoss);
        // Pu с ураном считаем продуктом (он в регенерате, не потерян), но не в буфер
        ledger.puOut(puInU);
        double puTank = puExtracted - puInU;
        double pu240 = comp.pu240();
        puTank240 = puTankKg + puTank > 0 ? (puTank240 * puTankKg + pu240 * puTank) / (puTankKg + puTank) : pu240;
        puTankKg += puTank;
        // газ и отходы
        EnumMap<Emitter, Double> hlw = new EnumMap<>(Emitter.class);
        double kr = 0, iRel = 0;
        for (Map.Entry<Emitter, Double> e : activity.activities().entrySet()) {
            double a = e.getValue();
            switch (e.getKey()) {
                case KR85 -> kr += a;
                case I131 -> {
                    double gas = a * IODINE_TO_OFFGAS;
                    iRel += iodineFilter ? gas / IODINE_FILTER_DF : gas;
                    hlw.merge(Emitter.I131, a - gas, Double::sum);
                }
                default -> hlw.merge(e.getKey(), a, Double::sum);
            }
        }
        double glass = hmKg * comp.fpPerHm() * OXIDE_PER_FP / WASTE_LOADING;
        double df = Math.min(PurexFlowsheet.decontamination(PurexFlowsheet.Species.ZR, acidity),
                Math.min(PurexFlowsheet.decontamination(PurexFlowsheet.Species.RU, acidity),
                        PurexFlowsheet.decontamination(PurexFlowsheet.Species.CS_SR, acidity)));
        return new Batch(hmKg, uProduct, comp.u235(), comp.u236(), puTank, comp.pu239(), comp.pu240(), comp.pu241(), comp.pu242(), uLoss,
                puLoss, puInU, new ActivityInventory(hlw, glass, 2.7), glass, kr, iRel, hmKg * ACID_KG_PER_KG_HM, df);
    }

    /**
     * Осадить оксалат из буферной ёмкости и прокалить в PuO₂.
     *
     * @return плутоний в PuO₂, кг
     */
    public double precipitate(double maxKg) {
        double take = Math.clamp(maxKg, 0, puTankKg);
        puTankKg -= take;
        double pu = take * (1 - OXALATE_LOSS);
        ledger.puOut(pu);
        ledger.puLoss(take - pu);
        return pu;
    }

    /** Делений во вспышке, если буфер Pu сверх критической массы (0 — нет СЦР). */
    public double criticalityFissions() {
        return Criticality.excursionFissionsOver(puTankKg, Criticality.minCriticalPuKg(puTank240));
    }

    /** После СЦР раствор разбавляют и сливают в отходы: плутоний буфера — потеря. */
    public void dumpPuTank() {
        ledger.puLoss(puTankKg);
        puTankKg = 0;
    }

    public void save(StateWriter out) {
        out.putDouble("h", acidity);
        out.putDouble("red", reductant);
        out.putDouble("pu_tank", puTankKg);
        out.putDouble("pu_tank240", puTank240);
        ledger.save(out);
    }

    public void load(StateReader in) {
        setAcidity(in.optDouble("h", 3));
        setReductant(in.optDouble("red", 1.5));
        puTankKg = in.optDouble("pu_tank", 0);
        puTank240 = in.optDouble("pu_tank240", 0);
        ledger.load(in);
    }
}
