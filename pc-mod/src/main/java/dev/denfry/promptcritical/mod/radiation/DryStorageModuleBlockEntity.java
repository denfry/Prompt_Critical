package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.environment.SelfEmittingSource;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.sim.radiation.DryStorage;
import dev.denfry.promptcritical.sim.radiation.SurfaceContamination;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Горизонтальный модуль сухого хранения класса NUHOMS ({@code dry_storage_module_real}, 02 §2.7.4): пенал на
 * {@value DryStorage#ASSEMBLIES} ТВС задвигают с тележки через дверь на фасаде ({@link CaskCartBlockEntity}). Тепло
 * уносит воздух: два входа внизу фасада, два выхода на крыше. Каналы считаются открытыми, если клетка за решёткой
 * свободна (не твёрдый блок и не жидкость); от доли открытых зависит температура оболочек ({@link DryStorage}).
 * Выше 400 °C — тревога; больше суток физического времени над пределом — оболочки рвутся, аэрозоль топлива выходит
 * через выходные каналы на землю вокруг. Наружу светит то, что прошло 0,9 м бетона.
 */
public final class DryStorageModuleBlockEntity extends DeviceBlockEntity implements SelfEmittingSource, FoundryDevice, RealScaleBlockEntity, ShieldedContents {
    public static final double AMBIENT_K = 293.15;
    /** Физ. секунд на тик игры (K_slow 72: 1 тик = 3,6 физ. с). */
    static final double PHYS_S_PER_TICK = 3.6;
    /** Сколько физ. секунд над пределом до разрушения оболочек [BALANCE: сутки]. */
    static final double FAILURE_AFTER_S = 86_400;
    static final double CLAD_RELEASE_FRACTION = FuelRackBlockEntity.CLAD_RELEASE_FRACTION;
    /** Решётки воздуха в пространстве модели: клетка и грань (02 §2.7.4, лист размеров NUHOMS). */
    static final int[][] VENTS = {{-1, 0, 0}, {1, 0, 0}, {-1, 4, 4}, {1, 4, 4}};
    static final Direction[] VENT_FACES = {Direction.NORTH, Direction.NORTH, Direction.UP, Direction.UP};

    private final ItemStackHandler slots = ShieldedLoad.slots(DryStorage.ASSEMBLIES, this::setChanged);
    private double cladK = AMBIENT_K, overS, heatW, ventOpen = 1;
    private boolean failed, warned;

    public DryStorageModuleBlockEntity(BlockPos pos, BlockState state) {
        super(RadiationBlockEntities.DRY_STORAGE_MODULE.get(), pos, state);
    }

    public ItemStackHandler slots() {
        return slots;
    }

    public double cladK() {
        return cladK;
    }

    public double ventOpen() {
        return ventOpen;
    }

    public boolean empty() {
        return ShieldedLoad.count(slots) == 0;
    }

    /** Середина пенала (8, 32, 48) px. */
    Vec3 canister() {
        return BoilerGeometry.world(worldPosition, wholeModelFacing(), 8, 32, 48);
    }

    /** Клетка модели (блоки от контроллера, фасад на север) в мире. */
    BlockPos cell(int x, int y, int z) {
        return BoilerGeometry.offset(worldPosition, wholeModelFacing(), x, y, z);
    }

    /** Грань модели в мире: север модели — фасад. */
    Direction world(Direction model) {
        Direction f = wholeModelFacing();
        return switch (model) {
            case NORTH -> f;
            case SOUTH -> f.getOpposite();
            case EAST -> f.getClockWise();
            case WEST -> f.getCounterClockWise();
            default -> model;
        };
    }

    /** Доля открытых вентканалов: клетка за решёткой не твёрдая и без жидкости. */
    double vents() {
        int open = 0;
        for (int i = 0; i < VENTS.length; i++) {
            BlockPos cell = cell(VENTS[i][0], VENTS[i][1], VENTS[i][2]);
            BlockPos out = cell.relative(world(VENT_FACES[i]));
            BlockState s = level.getBlockState(out);
            if (!s.blocksMotion() && s.getFluidState().isEmpty()) open++;
        }
        return (double) open / VENTS.length;
    }

    @Override
    public void tickDevice() {
        if (level == null) return;
        if (level.isClientSide) {
            clientTick();
            return;
        }
        if (!(level instanceof ServerLevel s) || level.getGameTime() % 20 != 0) return;
        ShieldedLoad l = ShieldedLoad.of(slots, s.getGameTime());
        heatW = l.heatW();
        ventOpen = vents();
        double dt = 20 * PHYS_S_PER_TICK;
        cladK = DryStorage.step(cladK, AMBIENT_K, heatW, ventOpen, dt);
        if (!l.source().empty()) RadiationService.source(s, canister(), DryStorage.outside(l.source()));
        boolean hot = DryStorage.overLimit(cladK);
        if (hot) {
            overS += dt;
            if (!warned) {
                warned = true;
                for (ServerPlayer p : s.getPlayers(p -> p.distanceToSqr(canister()) < 64 * 64))
                    p.displayClientMessage(RadProtText.msg("module.hot", RadProtText.num(cladK - 273.15)).withStyle(ChatFormatting.RED), false);
            }
            if (!failed && overS > FAILURE_AFTER_S) {
                failed = true;
                double beta = l.bq() * CLAD_RELEASE_FRACTION;
                BlockPos outlet = cell(0, 4, 4).above();
                ContaminationService.spill(s, outlet, FuelRackBlockEntity.CLAD_SPILL_RADIUS, beta * SurfaceContamination.FUEL_ALPHA_PER_BETA, beta);
            }
        } else {
            warned = false;
            overS = Math.max(0, overS - dt);
        }
        if (empty()) failed = false;
        boolean warm = heatW > SpentFuelCaskBlockEntity.VISIBLE_HEAT_W && ventOpen > 0;
        BlockState bs = getBlockState();
        if (bs.hasProperty(BlockStateProperties.LIT) && bs.getValue(BlockStateProperties.LIT) != warm)
            level.setBlock(worldPosition, bs.setValue(BlockStateProperties.LIT, warm), Block.UPDATE_CLIENTS);
        if (s.getGameTime() % 200 == 0) setChanged();
    }

    /** Тёплый воздух над выходными колпаками (-10 и 26 px по x, z 76, крыша 80 px). */
    private void clientTick() {
        BlockState bs = getBlockState();
        if (!bs.hasProperty(BlockStateProperties.LIT) || !bs.getValue(BlockStateProperties.LIT)) return;
        MachineFx fx = MachineFx.of(this);
        if (fx == null || !fx.chance(2)) return;
        fx.wisp(fx.at(fx.random().nextBoolean() ? -10 : 26, 82, 76), 0.3);
    }

    /** Принять пенал с тележки: сколько ТВС влезло. */
    int receive(ItemStackHandler from) {
        int n = 0;
        for (int i = 0; i < from.getSlots(); i++) {
            ItemStack st = from.getStackInSlot(i);
            if (st.isEmpty()) continue;
            int moved = ShieldedLoad.insert(slots, st);
            if (moved > 0) {
                from.extractItem(i, moved, false);
                n += moved;
            }
        }
        return n;
    }

    /** Отдать пенал на тележку (сколько влезет в её корзину). */
    int release(ItemStackHandler to) {
        int n = 0;
        for (int i = 0; i < slots.getSlots(); i++) {
            ItemStack st = slots.getStackInSlot(i);
            if (st.isEmpty()) continue;
            int moved = ShieldedLoad.insert(to, st);
            if (moved > 0) {
                slots.extractItem(i, moved, false);
                n += moved;
            }
        }
        if (n > 0 && level instanceof ServerLevel s && empty()) RadiationService.removeSource(s, canister());
        return n;
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
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (empty()) {
            player.displayClientMessage(RadProtText.msg("module.empty"), false);
            return InteractionResult.SUCCESS;
        }
        player.displayClientMessage(RadProtText.msg("module.status", ShieldedLoad.count(slots), RadProtText.num(heatW),
                RadProtText.num(cladK - 273.15), Math.round(ventOpen * 100)).withStyle(DryStorage.overLimit(cladK) ? ChatFormatting.RED
                        : ChatFormatting.WHITE), false);
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public ItemStackHandler contents() {
        return slots;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("slots", slots.serializeNBT(registries));
        tag.putDouble("clad", cladK);
        tag.putDouble("over", overS);
        tag.putBoolean("failed", failed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("slots")) slots.deserializeNBT(registries, tag.getCompound("slots"));
        if (tag.contains("clad")) cladK = tag.getDouble("clad");
        overS = tag.getDouble("over");
        failed = tag.getBoolean("failed");
    }
}
