package dev.denfry.promptcritical.mod.gas;

import dev.denfry.promptcritical.mod.acoustics.MachineAcoustics;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.sound.PcSfx;
import dev.denfry.promptcritical.sim.automation.plc.AntiSurge;
import dev.denfry.promptcritical.sim.core.SimClock;
import dev.denfry.promptcritical.sim.gasprocessing.CentrifugalCompressor;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Центробежный компрессор газопровода ({@link CentrifugalCompressor}, 09 §9.8.5) на валу Create, вал — в лицевую грань
 * контроллера; номинал — {@value #NOMINAL_RPM} об/мин вала. Ротор разгоняется к оборотам вала за 20 с (инерция).
 * Коллектор 5 бар → газопровод 8 бар; линейный клапан — с щита, рецикл ведёт встроенный антипомпажный регулятор —
 * тот же блок {@code PC_ANTISURGE}, что в ПЛК (03d §3d.5.12), с паспортной линией помпажа; на щите — ручной режим,
 * запас b_ctrl и скорость закрытия.
 *
 * <p>Помпаж — физика, а не тревога (09 §9.8.5): каждый цикл — реверс потока, удар в корпусе, износ подшипника машины
 * в вибродиагностике 04a ×10 ({@value #BEARING_DAMAGE_PER_CYCLE} D_b за цикл [BALANCE]), вибрация ротора на щите;
 * {@value CentrifugalCompressor#SURGE_LIFE_S} с помпажа в сумме разрушают машину. Пуск из стоянки до подачи в
 * газопровод 90 % расчётного расхода без единого цикла помпажа — достижение {@code no_surge} (11). Компрессор не
 * перекачивает жидкости и газы Create: это станция газопровода, её продукт — режим (известное ограничение).
 */
public final class CentrifugalCompressorBlockEntity extends KineticMachineBlockEntity<ShaftInput> implements PanelHost {
    public static final double NOMINAL_RPM = 64, RATED_W = 500e3, SPIN_UP_S = 20, BEARING_DAMAGE_PER_CYCLE = 0.002;
    /** Событие {@code sim_event}: пуск без помпажа (тег {@code cycles}), и физический факт помпажа (тег — циклы). */
    public static final String NO_SURGE = "promptcritical:no_surge", SURGE = "promptcritical:compressor_surge";
    private static final double DT = SimClock.FAST_DT;

    private final ShaftInput shaft = new ShaftInput();
    private final CentrifugalCompressor c = new CentrifugalCompressor();
    private final AntiSurge.Instance controller = new AntiSurge.Instance();
    private long tick;
    private double speed, lineCmd, manualRecycle = 1;
    private boolean auto = true, armed = true, surgeAnnounced, cleanStart;
    private long startCycles;

    public CentrifugalCompressorBlockEntity(BlockPos pos, BlockState state) {
        super(GasBlockEntities.CENTRIFUGAL_COMPRESSOR.get(), pos, state);
        controller.in[AntiSurge.KS] = CentrifugalCompressor.SURGE_KS;
    }

    public CentrifugalCompressor compressor() {
        return c;
    }

    public AntiSurge.Instance controller() {
        return controller;
    }

    /** Последний пуск вышел в линию без единого цикла помпажа (достижение {@code no_surge}). */
    public boolean cleanStart() {
        return cleanStart;
    }

    public boolean auto() {
        return auto;
    }

    public void setAuto(boolean on) {
        auto = on;
        setChanged();
    }

    public void setLine(double position) {
        lineCmd = Math.clamp(position, 0, 1);
        setChanged();
    }

    public void setManualRecycle(double position) {
        manualRecycle = Math.clamp(position, 0, 1);
        setChanged();
    }

    @Override
    protected ShaftInput unit(SimLevelBinding binding) {
        return shaft;
    }

    @Override
    protected ShaftInput shaft(ShaftInput u) {
        return u;
    }

    @Override
    protected double powerW(ShaftInput u) {
        return u.rpm(tick) > 0 ? Math.min(RATED_W, c.powerW()) : 0;
    }

    @Override
    protected double ratedW() {
        return RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return NOMINAL_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding binding, ShaftInput u) {
        tick = binding.world().clock().tick();
        double target = Math.min(1.1, u.rpm(tick) / NOMINAL_RPM);
        step(target);
    }

    /** Один шаг FAST: ротор к {@code shaftSpeed} (доля номинала), регулятор, компрессор, последствия. */
    public void step(double shaftSpeed) {
        double ramp = DT / SPIN_UP_S;
        speed += Math.clamp(shaftSpeed - speed, -ramp, ramp);
        double[] in = controller.in;
        in[AntiSurge.DPO] = c.orificeDpKpa();
        in[AntiSurge.PS] = c.suctionPa() / 1e5;
        in[AntiSurge.PD] = c.dischargePa() / 1e5;
        in[AntiSurge.MAN] = auto ? 0 : 1;
        in[AntiSurge.XM] = 10 * manualRecycle;
        controller.scan(DT);
        c.step(DT, speed, controller.y() / 10, lineCmd);
        if (!(level instanceof ServerLevel sl)) return;
        if (speed < 0.02) {
            armed = true;
            startCycles = c.surgeCycles();
        }
        int cycles = c.surgeCyclesThisStep();
        if (cycles > 0) onSurge(sl, cycles);
        if (!c.surging()) surgeAnnounced = false;
        if (armed && c.lineFlowM3s() >= 0.9 * CentrifugalCompressor.Q_DESIGN) {
            armed = false;
            cleanStart = c.surgeCycles() == startCycles;
            if (cleanStart) SimAchievements.nearby(sl, worldPosition, NO_SURGE, "cycles", 0);
        }
        if (c.destroyed()) {
            destroy(sl);
            return;
        }
        if (level.getGameTime() % 10 == 0) setStateFlag(PcProperties.RUNNING, speed > 0.05);
        setChanged();
    }

    private void onSurge(ServerLevel sl, int cycles) {
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1, z = worldPosition.getZ() + 0.5;
        sl.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6f, 1.6f);
        sl.sendParticles(ParticleTypes.POOF, x, y + 0.5, z, 6, 0.6, 0.3, 0.6, 0.02);
        MachineAcoustics.setBearingDamage(sl, worldPosition,
                MachineAcoustics.bearingDamage(sl, worldPosition) + BEARING_DAMAGE_PER_CYCLE * cycles);
        if (!surgeAnnounced) {
            surgeAnnounced = true;
            SimAchievements.nearby(sl, worldPosition, SURGE, "cycles", c.surgeCycles());
        }
    }

    /** Длительный помпаж: проточная часть разрушена — машина разлетается, без разрушения блоков вокруг. */
    private void destroy(ServerLevel sl) {
        BlockPos p = worldPosition;
        MachineAcoustics.forget(sl, p);
        sl.destroyBlock(p, false);
        sl.explode(null, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 2f, Level.ExplosionInteraction.NONE);
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return GasPanels.CENTRIFUGAL;
    }

    @Override
    public void readPanel(int[] out) {
        out[GasPanels.C_SPEED_PCT] = (int) Math.round(speed * 100);
        out[GasPanels.C_PD_X100] = (int) Math.round(c.dischargePa() / 1e3);
        out[GasPanels.C_FLOW_X1000] = (int) Math.round(c.flowM3s() * 1000);
        out[GasPanels.C_LINE_X1000] = (int) Math.round(c.lineFlowM3s() * 1000);
        out[GasPanels.C_RECYCLE_PCT] = (int) Math.round(c.recyclePosition() * 100);
        out[GasPanels.C_SS_X1000] = (int) Math.round(Math.min(10, c.surgeRatio()) * 1000);
        out[GasPanels.C_CYCLES] = (int) Math.min(Integer.MAX_VALUE, c.surgeCycles());
        out[GasPanels.C_VIB_X10] = (int) Math.round(c.vibrationMmS() * 10);
        out[GasPanels.C_DAMAGE_PERMIL] = (int) Math.round(c.damage() * 1000);
        out[GasPanels.C_POWER_KW] = (int) Math.round(c.powerW() / 1000);
        out[GasPanels.C_BCTRL_X1000] = (int) Math.round(controller.in[AntiSurge.BCTRL] * 1000);
        out[GasPanels.C_RATE_X1000] = (int) Math.round(controller.in[AntiSurge.RATE] * 1000);
        out[GasPanels.C_LINE_VALVE_PCT] = (int) Math.round(lineCmd * 100);
        out[GasPanels.C_XM_PCT] = (int) Math.round(manualRecycle * 100);
        int f = 0;
        if (speed > 0.05) f |= 1 << GasPanels.CF_RUNNING;
        if (c.surging()) f |= 1 << GasPanels.CF_SURGE;
        if (auto) f |= 1 << GasPanels.CF_AUTO;
        if (c.lineFlowM3s() >= 0.9 * CentrifugalCompressor.Q_DESIGN) f |= 1 << GasPanels.CF_ONLINE;
        out[GasPanels.C_FLAGS] = f;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        double[] in = controller.in;
        switch (id) {
            case GasPanels.CB_LINE_DOWN -> setLine(lineCmd - 0.05);
            case GasPanels.CB_LINE_UP -> setLine(lineCmd + 0.05);
            case GasPanels.CB_MODE -> {
                // безударно: ручное задание берёт текущее положение рецикла
                if (auto) manualRecycle = Math.round(c.recyclePosition() * 20) / 20.0;
                setAuto(!auto);
            }
            case GasPanels.CB_REC_DOWN -> setManualRecycle(manualRecycle - 0.05);
            case GasPanels.CB_REC_UP -> setManualRecycle(manualRecycle + 0.05);
            case GasPanels.CB_MARGIN_DOWN -> in[AntiSurge.BCTRL] = Math.max(0, in[AntiSurge.BCTRL] - 0.01);
            case GasPanels.CB_MARGIN_UP -> in[AntiSurge.BCTRL] = Math.min(0.5, in[AntiSurge.BCTRL] + 0.01);
            case GasPanels.CB_RATE_DOWN -> in[AntiSurge.RATE] = Math.max(0.002, in[AntiSurge.RATE] - 0.002);
            case GasPanels.CB_RATE_UP -> in[AntiSurge.RATE] = Math.min(0.2, in[AntiSurge.RATE] + 0.002);
            default -> {
            }
        }
        setChanged();
    }

    // ------------------------------------------------------------------ клиент, руки, сохранение

    /** Свист проточной части, чаще и выше с оборотами; при помпаже клиент слышит удары от сервера. */
    @Override
    protected void clientTick() {
        if (!runningState()) return;
        MachineFx fx = fx();
        if (fx == null) return;
        float k = Math.min(2f, Math.abs(getSpeed()) / 64f);
        if (fx.chance(0.5)) fx.sound(fx.at(8, 16, 16), PcSfx.COMPRESSOR.get(), 0.3f + 0.2f * k, 0.8f + 0.4f * k);
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

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) return;
        tag.put("compressor", doubles(c.state()));
        tag.put("controller", doubles(controller.state()));
        tag.put("controller_in", doubles(controller.in));
        tag.putDouble("speed", speed);
        tag.putDouble("line", lineCmd);
        tag.putDouble("manual", manualRecycle);
        tag.putBoolean("auto", auto);
        tag.putBoolean("armed", armed);
        tag.putLong("start_cycles", startCycles);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket || !tag.contains("compressor")) return;
        c.restore(doubles(tag.getList("compressor", Tag.TAG_DOUBLE)));
        controller.restore(doubles(tag.getList("controller", Tag.TAG_DOUBLE)));
        double[] in = doubles(tag.getList("controller_in", Tag.TAG_DOUBLE));
        System.arraycopy(in, 0, controller.in, 0, Math.min(in.length, controller.in.length));
        speed = tag.getDouble("speed");
        lineCmd = tag.getDouble("line");
        manualRecycle = tag.getDouble("manual");
        auto = tag.getBoolean("auto");
        armed = tag.getBoolean("armed");
        startCycles = tag.getLong("start_cycles");
    }

    private static ListTag doubles(double[] a) {
        ListTag l = new ListTag();
        for (double v : a) l.add(net.minecraft.nbt.DoubleTag.valueOf(v));
        return l;
    }

    private static double[] doubles(ListTag l) {
        double[] a = new double[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.getDouble(i);
        return a;
    }
}
