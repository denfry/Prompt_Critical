package dev.denfry.promptcritical.mod.nonferrous;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.nonferrous.AluminaPrecipitator;
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
 * Декомпозёр ({@link AluminaPrecipitator}, 08 §8.8.1): алюминатный раствор — трубой к боковым граням в бак на
 * {@value #HOLDUP_MB} л (это и есть объём, определяющий время пребывания); гидроксид — мешками по
 * {@code metallurgy.bulk_item_mass_kg} в склад (воронкой снизу); отработанный раствор — сверху щёлочью обратно на
 * выщелачивание (по массе NaOH: {@value BayerProcess#NAOH_G_L} г/л раствора — в 11 %-ю щёлочь).
 */
public final class AluminaPrecipitatorBlockEntity extends UraniumProcessBlockEntity implements dev.denfry.promptcritical.mod.client.whole.WholeModelHost {
    public static final int HOLDUP_MB = 20_000, SPENT_MB = 8_000;

    private final FeedTank liquor = new FeedTank(PcFluids.SODIUM_ALUMINATE_LIQUOR.source.get(), HOLDUP_MB);
    private final ProductTank spent = new ProductTank(PcFluids.SODIUM_HYDROXIDE_SOLUTION.source.get(), SPENT_MB);
    private final AluminaPrecipitator precipitator = new AluminaPrecipitator();
    private double hydroxideKg;

    public AluminaPrecipitatorBlockEntity(BlockPos pos, BlockState state) {
        super(NonferrousBlockEntities.ALUMINA_PRECIPITATOR.get(), pos, state, 0, 4, s -> false);
    }

    public AluminaPrecipitator precipitator() {
        return precipitator;
    }

    public FeedTank liquor() {
        return liquor;
    }

    public ProductTank spent() {
        return spent;
    }

    /** Литры 11 %-й щёлочи на литр отработанного раствора. */
    public static double causticPerLiquorL() {
        return BayerProcess.NAOH_KG_L / NonferrousUnits.CAUSTIC_NAOH_KG_L;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        double holdup = on && hasRoom(PcItems.ALUMINIUM_HYDROXIDE.get()) ? liquor.amount() : 0;
        holdup = Math.min(holdup, spent.room() / causticPerLiquorL() * AluminaPrecipitator.RESIDENCE_H * 3600 / Math.max(dt, 1e-9));
        double litres = precipitator.step(dt, holdup);
        liquor.take(litres);
        spent.add(litres * causticPerLiquorL());
        hydroxideKg += precipitator.lastHydroxideKgS() * dt;
        double kg = NonferrousUnits.bulkKg();
        while (hydroxideKg >= kg && emit(PcItems.ALUMINIUM_HYDROXIDE.get())) hydroxideKg -= kg;
    }

    /** Мешалка декомпозёра, Вт. */
    @Override
    protected double ratedPowerW() {
        return 30e3;
    }

    @Override
    protected boolean running() {
        return precipitator.lastLiquorLs() > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return side == Direction.UP ? spent : liquor;
    }

    @Override
    public PanelLayout panelLayout() {
        return NonferrousPanels.PRECIPITATOR;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        double t = precipitator.tempK();
        v[NonferrousPanels.T_C] = (int) Math.round(t - 273.15);
        v[NonferrousPanels.A] = (int) Math.round(AluminaPrecipitator.yieldAt(t) * 1000);
        v[NonferrousPanels.B] = (int) Math.round(precipitator.lastHydroxideKgS() * 3600);
        v[NonferrousPanels.C] = (int) Math.round(hydroxideKg);
        v[NonferrousPanels.TANK] = (int) Math.round(liquor.fill() * 1000);
        v[NonferrousPanels.TANK2] = (int) Math.round(spent.fill() * 1000);
        boolean window = t >= AluminaPrecipitator.LOW_K && t <= AluminaPrecipitator.HIGH_K;
        v[NonferrousPanels.FLAGS] = (on ? 1 << NonferrousPanels.F_ON : 0) | (running() ? 1 << NonferrousPanels.F_RUN : 0)
                | (window ? 0 : 1 << NonferrousPanels.F_WARN);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == NonferrousPanels.B_RUN) use(player);
        else if (id == NonferrousPanels.B_DOWN) precipitator.setTempK(precipitator.tempK() - 5);
        else if (id == NonferrousPanels.B_UP) precipitator.setTempK(precipitator.tempK() + 5);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        liquor.save(tag, "liquor");
        spent.save(tag, "spent");
        tag.putDouble("hydrate", hydroxideKg);
        MutableStateTree t = new MutableStateTree();
        precipitator.save(new TreeStateWriter(t));
        tag.put("precip", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        liquor.load(tag, "liquor");
        spent.load(tag, "spent");
        hydroxideKg = tag.getDouble("hydrate");
        if (tag.contains("precip")) precipitator.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("precip"))));
    }

    // --- блок реального масштаба с цельной моделью (одна модель; привод мешалки — partial) ---

    @Override
    @org.jetbrains.annotations.Nullable
    public String wholeModelId() {
        return NonferrousReal.model(getBlockState(), "");
    }

    @Override
    public net.minecraft.core.Direction wholeModelFacing() {
        return NonferrousReal.facing(getBlockState());
    }

    @Override
    public net.minecraft.core.BlockPos wholeModelLightPos() {
        return NonferrousReal.lightPos(worldPosition, getBlockState(), 4);
    }
}
