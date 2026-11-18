package dev.denfry.promptcritical.mod.nonferrous;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.nonferrous.BayerDigester;
import dev.denfry.promptcritical.sim.nonferrous.BayerProcess;
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
 * Автоклав выщелачивания боксита со сгустителем ({@link BayerDigester}, 08 §8.8.1). Боксит — воронкой или рукой
 * (Al₂O₃ — по пробе), щёлочь (электролитическая, 11 % NaOH — свежая и отработанная из декомпозёра) и топливный газ на
 * пар — трубами к боковым граням; алюминатный раствор — сверху, красный шлам — снизу. Температуру автоклава задают
 * кнопками щита: гиббсит берётся при 140–150 °C, бёмит — при 240–250 °C.
 */
public final class BayerDigesterBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 8_000;

    private final FeedTank caustic = new FeedTank(PcFluids.SODIUM_HYDROXIDE_SOLUTION.source.get(), MB);
    private final FeedTank fuel = new FeedTank(PcFluids.SALES_GAS.source.get(), MB / 2);
    private final IFluidHandler inlet = new MultiInlet(caustic, fuel);
    private final ProductTank liquor = new ProductTank(PcFluids.SODIUM_ALUMINATE_LIQUOR.source.get(), MB);
    private final ProductTank mud = new ProductTank(PcFluids.RED_MUD.source.get(), MB);
    private final BayerDigester digester = new BayerDigester();
    /** Боксит в автоклаве: масса и средневзвешенное содержание Al₂O₃. */
    private double bauxiteKg, al2o3 = BayerProcess.DEFAULT_AL2O3;

    public BayerDigesterBlockEntity(BlockPos pos, BlockState state) {
        super(NonferrousBlockEntities.BAYER_DIGESTER.get(), pos, state, 4, 0, BayerDigesterBlockEntity::bauxite);
    }

    static boolean bauxite(ItemStack s) {
        return s.is(PcItems.RAW_BAUXITE.get());
    }

    public BayerDigester digester() {
        return digester;
    }

    public FeedTank caustic() {
        return caustic;
    }

    public FeedTank fuel() {
        return fuel;
    }

    public ProductTank liquor() {
        return liquor;
    }

    public ProductTank mud() {
        return mud;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        if (!on) {
            digester.step(dt, 0, 0, al2o3);
            return;
        }
        double t = digester.tempK();
        double perKg = BayerProcess.netAl2o3PerKg(al2o3, t);
        // загрузка боксита в автоклав до запаса на шаг
        double want = perKg > 0 ? BayerDigester.LIQUOR_LS * dt * BayerProcess.NET_AL2O3_KG_L / perKg : 0;
        while (bauxiteKg < want) {
            ItemStack ore = takeOne(BayerDigesterBlockEntity::bauxite);
            if (ore.isEmpty()) break;
            double kg = NonferrousUnits.bulkKg();
            double a = NonferrousUnits.assay(ore, BayerProcess.DEFAULT_AL2O3);
            al2o3 = (al2o3 * bauxiteKg + a * kg) / (bauxiteKg + kg);
            bauxiteKg += kg;
        }
        perKg = BayerProcess.netAl2o3PerKg(al2o3, t);
        // раствор, который можно прокачать: щёлочь (с потерей в шлам), место под раствор и шлам, пар
        double litres = liquor.room();
        if (perKg > 0) {
            double bauxitePerL = BayerProcess.NET_AL2O3_KG_L / perKg;
            double naohPerL = BayerProcess.NAOH_KG_L + bauxitePerL * BayerProcess.naohLossPerKg();
            litres = Math.min(litres, caustic.amount() * NonferrousUnits.CAUSTIC_NAOH_KG_L / naohPerL);
            double mudLPerL = bauxitePerL * BayerProcess.redMudPerKg(al2o3, t) * BayerDigester.mudLitresPerKg();
            litres = Math.min(litres, mud.room() / mudLPerL);
            double heatPerL = BayerDigester.LIQUOR_KG_L * BayerDigester.CP_J_KGK * Math.max(0, t - BayerDigester.FEED_K)
                    * (1 - BayerDigester.HEAT_RECOVERY);
            if (heatPerL > 0) litres = Math.min(litres, fuel.amount() * NonferrousUnits.FUEL_J_NM3 * NonferrousUnits.BOILER_EFFICIENCY / heatPerL);
        }
        double done = digester.step(dt, Math.max(0, litres), bauxiteKg, al2o3);
        double bauxite = digester.lastBauxiteKgS() * dt;
        bauxiteKg = Math.max(0, bauxiteKg - bauxite);
        caustic.take((done * BayerProcess.NAOH_KG_L + digester.lastNaohLossKgS() * dt) / NonferrousUnits.CAUSTIC_NAOH_KG_L);
        fuel.take(digester.lastHeatW() * dt / (NonferrousUnits.FUEL_J_NM3 * NonferrousUnits.BOILER_EFFICIENCY));
        liquor.add(done);
        mud.add(digester.lastMudKgS() * dt * BayerDigester.mudLitresPerKg());
    }

    @Override
    protected boolean running() {
        return on && digester.lastLiquorLs() > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == Direction.UP) return liquor;
        if (side == Direction.DOWN) return mud;
        return inlet;
    }

    @Override
    public PanelLayout panelLayout() {
        return NonferrousPanels.DIGESTER;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        double t = digester.tempK();
        v[NonferrousPanels.T_C] = (int) Math.round(t - 273.15);
        v[NonferrousPanels.A] = (int) Math.round(BayerProcess.extraction(t) * 1000);
        v[NonferrousPanels.B] = (int) Math.round(digester.lastAl2o3KgS() * 3600);
        double tPerT = BayerProcess.bauxitePerAlumina(al2o3, t);
        v[NonferrousPanels.C] = Double.isFinite(tPerT) ? (int) Math.round(tPerT * 100) : 0;
        v[NonferrousPanels.D] = (int) Math.round(digester.lastBauxiteKgS() > 0
                ? digester.lastNaohLossKgS() / digester.lastAl2o3KgS() * 1000 : 0);
        v[NonferrousPanels.TANK] = (int) Math.round(liquor.fill() * 1000);
        v[NonferrousPanels.TANK2] = (int) Math.round(mud.fill() * 1000);
        v[NonferrousPanels.TANK3] = (int) Math.round(caustic.fill() * 1000);
        v[NonferrousPanels.FLAGS] = (on ? 1 << NonferrousPanels.F_ON : 0) | (running() ? 1 << NonferrousPanels.F_RUN : 0)
                | (BayerProcess.extraction(t) < 1 - BayerProcess.BOEHMITE_SHARE - 1e-9 ? 1 << NonferrousPanels.F_WARN : 0)
                | (on && fuel.amount() < 1 ? 1 << NonferrousPanels.F_AUX : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == NonferrousPanels.B_RUN) use(player);
        else if (id == NonferrousPanels.B_DOWN) digester.setTempK(digester.tempK() - 10);
        else if (id == NonferrousPanels.B_UP) digester.setTempK(digester.tempK() + 10);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        caustic.save(tag, "caustic");
        fuel.save(tag, "fuel");
        liquor.save(tag, "liquor");
        mud.save(tag, "mud");
        tag.putDouble("bauxite", bauxiteKg);
        tag.putDouble("grade", al2o3);
        MutableStateTree t = new MutableStateTree();
        digester.save(new TreeStateWriter(t));
        tag.put("digester", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        caustic.load(tag, "caustic");
        fuel.load(tag, "fuel");
        liquor.load(tag, "liquor");
        mud.load(tag, "mud");
        bauxiteKg = tag.getDouble("bauxite");
        al2o3 = tag.contains("grade") ? tag.getDouble("grade") : BayerProcess.DEFAULT_AL2O3;
        if (tag.contains("digester")) digester.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("digester"))));
    }
}
