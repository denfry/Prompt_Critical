package dev.denfry.promptcritical.mod.automation;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationSubsystem;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.automation.ProcessSwitchKind;
import dev.denfry.promptcritical.sim.automation.RelayBank;
import dev.denfry.promptcritical.sim.automation.StationMode;
import dev.denfry.promptcritical.sim.automation.TimeRelayType;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * BE блока автоматики (03d §3d.3–3d.4, 03c §3c.2): двойник {@link AutomationUnit} в {@link AutomationSubsystem}, провода
 * на клеммах, входы от мира и показания в состоянии блока. Кнопка нажата полсекунды на клик; переключатель — клик на
 * положение (Shift — назад); ключ с подтверждением (SBO, §3c.2.4): клик выбирает, затем Shift + клик — ВКЛ, клик —
 * ОТКЛ, импульс 1 с. Щит настроек — у {@link AutomationPanelBlockEntity}.
 */
public class AutomationBlockEntity extends DeviceBlockEntity implements WiredDevice, FoundryDevice {
    /** Нажатие кнопки, тиков. */
    public static final int PRESS_TICKS = 10;
    /** Ключ: ожидание поворота после выбора и длина импульса команды, тиков. */
    public static final int KEY_ARM_TICKS = 100, KEY_PULSE_TICKS = 20;
    /** Т90 термокаталитического датчика метана 15 с: τ = Т90 / ln 10. */
    public static final double GAS_TAU_S = 15 / Math.log(10);

    protected final AutomationKind kind;
    private final List<Wire> wires = new ArrayList<>();
    private boolean registered, unloading;
    protected int ticks;
    private int pressTicks, keyArmed, keyPulse, keyDir;
    private double lel;
    /** Клиент: угол подвижной части (шкала, щётки, барабан, рукоятка), градусы; сервер — последний отосланный. */
    private float angle, sentAngle = Float.NaN;
    /** Клиент: показываемый угол для плавного рендера. */
    public float shownAngle = Float.NaN;

