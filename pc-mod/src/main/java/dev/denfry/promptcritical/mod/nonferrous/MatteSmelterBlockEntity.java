package dev.denfry.promptcritical.mod.nonferrous;

import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.nonferrous.MatteSmelter;
import dev.denfry.promptcritical.sim.nonferrous.SulfideSmelting;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Отражательная печь ({@link MatteSmelter}, 08 §8.8.3, §8.8.5) 3×3×3. Загрузка воронкой или рукой: флотоконцентрат
 * (медный или медно-никелевый) с кварцем {@code vein_quartz} — плавка на штейн; оловянный концентрат {@code tin_dust}
 * с коксом — восстановительная плавка (концентрат идёт первым). Топливный газ и кислород — трубой к любой грани;
 * штейн и олово — в склад (воронкой снизу). SO₂ — газоходом в сернокислотную установку рядом, иначе в трубу.
 */
public final class MatteSmelterBlockEntity extends UraniumProcessBlockEntity implements dev.denfry.promptcritical.mod.client.whole.WholeModelHost {
    /** Дутьевые вентиляторы горелок и загрузка шихты, Вт. */
    @Override
    protected double ratedPowerW() {
        return 55e3;
    }

    public static final int MB = 8_000;
    /** Кремнезём кварцевой жилы (08 §8.1.2: SiO₂ > 99 %). */
    public static final double QUARTZ_SIO2 = 0.99;

    private final FeedTank fuel = new FeedTank(PcFluids.SALES_GAS.source.get(), MB);
    private final FeedTank oxygen = new FeedTank(PcFluids.OXYGEN.source.get(), MB);
    private final IFluidHandler inlet = new MultiInlet(fuel, oxygen);
    private final SulfurCapture so2 = new SulfurCapture();
    /** Металл в загруженном концентрате по линиям, кг; кварц и кокс в печи, кг; олово в концентрате, кг. */
    private double cuFeedKg, niFeedKg, fluxKg, cokeKg, tinFeedKg;
    /** Металл в штейне и олово, ещё не вылитые, кг. */
    private double cuMatteKg, niMatteKg, tinKg;
    private double lastMetalKgS, lastTinKgS, lastSo2KgS;

    public MatteSmelterBlockEntity(BlockPos pos, BlockState state) {
        super(NonferrousBlockEntities.MATTE_SMELTER.get(), pos, state, 6, 4, MatteSmelterBlockEntity::charge);
    }

    static boolean charge(ItemStack s) {
        return s.is(PcItems.FLOTATION_CONCENTRATE.get()) || s.is(PcBlocks.VEIN_QUARTZ.get().asItem()) || s.is(PcItems.TIN_DUST.get())
                || s.is(PcItems.COKE.get());
    }

    public FeedTank fuel() {
        return fuel;
    }

    public FeedTank oxygen() {
        return oxygen;
    }

    public double lastSo2KgS() {
        return lastSo2KgS;
    }

