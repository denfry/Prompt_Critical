package dev.denfry.promptcritical.sim.mining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Смешанные жилы (08 §8.1): детерминизм, роли, содержание, угольные свиты и правило Хильта. */
class VeinFieldTest {
    private static final long SEED = 0x5EED_C0A1L;

    /** 02 §2.1.3: шум нормирован к σ ≈ 1. */
    @Test
    void noiseHasUnitVariance() {
        SimplexNoise n = new SimplexNoise(42, 6);
        double s = 0, s2 = 0;
        int k = 0;
        for (int x = 0; x < 120; x += 3)
            for (int y = -60; y < 60; y += 3)
                for (int z = 0; z < 600; z += 7) {
                    double v = n.sample(x, y, z);
                    s += v;
                    s2 += v * v;
                    k++;
                }
        double mean = s / k;
        assertThat(mean).isCloseTo(0, within(0.05));
        assertThat(Math.sqrt(s2 / k - mean * mean)).isCloseTo(1, within(0.05));
    }

    /** Первое тело-линза с полным набором ролей, начиная с ячейки (0, 0). */
    private static List<VeinBody> lensBodies(VeinField f, int count) {
        List<VeinBody> out = new ArrayList<>();
        for (int i = 0; out.size() < count; i++) {
            VeinBody b = f.body(i % 40, i / 40, null);
            if (b == null || b.type().seam()) continue;
            boolean full = true;
            for (OreRole r : OreRole.values()) full &= b.type().grade(r) != null;
            if (full) out.add(b);
        }
        return out;
    }

