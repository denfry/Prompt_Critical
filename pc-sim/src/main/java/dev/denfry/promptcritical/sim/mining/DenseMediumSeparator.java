package dev.denfry.promptcritical.sim.mining;

import dev.denfry.promptcritical.sim.core.DeviceSubsystem;
import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import java.util.Locale;

/**
 * Двойник {@code dense_medium_separator} (08 §8.3): барабанный тяжелосредный сепаратор I1 на валу Create. Уголь
 * разделяется в магнетитовой суспензии {@code dense_medium} по {@link DensitySeparation} с E_p = 0,03 на два
 * продукта: всплывшее — концентрат, утонувшее — порода. Плотность разделения равна плотности суспензии (§8.3:
 * 1,4–1,8 г/см³) [допущение: сдвиг ρ_p относительно ρ_среды не моделируется], а её задаёт состав контура:
 * ρ = (W + M)/(W + M/ρ_mag), W — вода, кг (= л), M — магнетит, кг, ρ_mag = 5,0 г/см³.
 *
 * <p>Магнетит уходит с продуктами 0,75 кг/т питания (§8.3: 0,5–1 кг/т), вместе с ним — суспензия того же состава,
 * так что унос снижает объём контура, а не плотность. Магнетит — из {@code iron_ore_concentrate}, вода — подпитка.
 * Производительность и мощность — как у {@link CoalJig}: 10 кг/с при 64 об/мин [BALANCE]; мощность больше на насос
 * суспензии [BALANCE: 22 кВт]. Без суспензии в контуре ({@link #MIN_MEDIUM_L}) сепаратор стоит.
 *
 * <p>Питание — партиями одного анализа, как у отсадочной машины.
 */
public final class DenseMediumSeparator implements DeviceSubsystem.Unit {
    public static final String DEVICE_TYPE = "promptcritical:dense_medium_separator";
    public static final double NOMINAL_RPM = 64, MIN_RPM = 16, MAX_SPEED_RATIO = 2;
    public static final double NOMINAL_FEED_KG_S = 10, RATED_W = 22_000, IDLE_SHARE = 0.05;
    /** Плотность магнетита, г/см³. */
    public static final double MAGNETITE_DENSITY = 5.0;
    /** Потери магнетита с продуктами, кг на кг питания (0,75 кг/т). */
    public static final double MAGNETITE_LOSS_PER_KG = 0.75e-3;
    /** Объём контура суспензии и минимум для работы, л. */
    public static final double MEDIUM_CAPACITY_L = 1_500, MIN_MEDIUM_L = 500;
    /** Предел плотности суспензии: гуще насос не прокачивает [BALANCE]. */
    public static final double MAX_MEDIUM_DENSITY = 2.0;
    public static final double FEED_CAPACITY_KG = 200, PRODUCT_CAPACITY_KG = 500;

    /** Продукты барабана. */
    public enum Product { CLEAN, REFUSE }

    public final ShaftInput shaft = new ShaftInput();
    private CoalAssay feed;
    private double feedKg, waterKg, magnetiteKg, speedRatio;
    private boolean running;
    private DensitySeparation.Result split;
    private double splitRho = Double.NaN;
    private final CoalAssay[] productAssay = new CoalAssay[2];
    private final double[] productKg = new double[2];

    @Override
    public String deviceType() {
        return DEVICE_TYPE;
    }

    public void step(FastContext ctx) {
        double rpm = shaft.rpm(ctx.tick());
        speedRatio = rpm < MIN_RPM ? 0 : Math.min(MAX_SPEED_RATIO, rpm / NOMINAL_RPM);
        running = false;
        if (speedRatio <= 0 || feedKg <= 0 || mediumL() < MIN_MEDIUM_L || productFull()) return;
        double rho = mediumDensity();
        // плотность контура меняется только подпиткой: разделение пересчитывается при её смене
        if (split == null || Math.abs(rho - splitRho) > 1e-4) {
            split = DensitySeparation.wash(feed, rho, rho, DensitySeparation.Separator.DENSE_MEDIUM);
            splitRho = rho;
        }
        double kg = Math.min(feedKg, NOMINAL_FEED_KG_S * speedRatio * ctx.dt());
        feedKg -= kg;
        running = true;
        add(Product.CLEAN, split.cleanYield() * kg, split.clean());
        add(Product.REFUSE, split.refuseYield() * kg, split.refuse());
        dragOut(kg * MAGNETITE_LOSS_PER_KG);
        if (feedKg <= 1e-9) {
            feedKg = 0;
            feed = null;
            split = null;
        }
    }

    /** Унос суспензии с продуктами: магнетит {@code lostKg} и вода в той же пропорции. */
    private void dragOut(double lostKg) {
        if (magnetiteKg <= 0) return;
        double share = Math.min(1, lostKg / magnetiteKg);
        magnetiteKg -= magnetiteKg * share;
        waterKg -= waterKg * share;
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

    /** Объём суспензии в контуре, л. */
    public double mediumL() {
        return waterKg + magnetiteKg / MAGNETITE_DENSITY;
    }

    /** Плотность суспензии, г/см³; пустой контур — плотность воды. */
    public double mediumDensity() {
        double v = mediumL();
        return v <= 1e-9 ? 1 : (waterKg + magnetiteKg) / v;
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

    /** Долить воды в контур, л; возвращает принятое. */
    public double addWater(double litres) {
        double add = Math.max(0, Math.min(litres, MEDIUM_CAPACITY_L - mediumL()));
        waterKg += add;
        return add;
    }

    /**
     * Добавить магнетит, кг, целиком или никак: {@code false}, если суспензия выйдет за объём контура или станет
     * гуще {@link #MAX_MEDIUM_DENSITY}.
     */
    public boolean addMagnetite(double kg) {
        double m = magnetiteKg + kg, v = waterKg + m / MAGNETITE_DENSITY;
        if (kg <= 0 || v > MEDIUM_CAPACITY_L + 1e-9 || v > 0 && (waterKg + m) / v > MAX_MEDIUM_DENSITY + 1e-9) return false;
        magnetiteKg = m;
        return true;
    }

    /** Забрать {@code kg} продукта, если накоплено; анализ забранного или {@code null}. */
    public CoalAssay take(Product p, double kg) {
        int i = p.ordinal();
        if (productKg[i] < kg || productAssay[i] == null) return null;
        productKg[i] -= kg;
        return productAssay[i];
    }

    public double productKg(Product p) {
        return productKg[p.ordinal()];
    }

    public double feedKg() {
        return feedKg;
    }

    public double magnetiteKg() {
        return magnetiteKg;
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
        out.putDouble("feed_kg", feedKg);
        out.putDouble("water_kg", waterKg);
        out.putDouble("magnetite_kg", magnetiteKg);
        if (feed != null) CoalAssayState.write(out, "feed", feed);
        for (Product p : Product.values()) {
            String k = p.name().toLowerCase(Locale.ROOT);
            out.putDouble(k + "_kg", productKg[p.ordinal()]);
            if (productAssay[p.ordinal()] != null) CoalAssayState.write(out, k, productAssay[p.ordinal()]);
        }
    }

    @Override
    public void load(StateReader in) {
        feedKg = in.getDouble("feed_kg");
        waterKg = in.getDouble("water_kg");
        magnetiteKg = in.getDouble("magnetite_kg");
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
