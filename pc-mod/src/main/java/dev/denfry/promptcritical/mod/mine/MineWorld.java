package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.block.VentilationDoorBlock;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.item.MineInstrumentItem;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.registry.PcTags;
import dev.denfry.promptcritical.mod.worldgen.VeinFields;
import dev.denfry.promptcritical.sim.consequence.BlastScaling;
import dev.denfry.promptcritical.sim.consequence.DamageBudgetConfig;
import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.sim.consequence.DamageRequest;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.mining.ExposureState;
import dev.denfry.promptcritical.sim.mining.GasExposure;
import dev.denfry.promptcritical.sim.mining.IgnitionSource;
import dev.denfry.promptcritical.sim.mining.LampState;
import dev.denfry.promptcritical.sim.mining.MethaneField;
import dev.denfry.promptcritical.sim.mining.MineDrainage;
import dev.denfry.promptcritical.sim.mining.MineGas;
import dev.denfry.promptcritical.sim.mining.MineNetwork;
import dev.denfry.promptcritical.sim.mining.MineSubsystem;
import dev.denfry.promptcritical.sim.mining.MineSupport;
import dev.denfry.promptcritical.sim.mining.MineTopology;
import dev.denfry.promptcritical.sim.mining.MineVentilation;
import dev.denfry.promptcritical.sim.mining.MiningConfig;
import dev.denfry.promptcritical.sim.mining.RoofRock;
import dev.denfry.promptcritical.sim.mining.RoofSpanRule;
import dev.denfry.promptcritical.sim.mining.SafetyLamp;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * Шахты измерения в мире (08 §8.2): якоря, заливка и перестройка графа, устройства (вентилятор, двери, скважины),
 * физиология игроков и канареек, лампа и метанометр, осланцевание, фронт взрыва → заявки {@code DamageRequest} и
 * ударная волна, правило пролёта кровли. Только серверный поток: {@link #preTick} — до шага домена,
 * {@link #postTick} — после.
 */
public final class MineWorld {
    public static final int SECOND = 20;
    /** Полная перезаливка шахты, даже если событий не было (Create-буры и прочие изменения без событий). */
    public static final long RESCAN_PERIOD_TICKS = 1200;
    /** Каптаж дегазации, доля газовыделения узла (08 §8.2.4: 30–60 %). */
    public static final double DEGAS_CAPTURE = 0.45;
    /** Изолирующий самоспасатель, тиков (08 §8.2.6: 30–60 мин). */
    public static final long SELF_RESCUER_TICKS = 30 * 60 * 20;
    /** Радиус заявки на разрушение от узла взрыва, м (≈ половина ячейки узла). */
    public static final double BLAST_RADIUS_M = 3;
    /** Тип урона {@code promptcritical:mine_gas}: удушье рудничным газом. */
    public static final ResourceKey<DamageType> MINE_GAS = ResourceKey.create(Registries.DAMAGE_TYPE, PromptCritical.id("mine_gas"));

    private static final class Entry {
        MineScanner.Scan scan;
        BoundingBox bounds;
        boolean dirty = true;
        long due;
        long lastScan = Long.MIN_VALUE / 2;
        boolean warnedTruncated;
    }

    private static final class Breather {
        final GasExposure exposure;
        long isolatedUntil;

        Breather(double ratio) {
            exposure = GasExposure.human(ratio);
        }
    }

    private final SimLevelBinding binding;
    private final ServerLevel level;
    private final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();
    private final Long2LongOpenHashMap anchorMine = new Long2LongOpenHashMap();
    private final Map<UUID, Breather> breathers = new HashMap<>();
    private final Long2ObjectOpenHashMap<GasExposure> canaries = new Long2ObjectOpenHashMap<>();
    private final Long2DoubleOpenHashMap roofDue = new Long2DoubleOpenHashMap();
    private long requestSeq = 1L << 40;
    private int blastRequests;
    private boolean restored;

    public MineWorld(SimLevelBinding binding) {
        this.binding = binding;
        this.level = binding.level();
        anchorMine.defaultReturnValue(-1);
    }

    @Nullable
    public MineSubsystem sim() {
        return binding.world().subsystem(MineSubsystem.class);
    }

    private MineScanner scanner() {
        return new MineScanner(level, ServerConfig.MINING_MAX_MINE_BLOCKS.get(),
                ServerConfig.MINING_BARRIER_INERT_KG_PER_BLOCK.get(), ServerConfig.MINING_SEAM_GAS_CONTENT.get());
    }

    private Entry entry(MineNetwork n) {
        return entries.computeIfAbsent(n.id(), k -> new Entry());
    }

    /** Заливка шахты (для тестов и команд); {@code null} — шахта ещё не залита. */
    @Nullable
    public MineScanner.Scan scanOf(MineNetwork n) {
        Entry e = entries.get(n.id());
        return e == null ? null : e.scan;
    }

    // --- поиск ---------------------------------------------------------------------------------------------

    /** Шахта, которой принадлежит блок (воздух выработки или обнажённый уголь), или {@code null}. */
    @Nullable
    public MineNetwork mineAt(BlockPos pos) {
        MineSubsystem sim = sim();
        if (sim == null) return null;
        long key = pos.asLong();
        for (MineNetwork n : sim.networks()) if (n.nodeOf(key) > 0) return n;
        return null;
    }

    @Nullable
    private MineNetwork mineNear(BlockPos pos) {
        MineNetwork n = mineAt(pos);
        if (n != null) return n;
        for (Direction d : Direction.values()) if ((n = mineAt(pos.relative(d))) != null) return n;
        return null;
    }

    // --- тик -----------------------------------------------------------------------------------------------

    public void preTick(long gameTime) {
        MineSubsystem sim = sim();
        if (sim == null) return;
        if (!restored) {
            restored = true;
            reindexAnchors();
            for (MineNetwork n : sim.networks()) entry(n).due = gameTime;
        }
        for (MineNetwork n : new ArrayList<>(sim.networks())) {
            if (sim.network(n.id()) == null) continue;
            Entry e = entry(n);
            if (!(e.dirty && gameTime >= e.due || gameTime - e.lastScan >= RESCAN_PERIOD_TICKS)) continue;
            if (!loaded(n, e)) continue; // выработки в выгруженных чанках не перезаливаются: газ узлов сохраняется
            for (int pass = 0; pass < 4 && sim.network(n.id()) != null; pass++) {
                int anchors = n.anchors().length;
                rescan(sim, n, e, gameTime);
                if (sim.network(n.id()) == null || n.anchors().length == anchors) break; // заливка нашла новые якоря — ещё раз
            }
        }
        if (gameTime % SECOND == 0) for (MineNetwork n : sim.networks()) applyDevices(n, entries.get(n.id()));
    }

    public void postTick(long gameTime) {
        MineSubsystem sim = sim();
        if (sim == null) return;
        for (MineNetwork n : sim.networks()) n.drainBurned((node, origin, energy) -> onBurned(n, node, origin, energy));
        if (gameTime % SECOND == 0) {
            for (MineNetwork n : sim.networks()) {
                Entry e = entries.get(n.id());
                if (e != null) flood(n, e);
            }
            breathe(sim, gameTime);
            canaries();
            alarms(sim);
            roofTimers(sim);
        }
    }

    // --- якоря и перестройка -------------------------------------------------------------------------------

    /** Блок в позиции изменился (событие соседей, поставлен или снят блок мода). */
    public void onBlockChanged(BlockPos pos) {
        MineSubsystem sim = sim();
        if (sim == null) return;
        long key = pos.asLong(), now = level.getGameTime();
        for (MineNetwork n : sim.networks()) {
            Entry e = entries.get(n.id());
            if (e != null && e.bounds != null && e.bounds.isInside(pos)) markDirty(e, now);
        }
        BlockState s = level.getBlockState(pos);
        long mine = anchorMine.get(key);
        if (MineScanner.isAnchorBlock(s)) {
            if (mine < 0) addAnchor(sim, pos, now);
        } else if (mine >= 0 && !scanner().isMineAir(pos)) {
            MineNetwork n = sim.network(mine);
            if (n != null) {
                LongLinkedOpenHashSet a = new LongLinkedOpenHashSet(n.anchors());
                a.remove(key);
                n.setAnchors(a.toLongArray());
                markDirty(entry(n), now);
            }
            anchorMine.remove(key);
        }
    }

    private void addAnchor(MineSubsystem sim, BlockPos pos, long now) {
        long key = pos.asLong();
        MineNetwork owner = null;
        for (long c : cells(pos)) {
            BlockPos cp = BlockPos.of(c);
            owner = mineNear(cp);
            if (owner != null) break;
        }
        if (owner == null) {
            owner = sim.create(new long[] {key});
        } else {
            long[] a = owner.anchors();
            long[] b = java.util.Arrays.copyOf(a, a.length + 1);
            b[a.length] = key;
            owner.setAnchors(b);
        }
        anchorMine.put(key, owner.id());
        Entry e = entry(owner);
        e.dirty = true;
        e.due = now;
    }

    private long[] cells(BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof PcBlock pc) || pc.spec().footprint().isSingle()) return new long[] {pos.asLong()};
        LongArrayList out = new LongArrayList();
        for (Footprint.Cell c : pc.spec().footprint().cells(pos, pc.facing(s))) out.add(c.pos().asLong());
        return out.toLongArray();
    }

    private void markDirty(Entry e, long now) {
        if (e.dirty) return;
        e.dirty = true;
        e.due = now + 10 + (e.scan == null ? 0 : e.scan.airBlocks / 512);
    }

    /** Чанки выработок (или якорей, пока шахта не залита) загружены. */
    private boolean loaded(MineNetwork n, Entry e) {
        if (e.bounds != null && e.scan != null && e.scan.airBlocks > 0)
            return level.hasChunksAt(e.bounds.minX(), e.bounds.minY(), e.bounds.minZ(), e.bounds.maxX(), e.bounds.maxY(), e.bounds.maxZ());
        for (long a : n.anchors()) if (!level.isLoaded(BlockPos.of(a))) return false;
        return true;
    }

    private boolean validAnchor(MineScanner scanner, long key) {
        BlockPos p = BlockPos.of(key);
        if (!level.isLoaded(p)) return true;
        return MineScanner.isAnchorBlock(level.getBlockState(p)) || scanner.isMineAir(p);
    }

    private void rescan(MineSubsystem sim, MineNetwork n, Entry e, long now) {
        MineScanner scanner = scanner();
        MineScanner.Scan s = scanner.scan(n.anchors());
        LongLinkedOpenHashSet keep = new LongLinkedOpenHashSet(s.anchors);
        for (long a : n.anchors()) {
            if (s.topology.nodeOf(a) > 0 || keep.contains(a)) {
                keep.add(a);
            } else if (validAnchor(scanner, a)) {
                if (s.airBlocks == 0 || s.truncated) keep.add(a);
                else entry(sim.create(new long[] {a})).due = now; // выработка разделилась
            }
        }
        for (MineNetwork o : new ArrayList<>(sim.networks())) {
            if (o == n) continue;
            for (long a : o.anchors()) {
                if (!keep.contains(a)) continue;
                for (long k : o.anchors()) if (validAnchor(scanner, k)) keep.add(k);
                for (long k : o.workedRoof()) n.markWorkedRoof(k);
                sim.remove(o.id());
                entries.remove(o.id());
                break;
            }
        }
        if (keep.isEmpty()) {
            sim.remove(n.id());
            entries.remove(n.id());
            reindexAnchors();
            return;
        }
        n.setAnchors(keep.toLongArray());
        n.rebuild(s.topology, s.origins, s.flames, s.coalKeys, s.coalX, s.coalCount);
        e.scan = s;
        e.bounds = s.airBlocks > 0 ? s.bounds : null;
        for (long a : keep) {
            BlockPos p = BlockPos.of(a);
            BoundingBox ab = new BoundingBox(p).inflatedBy(2);
            e.bounds = e.bounds == null ? ab : BoundingBox.encapsulatingBoxes(List.of(e.bounds, ab)).orElse(ab);
        }
        e.dirty = false;
        e.lastScan = now;
        if (s.truncated && !e.warnedTruncated) {
            e.warnedTruncated = true;
            PromptCritical.LOGGER.warn("[{}] mine {} traced {} blocks, stopped at the limit or an unloaded chunk ({}); the rest is closed off",
                    level.dimension().location(), n.id(), s.airBlocks, BlockPos.of(s.firstUnloaded).toShortString());
        }
        reindexAnchors();
        applyDevices(n, e);
        evaluateRoof(sim, n);
    }

    private void reindexAnchors() {
        anchorMine.clear();
        MineSubsystem sim = sim();
        if (sim == null) return;
        for (MineNetwork n : sim.networks()) for (long a : n.anchors()) anchorMine.put(a, n.id());
    }

    // --- устройства ----------------------------------------------------------------------------------------

    private void applyDevices(MineNetwork n, @Nullable Entry e) {
        if (e == null || e.scan == null || !n.ready()) return;
        MineScanner.Scan s = e.scan;
        MineVentilation v = n.ventilation();
        long tick = binding.world().clock().tick();
        for (int f = 0; f < s.fanCtrl.length; f++) {
            BlockPos p = BlockPos.of(s.fanCtrl[f]);
            BlockState st = level.getBlockState(p);
            if (!st.is(PcBlocks.MAIN_MINE_FAN.get()) || !(level.getBlockEntity(p) instanceof MainMineFanBlockEntity fan)) continue;
            fan.setPowerW(v.fanPowerW(f));                           // по прошлому решению: N = hQ/η при старой скорости
            double rpm = fan.fanRpm(tick);
            v.setFanSpeed(f, rpm);
            boolean on = rpm > 0;
            if (st.getValue(PcProperties.RUNNING) != on) level.setBlock(p, st.setValue(PcProperties.RUNNING, on), Block.UPDATE_CLIENTS);
        }
        for (int d = 0; d < s.doorCtrl.length; d++) {
            BlockState st = level.getBlockState(BlockPos.of(s.doorCtrl[d]));
            if (!st.is(PcBlocks.VENTILATION_DOOR.get())) continue;
            if (st.getValue(PcProperties.DOOR_KIND) == PcProperties.DoorKind.REGULATOR) {
                v.setDoorClosed(d, true);
                v.setRegulatorArea(d, s.doorSection[d] * VentilationDoorBlock.windowFraction(st));
            } else {
                v.setDoorClosed(d, !st.getValue(BlockStateProperties.OPEN));
            }
        }
        for (var b : s.boreholeNode.long2IntEntrySet()) {
            BlockPos bore = BlockPos.of(b.getLongKey());
            double capture = 0;
            for (Direction d : Direction.values())
                if (level.getBlockEntity(bore.relative(d)) instanceof DegasificationPumpBlockEntity pump)
                    capture = Math.max(capture, pump.capture(tick));
            n.field().setDegasification(b.getIntValue(), capture);
        }
        drainage(n, s, tick);
    }

    // --- взрыв ---------------------------------------------------------------------------------------------

    private void onBurned(MineNetwork n, int node, long origin, double energyJ) {
        GridPos o = GridPos.fromLong(origin);
        binding.damage().submit(new DamageRequest(requestSeq++, SimId.NONE, null, DamageKind.BLAST, o, energyJ, BLAST_RADIUS_M, 2));
        blastRequests++;
        DamageBudgetConfig dc = binding.damage().config();
        double power = Math.max(1, Math.min(4, BlastScaling.power(energyJ, dc.tntReferenceKg(), dc.maxExplosionPower())));
        level.explode(null, o.x() + 0.5, o.y() + 0.5, o.z() + 0.5, (float) power, Level.ExplosionInteraction.NONE);
        Entry e = entries.get(n.id());
        if (e == null || e.scan == null || node >= e.scan.nodeStart.length - 1) return;
        // крепь узла ломается, двери распахивает; пламя и дым заполняют выработку узла — фронт видно по всей
        // длине, а не только ванильным «пыхом» в точке (мощность взрыва узла мала, порода почти не рушится)
        for (int i = e.scan.nodeStart[node]; i < e.scan.nodeStart[node + 1]; i++) {
            BlockPos p = BlockPos.of(e.scan.nodeBlocks[i]);
            BlockState st = level.getBlockState(p);
            if (st.is(PcBlocks.MINE_SUPPORT.get()) && !st.getValue(PcProperties.DAMAGED))
                level.setBlock(p, st.setValue(PcProperties.DAMAGED, true), Block.UPDATE_CLIENTS);
            if (st.isAir()) {
                double x = p.getX() + 0.5, y = p.getY() + 0.5, z = p.getZ() + 0.5;
                level.sendParticles(ParticleTypes.FLAME, x, y, z, 12, 0.4, 0.4, 0.4, 0.08);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 3, 0.4, 0.4, 0.4, 0.03);
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.3, z, 1, 0.3, 0.2, 0.3, 0.01);
            }
        }
        level.playSound(null, o.x() + 0.5, o.y() + 0.5, o.z() + 0.5, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 6f, 0.6f);
        for (long door : e.scan.doorCtrl) {
            BlockPos p = BlockPos.of(door);
            BlockState st = level.getBlockState(p);
            if (st.is(PcBlocks.VENTILATION_DOOR.get()) && !st.getValue(BlockStateProperties.OPEN) && near(p, o, 6))
                level.setBlock(p, st.setValue(BlockStateProperties.OPEN, true), Block.UPDATE_ALL);
        }
        PromptCritical.LOGGER.warn("[{}] mine {} explosion front at {} {} {}: {} kJ", level.dimension().location(), n.id(),
                o.x(), o.y(), o.z(), Math.round(energyJ / 1e3));
    }

    private static boolean near(BlockPos p, GridPos o, int r) {
        return Math.abs(p.getX() - o.x()) <= r && Math.abs(p.getY() - o.y()) <= r && Math.abs(p.getZ() - o.z()) <= r;
    }

    // --- люди, лампа, канарейки ----------------------------------------------------------------------------

    private void breathe(MineSubsystem sim, long now) {
        double ratio = sim.config().canaryUptakeRatio();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            BlockPos eye = BlockPos.containing(p.getEyePosition());
            MineNetwork n = mineAt(eye);
            int node = n == null ? -1 : n.nodeOf(eye.asLong());
            double co = node > 0 ? n.field().concentration(node, MineGas.CO) : 0;
            double o2 = node > 0 ? n.field().concentration(node, MineGas.O2) : MineGas.O2.freshAir;
            Breather b = breathers.computeIfAbsent(p.getUUID(), k -> new Breather(ratio));
            boolean isolated = b.isolatedUntil > now;
            if (b.isolatedUntil != 0 && !isolated) {
                b.isolatedUntil = 0;
                p.displayClientMessage(Component.translatable("promptcritical.mine.self_rescuer.spent").withStyle(ChatFormatting.GOLD), true);
            }
            if (!p.isCreative()) {
                b.exposure.step(1.0, isolated ? 0 : co, isolated ? MineGas.O2.freshAir : o2);
                applyExposure(p, b);
            }
            if (holding(p, PcItems.FLAME_SAFETY_LAMP.get())) lamp(p, n, node, o2);
        }
    }

    private void applyExposure(ServerPlayer p, Breather b) {
        ExposureState st = b.exposure.state();
        switch (st) {
            case CALM -> {
            }
            case AGITATED -> {
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
            }
            case FALLEN -> {
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, false));
            }
            case DEAD -> {
                breathers.remove(p.getUUID());
                p.hurt(damageSource(), Float.MAX_VALUE);
            }
        }
    }

    private DamageSource damageSource() {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(MINE_GAS));
    }

    private static boolean holding(Player p, net.minecraft.world.item.Item item) {
        return p.getMainHandItem().is(item) || p.getOffhandItem().is(item);
    }

    private void lamp(ServerPlayer p, @Nullable MineNetwork n, int node, double o2) {
        LampState st;
        double ch4 = 0;
        if (node > 0) {
            ch4 = n.field().roofMethane(node);
            st = SafetyLamp.state(ch4, o2, n.field().nodeVelocity(node), true);
        } else {
            st = LampState.NORMAL;
        }
        String key = "promptcritical.mine.lamp." + st.name().toLowerCase(java.util.Locale.ROOT);
        ChatFormatting color = switch (st) {
            case NORMAL -> ChatFormatting.YELLOW;
            case HALO -> ChatFormatting.AQUA;
            case FLAME_IN_GAUZE, IGNITES -> ChatFormatting.RED;
            case EXTINGUISHED -> ChatFormatting.DARK_GRAY;
        };
        p.displayClientMessage(Component.translatable(key, String.format("%.0f", 100 * SafetyLamp.haloFraction(ch4))).withStyle(color), true);
        if (st == LampState.IGNITES && n != null) n.ignite(node, IgnitionSource.LAMP);
    }

    /** Птица посажена в клетку: отравление с нуля. */
    public void onCanaryPlaced(BlockPos pos) {
        canaries.put(pos.asLong(), GasExposure.canary());
    }

    /** Птицу забирают; {@code true} — живая. Упавшую, но живую птицу вынесли — она отойдёт ({@code canary_saved}). */
    public boolean takeCanary(BlockPos pos) {
        GasExposure e = canaries.remove(pos.asLong());
        if (e != null && e.alive() && e.state() == ExposureState.FALLEN)
            dev.denfry.promptcritical.mod.content.SimAchievements.nearby(level, pos, dev.denfry.promptcritical.mod.content.SimAchievements.CANARY_REVIVED, "", 0);
        return e == null || e.alive();
    }

    /** Позиция внутри выработок какой-нибудь шахты. */
    public boolean inMine(BlockPos pos) {
        MineNetwork n = mineAt(pos);
        return n != null && n.nodeOf(pos.asLong()) > 0;
    }

    private void canaries() {
        for (Entry e : entries.values()) {
            if (e.scan == null) continue;
            for (long k : e.scan.cages) {
                BlockState st = level.getBlockState(BlockPos.of(k));
                if (st.is(PcBlocks.CANARY_CAGE.get()) && st.getValue(PcProperties.BIRD) != PcProperties.Bird.EMPTY)
                    canaries.computeIfAbsent(k, x -> GasExposure.canary());
            }
        }
        var it = canaries.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var en = it.next();
            BlockPos pos = BlockPos.of(en.getLongKey());
            if (!level.isLoaded(pos)) continue;
            BlockState st = level.getBlockState(pos);
            if (!st.is(PcBlocks.CANARY_CAGE.get()) || st.getValue(PcProperties.BIRD) == PcProperties.Bird.EMPTY) {
                it.remove();
                continue;
            }
            MineNetwork n = mineAt(pos);
            int node = n == null ? -1 : n.nodeOf(pos.asLong());
            GasExposure g = en.getValue();
            g.step(1.0, node > 0 ? n.field().concentration(node, MineGas.CO) : 0,
                    node > 0 ? n.field().concentration(node, MineGas.O2) : MineGas.O2.freshAir);
            ExposureState es = g.state();
            PcProperties.Bird bird = es == ExposureState.FALLEN || es == ExposureState.DEAD ? PcProperties.Bird.FAINTED : PcProperties.Bird.ALIVE;
            if (st.getValue(PcProperties.BIRD) != bird) {
                level.setBlock(pos, st.setValue(PcProperties.BIRD, bird), Block.UPDATE_ALL);
                // упавшая птица пришла в себя на свежем воздухе — её вывели вовремя (11 §11.2.2, canary_saved)
                if (bird == PcProperties.Bird.ALIVE)
                    dev.denfry.promptcritical.mod.content.SimAchievements.nearby(level, pos, dev.denfry.promptcritical.mod.content.SimAchievements.CANARY_REVIVED, "", 0);
            }
            if (es == ExposureState.AGITATED) level.playSound(null, pos, SoundEvents.PARROT_AMBIENT, SoundSource.BLOCKS, 1.0f, 2.0f);
        }
    }

    /** Состояние канарейки в клетке или {@code null}. */
    @Nullable
    public GasExposure canary(BlockPos pos) {
        return canaries.get(pos.asLong());
    }

    // --- диспетчерская -------------------------------------------------------------------------------------

    private void alarms(MineSubsystem sim) {
        double trip = ServerConfig.MINING_AGZ_TRIP_FRAC.get();
        for (MineNetwork n : sim.networks()) {
            if (!n.ready()) continue;
            boolean alarm = n.maxConcentration(MineGas.CH4) >= trip;
            for (long a : n.anchors()) {
                BlockPos p = BlockPos.of(a);
                if (!level.isLoaded(p)) continue;
                BlockState st = level.getBlockState(p);
                if (st.is(PcBlocks.MINE_DISPATCH_CABINET.get()) && st.getValue(PcProperties.ALARM) != alarm)
                    level.setBlock(p, st.setValue(PcProperties.ALARM, alarm), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Сводка диспетчерской шахты игроку. */
    public void report(BlockPos cabinet, Player player) {
        MineSubsystem sim = sim();
        long mine = anchorMine.get(cabinet.asLong());
        MineNetwork n = sim == null || mine < 0 ? null : sim.network(mine);
        if (n == null || !n.ready() || n.topology().nodeCount() <= 1) {
            player.sendSystemMessage(Component.translatable("promptcritical.mine.report.none"));
            return;
        }
        MineTopology t = n.topology();
        player.sendSystemMessage(Component.translatable("promptcritical.mine.report.header", n.id(), t.nodeCount() - 1,
                String.format("%.1f", n.surfaceAirflowM3s())).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("promptcritical.mine.report.gas",
                String.format("%.2f", 100 * n.maxConcentration(MineGas.CH4)),
                String.format("%.0f", 1e6 * n.maxConcentration(MineGas.CO)),
                String.format("%.1f", 100 * n.minOxygen()),
                String.format("%.2f", 60 * n.methaneEmissionM3s())));
        if (n.exploding() || n.explosions() > 0)
            player.sendSystemMessage(Component.translatable("promptcritical.mine.report.explosions", n.explosions()).withStyle(ChatFormatting.RED));
        player.sendSystemMessage(drainageLine(n));
        if (!n.drainage().capacityOk()) player.sendSystemMessage(HoistText.msg("drainage.small_sump").withStyle(ChatFormatting.RED));
    }

    /** Показания щита шкафа газовой защиты ({@link MinePanels#CABINET}). */
    public void readCabinet(BlockPos cabinet, int[] out) {
        java.util.Arrays.fill(out, 0);
        out[MinePanels.D_TRIP_X100] = (int) Math.round(100 * 100 * ServerConfig.MINING_AGZ_TRIP_FRAC.get());
        MineSubsystem sim = sim();
        long mine = anchorMine.get(cabinet.asLong());
        MineNetwork n = sim == null || mine < 0 ? null : sim.network(mine);
        if (n == null || !n.ready() || n.topology().nodeCount() <= 1) return;
        MineDrainage d = n.drainage();
        double ch4 = n.maxConcentration(MineGas.CH4);
        out[MinePanels.D_CH4_X100] = (int) Math.round(100 * 100 * ch4);
        out[MinePanels.D_CO_PPM] = (int) Math.round(1e6 * n.maxConcentration(MineGas.CO));
        out[MinePanels.D_O2_X10] = (int) Math.round(10 * 100 * n.minOxygen());
        out[MinePanels.D_EMISSION_X100] = (int) Math.round(100 * 60 * n.methaneEmissionM3s());
        out[MinePanels.D_AIR_X10] = (int) Math.round(10 * n.surfaceAirflowM3s());
        out[MinePanels.D_SUMP_PERMIL] = (int) Math.round(1000 * Math.min(1, d.levelFrac()));
        out[MinePanels.D_INFLOW_X10] = (int) Math.round(10 * 3600 * d.inflowM3s());
        out[MinePanels.D_PUMPED_X10] = (int) Math.round(10 * 3600 * d.pumpedM3s());
        out[MinePanels.D_EXPLOSIONS] = n.explosions();
        out[MinePanels.D_NODES] = n.topology().nodeCount() - 1;
        out[MinePanels.D_FLAGS] = 1 << MinePanels.DF_READY
                | (ch4 >= ServerConfig.MINING_AGZ_TRIP_FRAC.get() ? 1 << MinePanels.DF_ALARM : 0)
                | (n.exploding() || n.explosions() > 0 ? 1 << MinePanels.DF_EXPLOSION : 0)
                | (d.floodedM3() > 0 ? 1 << MinePanels.DF_FLOODED : 0)
                | (d.acidic() ? 1 << MinePanels.DF_ACID : 0)
                | (!d.capacityOk() ? 1 << MinePanels.DF_SUMP_SMALL : 0);
    }

    /** Строка водоотлива: водосборник, приток, откачка, затопление, кислотность. */
    public static Component drainageLine(MineNetwork n) {
        MineDrainage d = n.drainage();
        Component extra = Component.empty();
        if (d.floodedM3() > 0) extra = extra.copy().append(HoistText.msg("drainage.flooded", HoistText.num(d.floodedM3())));
        if (d.acidic()) extra = extra.copy().append(HoistText.msg("drainage.acid"));
        return HoistText.msg("drainage", HoistText.num(Math.min(d.volumeM3(), d.capacityM3())), HoistText.num(d.capacityM3()),
                HoistText.num(100 * Math.min(1, d.levelFrac())), HoistText.num(d.inflowM3s() * 3600),
                HoistText.num(d.pumpedM3s() * 3600), extra).withStyle(d.levelFrac() >= 0.8 ? ChatFormatting.RED : ChatFormatting.AQUA);
    }

    // --- водоотлив и затопление ---------------------------------------------------------------------------

    /** Руда и порода на блок, т [допущение: 2,5 т/м³]; сера пирита, %. */
    private static final double ORE_T_PER_BLOCK = 2.5, PYRITE_SULFUR_PCT = 50;
    /** Сколько блоков воды затопления ставить или снимать за секунду. */
    private static final int FLOOD_STEP = 16;

    /** Подача насосов шахты на их напорах → домен; откачка прошлого шага → насосам (мощность, вода в трубу). */
    private void drainage(MineNetwork n, MineScanner.Scan s, long tick) {
        MineDrainagePumpBlockEntity[] pumps = new MineDrainagePumpBlockEntity[s.pumps.size()];
        double[] cap = new double[pumps.length];
        double sum = 0;
        int i = 0;
        for (long k : s.pumps) {
            if (level.getBlockEntity(BlockPos.of(k)) instanceof MineDrainagePumpBlockEntity p) {
                pumps[i] = p;
                cap[i] = p.capacityM3s(tick);
                sum += cap[i];
            }
            i++;
        }
        MineDrainage d = n.drainage();
        double load = d.pumpLoad();
        for (i = 0; i < pumps.length; i++) if (pumps[i] != null) pumps[i].setPumped(cap[i] * load, d.acidic());
        d.setPumpCapacityM3s(sum);
    }

    /**
     * Вода сверх водосборника — блоками шахтной воды в самых низких выработках (1 м³ на блок), откачка снимает их в
     * обратном порядке. Шахтная вода не превращается в источник, поэтому снятый блок не наполняется заново.
     */
    private void flood(MineNetwork n, Entry e) {
        if (e.scan == null || !n.ready() || !loaded(n, e)) return;
        long[] placed = n.floodBlocks();
        int want = (int) Math.floor(n.drainage().floodedM3());
        if (want == placed.length) return;
        LongArrayList list = new LongArrayList(placed);
        if (want > placed.length) {
            for (long k : lowestAir(e.scan, Math.min(FLOOD_STEP, want - placed.length))) {
                level.setBlock(BlockPos.of(k), PcBlocks.MINE_WATER.get().defaultBlockState(), Block.UPDATE_ALL);
                list.add(k);
            }
        } else {
            for (int r = 0; r < FLOOD_STEP && list.size() > want; r++) {
                BlockPos p = BlockPos.of(list.removeLong(list.size() - 1));
                if (level.isLoaded(p) && level.getFluidState(p).getType() == PcFluids.MINE_WATER.source.get())
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        n.setFloodBlocks(list.toLongArray());
    }

    /** {@code k} самых низких пустых клеток выработки (по y, затем по ключу). */
    private long[] lowestAir(MineScanner.Scan s, int k) {
        LongArrayList free = new LongArrayList();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (long key : s.nodeBlocks) {
            BlockState st = level.getBlockState(p.set(key));
            if (st.isAir() || st.getFluidState().getType() == PcFluids.MINE_WATER.flowing.get()) free.add(key);
        }
        long[] a = free.toLongArray();
        it.unimi.dsi.fastutil.longs.LongArrays.quickSort(a, (x, y) -> {
            int c = Integer.compare(BlockPos.getY(x), BlockPos.getY(y));
            return c != 0 ? c : Long.compare(x, y);
        });
        return java.util.Arrays.copyOf(a, Math.min(k, a.length));
    }

    // --- ручные средства ----------------------------------------------------------------------------------

    /** Действие предмета {@link MineInstrumentItem}; {@code true} — выполнено. */
    public boolean useInstrument(MineInstrumentItem.Kind kind, Player player, ItemStack stack) {
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        MineNetwork n = mineAt(eye);
        int node = n == null ? -1 : n.nodeOf(eye.asLong());
        switch (kind) {
            case METHANOMETER -> {
                double ch4 = node > 0 ? n.field().roofMethane(node) : 0;
                player.displayClientMessage(Component.translatable("promptcritical.mine.methanometer",
                        String.format("%.1f", Math.round(ch4 * 1000) / 10.0)).withStyle(ch4 >= 0.01 ? ChatFormatting.RED : ChatFormatting.GREEN), true);
                return true;
            }
            case SELF_RESCUER -> {
                Breather b = breathers.computeIfAbsent(player.getUUID(), k -> new Breather(sim() == null ? 20 : sim().config().canaryUptakeRatio()));
                b.isolatedUntil = level.getGameTime() + SELF_RESCUER_TICKS;
                stack.consume(1, player);
                player.displayClientMessage(Component.translatable("promptcritical.mine.self_rescuer.on").withStyle(ChatFormatting.GREEN), true);
                return true;
            }
            case STONE_DUSTER -> {
                if (node <= 0) {
                    player.displayClientMessage(Component.translatable("promptcritical.mine.not_in_mine"), true);
                    return false;
                }
                ItemStack dust = findItem(player, PcItems.STONE_DUST.get());
                if (dust.isEmpty()) {
                    player.displayClientMessage(Component.translatable("promptcritical.mine.duster.empty"), true);
                    return false;
                }
                dust.consume(1, player);
                MineTopology t = n.topology();
                double surface = t.perimeterM(node) * t.volumeM3(node) / t.sectionM2(node);
                n.field().depositStoneDust(node, ServerConfig.METALLURGY_BULK_ITEM_MASS_KG.get() * 1000 / surface);
                player.displayClientMessage(Component.translatable("promptcritical.mine.duster.done",
                        String.format("%.0f", 100 * n.field().inertFraction(node))), true);
                level.playSound(null, eye, SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
                return true;
            }
        }
        return false;
    }

    private static ItemStack findItem(Player p, net.minecraft.world.item.Item item) {
        for (ItemStack s : p.getInventory().items) if (s.is(item)) return s;
        ItemStack off = p.getOffhandItem();
        return off.is(item) ? off : ItemStack.EMPTY;
    }

    // --- добыча --------------------------------------------------------------------------------------------

    /**
     * Игрок ломает блок (до снятия). Уголь пласта отдаёт газ и пыль, у выработки без шахты — заводит шахту; кровля над
     * пройденным блоком попадает под правило пролёта.
     */
    public void onBlockBroken(BlockPos pos, BlockState state) {
        onBlockBroken(pos, state, 1);
    }

    /** То же для комбайна: от пыли пласта остаётся доля {@code dustFactor} (орошение, 08 §8.2.3). */
    public void onBlockBroken(BlockPos pos, BlockState state, double dustFactor) {
        MineSubsystem sim = sim();
        if (sim == null) return;
        MineNetwork n = mineNear(pos);
        boolean coal = state.is(PcBlocks.COAL_SEAM.get());
        if (n == null && coal) {
            n = sim.create(new long[] {pos.asLong()});
            anchorMine.put(pos.asLong(), n.id());
            entry(n).due = level.getGameTime() + 1;
        }
        if (n == null) return;
        if (coal && n.ready()) {
            double vdaf = VeinFields.get(level.getSeed()).volatileDafPct(pos.getX(), pos.getY(), pos.getZ()) / 100;
            n.field().onMined(pos.asLong(), MethaneField.gasContent(ServerConfig.MINING_SEAM_GAS_CONTENT.get(), pos.getY()), vdaf, dustFactor);
        }
        if (coal) {
            double sulfur = 100 * VeinFields.get(level.getSeed()).coalAssay(pos.getX(), pos.getY(), pos.getZ()).sulfurDryFrac();
            n.drainage().onMined(MethaneField.COAL_DENSITY / 1000, sulfur);
        } else if (state.is(Tags.Blocks.ORES)) {
            boolean pyrite = state.is(PcBlocks.PYRITE_ORE.get()) || state.is(PcBlocks.DEEPSLATE_PYRITE_ORE.get());
            n.drainage().onMined(ORE_T_PER_BLOCK, pyrite ? PYRITE_SULFUR_PCT : 0);
        }
        n.markWorkedRoof(pos.above().asLong());
    }

    // --- кровля --------------------------------------------------------------------------------------------

    private final class Roof implements RoofSpanRule.RoofGrid {
        final MineNetwork n;
        final int y;

        Roof(MineNetwork n, int y) {
            this.n = n;
            this.y = y;
        }

        @Override
        public boolean open(int x, int z) {
            if (n.nodeOf(BlockPos.asLong(x, y - 1, z)) <= 0) return false;
            BlockState s = level.getBlockState(new BlockPos(x, y, z));
            return !s.isAir() && !s.getCollisionShape(level, new BlockPos(x, y, z)).isEmpty();
        }

        @Override
        public MineSupport support(int x, int z) {
            BlockState s = supportState(x, z);
            if (s == null) return null;
            if (s.is(PcBlocks.POWERED_ROOF_SUPPORT.get())) return MineSupport.POWERED;
            return s.getValue(PcProperties.SUPPORT_KIND) == PcProperties.SupportKind.ARCH ? MineSupport.ARCH : MineSupport.TIMBER;
        }

        @Override
        public double supportCondition(int x, int z) {
            BlockState s = supportState(x, z);
            if (s == null) return 0;
            // разгруженная секция мехкрепи кровлю не держит
            if (s.is(PcBlocks.POWERED_ROOF_SUPPORT.get())) return PoweredRoofSupportBlock.set(s) ? 1.0 : 0;
            return s.getValue(PcProperties.DAMAGED) ? 0.4 : 1.0;
        }

        @Nullable
        private BlockState supportState(int x, int z) {
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos p = new BlockPos(x, y - dy, z);
                BlockState s = level.getBlockState(p);
                if (s.is(PcBlocks.MULTIBLOCK_PART.get())) {
                    BlockPos a = MultiblockPartBlock.anchor(s, p);
                    s = level.getBlockState(a);
                }
                if (s.is(PcBlocks.MINE_SUPPORT.get()) || s.is(PcBlocks.POWERED_ROOF_SUPPORT.get())) return s;
            }
            return null;
        }
    }

    private static RoofRock rock(BlockState s) {
        if (s.is(PcTags.Blocks.ROOF_STABLE)) return RoofRock.SANDSTONE;
        if (s.is(PcTags.Blocks.ROOF_WEAK)) return RoofRock.CARBONACEOUS_SHALE;
        return RoofRock.MUDSTONE;
    }

    private void evaluateRoof(MineSubsystem sim, MineNetwork n) {
        if (!ServerConfig.MINING_ROOF_COLLAPSE.get()) return;
        MiningConfig cfg = sim.config();
        double now = binding.world().clock().slowPhysTime();
        for (long k : n.workedRoof()) {
            BlockPos roof = BlockPos.of(k);
            if (!level.isLoaded(roof)) continue;
            Roof g = new Roof(n, roof.getY());
            if (!g.open(roof.getX(), roof.getZ())) {
                roofDue.remove(k);
                if (n.nodeOf(roof.asLong()) > 0) n.forgetWorkedRoof(k); // свод выбран: блок стал воздухом
                continue;
            }
            RoofRock r = rock(level.getBlockState(roof));
            int span = RoofSpanRule.span(g, roof.getX(), roof.getZ());
            if (span >= RoofSpanRule.MAX_SCAN || RoofSpanRule.supported(g, roof.getX(), roof.getZ(), r, cfg)) roofDue.remove(k);
            else if (!roofDue.containsKey(k)) roofDue.put(k, now + r.collapseDelayS);
        }
    }

    private void roofTimers(MineSubsystem sim) {
        if (roofDue.isEmpty()) return;
        double now = binding.world().clock().slowPhysTime();
        LongArrayList due = new LongArrayList();
        for (var e : roofDue.long2DoubleEntrySet()) if (e.getDoubleValue() <= now) due.add(e.getLongKey());
        for (long k : due) {
            if (!roofDue.containsKey(k)) continue;
            roofDue.remove(k);
            BlockPos roof = BlockPos.of(k);
            MineNetwork n = mineAt(roof.below());
            if (n == null || !level.isLoaded(roof)) continue;
            Roof g = new Roof(n, roof.getY());
            if (!g.open(roof.getX(), roof.getZ())) continue;
            RoofRock r = rock(level.getBlockState(roof));
            int span = RoofSpanRule.span(g, roof.getX(), roof.getZ());
            int adm = RoofSpanRule.admissibleSpan(g, roof.getX(), roof.getZ(), r, sim.config());
            int h = RoofSpanRule.collapseHeight(span, adm);
            if (h <= 0 || span >= RoofSpanRule.MAX_SCAN) continue;
            collapse(roof, h);
            PromptCritical.LOGGER.info("[{}] mine {} roof fall at {} (span {} > {}, dome {})", level.dimension().location(), n.id(),
                    roof.toShortString(), span, adm, h);
        }
    }

    /** Купол обрушения высотой h — заявка {@code DamageRequest(BLAST, priority 5)} (08 §8.2.2). */
    private void collapse(BlockPos roof, int h) {
        double r = 0.5 * h + 0.5;
        DamageBudgetConfig dc = binding.damage().config();
        double power = r / BlastScaling.RADIUS_PER_POWER;
        double energy = dc.tntReferenceKg() * Math.pow(power / BlastScaling.VANILLA_TNT_POWER, 3) * BlastScaling.TNT_J_PER_KG;
        GridPos o = new GridPos(roof.getX(), roof.getY() + (h - 1) / 2, roof.getZ());
        binding.damage().submit(new DamageRequest(requestSeq++, SimId.NONE, null, DamageKind.BLAST, o, energy, r, 5));
        level.playSound(null, roof, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 2.0f, 0.5f);
        int rr = (int) Math.ceil(r) + 1;
        roofDue.long2DoubleEntrySet().removeIf(e -> {
            BlockPos p = BlockPos.of(e.getLongKey());
            return Math.abs(p.getX() - roof.getX()) <= rr && Math.abs(p.getZ() - roof.getZ()) <= rr && Math.abs(p.getY() - roof.getY()) <= rr;
        });
    }

    /** Заявок {@code DamageRequest(BLAST, priority 2)} от фронтов взрыва с загрузки. */
    public int blastRequests() {
        return blastRequests;
    }

    /** Ожидающих обрушения клеток кровли (для тестов). */
    public int pendingRoofFalls() {
        return roofDue.size();
    }
}
