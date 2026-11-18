package dev.denfry.promptcritical.mod.ops;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.automation.ProcessSources;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.grid.BreakerCellBlockEntity;
import dev.denfry.promptcritical.mod.grid.DisconnectorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.mining.MineGas;
import dev.denfry.promptcritical.sim.mining.MineNetwork;
import dev.denfry.promptcritical.sim.ops.GasLimits;
import dev.denfry.promptcritical.sim.ops.GasTest;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry.IsolationState;
import dev.denfry.promptcritical.sim.ops.OpsLogEntry;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.PermitEnforcement;
import dev.denfry.promptcritical.sim.ops.PermitRegistry;
import dev.denfry.promptcritical.sim.ops.PermitRegistry.Permit;
import dev.denfry.promptcritical.sim.ops.PermitRegistry.Result;
import dev.denfry.promptcritical.sim.ops.PermitState;
import dev.denfry.promptcritical.sim.ops.PermitType;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Наряды-допуски в мире (13 §13.2): бланк {@code work_permit} по объекту выписывает наряд (вид — по опасностям объекта,
 * §13.2.2), заполненный бланк — ссылка на оригинал в {@link PermitRegistry} и ведёт наряд по автомату §13.2.4.
 *
 * <p>Руки игрока: бланком по объекту (кроме аппаратов сети — у них электрический наряд ПОТ ЭЭ) — выписать; нарядом по
 * объекту — следующий шаг (изоляция → допуск, пожарный пост для огневых работ); Shift — работы окончены, затем
 * закрыть; ПКМ в воздух — состояние. Газоанализ заносит переносной газоанализатор
 * ({@link PortableGasDetectorItem}). Раз в секунду: непрерывный газовый контроль огневых работ (тревога —
 * {@code SUSPENDED}), наработка и доза бригады в объёме работ.
 *
 * <p>Вид наряда: объект — ГЦН ({@code primary_pump}) или мощность дозы у него выше 12 мкЗв/ч (помещение
 * {@code CONTROLLED_UNSERVICED}, 04b §4b.12.1) — {@code RADIATION}; в 11 м есть горючая среда или горючая атмосфера —
 * {@code HOT}; иначе {@code COLD}.
 */
public final class Permits {
    /** Объём работ обычного и радиационного наряда вокруг объекта, м [BALANCE]. */
    public static final double WORK_RADIUS_M = 5;
    /** Сколько бригаде работать у объекта наряда {@code RADIATION} до окончания работ, с реального времени [BALANCE]. */
    public static final double REPAIR_WORK_S = 60;
    /** Мощность дозы, выше которой помещение — необслуживаемое и работы в нём только по наряду, Зв/ч (04b §4b.12.1). */
    public static final double UNSERVICED_SV_H = 12e-6;
    /** Порог коллективной дозы ремонта ГЦН для достижения {@code alara} (11), Зв. */
    public static final double ALARA_COLLECTIVE_SV = 0.010;

