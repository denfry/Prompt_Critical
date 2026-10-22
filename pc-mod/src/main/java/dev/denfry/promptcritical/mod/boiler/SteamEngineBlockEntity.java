package dev.denfry.promptcritical.mod.boiler;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.ops.OpsActions;
import dev.denfry.promptcritical.mod.ops.OpsMachine;
import dev.denfry.promptcritical.mod.ops.OpsWorld;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.core.CommandSource;
import dev.denfry.promptcritical.sim.core.SimCommand;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.ops.Baseline;
import dev.denfry.promptcritical.sim.ops.MaintenanceFaultKind;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.UnitKind;
import dev.denfry.promptcritical.sim.plant.EngineUnit;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * BE паровой машины (07 §7.9.7) на контроллере. Двойник — {@link EngineUnit}: BE подключает его к котлу, клетки
 * которого касаются машины (паропровод — часть машины; трубы высокого давления 00 появятся позже), передаёт
 * нагрузку сети Create от {@link KineticPortBlockEntity} и считает выход на вал (03a §3a.7.3):
 *
 * <ul>
 *   <li>скорость R3: n_set при ω ≥ 0,95 ω₀; n_set·⌊4ω/ω₀⌋/4 (не меньше ¼) при 0,2 ω₀ ≤ ω &lt; 0,95 ω₀; 0 ниже;
 *       гистерезис 2 %. n_set — передача 16/32/64 RPM (ключ Create по муфте);</li>
 *   <li>ёмкость C_SU = P_расп/W_PER_SU, где P_расп — мощность на валу при номинальных оборотах и полностью открытой
 *       заслонке при нынешнем давлении в котле; обновление не чаще 1 Гц, мёртвая зона 2 %.</li>
 * </ul>
 *
 * <p>Износ и пусконаладка — {@link OpsMachine}: заплавленный подшипник останавливает вал, сальник парит, стопорный
 * вентиль открывается только с разрешения пуска (13 §13.6.6).
 */
public class SteamEngineBlockEntity extends DeviceBlockEntity implements PressureSource, PanelHost, OpsMachine {
    public static final int[] GEARS = {16, 32, 64};
    private static final int LINK_PERIOD = 20, SYNC_PERIOD = 2;
    private static final double HYSTERESIS = 0.02, CAPACITY_DEADBAND = 0.02;
    /** p_раб машины = p_раб котла I0 (избыточное), Па. */
    public static final double WORKING_PA = 1.2e6;
    /** Износ набивки, с которого сальник заметно парит (порог «внимание» строки PACKING_LEAK). */
    private static final double PACKING_STEAM = 0.6, TICK_S = 0.05;

    private int gear = 1, level4, ticks;
    private double portLoadW;
    private float capacityPerRpm, lastCapacitySu = -1;
    private long lastCapacityTick = Long.MIN_VALUE / 2;
    // видимое клиенту
    private float omega, crank, chestPa;
    private boolean valveOpen;
    private float sentOmega = -1;
    private int sentFlags = -1;
    /** Клиент: угол коленвала, догоняемый по ω между пакетами. */
    private float clientCrank;

    public SteamEngineBlockEntity(BlockPos pos, BlockState state) {
        super(BoilerBlockEntities.STEAM_ENGINE.get(), pos, state);
    }

