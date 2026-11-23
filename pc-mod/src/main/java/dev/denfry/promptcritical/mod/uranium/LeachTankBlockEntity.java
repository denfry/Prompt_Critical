package dev.denfry.promptcritical.mod.uranium;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.uranium.UraniumLeach;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Выщелачивание с противоточной декантацией ({@link UraniumLeach}): каскад пачуков и сгустители. Молотая урановая
 * руда и пиролюзит — воронкой или рукой; серная кислота и вода (или маточник сорбции — оборотный раствор) — трубами к
 * боковым граням. Продуктивный раствор (1 г/л U) — сверху, хвостовая пульпа — снизу. Руда подаётся порцией раз в
 * {@value #FEED_EVERY_H} игрового часа, если хватает кислоты, воды на раствор и места под хвосты и раствор.
 */
public final class LeachTankBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 16_000;
    public static final double FEED_EVERY_H = 0.25;

    private final FeedTank acid = new FeedTank(PcFluids.SULFURIC_ACID.source.get(), MB / 4);
    private final FeedTank water = new FeedTank(Fluids.WATER, MB);
    private final FeedTank barren = new FeedTank(PcFluids.RAFFINATE.source.get(), MB);
    private final IFluidHandler inlet = new MultiInlet(acid, water, barren);
    private final ProductTank pls = new ProductTank(PcFluids.PREGNANT_LEACH_SOLUTION.source.get(), MB);
    private final ProductTank tailings = new ProductTank(PcFluids.URANIUM_TAILINGS.source.get(), MB);
    private final UraniumLeach leach = new UraniumLeach();
    private double sinceFeedH, mno2Kg, oxidant = 1, acidOk = 1;

    public LeachTankBlockEntity(BlockPos pos, BlockState state) {
        super(UraniumBlockEntities.LEACH_TANK.get(), pos, state, 6, 0,
                s -> s.is(PcItems.GROUND_URANIUM_ORE.get()) || s.is(PcItems.RAW_PYROLUSITE.get()));
    }

    public UraniumLeach leach() {
        return leach;
    }

    public ProductTank pls() {
        return pls;
    }

    public ProductTank tailings() {
        return tailings;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        double h = UraniumUnits.GAME_H_PER_TICK;
        sinceFeedH += h;
        double ore = UraniumUnits.ORE_KG, acidL = ore * UraniumLeach.acidPerOreKg() / UraniumUnits.ACID_KG_L;
        double solutionL = ore * UraniumUnits.ORE_U / UraniumUnits.PLS_KG_U_L, tailL = ore * UraniumUnits.TAILINGS_L_PER_KG;
        if (on && sinceFeedH >= FEED_EVERY_H && acid.amount() >= acidL && water.amount() + barren.amount() >= solutionL
                && tailings.room() >= tailL && pls.room() >= solutionL) {
            ItemStack s = takeOne(x -> x.is(PcItems.GROUND_URANIUM_ORE.get()));
            if (!s.isEmpty()) {
                sinceFeedH = 0;
                acid.take(acidL);
                double fromBarren = Math.min(barren.amount(), solutionL);
                barren.take(fromBarren);
                water.take(solutionL - fromBarren);
                // окислитель на порцию
                double need = ore * UraniumLeach.mno2PerOreKg();
                while (mno2Kg < need) {
                    ItemStack p = takeOne(x -> x.is(PcItems.RAW_PYROLUSITE.get()));
                    if (p.isEmpty()) break;
                    mno2Kg += UraniumUnits.PYROLUSITE_KG * UraniumUnits.PYROLUSITE_MNO2;
                }
                oxidant = Math.min(1, mno2Kg / need);
                mno2Kg = Math.max(0, mno2Kg - need);
                acidOk = 1;
                leach.feed(ore, UraniumUnits.ORE_U);
                tailings.add(tailL);
            }
        }
        if (sinceFeedH > 4 * FEED_EVERY_H) acidOk = Math.min(acidOk, acid.amount() > 0 ? 1 : 0);
        double u = on ? leach.step(h, acidOk, oxidant) : 0;
        pls.add(Math.min(pls.room(), u / UraniumUnits.PLS_KG_U_L));
        leach.takeLossKg();
    }

    /** Мешалка пачука и насос пульпы, Вт. */
    @Override
    protected double ratedPowerW() {
        return 30e3;
    }

    @Override
    protected boolean running() {
        return on && leach.dissolutionKgH() > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == Direction.UP) return pls;
        if (side == Direction.DOWN) return tailings;
        return inlet;
    }

    @Override
    public PanelLayout panelLayout() {
        return UraniumPanels.LEACH;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[UraniumPanels.T_C] = (int) Math.round(leach.tempK() - 273.15);
        v[UraniumPanels.A] = (int) Math.round(leach.ledger().recovery() * 1000);
        v[UraniumPanels.B] = (int) Math.round(leach.dissolutionKgH() * 1000);
        v[UraniumPanels.C] = (int) Math.round(oxidant * 100);
        v[UraniumPanels.TANK] = (int) Math.round(pls.fill() * 1000);
        v[UraniumPanels.TANK2] = (int) Math.round(tailings.fill() * 1000);
        UraniumPanels.ledger(v, leach.ledger());
        v[UraniumPanels.FLAGS] = (on ? 1 << UraniumPanels.F_ON : 0) | (running() ? 1 << UraniumPanels.F_RUN : 0)
                | (oxidant < 0.99 ? 1 << UraniumPanels.F_WARN : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == UraniumPanels.B_RUN) use(player);
        else if (id == UraniumPanels.B_T_DOWN) leach.setTempK(leach.tempK() - 5);
        else if (id == UraniumPanels.B_T_UP) leach.setTempK(leach.tempK() + 5);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        acid.save(tag, "acid");
        water.save(tag, "water");
        barren.save(tag, "barren");
        pls.save(tag, "pls");
        tailings.save(tag, "tail");
        tag.putDouble("since", sinceFeedH);
        tag.putDouble("mno2", mno2Kg);
        tag.putDouble("ox", oxidant);
        MutableStateTree t = new MutableStateTree();
        leach.save(new TreeStateWriter(t));
        tag.put("leach", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        acid.load(tag, "acid");
        water.load(tag, "water");
        barren.load(tag, "barren");
        pls.load(tag, "pls");
        tailings.load(tag, "tail");
        sinceFeedH = tag.getDouble("since");
        mno2Kg = tag.getDouble("mno2");
        oxidant = tag.contains("ox") ? tag.getDouble("ox") : 1;
        if (tag.contains("leach")) leach.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("leach"))));
    }
}
