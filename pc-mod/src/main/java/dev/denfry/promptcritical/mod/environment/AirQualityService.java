package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.combustion.CombustionResult;
import dev.denfry.promptcritical.sim.environment.EmissionLedger;
import dev.denfry.promptcritical.sim.environment.Pollution;
import dev.denfry.promptcritical.sim.environment.PollutionField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Загрязнение воздуха в мире (14 §14.1, §14.3, §14.4, R21). Топки раз в секунду сообщают выбросы из своего
 * {@link CombustionResult} и параметры трубы ({@link #emit}); каждые {@value #STEP_TICKS} тиков (10 с реального времени,
 * 720 с физики):
 *
 * <ol>
 *   <li>слой A — приземные концентрации SO₂, NOx и PM в чанках ядра 17×17 вокруг каждой трубы по гауссову шлейфу с
 *   подъёмом по Бриггсу; расстояния сжаты {@value #DISTANCE_SCALE} ({@code env.plume_distance_scale}), значения —
 *   нет;</li>
 *   <li>слой B — {@link PollutionField}: осаждение, перенос по ветру, очистка; поле в {@code SavedData} измерения;</li>
 *   <li>учёт — {@link EmissionLedger} (метод «рассчитано», пока на трубах нет САКВ);</li>
 *   <li>эффекты на игрока по уровням §14.4.1: дымка и смог — сообщение, кашель — замедление, слабость — слабость
 *   и замедление; кислотный дождь — сообщение. Полумаска или противогаз в слоте головы защищают.</li>
 * </ol>
 */
public final class AirQualityService {
    public static final int STEP_TICKS = 200, KERNEL_RADIUS = 8, HOLD_TICKS = 60;
    public static final double DISTANCE_SCALE = 0.05, EXIT_VELOCITY_MS = 10, STEP_PHYS_S = 720;
    private static final String P = "message.promptcritical.air.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        LANG.put(P + "haze", new String[] {"Haze: PM₁₀ %s µg/m³", "Дымка: PM₁₀ %s мкг/м³"});
        LANG.put(P + "smog", new String[] {"Smog: SO₂ %s, PM₁₀ %s µg/m³", "Смог: SO₂ %s, PM₁₀ %s мкг/м³"});
        LANG.put(P + "cough", new String[] {"Smog makes you cough: SO₂ %s, PM₁₀ %s µg/m³ — wear a respirator or gas mask",
                "Смог душит: SO₂ %s, PM₁₀ %s мкг/м³ — наденьте респиратор или противогаз"});
        LANG.put(P + "acid_rain", new String[] {"Acid rain: pH %s", "Кислотный дождь: pH %s"});
        LANG.put(P + "withering", new String[] {"Acid load on this chunk: Φ = %s — plants suffer", "Кислотная нагрузка чанка: Φ = %s — растения страдают"});
    }

    /** Источник: выбросы, кг/с, по веществам слоя A, параметры трубы, срок действия. */
    private record Source(BlockPos top, double heightM, double exitK, double flueKgS, double[] qKgS, long until) {}

    private static final Map<ServerLevel, Map<BlockPos, Source>> SOURCES = new WeakHashMap<>();
    private static final Map<ServerLevel, EmissionLedger> LEDGERS = new WeakHashMap<>();
    /** Слой A на последнем шаге: чанк → кг/м³ по веществам. */
    private static final Map<ServerLevel, Map<Long, double[]>> AIR = new WeakHashMap<>();
    private static final Map<ServerPlayer, Pollution.Level> SHOWN = new WeakHashMap<>();

    private AirQualityService() {
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }

    private static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel s && s.getGameTime() % STEP_TICKS == 0) step(s);
        });
    }

    /**
     * Выброс топки за секунду: труба вершиной {@code top}, высотой {@code heightM}, температура газов в устье.
     * Учёт пишется за 1 с реального времени = {@code kSlow} физики.
     */
    public static void emit(ServerLevel level, BlockPos top, double heightM, double exitK, CombustionResult r, String id,
            EmissionLedger.Category category) {
        if (r == null || !(r.fluegasKgS > 0)) return;
        double[] q = {r.so2KgS, r.noxKgS, r.pmKgS};
        // очистка на тракте: аппараты рядом с трубой (§14.2); расход газа при температуре устья
        double flowM3S = r.fluegasKgS / (353 / Math.max(300, exitK));
        FlueGasCleaner.apply(level, top, q, flowM3S, 72);
        SOURCES.computeIfAbsent(level, l -> new HashMap<>()).put(top.immutable(),
                new Source(top.immutable(), Math.max(1, heightM), exitK, r.fluegasKgS, q, level.getGameTime() + HOLD_TICKS));
        EmissionLedger ledger = ledger(level);
        double dt = 72;
        ledger.record(id, category, EmissionLedger.Pollutant.SO2, q[0], dt);
        ledger.record(id, category, EmissionLedger.Pollutant.NOX, q[1], dt);
        ledger.record(id, category, EmissionLedger.Pollutant.PM, q[2], dt);
        // САКВ у устья трубы — выбросы измерены, отчётность без повышающего множителя (§14.1.4)
        ledger.source(id, category).method = cems(level, top) ? EmissionLedger.Method.MEASURED : EmissionLedger.Method.CALCULATED;
        ledger.record(id, category, EmissionLedger.Pollutant.CO2, r.co2KgS, dt);
        ledger.record(id, category, EmissionLedger.Pollutant.CH4, r.ch4KgS, dt);
        ledger.record(id, category, EmissionLedger.Pollutant.CO, r.coKgS, dt);
    }

    /** Анализатор САКВ в 3×3×3 у устья трубы. */
    static boolean cems(ServerLevel level, BlockPos top) {
        for (BlockPos p : BlockPos.betweenClosed(top.offset(-1, -2, -1), top.offset(1, 0, 1)))
            if (level.getBlockState(p).is(dev.denfry.promptcritical.mod.registry.PcBlocks.CEMS_ANALYZER.get())) return true;
        return false;
    }

    /**
     * Выброс по массовым расходам, кг/с, для установок без модели горения (§14.1.3): SO₂, NOx, PM идут в поле,
     * всё — в учёт.
     */
    public static void emit(ServerLevel level, BlockPos top, double heightM, double exitK, double flueKgS, double so2KgS, double noxKgS,
            double pmKgS, double co2KgS, double ch4KgS, String id, EmissionLedger.Category category) {
        CombustionResult r = new CombustionResult();
        r.fluegasKgS = Math.max(1e-3, flueKgS);
        r.so2KgS = so2KgS;
        r.noxKgS = noxKgS;
        r.pmKgS = pmKgS;
        r.co2KgS = co2KgS;
        r.ch4KgS = ch4KgS;
        emit(level, top, heightM, exitK, r, id, category);
    }

    /**
     * Сжигание горючего газа по тепловой мощности, Вт: CO₂ 56,1 кг/ГДж (IPCC 2006, природный газ), PM 0,0009 кг/ГДж,
     * NOx — по типу горелки, кг/ГДж (EMEP/EEA 2019: котлы и печи ≈ 0,05–0,09, ГТУ с диффузионными камерами ≈ 0,2,
     * факел 0,03 по AP-42 13.5); недожог {@code slip} уходит метаном.
     */
    public static void emitGasFiring(ServerLevel level, BlockPos top, double heightM, double exitK, double heatW, double flueKgS,
            double noxKgPerGJ, double slip, String id, EmissionLedger.Category category) {
        if (!(heatW > 0)) return;
        double gj = heatW * 1e-9;
        double ch4 = slip > 0 ? heatW / 50e6 * slip : 0; // метан: 50 МДж/кг
        emit(level, top, heightM, exitK, flueKgS, 0, noxKgPerGJ * gj, 0.0009 * gj, 56.1 * gj * (1 - slip), ch4, id, category);
    }

    public static EmissionLedger ledger(ServerLevel level) {
        return LEDGERS.computeIfAbsent(level, l -> new EmissionLedger());
    }

    /** Приземная концентрация в чанке, мкг/м³: SO₂, NOx, PM. */
    public static double[] airUgM3(ServerLevel level, BlockPos pos) {
        Map<Long, double[]> a = AIR.get(level);
        double[] c = a == null ? null : a.get(PollutionField.key(pos.getX() >> 4, pos.getZ() >> 4));
        return c == null ? new double[3] : new double[] {c[0] * 1e9, c[1] * 1e9, c[2] * 1e9};
    }

    /** Осаждённая нагрузка чанка, мг/м²: S, N, PM. */
    public static double[] loadMgM2(ServerLevel level, BlockPos pos) {
        return field(level).field.load(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static Data field(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), PromptCritical.MOD_ID + "_pollution");
    }

    private static void step(ServerLevel level) {
        long now = level.getGameTime();
        Map<BlockPos, Source> src = SOURCES.getOrDefault(level, Map.of());
        src.values().removeIf(s -> s.until < now);
        Map<Long, double[]> air = new HashMap<>();
        var w = EnvironmentService.sample(level, level.getSharedSpawnPos(), 10);
        double wind = Math.max(1, w.meanWindMs()), dir = w.windDirRad(), ambientK = w.airTempC() + 273.15;
        double ux = Math.cos(dir), uz = Math.sin(dir);
        for (Source s : src.values()) {
            double rho = 353 / Math.max(300, s.exitK);
            double d = Math.sqrt(4 * s.flueKgS / rho / (Math.PI * EXIT_VELOCITY_MS));
            double he = s.heightM + Pollution.plumeRise(Pollution.buoyancyFlux(EXIT_VELOCITY_MS, d, s.exitK, ambientK), wind);
            int scx = s.top.getX() >> 4, scz = s.top.getZ() >> 4;
            for (int dx = -KERNEL_RADIUS; dx <= KERNEL_RADIUS; dx++)
                for (int dz = -KERNEL_RADIUS; dz <= KERNEL_RADIUS; dz++) {
                    double gx = dx * 16.0, gz = dz * 16.0;
                    // по ветру и поперёк, в физических метрах
                    double x = (gx * ux + gz * uz) / DISTANCE_SCALE, y = (-gx * uz + gz * ux) / DISTANCE_SCALE;
                    if (x <= 1) continue;
                    double k = Pollution.groundConcentration(1, wind, he, x, y);
                    if (k <= 0) continue;
                    double[] c = air.computeIfAbsent(PollutionField.key(scx + dx, scz + dz), kk -> new double[3]);
                    for (int i = 0; i < 3; i++) c[i] += s.qKgS[i] * k;
                }
        }
        AIR.put(level, air);
        Data data = field(level);
        data.field.step(STEP_PHYS_S, new PollutionField.Air() {
            @Override
            public double concentration(int cx, int cz, int s) {
                double[] c = air.get(PollutionField.key(cx, cz));
                return c == null ? 0 : c[s];
            }

            @Override
            public double vegetation(int cx, int cz) {
                return 0.5;
            }
        }, new ArrayList<>(air.keySet()), level.isRaining(), dir);
        data.setDirty();
        effects(level, air, data.field);
    }

    private static boolean protectedHead(ServerPlayer p) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getItemBySlot(EquipmentSlot.HEAD).getItem()).getPath();
        return id.contains("respirator") || id.contains("gas_mask") || id.contains("scba");
    }

    private static void effects(ServerLevel level, Map<Long, double[]> air, PollutionField field) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            int cx = p.blockPosition().getX() >> 4, cz = p.blockPosition().getZ() >> 4;
            double[] c = air.getOrDefault(PollutionField.key(cx, cz), new double[3]);
            double so2 = c[0] * 1e9, no2 = c[1] * 1e9, pm = c[2] * 1e9;
            double[] d = field.load(cx, cz);
            double phi = Pollution.acidIndex(d[0], d[1]);
            double ph = Pollution.rainPh(so2, no2);
            Pollution.Level lv = Pollution.level(pm, so2, so2, phi, level.isRaining() && level.canSeeSky(p.blockPosition()), ph);
            boolean safe = protectedHead(p) || p.isCreative();
            if (!safe && lv == Pollution.Level.COUGH) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STEP_TICKS, 0, false, true));
            if (!safe && lv == Pollution.Level.WEAKNESS) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STEP_TICKS, 1, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STEP_TICKS, 0, false, true));
            }
            if (SHOWN.get(p) == lv) continue;
            SHOWN.put(p, lv);
            String f0 = String.format(java.util.Locale.ROOT, "%.0f", so2), f1 = String.format(java.util.Locale.ROOT, "%.0f", pm);
            Component m = switch (lv) {
                case HAZE -> msg("haze", f1);
                case SMOG -> msg("smog", f0, f1);
                case WITHERING -> msg("withering", String.format(java.util.Locale.ROOT, "%.1f", phi));
                case COUGH, WEAKNESS -> msg("cough", f0, f1).withStyle(ChatFormatting.YELLOW);
                case ACID_RAIN -> msg("acid_rain", String.format(java.util.Locale.ROOT, "%.1f", ph)).withStyle(ChatFormatting.YELLOW);
                case CLEAN -> null;
            };
            if (m != null) p.displayClientMessage(m, true);
        }
    }

    /** Поле слоя B измерения. */
    static final class Data extends SavedData {
        final PollutionField field = new PollutionField();

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            ListTag list = tag.getList("cells", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                long k = c.getLong("k");
                d.field.set(PollutionField.cx(k), PollutionField.cz(k), new double[] {c.getDouble("s"), c.getDouble("n"), c.getDouble("pm")});
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            Set<Long> seen = new HashSet<>();
            for (Map.Entry<Long, double[]> e : field.view().entrySet()) {
                if (!seen.add(e.getKey())) continue;
                CompoundTag c = new CompoundTag();
                c.putLong("k", e.getKey());
                c.putDouble("s", e.getValue()[0]);
                c.putDouble("n", e.getValue()[1]);
                c.putDouble("pm", e.getValue()[2]);
                list.add(c);
            }
            tag.put("cells", list);
            return tag;
        }
    }

    /** Для тестов: число активных источников. */
    public static int activeSources(ServerLevel level) {
        return SOURCES.getOrDefault(level, Map.of()).size();
    }

    static List<BlockPos> sourcePositions(ServerLevel level) {
        return new ArrayList<>(SOURCES.getOrDefault(level, Map.of()).keySet());
    }
}
