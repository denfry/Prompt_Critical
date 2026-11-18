package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.powerhouse.WaterTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.plant.BoilerUnit;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import dev.denfry.promptcritical.sim.plant.WaterTubeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
 * Парогенератор АЭС: теплообменник «вода первого контура — вода котла». Стоит вплотную к котлу второго контура
 * (жаротрубному или барабану водотрубного) и отдаёт тепло реактора в его воду, как электродный котёл
 * ({@link SteamPlant#setElectricHeatW}); пар из котла идёт в существующие турбины. Реактор находит парогенератор в
 * {@value PowerReactorBlockEntity#LINK_RADIUS} блоках. Нет котла или котёл на предохранительном клапане — тепло некуда
 * отдать, первый контур греется.
 */
public final class SteamGeneratorBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    private SimId boiler = SimId.NONE;
    private double heatW;
    private boolean sink;
    private int ticks;

    public SteamGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.STEAM_GENERATOR.get(), pos, state);
    }

    @Nullable
    private SteamPlant plant() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        return b == null ? null : b.world().subsystem(SteamPlant.class);
    }

    /** Температура воды котла второго контура, К; {@code NaN} — котла нет или он не принимает тепло. */
    double secondaryK() {
        SteamPlant p = plant();
        if (p == null || boiler.isNone() || !sink && heatW > 0) return Double.NaN;
        BoilerUnit b = p.boiler(boiler);
        if (b != null) return b.waterTempK();
        WaterTubeUnit w = p.waterTube(boiler);
        return w != null ? w.steamTempK() : Double.NaN;
    }

    /** Тепло реактора в котёл, Вт. */
    void deliver(double watts) {
        heatW = watts;
    }

    public double heatW() {
        return heatW;
    }

    public boolean linked() {
        return !boiler.isNone();
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        if (ticks++ % 20 == 0) boiler = findBoiler();
        SteamPlant p = plant();
        sink = p != null && !boiler.isNone() && p.setElectricHeatW(boiler, heatW);
        if (heatW > 1e6 && ticks % 10 == 0)
            sl.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5,
                    1, 0.15, 0.05, 0.15, 0.02);
        if (ticks % 10 == 0) {
            BlockState st = getBlockState();
            boolean lit = heatW > 1e5;
            if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT) != lit)
                level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
        }
        heatW = 0; // реактор передаёт тепло каждый тик заново
    }

    /** Котёл второго контура, которого парогенератор касается гранью. */
    private SimId findBoiler() {
        for (Direction d : Direction.values()) {
            BlockPos n = worldPosition.relative(d);
            FireTubeBoilerBlockEntity fire = FireTubeBoilerBlockEntity.at(level, n);
            if (fire != null && !fire.simId().isNone()) return fire.simId();
            BlockState s = level.getBlockState(n);
            if (s.is(PcBlocks.STEAM_DRUM.get()) || s.is(PcBlocks.BOILER_WALL.get()) || s.is(PcBlocks.WATER_TUBE_BOILER.get())) {
                WaterTubeBoilerBlockEntity wt = WaterTubeBoilerBlockEntity.of(level, n);
                if (wt != null && wt.shape() != null && !wt.simId().isNone()) return wt.simId();
            }
        }
        return SimId.NONE;
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
}