    /** Состав тела: (роль, содержание) по всем блокам описывающего параллелепипеда — в одну строку битов. */
    private static long[] scan(VeinField f, VeinBody b) {
        VeinField.Sampler s = f.sampler(b.minX(), b.minZ(), b.maxX(), b.maxZ(), null);
        List<Long> bits = new ArrayList<>();
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int y = b.minY(); y <= b.maxY(); y++)
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    VeinHit h = s.sample(x, y, z);
                    if (h == null) continue;
                    bits.add((long) x << 40 ^ (long) y << 20 ^ z);
                    bits.add((long) h.role().ordinal() << 32 ^ h.mineral().hashCode());
                    OreAssay a = f.assay(h, x, y, z);
                    if (a != null) {
                        bits.add(Double.doubleToRawLongBits(a.gradeFrac()));
                        bits.add(Double.doubleToRawLongBits(a.sulfurFrac()));
                        for (Map.Entry<String, Double> e : a.byproductsPpm().entrySet())
                            bits.add(Double.doubleToRawLongBits(e.getValue()));
                    }
                }
        return bits.stream().mapToLong(Long::longValue).toArray();
    }

    /** T8.2: сид S, ячейка C, генерация дважды и на другом потоке — бит в бит. */
    @Test
    void t8_2_generationIsBitExactAcrossThreads() throws Exception {
        VeinField f = new VeinField(SEED);
        VeinBody b = lensBodies(f, 1).get(0);
        long[] first = scan(f, b);
        long[] again = scan(new VeinField(SEED), b);
        long[] other = CompletableFuture.supplyAsync(() -> {
            VeinField g = new VeinField(SEED);
            return scan(g, g.body(b.cellX(), b.cellZ(), null));
        }).get();
        assertThat(first.length).isGreaterThan(500);
        assertThat(again).isEqualTo(first);
        assertThat(other).isEqualTo(first);
        assertThat(scan(new VeinField(SEED + 1), b)).isNotEqualTo(first);
    }

    /** T8.2: доли ролей по зонам высоты — по таблице §8.1.1 ±5 %. */
    @Test
    void t8_2_roleSharesFollowZoningTable() {
        VeinField f = new VeinField(SEED);
        long[][] count = new long[3][OreRole.values().length];
        for (VeinBody b : lensBodies(f, 12)) {
            VeinField.Sampler s = f.sampler(b.minX(), b.minZ(), b.maxX(), b.maxZ(), null);
            for (int x = b.minX(); x <= b.maxX(); x++)
                for (int y = b.minY(); y <= b.maxY(); y++)
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        VeinHit h = s.sample(x, y, z);
                        if (h != null && h.body().cellX() == b.cellX() && h.body().cellZ() == b.cellZ())
                            count[OreRole.zone(h.h())][h.role().ordinal()]++;
                    }
        }
        double[] hZone = {0.8, 0.5, 0.2};
        for (int zone = 0; zone < 3; zone++) {
            long total = 0;
            for (long c : count[zone]) total += c;
            assertThat(total).as("zone %d", zone).isGreaterThan(1000);
            for (OreRole r : OreRole.values())
                assertThat((double) count[zone][r.ordinal()] / total).as("zone %d %s", zone, r)
                        .isCloseTo(OreRole.probability(r, hZone[zone]), within(0.05));
        }
    }

    /** Доля рудных блоков линзы ≈ f₀·⟨√(1 − r²)⟩ = f₀·2/5·… — проверяем порядок: не пусто и не сплошь. */
    @Test
    void lensIsPartiallyMineralised() {
        VeinField f = new VeinField(SEED);
        VeinBody b = lensBodies(f, 1).get(0);
        VeinField.Sampler s = f.sampler(b.minX(), b.minZ(), b.maxX(), b.maxZ(), null);
        int ore = 0;
        double volume = 4.0 / 3 * Math.PI * b.semiAxisA() * b.semiAxisA() * b.semiAxisC();
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int y = b.minY(); y <= b.maxY(); y++)
                for (int z = b.minZ(); z <= b.maxZ(); z++)
                    if (s.sample(x, y, z) != null) ore++;
        // ⟨√(1 − r²)⟩ по объёму эллипсоида = 3π/16 ≈ 0,589
        assertThat(ore / volume).isCloseTo(b.type().f0() * 0.589, within(0.08));
    }

    /** Содержание в границах, класс G0–G5, попутный Co коррелирован с Ni, сера — по стехиометрии. */
    @Test
    void assayFollowsGradeLaw() {
        VeinField f = new VeinField(SEED, VeinConfig.defaults(), List.of(VeinCatalog.byId("cu_ni_sulfide")));
        VeinBody b = f.body(0, 0, null);
        for (int i = 1; b == null; i++) b = f.body(i, 0, null);
        VeinField.Sampler s = f.sampler(b.minX(), b.minZ(), b.maxX(), b.maxZ(), null);
        int pent = 0;
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int y = b.minY(); y <= b.maxY(); y++)
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    VeinHit h = s.sample(x, y, z);
                    if (h == null) continue;
                    OreAssay a = f.assay(h, x, y, z);
                    OreGrade g = h.grade();
                    assertThat(a.veinId()).isEqualTo("cu_ni_sulfide");
                    assertThat(a.gradeFrac()).isBetween(g.gMin(), g.gMax());
                    assertThat((int) a.gradeClass()).isBetween(0, 5);
                    assertThat(a.sulfurFrac()).isCloseTo(Math.min(1, a.gradeFrac() * g.sulfurPerGrade()), within(1e-12));
                    if (h.mineral().equals("pentlandite_ore")) {
                        pent++;
                        double ratio = a.byproductPpm("Co") * 1e-6 / a.gradeFrac();
                        assertThat(ratio).isBetween(0.035 * Math.exp(-1.8), 0.035 * Math.exp(1.8));
                    }
                }
        assertThat(pent).isGreaterThan(50);
        OreGrade ni = VeinCatalog.byId("cu_ni_sulfide").grade(OreRole.PRIMARY);
        assertThat(ni.gradeClass(ni.gMin())).isZero();
        assertThat(ni.gradeClass(ni.gMax())).isEqualTo(5);
    }

    /** Жила с весом 0 не появляется; выключенный worldgen — пусто; биомы ограничивают типы. */
    @Test
    void configAndBiomesSelectTypes() {
        VeinField off = new VeinField(SEED, new VeinConfig(false, 3, 0.45, Map.of(), 1, 4, 0.32), VeinCatalog.M2);
        VeinField noIron = new VeinField(SEED,
                new VeinConfig(true, 3, 0.45, Map.of("banded_iron", 0.0), 1, 4, 0.32), VeinCatalog.M2);
        VeinField all = new VeinField(SEED);
        int bodies = 0;
        for (int cx = 0; cx < 60; cx++)
            for (int cz = 0; cz < 10; cz++) {
                assertThat(off.body(cx, cz, null)).isNull();
                VeinBody b = noIron.body(cx, cz, null);
                if (b != null) assertThat(b.type().id()).isNotEqualTo("banded_iron");
                VeinBody p = all.body(cx, cz, (x, z) -> BiomeClass.PLAINS);
                if (p != null) {
                    bodies++;
                    assertThat(p.type().id()).isIn("banded_iron", "coal_seam", "polymetallic");
                }
                VeinBody j = all.body(cx, cz, (x, z) -> BiomeClass.JUNGLE);
                if (j != null) assertThat(j.type().id()).isIn("banded_iron", "polymetallic", "bauxite");
            }
        // fill 0,45 по 600 ячейкам
        assertThat(bodies / 600.0).isCloseTo(0.45, within(0.06));
    }

    /** T8.3: V^daf(y = 0) при нулевом шуме = 21,96 % (марка К). */
    @Test
    void t8_3_hiltRuleAtSeaLevel() {
        double v = VeinField.hiltVolatileDafPct(0, 0, 0.32);
        assertThat(v).isCloseTo(21.96, within(0.01));
        assertThat(CoalRank.classify(v / 100, VeinField.plasticLayerMm(v / 100))).isEqualTo(CoalRank.K);
        assertThat(SeamRank.of(v)).isEqualTo(SeamRank.BITUMINOUS);
    }

    /** Правило Хильта: марка растёт с глубиной Б → Д → Г → Ж → К → ОС → Т → А. */
    @Test
    void rankSequenceWithDepth() {
        int[] y = {72, 60, 47, 30, 0, -10, -20, -50};
        CoalRank[] expect = {CoalRank.B, CoalRank.D, CoalRank.G, CoalRank.ZH, CoalRank.K, CoalRank.OS, CoalRank.T,
                CoalRank.A};
        for (int i = 0; i < y.length; i++) {
            double v = VeinField.hiltVolatileDafPct(y[i], 0, 0.32) / 100;
            assertThat(CoalRank.classify(v, VeinField.plasticLayerMm(v))).as("y = %d", y[i]).isEqualTo(expect[i]);
        }
        assertThat(VeinField.hiltVolatileDafPct(300, 0, 0.32)).isEqualTo(48);
        assertThat(VeinField.hiltVolatileDafPct(-300, 0, 0.32)).isEqualTo(3);
    }

    /** Свита: уголь в пределах мощности пластов, под пластом — огнеупорная глина; у угля — coal_assay. */
    @Test
    void coalSeamSuiteGeometry() {
        VeinField f = new VeinField(SEED, VeinConfig.defaults(), List.of(VeinCatalog.byId("coal_seam")));
        VeinBody b = f.body(0, 0, null);
        for (int i = 1; b == null; i++) b = f.body(i, 0, null);
        assertThat(b.seamCount()).isBetween(1, 3);
        for (int k = 0; k < b.seamCount(); k++) {
            assertThat(b.seamThickness(k)).isBetween(1, 4);
            assertThat(b.seamSpacing(k)).isBetween(6, 20);
        }
        int cx = (int) Math.floor(b.centerX()), cz = (int) Math.floor(b.centerZ());
        VeinField.Sampler s = f.sampler(cx - 2, cz - 2, cx + 2, cz + 2, null);
        int coal = 0, clay = 0;
        for (int x = cx - 2; x <= cx + 2; x++)
            for (int z = cz - 2; z <= cz + 2; z++)
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    VeinHit h = s.sample(x, y, z);
                    if (h == null) continue;
                    if (h.coal()) {
                        coal++;
                        CoalAssay a = f.coalAssay(x, y, z);
                        assertThat(a.volatileDaf() * 100).isCloseTo(f.volatileDafPct(x, y, z), within(1e-9));
                        assertThat(a.ashDryFrac()).isBetween(0.08, 0.35);
                        assertThat(f.seamRank(x, y, z)).isEqualTo(SeamRank.of(a.volatileDaf() * 100));
                    } else if (h.mineral().equals("fireclay")) clay++;
                }
        assertThat(coal).isGreaterThan(10);
        assertThat(clay).isGreaterThan(2);
    }

    /** Выборка по чанку совпадает с поблочной. */
    @Test
    void chunkSamplerMatchesPointSampling() {
        VeinField f = new VeinField(SEED);
        VeinBody b = lensBodies(f, 1).get(0);
        int chunkX = Math.floorDiv((int) b.centerX(), 16), chunkZ = Math.floorDiv((int) b.centerZ(), 16);
        VeinField.Sampler s = f.sampler(chunkX, chunkZ, null);
        assertThat(s.bodyCount()).isGreaterThanOrEqualTo(1);
        for (int x = chunkX * 16; x < chunkX * 16 + 16; x += 3)
            for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z += 3)
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    VeinHit a = s.sample(x, y, z), p = f.sample(x, y, z, null);
                    assertThat(a == null ? null : a.mineral()).isEqualTo(p == null ? null : p.mineral());
                }
    }
}
