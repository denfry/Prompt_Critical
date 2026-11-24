package dev.denfry.promptcritical.mod.uranium;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.uranium.DryConversion;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Печь сухой конверсии ({@link DryConversion}) — одна из трёх стадий: прокалочная печь (жёлтый кек → UO₃),
 * восстановительная (UO₃ + H₂ → UO₂), гидрофторирование (UO₂ + HF → UF₄). Порошок — мешками по
 * {@value UraniumUnits#U_PER_ITEM_KG} кг урана воронкой или рукой; реагент (водород, нм³, или безводный HF, л) и
 * топливный газ — трубами к любой грани; продукт — в склад (воронкой снизу). Температура стенки — кнопками на щите.
 */
public final class ConversionBlockEntity extends UraniumProcessBlockEntity {
    /** Привод печи, шнеки и электрообогрев, Вт. */
    @Override
    protected double ratedPowerW() {
        return 75e3;
    }

    public static final int MB = 4_000;

    private final DryConversion stage;
    private final Item feedItem, productItem;
    @Nullable
    private final FeedTank reagent;
    private final FeedTank fuel = new FeedTank(PcFluids.SALES_GAS.source.get(), MB);
    private final IFluidHandler inlet;
    /** Килограммы реагента на единицу объёма трубы: нм³ для газа, л для жидкости. */
    private final double reagentKgPerUnit;
    private double feedKgU, productKgU;

    private ConversionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, DryConversion.Step step, Item feed, Item product,
            @Nullable Fluid reagentFluid, double reagentKgPerUnit) {
        super(type, pos, state, 4, 4, s -> s.is(feed));
        this.stage = new DryConversion(step);
        this.feedItem = feed;
        this.productItem = product;
        this.reagent = reagentFluid == null ? null : new FeedTank(reagentFluid, MB);
        this.reagentKgPerUnit = reagentKgPerUnit;
        this.inlet = reagent == null ? fuel : new MultiInlet(reagent, fuel);
    }

    public static ConversionBlockEntity calciner(BlockPos pos, BlockState state) {
        return new ConversionBlockEntity(UraniumBlockEntities.CALCINER.get(), pos, state, DryConversion.Step.CALCINE, PcItems.YELLOWCAKE.get(),
                PcItems.UO3_POWDER.get(), null, 0);
    }

    public static ConversionBlockEntity reduction(BlockPos pos, BlockState state) {
        return new ConversionBlockEntity(UraniumBlockEntities.REDUCTION.get(), pos, state, DryConversion.Step.REDUCE, PcItems.UO3_POWDER.get(),
                PcItems.UO2_POWDER.get(), PcFluids.HYDROGEN.source.get(), UraniumUnits.H2_KG_NM3);
    }

    public static ConversionBlockEntity hydrofluorination(BlockPos pos, BlockState state) {
        return new ConversionBlockEntity(UraniumBlockEntities.HYDROFLUORINATION.get(), pos, state, DryConversion.Step.HYDROFLUORINATE,
                PcItems.UO2_POWDER.get(), PcItems.UF4_POWDER.get(), PcFluids.HYDROGEN_FLUORIDE.source.get(), UraniumUnits.HF_KG_L);
    }

    public DryConversion stage() {
        return stage;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        if (on && hasRoom(productItem)) {
            while (feedKgU < stage.kind().capacityKgS * dt && !takeOne(s -> s.is(feedItem)).isEmpty()) feedKgU += UraniumUnits.U_PER_ITEM_KG;
            double reagentKg = reagent == null ? 0 : reagent.amount() * reagentKgPerUnit;
            double kg = Math.min(feedKgU, stage.capacityKg(dt, reagentKg, fuel.amount() * UraniumUnits.FUEL_J_NM3));
            if (kg > 0) {
                feedKgU -= kg;
                if (reagent != null) reagent.take(kg * stage.kind().reagentWithExcess() / reagentKgPerUnit);
                fuel.take(kg * stage.kind().heatJPerKgU / UraniumUnits.FUEL_J_NM3);
                productKgU += stage.process(kg, dt);
            } else stage.process(0, dt);
        } else stage.process(0, dt);
        while (productKgU >= UraniumUnits.U_PER_ITEM_KG && emit(productItem)) productKgU -= UraniumUnits.U_PER_ITEM_KG;
    }

    @Override
    protected boolean running() {
        return stage.lastKgS() > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return inlet;
    }

    @Override
    public PanelLayout panelLayout() {
        return switch (stage.kind()) {
            case CALCINE -> UraniumPanels.CALCINER;
            case REDUCE -> UraniumPanels.REDUCTION;
            default -> UraniumPanels.HYDROFLUORINATION;
        };
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[UraniumPanels.T_C] = (int) Math.round(stage.tempK() - 273.15);
        v[UraniumPanels.A] = (int) Math.round(DryConversion.efficiency(stage.kind(), stage.tempK()) * 1000);
        v[UraniumPanels.B] = (int) Math.round(stage.lastKgS() * 3600);
        v[UraniumPanels.TANK] = reagent == null ? 0 : (int) Math.round(reagent.fill() * 1000);
        v[UraniumPanels.TANK2] = (int) Math.round(fuel.fill() * 1000);
        UraniumPanels.ledger(v, stage.ledger());
        v[UraniumPanels.FLAGS] = (on ? 1 << UraniumPanels.F_ON : 0) | (running() ? 1 << UraniumPanels.F_RUN : 0)
                | (DryConversion.efficiency(stage.kind(), stage.tempK()) < 1 ? 1 << UraniumPanels.F_WARN : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == UraniumPanels.B_RUN) use(player);
        else if (id == UraniumPanels.B_T_DOWN) stage.setTempK(stage.tempK() - 10);
        else if (id == UraniumPanels.B_T_UP) stage.setTempK(stage.tempK() + 10);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        if (reagent != null) reagent.save(tag, "reagent");
        fuel.save(tag, "fuel");
        tag.putDouble("feed", feedKgU);
        tag.putDouble("product", productKgU);
        MutableStateTree t = new MutableStateTree();
        stage.save(new TreeStateWriter(t));
        tag.put("stage", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        if (reagent != null) reagent.load(tag, "reagent");
        fuel.load(tag, "fuel");
        feedKgU = tag.getDouble("feed");
        productKgU = tag.getDouble("product");
        if (tag.contains("stage")) stage.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("stage"))));
    }
}