    @Nullable
    public EngineUnit unit() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null || simId().isNone() || !b.world().topology().exists(simId())) return null;
        SteamPlant plant = b.world().subsystem(SteamPlant.class);
        return plant == null ? null : plant.addEngine(simId());
    }

    public Direction facing() {
        return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    // --- порт Create ---

    /** Обороты вала Create, RPM (0 — вал не крутится). */
    public float portRpm() {
        return GEARS[gear] * level4 / 4f;
    }

    /** Ёмкость на 1 RPM; пока вал стоит — на номинальной передаче (видно в очках инженера). */
    public float portCapacityPerRpm() {
        return capacityPerRpm;
    }

    /** Доля нагрузки сети Create, Вт (порт пишет каждый тик). */
    public void setPortLoad(double watts) {
        portLoadW = watts;
    }

    /** Ключ Create по муфте: следующая передача. */
    public InteractionResult cycleGear(@Nullable Player player) {
        gear = (gear + 1) % GEARS.length;
        setChanged();
        if (player != null) player.displayClientMessage(Component.translatable(BoilerText.GEAR, GEARS[gear]), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public double gaugePressurePa() {
        return Float.isNaN(chestPa) ? 0 : Math.max(0, chestPa - SteamPlant.ATMOSPHERE_PA);
    }

    /** Клиент: угол коленвала для рендера с поправкой на долю тика. */
    public float renderCrank(float partialTick) {
        return clientCrank + omega * 0.05f * partialTick;
    }

    public float omega() {
        return omega;
    }

    // --- тик ---

    public void tick() {
        ticks++;
        if (level.isClientSide) {
            float before = clientCrank;
            clientCrank = (float) ((clientCrank + omega * 0.05) % (2 * Math.PI));
            exhaust(before);
            return;
        }
        SimLevelBinding b = SimBindings.get((ServerLevel) level);
        EngineUnit e = unit();
        if (b == null || e == null) return;
        if (ticks % LINK_PERIOD == 1) b.world().subsystem(SteamPlant.class).connect(simId(), findBoiler());
        e.setLoadPower(portLoadW);
        operations(e);

        double r = e.omega() / e.nominalOmega();
        int next = level4(r);
        if (next > level4) next = Math.max(level4, level4(r / (1 + HYSTERESIS)));
        else if (next < level4) next = Math.min(level4, level4(r * (1 + HYSTERESIS)));
        level4 = next;

        double wPerSu = ServerConfig.W_PER_SU.get();
        float su = (float) (e.capacityW() / wPerSu);
        long now = level.getGameTime();
        if (now - lastCapacityTick >= 20 && (lastCapacitySu < 0 || Math.abs(su - lastCapacitySu) > CAPACITY_DEADBAND * Math.max(lastCapacitySu, 1))) {
            lastCapacitySu = su;
            lastCapacityTick = now;
        }
        float rpm = portRpm();
        capacityPerRpm = lastCapacitySu <= 0 ? 0 : lastCapacitySu / (rpm > 0 ? rpm : GEARS[gear]);

        omega = (float) e.omega();
        crank = (float) e.crankAngle();
        chestPa = (float) e.chestPressurePa();
        valveOpen = e.stopValveOpen();
        boolean running = level4 > 0;
        BlockState s = getBlockState();
        if (s.getValue(PcProperties.RUNNING) != running) level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, running), Block.UPDATE_CLIENTS);
        if (ticks % SYNC_PERIOD == 0) syncIfChanged();
    }

    /** Износ, заклинивание, сальник и пробный пуск (13 §13.6.5–13.6.7). */
    private void operations(EngineUnit e) {
        OpsSubsystem ops = OpsWorld.ops(level);
        OpsSubsystem.Unit u = OpsWorld.ensure(level, simId(), UnitKind.STEAM_ENGINE, WORKING_PA, e.dashpotFitted());
        if (ops == null || u == null) return;
        boolean running = e.omega() > 0.05 * e.nominalOmega();
        u.setRunning(running, false, Double.NaN);
        e.setSeized(u.seized());
        double load = e.capacityW() > 0 ? portLoadW / e.capacityW() : 0;
        ops.observeTrial(simId(), TICK_S, running, load, false, opsCurrent());
        int i = OpsWorld.index(u, MaintenanceFaultKind.PACKING_LEAK);
        if (running && i >= 0 && ticks % 8 == 0 && u.panel().source(i).value() >= PACKING_STEAM
                && level instanceof ServerLevel sl) {
            Vec3 p = opsBearingPos();
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, p.x, p.y, p.z, 2, 0.15, 0.05, 0.15, 0.02);
        }
    }

    /** Клуб пара из выхлопной трубы, когда поршень проходит мёртвую точку (каждые полоборота). */
    private void exhaust(float before) {
        if (!valveOpen || omega < 0.5f || (int) (before / Math.PI) == (int) (clientCrank / Math.PI)) return;
        MachineFx fx = MachineFx.of(level, worldPosition, facing());
        if (fx == null) return;
        fx.puff(fx.at(-0.5, 32.5, -4), new Vec3(0, 0.12, 0), 3);
        fx.sound(fx.at(-0.5, 32, -4), BoilerSounds.steam(), 0.15f, 1.7f);
    }

    private static int level4(double r) {
        if (r >= 0.95) return 4;
        if (r >= 0.2) return Math.max(1, Math.min(3, (int) Math.floor(4 * r)));
        return 0;
    }

    /** Котёл, клетка которого касается гранью любой клетки машины; {@link SimId#NONE} — нет. */
    private SimId findBoiler() {
        Footprint fp = PcBlocks.FP_ENGINE;
        for (Footprint.Cell c : fp.cells(worldPosition, facing())) {
            for (Direction d : Direction.values()) {
                FireTubeBoilerBlockEntity boiler = FireTubeBoilerBlockEntity.at(level, c.pos().relative(d));
                if (boiler != null && !boiler.simId().isNone()) return boiler.simId();
            }
        }
        return SimId.NONE;
    }

    // --- игрок ---

    /** Провернуть машину ломом с мёртвой точки. Только сервер. */
    public InteractionResult barOver(Player player) {
        SimLevelBinding b = SimBindings.get((ServerLevel) level);
        if (b == null || unit() == null) return InteractionResult.PASS;
        b.submit(new SimCommand.Operate(simId(), SteamPlant.BAR_OVER), new CommandSource.Panel(player.getUUID(), worldPosition.asLong()));
        player.displayClientMessage(Component.translatable(BoilerText.BARRED_OVER), true);
        level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.3f, 1.6f);
        return InteractionResult.SUCCESS;
    }

    /** Открыть или закрыть стопорный вентиль. Только сервер. */
    public InteractionResult toggleValve(Player player) {
        SimLevelBinding b = SimBindings.get((ServerLevel) level);
        EngineUnit e = unit();
        if (b == null || e == null) return InteractionResult.PASS;
        if (!e.stopValveOpen() && !OpsActions.startAllowed(player, level, this)) return InteractionResult.SUCCESS;
        if (e.boiler().isNone()) player.displayClientMessage(Component.translatable(BoilerText.NO_BOILER), true);
        else player.displayClientMessage(Component.translatable(e.stopValveOpen() ? BoilerText.VALVE_CLOSED : BoilerText.VALVE_OPEN), true);
        b.submit(new SimCommand.Operate(simId(), SteamPlant.TOGGLE_STOP_VALVE), new CommandSource.Panel(player.getUUID(), worldPosition.asLong()));
        level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 0.7f);
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return BoilerPanels.ENGINE;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[BoilerPanels.E_GEAR] = GEARS[gear];
        EngineUnit e = unit();
        if (e == null) return;
        out[BoilerPanels.E_RPM10] = (int) Math.round(e.omega() * 60 / (2 * Math.PI) * 10);
        out[BoilerPanels.E_CHEST_KPA] = (int) Math.round(gaugePressurePa() / 1000);
        out[BoilerPanels.E_OUT_RPM10] = Math.round(portRpm() * 10);
        out[BoilerPanels.E_CAPACITY_SU] = Math.round(lastCapacitySu);
        out[BoilerPanels.E_LOAD_KW10] = (int) Math.round(portLoadW / 100);
        out[BoilerPanels.E_STEAM_KGH] = FireTubeBoilerBlockEntity.finite(e.steamFlowKgS() * 3600);
        out[BoilerPanels.E_THROTTLE_PERMIL] = FireTubeBoilerBlockEntity.finite(e.throttle() * 1000);
        out[BoilerPanels.E_FLAGS] = (e.stopValveOpen() ? 1 << BoilerPanels.EF_VALVE : 0)
                | (e.dashpotFitted() ? 1 << BoilerPanels.EF_DASHPOT : 0)
                | (e.boiler().isNone() ? 0 : 1 << BoilerPanels.EF_BOILER)
                | (level4 > 0 ? 1 << BoilerPanels.EF_RUNNING : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case BoilerPanels.BUTTON_VALVE -> toggleValve(player);
            case BoilerPanels.BUTTON_BAR -> barOver(player);
            case BoilerPanels.BUTTON_GEAR -> cycleGear(player);
            default -> {
            }
        }
    }

    public ItemInteractionResult fitDashpot(Player player, ItemStack stack) {
        SimLevelBinding b = SimBindings.get((ServerLevel) level);
        EngineUnit e = unit();
        if (b == null || e == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (e.dashpotFitted()) {
            player.displayClientMessage(Component.translatable(BoilerText.DASHPOT_PRESENT), true);
            return ItemInteractionResult.CONSUME;
        }
        b.submit(new SimCommand.Operate(simId(), SteamPlant.FIT_GOVERNOR_DASHPOT), new CommandSource.Panel(player.getUUID(), worldPosition.asLong()));
        stack.consume(1, player);
        player.displayClientMessage(Component.translatable(BoilerText.DASHPOT_FITTED), true);
        return ItemInteractionResult.SUCCESS;
    }

    // --- эксплуатация ---

    @Override
    public UnitKind opsKind() {
        return UnitKind.STEAM_ENGINE;
    }

    @Override
    public boolean opsStopped() {
        EngineUnit e = level != null && !level.isClientSide ? unit() : null;
        return e == null ? omega < 0.05f : e.omega() < 0.05;
    }

    /** Коренной подшипник у маховика. */
    @Override
    public Vec3 opsBearingPos() {
        return BoilerGeometry.world(worldPosition, facing(), 8, 12, 8);
    }

    @Override
    public Baseline opsCurrent() {
        EngineUnit e = unit();
        return e == null ? Baseline.NONE : new Baseline(Double.NaN, Double.NaN, Double.NaN, Double.NaN, e.omega(), Double.NaN);
    }

    /** Строка состояния для отладки и тестов. */
    public String describe() {
        return String.format(Locale.ROOT, "omega=%.2f crank=%.2f chest=%.0f rpm=%.1f cap/rpm=%.2f load=%.0f", omega, crank,
                chestPa, portRpm(), capacityPerRpm, portLoadW);
    }

    // --- сохранение и клиент ---

    private int flags() {
        return (valveOpen ? 1 : 0) | level4 << 1;
    }

    private void syncIfChanged() {
        int f = flags();
        if (f == sentFlags && Math.abs(omega - sentOmega) < 0.05f) return;
        sentFlags = f;
        sentOmega = omega;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("gear", gear);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("gear")) gear = Math.floorMod(tag.getInt("gear"), GEARS.length);
        if (tag.contains("view_omega")) {
            omega = tag.getFloat("view_omega");
            crank = tag.getFloat("view_crank");
            clientCrank = crank;
            chestPa = tag.getFloat("view_chest");
            int f = tag.getInt("view_flags");
            valveOpen = (f & 1) != 0;
            level4 = f >> 1;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("gear", gear);
        tag.putFloat("view_omega", omega);
        tag.putFloat("view_crank", crank);
        tag.putFloat("view_chest", chestPa);
        tag.putInt("view_flags", flags());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
