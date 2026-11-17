package dev.denfry.promptcritical.mod.powerhouse;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.core.CommandSource;
import dev.denfry.promptcritical.sim.core.DeviceMode;
import dev.denfry.promptcritical.sim.core.SimCommand;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.plant.EngineUnit;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Паровая турбина с противодавлением ({@link dev.denfry.promptcritical.sim.combustion.SteamTurbine}) — источник вращения
 * Create. Пар берёт от котла, которого касается гранью (жаротрубного или водотрубного: барабан, экран, щит). Вал —
 * через встроенный редуктор: 3000 об/мин турбины → передача 64/128/256 об/мин Create (ключом Create). Правый клик —
 * стопорный клапан; с Shift — взвод автомата безопасности.
 */
public class SteamTurbineBlockEntity extends GeneratingKineticBlockEntity
        implements dev.denfry.promptcritical.mod.binding.DeviceHost, FoundryDevice {
    public static final int[] GEARS = {64, 128, 256};
    private static final int LINK_PERIOD = 20;
    private static final double HYSTERESIS = 0.02;

    private SimId simId = SimId.NONE;
    private boolean chunkUnloading;
    private int gear = 2, level4, ticks;
    private float capacityPerRpm, lastRpm;

    public SteamTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(PowerhouseBlockEntities.STEAM_TURBINE.get(), pos, state);
    }

    @Nullable
    public EngineUnit unit() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null || simId.isNone() || !b.world().topology().exists(simId)) return null;
        SteamPlant plant = b.world().subsystem(SteamPlant.class);
        return plant == null ? null : plant.addTurbine(simId);
    }

    private Direction facing() {
        return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    // --- источник Create ---

    public float outputRpm() {
        return GEARS[gear] * level4 / 4f;
    }

    @Override
    public float getGeneratedSpeed() {
        return level != null && level.isClientSide ? lastRpm : outputRpm();
    }

    @Override
    public float calculateAddedStressCapacity() {
        lastCapacityProvided = capacityPerRpm;
        return capacityPerRpm;
    }

    @Override
    public void tickDevice() {
        tick();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel sl)) return;
        ticks++;
        SimLevelBinding b = SimBindings.get(sl);
        EngineUnit e = unit();
        if (b == null || e == null) return;
        if (ticks % LINK_PERIOD == 1) {
            b.world().subsystem(SteamPlant.class).connect(simId, findBoiler());
            e.setExhaustPressure(exhaustPa(sl));
        }

        double r = e.omega() / e.nominalOmega();
        int next = level4(r);
        if (next > level4) next = Math.max(level4, level4(r / (1 + HYSTERESIS)));
        else if (next < level4) next = Math.min(level4, level4(r * (1 + HYSTERESIS)));
        level4 = next;
        float rpm = outputRpm();
        double wPerSu = ServerConfig.W_PER_SU.get();
        float cap = (float) (e.capacityW() / wPerSu / (rpm > 0 ? rpm : GEARS[gear]));
        if (rpm != lastRpm) {
            lastRpm = rpm;
            capacityPerRpm = cap;
            updateGeneratedRotation();
        } else if (Math.abs(cap - capacityPerRpm) > 0.02 * Math.max(capacityPerRpm, 0.01f) && ticks % 20 == 0) {
            capacityPerRpm = cap;
            if (hasNetwork() && getOrCreateNetwork() != null) notifyStressCapacityChange(calculateAddedStressCapacity());
        }
        double load = 0;
        float speed = Math.abs(getSpeed());
        if (speed > 0 && !isOverStressed() && capacity > 0) {
            double own = lastCapacityProvided * speed;
            load = wPerSu * Math.min(stress, capacity) * own / capacity;
        }
        e.setLoadPower(load);
        if (ticks % 5 == 0) {
            BlockState s = getBlockState();
            boolean running = level4 > 0;
            if (s.getValue(PcProperties.RUNNING) != running) level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, running), Block.UPDATE_CLIENTS);
        }
    }

    private static int level4(double r) {
        if (r >= 0.95) return 4;
        if (r >= 0.2) return Math.max(1, Math.min(3, (int) Math.floor(4 * r)));
        return 0;
    }

    /**
     * Противодавление: конденсатор под турбиной, который касается воды, держит вакуум по температуре этой воды;
     * без него — выхлоп в атмосферу.
     */
    private double exhaustPa(ServerLevel sl) {
        BlockPos c = worldPosition.below();
        if (!sl.getBlockState(c).is(PcBlocks.CONDENSER.get())) return SteamPlant.EXHAUST_PA;
        boolean water = sl.getFluidState(c.below()).is(net.minecraft.tags.FluidTags.WATER);
        for (Direction d : Direction.Plane.HORIZONTAL) water |= sl.getFluidState(c.relative(d)).is(net.minecraft.tags.FluidTags.WATER);
        if (!water) return SteamPlant.EXHAUST_PA;
        double tw = dev.denfry.promptcritical.mod.environment.EnvironmentService.waterTempC(sl, c) + 273.15;
        return dev.denfry.promptcritical.sim.combustion.SteamTurbine.condenserPressurePa(
                dev.denfry.promptcritical.sim.thermo.WaterProperties.tables(), tw);
    }

    /** Котёл, которого турбина касается гранью; {@link SimId#NONE} — нет. */
    private SimId findBoiler() {
        for (Direction d : Direction.values()) {
            BlockPos n = worldPosition.relative(d);
            FireTubeBoilerBlockEntity fire = FireTubeBoilerBlockEntity.at(level, n);
            if (fire != null && !fire.simId().isNone()) return fire.simId();
            BlockState s = level.getBlockState(n);
            if (s.is(PcBlocks.STEAM_DRUM.get()) || s.is(PcBlocks.BOILER_WALL.get()) || s.is(PcBlocks.WATER_TUBE_BOILER.get())) {
                WaterTubeBoilerBlockEntity wt = WaterTubeBoilerBlockEntity.of(level, n);
                if (wt != null && wt.shape() != null && !wt.simId().isNone()) return wt.simId();
            }
        }
        return SimId.NONE;
    }

    // --- игрок ---

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
        if (stack.is(com.simibubi.create.AllItems.WRENCH.get())) {
            gear = (gear + 1) % GEARS.length;
            setChanged();
            player.displayClientMessage(PowerhouseText.msg("turbine.gear", GEARS[gear]), true);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Стопорный клапан; с Shift — взвод автомата безопасности. Сообщение — обороты, мощность, пар. */
    @Override
    public InteractionResult use(Player player) {
        SimLevelBinding b = SimBindings.get((ServerLevel) level);
        EngineUnit e = unit();
        if (b == null || e == null) return InteractionResult.PASS;
        CommandSource src = new CommandSource.Panel(player.getUUID(), worldPosition.asLong());
        if (player.isShiftKeyDown()) {
            b.submit(new SimCommand.Operate(simId, SteamPlant.RESET_TRIP), src);
            player.displayClientMessage(PowerhouseText.msg(e.turbine().tripped() ? "turbine.reset" : "turbine.not_tripped"), true);
            return InteractionResult.SUCCESS;
        }
        if (e.boiler().isNone()) {
            player.displayClientMessage(PowerhouseText.msg("turbine.no_boiler").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        b.submit(new SimCommand.Operate(simId, SteamPlant.TOGGLE_STOP_VALVE), src);
        player.displayClientMessage(PowerhouseText.msg(e.stopValveOpen() ? "turbine.valve_closed" : "turbine.valve_open",
                String.format(Locale.ROOT, "%.0f", 60 * e.omega() / (2 * Math.PI)),
                String.format(Locale.ROOT, "%.0f", e.shaftPowerW() / 1e3), String.format(Locale.ROOT, "%.2f", e.steamFlowKgS()),
                String.format(Locale.ROOT, "%.1f", e.exhaustPressurePa() / 1e3)), true);
        level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 0.6f);
        return InteractionResult.SUCCESS;
    }

    // --- DeviceHost ---

    @Override
    public SimId simId() {
        return simId;
    }

    @Override
    public void bindSimId(SimId id) {
        if (!id.equals(simId)) {
            simId = id;
            setChanged();
        }
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel) level;
    }

    @Override
    public BlockPos pos() {
        return worldPosition;
    }

    @Override
    public String deviceType() {
        return BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).toString();
    }

    @Override
    public DeviceMode mode() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null || simId.isNone() || !b.world().topology().exists(simId)) return DeviceMode.LOCAL;
        return b.world().topology().deviceMode(simId);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        chunkUnloading = false;
        if (level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) b.attach(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        chunkUnloading = true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) b.detach(this, !chunkUnloading);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) {
            tag.putLong("sim_id", simId.raw());
            tag.putInt("gear", gear);
        }
        tag.putFloat("rpm", lastRpm);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket) {
            simId = new SimId(tag.getLong("sim_id"));
            if (tag.contains("gear")) gear = Math.floorMod(tag.getInt("gear"), GEARS.length);
        }
        lastRpm = tag.getFloat("rpm");
    }
}
