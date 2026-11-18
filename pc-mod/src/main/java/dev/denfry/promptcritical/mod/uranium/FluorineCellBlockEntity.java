package dev.denfry.promptcritical.mod.uranium;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.grid.DcLoad;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.uranium.FluorineCellRoom;
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
 * Серия фторных электролизёров ({@link FluorineCellRoom}) на выпрямительном агрегате электролиза (как хлорные ванны):
 * {@value FluorineCellRoom#CELLS} ванн с расплавом KF·2HF. Безводный HF — трубой к боковым граням, автоматика держит
 * его в расплаве на норме; фтор (нм³) — сверху, водород (нм³) — снизу.
 */
public final class FluorineCellBlockEntity extends UraniumProcessBlockEntity implements DcLoad {
    public static final int MB = 4_000;

    private final FeedTank hf = new FeedTank(PcFluids.HYDROGEN_FLUORIDE.source.get(), MB);
    private final ProductTank fluorine = new ProductTank(PcFluids.FLUORINE.source.get(), MB);
    private final ProductTank hydrogen = new ProductTank(PcFluids.HYDROGEN.source.get(), MB);
    private final FluorineCellRoom cells = new FluorineCellRoom();
    private double suppliedAmps;
    private long suppliedTick = -10;

    public FluorineCellBlockEntity(BlockPos pos, BlockState state) {
        super(UraniumBlockEntities.FLUORINE_CELL.get(), pos, state, 0, 0, s -> false);
        on = false;
    }

    public FluorineCellRoom cells() {
        return cells;
    }

    public ProductTank fluorine() {
        return fluorine;
    }

    @Override
    public double dcSetpointA() {
        return cells.currentKa() * 1000;
    }

    @Override
    public double dcDemandW() {
        return on ? cells.dcDemandW(dcSetpointA()) : 0;
    }

    @Override
    public void supply(double amps) {
        suppliedAmps = Math.max(0, amps);
        suppliedTick = level.getGameTime();
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        cells.setOn(on);
        // подпитка HF до нормы расплава
        double want = Math.min(cells.hfRoomKg(), Math.max(0, (FluorineCellRoom.HF_NOMINAL - cells.hfFraction()) * 1e4 + cells.hfUseKgS() * dt));
        double kg = Math.min(want, hf.amount() * UraniumUnits.HF_KG_L);
        cells.addHf(kg);
        hf.take(kg / UraniumUnits.HF_KG_L);
        double amps = level.getGameTime() - suppliedTick <= 2 ? suppliedAmps : 0;
        // некуда девать фтор — серия на себя
        if (fluorine.room() <= 0) amps = 0;
        cells.step(dt, amps);
        fluorine.add(Math.min(fluorine.room(), cells.f2KgS() * dt / UraniumUnits.F2_KG_NM3));
        hydrogen.add(Math.min(hydrogen.room(), cells.h2KgS() * dt / UraniumUnits.H2_KG_NM3));
    }

    @Override
    protected boolean running() {
        return cells.f2KgS() > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == Direction.UP) return fluorine;
        if (side == Direction.DOWN) return hydrogen;
        return hf;
    }

    @Override
    public PanelLayout panelLayout() {
        return UraniumPanels.F2_CELLS;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[UraniumPanels.A] = (int) Math.round(cells.voltageV() * 10);
        v[UraniumPanels.B] = (int) Math.round(cells.hfFraction() * 1000);
        v[UraniumPanels.C] = (int) Math.round(cells.f2KgS() * 3600 * 10);
        v[UraniumPanels.D] = (int) Math.round(cells.currentKa() * 10);
        v[UraniumPanels.TANK] = (int) Math.round(fluorine.fill() * 1000);
        v[UraniumPanels.TANK2] = (int) Math.round(hf.fill() * 1000);
        v[UraniumPanels.FLAGS] = (on ? 1 << UraniumPanels.F_ON : 0) | (running() ? 1 << UraniumPanels.F_RUN : 0)
                | (cells.anodeEffect() ? 1 << UraniumPanels.F_LEAK : 0)
                | (cells.hfFraction() < FluorineCellRoom.HF_ANODE_EFFECT + 0.015 ? 1 << UraniumPanels.F_WARN : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == UraniumPanels.B_RUN) use(player);
        else if (id == UraniumPanels.B_T_DOWN) cells.setCurrentKa(cells.currentKa() - 0.5);
        else if (id == UraniumPanels.B_T_UP) cells.setCurrentKa(cells.currentKa() + 0.5);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        hf.save(tag, "hf");
        fluorine.save(tag, "f2");
        hydrogen.save(tag, "h2");
        MutableStateTree t = new MutableStateTree();
        cells.save(new TreeStateWriter(t));
        tag.put("cells", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        hf.load(tag, "hf");
        fluorine.load(tag, "f2");
        hydrogen.load(tag, "h2");
        if (tag.contains("cells")) cells.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("cells"))));
    }
}
