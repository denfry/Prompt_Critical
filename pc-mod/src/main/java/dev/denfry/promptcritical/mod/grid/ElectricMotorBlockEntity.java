package dev.denfry.promptcritical.mod.grid;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import dev.denfry.promptcritical.mod.binding.DeviceHost;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.core.DeviceMode;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.GridUnit;
import dev.denfry.promptcritical.sim.grid.MotorUnit;
import dev.denfry.promptcritical.sim.grid.RelaySetting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * {@code electric_motor} (07 §7.6.3) — источник вращения Create от сети: вал спереди, выводы — остальные грани. Скорость
 * Create — ступень редуктора × доля скорости ротора с квантованием R3 (как у паровой машины); ёмкость
 * C_SU = P_ном·min(1, v²/0,81)/W_PER_SU; доля нагрузки сети Create возвращается двигателю моментом. Два места под модули
 * 49, АЧР, 27, 51.
 */
public class ElectricMotorBlockEntity extends GeneratingKineticBlockEntity implements DeviceHost, GridDevice, FoundryDevice,
        PanelHost {
    public static final BooleanProperty MV = PcProperties.MEDIUM_VOLTAGE;
    public static final int SLOTS = 2;
    private static final double HYSTERESIS = 0.02;

    private final ItemStackHandler modules = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof RelayModuleItem m && accepts(m.kind());
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private SimId simId = SimId.NONE;
    private boolean chunkUnloading, registered;
    private int level4, ticks;
    private float capacityPerRpm, lastRpm;

    public ElectricMotorBlockEntity(BlockPos pos, BlockState state) {
        super(GridBlockEntities.ELECTRIC_MOTOR.get(), pos, state);
    }

    static boolean accepts(RelaySetting.Kind k) {
        return k == RelaySetting.Kind.THERMAL || k == RelaySetting.Kind.UFLS || k == RelaySetting.Kind.UNDERVOLTAGE
                || k == RelaySetting.Kind.OVERCURRENT || k == RelaySetting.Kind.UNDERFREQUENCY;
    }

    @Nullable
    public MotorUnit unit() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null || simId.isNone() || !b.world().topology().exists(simId)) return null;
        GridSubsystem g = b.world().subsystem(GridSubsystem.class);
        return g == null ? null : g.motor(simId);
    }

    @Override
    @Nullable
    public GridUnit gridUnit(GridSubsystem grid) {
        return simId.isNone() ? null : grid.motor(simId);
    }

    private Direction facing() {
        return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        GridDevice.faces(out, worldPosition, facing(), 0, 0, 0, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP,
                Direction.DOWN);
        return List.of(out);
    }

    // --- источник Create ---

    /** Обороты вала Create, RPM. */
    public float outputRpm() {
        MotorUnit u = unit();
        int gear = u == null ? MotorUnit.GEARS[2] : u.gearRpm();
        return gear * level4 / 4f;
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
        if (!(level instanceof ServerLevel)) return;
        ticks++;
        MotorUnit u = unit();
        if (u == null) return;
        if (ticks % 20 == 9) u.setRelays(settings());
        double r = u.speedPu();
        int next = level4(r);
        if (next > level4) next = Math.max(level4, level4(r / (1 + HYSTERESIS)));
        else if (next < level4) next = Math.min(level4, level4(r * (1 + HYSTERESIS)));
        level4 = next;
        float rpm = outputRpm();
        double wPerSu = ServerConfig.W_PER_SU.get();
        float cap = (float) (u.capacityW() / wPerSu / (rpm > 0 ? rpm : u.gearRpm()));
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
        u.setLoadPowerW(load);
        if (ticks % 5 == 0) {
            setFlag(PcProperties.RUNNING, level4 > 0);
            setFlag(MV, u.mediumVoltage());
        }
    }

    private void setFlag(BooleanProperty prop, boolean value) {
        BlockState s = getBlockState();
        if (s.hasProperty(prop) && s.getValue(prop) != value) level.setBlock(worldPosition, s.setValue(prop, value), Block.UPDATE_CLIENTS);
    }

    private static int level4(double r) {
        if (r >= 0.95) return 4;
        if (r >= 0.2) return Math.max(1, Math.min(3, (int) Math.floor(4 * r)));
        return 0;
    }

    List<RelaySetting> settings() {
        List<RelaySetting> out = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            RelaySetting s = RelayModuleItem.setting(modules.getStackInSlot(i));
            if (s != null) out.add(s);
        }
        return out;
    }

    // --- руки ---

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

    @Override
    public InteractionResult use(Player player) {
        MotorUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        u.setContactor(!u.contactorClosed());
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return GridPanels.MOTOR;
    }

    @Override
    public Component panelTitle() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public boolean panelValid(Player player) {
        return PanelHost.near(this, player);
    }

    @Override
    @Nullable
    public IItemHandlerModifiable panelItems() {
        return modules;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        MotorUnit u = unit();
        if (u == null) return;
        out[GridPanels.M_SPEED_PERMIL] = (int) Math.round(u.speedPu() * 1000);
        out[GridPanels.M_CURRENT_A10] = (int) Math.round(u.currentA() * 10);
        out[GridPanels.M_P_KW10] = (int) Math.round(u.powerW() / 100);
        out[GridPanels.M_TEMP_C10] = (int) Math.round(u.windingTemperatureC() * 10);
        out[GridPanels.M_STATE] = u.state().ordinal();
        out[GridPanels.M_FLAGS] = (u.contactorClosed() ? 1 : 0) | (u.mediumVoltage() ? 2 : 0);
        out[GridPanels.M_GEAR] = u.gearRpm();
        out[GridPanels.M_OUT_RPM10] = Math.round(outputRpm() * 10);
        out[GridPanels.M_LOAD_KW10] = (int) Math.round(u.capacityW() > 0 ? lastLoadW(u) / 100 : 0);
    }

    private double lastLoadW(MotorUnit u) {
        return Math.max(0, u.powerW());
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        MotorUnit u = unit();
        if (u == null) return;
        switch (id) {
            case GridPanels.BUTTON_START -> u.setContactor(true);
            case GridPanels.BUTTON_STOP -> u.setContactor(false);
            case GridPanels.BUTTON_GEAR -> {
                u.cycleGear();
                player.displayClientMessage(GridText.msg(GridText.GEAR, u.gearRpm()), true);
            }
            case GridPanels.BUTTON_CLASS -> {
                if (u.speedPu() > 0.05 || u.contactorClosed()) {
                    player.displayClientMessage(GridText.msg(GridText.STOP_FIRST), true);
                    return;
                }
                u.setMediumVoltage(!u.mediumVoltage());
                player.displayClientMessage(GridText.msg(GridText.VOLTAGE_CLASS, u.mediumVoltage() ? 6 : 0.4), true);
            }
            default -> {
            }
        }
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
            if (b != null) {
                b.attach(this);
                b.grids().register(worldPosition);
                registered = true;
            }
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
            if (b != null) {
                b.detach(this, !chunkUnloading);
                if (registered) b.grids().unregister(worldPosition);
            }
            registered = false;
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) {
            tag.putLong("sim_id", simId.raw());
            tag.put("modules", modules.serializeNBT(registries));
        }
        tag.putFloat("rpm", lastRpm);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket) {
            simId = new SimId(tag.getLong("sim_id"));
            if (tag.contains("modules")) modules.deserializeNBT(registries, tag.getCompound("modules"));
        }
        lastRpm = tag.getFloat("rpm");
    }
}
