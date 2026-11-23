package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.manufacturing.ForgeEvent;
import dev.denfry.promptcritical.sim.manufacturing.Forging;
import dev.denfry.promptcritical.sim.manufacturing.Workpiece;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Механический молот {@code power_hammer} и кривошипный пресс {@code crank_press} (18 §18.6.3, §18.7, механизированная
 * ступень). Энергия хода — от мощности вала ({@link Forging#strokeJ}), доля энергии задаётся на щите. Машина куёт
 * сама сериями ходов, пока заготовка в ковочном интервале и не достигла заданной толщины: холодную не бьёт (трещин от
 * машины нет — их делает человек, который не вернул заготовку в печь), после события, отличного от OK, встаёт.
 */
public final class PowerHammerBlockEntity extends PoweredSmithyDevice {
    /** Параметры машины: мощность и обороты по номиналу, ходов в минуту, скорость деформации, длительность серии. */
    public enum Kind {
        /** Пневмомолот на 20 кВт при 64 об/мин [BALANCE]. */
        HAMMER(20_000, 64, Forging.POWER_BLOWS_PER_MIN, Forging.POWER_STRAIN_RATE, 5),
        /** Кривошипный пресс на 30 кВт при 32 об/мин: реже, медленнее, металл течёт легче [BALANCE]. */
        PRESS(30_000, 32, Forging.PRESS_STROKES_PER_MIN, Forging.PRESS_STRAIN_RATE, 6);

        public final double ratedW, nominalRpm, strokesPerMin, strainRate, seriesS;

        Kind(double ratedW, double nominalRpm, double strokesPerMin, double strainRate, double seriesS) {
            this.ratedW = ratedW;
            this.nominalRpm = nominalRpm;
            this.strokesPerMin = strokesPerMin;
            this.strainRate = strainRate;
            this.seriesS = seriesS;
        }

        public int strokesPerSeries() {
            return (int) Math.max(1, Math.round(strokesPerMin * seriesS / 60));
        }
    }

    /** Машина не бьёт ближе этого к нижней границе интервала, К: запас на остывание за серию [моё]. */
    public static final double COLD_MARGIN_K = 30;
    /** Пределы и шаг заданной толщины, мм. */
    public static final int TARGET_MIN_MM = 4, TARGET_MAX_MM = 400, TARGET_STEP_MM = 5;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT};

    private final Kind kind;
    private Forging.Op op = Forging.Op.DRAW;
    private int targetMm = 20;
    private int energyPct = 100;
    private boolean on;
    private int lastEvent = -1;
    private int series;
    private double sinceSeriesS;

    public PowerHammerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Kind kind) {
        super(type, pos, state, ROLES, (slot, s) -> Smithy.forgeable(s));
        this.kind = kind;
    }

    public static PowerHammerBlockEntity hammer(BlockPos pos, BlockState state) {
        return new PowerHammerBlockEntity(SmithyBlockEntities.POWER_HAMMER.get(), pos, state, Kind.HAMMER);
    }

    public static PowerHammerBlockEntity press(BlockPos pos, BlockState state) {
        return new PowerHammerBlockEntity(SmithyBlockEntities.CRANK_PRESS.get(), pos, state, Kind.PRESS);
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected double ratedW() {
        return kind.ratedW;
    }

    @Override
    protected double nominalRpm() {
        return kind.nominalRpm;
    }

    public void setMode(Forging.Op o, int targetMm, int energyPct, boolean on) {
        this.op = o == Forging.Op.UPSET ? Forging.Op.UPSET : Forging.Op.DRAW;
        this.targetMm = Math.max(TARGET_MIN_MM, Math.min(TARGET_MAX_MM, targetMm));
        this.energyPct = Math.max(25, Math.min(100, energyPct));
        this.on = on;
        setChanged();
    }

    public boolean on() {
        return on;
    }

    public int series() {
        return series;
    }

    /** Заданная толщина достигнута для текущей операции. */
    private boolean reached(Workpiece w) {
        double a = w.aM() * 1000;
        return op == Forging.Op.UPSET ? a >= targetMm : a <= targetMm;
    }

    private boolean inRange(Workpiece w) {
        return w.tempK() >= Forging.range(w.grade().family()).lowerK() + COLD_MARGIN_K;
    }

    @Override
    protected boolean working() {
        Workpiece w = piece();
        return on && w != null && inRange(w) && !reached(w);
    }

    private Workpiece piece() {
        ItemStack s = inv.getStackInSlot(0);
        return level == null || s.getCount() != 1 ? null : Smithy.read(s, level.getGameTime());
    }

    @Override
    protected void second(double shaftW) {
        Workpiece w = piece();
        if (!on || shaftW <= 0 || w == null || !inRange(w) || reached(w)) {
            sinceSeriesS = 0;
            return;
        }
        sinceSeriesS += 1;
        if (sinceSeriesS < kind.seriesS) return;
        sinceSeriesS = 0;
        double strokeJ = Forging.strokeJ(shaftW, kind.strokesPerMin) * energyPct / 100.0;
        // остывание между сериями уже учтено временем предмета: серия сама не остужает
        Forging.Result r = Forging.series(w, op, strokeJ, kind.strokesPerSeries(), kind.strainRate, 0, WorkpieceData.AMBIENT_K);
        inv.setStackInSlot(0, Smithy.stack(r.piece(), level.getGameTime()));
        ForgeEvent last = r.events().get(r.events().size() - 1);
        lastEvent = last.ordinal();
        series++;
        if (last != ForgeEvent.OK) on = false;
        if (level instanceof ServerLevel sl) {
            sl.playSound(null, worldPosition, kind == Kind.HAMMER ? SoundEvents.ANVIL_LAND : SoundEvents.PISTON_EXTEND,
                    SoundSource.BLOCKS, kind == Kind.HAMMER ? 0.5f : 0.7f, kind == Kind.HAMMER ? 0.7f : 0.5f);
            sl.sendParticles(ParticleTypes.LAVA, worldPosition.getX() + 0.5, worldPosition.getY() + 0.7,
                    worldPosition.getZ() + 0.5, 3, 0.15, 0.02, 0.15, 0.01);
        }
        setChanged();
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case SmithyPanels.BUTTON_DRAW -> op = Forging.Op.DRAW;
            case SmithyPanels.BUTTON_UPSET -> op = Forging.Op.UPSET;
            case SmithyPanels.P_BUTTON_THINNER -> targetMm = Math.max(TARGET_MIN_MM, targetMm - TARGET_STEP_MM);
            case SmithyPanels.P_BUTTON_THICKER -> targetMm = Math.min(TARGET_MAX_MM, targetMm + TARGET_STEP_MM);
            case SmithyPanels.P_BUTTON_ENERGY -> energyPct = energyPct >= 100 ? 25 : energyPct + 25;
            case SmithyPanels.P_BUTTON_RUN -> on = !on;
            default -> { }
        }
        setChanged();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return Smithy.forgeable(stack);
    }

    @Override
    public PanelLayout panelLayout() {
        return kind == Kind.HAMMER ? SmithyPanels.POWER_HAMMER : SmithyPanels.CRANK_PRESS;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        double w0 = shaftW();
        out[SmithyPanels.P_RPM10] = rpm10();
        out[SmithyPanels.P_POWER_KW10] = (int) Math.round(w0 / 100);
        out[SmithyPanels.P_STROKE_J] = (int) Math.round(Forging.strokeJ(w0, kind.strokesPerMin) * energyPct / 100.0);
        out[SmithyPanels.P_OP] = op.ordinal();
        out[SmithyPanels.P_TARGET_MM] = targetMm;
        out[SmithyPanels.P_ENERGY_PCT] = energyPct;
        out[SmithyPanels.P_EVENT] = lastEvent;
        out[SmithyPanels.P_SERIES] = series;
        int f = on ? 1 << SmithyPanels.PF_ON : 0;
        if (w0 > 0) f |= 1 << SmithyPanels.PF_SHAFT;
        Workpiece w = piece();
        if (w != null) {
            f |= 1 << SmithyPanels.PF_PIECE;
            Forging.Range range = Forging.range(w.grade().family());
            out[SmithyPanels.P_TEMP_C] = (int) Math.round(w.tempK() - 273.15);
            out[SmithyPanels.P_A_MM10] = (int) Math.round(w.aM() * 10_000);
            out[SmithyPanels.P_LEN_MM] = (int) Math.round(w.lengthM() * 1000);
            out[SmithyPanels.P_LOWER_C] = (int) Math.round(range.lowerK() - 273.15);
            if (inRange(w)) f |= 1 << SmithyPanels.PF_IN_RANGE;
            else f |= 1 << SmithyPanels.PF_COLD;
            if (reached(w)) f |= 1 << SmithyPanels.PF_DONE;
        }
        out[SmithyPanels.FLAGS] = f;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) return;
        tag.putString("op", op.name());
        tag.putInt("target", targetMm);
        tag.putInt("energy", energyPct);
        tag.putBoolean("on", on);
        tag.putInt("last", lastEvent);
        tag.putInt("series", series);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        op = "UPSET".equals(tag.getString("op")) ? Forging.Op.UPSET : Forging.Op.DRAW;
        targetMm = tag.contains("target") ? Math.max(TARGET_MIN_MM, Math.min(TARGET_MAX_MM, tag.getInt("target"))) : 20;
        energyPct = tag.contains("energy") ? Math.max(25, Math.min(100, tag.getInt("energy"))) : 100;
        on = tag.getBoolean("on");
        lastEvent = tag.contains("last") ? tag.getInt("last") : -1;
        series = Math.max(0, tag.getInt("series"));
    }
}
