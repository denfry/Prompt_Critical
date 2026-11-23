package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.manufacturing.ElectricalSheet;
import dev.denfry.promptcritical.sim.manufacturing.Forging;
import dev.denfry.promptcritical.sim.manufacturing.Lamination;
import dev.denfry.promptcritical.sim.manufacturing.LaminationShape;
import dev.denfry.promptcritical.sim.manufacturing.Punching;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Пазовырубной пресс {@code notching_press} на валу Create (18 §18.6.8): вырубает из листа пачку пластин статора
 * {@link LaminationShape#STATOR_112}. Пресс с усилием {@link #CAPACITY_N} не тянет пластину целиком (≈ 46 т), поэтому
 * штампует паз за пазом и два хода на окружности — {@link Punching.Mode#NOTCHING}. Заусенец растёт с износом штампа;
 * переточка ({@link #REGRIND_S}) возвращает штамп к свежему.
 */
public final class NotchingPressBlockEntity extends PoweredSmithyDevice {
    /** Мощность при номинале: 7,5 кВт при 64 об/мин [BALANCE]. */
    public static final double RATED_W = 7_500, NOMINAL_RPM = 64;
    /** Ходов в минуту [сверить: пазовырубные прессы 300–600 ход/мин]. */
    public static final double STROKES_PER_MIN = 300;
    /** Усилие пресса, Н (25 т) [BALANCE]. */
    public static final double CAPACITY_N = 250e3;
    /** Зазор штампа на сторону, м: оптимален для листа 0,5 мм [сверить: 5–8 % толщины]. */
    public static final double DIE_CLEARANCE_M = 30e-6;
    /** Переточка штампа, с. */
    public static final int REGRIND_S = 120;
    /** Работа хода — усилие на половине толщины листа: металл скалывается раньше полного хода [сверить: 0,4–0,6]. */
    public static final double SHEAR_DEPTH = 0.5;
    public static final int STOP_NONE = 0, STOP_DONE = 1, STOP_OUTPUT = 2, STOP_WEAK = 3;
    /** Выход — четыре слота: пачки разных классов заусенца не складываются вместе. */
    public static final int OUTPUTS = 4;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.OUTPUT,
        MachineInventory.Role.OUTPUT, MachineInventory.Role.OUTPUT, MachineInventory.Role.OUTPUT};

    private boolean on;
    private int stop;
    private long dieStrokes;
    private double strokes;
    private int regrindS;
    private int bundles;
    private int lastForceN;

    public NotchingPressBlockEntity(BlockPos pos, BlockState state) {
        super(SmithyBlockEntities.NOTCHING_PRESS.get(), pos, state, ROLES,
                (slot, s) -> slot == 0 && Electrical.isFullSheet(s));
    }

    @Override
    protected double ratedW() {
        return RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return NOMINAL_RPM;
    }

    public void setOn(boolean on) {
        this.on = on;
        if (on) stop = STOP_NONE;
        setChanged();
    }

    public long dieStrokes() {
        return dieStrokes;
    }

    public int bundles() {
        return bundles;
    }

    public int stop() {
        return stop;
    }

    /** Зазор штампа в долях толщины листа. */
    public static double clearance(ElectricalSheet s) {
        return DIE_CLEARANCE_M / s.thicknessM();
    }

    @Override
    protected boolean working() {
        return on && stop == STOP_NONE && regrindS == 0 && !inv.getStackInSlot(0).isEmpty();
    }

    @Override
    protected void second(double shaftW) {
        if (regrindS > 0) {
            if (--regrindS == 0) dieStrokes = 0;
            setChanged();
            return;
        }
        // короткий лист стана между отжигами не по штампу: пресс его не берёт
        ElectricalSheet sheet = Electrical.isFullSheet(inv.getStackInSlot(0)) ? Electrical.sheet(inv.getStackInSlot(0)) : null;
        if (!on || stop != STOP_NONE || shaftW <= 0) return;
        if (sheet == null) {
            halt(STOP_DONE);
            return;
        }
        Punching.Result r = Punching.punch(sheet, LaminationShape.STATOR_112, clearance(sheet), dieStrokes, CAPACITY_N);
        lastForceN = (int) Math.round(r.peakForceN());
        double strokeJ = Forging.POWER_DRIVE_EFFICIENCY * shaftW * 60 / STROKES_PER_MIN;
        if (r.mode() == Punching.Mode.TOO_WEAK || strokeJ < r.peakForceN() * sheet.thicknessM() * SHEAR_DEPTH) {
            halt(STOP_WEAK);
            return;
        }
        Lamination lam = r.lamination();
        ItemStack made = Electrical.bundleStack(lam, 1);
        int slot = outputSlot(made);
        if (slot < 0) {
            halt(STOP_OUTPUT);
            return;
        }
        strokes += STROKES_PER_MIN / 60;
        int perSheet = r.strokes() * ElectricalData.LAMINATIONS_PER_BUNDLE;
        if (strokes < perSheet) return;
        strokes = 0;
        dieStrokes += perSheet;
        inv.getStackInSlot(0).shrink(1);
        ItemStack out = inv.getStackInSlot(slot);
        if (out.isEmpty()) inv.setStackInSlot(slot, made);
        else out.grow(1);
        bundles++;
        if (level instanceof ServerLevel sl)
            sl.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.4f, 1.4f);
        setChanged();
    }

    /** Слот выхода под пачку: тот же класс с местом, иначе пустой; -1 — некуда. */
    private int outputSlot(ItemStack made) {
        for (int i = 1; i <= OUTPUTS; i++) {
            ItemStack o = inv.getStackInSlot(i);
            if (!o.isEmpty() && ItemStack.isSameItemSameComponents(o, made) && o.getCount() < o.getMaxStackSize()) return i;
        }
        for (int i = 1; i <= OUTPUTS; i++) if (inv.getStackInSlot(i).isEmpty()) return i;
        return -1;
    }

    private void halt(int why) {
        stop = why;
        on = false;
        strokes = 0;
        setChanged();
    }

    /** Поставить штамп на переточку: пресс стоит {@link #REGRIND_S} секунд. */
    public void regrind() {
        if (regrindS == 0 && dieStrokes > 0) {
            regrindS = REGRIND_S;
            on = false;
            strokes = 0;
            setChanged();
        }
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ElectricalPanels.BUTTON_RUN -> setOn(!on);
            case ElectricalPanels.BUTTON_REGRIND -> regrind();
            default -> { }
        }
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return Electrical.isFullSheet(stack);
    }

    @Override
    public PanelLayout panelLayout() {
        return ElectricalPanels.NOTCHING_PRESS;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[ElectricalPanels.N_RPM10] = rpm10();
        out[ElectricalPanels.N_STOP] = stop;
        out[ElectricalPanels.N_BUNDLES] = bundles;
        out[ElectricalPanels.N_FORCE_KN] = lastForceN / 1000;
        out[ElectricalPanels.N_WEAR_PCT] = (int) Math.min(999, 100 * dieStrokes / Punching.DIE_LIFE_STROKES);
        out[ElectricalPanels.N_REGRIND_S] = regrindS;
        int f = on ? 1 << ElectricalPanels.NF_ON : 0;
        if (shaftW() > 0) f |= 1 << ElectricalPanels.NF_SHAFT;
        ElectricalSheet s = Electrical.sheet(inv.getStackInSlot(0));
        if (s != null) {
            f |= 1 << ElectricalPanels.NF_SHEET;
            out[ElectricalPanels.N_BURR_UM] = (int) Math.round(Punching.burrM(s.thicknessM(), clearance(s), dieStrokes) * 1e6);
            out[ElectricalPanels.N_THICK_UM] = (int) Math.round(s.thicknessM() * 1e6);
            Punching.Result r = Punching.punch(s, LaminationShape.STATOR_112, clearance(s), dieStrokes, CAPACITY_N);
            int per = Math.max(1, r.strokes() * ElectricalData.LAMINATIONS_PER_BUNDLE);
            out[ElectricalPanels.N_PROGRESS_PCT] = (int) Math.round(100 * Math.min(1, strokes / per));
        }
        out[ElectricalPanels.FLAGS] = f;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) return;
        tag.putBoolean("on", on);
        tag.putInt("stop", stop);
        tag.putLong("die", dieStrokes);
        tag.putDouble("strokes", strokes);
        tag.putInt("regrind", regrindS);
        tag.putInt("bundles", bundles);
        tag.putInt("force", lastForceN);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        on = tag.getBoolean("on");
        stop = Math.max(0, Math.min(STOP_WEAK, tag.getInt("stop")));
        dieStrokes = Math.max(0, tag.getLong("die"));
        double st = tag.getDouble("strokes");
        strokes = Double.isFinite(st) && st > 0 ? st : 0;
        regrindS = Math.max(0, Math.min(REGRIND_S, tag.getInt("regrind")));
        bundles = Math.max(0, tag.getInt("bundles"));
        lastForceN = Math.max(0, tag.getInt("force"));
    }
}
