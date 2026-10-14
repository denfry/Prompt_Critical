package dev.denfry.promptcritical.sim.mining;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Детерминированное поле смешанных жил (08 §8.1): ячейки 3×3 чанка, в ячейке с вероятностью 0,45 — одно тело,
 * тип по весам каталога с множителями биома. Доля рудных блоков линзы f = f₀√(1 − r²)(1 + 0,35N), роль — по
 * относительной высоте h; содержание g = clamp(g_med·e^{σN}, g_min, g_max) (§8.1.3); угольные свиты — по §8.1.4
 * с правилом Хильта. Всё — функция (сид, координаты): одинаково на любом потоке, кешей и изменяемого состояния нет
 * (T8.2). Генератор чанка берёт {@link #sampler} один раз на чанк.
 */
public final class VeinField {
    /** ID блока угольного пласта. */
    public static final String COAL_BLOCK = "coal_seam";
    /** Масштаб шума доли руды (§8.1.1) и содержания (02 §2.1.3), блоков. */
    public static final double ORE_NOISE_SCALE = 6, GRADE_NOISE_SCALE = 24;
    /** Амплитуда шума в f_ore. */
    public static final double ORE_NOISE_AMPLITUDE = 0.35;
    /** Полуоси линзы и наклон (§8.1.1). */
    static final double A_MIN = 12, A_MAX = 20, C_MIN = 3, C_MAX = 6, TILT_MAX = Math.toRadians(30);
    /** Свита (§8.1.4): протяжённость 48–160 (полуоси 24–80), падение до 10°, шаг 6–20, 1–3 пласта. */
    static final double SEAM_R_MIN = 24, SEAM_R_MAX = 80, DIP_MAX = Math.toRadians(10);
    static final int SPACING_MIN = 6, SPACING_MAX = 20, SEAMS_MAX = 3;
    /** Правило Хильта (§8.1.4): V^daf(y) = clamp(45 − k(72 − y) + 3N, 3, 48) %. */
    public static final double HILT_V0 = 45, HILT_Y0 = 72, HILT_NOISE = 3, HILT_MIN = 3, HILT_MAX = 48;

    private static final int SALT_FILL = 1, SALT_TYPE = 2, SALT_GEOM = 16, SALT_ORE = 101, SALT_ROLE = 102;

    /** Класс биома в точке поверхности; {@code null} — биомы не учитываются. */
    @FunctionalInterface
    public interface BiomeProbe {
        BiomeClass at(int blockX, int blockZ);
    }

    private final long seed;
    private final VeinConfig cfg;
    private final List<VeinType> catalog;
    private final SimplexNoise oreNoise, gradeNoise, byproductNoise, coalNoise, ashNoise;

    public VeinField(long seed, VeinConfig cfg, List<VeinType> catalog) {
        this.seed = seed;
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.catalog = List.copyOf(catalog);
        this.oreNoise = new SimplexNoise(WorldHash.mix(seed ^ 0x6F72L), ORE_NOISE_SCALE);
        this.gradeNoise = new SimplexNoise(WorldHash.mix(seed ^ 0x6772L), GRADE_NOISE_SCALE);
        this.byproductNoise = new SimplexNoise(WorldHash.mix(seed ^ 0x6270L), GRADE_NOISE_SCALE);
        this.coalNoise = new SimplexNoise(WorldHash.mix(seed ^ 0x636FL), GRADE_NOISE_SCALE);
        this.ashNoise = new SimplexNoise(WorldHash.mix(seed ^ 0x6173L), GRADE_NOISE_SCALE);
    }

    public VeinField(long seed) {
        this(seed, VeinConfig.defaults(), VeinCatalog.M2);
    }

    public VeinConfig config() {
        return cfg;
    }

    /** Ячейка, в которую попадает блок. */
    public int cellOf(int block) {
        return Math.floorDiv(block, cfg.cellBlocks());
    }

    /** Тело ячейки или {@code null}. Биом берётся в центре ячейки. */
    public VeinBody body(int cellX, int cellZ, BiomeProbe biomes) {
        if (!cfg.enabled()) return null;
        if (WorldHash.unit(seed, cellX, 0, cellZ, SALT_FILL) >= cfg.fillProbability()) return null;
        int s = cfg.cellBlocks();
        BiomeClass biome = biomes == null ? null : biomes.at(cellX * s + s / 2, cellZ * s + s / 2);
        double total = 0;
        for (VeinType t : catalog) total += cfg.weight(t) * t.biomeFactor(biome);
        if (!(total > 0)) return null;
        double pick = WorldHash.unit(seed, cellX, 0, cellZ, SALT_TYPE) * total;
        VeinType type = null;
        for (VeinType t : catalog) {
            double w = cfg.weight(t) * t.biomeFactor(biome);
            if (w <= 0) continue;
            type = t;
            if (pick < w) break;
            pick -= w;
        }
        return build(type, cellX, cellZ);
    }

    private VeinBody build(VeinType type, int cellX, int cellZ) {
        int s = cfg.cellBlocks();
        double x0 = cellX * s + geom(cellX, cellZ, 0) * s, z0 = cellZ * s + geom(cellX, cellZ, 1) * s;
        double y0 = triangular(type.yMin(), type.yPeak(), type.yMax(), geom(cellX, cellZ, 2));
        double azimuth = geom(cellX, cellZ, 3) * 2 * Math.PI;
        int salt = (int) WorldHash.hash(seed, cellX, 0, cellZ, SALT_GEOM);
        if (!type.seam()) {
            double a = A_MIN + geom(cellX, cellZ, 4) * (A_MAX - A_MIN);
            double c = C_MIN + geom(cellX, cellZ, 5) * (C_MAX - C_MIN);
            double tilt = geom(cellX, cellZ, 6) * TILT_MAX;
            return new VeinBody(type, cellX, cellZ, x0, y0, z0, a, c, 0, 0, azimuth, tilt, null, null, salt);
        }
        int n = 1 + (int) (geom(cellX, cellZ, 7) * SEAMS_MAX);
        int[] thick = new int[n], spacing = new int[n];
        int tSpan = cfg.seamThicknessMax() - cfg.seamThicknessMin() + 1;
        for (int k = 0; k < n; k++) {
            thick[k] = cfg.seamThicknessMin() + (int) (geom(cellX, cellZ, 10 + k) * tSpan);
            spacing[k] = Math.max(thick[k] + 2,
                    SPACING_MIN + (int) (geom(cellX, cellZ, 20 + k) * (SPACING_MAX - SPACING_MIN + 1)));
        }
        double rx = SEAM_R_MIN + geom(cellX, cellZ, 4) * (SEAM_R_MAX - SEAM_R_MIN);
        double rz = SEAM_R_MIN + geom(cellX, cellZ, 5) * (SEAM_R_MAX - SEAM_R_MIN);
        double dip = geom(cellX, cellZ, 6) * DIP_MAX;
        return new VeinBody(type, cellX, cellZ, x0, y0, z0, 0, 0, rx, rz, azimuth, dip, thick, spacing, salt);
    }

    private double geom(int cellX, int cellZ, int k) {
        return WorldHash.unit(seed, cellX, k, cellZ, SALT_GEOM + 1);
    }

    /** Обратная функция треугольного распределения (min, mode, max) от u ∈ [0, 1). */
    static double triangular(double lo, double mode, double hi, double u) {
        if (hi <= lo) return lo;
        double f = (mode - lo) / (hi - lo);
        return u < f ? lo + Math.sqrt(u * (hi - lo) * (mode - lo)) : hi - Math.sqrt((1 - u) * (hi - lo) * (hi - mode));
    }

    /** Выборка по чанку: тела соседних ячеек, задевающие чанк. Потокобезопасна (неизменяема). */
    public Sampler sampler(int chunkX, int chunkZ, BiomeProbe biomes) {
        return sampler(chunkX * 16, chunkZ * 16, chunkX * 16 + 15, chunkZ * 16 + 15, biomes);
    }

    /** Выборка по прямоугольнику блоков [x0, x1] × [z0, z1]. */
    public Sampler sampler(int x0, int z0, int x1, int z1, BiomeProbe biomes) {
        int reach = (int) Math.ceil(Math.max(SEAM_R_MAX, A_MAX + C_MAX)) + 2;
        List<VeinBody> list = new ArrayList<>(2);
        for (int cx = cellOf(x0 - reach); cx <= cellOf(x1 + reach); cx++)
            for (int cz = cellOf(z0 - reach); cz <= cellOf(z1 + reach); cz++) {
                VeinBody b = body(cx, cz, biomes);
                if (b != null && b.intersects(x0, x1, z0, z1)) list.add(b);
            }
        return new Sampler(list.toArray(VeinBody[]::new));
    }

    /** Рудный блок в точке или {@code null} (удобство для тестов и команд; генератор берёт {@link #sampler}). */
    public VeinHit sample(int x, int y, int z, BiomeProbe biomes) {
        return sampler(x, z, x, z, biomes).sample(x, y, z);
    }

    /** Тела вокруг области. */
    public final class Sampler {
        private final VeinBody[] bodies;

        private Sampler(VeinBody[] bodies) {
            this.bodies = bodies;
        }

        public int bodyCount() {
            return bodies.length;
        }

        public VeinBody body(int i) {
            return bodies[i];
        }

        /** Рудный блок или {@code null}; при наложении тел побеждает первое в порядке ячеек. */
        public VeinHit sample(int x, int y, int z) {
            for (VeinBody b : bodies) {
                if (!b.inBox(x, y, z)) continue;
                VeinHit hit = b.type.seam() ? sampleSeam(b, x, y, z) : sampleLens(b, x, y, z);
                if (hit != null) return hit;
            }
            return null;
        }
    }

    private VeinHit sampleLens(VeinBody b, int x, int y, int z) {
        double dx = x + 0.5 - b.x0, dy = y + 0.5 - b.y0, dz = z + 0.5 - b.z0;
        double u = dx * b.cosAz + dz * b.sinAz, w = -dx * b.sinAz + dz * b.cosAz;
        double up = u * b.cosTilt + dy * b.sinTilt, vp = -u * b.sinTilt + dy * b.cosTilt;
        double r2 = (up * up + w * w) / (b.a * b.a) + vp * vp / (b.c * b.c);
        if (r2 >= 1) return null;
        if (!oreHere(b, x, y, z, r2)) return null;
        double h = (vp + b.c) / (2 * b.c);
        OreRole role = OreRole.pick(h, WorldHash.unit(seed, x, y, z, SALT_ROLE ^ b.salt));
        OreGrade g = b.type.grade(role);
        if (g == null) {
            role = OreRole.PRIMARY;
            g = b.type.grade(role);
        }
        return new VeinHit(b, role, g, h);
    }

    private VeinHit sampleSeam(VeinBody b, int x, int y, int z) {
        double dx = x + 0.5 - b.x0, dz = z + 0.5 - b.z0;
        double u = dx * b.cosAz + dz * b.sinAz, w = -dx * b.sinAz + dz * b.cosAz;
        double r2 = u * u / (b.rx * b.rx) + w * w / (b.rz * b.rz);
        if (r2 >= 1) return null;
        double roof = b.y0 - u * b.tanDip;
        for (int k = 0; k < b.thickness.length; k++) {
            int top = (int) Math.floor(roof);
            if (y <= top && y > top - b.thickness[k]) {
                if (!oreHere(b, x, y, z, r2)) return null;
                OreRole role = OreRole.pickSeam(WorldHash.unit(seed, x, y, z, SALT_ROLE ^ b.salt));
                return new VeinHit(b, role, b.type.grade(role), 0.5);
            }
            if (y == top - b.thickness[k]) {
                // почва пласта — огнеупорная глина (§8.1.4)
                if (!oreHere(b, x, y, z, r2)) return null;
                return new VeinHit(b, OreRole.INTERMEDIATE, b.type.grade(OreRole.INTERMEDIATE), 0);
            }
            roof -= b.spacing[k];
        }
        return null;
    }

    private boolean oreHere(VeinBody b, int x, int y, int z, double r2) {
        double f = b.type.f0() * Math.sqrt(1 - r2) * (1 + ORE_NOISE_AMPLITUDE * oreNoise.sample(x, y, z));
        return WorldHash.unit(seed, x, y, z, SALT_ORE ^ b.salt) < f;
    }

    /** Анализ рудного блока (§8.1.3); у угля — {@code null} (см. {@link #coalAssay}). */
    public OreAssay assay(VeinHit hit, int x, int y, int z) {
        OreGrade gr = hit.grade();
        if (gr == null) return null;
        double g = gr.grade(gradeNoise.sample(x, y, z));
        Map<String, Double> by = Map.of();
        if (gr.byproduct() != null) {
            double gb = gr.byproductRatio() * g * StrictMath.exp(gr.byproductSigma() * byproductNoise.sample(x, y, z));
            by = new HashMap<>(1);
            by.put(gr.byproduct(), gb * 1e6);
        }
        double purity = "SiO2".equals(gr.component()) ? g : Double.NaN;
        return new OreAssay(hit.veinId(), gr.mineral(), (byte) gr.gradeClass(g), g, by,
                Math.min(1, g * gr.sulfurPerGrade()), null, purity);
    }

    /** Правило Хильта: V^daf(y) = clamp(45 − k(72 − y) + 3N, 3, 48), %. */
    public static double hiltVolatileDafPct(double y, double noise, double gradientPerBlock) {
        double v = HILT_V0 - gradientPerBlock * (HILT_Y0 - y) + HILT_NOISE * noise;
        return Math.max(HILT_MIN, Math.min(HILT_MAX, v));
    }

    /** V^daf блока пласта, %. */
    public double volatileDafPct(int x, int y, int z) {
        return hiltVolatileDafPct(y, coalNoise.sample(x, y, z), cfg.rankGradientPerBlock());
    }

    /** Свойство {@code rank} блока пласта. */
    public SeamRank seamRank(int x, int y, int z) {
        return SeamRank.of(volatileDafPct(x, y, z));
    }

    /** Пластический слой y по V^daf (доля), мм — см. {@link #coalAssay}. */
    public static double plasticLayerMm(double volatileDaf) {
        double dv = (volatileDaf - 0.28) / 0.08;
        return 26 * StrictMath.exp(-dv * dv);
    }

    /**
     * {@code coal_assay} блока пласта. V^daf — по Хильту; остальное — [допущение], в GDD формул нет: пластический
     * слой y = 26·exp(−((V − 0,28)/0,08)²) мм (так марки ложатся по ГОСТ 25543: Д → Г → Ж → К → ОС → Т с ростом
     * глубины), A^d = clamp(0,20 + 0,06N, 0,08, 0,35) (§8.2.1: 12–30 %), W^r: Б 0,35, Д–Г 0,08–0,15, глубже 0,06,
     * S^d = clamp(0,015·e^{0,5N}, 0,004, 0,05).
     */
    public CoalAssay coalAssay(int x, int y, int z) {
        double v = volatileDafPct(x, y, z) / 100;
        double plastic = plasticLayerMm(v);
        double n = ashNoise.sample(x, y, z);
        double ash = Math.max(0.08, Math.min(0.35, 0.20 + 0.06 * n));
        double moisture = v > 0.42 ? 0.35 : v >= 0.33 ? 0.08 + 0.07 * (v - 0.33) / 0.09 : 0.06;
        double sulfur = Math.max(0.004, Math.min(0.05, 0.015 * StrictMath.exp(0.5 * byproductNoise.sample(x, y, z))));
        return CoalAssay.typical(v, ash, moisture, sulfur, plastic);
    }
}
