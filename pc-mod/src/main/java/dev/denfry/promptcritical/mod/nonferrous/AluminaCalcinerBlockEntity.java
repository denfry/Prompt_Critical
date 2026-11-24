package dev.denfry.promptcritical.mod.nonferrous;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.MvProcessBlockEntity;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.nonferrous.AluminaCalciner;
import dev.denfry.promptcritical.sim.nonferrous.BayerProcess;
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
 * Печь прокалки гидроксида ({@link AluminaCalciner}, 08 §8.8.1): гидроксид — мешками воронкой или рукой, топливный
 * газ — трубой к любой грани, глинозём — мешками в склад (воронкой снизу). Температура — кнопками щита.
 */
public final class AluminaCalcinerBlockEntity extends MvProcessBlockEntity {
    /** Дымосос с электрофильтром, питатели и транспорт гидроксида, Вт. */
    @Override
    protected double mvRatedW() {
        return 600e3;
    }


    public static final int MB = 4_000;

    private final FeedTank fuel = new FeedTank(PcFluids.SALES_GAS.source.get(), MB);
    private final AluminaCalciner kiln = new AluminaCalciner();
    private double hydroxideKg, aluminaKg;

    public AluminaCalcinerBlockEntity(BlockPos pos, BlockState state) {
        super(NonferrousBlockEntities.ALUMINA_CALCINER.get(), pos, state, 4, 4, s -> s.is(PcItems.ALUMINIUM_HYDROXIDE.get()));
    }

    public AluminaCalciner kiln() {
        return kiln;
    }

    public FeedTank fuel() {
        return fuel;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        if (on && hasRoom(PcItems.ALUMINA.get())) {
            double want = AluminaCalciner.CAPACITY_KG_S * dt * BayerProcess.HYDROXIDE_PER_AL2O3;
            while (hydroxideKg < want && !takeOne(s -> s.is(PcItems.ALUMINIUM_HYDROXIDE.get())).isEmpty())
                hydroxideKg += NonferrousUnits.bulkKg();
            double kg = kiln.step(dt, hydroxideKg, fuel.amount() * NonferrousUnits.FUEL_J_NM3);
            hydroxideKg = Math.max(0, hydroxideKg - kg * BayerProcess.HYDROXIDE_PER_AL2O3);
            fuel.take(kiln.lastHeatW() * dt / NonferrousUnits.FUEL_J_NM3);
            aluminaKg += kg;
        } else kiln.step(dt, 0, 0);
        double kg = NonferrousUnits.bulkKg();
        while (aluminaKg >= kg && emit(PcItems.ALUMINA.get())) aluminaKg -= kg;
    }

    @Override
    protected boolean running() {
        return kiln.lastAl2o3KgS() > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return fuel;
    }

    @Override
    public PanelLayout panelLayout() {
        return NonferrousPanels.CALCINER;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        double t = kiln.tempK();
        v[NonferrousPanels.T_C] = (int) Math.round(t - 273.15);
        v[NonferrousPanels.A] = (int) Math.round(AluminaCalciner.conversion(t) * 1000);
        v[NonferrousPanels.B] = (int) Math.round(kiln.lastAl2o3KgS() * 3600);
        v[NonferrousPanels.C] = (int) Math.round(AluminaCalciner.heatJPerKg(t) / 1e5);
        v[NonferrousPanels.TANK] = (int) Math.round(fuel.fill() * 1000);
        v[NonferrousPanels.FLAGS] = (on ? 1 << NonferrousPanels.F_ON : 0) | (running() ? 1 << NonferrousPanels.F_RUN : 0)
                | (t < AluminaCalciner.LOW_K || t > AluminaCalciner.HIGH_K ? 1 << NonferrousPanels.F_WARN : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == NonferrousPanels.B_RUN) use(player);
        else if (id == NonferrousPanels.B_DOWN) kiln.setTempK(kiln.tempK() - 25);
        else if (id == NonferrousPanels.B_UP) kiln.setTempK(kiln.tempK() + 25);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        fuel.save(tag, "fuel");
        tag.putDouble("hydrate", hydroxideKg);
        tag.putDouble("alumina", aluminaKg);
        MutableStateTree t = new MutableStateTree();
        kiln.save(new TreeStateWriter(t));
        tag.put("kiln", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        fuel.load(tag, "fuel");
        hydroxideKg = tag.getDouble("hydrate");
        aluminaKg = tag.getDouble("alumina");
        if (tag.contains("kiln")) kiln.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("kiln"))));
    }
}
