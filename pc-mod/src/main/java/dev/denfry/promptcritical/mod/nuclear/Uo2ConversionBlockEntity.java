package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.enrichment.Criticality;
import dev.denfry.promptcritical.sim.fuel.MaterialBalance;
import dev.denfry.promptcritical.sim.fuel.Uo2Conversion;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Участок конверсии ({@link Uo2Conversion}): баллон обогащённого UF₆ в слот, жидкий аммиак, водород и питательная вода —
 * трубой к любой грани. Порошок UO₂ выходит мешками по 1 кг урана с партией (обогащение) на мешке. Мешки разных
 * партий не смешиваются: пока слот занят чужой партией, участок ждёт.
 */
public final class Uo2ConversionBlockEntity extends FuelShopBlockEntity {
    /** Электропечь конверсии, испаритель UF₆ и газодувки, Вт. */
    @Override
    protected double ratedPowerW() {
        return 150e3;
    }

    public static final int MB = 8_000;
    public static final double NH3_KG_L = 0.682, H2_KG_NM3 = 0.0899;

    private final FeedTank ammonia = new FeedTank(PcFluids.AMMONIA.source.get(), MB);
    private final FeedTank hydrogen = new FeedTank(PcFluids.HYDROGEN.source.get(), MB);
    private final FeedTank water = new FeedTank(PcFluids.BOILER_FEEDWATER.source.get(), MB);
    private final IFluidHandler inlet = new PyrolysisFurnaceBlockEntity.MultiInlet(ammonia, hydrogen, water);
    private final Uo2Conversion line = new Uo2Conversion();
    private double powderKg, powderX, powderX234;

    public Uo2ConversionBlockEntity(BlockPos pos, BlockState state) {
        super(NuclearBlockEntities.UO2_CONVERSION.get(), pos, state, 2);
    }

    public Uo2Conversion line() {
        return line;
    }

    @Override
    public MaterialBalance balance() {
        return line.balance();
    }

    @Override
    public void tickDevice() {
        if (level != null && level.isClientSide) {
            clientTick();
            return;
        }
        super.tickDevice();
    }

    /** Нагретая печь (art/blockbench/conversion/uo2_conversion.author.js): над крышкой бункера дрожит тёплый воздух. */
    private void clientTick() {
        BlockState st = getBlockState();
        if (!st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                || !st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) return;
        dev.denfry.promptcritical.mod.fx.MachineFx fx = dev.denfry.promptcritical.mod.fx.MachineFx.of(level, worldPosition,
                st.getBlock() instanceof dev.denfry.promptcritical.mod.block.PcBlock pb ? pb.facing(st) : net.minecraft.core.Direction.NORTH);
        if (fx != null && fx.chance(1)) fx.wisp(fx.at(8, 13, 8), 0.4);
    }

    @Override
    protected boolean valid(int slot, ItemStack stack) {
        return slot == FuelPanels.C_SLOT_UF6 && stack.getItem() instanceof Uf6CylinderItem;
    }

    @Override
    protected String messageKey() {
        return "conversion";
    }

    @Override
    protected void step(ServerLevel level, double dt) {
        ItemStack cyl = items.getStackInSlot(FuelPanels.C_SLOT_UF6);
        Uf6Charge c = Uf6CylinderItem.charge(cyl);
        // порошок другой партии ещё не выгружен — новую не начинаем
        boolean otherLot = powderKg > 1e-9 && !c.empty() && Math.abs(c.u235() - powderX) > 1e-9;
        double avail = otherLot ? 0 : c.uraniumKg();
        Uo2Conversion.Result r = line.step(dt, avail, c.empty() ? powderX : c.u235(), ammonia.amount() * NH3_KG_L,
                hydrogen.amount() * H2_KG_NM3, water.amount());
        if (r.feedKgU() > 0) {
            double mu = Uf6Charge.uraniumMolarMass(c.u235(), c.u234());
            Uf6CylinderItem.take(cyl, r.feedKgU() * (mu + 6 * Uf6Charge.M_F) / mu);
            ammonia.take(r.ammoniaKg() / NH3_KG_L);
            water.take(r.waterKg());
            powderX = c.u235();
            powderX234 = c.u234();
        }
        hydrogen.take(r.hydrogenKg() / H2_KG_NM3);
        powderKg += r.powderKgU();
        while (powderKg >= 1) {
            ItemStack bag = FuelLot.powder(1, powderX, powderX234).on(new ItemStack(PcItems.UO2_POWDER.get()));
            if (!output(FuelPanels.C_SLOT_POWDER, bag)) break;
            powderKg -= 1;
        }
        if (level.getGameTime() % 10 == 0) setLit(r.powderKgU() > 0 || r.feedKgU() > 0);
    }

    // --- руки ---

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return inlet;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return FuelPanels.CONVERSION;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        ledger(v, FuelPanels.L_IN);
        Uo2Conversion.Result r = line.last();
        Uf6Charge c = Uf6CylinderItem.charge(items.getStackInSlot(FuelPanels.C_SLOT_UF6));
        double x = c.empty() ? powderX : c.u235();
        double safe = Criticality.safeU235Kg(x);
        v[FuelPanels.C_X] = (int) Math.round(x * 1e5);
        double perH = 3600 / (0.05 * 72);
        v[FuelPanels.C_FEED_KGH] = (int) Math.round(r.feedKgU() * perH * 10);
        v[FuelPanels.C_POWDER_KGH] = (int) Math.round(r.powderKgU() * perH * 10);
        v[FuelPanels.C_SAFE_G] = Double.isInfinite(safe) ? 0 : (int) Math.round(safe * 1000);
        v[FuelPanels.C_NH3] = (int) Math.round(ammonia.fill() * 1000);
        v[FuelPanels.C_H2] = (int) Math.round(hydrogen.fill() * 1000);
        v[FuelPanels.C_WATER] = (int) Math.round(water.fill() * 1000);
        boolean starved = running && r.feedKgU() > 0 && (ammonia.amount() < 1 || hydrogen.amount() < 1);
        v[FuelPanels.FLAGS] = (running && (r.feedKgU() > 0 || r.powderKgU() > 0) ? 1 << FuelPanels.F_RUN : 0)
                | (r.limitedBySafety() ? 1 << FuelPanels.F_LIMIT : 0) | (r.refused() ? 1 << FuelPanels.F_REFUSED : 0)
                | (starved ? 1 << FuelPanels.F_STARVED : 0);
    }

    // --- сохранение ---

    @Override
    protected void saveState(StateWriter out) {
        out.beginObject("line");
        line.save(out);
        out.endObject();
        out.putDouble("powder", powderKg);
        out.putDouble("powder_x", powderX);
        out.putDouble("powder_x234", powderX234);
    }

    @Override
    protected void loadState(StateReader in) {
        if (in.has("line")) {
            in.beginObject("line");
            line.load(in);
            in.endObject();
        }
        powderKg = in.optDouble("powder", 0);
        powderX = in.optDouble("powder_x", 0);
        powderX234 = in.optDouble("powder_x234", 0);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ammonia.save(tag, "nh3");
        hydrogen.save(tag, "h2");
        water.save(tag, "water");
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ammonia.load(tag, "nh3");
        hydrogen.load(tag, "h2");
        water.load(tag, "water");
    }
}
