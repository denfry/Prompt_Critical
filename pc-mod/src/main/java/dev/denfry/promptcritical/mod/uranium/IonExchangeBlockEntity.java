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
import dev.denfry.promptcritical.sim.uranium.IonExchangeColumn;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Ионообменная установка ({@link IonExchangeColumn}): две колонны с сильноосновным анионитом каруселью. Смолу —
 * мешками по {@value UraniumUnits#RESIN_L} л в слоты (до {@value #RESIN_SLOTS}); продуктивный раствор, рассол, серная
 * кислота и вода для элюента — трубами к боковым граням. Элюат (10 г/л U) — сверху, маточник сорбции — снизу (его
 * возвращают на выщелачивание вместо воды).
 */
public final class IonExchangeBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 16_000, RESIN_SLOTS = 4;

    private final FeedTank pls = new FeedTank(PcFluids.PREGNANT_LEACH_SOLUTION.source.get(), MB);
    private final FeedTank brine = new FeedTank(PcFluids.BRINE.source.get(), MB / 4);
    private final FeedTank acid = new FeedTank(PcFluids.SULFURIC_ACID.source.get(), MB / 8);
    private final FeedTank water = new FeedTank(Fluids.WATER, MB / 2);
    private final IFluidHandler inlet = new MultiInlet(pls, brine, acid, water);
    private final ProductTank eluate = new ProductTank(PcFluids.URANIUM_ELUATE.source.get(), MB);
    private final ProductTank barren = new ProductTank(PcFluids.RAFFINATE.source.get(), MB);
    private final IonExchangeColumn column = new IonExchangeColumn();

    public IonExchangeBlockEntity(BlockPos pos, BlockState state) {
        super(UraniumBlockEntities.ION_EXCHANGE.get(), pos, state, RESIN_SLOTS, 0, s -> s.is(PcItems.ION_EXCHANGE_RESIN_SBA.get()));
    }

    public IonExchangeColumn column() {
        return column;
    }

    public ProductTank eluate() {
        return eluate;
    }

    public ProductTank barren() {
        return barren;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        column.setResinL(count(s -> s.is(PcItems.ION_EXCHANGE_RESIN_SBA.get())) * UraniumUnits.RESIN_L);
        if (!on) return;
        // сорбция: раствор идёт сквозь колонну, сколько примет маточник
        double litres = Math.min(pls.amount(), Math.min(barren.room(), column.resinL() * 10 * UraniumUnits.GAME_H_PER_TICK));
        if (litres > 0) {
            pls.take(litres);
            column.load(litres * UraniumUnits.PLS_KG_U_L);
            barren.add(litres);
        }
        // элюирование: по элюенту, месту под элюат и скорости смолы
        double kg = column.elutable(UraniumUnits.GAME_H_PER_TICK);
        kg = Math.min(kg, brine.amount() / IonExchangeColumn.BRINE_L_PER_KG);
        kg = Math.min(kg, acid.amount() / IonExchangeColumn.ACID_L_PER_KG);
        kg = Math.min(kg, water.amount() / IonExchangeColumn.WATER_L_PER_KG);
        kg = Math.min(kg, eluate.room() * UraniumUnits.ELUATE_KG_U_L);
        double e = column.elute(Math.max(0, kg));
        brine.take(e * IonExchangeColumn.BRINE_L_PER_KG);
        acid.take(e * IonExchangeColumn.ACID_L_PER_KG);
        water.take(e * IonExchangeColumn.WATER_L_PER_KG);
        eluate.add(e / UraniumUnits.ELUATE_KG_U_L);
    }

    @Override
    protected boolean running() {
        return on && column.resinL() > 0 && (pls.amount() > 0 || column.loadedKg() > 0);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == Direction.UP) return eluate;
        if (side == Direction.DOWN) return barren;
        return inlet;
    }

    @Override
    public PanelLayout panelLayout() {
        return UraniumPanels.IX;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[UraniumPanels.A] = (int) Math.round(column.loading(0) * 1000);
        v[UraniumPanels.B] = (int) Math.round(column.loading(1) * 1000);
        v[UraniumPanels.C] = (int) Math.round(column.resinL());
        v[UraniumPanels.D] = column.activeBed();
        v[UraniumPanels.TANK] = (int) Math.round(eluate.fill() * 1000);
        v[UraniumPanels.TANK2] = (int) Math.round(pls.fill() * 1000);
        UraniumPanels.ledger(v, column.ledger());
        v[UraniumPanels.FLAGS] = (on ? 1 << UraniumPanels.F_ON : 0) | (running() ? 1 << UraniumPanels.F_RUN : 0)
                | (!column.sorbing() ? 1 << UraniumPanels.F_LEAK : 0)
                | (column.mode(0) == IonExchangeColumn.Mode.ELUTE || column.mode(1) == IonExchangeColumn.Mode.ELUTE ? 1 << UraniumPanels.F_AUX : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == UraniumPanels.B_RUN) use(player);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        pls.save(tag, "pls");
        brine.save(tag, "brine");
        acid.save(tag, "acid");
        water.save(tag, "water");
        eluate.save(tag, "eluate");
        barren.save(tag, "barren");
        MutableStateTree t = new MutableStateTree();
        column.save(new TreeStateWriter(t));
        tag.put("column", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        pls.load(tag, "pls");
        brine.load(tag, "brine");
        acid.load(tag, "acid");
        water.load(tag, "water");
        eluate.load(tag, "eluate");
        barren.load(tag, "barren");
        if (tag.contains("column")) column.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("column"))));
    }
}
