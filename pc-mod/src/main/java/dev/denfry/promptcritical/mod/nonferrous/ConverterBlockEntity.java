package dev.denfry.promptcritical.mod.nonferrous;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.nonferrous.PeirceSmithConverter;
import dev.denfry.promptcritical.sim.nonferrous.PeirceSmithConverter.Phase;
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
 * Конвертер Пирса–Смита ({@link PeirceSmithConverter}, 08 §8.8.3) 3×2×2. Медный штейн (медно-никелевый сюда не идёт:
 * он отливается анодами для ванны в режиме Ni), черновая медь на огневое рафинирование и кварц — воронкой или рукой;
 * природный газ на дразнение — трубой к любой грани. Черновая или анодная медь — слитками по
 * {@code metallurgy.ingot_mass_kg} в склад (воронкой снизу). Кнопка «Рафинировать» на щите решает, что выдаёт плавка.
 * Поворот бочки валом Create в игре не нужен: дутьё — воздуходувкой цеха.
 */
public final class ConverterBlockEntity extends UraniumProcessBlockEntity implements dev.denfry.promptcritical.mod.client.whole.WholeModelHost {
    public static final int MB = 4_000;

    private final FeedTank gas = new FeedTank(PcFluids.SALES_GAS.source.get(), MB);
    private final PeirceSmithConverter converter = new PeirceSmithConverter();
    private final SulfurCapture so2 = new SulfurCapture();
    private double fluxKg, blisterKg, anodeKg;

    public ConverterBlockEntity(BlockPos pos, BlockState state) {
        super(NonferrousBlockEntities.PEIRCE_SMITH_CONVERTER.get(), pos, state, 6, 4, ConverterBlockEntity::charge);
    }

    static boolean charge(ItemStack s) {
        return (s.is(PcItems.SULFIDE_MATTE.get()) && !NonferrousComponents.nickel(s)) || s.is(PcItems.BLISTER_COPPER.get())
                || s.is(PcBlocks.VEIN_QUARTZ.get().asItem());
    }

    public PeirceSmithConverter converter() {
        return converter;
    }

    public FeedTank gas() {
        return gas;
    }

    public SulfurCapture so2() {
        return so2;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        double ingot = NonferrousUnits.ingotKg();
        String id = "peirce_smith_converter@" + worldPosition.toShortString();
        if (on) {
            Phase p = converter.phase();
            double cuPerMatte = ingot * Metal.COPPER.matteGrade;
            if (p == Phase.EMPTY && count(s -> s.is(PcItems.BLISTER_COPPER.get())) > 0 && count(s -> s.is(PcItems.SULFIDE_MATTE.get())) == 0) {
                double cu = 0;
                while (cu + ingot * PeirceSmithConverter.BLISTER_CU <= PeirceSmithConverter.CHARGE_CU_KG
                        && !takeOne(s -> s.is(PcItems.BLISTER_COPPER.get())).isEmpty()) cu += ingot * PeirceSmithConverter.BLISTER_CU;
                converter.chargeBlister(cu);
            } else {
                while (converter.roomCuKg() >= cuPerMatte && !takeOne(s -> s.is(PcItems.SULFIDE_MATTE.get()) && !NonferrousComponents.nickel(s)).isEmpty())
                    converter.chargeMatte(Metal.COPPER, cuPerMatte);
            }
            if (converter.phase() == Phase.SLAG_BLOW && fluxKg < converter.feKg() * dev.denfry.promptcritical.sim.nonferrous.SulfideSmelting.SIO2_PER_FE
                    && !takeOne(s -> s.is(PcBlocks.VEIN_QUARTZ.get().asItem())).isEmpty())
                fluxKg += NonferrousUnits.bulkKg() * MatteSmelterBlockEntity.QUARTZ_SIO2;
            fluxKg = Math.max(0, fluxKg - converter.step(dt, fluxKg, gas.amount()));
            gas.take(converter.gasUse());
            so2.offer(level, worldPosition, converter.lastSo2KgS() * dt, id);
            // слив: черновая — если не рафинируем, анодная — по готовности
            if (converter.phase() == Phase.BLISTER && !converter.refine()) blisterKg += converter.tapCu();
            else if (converter.phase() == Phase.ANODE) anodeKg += converter.tapCu();
        } else so2.offer(level, worldPosition, 0, id);
        double blister = ingot * PeirceSmithConverter.BLISTER_CU, anode = ingot * PeirceSmithConverter.ANODE_CU;
        while (blisterKg >= blister && emit(PcItems.BLISTER_COPPER.get())) blisterKg -= blister;
        while (anodeKg >= anode && emit(PcItems.COPPER_ANODE.get())) anodeKg -= anode;
    }

    @Override
    protected boolean running() {
        Phase p = converter.phase();
        return on && (p == Phase.SLAG_BLOW || p == Phase.COPPER_BLOW || p == Phase.REFINING) && (converter.lastO2KgS() > 0 || p == Phase.REFINING);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return gas;
    }

    @Override
    public PanelLayout panelLayout() {
        return NonferrousPanels.CONVERTER;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[NonferrousPanels.A] = (int) Math.round(converter.cuKg());
        v[NonferrousPanels.B] = (int) Math.round(converter.feKg() * 10);
        v[NonferrousPanels.C] = (int) Math.round(converter.sKg() * 10);
        v[NonferrousPanels.D] = (int) Math.round(converter.lastSo2KgS() * 3600);
        v[NonferrousPanels.E] = converter.phase().ordinal();
        v[NonferrousPanels.TANK] = (int) Math.round(gas.fill() * 1000);
        v[NonferrousPanels.FLAGS] = (on ? 1 << NonferrousPanels.F_ON : 0) | (running() ? 1 << NonferrousPanels.F_RUN : 0)
                | (converter.phase() == Phase.SLAG_BLOW && converter.lastO2KgS() == 0 ? 1 << NonferrousPanels.F_WARN : 0)
                | (converter.refine() ? 1 << NonferrousPanels.F_AUX : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == NonferrousPanels.B_RUN) use(player);
        else if (id == NonferrousPanels.B_AUX) converter.setRefine(!converter.refine());
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        gas.save(tag, "gas");
        tag.putDouble("flux", fluxKg);
        tag.putDouble("blister", blisterKg);
        tag.putDouble("anode", anodeKg);
        MutableStateTree t = new MutableStateTree();
        converter.save(new TreeStateWriter(t));
        tag.put("converter", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        gas.load(tag, "gas");
        fluxKg = tag.getDouble("flux");
        blisterKg = tag.getDouble("blister");
        anodeKg = tag.getDouble("anode");
        if (tag.contains("converter")) converter.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("converter"))));
    }

    // --- блок реального масштаба с цельной моделью (дутьё — модель _running) ---

    @Override
    @org.jetbrains.annotations.Nullable
    public String wholeModelId() {
        return NonferrousReal.model(getBlockState(), getBlockState().getValue(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING) ? "running" : "");
    }

    @Override
    public net.minecraft.core.Direction wholeModelFacing() {
        return NonferrousReal.facing(getBlockState());
    }

    @Override
    public net.minecraft.core.BlockPos wholeModelLightPos() {
        return NonferrousReal.lightPos(worldPosition, getBlockState(), 3);
    }
}
