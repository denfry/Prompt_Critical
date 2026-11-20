package dev.denfry.promptcritical.mod.powerhouse;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.environment.RiverService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.environment.RiverCatchment;
import dev.denfry.promptcritical.sim.hydraulics.HydroTurbine;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Гидротурбина ({@link HydroTurbine}) — источник вращения Create, вал вверх. Вода — от затвора водозабора
 * ({@link DamGateBlock}) в воде выше турбины в пределах {@link #SEARCH} блоков: напор — разница высот поверхности
 * воды у затвора и турбины, длина водовода — расстояние до затвора, диаметр {@link #PENSTOCK_D_M} м. Расчётный
 * расход — меньшее из пропускной способности водовода (4 м/с) и среднего расхода реки; текущий расход реки
 * ограничивает открытие аппарата. Агрегат собирается заново, когда меняется место (раз в 5 с).
 */
public class HydroTurbineBlockEntity extends GeneratingKineticBlockEntity implements FoundryDevice {
    public static final int[] GEARS = {64, 128, 256};
    static final int SEARCH = 48, RESCAN = 100;
    static final double PENSTOCK_D_M = 1.4, PENSTOCK_V_MS = 4, MIN_HEAD_M = 3, UNIT_RPM = 500;
    private static final double DT = 0.05, HYSTERESIS = 0.02;

    @Nullable
    private HydroTurbine turbine;
    @Nullable
    private BlockPos gate;
    private int gear = 1, level4, ticks;
    private float capacityPerRpm, lastRpm;
    private double portLoadW;
    private CompoundTag savedState;

    public HydroTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(PowerhouseBlockEntities.HYDRO_TURBINE.get(), pos, state);
    }

    @Nullable
    public HydroTurbine turbine() {
        return turbine;
    }

    @Nullable
    public BlockPos gate() {
        return gate;
    }

    public float outputRpm() {
        return GEARS[gear] * level4 / 4f;
    }

    @Override
    public float getGeneratedSpeed() {
        return level != null && level.isClientSide ? lastRpm : outputRpm();
    }

    @Override
    public float calculateAddedStressCapacity() {
        lastCapacityProvided = capacityPerRpm;
        return capacityPerRpm;
    }

    @Override
    public void tickDevice() {
        tick();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel sl)) return;
        ticks++;
        if (ticks % RESCAN == 1) rescan(sl);
        HydroTurbine t = turbine;
        if (t == null) {
            level4 = 0;
            updateRotation(0);
            return;
        }
        BlockState gs = gate != null && sl.isLoaded(gate) ? sl.getBlockState(gate) : null;
        boolean isGate = gs != null && gs.is(PcBlocks.DAM_GATE.get());
        if (gate != null && gs != null && !isGate) ticks = 0;   // затвор сломан или залит: пересканировать водозабор на следующем тике
        boolean open = isGate && gs.getValue(BlockStateProperties.OPEN);
        if (ticks % 20 == 0) t.setAvailableFlow(RiverService.dischargeM3s(sl, gate));
        t.step(DT, portLoadW / Math.max(t.omega(), 0.2 * t.spec().nominalOmega()), open);

        double r = t.omega() / t.spec().nominalOmega();
        int next = level4(r);
        if (next > level4) next = Math.max(level4, level4(r / (1 + HYSTERESIS)));
        else if (next < level4) next = Math.min(level4, level4(r * (1 + HYSTERESIS)));
        level4 = next;
        updateRotation(open ? t.capacityW() : 0);
        if (ticks % 5 == 0) {
            BlockState s = getBlockState();
            boolean running = level4 > 0;
            if (s.getValue(PcProperties.RUNNING) != running) level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, running), Block.UPDATE_CLIENTS);
        }
        if (ticks % 100 == 0) setChanged();
    }

    private void updateRotation(double capacityW) {
        float rpm = outputRpm();
        double wPerSu = ServerConfig.W_PER_SU.get();
        float cap = (float) (capacityW / wPerSu / (rpm > 0 ? rpm : GEARS[gear]));
        if (rpm != lastRpm) {
            lastRpm = rpm;
            capacityPerRpm = cap;
            updateGeneratedRotation();
        } else if (Math.abs(cap - capacityPerRpm) > 0.02 * Math.max(capacityPerRpm, 0.01f) && ticks % 20 == 0) {
            capacityPerRpm = cap;
            if (hasNetwork() && getOrCreateNetwork() != null) notifyStressCapacityChange(calculateAddedStressCapacity());
        }
        double load = 0;
        float speed = Math.abs(getSpeed());
        if (speed > 0 && !isOverStressed() && capacity > 0) {
            double own = lastCapacityProvided * speed;
            load = wPerSu * Math.min(stress, capacity) * own / capacity;
        }
        portLoadW = load;
    }

    private static int level4(double r) {
        if (r >= 0.95) return 4;
        if (r >= 0.2) return Math.max(1, Math.min(3, (int) Math.floor(4 * r)));
        return 0;
    }

    /** Найти затвор в воде выше турбины; при новом месте — новый паспорт агрегата (состояние вала сохраняется). */
    private void rescan(ServerLevel sl) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : RiverService.gatesNear(sl, worldPosition, SEARCH)) {
            if (!sl.isLoaded(p) || !sl.getBlockState(p).is(PcBlocks.DAM_GATE.get())) continue;
            double d = p.distSqr(worldPosition);
            if (d < bestD && waterSurfaceY(sl, p) - worldPosition.getY() >= MIN_HEAD_M) {
                bestD = d;
                best = p.immutable();
            }
        }
        if (best == null) {
            gate = null;
            turbine = null;
            return;
        }
        double head = waterSurfaceY(sl, best) - worldPosition.getY();
        double length = Math.sqrt(bestD);
        double area = Math.PI * PENSTOCK_D_M * PENSTOCK_D_M / 4;
        double meanRiver = RiverCatchment.dischargeM3s(RiverService.catchmentAreaKm2(sl, best), RiverCatchment.MEAN_RUNOFF_MM_DAY);
        double flow = Math.max(0.01, Math.min(PENSTOCK_V_MS * area, meanRiver));
        HydroTurbine.Spec spec = new HydroTurbine.Spec(head, flow, Math.max(length, head), PENSTOCK_D_M, 0.9,
                2 * Math.PI * UNIT_RPM / 60, 3, 0.05);
        if (turbine != null && best.equals(gate) && turbine.spec().equals(spec)) return;
        HydroTurbine fresh = new HydroTurbine(spec);
        if (savedState != null) {
            MutableStateTree tree = dev.denfry.promptcritical.mod.binding.NbtStateTrees.fromNbt(savedState);
            fresh.load(new TreeStateReader(tree));
            savedState = null;
        } else if (turbine != null) {
            fresh.setOmega(turbine.omega());
        }
        gate = best;
        turbine = fresh;
    }

    /** Верх столба воды у затвора: сам затвор в воде, вода над ним — до поверхности. */
    private static double waterSurfaceY(ServerLevel sl, BlockPos gate) {
        BlockPos p = gate;
        boolean wet = sl.getFluidState(gate).is(FluidTags.WATER);
        for (Direction d : Direction.Plane.HORIZONTAL) wet |= sl.getFluidState(gate.relative(d)).is(FluidTags.WATER);
        if (!wet) return Double.NEGATIVE_INFINITY;
        while (sl.getFluidState(p.above()).is(FluidTags.WATER) && p.getY() - gate.getY() < 32) p = p.above();
        return p.getY() + 1;
    }

    // --- игрок ---

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
        if (stack.is(com.simibubi.create.AllItems.WRENCH.get())) {
            gear = (gear + 1) % GEARS.length;
            setChanged();
            player.displayClientMessage(PowerhouseText.msg("turbine.gear", GEARS[gear]), true);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Состояние агрегата: напор, расход, мощность; с Shift на порванном водоводе — замена. */
    @Override
    public InteractionResult use(Player player) {
        HydroTurbine t = turbine;
        if (t == null) {
            player.displayClientMessage(PowerhouseText.msg("hydro.no_gate").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        if (t.burst()) {
            if (player.isShiftKeyDown()) {
                t.repair();
                player.displayClientMessage(PowerhouseText.msg("hydro.repaired"), true);
            } else {
                player.displayClientMessage(PowerhouseText.msg("hydro.burst").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.SUCCESS;
        }
        HydroTurbine.Spec s = t.spec();
        player.displayClientMessage(PowerhouseText.msg("hydro.state", f(s.headM(), 1), f(t.flowM3s(), 2), f(s.flowM3s(), 2),
                f(t.powerW() / 1e3, 0), f(s.ratedPowerW() / 1e3, 0), f(60 * t.omega() / (2 * Math.PI), 0), f(s.waterStartingTimeS(), 2),
                PowerhouseText.msg("hydro.kind." + s.kind())), true);
        return InteractionResult.SUCCESS;
    }

    private static String f(double v, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", v);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) {
            tag.putInt("gear", gear);
            if (turbine != null) {
                MutableStateTree tree = new MutableStateTree();
                turbine.save(new TreeStateWriter(tree));
                tag.put("hydro", dev.denfry.promptcritical.mod.binding.NbtStateTrees.toNbt(tree));
            }
        }
        tag.putFloat("rpm", lastRpm);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket) {
            if (tag.contains("gear")) gear = Math.floorMod(tag.getInt("gear"), GEARS.length);
            if (tag.contains("hydro")) savedState = tag.getCompound("hydro");
        }
        lastRpm = tag.getFloat("rpm");
    }
}
