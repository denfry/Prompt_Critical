package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.thermal.WaterIntake;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Водозабор {@code water_intake} (07 §7.13.4), 1×2×1, ставится у воды: забирает её через решётку и отдаёт трубам как
 * природную воду ({@code raw_water}, 1 мБ = 1 л) — для водоподготовки 08 §8.12, тушения, охлаждения. Водоём узнаётся
 * заливкой воды от соседних блоков (до {@value WaterIntake#OCEAN_BLOCKS} + 1): биом океана или больше — океан, биом реки —
 * река (сечение — наибольшее число блоков воды в вертикальной плоскости вдоль x или z через водозабор, в
 * {@value #SECTION_RADIUS} блоках), иначе пруд по объёму. Решётка засоряется ({@link WaterIntake}), пока идёт вода;
 * температура воды — температура воздуха у водозабора [допущение]. Shift + ПКМ — очистить решётку, ПКМ — состояние.
 */
public final class WaterIntakeBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    public static final int TANK_MB = 4000, SECTION_RADIUS = 32, RESCAN_TICKS = 200;

    private final FluidTank tank = new FluidTank(TANK_MB, s -> false) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private WaterIntake state = new WaterIntake(0);
    private WaterIntake.Body body = WaterIntake.Body.LAKE;
    private double section, volume, capacityM3s, pending;
    private boolean wet;

    public WaterIntakeBlockEntity(BlockPos pos, BlockState state) {
        super(SafetyBlockEntities.WATER_INTAKE.get(), pos, state);
    }

    public WaterIntake intake() {
        return state;
    }

    public WaterIntake.Body body() {
        return body;
    }

    public double capacityM3s() {
        return capacityM3s;
    }

    public boolean wet() {
        return wet;
    }

    /** Для испытаний и сценариев: задать засор решётки. */
    public void setClog(double clog) {
        state = new WaterIntake(clog);
        setChanged();
    }

    /** Найти водоём у водозабора. */
    public void survey(ServerLevel level) {
        Set<Long> water = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            if (level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p).isSource() && water.add(p.asLong())) q.add(p);
        }
        wet = !q.isEmpty();
        while (!q.isEmpty() && water.size() <= WaterIntake.OCEAN_BLOCKS) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (level.isLoaded(n) && !water.contains(n.asLong()) && level.getFluidState(n).is(FluidTags.WATER)
                        && level.getFluidState(n).isSource()) {
                    water.add(n.asLong());
                    q.add(n);
                }
            }
        }
        var biome = level.getBiome(worldPosition);
        volume = water.size();
        if (biome.is(BiomeTags.IS_OCEAN) || water.size() > WaterIntake.OCEAN_BLOCKS) body = WaterIntake.Body.OCEAN;
        else if (biome.is(BiomeTags.IS_RIVER)) {
            body = WaterIntake.Body.RIVER;
            int sx = 0, sz = 0;
            for (long l : water) {
                BlockPos p = BlockPos.of(l);
                if (p.getX() == worldPosition.getX() && Math.abs(p.getZ() - worldPosition.getZ()) <= SECTION_RADIUS) sx++;
                if (p.getZ() == worldPosition.getZ() && Math.abs(p.getX() - worldPosition.getX()) <= SECTION_RADIUS) sz++;
            }
            section = Math.max(sx, sz);
        } else body = WaterIntake.Body.LAKE;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || (sl.getGameTime() + worldPosition.hashCode()) % 20 != 0) return;
        if ((sl.getGameTime() + worldPosition.hashCode()) % RESCAN_TICKS == 0 || volume == 0) survey(sl);
        SimLevelBinding b = SimBindings.get(sl);
        double kSlow = b == null ? 1 : b.world().clock().kSlow();
        capacityM3s = wet ? state.capacityM3s(body, section, volume) : 0;
        int room = TANK_MB - tank.getFluidAmount();
        boolean flowing = wet && room > 0;
        pending += Math.min(room, capacityM3s * 1000);
        int mb = (int) pending;
        if (mb > 0) {
            pending -= mb;
            tank.setFluid(new FluidStack(PcFluids.RAW_WATER.source.get(), tank.getFluidAmount() + mb));
        }
        double waterC = EnvironmentService.sample(sl, worldPosition, 2).airTempC();
        state.step(kSlow, Math.max(0, waterC), flowing);
        boolean fouled = state.clog() > 0.5;
        BlockState s = getBlockState();
        if (s.hasProperty(PcProperties.ALARM) && s.getValue(PcProperties.ALARM) != fouled)
            sl.setBlock(worldPosition, s.setValue(PcProperties.ALARM, fouled), Block.UPDATE_CLIENTS);
        if (sl.getGameTime() % 200 == 0) setChanged();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return tank;
    }

    @Override
    public InteractionResult use(Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isSecondaryUseActive()) {
            state.clean();
            level.playSound(null, worldPosition, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 0.8f, 0.8f);
            player.displayClientMessage(SafetyText.msg("intake_cleaned").withStyle(ChatFormatting.GREEN), true);
        } else {
            player.displayClientMessage(SafetyText.msg(wet ? "intake_status" : "intake_dry",
                    SafetyText.msg("intake_body." + body.name().toLowerCase(java.util.Locale.ROOT)),
                    Math.round(capacityM3s * 1000), Math.round(state.clog() * 100)), false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("clog", state.clog());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
        state = new WaterIntake(tag.getDouble("clog"));
    }
}
