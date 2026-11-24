package dev.denfry.promptcritical.mod.production;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.AirQualityService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.environment.EmissionLedger;
import dev.denfry.promptcritical.sim.pipeline.CrudeViscosity;
import dev.denfry.promptcritical.sim.pipeline.LineHeater;
import dev.denfry.promptcritical.sim.production.ProducingReservoir;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Путевой подогреватель ПП-0,63 реального масштаба (09 §9.7.1, {@link LineHeater}): нефть идёт по змеевику в водяной
 * бане, горелка на попутном газе держит баню горячей. Нефть входит с температурой воздуха (выкидные линии лежат на
 * земле) и выходит нагретой — не выше бани без недогрева змеевика и не больше, чем даёт тепловая мощность 0,63 МВт.
 * Газ на горелку — в патрубок на западном днище; без газа горелка гаснет. Дымовые газы уходят в учёт выбросов из устья
 * трубы на высоте {@value #STACK_M} м.
 * <p>
 * Патрубки нефти — оба на восточном днище: трубы заливают нефть в змеевик и забирают нагретую; газ — по своему
 * патрубку. Обогрев ниже по линии видят машины транспорта нефти ({@link CrudeLine}): насос — по вязкости.
 */
public final class LineHeaterBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost, CrudeLine.Source,
        dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity {
    public static final int CRUDE_MB = 8_000, GAS_MB = 1_000;
    public static final double DENSITY = ProducingReservoir.STOCK_OIL_KGM3;
    /** Высота устья дымовой трубы, м, и температура уходящих газов, К (печи с промежуточным теплоносителем) [моё]. */
    public static final double STACK_M = 8.8, FLUE_K = 450;
    /** Дымовые газы на 1 нм³ попутного газа при избытке воздуха 1,15, кг [моё: ≈ 10,5 нм³ воздуха + газ]. */
    public static final double FLUE_KG_PER_NM3 = 14;
    public static final int B_IGNITE = 0, B_SHUT = 1;
    /** Устье трубы в модели (верх клетки (−4, 8, 0)). */
    private static final BlockPos STACK_CELL = new BlockPos(-4, 8, 0);

    private final FeedTank inlet = new FeedTank(PcFluids.CRUDE_OIL.source.get(), CRUDE_MB);
    private final ProductTank outlet = new ProductTank(PcFluids.CRUDE_OIL.source.get(), CRUDE_MB);
    private final FeedTank fuel = new FeedTank(PcFluids.ASSOCIATED_GAS.source.get(), GAS_MB);
    private final IFluidHandler ports = new Ports();
    private boolean burner;
    private double inletC = Double.NaN, outletC = Double.NaN, firing, heatW, gasNm3s, flowKgS;
    private int ticks;

    public LineHeaterBlockEntity(BlockPos pos, BlockState state) {
        super(ProductionBlockEntities.LINE_HEATER.get(), pos, state);
    }

    public FeedTank inlet() {
        return inlet;
    }

    public ProductTank outlet() {
        return outlet;
    }

    public FeedTank fuel() {
        return fuel;
    }

    public boolean burner() {
        return burner;
    }

    public void setBurner(boolean v) {
        burner = v;
        setChanged();
    }

    public double firing() {
        return firing;
    }

    public double heatToOilW() {
        return heatW;
    }

    public double gasNm3s() {
        return gasNm3s;
    }

    public double inletC() {
        return inletC;
    }

    /** Температура нефти на выходе змеевика, °C. */
    @Override
    public double crudeC() {
        return outletC;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) {
            clientTick();
            return;
        }
        if (ticks++ % 20 != 0) return;
        inletC = CrudeLine.ambientC(sl, worldPosition);
        // нефть за секунду: всё, что налили в змеевик и что помещается на выходе
        double mb = Math.min(inlet.amount(), outlet.room());
        flowKgS = mb / 1000 * DENSITY;
        // доля мощности горелки: сколько газа есть на полную секунду работы
        double needFull = LineHeater.fuelNm3s(LineHeater.DUTY_W, 1);
        firing = burner ? Math.min(1, fuel.amount() / needFull) : 0;
        if (burner && firing <= 0) burner = false; // кончился газ — пламя сорвалось, розжиг заново
        outletC = LineHeater.outletC(inletC, flowKgS, firing);
        heatW = LineHeater.heatToOilW(inletC, outletC, flowKgS);
        gasNm3s = LineHeater.fuelNm3s(heatW, firing);
        if (mb > 0) outlet.add(inlet.take(mb));
        if (gasNm3s > 0) fuel.take(gasNm3s);
        if (gasNm3s > 0) {
            Direction f = wholeModelFacing();
            BlockPos top = CrudeLine.portCell(worldPosition, f, STACK_CELL).above();
            AirQualityService.emitGasFiring(sl, top, STACK_M, FLUE_K, gasNm3s * LineHeater.GAS_LHV_J_NM3, gasNm3s * FLUE_KG_PER_NM3,
                    0.06, 0.005, "line_heater@" + worldPosition.toShortString(), EmissionLedger.Category.PROCESS);
        }
        BlockState st = getBlockState();
        boolean lit = firing > 0;
        if (st.getValue(BlockStateProperties.LIT) != lit) level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_ALL);
        if (ticks % 200 == 1) setChanged();
    }

    private void clientTick() {
        BlockState st = getBlockState();
        if (!st.getValue(BlockStateProperties.LIT)) return;
        MachineFx fx = MachineFx.of(level, worldPosition, st.getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (fx == null) return;
        // горячий дым из устья трубы (−56, 144, 8), огонь в смотровом окне горелки на западном днище
        if (fx.chance(4)) fx.wisp(fx.at(-56, 144, 8), 0.1);
        if (fx.chance(2)) fx.flame(fx.at(-74, 26, 8), 0.02);
    }

    /** Нефть и газ наливают по своим жидкостям, сливается только нагретая нефть. */
    private final class Ports implements IFluidHandler {
        @Override
        public int getTanks() {
            return 3;
        }

        private IFluidHandler tank(int i) {
            return i == 0 ? inlet : i == 1 ? outlet : fuel;
        }

        @Override
        public FluidStack getFluidInTank(int i) {
            return tank(i).getFluidInTank(0);
        }

        @Override
        public int getTankCapacity(int i) {
            return tank(i).getTankCapacity(0);
        }

        @Override
        public boolean isFluidValid(int i, FluidStack stack) {
            return i != 1 && tank(i).isFluidValid(0, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.getFluid().isSame(PcFluids.ASSOCIATED_GAS.source.get())) return fuel.fill(resource, action);
            return inlet.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return outlet.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return outlet.drain(maxDrain, action);
        }
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return ports;
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
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing(), 2);
    }

    @Override
    public PanelLayout panelLayout() {
        return TransportPanels.LINE_HEATER;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == B_IGNITE) setBurner(fuel.amount() > 0);
        else if (id == B_SHUT) setBurner(false);
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[TransportPanels.H_IN_C10] = Double.isFinite(inletC) ? (int) Math.round(inletC * 10) : 0;
        out[TransportPanels.H_OUT_C10] = Double.isFinite(outletC) ? (int) Math.round(outletC * 10) : 0;
        out[TransportPanels.H_DUTY_PERMIL] = (int) Math.round(heatW / LineHeater.DUTY_W * 1000);
        out[TransportPanels.H_GAS_M3H10] = (int) Math.round(gasNm3s * 3600 * 10);
        out[TransportPanels.H_FLOW_TH10] = (int) Math.round(flowKgS * 3.6 * 10);
        out[TransportPanels.H_VISC_CP10] = Double.isFinite(outletC) ? (int) Math.round(CrudeViscosity.deadOilCp(DENSITY, outletC) * 10) : 0;
        out[TransportPanels.H_FUEL_PERMIL] = (int) Math.round(fuel.fill() * 1000);
        out[TransportPanels.H_FLAGS] = firing > 0 ? 1 << TransportPanels.HF_LIT : 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("burner", burner);
        inlet.save(tag, "inlet");
        outlet.save(tag, "outlet");
        fuel.save(tag, "fuel");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        burner = tag.getBoolean("burner");
        inlet.load(tag, "inlet");
        outlet.load(tag, "outlet");
        fuel.load(tag, "fuel");
    }
}
