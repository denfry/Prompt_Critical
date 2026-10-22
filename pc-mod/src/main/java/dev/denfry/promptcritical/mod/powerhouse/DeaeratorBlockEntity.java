package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.ops.WaterTreatmentBlockEntity;
import dev.denfry.promptcritical.sim.plant.WaterTubeUnit;
import dev.denfry.promptcritical.sim.utilities.Deaerator;
import dev.denfry.promptcritical.sim.utilities.WaterGrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Атмосферный деаэратор {@code deaerator} (08 §8.12): умягчённая вода ({@code softened_water}) греется паром из
 * барабана ближайшего котла I1 ({@value WaterTubeBoilerBlockEntity#LINK_RANGE} блоков) до кипения при 0,12 МПа — на
 * выходе {@code boiler_feedwater}, 104 °C, O₂ ≤ 15 мкг/кг, вместе с конденсатом греющего пара. Пар идёт, пока в
 * барабане больше {@value WaterTubeUnit#DEAERATOR_MIN_PA} Па; производительность — до {@value #MAX_WATER_KG_S} кг/с.
 */
public final class DeaeratorBlockEntity extends IronworksBlockEntity {
    public static final int TANK_MB = 4000;
    /** Колонка ДА-25: до 25 т/ч ≈ 7 кг/с воды. */
    public static final double MAX_WATER_KG_S = 7;
    private static final double TICK_S = 0.05;
    private static final int LINK_PERIOD = 40, RUN_HOLD = 20;

    private final FluidTank in = new FluidTank(TANK_MB, fs -> WaterTreatmentBlockEntity.grade(fs.getFluid()) == WaterGrade.SOFTENED) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final FluidTank out = new FluidTank(TANK_MB, fs -> WaterTreatmentBlockEntity.grade(fs.getFluid()) == WaterGrade.DEAERATED) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IFluidHandler ports = new Ports();
    @Nullable
    private BlockPos boiler;
    private double inDebt, outCarry, steamKgH;
    private int runTicks;

    public DeaeratorBlockEntity(BlockPos pos, BlockState state) {
        super(PowerhouseBlockEntities.DEAERATOR.get(), pos, state);
    }

    public FluidTank input() {
        return in;
    }

    public FluidTank output() {
        return out;
    }

    @Nullable
    private WaterTubeBoilerBlockEntity boiler() {
        return boiler != null && level.getBlockEntity(boiler) instanceof WaterTubeBoilerBlockEntity b && b.shape() != null ? b : null;
    }

    @Override
    protected void serverTick(SimLevelBinding binding) {
        if (level.getGameTime() % LINK_PERIOD == Math.floorMod(worldPosition.asLong(), LINK_PERIOD) || boiler == null) {
            WaterTubeBoilerBlockEntity b = WaterTubeBoilerBlockEntity.near(level, worldPosition);
            boiler = b == null ? null : b.getBlockPos();
        }
        WaterTubeBoilerBlockEntity b = boiler();
        if (runTicks > 0) runTicks--;
        if (b == null) {
            setStateFlag(PcProperties.RUNNING, false);
            return;
        }
        double ratio = Deaerator.waterPerSteam(b.drumSteamEnthalpy(), 288.15);
        double steam = 0;
        if (ratio > 0) {
            // пар, отданный с прошлого тика, нагревает воду; заявка — под воду в баке и место на выходе
            double hot = b.drawDeaeratorSteam(0);
            double water = Math.min(hot * ratio, in.getFluidAmount() - inDebt);
            steam = water / ratio;
            if (water > 0) {
                inDebt += water;
                int mb = (int) Math.floor(inDebt);
                if (mb > 0) {
                    in.drain(mb, IFluidHandler.FluidAction.EXECUTE);
                    inDebt -= mb;
                }
                outCarry += water + steam;
                int put = (int) Math.floor(outCarry);
                if (put > 0) {
                    outCarry -= out.fill(new FluidStack(WaterTreatmentBlockEntity.fluid(WaterGrade.DEAERATED), put),
                            IFluidHandler.FluidAction.EXECUTE);
                    outCarry = Math.min(outCarry, 1);
                }
                runTicks = RUN_HOLD;
            }
            double room = out.getCapacity() - out.getFluidAmount() - outCarry;
            double waterKgS = Math.min(MAX_WATER_KG_S, Math.min(in.getFluidAmount() - inDebt, room) / TICK_S);
            b.drawDeaeratorSteam(waterKgS > 0 ? waterKgS / ratio : 0);
        }
        steamKgH = 0.9 * steamKgH + 0.1 * steam / TICK_S * 3600;
        setStateFlag(PcProperties.RUNNING, runTicks > 0);
    }

    @Override
    protected void clientTick() {
        if (stateFlag(PcProperties.RUNNING) && level.random.nextInt(6) == 0) {
            var fx = fx();
            var p = fx.at(8, 33, 8);
            level.addParticle(ParticleTypes.CLOUD, p.x, p.y, p.z, 0, 0.08, 0);
        }
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return ports;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return FluidUtil.getFluidHandler(stack).isPresent();
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return FluidUtil.interactWithFluidHandler(player, hand, ports) ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(Player player) {
        WaterTubeBoilerBlockEntity b = boiler();
        var state = b == null ? PowerhouseText.msg("deaerator.no_boiler")
                : !(b.drumPressurePa() > WaterTubeUnit.DEAERATOR_MIN_PA) ? PowerhouseText.msg("deaerator.cold")
                : PowerhouseText.msg(runTicks > 0 ? "deaerator.working" : "deaerator.idle");
        player.displayClientMessage(PowerhouseText.msg("deaerator", state, PowerhouseText.num(steamKgH, 0),
                in.getFluidAmount(), out.getFluidAmount()), true);
        return InteractionResult.SUCCESS;
    }

    /** Вход — умягчённая вода (только налив), выход — питательная (только слив). */
    private final class Ports implements IFluidHandler {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int t) {
            return t == 0 ? in.getFluid() : out.getFluid();
        }

        @Override
        public int getTankCapacity(int t) {
            return TANK_MB;
        }

        @Override
        public boolean isFluidValid(int t, FluidStack stack) {
            return t == 0 && in.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return in.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return out.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return out.drain(maxDrain, action);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("in", in.writeToNBT(registries, new CompoundTag()));
        tag.put("out", out.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("in_debt", inDebt);
        tag.putDouble("out_carry", outCarry);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("in")) in.readFromNBT(registries, tag.getCompound("in"));
        if (tag.contains("out")) out.readFromNBT(registries, tag.getCompound("out"));
        inDebt = tag.getDouble("in_debt");
        outCarry = tag.getDouble("out_carry");
    }
}
