package dev.denfry.promptcritical.mod.uranium;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.environment.ToxicReleaseService;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.consequence.ToxicGas;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.uranium.HfKiln;
import java.util.Arrays;
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
 * Печь фтористого водорода ({@link HfKiln}): плавиковый шпат (руда флюорита, CaF₂ — по пробе) воронкой или рукой,
 * серная кислота и топливный газ — трубами к боковым граням; безводный HF — снизу (1 мБ = 1 л). HF, которому некуда
 * уходить, сбрасывается из конденсатора в атмосферу — облако фтористого водорода.
 */
public final class HfKilnBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 8_000;

    private final FeedTank acid = new FeedTank(PcFluids.SULFURIC_ACID.source.get(), MB);
    private final FeedTank fuel = new FeedTank(PcFluids.SALES_GAS.source.get(), MB / 2);
    private final IFluidHandler inlet = new MultiInlet(acid, fuel);
    private final ProductTank hf = new ProductTank(PcFluids.HYDROGEN_FLUORIDE.source.get(), MB);
    private final HfKiln kiln = new HfKiln();
    private double caf2Kg, ventKgS;

    public HfKilnBlockEntity(BlockPos pos, BlockState state) {
        super(UraniumBlockEntities.HF_KILN.get(), pos, state, 4, 0, s -> s.is(PcBlocks.FLUORITE_ORE.get().asItem()));
    }

    public HfKiln kiln() {
        return kiln;
    }

    public ProductTank hf() {
        return hf;
    }

    public double ventKgS() {
        return ventKgS;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        double want = HfKiln.CAPACITY_CAF2_KG_S * dt;
        while (on && caf2Kg < want) {
            ItemStack ore = takeOne(s -> s.is(PcBlocks.FLUORITE_ORE.get().asItem()));
            if (ore.isEmpty()) break;
            caf2Kg += UraniumUnits.MINERAL_KG * UraniumUnits.assay(ore, UraniumUnits.FLUORITE_DEFAULT);
        }
        double kg = Math.min(want, caf2Kg);
        kg = Math.min(kg, acid.amount() * UraniumUnits.ACID_KG_L / HfKiln.acidPerCaf2());
        kg = Math.min(kg, fuel.amount() * UraniumUnits.FUEL_J_NM3 / HfKiln.HEAT_J_PER_KG);
        kiln.setOn(on);
        kiln.step(kg / dt);
        caf2Kg -= kiln.caf2KgS() * dt;
        acid.take(kiln.acidKgS() * dt / UraniumUnits.ACID_KG_L);
        fuel.take(kiln.heatW() * dt / UraniumUnits.FUEL_J_NM3);
        double litres = kiln.hfKgS() * dt / UraniumUnits.HF_KG_L, stored = Math.min(litres, hf.room());
        hf.add(stored);
        ventKgS = (litres - stored) * UraniumUnits.HF_KG_L / dt;
        if (ventKgS > 1e-6) ToxicReleaseService.vent(level, worldPosition.above(), ToxicGas.HYDROGEN_FLUORIDE, ventKgS);
    }

    @Override
    protected boolean running() {
        return kiln.hfKgS() > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return side == Direction.DOWN ? hf : inlet;
    }

    @Override
    public PanelLayout panelLayout() {
        return UraniumPanels.HF;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[UraniumPanels.T_C] = (int) Math.round(kiln.tempK() - 273.15);
        v[UraniumPanels.A] = (int) Math.round(HfKiln.hfYield(kiln.tempK()) * 1000);
        v[UraniumPanels.B] = (int) Math.round(kiln.hfKgS() * 3600);
        v[UraniumPanels.TANK] = (int) Math.round(hf.fill() * 1000);
        v[UraniumPanels.FLAGS] = (on ? 1 << UraniumPanels.F_ON : 0) | (running() ? 1 << UraniumPanels.F_RUN : 0)
                | (ventKgS > 0 ? 1 << UraniumPanels.F_LEAK : 0) | (HfKiln.hfYield(kiln.tempK()) < 0.9 ? 1 << UraniumPanels.F_WARN : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == UraniumPanels.B_RUN) use(player);
        else if (id == UraniumPanels.B_T_DOWN) kiln.setTempK(kiln.tempK() - 10);
        else if (id == UraniumPanels.B_T_UP) kiln.setTempK(kiln.tempK() + 10);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        acid.save(tag, "acid");
        fuel.save(tag, "fuel");
        hf.save(tag, "hf");
        tag.putDouble("caf2", caf2Kg);
        MutableStateTree t = new MutableStateTree();
        kiln.save(new TreeStateWriter(t));
        tag.put("kiln", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        acid.load(tag, "acid");
        fuel.load(tag, "fuel");
        hf.load(tag, "hf");
        caf2Kg = tag.getDouble("caf2");
        if (tag.contains("kiln")) kiln.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("kiln"))));
    }
}
