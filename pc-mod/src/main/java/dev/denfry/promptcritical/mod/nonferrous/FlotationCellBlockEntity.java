package dev.denfry.promptcritical.mod.nonferrous;

import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.nonferrous.Flotation;
import dev.denfry.promptcritical.sim.nonferrous.SulfideSmelting.Metal;
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
 * Флотомашина ({@link Flotation}, 08 §8.8.3): халькопирит или пентландит — воронкой или рукой (содержание по пробе;
 * без пробы — медиана закона жилы), вода и каменноугольная смола (вспениватель — её крезолы) — трубой к любой грани.
 * Концентрат копится по металлу и выходит мешками по {@code metallurgy.bulk_item_mass_kg} стандартного содержания
 * линии ({@link Metal#concentrateGrade}); пентландитовый — с меткой медно-никелевой линии. Хвосты сбрасываются.
 */
public final class FlotationCellBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 8_000;
    /** Медиана содержания без пробы: Cu 3 % (норильский халькопирит), Ni 1,7 % (08 §8.1.2, каталог жил). */
    public static final double DEFAULT_CU = 0.03, DEFAULT_NI = 0.017;

    private final FeedTank water = new FeedTank(Fluids.WATER, MB);
    private final FeedTank tar = new FeedTank(PcFluids.COAL_TAR.source.get(), MB / 8);
    private final IFluidHandler inlet = new MultiInlet(water, tar);
    /** Руда в машине по линиям, кг, и металл в ней; металл в концентрате, кг. */
    private double oreKg, oreMetalKg, cuConcKg, niConcKg;
    private boolean oreNickel;
    private double lastOreKgS;

    public FlotationCellBlockEntity(BlockPos pos, BlockState state) {
        super(NonferrousBlockEntities.FLOTATION_CELL.get(), pos, state, 4, 4, FlotationCellBlockEntity::ore);
    }

    static boolean ore(ItemStack s) {
        return s.is(PcItems.RAW_CHALCOPYRITE.get()) || s.is(PcItems.RAW_PENTLANDITE.get());
    }

    public FeedTank water() {
        return water;
    }

    public FeedTank tar() {
        return tar;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        lastOreKgS = 0;
        if (!on) return;
        if (oreKg <= 0) {
            ItemStack s = takeOne(FlotationCellBlockEntity::ore);
            if (!s.isEmpty()) {
                oreNickel = s.is(PcItems.RAW_PENTLANDITE.get());
                oreKg = NonferrousUnits.bulkKg();
                oreMetalKg = oreKg * NonferrousUnits.assay(s, oreNickel ? DEFAULT_NI : DEFAULT_CU);
            }
        }
        if (oreKg <= 0) return;
        double kg = Math.min(oreKg, Flotation.ORE_KG_S * dt);
        kg = Math.min(kg, water.amount() / Flotation.WATER_L_PER_KG);
        kg = Math.min(kg, tar.amount() / Flotation.tarLitresPerKg());
        if (kg <= 0) return;
        double metal = oreMetalKg * kg / oreKg;
        oreMetalKg -= metal;
        oreKg -= kg;
        water.take(kg * Flotation.WATER_L_PER_KG);
        tar.take(kg * Flotation.tarLitresPerKg());
        double conc = Flotation.metalToConcentrate(metal / kg) * kg;
        if (oreNickel) niConcKg += conc;
        else cuConcKg += conc;
        lastOreKgS = kg / dt;
        emitConcentrates();
    }

    private void emitConcentrates() {
        double bulk = NonferrousUnits.bulkKg();
        double cuPer = bulk * Metal.COPPER.concentrateGrade, niPer = bulk * Metal.NICKEL.concentrateGrade;
        while (cuConcKg >= cuPer && emit(new ItemStack(PcItems.FLOTATION_CONCENTRATE.get()))) cuConcKg -= cuPer;
        while (niConcKg >= niPer && emit(NonferrousComponents.nickel(new ItemStack(PcItems.FLOTATION_CONCENTRATE.get()), true)))
            niConcKg -= niPer;
    }

    @Override
    protected boolean running() {
        return lastOreKgS > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return inlet;
    }

    @Override
    public PanelLayout panelLayout() {
        return NonferrousPanels.FLOTATION;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[NonferrousPanels.A] = (int) Math.round(Flotation.RECOVERY * 1000);
        v[NonferrousPanels.B] = (int) Math.round(lastOreKgS * 3600);
        v[NonferrousPanels.C] = (int) Math.round(cuConcKg * 10);
        v[NonferrousPanels.D] = (int) Math.round(niConcKg * 10);
        v[NonferrousPanels.TANK] = (int) Math.round(water.fill() * 1000);
        v[NonferrousPanels.TANK2] = (int) Math.round(tar.fill() * 1000);
        v[NonferrousPanels.FLAGS] = (on ? 1 << NonferrousPanels.F_ON : 0) | (running() ? 1 << NonferrousPanels.F_RUN : 0)
                | (on && oreKg > 0 && tar.amount() <= 0 ? 1 << NonferrousPanels.F_WARN : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == NonferrousPanels.B_RUN) use(player);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        water.save(tag, "water");
        tar.save(tag, "tar");
        tag.putDouble("ore", oreKg);
        tag.putDouble("ore_metal", oreMetalKg);
        tag.putBoolean("ore_ni", oreNickel);
        tag.putDouble("cu", cuConcKg);
        tag.putDouble("ni", niConcKg);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        water.load(tag, "water");
        tar.load(tag, "tar");
        oreKg = tag.getDouble("ore");
        oreMetalKg = tag.getDouble("ore_metal");
        oreNickel = tag.getBoolean("ore_ni");
        cuConcKg = tag.getDouble("cu");
        niConcKg = tag.getDouble("ni");
    }
}
