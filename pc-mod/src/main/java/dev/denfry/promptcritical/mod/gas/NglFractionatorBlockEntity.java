package dev.denfry.promptcritical.mod.gas;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.gasprocessing.Flash;
import dev.denfry.promptcritical.sim.gasprocessing.GasComponent;
import dev.denfry.promptcritical.sim.gasprocessing.GlycolDehydration;
import dev.denfry.promptcritical.sim.gasprocessing.LowTemperatureSeparation;
import dev.denfry.promptcritical.sim.gasprocessing.NglFractionator;
import dev.denfry.promptcritical.sim.gasprocessing.WaterContent;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Газофракционирующая установка ({@link NglFractionator}): ШФЛУ и топливный газ подводят трубами к любой грани;
 * пропан забирают сверху, бутаны с боков, газовый бензин снизу. Верх деэтанизатора — метан и этан — идёт в
 * собственный топливный бак и сжигается в кубах колонн вместе с подведённым товарным газом: тепло куба
 * (R + 1)·D·λ на каждую колонну, λ = 400 кДж/кг (средняя для C2–C4; GPSA, рис. 23-2); избыток газа деэтанизатора
 * сжигается на факеле установки. Состав ШФЛУ в трубах не
 * переносится — принят состав НТС при уставках по умолчанию [допущение]. Производительность — {@link #FEED_LH} л/ч
 * [BALANCE].
 */
public final class NglFractionatorBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final double FEED_LH = 2_000, STAGES = 15, LATENT_J_KG = 400e3, FUEL_J_PER_SM3 = 36e6, EFFICIENCY = 0.85;
    public static final double REFLUX_MIN = 0.5, REFLUX_MAX = 5, REFLUX_STEP = 0.5;
    public static final int MB = 8_000;
    private static double[] feedComposition;

    private final FeedTank feed = new FeedTank(PcFluids.NGL.source.get(), MB);
    private final FeedTank fuel = new FeedTank(PcFluids.SALES_GAS.source.get(), 2_000);
    private final ProductTank propane = new ProductTank(PcFluids.PROPANE.source.get(), MB);
    private final ProductTank butane = new ProductTank(PcFluids.BUTANE.source.get(), MB);
    private final ProductTank gasoline = new ProductTank(PcFluids.NATURAL_GASOLINE.source.get(), MB);
    private final IFluidHandler top = new PortHandler(propane, feed, fuel), side = new PortHandler(butane, feed, fuel),
            bottom = new PortHandler(gasoline, feed, fuel);
    private double reflux = 2, feedLs;
    private NglFractionator.Result result;
    private double resultReflux = Double.NaN;

    public NglFractionatorBlockEntity(BlockPos pos, BlockState state) {
        super(GasBlockEntities.NGL_FRACTIONATOR.get(), pos, state);
    }

    /** Состав ШФЛУ, принятый для труб: НТС при уставках по умолчанию. */
    public static double[] feedComposition() {
        if (feedComposition == null)
            feedComposition = new LowTemperatureSeparation(GlycolDehydration.STANDARD).process(1, 0, 0).nglComposition();
        return feedComposition;
    }

    public FeedTank feed() {
        return feed;
    }

    public ProductTank propane() {
        return propane;
    }

    public ProductTank butane() {
        return butane;
    }

    public ProductTank gasoline() {
        return gasoline;
    }

    public NglFractionator.Result result() {
        if (result == null || reflux != resultReflux) {
            result = new NglFractionator(STAGES, reflux).split(feedComposition());
            resultReflux = reflux;
        }
        return result;
    }

    public void setReflux(double r) {
        reflux = Math.clamp(r, REFLUX_MIN, REFLUX_MAX);
        setChanged();
    }

    private static double litres(double[] moles) {
        double n = NglFractionator.Result.total(moles);
        return n <= 0 ? 0 : n * Flash.liquidM3PerMol(GasComponent.normalize(moles)) * 1000;
    }

    private static double kg(double[] moles) {
        double m = 0;
        for (GasComponent c : GasComponent.values()) m += moles[c.ordinal()] * c.molarMassG / 1000;
        return m;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        double dt = 0.05 * b.world().clock().kSlow();
        NglFractionator.Result r = result();
        double[] x = feedComposition();
        double molPerL = 1 / (Flash.liquidM3PerMol(x) * 1000);
        // на литр сырья: продукты, газ деэтанизатора и тепло кубов
        double pL = litres(r.propane()) * molPerL, bL = litres(r.butane()) * molPerL, gL = litres(r.gasoline()) * molPerL;
        double offSm3 = NglFractionator.Result.total(r.fuelGas()) * molPerL / WaterContent.MOL_PER_SM3;
        double heatJ = (reflux + 1) * (kg(r.fuelGas()) + kg(r.propane()) + kg(r.butane())) * molPerL * LATENT_J_KG;
        double fuelSm3PerL = heatJ / (EFFICIENCY * FUEL_J_PER_SM3);
        double litresIn = Math.min(FEED_LH / 3600 * dt, feed.amount());
        litresIn = Math.min(litresIn, Math.min(propane.room() / Math.max(pL, 1e-9), Math.min(butane.room() / Math.max(bL, 1e-9),
                gasoline.room() / Math.max(gL, 1e-9))));
        double net = fuelSm3PerL - offSm3;
        if (net > 0) litresIn = Math.min(litresIn, fuel.amount() / net);
        litresIn = Math.max(0, litresIn);
        feed.take(litresIn);
        if (net > 0) fuel.take(net * litresIn);
        propane.add(pL * litresIn);
        butane.add(bL * litresIn);
        gasoline.add(gL * litresIn);
        feedLs = litresIn / dt;
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction face) {
        if (face == Direction.UP) return top;
        if (face == Direction.DOWN) return bottom;
        return side;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction face) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(Player player) {
        NglFractionator.Result r = result();
        player.displayClientMessage(GasText.msg("gfu_status", GasText.num(feedLs * 3600, 0), GasText.num(r.propanePurity() * 100, 1),
                GasText.num(r.butanePurity() * 100, 1), GasText.num(r.gasolinePurity() * 100, 1)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == GasPanels.GB_R_DOWN) setReflux(reflux - REFLUX_STEP);
        else if (id == GasPanels.GB_R_UP) setReflux(reflux + REFLUX_STEP);
    }

    @Override
    public PanelLayout panelLayout() {
        return GasPanels.FRACTIONATOR;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        NglFractionator.Result r = result();
        out[GasPanels.G_FEED_LH] = (int) Math.round(feedLs * 3600);
        out[GasPanels.G_C3_X10] = (int) Math.round(r.propanePurity() * 1000);
        out[GasPanels.G_C4_X10] = (int) Math.round(r.butanePurity() * 1000);
        out[GasPanels.G_C5_X10] = (int) Math.round(r.gasolinePurity() * 1000);
        out[GasPanels.G_REFLUX_X10] = (int) Math.round(reflux * 10);
        out[GasPanels.G_TANK0] = (int) Math.round(propane.fill() * 1000);
        out[GasPanels.G_TANK0 + 1] = (int) Math.round(butane.fill() * 1000);
        out[GasPanels.G_TANK0 + 2] = (int) Math.round(gasoline.fill() * 1000);
        out[GasPanels.G_FLAGS] = feedLs > 0 ? 1 << GasPanels.GF_RUNNING : 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        feed.save(tag, "feed");
        fuel.save(tag, "fuel");
        propane.save(tag, "propane");
        butane.save(tag, "butane");
        gasoline.save(tag, "gasoline");
        tag.putDouble("reflux", reflux);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        feed.load(tag, "feed");
        fuel.load(tag, "fuel");
        propane.load(tag, "propane");
        butane.load(tag, "butane");
        gasoline.load(tag, "gasoline");
        if (tag.contains("reflux")) reflux = tag.getDouble("reflux");
    }
}