    public AutomationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, AutomationKind kind) {
        super(type, pos, state);
        this.kind = kind;
    }

    public AutomationKind kind() {
        return kind;
    }

    /** Двойник; {@code null} — биндинга нет или блок ещё не в топологии. */
    @Nullable
    public AutomationUnit unit() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null || simId().isNone() || !b.world().topology().exists(simId())) return null;
        AutomationSubsystem a = b.world().subsystem(AutomationSubsystem.class);
        return a == null ? null : a.unit(simId(), kind);
    }

    // ================================================================ WiredDevice

    @Override
    public BlockPos wiredPos() {
        return worldPosition;
    }

    @Override
    public AutomationKind wiredKind() {
        return kind;
    }

    @Override
    public Direction wiredFacing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock pc ? pc.facing(s) : Direction.NORTH;
    }

    @Override
    public List<Wire> wires() {
        return wires;
    }

    @Override
    public void wiresChanged() {
        sync();
    }

    @Override
    @Nullable
    public AutomationUnit automationUnit(AutomationSubsystem automation) {
        return simId().isNone() ? null : automation.unit(simId(), kind);
    }

    // ================================================================ жизненный цикл

    @Override
    public void onLoad() {
        super.onLoad();
        unloading = false;
        if (level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) {
                b.automation().register(worldPosition);
                registered = true;
            }
        } else if (level != null) {
            ClientWires.add(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        unloading = true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel s) {
            SimLevelBinding b = registered ? SimBindings.get(s) : null;
            if (b != null) {
                if (!unloading) b.automation().deviceRemoved(this);
                b.automation().unregister(worldPosition);
            }
            registered = false;
        } else {
            ClientWires.remove(this);
        }
    }

    // ================================================================ тик

    @Override
    public void tickDevice() {
        ticks++;
        if (level == null || level.isClientSide) return;
        AutomationUnit u = unit();
        if (u != null) serverTick(u);
    }

    protected void serverTick(AutomationUnit u) {
        switch (kind) {
            case RELAY, RELAY_LATCHING -> setFlag(PcProperties.PICKED, u.relayPicked());
            case TIME_RELAY -> {
                boolean energized = u.coilVoltage() >= 0.75 * RelayBank.NOMINAL_V;
                setFlag(PcProperties.DONE, u.timerOutput());
                setFlag(PcProperties.RUNNING, energized && !u.timerOutput());
                TimeRelayType t = TimeRelayType.values()[Math.floorMod(u.intParam(AutomationUnit.I_VARIANT), 3)];
                double frac = Math.log(u.param(AutomationUnit.D_SET) / t.minS) / Math.log(t.maxS / t.minS);
                syncAngle((float) (270 * Math.max(0, Math.min(1, frac))), 1);
            }
            case STEPPING_SWITCH -> syncAngle((u.stepperPosition() - 1) * 360f / RelayBank.STEPPER_POSITIONS, 1);
            case CAM_CONTROLLER -> {
                u.setInput(AutomationUnit.IN_MAIN, shaftRpm());
                syncAngle((float) u.camAngleDeg(), 3);
            }
            case PROCESS_SWITCH -> {
                ProcessSwitchKind k = ProcessSwitchKind.values()[Math.floorMod(u.intParam(AutomationUnit.I_VARIANT), 4)];
                u.setInput(AutomationUnit.IN_MAIN, ProcessSources.read((ServerLevel) level,
                        AutomationWallBlock.mountPos(getBlockState(), worldPosition), k));
                setFlag(PcProperties.ALARM, u.processTripped());
                setEnum(PcProperties.PROCESS_KIND, PcProperties.ProcessKind.values()[k.ordinal()]);
            }
            case PUSHBUTTON -> {
                if (pressTicks > 0) pressTicks--;
                u.setInput(AutomationUnit.IN_MAIN, pressTicks > 0 ? 1 : 0);
                setFlag(BlockStateProperties.LIT, pressTicks > 0);
            }
            case SELECTOR_SWITCH -> syncAngle(45f * (u.intParam(AutomationUnit.I_POSITION) - 1), 1);
            case KEY_SWITCH -> {
                if (keyArmed > 0) keyArmed--;
                if (keyPulse > 0) keyPulse--;
                u.setInput(AutomationUnit.IN_MAIN, keyPulse > 0 && keyDir > 0 ? 1 : 0);
                u.setInput(AutomationUnit.IN_AUX, keyPulse > 0 && keyDir < 0 ? 1 : 0);
                syncAngle(keyPulse > 0 ? 45f * keyDir : 0, 1);
            }
            case INDICATOR_LAMP -> setFlag(BlockStateProperties.LIT, u.loadEnergized(0));
            case REDSTONE_INTERFACE -> {
                u.setInput(AutomationUnit.IN_MAIN, redstoneIn() > 0 ? 1 : 0);
                boolean on = u.loadEnergized(0);
                BlockState s = getBlockState();
                if (s.getValue(BlockStateProperties.POWERED) != on) {
                    level.setBlock(worldPosition, s.setValue(BlockStateProperties.POWERED, on), Block.UPDATE_ALL);
                    level.updateNeighborsAt(AutomationWallBlock.mountPos(s, worldPosition), s.getBlock());
                }
            }
            case GAS_DETECTOR -> {
                double target = ProcessSources.methaneLel((ServerLevel) level, worldPosition);
                lel += (target - lel) * -Math.expm1(-0.05 / GAS_TAU_S);
                u.setInput(AutomationUnit.IN_MAIN, lel);
                PcProperties.GasAlarm a = lel >= u.param(AutomationUnit.D_RETURN) ? PcProperties.GasAlarm.ALARM
                        : lel >= u.param(AutomationUnit.D_SET) ? PcProperties.GasAlarm.WARN : PcProperties.GasAlarm.NONE;
                setEnum(PcProperties.GAS_ALARM, a);
            }
            case ANALOG_COMPARATOR -> setFlag(PcProperties.ALARM, u.output() > 0.5);
            case MANUAL_STATION -> setFlag(PcProperties.MANUAL, u.stationMode() == StationMode.MANUAL);
            default -> {
            }
        }
    }

    /** Вал Create слева или справа от барабана (ось барабана — поперёк фасада). */
    private double shaftRpm() {
        Direction f = wiredFacing();
        for (Direction d : new Direction[] {f.getClockWise(), f.getCounterClockWise()}) {
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof KineticBlockEntity k && k.getSpeed() != 0) return k.getSpeed();
        }
        return 0;
    }

    /** Сигнал редстоуна на входе переходника: соседи кроме стены, на которой он висит, и фасада. */
    private int redstoneIn() {
        Direction f = wiredFacing();
        int max = 0;
        for (Direction d : Direction.values()) {
            if (d == f || d == f.getOpposite()) continue;
            max = Math.max(max, level.getSignal(worldPosition.relative(d), d));
        }
        return max;
    }

    protected void setFlag(BooleanProperty prop, boolean value) {
        BlockState s = getBlockState();
        if (s.hasProperty(prop) && s.getValue(prop) != value) level.setBlock(worldPosition, s.setValue(prop, value), Block.UPDATE_CLIENTS);
    }

    private <E extends Enum<E> & net.minecraft.util.StringRepresentable> void setEnum(
            net.minecraft.world.level.block.state.properties.EnumProperty<E> prop, E value) {
        BlockState s = getBlockState();
        if (s.hasProperty(prop) && s.getValue(prop) != value) level.setBlock(worldPosition, s.setValue(prop, value), Block.UPDATE_CLIENTS);
    }

    /** Отослать угол подвижной части клиенту при изменении больше {@code step} градусов. */
    private void syncAngle(float a, float step) {
        angle = a;
        if (Float.isNaN(sentAngle) || Math.abs(a - sentAngle) >= step) {
            sentAngle = a;
            sync();
        }
    }

    /** Угол подвижной части (клиент). */
    public float angle() {
        return angle;
    }

    protected void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ================================================================ руки

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
        AutomationUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        switch (kind) {
            case PUSHBUTTON -> {
                press();
                level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, 1.2f);
            }
            case SELECTOR_SWITCH -> {
                int pos = Math.floorMod(u.intParam(AutomationUnit.I_POSITION) + (player.isShiftKeyDown() ? -1 : 1), 3);
                u.setIntParam(AutomationUnit.I_POSITION, pos);
                level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 1.1f);
                player.displayClientMessage(AutomationText.msg(AutomationText.SELECTOR, pos + 1), true);
            }
            case KEY_SWITCH -> turnKey(player, player.isShiftKeyDown());
            case INDICATOR_LAMP -> player.displayClientMessage(AutomationText.msg(AutomationText.LAMP_STATUS,
                    AutomationText.msg(u.loadEnergized(0) ? AutomationText.ON : AutomationText.OFF)), true);
            case RELAY, RELAY_LATCHING -> player.displayClientMessage(AutomationText.msg(AutomationText.RELAY_STATUS,
                    AutomationPanels.relayStateName(u.relayState()), u.relayOps()), true);
            case REDSTONE_INTERFACE -> player.displayClientMessage(AutomationText.msg(AutomationText.INTERFACE_STATUS,
                    AutomationText.msg(u.loadEnergized(0) ? AutomationText.ON : AutomationText.OFF), redstoneIn()), true);
            default -> {
                return InteractionResult.PASS;
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Нажать кнопку (игрок, тест). */
    public void press() {
        pressTicks = PRESS_TICKS;
    }

    /** Ключ SBO: выбрать, затем повернуть ({@code right} — ВКЛ). */
    public void turnKey(@Nullable Player player, boolean right) {
        if (keyArmed <= 0) {
            keyArmed = KEY_ARM_TICKS;
            if (player != null) player.displayClientMessage(AutomationText.msg(AutomationText.KEY_ARMED), true);
            return;
        }
        keyArmed = 0;
        keyDir = right ? 1 : -1;
        keyPulse = KEY_PULSE_TICKS;
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, right ? 1.3f : 0.9f);
        if (player != null) player.displayClientMessage(AutomationText.msg(right ? AutomationText.KEY_ON : AutomationText.KEY_OFF), true);
    }

    // ================================================================ сохранение и клиент

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("wires", WiredDevice.saveWires(wires));
        tag.putDouble("lel", lel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        WiredDevice.loadWires(tag, wires);
        lel = tag.getDouble("lel");
        if (tag.contains("angle")) angle = tag.getFloat("angle");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("wires", WiredDevice.saveWires(wires));
        tag.putFloat("angle", angle);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
