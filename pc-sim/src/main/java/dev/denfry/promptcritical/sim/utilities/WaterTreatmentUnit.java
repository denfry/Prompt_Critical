package dev.denfry.promptcritical.sim.utilities;

import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Двойник блока водоподготовки (08 §8.12) — одна ступень {@link WaterTreatmentTrain}: осветлитель с известкованием
 * ({@code water_clarifier}), песчаный фильтр ({@code sand_filter}) или Na-катионитная колонна
 * ({@code ion_exchange_column}). Вода идёт через блок в реальном времени (мир переливает жидкость каждый тик и
 * сообщает объём {@link #pass}), загрузка смолы и фильтра — по прошедшему объёму (баланс масс), качество — по
 * номиналу входного сорта. Шаг SLOW.
 */
public final class WaterTreatmentUnit implements UtilitiesUnit {
    public enum Stage { CLARIFIER, SAND_FILTER, ION_EXCHANGER }

    /** Объём смолы колонны, м³: 500 г-экв при 1,0 экв/л [BALANCE]. */
    public static final double RESIN_M3 = 0.5;
    /** Грязеёмкость песчаного фильтра, кг взвеси [BALANCE]. */
    public static final double FILTER_CAPACITY_KG = 10;
    /** Сорт на выходе колонны после проскока: жёсткость выше этой доли входной — вода «не умягчена». */
    public static final double SOFT_LIMIT = 0.5;

    private final String deviceType;
    private final Stage stage;
    private final WaterTreatmentTrain train;
    private WaterGrade inlet = WaterGrade.RAW;
    private double pendingM3, limeDueKg;
    private boolean lime;

    public WaterTreatmentUnit(String deviceType, Stage stage, UtilitiesConfig cfg) {
        this.deviceType = deviceType;
        this.stage = stage;
        this.train = new WaterTreatmentTrain(cfg, UtilitiesEvents.NONE);
        switch (stage) {
            case CLARIFIER -> train.setClarifier(true, false);
            case SAND_FILTER -> train.setSandFilter(FILTER_CAPACITY_KG);
            case ION_EXCHANGER -> {
                train.addColumn(ResinType.SAC_NA, RESIN_M3);
                train.setHydrogenFraction(0);
            }
        }
        train.stepSlow(0);
    }

    @Override
    public String deviceType() {
        return deviceType;
    }

    public Stage stage() {
        return stage;
    }

    public WaterTreatmentTrain train() {
        return train;
    }

    /** Сорт, который блок принимает: осветлитель — сырую, фильтр — осветлённую, колонна — сырую или отфильтрованную. */
    public boolean accepts(WaterGrade g) {
        return switch (stage) {
            case CLARIFIER -> g == WaterGrade.RAW;
            case SAND_FILTER -> g == WaterGrade.CLARIFIED;
            case ION_EXCHANGER -> g == WaterGrade.RAW || g == WaterGrade.FILTERED;
        };
    }

    /** Есть известь ({@code burnt_lime}) на известкование; без неё осветлитель только отстаивает. */
    public void setLime(boolean available) {
        lime = available;
    }

    /** Мир пропустил {@code m3} воды сорта {@code grade} за тик. */
    public void pass(WaterGrade grade, double m3) {
        if (grade == null || !(m3 > 0)) return;
        inlet = grade;
        pendingM3 += m3;
    }

    /**
     * Сорт на выходе для входного: осветлитель без извести воду не меняет, фильтр забитый пропускает, но не
     * очищает, колонна после проскока отдаёт входную воду.
     */
    public WaterGrade outlet(WaterGrade in) {
        return switch (stage) {
            case CLARIFIER -> lime ? WaterGrade.CLARIFIED : in;
            case SAND_FILTER -> train.filterLoadingFrac() >= 1 ? in : WaterGrade.FILTERED;
            case ION_EXCHANGER -> train.outlet(TreatmentStage.CATION).hardnessMolEqM3()
                    > SOFT_LIMIT * train.outlet(TreatmentStage.INLET).hardnessMolEqM3() ? in : WaterGrade.SOFTENED;
        };
    }

    /** Известь к оплате, кг: мир списывает предметы {@code burnt_lime} и вызывает {@link #payLime}. */
    public double limeDueKg() {
        return limeDueKg;
    }

    public void payLime(double kg) {
        limeDueKg = Math.max(0, limeDueKg - kg);
    }

    @Override
    public void step(UtilitiesSubsystem utilities, FastContext ctx, double slowDt) {
        if (!(slowDt > 0)) return;
        if (stage == Stage.CLARIFIER) train.setClarifier(lime, false);
        train.setInlet(inlet.quality(), pendingM3 / slowDt);
        train.stepSlow(slowDt);
        limeDueKg += train.limeKgS() * slowDt;
        pendingM3 = 0;
    }

    @Override
    public void save(StateWriter out) {
        out.putInt("inlet", inlet.ordinal());
        out.putDouble("pending_m3", pendingM3);
        out.putDouble("lime_due_kg", limeDueKg);
        out.putBoolean("lime", lime);
        train.save(out);
    }

    @Override
    public void load(StateReader in) {
        inlet = WaterGrade.values()[in.getInt("inlet")];
        pendingM3 = in.getDouble("pending_m3");
        limeDueKg = in.getDouble("lime_due_kg");
        lime = in.getBoolean("lime");
        train.load(in);
        train.setInlet(inlet.quality(), 0);
        train.stepSlow(0);
    }
}
