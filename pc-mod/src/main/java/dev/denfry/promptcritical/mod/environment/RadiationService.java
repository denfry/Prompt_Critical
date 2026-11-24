package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.radiation.DoseRate;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import dev.denfry.promptcritical.sim.radiation.RadiationSickness;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Ионизирующее излучение в мире: точечные источники γ и нейтронов (работающий котёл, остаточное тепловыделение,
 * отработанный материал) и вспышки (СЦР). Раз в секунду для каждого игрока считается мощность дозы от всех источников
 * в {@value #RANGE_M} м с ослаблением блоками на прямой ({@link ShieldBlocks}, {@link DoseRate}), плюс природный фон места ({@link BackgroundField}; запасное значение — {@value #BACKGROUND_USV_H} мкЗв/ч). Доза копится в данных игрока: полная (для дозиметра) и острая — она спадает
 * с периодом {@value RadiationSickness#RECOVERY_HALF_DAYS} игровых суток и задаёт лучевую болезнь
 * ({@link RadiationSickness}). Зиверт для γ и нейтронов приравнен к грею (взвешивающие коэффициенты уже в коэффициенте
 * перевода нейтронов).
 */
public final class RadiationService {
    public static final double RANGE_M = 200, BACKGROUND_USV_H = 0.1;
    public static final int HOLD_TICKS = 40;
    /** Игровые секунды за тик (тик — 3,6 с игрового времени): для восстановления. */
    static final double GAME_S_PER_TICK = 3.6;
    static final String TAG = PromptCritical.MOD_ID + ":dose";
    public static final ResourceKey<DamageType> RADIATION = ResourceKey.create(Registries.DAMAGE_TYPE, PromptCritical.id("radiation"));
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();
    private static final Map<ServerLevel, Map<Vec3, Held>> SOURCES = new WeakHashMap<>();
    /** Предметы с активностью (инвентари игроков, лежащие на земле): пересобираются раз в секунду. */
    private static final Map<ServerLevel, List<Positioned>> ITEMS = new WeakHashMap<>();
    /** Радиус поиска лежащих предметов вокруг игрока, м. */
    static final double ITEM_SCAN_M = 64;
    /**
     * Пропускание контейнера гамма-дефектоскопа (обеднённый уран, S-образный канал): 3,7 ТБк Ir-192 дают на 1 м около
     * 20 мкЗв/ч — порядок паспортных пределов (ГОСТ 23764: не больше 0,03 мЗв/ч на 1 м).
     */
    public static final double CONTAINER_TRANSMISSION = 5e-5;

    private record Positioned(Vec3 at, PointSource source, long until) {
    }

    /** Выброс радионуклида в атмосферу из трубы: активность в секунду. */
    private record Release(dev.denfry.promptcritical.sim.radiation.Emitter nuclide, double bqS, long until) {
    }

    private static final Map<ServerLevel, Map<Vec3, java.util.List<Release>>> RELEASES = new WeakHashMap<>();

    private record Held(PointSource source, long until) {
    }

    static {
        text("mild", "Nausea and weakness: acute radiation sickness, grade I (over 1 Gy)",
                "Тошнота и слабость: острая лучевая болезнь I степени (больше 1 Гр)");
        text("moderate", "Vomiting, fever: acute radiation sickness, grade II (over 2 Gy)",
                "Рвота, жар: острая лучевая болезнь II степени (больше 2 Гр)");
        text("severe", "Bone marrow failure: acute radiation sickness, grade III (over 4 Gy)",
                "Костный мозг не работает: острая лучевая болезнь III степени (больше 4 Гр)");
        text("extreme", "Lethal dose: acute radiation sickness, grade IV (over 6 Gy)",
                "Смертельная доза: острая лучевая болезнь IV степени (больше 6 Гр)");
        text("source_orphan", "ORPHAN SOURCE %s: unseen too long, last at %s %s %s. It keeps radiating there until found",
                "БЕСХОЗНЫЙ ИСТОЧНИК %s: давно не виден, последний раз — %s %s %s. Он излучает оттуда, пока его не найдут");
        text("source_destroyed", "Tracked source %s destroyed at %s %s %s: entered in the log",
                "Учтённый источник %s уничтожен в точке %s %s %s: запись в журнале");
        text("burst", "BLUE FLASH: criticality excursion. Your dose: %s Sv", "ГОЛУБАЯ ВСПЫШКА: СЦР. Ваша доза: %s Зв");
        LANG.put("death.attack.promptcritical.radiation", new String[] {"%1$s died of radiation sickness", "%1$s умер от лучевой болезни"});
        LANG.put("death.attack.promptcritical.radiation.player", new String[] {"%1$s died of radiation sickness while fighting %2$s",
                "%1$s умер от лучевой болезни, спасаясь от %2$s"});
    }

    private RadiationService() {
    }

    private static void text(String key, String en, String ru) {
        LANG.put("message.promptcritical.radiation." + key, new String[] {en, ru});
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable("message.promptcritical.radiation." + key, args);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel s) tick(s);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            BackgroundField.forget(e.getEntity().getUUID());
            RadonField.forget(e.getEntity().getUUID());
            IODINE.remove(e.getEntity().getUUID());
        });
        // учёт доз за жизнь и за год и официальная доза ТЛД переживают смерть (04b §4b.6.1: copyOnDeath); острая — нет
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> {
            if (!e.isWasDeath()) return;
            CompoundTag old = e.getOriginal().getPersistentData().getCompound(TAG), now = new CompoundTag();
            for (String k : new String[] {LEDGER, OFFICIAL, "ki"}) if (old.contains(k)) now.put(k, old.get(k).copy());
            if (old.contains("total")) now.putDouble("total", old.getDouble("total"));
            if (old.contains("internal")) now.putDouble("internal", old.getDouble("internal"));
            e.getEntity().getPersistentData().put(TAG, now);
        });
    }

    /** Непрерывный источник в точке; источник обновляет его хотя бы раз в {@value #HOLD_TICKS} тиков. */
    public static void source(ServerLevel level, Vec3 at, PointSource src) {
        if (src.empty()) return;
        SOURCES.computeIfAbsent(level, l -> new HashMap<>()).put(at, new Held(src, level.getGameTime() + HOLD_TICKS));
    }

    /**
     * Снять непрерывный источник в точке {@code at} сразу, не дожидаясь {@value #HOLD_TICKS} тиков: источник, который
     * переехал (сместился центр масс загрязнения), иначе ещё светил бы и со старого места.
     */
    public static void removeSource(ServerLevel level, Vec3 at) {
        Map<Vec3, Held> m = SOURCES.get(level);
        if (m != null) m.remove(at);
    }

    /**
     * Вспышка: полное число частиц; доза достаётся всем игрокам в радиусе сразу, с ослаблением стенами.
     *
     * @param announce сообщить игроку дозу общим текстом (иначе сообщает вызывающий по возвращённым дозам)
     * @return доза каждого облучённого игрока, Зв
     */
    public static Map<ServerPlayer, Double> burst(ServerLevel level, Vec3 at, PointSource burst, boolean announce) {
        Map<ServerPlayer, Double> doses = new LinkedHashMap<>();
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(at) > RANGE_M * RANGE_M || p.isSpectator()) continue;
            Vec3 eye = p.getEyePosition();
            double sv = DoseRate.burstSv(burst, eye.distanceTo(at), ShieldBlocks.between(level, at, eye));
            doses.put(p, sv);
            if (p.isCreative()) continue;
            add(p, sv);
            if (announce) p.displayClientMessage(msg("burst", String.format(java.util.Locale.ROOT, "%.2f", sv)).withStyle(ChatFormatting.RED), false);
            effects(p, level);
        }
        return doses;
    }

    /** Держится ли сейчас непрерывный источник в точке {@code at} (не просрочен). */
    public static boolean holds(ServerLevel level, Vec3 at) {
        Map<Vec3, Held> m = SOURCES.get(level);
        Held h = m == null ? null : m.get(at);
        return h != null && h.until >= level.getGameTime();
    }

    /** Мощность дозы в точке от всех источников, Зв/ч (без фона). */
    public static double rateSvH(ServerLevel level, Vec3 at) {
        return rateSvH(level, at, e -> 1);
    }

    /** То же для человека в носимой защите с пропусканием {@code worn(E)} (свинцовый фартук, 04b §4b.7.3). */
    public static double rateSvH(ServerLevel level, Vec3 at, java.util.function.DoubleUnaryOperator worn) {
        Map<Vec3, Held> m = SOURCES.get(level);
        double sum = 0;
        long now = level.getGameTime();
        for (Map.Entry<Vec3, Held> e : m == null ? Map.<Vec3, Held>of().entrySet() : m.entrySet()) {
            if (e.getValue().until < now) continue; // просрочен: не зависим от того, успел ли тик уровня его вычистить
            Vec3 src = e.getKey();
            double r = src.distanceTo(at);
            if (r > RANGE_M) continue;
            List<DoseRate.Layer> layers = ShieldBlocks.between(level, src, at);
            sum += DoseRate.totalSvH(e.getValue().source, r, layers, worn);
        }
        List<Positioned> items = ITEMS.get(level);
        if (items != null)
            for (Positioned it : items) {
                if (it.until < now) continue;
                double r = it.at.distanceTo(at);
                if (r > RANGE_M) continue;
                sum += DoseRate.totalSvH(it.source, r, ShieldBlocks.between(level, it.at, at), worn);
            }
        // предметы в контейнерах (R55): свой экран контейнера стоит у источника, дальше слои блоков на прямой
        for (ContainerActivityScan.ContainerSource c : ContainerActivityScan.sources(level)) {
            double r = c.at().distanceTo(at);
            if (r > RANGE_M) continue;
            List<DoseRate.Layer> layers = new java.util.ArrayList<>(c.extraShield());
            layers.addAll(ShieldBlocks.between(level, c.at(), at));
            sum += DoseRate.totalSvH(c.source(), r, layers, worn);
        }
        return sum;
    }

    /**
     * Непрерывный выброс радионуклида в атмосферу, Бк/с (труба радиохимического завода): облако по ветру считается
     * гауссовой моделью ({@link dev.denfry.promptcritical.sim.consequence.GasDispersion}), доза — погружение и ингаляция
     * ({@link dev.denfry.promptcritical.sim.radiation.AirborneDose}). Источник обновляет выброс каждую секунду.
     */
    public static void airRelease(ServerLevel level, Vec3 stack, dev.denfry.promptcritical.sim.radiation.Emitter nuclide, double bqS) {
        if (bqS <= 0) return;
        // вытяжка зала со спецфильтрами (03a §3a.9.3): выброс в зале уходит через фильтры и выходит с верха трубы
        var vent = dev.denfry.promptcritical.mod.safety.VentStackBlockEntity.capture(level, stack);
        if (vent != null) {
            bqS = vent.filter(nuclide, bqS);
            stack = vent.releasePoint();
            if (bqS <= 0) return;
        }
        var list = RELEASES.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(stack, k -> new java.util.ArrayList<>());
        long now = level.getGameTime();
        // новый эпизод выброса — для API аддонов; дальше источник лишь продлевает его раз в секунду
        boolean fresh = list.stream().noneMatch(r -> r.nuclide == nuclide && r.until >= now);
        list.removeIf(r -> r.nuclide == nuclide);
        list.add(new Release(nuclide, bqS, now + HOLD_TICKS));
        if (fresh) dev.denfry.promptcritical.mod.api.ApiEvents.release(level, stack, nuclide.name(), bqS);
    }

    /** Действующий выброс нуклида {@code nuclide} из точки {@code stack}, Бк/с; 0 — нет. */
    public static double airReleaseBqS(ServerLevel level, Vec3 stack, dev.denfry.promptcritical.sim.radiation.Emitter nuclide) {
        var m = RELEASES.get(level);
        var list = m == null ? null : m.get(stack);
        if (list == null) return 0;
        for (Release r : list) if (r.nuclide == nuclide && r.until >= level.getGameTime()) return r.bqS;
        return 0;
    }

    /** Мощность дозы от облаков выбросов в точке, Зв/ч. */
    public static double cloudSvH(ServerLevel level, Vec3 at) {
        return cloudSvH(level, at, 1);
    }

    /** То же для дышащего через маску: под неё попадает доля {@code inhaledFraction} аэрозоля (13 §13.7). */
    public static double cloudSvH(ServerLevel level, Vec3 at, double inhaledFraction) {
        Map<Vec3, java.util.List<Release>> m = RELEASES.get(level);
        if (m == null) return 0;
        long now = level.getGameTime();
        double sum = 0;
        for (Map.Entry<Vec3, java.util.List<Release>> e : m.entrySet()) {
            Vec3 src = e.getKey();
            double dx = at.x - src.x, dz = at.z - src.z;
            if (dx * dx + dz * dz > 4 * RANGE_M * RANGE_M) continue;
            var w = EnvironmentService.sample(level, net.minecraft.core.BlockPos.containing(src), 10);
            var st = dev.denfry.promptcritical.sim.consequence.GasDispersion.stability(w.meanWindMs(), w.solarWm2());
            double[] f = dev.denfry.promptcritical.sim.consequence.GasDispersion.windFrame(dx, dz, w.windDirRad());
            for (Release r : e.getValue()) {
                if (r.until < now) continue;
                double bqM3 = dev.denfry.promptcritical.sim.consequence.GasDispersion.plumeKgM3(r.bqS, w.meanWindMs(), st, f[0], f[1]);
                sum += dev.denfry.promptcritical.sim.radiation.AirborneDose.svH(r.nuclide, bqM3, inhaledFraction);
            }
        }
        return sum;
    }

    /**
     * Облака выбросов у человека в маске {@code mask} (04b §4b.7.3, §4b.8): [0] — погружение в облако, Зв/ч;
     * [1] — вдох аэрозолей под маску, Зв/ч; [2] — вдох I-131 под маску, Зв/ч (без йодной профилактики);
     * [3] — объёмная активность I-131, Бк/м³ (по ней расходуется йодный патрон).
     */
    public static double[] cloudParts(ServerLevel level, Vec3 at, dev.denfry.promptcritical.sim.radiation.RadRespirator mask) {
        double[] out = new double[4];
        Map<Vec3, java.util.List<Release>> m = RELEASES.get(level);
        if (m == null) return out;
        long now = level.getGameTime();
        for (Map.Entry<Vec3, java.util.List<Release>> e : m.entrySet()) {
            Vec3 src = e.getKey();
            double dx = at.x - src.x, dz = at.z - src.z;
            if (dx * dx + dz * dz > 4 * RANGE_M * RANGE_M) continue;
            var w = EnvironmentService.sample(level, net.minecraft.core.BlockPos.containing(src), 10);
            var st = dev.denfry.promptcritical.sim.consequence.GasDispersion.stability(w.meanWindMs(), w.solarWm2());
            double[] f = dev.denfry.promptcritical.sim.consequence.GasDispersion.windFrame(dx, dz, w.windDirRad());
            for (Release r : e.getValue()) {
                if (r.until < now) continue;
                double bqM3 = dev.denfry.promptcritical.sim.consequence.GasDispersion.plumeKgM3(r.bqS, w.meanWindMs(), st, f[0], f[1]);
                double outside = dev.denfry.promptcritical.sim.radiation.AirborneDose.svH(r.nuclide, bqM3, 0);
                double inhaled = dev.denfry.promptcritical.sim.radiation.AirborneDose.svH(r.nuclide, bqM3, mask.inhaledFraction(r.nuclide)) - outside;
                out[0] += outside;
                if (r.nuclide == dev.denfry.promptcritical.sim.radiation.Emitter.I131) {
                    out[2] += inhaled;
                    out[3] += bqM3;
                } else out[1] += inhaled;
            }
        }
        return out;
    }

    /** Источник от предмета с активностью на игровое время {@code tick}; {@code null} — у предмета её нет. */
    @org.jetbrains.annotations.Nullable
    public static PointSource itemSource(net.minecraft.world.item.ItemStack stack, long tick) {
        ActivityData a = stack.get(ActivityData.ACTIVITY.get());
        ActivityData shielded = stack.get(dev.denfry.promptcritical.mod.isotopes.IsotopeComponents.SHIELDED_SOURCE.get());
        if (a == null && shielded == null) return null;
        PointSource one = a == null ? PointSource.NONE : a.inventory(tick).source();
        // источник в защитном контейнере (дефектоскоп из обеднённого урана): на поверхности — единицы мЗв/ч
        if (shielded != null) one = one.plus(shielded.inventory(tick).source().scaled(CONTAINER_TRANSMISSION));
        return stack.getCount() == 1 ? one : one.scaled(stack.getCount());
    }

    /** Всё, что игрок несёт в инвентаре, руке, броне и слотах снаряжения, как один источник на игровое время {@code tick}. */
    public static PointSource carried(net.minecraft.world.entity.player.Player p, long tick) {
        PointSource carried = PointSource.NONE;
        for (net.minecraft.world.item.ItemStack st : p.getInventory().items) carried = plus(carried, itemSource(st, tick));
        for (net.minecraft.world.item.ItemStack st : p.getInventory().offhand) carried = plus(carried, itemSource(st, tick));
        for (net.minecraft.world.item.ItemStack st : p.getInventory().armor) carried = plus(carried, itemSource(st, tick));
        // слоты Curios и Accessories (16 §16.8.2, §16.9)
        for (net.minecraft.world.item.ItemStack st : dev.denfry.promptcritical.mod.compat.WornGear.accessories(p))
            carried = plus(carried, itemSource(st, tick));
        return carried;
    }

    /** Пересобрать предметные источники: инвентари игроков и предметы на земле рядом с ними. */
    private static void gatherItems(ServerLevel level, long now) {
        List<Positioned> out = new java.util.ArrayList<>();
        java.util.Set<net.minecraft.world.entity.item.ItemEntity> seen = new java.util.HashSet<>();
        for (ServerPlayer p : level.players()) {
            SourceLedger.sightCarried(level, p);
            PointSource carried = carried(p, now);
            if (!carried.empty()) out.add(new Positioned(p.position().add(0, 0.9, 0), carried, now + HOLD_TICKS));
            for (net.minecraft.world.entity.item.ItemEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    p.getBoundingBox().inflate(ITEM_SCAN_M))) {
                if (!seen.add(e)) continue;
                SourceLedger.sightOnGround(level, e);
                PointSource s = itemSource(e.getItem(), now);
                if (s != null && !s.empty()) out.add(new Positioned(e.position().add(0, 0.2, 0), s, now + HOLD_TICKS));
            }
        }
        ITEMS.put(level, out);
    }

    private static PointSource plus(PointSource a, @org.jetbrains.annotations.Nullable PointSource b) {
        return b == null ? a : a.plus(b);
    }

    /** Полная накопленная доза игрока, Зв. */
    public static double totalSv(ServerPlayer p) {
        return data(p).getDouble("total");
    }

    /** Острая доза игрока, Зв (Гр). */
    public static double acuteSv(ServerPlayer p) {
        return data(p).getDouble("acute");
    }

    /** Мощность дозы у игрока на последнем пересчёте, Зв/ч (с фоном). */
    public static double lastRateSvH(ServerPlayer p) {
        return data(p).getDouble("rate");
    }

    /** Мощность дозы внешнего облучения у игрока на последнем пересчёте (то, что меряют ЭПД, ТЛД и счётчик), Зв/ч. */
    public static double externalRateSvH(ServerPlayer p) {
        return data(p).getDouble("ext");
    }

    /** Игровые сутки мира (по времени верхнего мира): шкала годового учёта. */
    public static long day(ServerPlayer p) {
        return p.server.overworld().getGameTime() / 24000;
    }

    /** Физические часы мира: шкала йодной профилактики (игровые сутки = 24 физ. ч, R9). */
    static double hour(ServerPlayer p) {
        return p.server.overworld().getGameTime() * GAME_S_PER_TICK / 3600;
    }

    static final String LEDGER = "ledger", OFFICIAL = "official";

    private static dev.denfry.promptcritical.sim.radiation.DoseLedger ledger(CompoundTag d, String key) {
        if (!d.contains(key)) return new dev.denfry.promptcritical.sim.radiation.DoseLedger();
        CompoundTag t = d.getCompound(key);
        var list = t.getList("b", net.minecraft.nbt.Tag.TAG_DOUBLE);
        double[] b = new double[dev.denfry.promptcritical.sim.radiation.DoseLedger.BUCKETS];
        for (int i = 0; i < Math.min(b.length, list.size()); i++) b[i] = list.getDouble(i);
        return new dev.denfry.promptcritical.sim.radiation.DoseLedger(b, t.getLong("last"), t.getDouble("life"));
    }

    private static void store(CompoundTag d, String key, dev.denfry.promptcritical.sim.radiation.DoseLedger l) {
        CompoundTag t = new CompoundTag();
        var list = new net.minecraft.nbt.ListTag();
        for (double v : l.buckets()) list.add(net.minecraft.nbt.DoubleTag.valueOf(v));
        t.put("b", list);
        t.putLong("last", l.lastBucket());
        t.putDouble("life", l.lifetimeSv());
        d.put(key, t);
    }

    /** Доза игрока за последние 365 игровых суток (внешняя и ожидаемая внутренняя), Зв. */
    public static double annualSv(ServerPlayer p) {
        return ledger(data(p), LEDGER).annualSv(day(p));
    }

    /** «Официальная» доза за год — сумма считанных ТЛД (04b §4b.7.1), Зв. */
    public static double officialAnnualSv(ServerPlayer p) {
        return ledger(data(p), OFFICIAL).annualSv(day(p));
    }

    /** Записать считанную с ТЛД дозу в официальный учёт. */
    public static void recordOfficial(ServerPlayer p, double sv) {
        CompoundTag d = data(p);
        var l = ledger(d, OFFICIAL);
        l.add(day(p), sv);
        store(d, OFFICIAL, l);
    }

    private static void ledgerAdd(ServerPlayer p, double sv) {
        CompoundTag d = data(p);
        var l = ledger(d, LEDGER);
        l.add(day(p), sv);
        store(d, LEDGER, l);
    }

    private static final Map<java.util.UUID, dev.denfry.promptcritical.sim.radiation.IodineProphylaxis> IODINE = new HashMap<>();

    private static dev.denfry.promptcritical.sim.radiation.IodineProphylaxis iodine(ServerPlayer p) {
        return IODINE.computeIfAbsent(p.getUUID(), k -> {
            CompoundTag d = data(p);
            return d.contains("ki") ? dev.denfry.promptcritical.sim.radiation.IodineProphylaxis.restored(d.getDouble("ki")) : new dev.denfry.promptcritical.sim.radiation.IodineProphylaxis();
        });
    }

    /**
     * Игрок выпил таблетку KI (04b §4b.8.3): блокирование будущих поступлений I-131 на сутки и возврат части дозы от
     * поступлений последних суток (из дозы за жизнь и внутренней; годовой учёт ведётся по корзинам и не уменьшается).
     *
     * @return возвращённая ожидаемая доза, Зв
     */
    public static double takeIodide(ServerPlayer p) {
        double refund = iodine(p).takeTablet(hour(p));
        CompoundTag d = data(p);
        d.putDouble("ki", hour(p));
        if (refund > 0) {
            d.putDouble("total", Math.max(0, d.getDouble("total") - refund));
            d.putDouble("internal", Math.max(0, d.getDouble("internal") - refund));
        }
        return refund;
    }

    /** Действует ли сейчас таблетка KI. */
    public static boolean iodideActive(ServerPlayer p) {
        return iodine(p).active(hour(p));
    }

    private static CompoundTag data(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(TAG)) root.put(TAG, new CompoundTag());
        return root.getCompound(TAG);
    }

    /**
     * Ожидаемая доза внутреннего облучения (вдох, заглатывание), Зв: идёт в полную дозу, как в учёте доз персонала, но
     * не в острую — радионуклид внутри облучает годами, лучевой болезни от таких поступлений нет.
     */
    public static void addCommitted(ServerPlayer p, double sv) {
        if (!(sv > 0)) return;
        CompoundTag d = data(p);
        d.putDouble("total", d.getDouble("total") + sv);
        d.putDouble("internal", d.getDouble("internal") + sv);
        ledgerAdd(p, sv);
    }

    /** Накопленная ожидаемая доза внутреннего облучения, Зв. */
    public static double internalSv(ServerPlayer p) {
        return data(p).getDouble("internal");
    }

    static void add(ServerPlayer p, double sv) {
        CompoundTag d = data(p);
        d.putDouble("total", d.getDouble("total") + sv);
        d.putDouble("acute", d.getDouble("acute") + sv);
        ledgerAdd(p, sv);
    }

    private static void tick(ServerLevel level) {
        long now = level.getGameTime();
        Map<Vec3, Held> m = SOURCES.get(level);
        if (m != null) m.values().removeIf(h -> h.until < now);
        Map<Vec3, java.util.List<Release>> rel = RELEASES.get(level);
        if (rel != null) {
            rel.values().forEach(l -> l.removeIf(r -> r.until < now));
            rel.values().removeIf(java.util.List::isEmpty);
        }
        ContainerActivityScan.tick(level);
        if (now % 20 != 0) return;
        gatherItems(level, now);
        SourceLedger.tick(level, now);
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && RadonField.due(now, p)) RadonField.refresh(level, p); // наблюдатель проходит сквозь стены: заливку не тратим
            // внешнее облучение — за фартуком, если он надет; вдох облака — под маску по нуклиду (йод отдельно, 04b §4b.7.3)
            var mask = dev.denfry.promptcritical.mod.radiation.RadGear.respirator(p);
            double[] cloud = cloudParts(level, p.getEyePosition(), mask);
            double ext = rateSvH(level, p.getEyePosition(), dev.denfry.promptcritical.mod.radiation.RadGear.armorTransmission(p)) + cloud[0] + BackgroundField.ambientSvH(level, p);
            double rate = ext + cloud[1] + RadonField.doseSvH(level, p);
            CompoundTag d = data(p);
            d.putDouble("rate", rate);
            d.putDouble("ext", ext);
            if (p.isCreative() || p.isSpectator()) continue;
            add(p, rate / 3600);
            // I-131: ожидаемая доза с йодной профилактикой; йодный патрон тратится, пока в воздухе есть йод
            if (cloud[2] > 0) addCommitted(p, iodine(p).intake(hour(p), cloud[2] / 3600));
            if (cloud[3] > 0) cartridge(p);
            d.putDouble("acute", RadiationSickness.recover(d.getDouble("acute"), 20 * GAME_S_PER_TICK / 86400));
            effects(p, level);
        }
    }

    /** Секунда йодного патрона полнолицевой маски в воздухе с I-131 (вставка каски хранится копией — пишем обратно). */
    private static void cartridge(ServerPlayer p) {
        net.minecraft.world.item.ItemStack m = dev.denfry.promptcritical.mod.radiation.RadGear.radMask(p);
        if (m == null || dev.denfry.promptcritical.mod.radiation.RadGear.cartridge(m) <= 0) return;
        double left = Math.max(0, dev.denfry.promptcritical.mod.radiation.RadGear.cartridge(m) - 20 * GAME_S_PER_TICK);
        m.set(dev.denfry.promptcritical.mod.radiation.RadGear.IODINE_CARTRIDGE.get(), left);
        net.minecraft.world.item.ItemStack head = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if (head != m) dev.denfry.promptcritical.mod.safety.SafetyComponents.setInsert(head, m);
        if (left == 0) p.displayClientMessage(dev.denfry.promptcritical.mod.radiation.RadGearText.msg("cartridge_spent").withStyle(ChatFormatting.GOLD), true);
    }

    /** Детерминированные эффекты по острой дозе; сообщение — при переходе на новую ступень. */
    private static void effects(ServerPlayer p, ServerLevel level) {
        CompoundTag d = data(p);
        double acute = d.getDouble("acute");
        RadiationSickness.Stage st = RadiationSickness.stage(acute);
        int shown = d.getInt("stage");
        if (st.ordinal() > shown) {
            String key = switch (st) {
                case MILD -> "mild";
                case MODERATE -> "moderate";
                case SEVERE -> "severe";
                case EXTREMELY_SEVERE -> "extreme";
                default -> null;
            };
            if (key != null) p.displayClientMessage(msg(key).withStyle(st.ordinal() >= 4 ? ChatFormatting.RED : ChatFormatting.GOLD), false);
        }
        d.putInt("stage", st.ordinal());
        if (st.ordinal() >= RadiationSickness.Stage.MILD.ordinal()) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, st.ordinal() >= 3 ? 1 : 0, false, false));
            if (level.random.nextInt(60) == 0) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, false, false));
        }
        if (st.ordinal() >= RadiationSickness.Stage.MODERATE.ordinal()) p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 1, false, false));
        if (st.ordinal() >= RadiationSickness.Stage.SEVERE.ordinal())
            p.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, st == RadiationSickness.Stage.EXTREMELY_SEVERE ? 1 : 0, false, false));
        // свыше 20 Гр — церебральная форма: смерть за минуты
        if (acute >= 20) {
            DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(RADIATION));
            p.hurt(src, Float.MAX_VALUE);
        }
    }
}
