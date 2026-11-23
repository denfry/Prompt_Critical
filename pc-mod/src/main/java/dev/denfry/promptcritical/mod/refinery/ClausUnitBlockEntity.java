package dev.denfry.promptcritical.mod.refinery;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.refining.ClausUnit;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Установка Клауса ({@link ClausUnit}): печь с котлом-утилизатором, три каталитических конвертора с подогревом и
 * конденсаторы серы, дожиг хвостового газа. Кислый газ — сероводород аминовой регенерации из гидроочисток, которые
 * касаются установки, и газ коксования. Сера — гранулами {@link #SULFUR_KG_PER_ITEM} кг в склад (воронкой снизу), она идёт в
 * сернокислотную установку вместо колчедана. Температуру слоя первого конвертора задают кнопками щита (второй и
 * третий — на 60 и 75 К холоднее); воздух — коррекцией соотношения: ошибка подачи снижает извлечение на 3ε².
 */
public final class ClausUnitBlockEntity extends UraniumProcessBlockEntity {
    public static final double SULFUR_KG_PER_ITEM = 10, MAX_H2S_KG_S = 0.05, T_MIN_C = 180, T_MAX_C = 340;
    public static final double[] STAGE_OFFSET_K = {0, -60, -75};

    private final ClausUnit claus = new ClausUnit();
    private double bed1C = 300, airError, sulfurKg, h2sKgS;
    private ClausUnit.Result last = new ClausUnit().run(0, new double[] {300, 240, 225}, 0);
    private double totalSulfurKg, totalSo2Kg;
    /** Кислый газ, остающийся в реакторе и конденсаторах при остановке, кг [оценка]. */
    public static final double H2S_HOLDUP_KG = 5;
    private boolean wasRunning;

    public ClausUnitBlockEntity(BlockPos pos, BlockState state) {
        super(RefineryBlockEntities.CLAUS_UNIT.get(), pos, state, 0, 4, s -> false);
    }

    public ClausUnit unit() {
        return claus;
    }

    public ClausUnit.Result last() {
        return last;
    }

    public double totalSulfurKg() {
        return totalSulfurKg;
    }

    public double totalSo2Kg() {
        return totalSo2Kg;
    }

    public void setAirError(double e) {
        airError = Math.clamp(e, -0.5, 0.5);
    }

    public void setBed1C(double c) {
        bed1C = Math.clamp(c, T_MIN_C, T_MAX_C);
    }

    private double[] beds() {
        double[] b = new double[STAGE_OFFSET_K.length];
        for (int i = 0; i < b.length; i++) b[i] = bed1C + STAGE_OFFSET_K[i];
        return b;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        h2sKgS = 0;
        double[] beds = beds();
        if (on && hasRoom(PcItems.SULFUR.get())) {
            double kg = 0;
            for (Direction d : Direction.values())
                if (level.getBlockEntity(worldPosition.relative(d)) instanceof HydrotreaterBlockEntity h) kg += h.drainH2s(MAX_H2S_KG_S * dt - kg);
                else if (level.getBlockEntity(worldPosition.relative(d)) instanceof DelayedCokerBlockEntity c) kg += c.drainH2s(MAX_H2S_KG_S * dt - kg);
                else if (level.getBlockEntity(worldPosition.relative(d)) instanceof AmineUnitBlockEntity a) kg += a.drainH2s(MAX_H2S_KG_S * dt - kg);
            h2sKgS = kg / dt;
        }
        last = claus.run(h2sKgS, beds, airError);
        // хвостовой газ после печи дожига — труба 60 м, ≈ 570 К (09 §9.9, 14 §14.1.1)
        if (level.getGameTime() % 20 == 0 && last.so2KgS() > 0)
            dev.denfry.promptcritical.mod.environment.AirQualityService.emit(level, worldPosition.above(2), 60, 570, 3 + 20 * h2sKgS, last.so2KgS(),
                    0, 0, 0, 0, "claus@" + worldPosition.toShortString(), dev.denfry.promptcritical.sim.environment.EmissionLedger.Category.REFINERY);
        // остановка установки с кислым газом в аппаратах: задержка H₂S стравливается через неплотности облаком (09 §9.11.8)
        if (wasRunning && !on)
            dev.denfry.promptcritical.mod.environment.ToxicReleaseService.puff(level, worldPosition.above(),
                    dev.denfry.promptcritical.sim.consequence.ToxicGas.HYDROGEN_SULFIDE, H2S_HOLDUP_KG);
        wasRunning = h2sKgS > 0;
        for (int j = 0; j < beds.length; j++) claus.ageStage(j, beds[j], h2sKgS > 0 ? dt : 0);
        sulfurKg += last.sulfurKgS() * dt;
        totalSulfurKg += last.sulfurKgS() * dt;
        totalSo2Kg += last.so2KgS() * dt;
        while (sulfurKg >= SULFUR_KG_PER_ITEM && emit(PcItems.SULFUR.get())) sulfurKg -= SULFUR_KG_PER_ITEM;
    }

    /** Воздуходувка печи Клауса, Вт. */
    @Override
    protected double ratedPowerW() {
        return 75e3;
    }

    @Override
    protected boolean running() {
        return h2sKgS > 0;
    }

    /**
     * Установка работает: дымок дожига над трубой, пламя в гляделке печи, капель жидкой серы из гидрозатвора.
     * Точки — art/blockbench/refinery/claus_unit.author.js.
     */
    @Override
    protected void clientTick() {
        BlockState st = getBlockState();
        if (!st.hasProperty(PcProperties.RUNNING) || !st.getValue(PcProperties.RUNNING)) return;
        MachineFx fx = MachineFx.of(level, worldPosition, st.getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (fx == null) return;
        if (fx.chance(3)) fx.wisp(fx.at(13.5, 32.2, 13.5), 0.1);
        if (fx.chance(1)) fx.flame(fx.at(-0.6, 9.6, 8.6), 0.05);
        if (fx.chance(0.8)) fx.drip(fx.at(6, 1.8, 15));
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return null;
    }

    @Override
    public PanelLayout panelLayout() {
        return RefineryPanels.CLAUS;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[RefineryPanels.T_C] = (int) Math.round(bed1C);
        v[RefineryPanels.A] = (int) Math.round(last.recovery() * 1000);
        v[RefineryPanels.B] = (int) Math.round(last.sulfurKgS() * 3600);
        v[RefineryPanels.C] = (int) Math.round(last.so2KgS() * 3600 * 10);
        v[RefineryPanels.D] = (int) Math.round(airError * 100);
        v[RefineryPanels.FLAGS] = (on ? 1 << RefineryPanels.F_ON : 0) | (running() ? 1 << RefineryPanels.F_RUN : 0)
                | (last.wetStage() >= 0 ? 1 << RefineryPanels.F_WARN : 0) | (Math.abs(airError) >= 0.05 ? 1 << RefineryPanels.F_AUX : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case RefineryPanels.B_RUN -> use(player);
            case RefineryPanels.B_T_DOWN -> setBed1C(bed1C - 5);
            case RefineryPanels.B_T_UP -> setBed1C(bed1C + 5);
            case RefineryPanels.B_AIR_DOWN -> setAirError(airError - 0.02);
            case RefineryPanels.B_AIR_UP -> setAirError(airError + 0.02);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("bed1", bed1C);
        tag.putDouble("air", airError);
        tag.putDouble("s", sulfurKg);
        tag.putDouble("total_s", totalSulfurKg);
        tag.putDouble("total_so2", totalSo2Kg);
        for (int j = 0; j < ClausUnit.MAX_STAGES; j++) tag.putDouble("act" + j, claus.activity(j));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("bed1")) bed1C = tag.getDouble("bed1");
        airError = tag.getDouble("air");
        sulfurKg = tag.getDouble("s");
        totalSulfurKg = tag.getDouble("total_s");
        totalSo2Kg = tag.getDouble("total_so2");
        for (int j = 0; j < ClausUnit.MAX_STAGES; j++) if (tag.contains("act" + j)) claus.setActivity(j, tag.getDouble("act" + j));
    }
}
