package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Криогенный бак {@code cryogenic_tank} 1×3×1 (08 §8.11): двустенный бак LOX или LIN ({@code kind=storage}) или
 * оребрённый атмосферный испаритель ({@code kind=vaporizer}), который газифицирует жидкость до {@value #VAPORIZE_L_TICK}
 * л/тик: 1 л LOX — 0,8 нм³ O₂, 1 л LIN — 0,645 нм³ N₂ (газ 1 мБ = 1 нм³). Испаритель: жидкость сверху, газ — с боков и
 * снизу. Shift + ПКМ — сменить вид.
 */
public final class CryoTankBlockEntity extends IronworksBlockEntity {
    public static final double RHO_LOX = 1141, RHO_LIN = 807, RHO_O2 = 1.429, RHO_N2 = 1.2506;
    public static final int CAPACITY_MB = 24_000;
    public static final double VAPORIZE_L_TICK = 1;

    private final FluidTank tank = new FluidTank(CAPACITY_MB, CryoTankBlockEntity::cryogen) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final GasBuffer gas = new GasBuffer(8_000, s -> s.getFluid().isSame(PcFluids.OXYGEN.source.get())
            || s.getFluid().isSame(PcFluids.NITROGEN.source.get()), this::setChanged);
    private double carryL;

    public CryoTankBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.CRYOGENIC_TANK.get(), pos, state);
    }

    static boolean cryogen(FluidStack s) {
        return s.getFluid().isSame(PcFluids.LIQUID_OXYGEN.source.get()) || s.getFluid().isSame(PcFluids.LIQUID_NITROGEN.source.get());
    }

    public boolean vaporizer() {
        BlockState s = getBlockState();
        return s.hasProperty(PcProperties.CRYO_KIND) && s.getValue(PcProperties.CRYO_KIND) == PcProperties.CryoKind.VAPORIZER;
    }

    public FluidTank tank() {
        return tank;
    }

    public GasBuffer gas() {
        return gas;
    }

    /** Налить жидкость от блока разделения; вернуть налитое, мБ. */
    public int fillLiquid(FluidStack s) {
        return tank.fill(s, IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        if (!vaporizer() || tank.isEmpty()) return;
        boolean lox = tank.getFluid().getFluid().isSame(PcFluids.LIQUID_OXYGEN.source.get());
        double nm3PerL = lox ? RHO_LOX / 1000 / RHO_O2 : RHO_LIN / 1000 / RHO_N2;
        Fluid g = lox ? PcFluids.OXYGEN.source.get() : PcFluids.NITROGEN.source.get();
        double litres = Math.min(Math.min(VAPORIZE_L_TICK, tank.getFluidAmount() - carryL), gas.room() / nm3PerL);
        if (litres <= 0) return;
        double put = gas.put(g, litres * nm3PerL);
        carryL += put / nm3PerL;
        int whole = (int) Math.floor(carryL);
        if (whole > 0) {
            tank.drain(whole, IFluidHandler.FluidAction.EXECUTE);
            carryL -= whole;
        }
    }

    @Override
    protected void clientTick() {
        if (!vaporizer()) return;
        MachineFx fx = fx();
        if (fx.chance(2)) fx.wisp(fx.at(8, 4 + fx.random().nextInt(40), 8), 0.6);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (!vaporizer()) return tank;
        return side == null || side == Direction.UP ? new LiquidIn() : gas.output();
    }

    @Override
    public InteractionResult use(Player player) {
        if (player.isShiftKeyDown()) {
            PcProperties.CryoKind next = vaporizer() ? PcProperties.CryoKind.STORAGE : PcProperties.CryoKind.VAPORIZER;
            level.setBlock(worldPosition, getBlockState().setValue(PcProperties.CRYO_KIND, next), Block.UPDATE_ALL);
            return InteractionResult.SUCCESS;
        }
        player.displayClientMessage(SteelworksText.msg("cryo_tank", tank.isEmpty() ? SteelworksText.msg("empty") : tank.getFluid().getHoverName(),
                tank.getFluidAmount(), CAPACITY_MB, gas.amount()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.put("gas", gas.save(registries));
        tag.putDouble("carry_l", carryL);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
        if (tag.contains("gas")) gas.load(registries, tag.getCompound("gas"));
        carryL = tag.getDouble("carry_l");
    }

    /** Вход испарителя: только наливают. */
    private final class LiquidIn implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int i) {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int i) {
            return CAPACITY_MB;
        }

        @Override
        public boolean isFluidValid(int i, FluidStack stack) {
            return cryogen(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return tank.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
