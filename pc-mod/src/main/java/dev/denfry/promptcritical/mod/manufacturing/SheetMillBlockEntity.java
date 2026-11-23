package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.manufacturing.ElectricalSheet;
import dev.denfry.promptcritical.sim.manufacturing.Forging;
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
import org.jetbrains.annotations.Nullable;

/**
 * Листовой стан холодной прокатки {@code sheet_mill} на валу Create (18 §18.6.8, механизированная ступень): катает
 * стальной лист цеха (подкат до 4 мм) или электротехнический лист проход за проходом до заданной толщины. Предмет
 * листа — лист 1,0 × 0,5 м своей толщины, поэтому при прокатке листов становится больше в h₀/h раз. Стан встаёт, когда
 * следующий проход превысил бы предел наклёпа (нужен отжиг в печи) или кремния слишком много; проход идёт, пока вал не
 * отдаст работу деформации σ̄·V·Δε.
 */
public final class SheetMillBlockEntity extends PoweredSmithyDevice {
    /** Мощность при номинале: 20 кВт при 64 об/мин [BALANCE]. */
    public static final double RATED_W = 20_000, NOMINAL_RPM = 64;
    /** Толщины по ГОСТ 21427: 1,0; 0,65; 0,5; 0,35 мм. */
    public static final double[] TARGETS_MM = {1.0, 0.65, 0.5, 0.35};
    /** Больше листов на выходе стан не держит: один стек. */
    public static final int MAX_SHEETS = 64;
    public static final int STOP_NONE = 0, STOP_DONE = 1, STOP_ANNEAL = 2, STOP_BRITTLE = 3;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.OUTPUT};

    private int target = 2;
    private boolean on;
    private int stop;
    private int passes;
    /** Полоса в клети и её количество в листах; null — пусто. */
    @Nullable
    private ElectricalSheet strip;
    private double sheets;
    /** Работа, накопленная на текущий проход, Дж. */
    private double workJ;

    public SheetMillBlockEntity(BlockPos pos, BlockState state) {
        super(SmithyBlockEntities.SHEET_MILL.get(), pos, state, ROLES, (slot, s) -> slot == 0 && accepted(s));
    }

    /** Подкат цеха или электротехнический лист. */
    public static boolean accepted(ItemStack s) {
        return Electrical.isHotBand(s) || s.has(ManufacturingComponents.ELECTRICAL_SHEET.get());
    }

    @Override
    protected double ratedW() {
        return RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return NOMINAL_RPM;
    }

    public double targetM() {
        return TARGETS_MM[target] / 1000;
    }

    public void setTarget(int t, boolean on) {
        target = Math.max(0, Math.min(TARGETS_MM.length - 1, t));
        this.on = on;
        if (on) stop = STOP_NONE;
        setChanged();
    }

    public int stop() {
        return stop;
    }

    public int passes() {
        return passes;
    }

    @Override
    protected boolean working() {
        return on && stop == STOP_NONE && (strip != null || !inv.getStackInSlot(0).isEmpty());
    }

    /** Взять полосу из входа: подкат — один лист, листы — сколько влезет в выходной стек после прокатки. */
    private void load() {
        ItemStack in = inv.getStackInSlot(0);
        if (in.isEmpty() || !inv.getStackInSlot(1).isEmpty()) return;
        ElectricalSheet s = Electrical.hotBand(in);
        if (s == null) s = Electrical.sheet(in);
        if (s == null) return;
        int fit = (int) Math.max(1, Math.floor(MAX_SHEETS * Math.min(1, targetM() / s.thicknessM())));
        int take = Math.min(in.getCount(), fit);
        in.shrink(take);
        strip = s;
        sheets = take;
        workJ = 0;
    }

    @Override
    protected void second(double shaftW) {
        if (!on || stop != STOP_NONE) return;
        if (strip == null) load();
        if (strip == null || shaftW <= 0) return;
        ElectricalSheet.Pass p = strip.roll(targetM());
        switch (p.outcome()) {
            case OK -> {
                workJ += Forging.POWER_DRIVE_EFFICIENCY * shaftW;
                double need = p.workJ() * sheets * ElectricalSheet.SHEET_LENGTH_M * ElectricalSheet.SHEET_WIDTH_M;
                if (workJ < need) return;
                workJ = 0;
                sheets *= strip.thicknessM() / p.sheet().thicknessM();
                strip = p.sheet();
                passes++;
                if (level instanceof ServerLevel sl)
                    sl.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.25f, 0.5f);
            }
            case DONE -> eject(STOP_DONE);
            case NEEDS_ANNEAL -> eject(STOP_ANNEAL);
            case TOO_BRITTLE -> eject(STOP_BRITTLE);
        }
        setChanged();
    }

    /** Полосу — на выход листами, стан стоит с причиной {@code why}. */
    private void eject(int why) {
        int n = (int) Math.max(1, Math.min(MAX_SHEETS, Math.floor(sheets + 1e-6)));
        inv.setStackInSlot(1, Electrical.sheetStack(strip, n));
        strip = null;
        sheets = 0;
        stop = why;
        on = false;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ElectricalPanels.BUTTON_THINNER -> setTarget(target + 1, on);
            case ElectricalPanels.BUTTON_THICKER -> setTarget(target - 1, on);
            case ElectricalPanels.BUTTON_RUN -> setTarget(target, !on);
            default -> { }
        }
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return accepted(stack);
    }

    @Override
    public PanelLayout panelLayout() {
        return ElectricalPanels.SHEET_MILL;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[ElectricalPanels.S_RPM10] = rpm10();
        out[ElectricalPanels.S_TARGET_UM] = (int) Math.round(TARGETS_MM[target] * 1000);
        out[ElectricalPanels.S_STOP] = stop;
        out[ElectricalPanels.S_PASSES] = passes;
        int f = on ? 1 << ElectricalPanels.SF_ON : 0;
        if (shaftW() > 0) f |= 1 << ElectricalPanels.SF_SHAFT;
        ElectricalSheet s = strip;
        if (s != null) {
            f |= 1 << ElectricalPanels.SF_STRIP;
            out[ElectricalPanels.S_THICK_UM] = (int) Math.round(s.thicknessM() * 1e6);
            out[ElectricalPanels.S_SHEETS10] = (int) Math.round(sheets * 10);
            out[ElectricalPanels.S_STRAIN_PCT] = s.maxColdStrain() > 0
                    ? (int) Math.round(100 * Math.min(1, s.strain() / s.maxColdStrain())) : 100;
            out[ElectricalPanels.S_SI_PCT10] = (int) Math.round(s.siFrac() * 1000);
        }
        out[ElectricalPanels.FLAGS] = f;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) return;
        tag.putInt("target", target);
        tag.putBoolean("on", on);
        tag.putInt("stop", stop);
        tag.putInt("passes", passes);
        tag.putDouble("work", workJ);
        if (strip != null) {
            ElectricalData.Sheet.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, ElectricalData.Sheet.of(strip))
                    .ifSuccess(t -> tag.put("strip", t));
            tag.putDouble("sheets", sheets);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        target = tag.contains("target") ? Math.max(0, Math.min(TARGETS_MM.length - 1, tag.getInt("target"))) : 2;
        on = tag.getBoolean("on");
        stop = Math.max(0, Math.min(STOP_BRITTLE, tag.getInt("stop")));
        passes = Math.max(0, tag.getInt("passes"));
        workJ = Math.max(0, tag.getDouble("work"));
        strip = tag.contains("strip") ? ElectricalData.Sheet.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("strip"))
                .result().map(ElectricalData.Sheet::sheet).orElse(null) : null;
        double n = tag.getDouble("sheets");
        sheets = strip != null && Double.isFinite(n) && n > 0 ? Math.min(MAX_SHEETS * 16, n) : 0;
        if (sheets == 0) strip = null;
    }
}
