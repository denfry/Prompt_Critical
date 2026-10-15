package dev.denfry.promptcritical.sim.mining;

import dev.denfry.promptcritical.sim.core.DeviceSubsystem;
import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import java.util.Locale;

/**
 * Двойник {@code coal_jig} (08 §8.3): отсадочная машина на валу Create, разделение по {@link DensitySeparation} с
 * E_p = 0,10 на концентрат, промпродукт и породу. Работает в реальном времени (механика FAST); производительность
 * ∝ скорости вала [BALANCE: 10 кг/с = 36 т/ч и 15 кВт при 64 об/мин — предмет угля за 10 с]. Подпиточная вода
 * 0,5 л/кг [BALANCE; оборотная вода не моделируется] — без воды машина стоит.
 *
 * <p>Питание — партиями одного анализа: следующая партия другого угля принимается, когда текущая промыта.
 * Продукты копятся в килограммах; остаток прежней партии в накопителе получает анализ новой [допущение: смешение
 * анализов не моделируется].
 */
public final class CoalJig implements DeviceSubsystem.Unit {
    public static final String DEVICE_TYPE = "promptcritical:coal_jig";
    public static final double NOMINAL_RPM = 64, MIN_RPM = 16, MAX_SPEED_RATIO = 2;
    public static final double NOMINAL_FEED_KG_S = 10, RATED_W = 15_000, IDLE_SHARE = 0.02;
    public static final double WATER_L_PER_KG = 0.5, WATER_CAPACITY_L = 2_000;
    /** Питание в бункере и предел накопителя продукта, кг. */
    public static final double FEED_CAPACITY_KG = 200, PRODUCT_CAPACITY_KG = 500;
    /** Плотности разделения по умолчанию, г/см³ (концентрат 1,5; порода 1,8 — §8.3). */
    public static final double DEFAULT_RHO_CLEAN = 1.50, DEFAULT_RHO_REFUSE = 1.80;
    /** Ступени плотности концентрата, которые перебирает настройка. */
    public static final double[] RHO_CLEAN_STEPS = {1.40, 1.50, 1.60, 1.70};

    /** Продукты в порядке {@link DensitySeparation.Result}. */
    public enum Product { CLEAN, MIDDLINGS, REFUSE }

    public final ShaftInput shaft = new ShaftInput();
    private double rhoClean = DEFAULT_RHO_CLEAN, rhoRefuse = DEFAULT_RHO_REFUSE;
    private CoalAssay feed;
    private double feedKg, waterL, speedRatio;
    private boolean running;
    private DensitySeparation.Result split;
    private final CoalAssay[] productAssay = new CoalAssay[3];
    private final double[] productKg = new double[3];

    @Override
    public String deviceType() {
        return DEVICE_TYPE;
    }

    public void step(FastContext ctx) {
        double rpm = shaft.rpm(ctx.tick());
        speedRatio = rpm < MIN_RPM ? 0 : Math.min(MAX_SPEED_RATIO, rpm / NOMINAL_RPM);
        running = false;
        if (speedRatio <= 0 || feedKg <= 0 || waterL <= 0 || productFull()) return;
        if (split == null) split = DensitySeparation.wash(feed, rhoClean, rhoRefuse, DensitySeparation.Separator.JIG);
        double kg = Math.min(feedKg, Math.min(NOMINAL_FEED_KG_S * speedRatio * ctx.dt(), waterL / WATER_L_PER_KG));
        feedKg -= kg;
        waterL -= kg * WATER_L_PER_KG;
        running = true;
        add(Product.CLEAN, split.cleanYield() * kg, split.clean());
        add(Product.MIDDLINGS, split.middlingsYield() * kg, split.middlings());
        add(Product.REFUSE, split.refuseYield() * kg, split.refuse());
        if (feedKg <= 1e-9) {
            feedKg = 0;
            feed = null;
            split = null;
        }
    }

    private void add(Product p, double kg, CoalAssay assay) {
        if (assay == null || kg <= 0) return;
        productKg[p.ordinal()] += kg;
        productAssay[p.ordinal()] = assay;
    }

    private boolean productFull() {
        for (double kg : productKg) if (kg >= PRODUCT_CAPACITY_KG) return true;
        return false;
    }

    /** Принять партию угля; {@code false} — бункер полон или в нём уголь другого анализа. */
    public boolean feed(CoalAssay assay, double kg) {
        if (feedKg + kg > FEED_CAPACITY_KG || feedKg > 0 && !CoalAssayState.same(feed, assay)) return false;
        if (feedKg <= 0) {
            feed = assay;
            split = null;
        }
        feedKg += kg;
        return true;
    }

    /** Долить воды, л; возвращает принятое. */
    public double addWater(double litres) {
        double add = Math.max(0, Math.min(litres, WATER_CAPACITY_L - waterL));
        waterL += add;
        return add;
    }

    /** Забрать {@code kg} продукта, если накоплено; анализ забранного или {@code null}. */
    public CoalAssay take(Product p, double kg) {
        int i = p.ordinal();
        if (productKg[i] < kg || productAssay[i] == null) return null;
        productKg[i] -= kg;
        return productAssay[i];
    }

    /** Следующая ступень плотности концентрата; порода не ниже концентрата. Возвращает новую ρ. */
    public double cycleRhoClean() {
        int at = 0;
        for (int i = 0; i < RHO_CLEAN_STEPS.length; i++) if (Math.abs(RHO_CLEAN_STEPS[i] - rhoClean) < 1e-9) at = i;
        rhoClean = RHO_CLEAN_STEPS[(at + 1) % RHO_CLEAN_STEPS.length];
        rhoRefuse = Math.max(rhoRefuse, rhoClean);
        split = null;
        return rhoClean;
    }

    public double rhoClean() {
        return rhoClean;
    }

    public double rhoRefuse() {
        return rhoRefuse;
    }

    public double productKg(Product p) {
        return productKg[p.ordinal()];
    }

    public double feedKg() {
        return feedKg;
    }

    public double waterL() {
        return waterL;
    }

    public boolean running() {
        return running;
    }

    /** Мощность на валу, Вт. */
    public double powerW() {
        return RATED_W * speedRatio * (running ? 1 : IDLE_SHARE);
    }

    @Override
    public void save(StateWriter out) {
        out.putDouble("rho_clean", rhoClean);
        out.putDouble("rho_refuse", rhoRefuse);
        out.putDouble("feed_kg", feedKg);
        out.putDouble("water_l", waterL);
        if (feed != null) CoalAssayState.write(out, "feed", feed);
        for (Product p : Product.values()) {
            String k = p.name().toLowerCase(Locale.ROOT);
            out.putDouble(k + "_kg", productKg[p.ordinal()]);
            if (productAssay[p.ordinal()] != null) CoalAssayState.write(out, k, productAssay[p.ordinal()]);
        }
    }

    @Override
    public void load(StateReader in) {
        rhoClean = in.getDouble("rho_clean");
        rhoRefuse = in.getDouble("rho_refuse");
        feedKg = in.getDouble("feed_kg");
        waterL = in.getDouble("water_l");
        feed = in.has("feed") ? CoalAssayState.read(in, "feed") : null;
        if (feed == null) feedKg = 0;
        split = null;
        for (Product p : Product.values()) {
            String k = p.name().toLowerCase(Locale.ROOT);
            productKg[p.ordinal()] = in.getDouble(k + "_kg");
            productAssay[p.ordinal()] = in.has(k) ? CoalAssayState.read(in, k) : null;
        }
    }
}
