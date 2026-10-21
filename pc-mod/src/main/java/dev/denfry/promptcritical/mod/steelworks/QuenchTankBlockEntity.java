package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Закалочный бак {@code quench_tank} 2×1×2 (08 §8.7.5): вода или масло (любая другая жидкость) из труб. Садка из
 * термической печи греет среду; закалка состоится, только если среды не меньше {@value #MIN_MB} мБ и она холоднее
 * {@value #MAX_QUENCH_K} К (горячая вода даёт неполную закалку) [моё]. Вода выше 100 °C выкипает. Остывает к
 * воздуху с τ {@value #COOL_TAU_S} с физ. [BALANCE].
 */
public final class QuenchTankBlockEntity extends IronworksBlockEntity {
    public static final int CAPACITY_MB = 16_000, MIN_MB = 2_000;
    public static final double MAX_QUENCH_K = 353.15, COOL_TAU_S = 7_200, WATER_CP = 4186, OIL_CP = 2000, STEEL_CP = 650;
    private static final double AMBIENT_K = 293.15, BOIL_K = 373.15, LATENT_J_KG = 2.26e6;

    private final FluidTank tank = new FluidTank(CAPACITY_MB) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private double tempK = AMBIENT_K;

    public QuenchTankBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.QUENCH_TANK.get(), pos, state);
    }

    public double tempK() {
        return tempK;
    }

    public int amount() {
        return tank.getFluidAmount();
    }

    private boolean water() {
        return tank.getFluid().getFluid().is(FluidTags.WATER);
    }

    /**
     * Опустить {@code kg} стали при {@code steelK}: среда греется; {@code true} — закалка состоялась (среды достаточно,
     * она была холодной).
     */
    public boolean absorb(double kg, double steelK) {
        int mb = tank.getFluidAmount();
        boolean ok = mb >= MIN_MB && tempK < MAX_QUENCH_K;
        if (mb <= 0) return false;
        double cp = water() ? WATER_CP : OIL_CP;
        double q = kg * STEEL_CP * Math.max(0, steelK - tempK);
        tempK += q / (mb * cp);
        if (water() && tempK > BOIL_K) {
            double boiled = (tempK - BOIL_K) * mb * cp / LATENT_J_KG;
            tank.drain((int) Math.min(mb, Math.ceil(boiled)), IFluidHandler.FluidAction.EXECUTE);
            tempK = BOIL_K;
        }
        setChanged();
        return ok;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        double dtp = Ironworks.tickPhysS(b);
        if (tempK > AMBIENT_K + 0.01) tempK -= (tempK - AMBIENT_K) * Math.min(1, dtp / COOL_TAU_S);
        FluidStack f = tank.getFluid();
        if (!f.isEmpty()) {
            PcProperties.QuenchMedium m = water() ? PcProperties.QuenchMedium.WATER : PcProperties.QuenchMedium.OIL;
            BlockState s = getBlockState();
            if (s.getValue(PcProperties.QUENCH_MEDIUM) != m) level.setBlock(worldPosition, s.setValue(PcProperties.QUENCH_MEDIUM, m), 3);
        }
    }

    @Override
    protected void clientTick() {
        MachineFx fx = fx();
        if (fx.chance(20)) fx.smoke(fx.at(16, 15, 16), false, 0.3);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return tank;
    }

    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(SteelworksText.msg("quench_tank", tank.getFluidAmount(), SteelworksText.num(tempK - 273.15)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("temp_k", tempK);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
        if (tag.contains("temp_k")) tempK = tag.getDouble("temp_k");
    }
}