    /** Ссылка бланка на оригинал наряда. */
    public record Ref(long id, PermitType type, BlockPos target) {
        private static final Codec<PermitType> TYPE = Codec.STRING.xmap(PermitType::valueOf, PermitType::name);
        public static final Codec<Ref> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(Ref::id),
                TYPE.fieldOf("type").forGetter(Ref::type),
                BlockPos.CODEC.fieldOf("target").forGetter(Ref::target)).apply(i, Ref::new));
        public static final StreamCodec<ByteBuf, Ref> STREAM = ByteBufCodecs.fromCodec(CODEC);
    }

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Ref>> REF =
            COMPONENTS.registerComponentType("ops_permit", b -> b.persistent(Ref.CODEC).networkSynchronized(Ref.STREAM));
    /** Тик мира последней проверки газоанализатора поверочной смесью (§13.2.3). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> BUMP_TICK =
            COMPONENTS.registerComponentType("gas_detector_bump", b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    private Permits() {
    }

    // ------------------------------------------------------------------ доступ

    @Nullable
    public static PermitRegistry registry(@Nullable Level level) {
        OpsSubsystem ops = OpsWorld.ops(level);
        return ops == null ? null : ops.permits();
    }

    /** Тик симуляции мира (сроки нарядов — в тиках FAST). */
    public static long tick(Level level) {
        SimLevelBinding b = level instanceof ServerLevel sl ? SimBindings.get(sl) : null;
        return b == null ? level.getGameTime() : b.world().clock().tick();
    }

    public static PermitEnforcement enforcement() {
        return ServerConfig.OPS_PERMIT_ENFORCEMENT.get();
    }

    static GridPos key(BlockPos p) {
        return Lockouts.key(p);
    }

    static BlockPos pos(GridPos g) {
        return new BlockPos(g.x(), g.y(), g.z());
    }

    private static void journal(Level level, BlockPos at, OpsLogEntry.Kind kind, String detail) {
        OpsSubsystem ops = OpsWorld.ops(level);
        if (ops != null) ops.journal(dev.denfry.promptcritical.sim.core.SimId.NONE, kind, detail + " @" + at.toShortString());
    }

    // ------------------------------------------------------------------ атмосфера и горючие среды

    /** Состав атмосферы в точке: доля O₂, доля НКПР, H₂S и CO в ppm. */
    public record Air(double o2, double lel, double h2sPpm, double coPpm) {
        public static final Air CLEAN = new Air(MineGas.O2.freshAir, 0, 0, 0);
    }

    /**
     * Истинный состав в точке: в выработке шахты — поле газов 08 (метан у кровли, O₂, CO), иначе чистый воздух.
     * Сероводород мод пока не моделирует.
     */
    public static Air air(ServerLevel level, BlockPos pos) {
        // замкнутое пространство за люком (13 §13.2.6): точка внутри или сам люк
        dev.denfry.promptcritical.mod.safety.ManwayBlockEntity m = dev.denfry.promptcritical.mod.safety.ConfinedSpaces.at(level, pos);
        if (m == null && level.getBlockEntity(pos) instanceof dev.denfry.promptcritical.mod.safety.ManwayBlockEntity self && self.confined()) m = self;
        if (m != null) {
            var a = m.atmosphere();
            return new Air(a.fraction(dev.denfry.promptcritical.sim.ops.ConfinedSpaceAtmosphere.Gas.O2), a.lelFraction(),
                    a.ppm(dev.denfry.promptcritical.sim.ops.ConfinedSpaceAtmosphere.Gas.H2S), a.ppm(dev.denfry.promptcritical.sim.ops.ConfinedSpaceAtmosphere.Gas.CO));
        }
        SimLevelBinding b = SimBindings.get(level);
        MineNetwork n = b == null ? null : b.mines().mineAt(pos);
        int node = n == null ? -1 : n.nodeOf(pos.asLong());
        if (node <= 0) return Air.CLEAN;
        return new Air(n.field().concentration(node, MineGas.O2), n.field().roofMethane(node) / ProcessSources.CH4_LEL, 0,
                n.field().concentration(node, MineGas.CO) * 1e6);
    }

    /** Наибольшая доля НКПР в 27 точках вокруг места (§13.2.5). */
    public static double maxLel(ServerLevel level, BlockPos at) {
        double max = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) max = Math.max(max, air(level, at.offset(dx, dy, dz)).lel());
        return max;
    }

    /** Горючая среда 02 §2.0.5 ({@code FLAMMABLE}): нефть и нефтепродукты, спирты, смолы, горючие газы. */
    public static boolean flammable(Fluid f) {
        if (FireService.hydrocarbon(f)) return true;
        for (PcFluids.Entry e : List.of(PcFluids.MINE_METHANE, PcFluids.COKE_OVEN_GAS, PcFluids.BLAST_FURNACE_GAS, PcFluids.CONVERTER_GAS,
                PcFluids.ASSOCIATED_GAS, PcFluids.SALES_GAS, PcFluids.PROPANE, PcFluids.BUTANE, PcFluids.ETHYLENE, PcFluids.PROPYLENE,
                PcFluids.C4_FRACTION, PcFluids.SYNGAS, PcFluids.HYDROGEN, PcFluids.VINYL_CHLORIDE))
            if (f.isSame(e.source.get())) return true;
        return false;
    }

    /** Блок держит горючую среду (резервуар, труба, аппарат с жидкостью). */
    public static boolean holdsFlammable(Level level, BlockPos p) {
        IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, p, null);
        if (h == null) return false;
        for (int i = 0; i < h.getTanks(); i++) {
            var s = h.getFluidInTank(i);
            if (!s.isEmpty() && flammable(s.getFluid())) return true;
        }
        return false;
    }

    /** В {@code r} м от места есть горючая среда (блок с горючей жидкостью или газом) или горючая атмосфера. */
    public static boolean flammableNear(ServerLevel level, BlockPos at, double r) {
        if (maxLel(level, at) > 0) return true;
        return !flammableSources(level, at, r, 1).isEmpty();
    }

    /** Блоки с горючей средой в радиусе (не больше {@code limit}). */
    public static List<BlockPos> flammableSources(ServerLevel level, BlockPos at, double r, int limit) {
        List<BlockPos> out = new ArrayList<>();
        int ri = (int) Math.ceil(r);
        double r2 = r * r;
        for (int cx = (at.getX() - ri) >> 4; cx <= (at.getX() + ri) >> 4; cx++)
            for (int cz = (at.getZ() - ri) >> 4; cz <= (at.getZ() + ri) >> 4; cz++) {
                LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
                if (c == null) continue;
                for (BlockPos p : new ArrayList<>(c.getBlockEntities().keySet())) {
                    if (p.distSqr(at) > r2 || !holdsFlammable(level, p)) continue;
                    out.add(p);
                    if (out.size() >= limit) return out;
                }
            }
        return out;
    }

    /** Блоки-устройства в радиусе (контроллеры с BE). */
    static List<BlockEntity> blockEntities(ServerLevel level, BlockPos at, double r) {
        List<BlockEntity> out = new ArrayList<>();
        int ri = (int) Math.ceil(r);
        double r2 = r * r;
        for (int cx = (at.getX() - ri) >> 4; cx <= (at.getX() + ri) >> 4; cx++)
            for (int cz = (at.getZ() - ri) >> 4; cz <= (at.getZ() + ri) >> 4; cz++) {
                LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
                if (c == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> e : c.getBlockEntities().entrySet())
                    if (e.getKey().distSqr(at) <= r2) out.add(e.getValue());
            }
        return out;
    }

    // ------------------------------------------------------------------ бланк по объекту

    /** Бланк или заполненный наряд по блоку; электрические аппараты сети — у {@code Paperwork}. */
    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        ItemStack stack = e.getItemStack();
        if (!stack.is(PcItems.WORK_PERMIT.get())) return;
        boolean ref = stack.has(REF.get());
        if (!ref && stack.has(dev.denfry.promptcritical.mod.paperwork.Paperwork.WORK_PERMIT.get())) return; // электрический наряд
        Level level = e.getLevel();
        if (level.getBlockState(e.getPos()).getBlock() instanceof net.minecraft.world.level.block.LecternBlock) return; // документ — на пюпитр
        BlockPos target = controller(level, e.getPos());
        if (!ref && (level.getBlockEntity(target) instanceof DisconnectorBlockEntity || level.getBlockEntity(target) instanceof BreakerCellBlockEntity))
            return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (ref) advance(p, stack, target);
        else issue(p, stack, target);
    }

    /** Контроллер многоблочного объекта (на клиенте и на сервере). */
    static BlockPos controller(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof dev.denfry.promptcritical.mod.block.PcBlock pc) {
            BlockPos c = pc.controllerPos(s, pos);
            if (c != null) return c;
        }
        return pos;
    }

    /** Вид наряда по опасностям объекта (§13.2.2: опасности предзаполнены по объекту). */
    public static PermitType typeFor(ServerLevel level, BlockPos target) {
        if (level.getBlockState(target).is(PcBlocks.MANWAY.get())) return PermitType.CONFINED_SPACE; // 13 §13.2.6
        if (level.getBlockState(target).is(PcBlocks.PRIMARY_PUMP.get())
                || RadiationService.rateSvH(level, Vec3.atCenterOf(target)) > UNSERVICED_SV_H) return PermitType.RADIATION;
        if (flammableNear(level, target, PermitRegistry.HOT_WORK_RADIUS_M)) return PermitType.HOT;
        return PermitType.COLD;
    }

    /** Выписать наряд на объект: бланк из стопки становится нарядом, ссылкой на оригинал. */
    @Nullable
    public static Permit issue(ServerPlayer p, ItemStack blank, BlockPos target) {
        ServerLevel level = p.serverLevel();
        PermitRegistry r = registry(level);
        if (r == null) return null;
        PermitType type = typeFor(level, target);
        double radius = type == PermitType.HOT ? PermitRegistry.HOT_WORK_RADIUS_M : WORK_RADIUS_M;
        String kind = BuiltInRegistries.BLOCK.getKey(level.getBlockState(target).getBlock()).toString();
        Permit permit = r.issue(type, key(target), radius, kind, p.getUUID(), p.getGameProfile().getName(),
                type == PermitType.RADIATION ? REPAIR_WORK_S : 0, tick(level));
        ItemStack written = blank.split(1);
        written.set(REF.get(), new Ref(permit.id(), type, target.immutable()));
        if (!p.addItem(written)) p.drop(written, false);
        level.playSound(null, target, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 0.7f, 1.2f);
        p.displayClientMessage(PermitText.msg("issued", permit.id(), PermitText.type(type), level.getBlockState(target).getBlock().getName()), false);
        return permit;
    }

    /** Изоляция объекта по плану §13.1.4 выполнена и проверена: все точки заперты и проверены, источников без аппарата нет. */
    public static boolean isolationComplete(ServerLevel level, BlockPos target) {
        IsolationPlans.Plan plan = IsolationPlans.build(level, target);
        return plan.unswitchedSources().isEmpty() && plan.points().stream().allMatch(x -> x.state() == IsolationState.VERIFIED);
    }

    /** На точках изоляции объекта ещё висят замки. */
    public static boolean locksRemain(ServerLevel level, BlockPos target) {
        IsolationPlans.Plan plan = IsolationPlans.build(level, target);
        return plan.points().stream().anyMatch(x -> x.state() == IsolationState.LOCKED || x.state() == IsolationState.VERIFIED);
    }

    /** Пожарный пост: игрок с огнетушителем в зоне огневых работ (§13.2.1) — допускающий или кто-то рядом. */
    public static boolean fireWatch(ServerLevel level, BlockPos target, @Nullable ServerPlayer admitting) {
        double r2 = PermitRegistry.HOT_WORK_RADIUS_M * PermitRegistry.HOT_WORK_RADIUS_M;
        List<ServerPlayer> around = new ArrayList<>(level.players());
        if (admitting != null) around.add(admitting);
        for (ServerPlayer pl : around) {
            if (pl.distanceToSqr(Vec3.atCenterOf(target)) > r2) continue;
            if (pl.getInventory().hasAnyMatching(s -> s.getItem() instanceof FireExtinguisherItem)) return true;
        }
        return false;
    }

    /** Следующий шаг наряда по объекту (§13.2.4). */
    public static Result advance(ServerPlayer p, ItemStack stack, BlockPos clicked) {
        ServerLevel level = p.serverLevel();
        PermitRegistry r = registry(level);
        Ref ref = stack.get(REF.get());
        Permit permit = r == null || ref == null ? null : r.permit(ref.id());
        if (permit == null) {
            p.displayClientMessage(PermitText.result(Result.UNKNOWN), true);
            return Result.UNKNOWN;
        }
        BlockPos target = pos(permit.target());
        if (!permit.covers(key(clicked))) {
            p.displayClientMessage(PermitText.msg("other", level.getBlockState(target).getBlock().getName()), true);
            return Result.WRONG_STATE;
        }
        long now = tick(level);
        String name = p.getGameProfile().getName();
        Result res;
        switch (permit.state()) {
            case ISSUED -> {
                res = r.isolate(permit.id(), isolationComplete(level, target), now);
                if (res == Result.OK)
                    p.displayClientMessage(PermitText.msg(permit.type().needsGasTest() ? "isolated" : "isolated_no_gas").withStyle(ChatFormatting.GREEN), true);
            }
            case ISOLATED, GAS_TESTED -> {
                res = r.admit(permit.id(), p.getUUID(), name, fireWatch(level, target, p), now);
                if (res == Result.OK) {
                    p.displayClientMessage(PermitText.msg("admitted", permit.id()).withStyle(ChatFormatting.GREEN), false);
                    level.playSound(null, target, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 0.7f, 1.0f);
                }
            }
            case ACTIVE -> {
                if (p.isSecondaryUseActive()) {
                    res = r.complete(permit.id(), now);
                    if (res == Result.OK) p.displayClientMessage(PermitText.msg("completed"), true);
                } else {
                    r.join(permit.id(), p.getUUID(), name);
                    p.displayClientMessage(PermitText.msg("joined", permit.id()).append(" ").append(PermitText.msg("complete_hint")), true);
                    return Result.OK;
                }
            }
            case WORK_COMPLETE -> {
                res = p.isSecondaryUseActive() ? r.close(permit.id(), locksRemain(level, target), now) : Result.WRONG_STATE;
                if (res == Result.OK) {
                    p.displayClientMessage(PermitText.msg("closed", permit.id()).withStyle(ChatFormatting.GREEN), false);
                    onClosed(level, permit);
                } else if (!p.isSecondaryUseActive()) {
                    p.displayClientMessage(PermitText.msg("completed"), true);
                    return res;
                }
            }
            case SUSPENDED -> {
                p.displayClientMessage(PermitText.msg("suspended", permit.id(), PermitText.result(Result.NO_GAS_TEST)).withStyle(ChatFormatting.GOLD), true);
                return Result.NO_GAS_TEST;
            }
            default -> {
                status(p, stack);
                return Result.WRONG_STATE;
            }
        }
        if (res != Result.OK) p.displayClientMessage(PermitText.result(res).withStyle(ChatFormatting.GOLD), true);
        return res;
    }

    /** Наряд закрыт: ремонт ГЦН с коллективной дозой бригады ниже 10 мЗв — достижение {@code alara} бригаде. */
    static void onClosed(ServerLevel level, Permit permit) {
        boolean pump = permit.targetKind().equals(BuiltInRegistries.BLOCK.getKey(PcBlocks.PRIMARY_PUMP.get()).toString());
        if (permit.type() != PermitType.RADIATION || !pump || permit.workS() < permit.requiredWorkS()) return;
        double dose = permit.collectiveDoseSv();
        if (dose >= ALARA_COLLECTIVE_SV) return;
        for (UUID id : permit.crew().keySet()) {
            ServerPlayer pl = level.getServer().getPlayerList().getPlayer(id);
            if (pl != null) SimAchievements.direct(pl, SimAchievements.ALARA, "dose_msv", dose * 1000);
        }
    }

    /** Состояние наряда в чат. */
    public static void status(ServerPlayer p, ItemStack stack) {
        PermitRegistry r = registry(p.level());
        Ref ref = stack.get(REF.get());
        Permit x = r == null || ref == null ? null : r.permit(ref.id());
        if (x == null) {
            p.displayClientMessage(PermitText.result(Result.UNKNOWN), true);
            return;
        }
        long now = tick(p.level());
        BlockPos t = pos(x.target());
        p.displayClientMessage(PermitText.msg("status", x.id(), PermitText.type(x.type()), PermitText.state(x.state()),
                p.level().getBlockState(t).getBlock().getName().copy().append(" " + t.toShortString())).withStyle(ChatFormatting.GOLD), false);
        if (x.state().open()) {
            long left = Math.max(0, (x.validToTick() - now) / Math.round(PermitRegistry.TICKS_PER_S) / 60);
            p.displayClientMessage(PermitText.msg("status.valid", left), false);
        }
        GasTest g = x.gas();
        if (g == null) {
            if (x.type().needsGasTest()) p.displayClientMessage(PermitText.msg("status.gas_none"), false);
        } else {
            p.displayClientMessage(PermitText.msg("status.gas", pct(g.o2() * 100), pct(g.lelFraction() * 100), Math.round(g.coPpm()),
                    (now - g.tick()) / Math.round(PermitRegistry.TICKS_PER_S), g.bumpTested() ? "" : PermitText.msg("status.unbumped").getString()), false);
        }
        if (!x.crew().isEmpty() || x.requiredWorkS() > 0)
            p.displayClientMessage(PermitText.msg("status.crew", String.join(", ", x.crew().values()),
                    pct(x.collectiveDoseSv() * 1000), Math.round(x.workS()), Math.round(x.requiredWorkS())), false);
    }

    static String pct(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    // ------------------------------------------------------------------ газоанализ

    /**
     * Замер переносным газоанализатором в точке игрока: показания (термокаталитический датчик занижает горючие без
     * кислорода) заносятся во все открытые наряды, в объём которых попал игрок и которым нужен газоанализ, и в стенды
     * нарядов рядом (остановочный ремонт).
     *
     * @return замер
     */
    public static GasTest measure(ServerPlayer p, ItemStack detector, PermitEnforcement mode) {
        ServerLevel level = p.serverLevel();
        BlockPos at = p.blockPosition();
        Air a = air(level, at.above());
        double lelReading = ProcessSources.methaneLel(level, at.above()) / 100;
        Long bump = detector.get(BUMP_TICK.get());
        boolean bumped = bump != null && level.getGameTime() - bump <= PermitRegistry.BUMP_TEST_MAX_AGE_S * 20;
        long now = tick(level);
        GasTest t = new GasTest(now, p.getUUID(), "portable_gas_detector", bumped, a.o2(), lelReading, a.h2sPpm(), a.coPpm());
        // пробоотборник через открытый люк или замер внутри: атмосфера замкнутого пространства (13 §13.2.3, §13.2.6)
        dev.denfry.promptcritical.mod.safety.ManwayBlockEntity mw = dev.denfry.promptcritical.mod.safety.ConfinedSpaces.sampledFrom(level, p);
        if (mw != null) t = mw.sample(now, p.getUUID(), bumped);
        p.displayClientMessage(PermitText.msg("gas.reading", pct(t.o2() * 100), pct(t.lelFraction() * 100), Math.round(t.h2sPpm()),
                Math.round(t.coPpm()), bumped ? "" : PermitText.msg("status.unbumped").getString()), false);
        if (!bumped) p.displayClientMessage(PermitText.msg("gas.bump_needed").withStyle(ChatFormatting.GOLD), false);
        PermitRegistry r = registry(level);
        if (r != null) {
            GridPos g = key(at);
            for (Permit x : List.copyOf(r.all())) {
                if (!x.type().needsGasTest() || x.state().terminal() || !x.covers(g)) continue;
                Set<GasLimits.Fault> f = r.gasTest(x.id(), t, mode, now);
                p.displayClientMessage(f.isEmpty() ? PermitText.msg("gas.recorded", x.id()).withStyle(ChatFormatting.GREEN)
                        : PermitText.msg("gas.rejected", x.id(), PermitText.faults(f)).withStyle(ChatFormatting.GOLD), false);
                journal(level, at, OpsLogEntry.Kind.GAS_TEST, "#" + x.id() + (f.isEmpty() ? " OK" : " " + f));
            }
        }
        boolean free = GasLimits.DEFAULT.faults(PermitType.CONFINED_SPACE, t, mode == PermitEnforcement.ENFORCED).isEmpty();
        for (BlockEntity be : blockEntities(level, at, PermitBoardBlockEntity.SCOPE_MAX_M))
            if (be instanceof PermitBoardBlockEntity board && be.getBlockPos().distSqr(at) <= board.scope() * board.scope())
                board.gasTested(free, now);
        return t;
    }

    // ------------------------------------------------------------------ раз в секунду

    public static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 7) return;
        PermitRegistry r = registry(level);
        if (r == null) return;
        long now = tick(level);
        for (Permit x : List.copyOf(r.all())) {
            if (x.state() != PermitState.ACTIVE) continue;
            BlockPos t = pos(x.target());
            if (!level.isLoaded(t)) continue;
            if (x.type() == PermitType.HOT || x.type() == PermitType.CONFINED_SPACE) {
                Air a = air(level, t);
                if (r.limits().alarm(a.o2(), maxLel(level, t)) && r.gasAlarm(x.id(), now)) {
                    for (ServerPlayer pl : level.players())
                        if (x.covers(key(pl.blockPosition())))
                            pl.displayClientMessage(PermitText.msg("alarm", x.id()).withStyle(ChatFormatting.RED), false);
                    continue;
                }
            }
            crewTick(level, r, x, t);
        }
    }

    /** Секунда работы бригады: люди из наряда в объёме работ; объект с движущимися частями — только остановленный. */
    private static void crewTick(ServerLevel level, PermitRegistry r, Permit x, BlockPos t) {
        boolean present = false;
        for (ServerPlayer pl : level.players()) {
            if (!x.crew().containsKey(pl.getUUID()) || !x.covers(key(pl.blockPosition()))) continue;
            present = true;
            r.addDose(x.id(), pl.getUUID(), RadiationService.lastRateSvH(pl) / 3600);
        }
        BlockState s = level.getBlockState(t);
        boolean running = s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING);
        if (present && !running) r.addWork(x.id(), 1);
    }

    // ------------------------------------------------------------------ оформление предмета

    public static Component name(ItemStack stack) {
        Ref ref = stack.get(REF.get());
        return ref == null ? null : PermitText.msg("title", ref.id(), PermitText.type(ref.type()));
    }

    public static void tooltip(ItemStack stack, List<Component> out) {
        Ref ref = stack.get(REF.get());
        if (ref == null) return;
        out.add(Component.literal(ref.target().toShortString()).withStyle(ChatFormatting.GRAY));
    }

    /** Достижение {@code permit}: огневое действие под действующим нарядом с газоанализом. */
    static void hotWorkUnderPermit(ServerPlayer p, Permit permit) {
        if (permit.gas() != null) SimAchievements.direct(p, SimAchievements.HOT_WORK_PERMIT, "", 1);
    }
}
