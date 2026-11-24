package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.grid.MachinePower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Перегрузочная машина ВВЭР-70 ({@code vver70_refuelling_machine_real}): мост на рельсах над шахтой реактора и
 * бассейном выдержки, тележка, телескопическая штанга с захватом. Реактор ВВЭР-70 и бассейн должны стоять под путём
 * моста (модель: мост ходит по z −6…6, тележка по x −4…4 блоков от контроллера) и в досягаемости штанги
 * ({@value #MAX_DEPTH_M} м ниже пола машины до головки ТВС).
 *
 * <p>ПКМ пустой рукой — цикл выгрузки: самая выгоревшая ТВС из остановленного реактора в свободное гнездо бассейна;
 * Shift + ПКМ — цикл загрузки: свежая ТВС из бассейна в свободную позицию зоны. Цикл: мост и тележка к источнику,
 * штанга вниз, захват, вверх, переезд, вниз (в бассейн — через проём с поднятым затвором), расцепка, вверх, на
 * стоянку. Сборка всё время в штанге под водой (шахта реактора при перегрузке затоплена и соединена с бассейном) —
 * излучение от неё здесь не считается. Без питания машина стоит на тормозах и продолжает с того же места.
 * Скорости сжаты против настоящих (≈ полчаса на сборку): мост {@value #TRAVEL_TICKS_PER_M} тиков на метр, штанга
 * {@value #MAST_TICKS_PER_M} [игровое сжатие].
 */
public final class RefuellingMachineBlockEntity extends DeviceBlockEntity implements FoundryDevice, RealScaleBlockEntity {
    /** Привод моста, тележки и штанги, Вт [est]. */
    public static final double DRIVES_W = 15e3;
    public static final int TRAVEL_TICKS_PER_M = 80, MAST_TICKS_PER_M = 100, GRIP_TICKS = 60;
    /** Ход моста и тележки, блоков от контроллера (шапка vver70_fuel_handling.author.js). */
    public static final double BRIDGE_Z = 6, TROLLEY_X = 4;
    /**
     * Ход штанги ниже пола машины до низа захвата: четыре трубы по 3 м выходят из внешней штанги, низ которой на
     * 0,75 м над полом, захват — ещё 0,5 м под трубой.
     */
    public static final double MAX_DEPTH_M = (4 * 48 - 4) / 16.0;
    /** Головки ТВС в зоне ВВЭР-70 над контроллером корпуса, м [est]; в бассейне — верх стеллажей. */
    public static final double REACTOR_HEADS_M = 8;

    public enum Phase {
        IDLE, TO_SOURCE, LOWER_SOURCE, GRIP, RAISE_SOURCE, TO_TARGET, LOWER_TARGET, RELEASE, RAISE_TARGET, PARK;

        boolean atSource() {
            return this == LOWER_SOURCE || this == GRIP || this == RAISE_SOURCE;
        }

        boolean atTarget() {
            return this == LOWER_TARGET || this == RELEASE || this == RAISE_TARGET;
        }
    }

    /** Положение: мост по z, тележка по x (блоки модели), штанга вниз, м. */
    public record Pose(double z, double x, double depth) {
        static final Pose PARK = new Pose(0, 0, 0);

        Pose top() {
            return new Pose(z, x, 0);
        }

        static Pose lerp(Pose a, Pose b, double f) {
            return new Pose(a.z + (b.z - a.z) * f, a.x + (b.x - a.x) * f, a.depth + (b.depth - a.depth) * f);
        }
    }

    private Phase phase = Phase.IDLE;
    private int elapsed;
    private boolean unloading, resumed, moving;
    private Pose source = Pose.PARK, target = Pose.PARK;
    private ItemStack held = ItemStack.EMPTY;
    @Nullable
    private BlockPos reactorPos, poolPos;

    public RefuellingMachineBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.REFUELLING_MACHINE.get(), pos, state);
    }

    public Phase phase() {
        return phase;
    }

    public ItemStack held() {
        return held;
    }

    public boolean busy() {
        return phase != Phase.IDLE;
    }

    /** Длительность фазы, тиков. */
    public int duration(Phase p) {
        return switch (p) {
            case IDLE -> 1;
            case TO_SOURCE -> travel(Pose.PARK, source);
            case LOWER_SOURCE, RAISE_SOURCE -> mast(source.depth);
            case GRIP, RELEASE -> GRIP_TICKS;
            case TO_TARGET -> travel(resumed ? Pose.PARK : source, target);
            case LOWER_TARGET, RAISE_TARGET -> mast(target.depth);
            case PARK -> travel(target, Pose.PARK);
        };
    }

    private static int travel(Pose a, Pose b) {
        return Math.max(40, (int) Math.round(Math.hypot(b.z - a.z, b.x - a.x) * TRAVEL_TICKS_PER_M));
    }

    private static int mast(double depth) {
        return Math.max(20, (int) Math.round(depth * MAST_TICKS_PER_M));
    }

    /** Положение моста, тележки и штанги на {@code partial} тика вперёд (клиент рисует по нему). */
    public Pose pose(float partial) {
        double f = Math.min(1, (elapsed + (moving ? partial : 0)) / (double) duration(phase));
        return switch (phase) {
            case IDLE -> Pose.PARK;
            case TO_SOURCE -> Pose.lerp(Pose.PARK, source.top(), f);
            case LOWER_SOURCE -> Pose.lerp(source.top(), source, f);
            case GRIP -> source;
            case RAISE_SOURCE -> Pose.lerp(source, source.top(), f);
            case TO_TARGET -> Pose.lerp(resumed ? Pose.PARK : source.top(), target.top(), f);
            case LOWER_TARGET -> Pose.lerp(target.top(), target, f);
            case RELEASE -> target;
            case RAISE_TARGET -> Pose.lerp(target, target.top(), f);
            case PARK -> Pose.lerp(target.top(), Pose.PARK, f);
        };
    }

    // --- поиск реактора и бассейна под путём моста ---

    /** Мировое смещение → клетки модели (фасад на север), как поворот цельной модели наоборот. */
    private double[] local(BlockPos p) {
        double th = Math.toRadians(-(wholeModelFacing().toYRot() + 180));
        double dx = p.getX() - worldPosition.getX(), dz = p.getZ() - worldPosition.getZ();
        double c = Math.cos(th), s = Math.sin(th);
        return new double[] {Math.round(dx * c - dz * s), Math.round(dx * s + dz * c)};
    }

    private void scan() {
        reactorPos = null;
        poolPos = null;
        Direction f = wholeModelFacing();
        // путь моста — 9 × 13 клеток; ищем на 24 блока вниз от пола машины
        for (int lx = -4; lx <= 4; lx++)
            for (int lz = -6; lz <= 6; lz++) {
                BlockPos col = worldPosition.offset(rotate(lx, lz, f));
                for (int dy = 0; dy >= -24; dy--) {
                    BlockEntity be = level.getBlockEntity(col.above(dy));
                    if (be == null || !be.getBlockPos().equals(col.above(dy))) continue;
                    if (reactorPos == null && be instanceof PwrReactorBlockEntity r && "vver70_reactor_real".equals(r.wholeModelId()))
                        reactorPos = be.getBlockPos();
                    if (poolPos == null && be instanceof SpentFuelPoolBlockEntity) poolPos = be.getBlockPos();
                }
            }
    }

    private static BlockPos rotate(int lx, int lz, Direction f) {
        return switch (f) {
            case SOUTH -> new BlockPos(-lx, 0, -lz);
            case EAST -> new BlockPos(-lz, 0, lx);
            case WEST -> new BlockPos(lz, 0, -lx);
            default -> new BlockPos(lx, 0, lz);
        };
    }

    @Nullable
    private PwrReactorBlockEntity reactor() {
        return reactorPos != null && level.getBlockEntity(reactorPos) instanceof PwrReactorBlockEntity r ? r : null;
    }

    @Nullable
    private SpentFuelPoolBlockEntity pool() {
        return poolPos != null && level.getBlockEntity(poolPos) instanceof SpentFuelPoolBlockEntity p ? p : null;
    }

    /** Положение захвата над головками ТВС цели; {@code null} — вне хода моста, тележки или штанги. */
    @Nullable
    private Pose reach(BlockPos at, double headsM) {
        double[] l = local(at);
        double depth = worldPosition.getY() - (at.getY() + headsM);
        if (Math.abs(l[0]) > TROLLEY_X || Math.abs(l[1]) > BRIDGE_Z || depth < 0 || depth > MAX_DEPTH_M) return null;
        return new Pose(l[1], l[0], depth);
    }

    /**
     * Начать цикл: выгрузка (реактор → бассейн) или загрузка (бассейн → реактор).
     *
     * @return ключ сообщения об отказе или {@code null} — цикл пошёл
     */
    @Nullable
    public String start(boolean unload) {
        if (busy()) return "rm_busy";
        if (!MachinePower.powered(level, worldPosition, DRIVES_W)) return "rm_no_power";
        scan();
        PwrReactorBlockEntity r = reactor();
        SpentFuelPoolBlockEntity p = pool();
        if (r == null) return "rm_no_reactor";
        if (p == null) return "rm_no_pool";
        Pose atReactor = reach(reactorPos, REACTOR_HEADS_M), atPool = reach(poolPos, dev.denfry.promptcritical.sim.npp.SpentFuelPool.VVER70.rackTopM());
        if (atReactor == null || atPool == null) return "rm_out_of_reach";
        if (!r.refuellable()) return "rm_not_shut_down";
        boolean resume = !held.isEmpty();
        if (!resume) {
            if (unload) {
                if (r.mostBurnt() < 0) return "rm_core_empty";
                if (!p.hasFreeRack()) return "rm_pool_full";
            } else if (!p.hasFresh()) return "rm_no_fresh";
        } else {
            // в захвате уже сборка: везти её туда, куда она шла
            unload = held.has(dev.denfry.promptcritical.mod.environment.ActivityData.ACTIVITY.get());
        }
        unloading = unload;
        source = unload ? atReactor : atPool;
        target = unload ? atPool : atReactor;
        resumed = resume;
        enter(resume ? Phase.TO_TARGET : Phase.TO_SOURCE);
        return null;
    }

    private void enter(Phase p) {
        phase = p;
        elapsed = 0;
        SpentFuelPoolBlockEntity pool = pool();
        // затвор проёма поднят, пока штанга в бассейне
        boolean inPool = unloading ? p.atTarget() : p.atSource();
        if (pool != null && pool.gateOpen() != inPool) pool.setGate(inPool);
        sync();
    }

    @Override
    public void tickDevice() {
        if (level.isClientSide) {
            if (moving && phase != Phase.IDLE && elapsed < duration(phase)) elapsed++;
            return;
        }
        if (phase == Phase.IDLE) return;
        boolean powered = MachinePower.powered(level, worldPosition, DRIVES_W);
        if (powered != moving) {
            moving = powered;
            sync();
        }
        if (!powered) return;
        if (++elapsed < duration(phase)) return;
        finish(phase);
    }

    private void finish(Phase p) {
        PwrReactorBlockEntity r = reactor();
        SpentFuelPoolBlockEntity pool = pool();
        switch (p) {
            case GRIP -> {
                held = unloading ? (r != null ? r.dischargeMostBurnt() : ItemStack.EMPTY) : (pool != null ? pool.takeFresh() : ItemStack.EMPTY);
                if (held.isEmpty()) {
                    // брать нечего: штанга поднимается пустой и машина уходит на стоянку
                    say("rm_nothing");
                    target = source;
                    enter(Phase.RAISE_TARGET);
                    return;
                }
                level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 0.6f);
            }
            case RELEASE -> {
                boolean done = unloading ? pool != null && pool.store(held) : r != null && r.loadAssembly(held);
                if (done) {
                    held = ItemStack.EMPTY;
                    say(unloading ? "rm_done_unload" : "rm_done_load");
                    level.playSound(null, worldPosition, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.8f, 0.8f);
                } else {
                    // некуда поставить: сборка остаётся в штанге, следующий пуск довезёт её
                    say(unloading ? "rm_pool_full" : "rm_core_full");
                }
            }
            default -> {
            }
        }
        if (p == Phase.PARK) {
            phase = Phase.IDLE;
            elapsed = 0;
            setRunning(false);
            sync();
            return;
        }
        setRunning(true);
        enter(Phase.values()[p.ordinal() + 1]);
    }

    private void setRunning(boolean on) {
        BlockState st = getBlockState();
        if (st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING) != on)
            level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, on), 3);
    }

    private void say(String key) {
        if (level instanceof ServerLevel s) {
            var c = worldPosition.getCenter();
            for (var pl : s.getPlayers(pl -> pl.distanceToSqr(c) < 32 * 32)) pl.displayClientMessage(NppText.msg(key), true);
        }
    }

    private void sync() {
        setChanged();
        if (level instanceof ServerLevel sl) sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** ПКМ — цикл выгрузки, Shift + ПКМ — цикл загрузки; занята — сводка. */
    @Override
    public InteractionResult use(Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (busy()) {
            player.displayClientMessage(NppText.msg("rm_status", NppText.msg("rm_phase_" + phase.name().toLowerCase(java.util.Locale.ROOT)),
                    NppText.msg(unloading ? "rm_unloading" : "rm_loading")), true);
            return InteractionResult.SUCCESS;
        }
        String refusal = start(!player.isShiftKeyDown());
        player.displayClientMessage(NppText.msg(refusal == null ? (unloading ? "rm_start_unload" : "rm_start_load") : refusal), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag);
        if (!held.isEmpty()) tag.put("held", held.save(registries));
        if (reactorPos != null) tag.putLong("reactor", reactorPos.asLong());
        if (poolPos != null) tag.putLong("pool", poolPos.asLong());
    }

    private void write(CompoundTag tag) {
        tag.putString("phase", phase.name());
        tag.putInt("elapsed", elapsed);
        tag.putBoolean("unloading", unloading);
        tag.putBoolean("resumed", resumed);
        tag.putBoolean("moving", moving);
        tag.putDouble("sz", source.z);
        tag.putDouble("sx", source.x);
        tag.putDouble("sd", source.depth);
        tag.putDouble("tz", target.z);
        tag.putDouble("tx", target.x);
        tag.putDouble("td", target.depth);
        tag.putBoolean("carry", !held.isEmpty());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("phase")) {
            try {
                phase = Phase.valueOf(tag.getString("phase"));
            } catch (IllegalArgumentException e) {
                phase = Phase.IDLE;
            }
        }
        elapsed = tag.getInt("elapsed");
        unloading = tag.getBoolean("unloading");
        resumed = tag.getBoolean("resumed");
        moving = tag.getBoolean("moving");
        source = new Pose(tag.getDouble("sz"), tag.getDouble("sx"), tag.getDouble("sd"));
        target = new Pose(tag.getDouble("tz"), tag.getDouble("tx"), tag.getDouble("td"));
        if (tag.contains("held")) held = ItemStack.parse(registries, tag.getCompound("held")).orElse(ItemStack.EMPTY);
        else if (level == null || !level.isClientSide) held = ItemStack.EMPTY;
        clientCarry = tag.getBoolean("carry");
        reactorPos = tag.contains("reactor") ? BlockPos.of(tag.getLong("reactor")) : null;
        poolPos = tag.contains("pool") ? BlockPos.of(tag.getLong("pool")) : null;
    }

    private boolean clientCarry;

    /** Клиент: сборка в захвате (по последней синхронизации). */
    public boolean clientCarry() {
        return clientCarry;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        write(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