    public SulfurCapture so2() {
        return so2;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        lastMetalKgS = lastTinKgS = lastSo2KgS = 0;
        double bulk = NonferrousUnits.bulkKg(), ingot = NonferrousUnits.ingotKg();
        if (on) {
            double heatJ = fuel.amount() * NonferrousUnits.FUEL_J_NM3;
            // концентрат: по одному мешку, пока в печи нет металла
            if (cuFeedKg <= 0 && niFeedKg <= 0) {
                ItemStack c = takeOne(s -> s.is(PcItems.FLOTATION_CONCENTRATE.get()));
                if (!c.isEmpty()) {
                    if (NonferrousComponents.nickel(c)) niFeedKg += bulk * Metal.NICKEL.concentrateGrade;
                    else cuFeedKg += bulk * Metal.COPPER.concentrateGrade;
                }
            }
            if (cuFeedKg > 0 || niFeedKg > 0) {
                Metal m = cuFeedKg > 0 ? Metal.COPPER : Metal.NICKEL;
                double feed = m == Metal.COPPER ? cuFeedKg : niFeedKg;
                double fluxNeed = SulfideSmelting.smeltingFlux(m) * MatteSmelter.CONCENTRATE_KG_S * m.concentrateGrade * dt;
                while (fluxKg < fluxNeed && !takeOne(s -> s.is(PcBlocks.VEIN_QUARTZ.get().asItem())).isEmpty()) fluxKg += bulk * QUARTZ_SIO2;
                double kg = MatteSmelter.matteKg(m, dt, feed, heatJ, oxygen.amount() * NonferrousUnits.OXYGEN_KG_NM3, fluxKg);
                if (kg > 0) {
                    fuel.take(kg * MatteSmelter.matteHeatPerMetal(m) / NonferrousUnits.FUEL_J_NM3);
                    oxygen.take(kg * SulfideSmelting.smeltingO2(m) / NonferrousUnits.OXYGEN_KG_NM3);
                    fluxKg = Math.max(0, fluxKg - kg * SulfideSmelting.smeltingFlux(m));
                    double toMatte = kg * MatteSmelter.MATTE_RECOVERY;
                    if (m == Metal.COPPER) {
                        cuFeedKg -= kg;
                        cuMatteKg += toMatte;
                    } else {
                        niFeedKg -= kg;
                        niMatteKg += toMatte;
                    }
                    double gas = kg * SulfideSmelting.smeltingSo2(m);
                    lastSo2KgS = gas / dt;
                    so2.offer(level, worldPosition, gas, "matte_smelter@" + worldPosition.toShortString());
                    lastMetalKgS = kg / dt;
                }
            } else {
                // олово: концентрат и кокс по мешку
                if (tinFeedKg <= 0) {
                    if (!takeOne(s -> s.is(PcItems.TIN_DUST.get())).isEmpty()) tinFeedKg += NonferrousUnits.TIN_ITEM_KG * NonferrousUnits.TIN_DUST_SN;
                }
                if (tinFeedKg > 0) {
                    double cokeNeed = MatteSmelter.cokePerTin() * MatteSmelter.TIN_KG_S * dt;
                    while (cokeKg < cokeNeed && !takeOne(s -> s.is(PcItems.COKE.get())).isEmpty()) cokeKg += bulk;
                    double kg = MatteSmelter.tinKg(dt, tinFeedKg, heatJ, cokeKg);
                    if (kg > 0) {
                        fuel.take(kg * MatteSmelter.TIN_HEAT_J_PER_KG / NonferrousUnits.FUEL_J_NM3);
                        cokeKg = Math.max(0, cokeKg - kg * MatteSmelter.cokePerTin());
                        tinFeedKg -= kg;
                        tinKg += kg * MatteSmelter.TIN_RECOVERY;
                        lastTinKgS = kg / dt;
                    }
                }
                so2.offer(level, worldPosition, 0, "matte_smelter@" + worldPosition.toShortString());
            }
        }
        double cuPer = ingot * Metal.COPPER.matteGrade, niPer = ingot * Metal.NICKEL.matteGrade;
        while (cuMatteKg >= cuPer && emit(new ItemStack(PcItems.SULFIDE_MATTE.get()))) cuMatteKg -= cuPer;
        while (niMatteKg >= niPer && emit(NonferrousComponents.nickel(new ItemStack(PcItems.SULFIDE_MATTE.get()), true))) niMatteKg -= niPer;
        while (tinKg >= NonferrousUnits.TIN_ITEM_KG && emit(PcItems.TIN_INGOT.get())) tinKg -= NonferrousUnits.TIN_ITEM_KG;
    }

    @Override
    protected boolean running() {
        return lastMetalKgS > 0 || lastTinKgS > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return inlet;
    }

    @Override
    public PanelLayout panelLayout() {
        return NonferrousPanels.SMELTER;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[NonferrousPanels.A] = (int) Math.round(lastMetalKgS * 3600);
        v[NonferrousPanels.B] = (int) Math.round(lastTinKgS * 3600);
        v[NonferrousPanels.C] = (int) Math.round(lastSo2KgS * 3600);
        v[NonferrousPanels.D] = (int) Math.round(so2.capturedKg());
        v[NonferrousPanels.TANK] = (int) Math.round(fuel.fill() * 1000);
        v[NonferrousPanels.TANK2] = (int) Math.round(oxygen.fill() * 1000);
        boolean feed = cuFeedKg > 0 || niFeedKg > 0 || tinFeedKg > 0;
        v[NonferrousPanels.FLAGS] = (on ? 1 << NonferrousPanels.F_ON : 0) | (running() ? 1 << NonferrousPanels.F_RUN : 0)
                | (on && feed && !running() ? 1 << NonferrousPanels.F_WARN : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == NonferrousPanels.B_RUN) use(player);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        fuel.save(tag, "fuel");
        oxygen.save(tag, "o2");
        tag.putDouble("cu_feed", cuFeedKg);
        tag.putDouble("ni_feed", niFeedKg);
        tag.putDouble("flux", fluxKg);
        tag.putDouble("coke", cokeKg);
        tag.putDouble("tin_feed", tinFeedKg);
        tag.putDouble("cu_matte", cuMatteKg);
        tag.putDouble("ni_matte", niMatteKg);
        tag.putDouble("tin", tinKg);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        fuel.load(tag, "fuel");
        oxygen.load(tag, "o2");
        cuFeedKg = tag.getDouble("cu_feed");
        niFeedKg = tag.getDouble("ni_feed");
        fluxKg = tag.getDouble("flux");
        cokeKg = tag.getDouble("coke");
        tinFeedKg = tag.getDouble("tin_feed");
        cuMatteKg = tag.getDouble("cu_matte");
        niMatteKg = tag.getDouble("ni_matte");
        tinKg = tag.getDouble("tin");
    }

    // --- блок реального масштаба с цельной моделью (горит — модель _lit) ---

    @Override
    @org.jetbrains.annotations.Nullable
    public String wholeModelId() {
        return NonferrousReal.model(getBlockState(), getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ? "lit" : "");
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
