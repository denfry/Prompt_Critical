package dev.denfry.promptcritical.mod.chem;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.petrochem.CrackedGasSeparation;
import dev.denfry.promptcritical.sim.petrochem.PyroComponent;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Газоразделение пирогаза ({@link CrackedGasSeparation}) 3×3×3: вплотную к печи пиролиза, пирогаз — от неё напрямую.
 * Закалочная колонна, компрессор, щелочная промывка, гидрирование ацетилена, ректификация. Компрессор пирогаза
 * приводит паровая турбина на паре ВД из ЗИА печи — как на промышленных установках; пара ЗИА ему хватает. Продукты:
 * этилен — сверху (1 мБ = 1 нм³), пиробензин — снизу, пропилен — с севера и юга, C4-фракция — с востока, смола
 * пиролиза — с запада (жидкости, 1 мБ = 1 л). Этан и пропан возвращаются в печь, водород с метаном — её топливом.
 */
public final class CrackedGasUnitBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int MB = 16_000;
    /** Теплота сгорания (низшая): водород 120, метан 50 МДж/кг. */
    static final double H2_J_KG = 120e6, CH4_J_KG = 50e6, NM3_PER_MOL = 0.022414;

    private final ProductTank ethylene = new ProductTank(PcFluids.ETHYLENE.source.get(), MB);
    private final ProductTank propylene = new ProductTank(PcFluids.PROPYLENE.source.get(), MB);
    private final ProductTank c4 = new ProductTank(PcFluids.C4_FRACTION.source.get(), MB);
    private final ProductTank pygas = new ProductTank(PcFluids.PYROLYSIS_GASOLINE.source.get(), MB);
    private final ProductTank fuelOil = new ProductTank(PcFluids.PYROLYSIS_FUEL_OIL.source.get(), MB);
    /** Пирогаз, принятый от печи и ещё не разделённый, моль. */
    private final double[] buffer = new double[PyroComponent.N];
    /** Игровое время последнего разделения (сервер): по нему горит свойство {@code running}. */
    private long lastWorkTick = Long.MIN_VALUE / 2;
    @Nullable
    private BlockPos furnace;
    private CrackedGasSeparation.Result last;
    private double causticKgTotal;

    public CrackedGasUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ChemBlockEntities.CRACKED_GAS_UNIT.get(), pos, state);
    }

    /** Установка по любой клетке конструкции. */
    @Nullable
    public static CrackedGasUnitBlockEntity at(Level level, BlockPos cell) {
        BlockPos c = BoilerGeometry.controllerOf(level, cell);
        return c != null && level.getBlockEntity(c) instanceof CrackedGasUnitBlockEntity u ? u : null;
    }

    void bindFurnace(BlockPos pos) {
        furnace = pos.immutable();
    }

    /** Пирогаз от печи, кг по компонентам. */
    void acceptCrackedGas(double[] kg) {
        double[] mol = PyroComponent.molesFromMass(kg);
        for (int i = 0; i < mol.length; i++) buffer[i] += mol[i];
    }

    public ProductTank ethylene() {
        return ethylene;
    }

    public ProductTank propylene() {
        return propylene;
    }

    @Nullable
    public CrackedGasSeparation.Result lastResult() {
        return last;
    }

    @Override
    public void tickDevice() {
        if (level != null && level.isClientSide) {
            clientTick();
            return;
        }
        if (!(level instanceof ServerLevel)) return;
        if (level.getGameTime() % 10 == 0) {
            BlockState st = getBlockState();
            boolean run = level.getGameTime() - lastWorkTick < 40;
            if (st.hasProperty(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING)
                    && st.getValue(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING) != run)
                level.setBlock(worldPosition, st.setValue(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING, run),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        double total = CrackedGasSeparation.Result.total(buffer);
        if (total <= 0) return;
        CrackedGasSeparation.Result r = CrackedGasSeparation.split(buffer.clone());
        // продукты не влезли — газ ждёт в буфере (колонны «на себя»)
        double ethNm3 = CrackedGasSeparation.Result.total(r.ethylene()) * NM3_PER_MOL;
        double propL = litres(r.propylene(), 514), c4L = litres(r.c4(), 620), pyL = litres(r.pygas(), 830), oilL = litres(r.fuelOil(), 1080);
        if (ethylene.room() < ethNm3 || propylene.room() < propL || c4.room() < c4L || pygas.room() < pyL || fuelOil.room() < oilL) return;
        ethylene.add(ethNm3);
        propylene.add(propL);
        c4.add(c4L);
        pygas.add(pyL);
        fuelOil.add(oilL);
        double tailJ = r.fuelGas()[PyroComponent.H2.ordinal()] * PyroComponent.H2.molarMassG / 1000 * H2_J_KG
                + r.fuelGas()[PyroComponent.CH4.ordinal()] * PyroComponent.CH4.molarMassG / 1000 * CH4_J_KG;
        double ethaneKg = PyroComponent.massKg(r.ethane()), propaneKg = PyroComponent.massKg(r.propane());
        if (furnace != null && level.getBlockEntity(furnace) instanceof PyrolysisFurnaceBlockEntity f)
            f.acceptRecycle(ethaneKg, propaneKg, tailJ);
        causticKgTotal += r.causticKgS();
        last = r;
        lastWorkTick = level.getGameTime();
        Arrays.fill(buffer, 0);
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    /**
     * Пока газ идёт (арт — art/blockbench/petrochem/process_units.author.js): из концевых уплотнений паровой турбины
     * компрессора сочится пар (лабиринтовые уплотнения отсасывают пар не до конца — у старых машин всегда видно
     * облачко у вала), над закалочной колонной курится воздушник.
     */
    private void clientTick() {
        if (!(getBlockState().hasProperty(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING)
                && getBlockState().getValue(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING))) return;
        var fx = dev.denfry.promptcritical.mod.fx.MachineFx.of(this);
        if (fx == null) return;
        if (fx.chance(3)) fx.puff(fx.at(15.2, -7, -9), new net.minecraft.world.phys.Vec3(-0.02, 0.03, 0), 1);
        if (fx.chance(3)) fx.puff(fx.at(25.2, -7, -9), new net.minecraft.world.phys.Vec3(0.02, 0.03, 0), 1);
        if (fx.chance(2)) fx.wisp(fx.at(-6, 32, 20), 0.2);
    }

    private static double litres(double[] mol, double densityKgM3) {
        return PyroComponent.massKg(mol) / densityKgM3 * 1000;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == null || side == Direction.UP) return ethylene;
        return switch (side) {
            case DOWN -> pygas;
            case NORTH, SOUTH -> propylene;
            case EAST -> c4;
            default -> fuelOil;
        };
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
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
        return InteractionResult.PASS;
    }

    @Override
    public PanelLayout panelLayout() {
        return ChemPanels.SEPARATION;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[ChemPanels.S_TANK0] = (int) Math.round(ethylene.fill() * 1000);
        out[ChemPanels.S_TANK0 + 1] = (int) Math.round(propylene.fill() * 1000);
        out[ChemPanels.S_TANK0 + 2] = (int) Math.round(c4.fill() * 1000);
        out[ChemPanels.S_TANK0 + 3] = (int) Math.round(pygas.fill() * 1000);
        out[ChemPanels.S_TANK0 + 4] = (int) Math.round(fuelOil.fill() * 1000);
        out[ChemPanels.S_FLAGS] = furnace != null ? 1 << ChemPanels.SF_FURNACE : 0;
        CrackedGasSeparation.Result r = last;
        if (r == null) return;
        out[ChemPanels.S_C2H4_X100] = (int) Math.round(r.ethylenePurity() * 10000);
        out[ChemPanels.S_C3H6_X100] = (int) Math.round(r.propylenePurity() * 10000);
        out[ChemPanels.S_CAUSTIC_G] = (int) Math.round(causticKgTotal * 1000);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ethylene.save(tag, "ethylene");
        propylene.save(tag, "propylene");
        c4.save(tag, "c4");
        pygas.save(tag, "pygas");
        fuelOil.save(tag, "fuel_oil");
        tag.putDouble("caustic", causticKgTotal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ethylene.load(tag, "ethylene");
        propylene.load(tag, "propylene");
        c4.load(tag, "c4");
        pygas.load(tag, "pygas");
        fuelOil.load(tag, "fuel_oil");
        causticKgTotal = tag.getDouble("caustic");
    }
}
