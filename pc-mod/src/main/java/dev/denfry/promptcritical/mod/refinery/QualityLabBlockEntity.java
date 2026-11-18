package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import java.util.Arrays;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Лаборатория качества {@code quality_lab} (09 §9.9.13): моторные установки CFR (ОЧИ и ОЧМ, ASTM D2699/D2700),
 * анализатор серы, аппарат ДНП, плотномер. Пробу ({@code sample_bottle}) кладут в слот щита; анализ идёт
 * {@value #ANALYSIS_S} с физ. (40 мин; §9.9.13: 20–60 мин); результат — с воспроизводимостью ASTM: ОЧИ и ОЧМ ±0,7,
 * сера ±5 % (R — предел 95 %, σ = R/2,77) [допущение: прочие показатели — без погрешности]. Шум детерминирован —
 * от времени и места отбора пробы. Проанализированная проба уходит в склад снизу; проба партии станции смешения
 * ещё и пишет паспорт в станцию ({@code certifiedAt}) и выдаёт бумагу {@code batch_certificate}.
 */
public final class QualityLabBlockEntity extends UraniumProcessBlockEntity {
    public static final double ANALYSIS_S = 2400, R_OCTANE = 0.7, R_SULFUR_REL = 0.05, R_TO_SIGMA = 2.77;

    private double progressS;
    @Nullable
    private RefineryComponents.SampleData lastResult;

    public QualityLabBlockEntity(BlockPos pos, BlockState state) {
        super(RefineryBlockEntities.QUALITY_LAB.get(), pos, state, 1, 2, s -> s.is(PcItems.SAMPLE_BOTTLE.get())
                && s.has(RefineryComponents.SAMPLE_DATA.get()) && !s.get(RefineryComponents.SAMPLE_DATA.get()).analysed());
    }

    @Nullable
    public RefineryComponents.SampleData lastResult() {
        return lastResult;
    }

    public double progress() {
        return progressS / ANALYSIS_S;
    }

    /** Измеренное качество: истинное плюс погрешность воспроизводимости, детерминированная по пробе. */
    public static RefineryComponents.FuelQuality measure(RefineryComponents.SampleData d) {
        SplittableRandom rnd = new SplittableRandom(d.takenAt() * 0x9E3779B97F4A7C15L ^ d.source());
        RefineryComponents.FuelQuality t = d.truth();
        double s = R_OCTANE / R_TO_SIGMA;
        return new RefineryComponents.FuelQuality(round1(t.ron() + s * rnd.nextGaussian()), round1(t.mon() + s * rnd.nextGaussian()),
                round1(t.rvpKpa()), round1(t.sulfurPpm() * (1 + R_SULFUR_REL / R_TO_SIGMA * rnd.nextGaussian())), round1(t.benzenePct()),
                round1(t.aromaticsPct()), round1(t.olefinsPct()), Math.round(t.density()));
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        ItemStack in = input.getStackInSlot(0);
        RefineryComponents.SampleData d = in.isEmpty() ? null : in.get(RefineryComponents.SAMPLE_DATA.get());
        if (!on || d == null || d.analysed() || !hasRoom(PcItems.SAMPLE_BOTTLE.get()) || !hasRoom(PcItems.BATCH_CERTIFICATE.get())) {
            if (d == null) progressS = 0;
            return;
        }
        progressS += dt;
        if (progressS < ANALYSIS_S) return;
        progressS = 0;
        long now = level.getGameTime();
        RefineryComponents.SampleData done = d.withResult(measure(d), now);
        input.extractItem(0, 1, false);
        ItemStack bottle = new ItemStack(PcItems.SAMPLE_BOTTLE.get());
        bottle.set(RefineryComponents.SAMPLE_DATA.get(), done);
        emit(bottle);
        if (!done.grade().isEmpty()) {
            ItemStack cert = new ItemStack(PcItems.BATCH_CERTIFICATE.get());
            cert.set(RefineryComponents.SAMPLE_DATA.get(), done);
            emit(cert);
            if (level.getBlockEntity(BlockPos.of(done.source())) instanceof GasolineBlenderBlockEntity b)
                b.certify(done.measured().get(), now);
        }
        lastResult = done;
    }

    @Override
    protected boolean running() {
        return progressS > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return null;
    }

    @Override
    public PanelLayout panelLayout() {
        return ConversionPanels.LAB;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[ConversionPanels.LB_PROGRESS] = (int) Math.round(progress() * 1000);
        RefineryComponents.FuelQuality q = lastResult == null ? null : lastResult.measured().orElse(null);
        if (q != null) {
            v[ConversionPanels.LB_RON_X10] = (int) Math.round(q.ron() * 10);
            v[ConversionPanels.LB_MON_X10] = (int) Math.round(q.mon() * 10);
            v[ConversionPanels.LB_RVP_X10] = (int) Math.round(q.rvpKpa() * 10);
            v[ConversionPanels.LB_S_X10] = (int) Math.round(q.sulfurPpm() * 10);
            v[ConversionPanels.LB_BZ_X10] = (int) Math.round(q.benzenePct() * 10);
            v[ConversionPanels.LB_ARO_X10] = (int) Math.round(q.aromaticsPct() * 10);
            v[ConversionPanels.LB_OLE_X10] = (int) Math.round(q.olefinsPct() * 10);
            v[ConversionPanels.LB_RHO] = (int) Math.round(q.density());
        }
        v[ConversionPanels.LB_FLAGS] = (on ? 1 << ConversionPanels.F_ON : 0) | (running() ? 1 << ConversionPanels.F_RUN : 0)
                | (q != null ? 1 << ConversionPanels.F_AUX : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == ConversionPanels.B_RUN) use(player);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("progress", progressS);
        if (lastResult != null)
            RefineryComponents.SampleData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, lastResult).result()
                    .ifPresent(t -> tag.put("last", t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        progressS = tag.getDouble("progress");
        lastResult = tag.contains("last")
                ? RefineryComponents.SampleData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("last")).result().orElse(null)
                : null;
    }
}
