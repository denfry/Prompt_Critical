package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity;
import dev.denfry.promptcritical.mod.environment.DosimeterItem;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.environment.SelfEmittingSource;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.grid.MachinePower;
import dev.denfry.promptcritical.sim.radiation.DoseRate;
import dev.denfry.promptcritical.sim.radiation.HotCell;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Рабочее место горячей камеры реального масштаба ({@code hot_cell_real}, 04b §4b.4.4): стена из магнетитового
 * бетона 1 м с окном из свинцового стекла, два копирующих манипулятора класса CRL Model 8 и шлюз для контейнеров.
 * Облучённые предметы ({@code ActivityData}) подают через шлюз (ПКМ предметом) и забирают обратно (Shift, пустая рука);
 * в камере они лежат на столе за стеной, и в мир уходит только то, что прошло стену ({@link HotCell#outside}). ПКМ
 * пустой рукой — показания: активность, мощность дозы на столе и у окна оператора; манипуляторы при этом работают.
 * Лампы и вытяжка камеры — {@value #POWER_W} Вт; без питания камера тёмная, но защищает так же.
 */
public final class HotCellBlockEntity extends DeviceBlockEntity implements SelfEmittingSource, FoundryDevice, RealScaleBlockEntity, ShieldedContents {
    public static final int SLOTS = 9;
    /** Лампы камеры, вытяжной вентилятор и приводы манипуляторов, Вт. */
    public static final double POWER_W = 5_000;
    /** Стол камеры в пространстве модели, px от угла контроллера (фасад — сторона оператора на севере). */
    static final double TABLE_PX = 8, TABLE_PY = 16, TABLE_PZ = 32;
    /** Сколько тиков после команды манипуляторы ещё в движении. */
    public static final int WORK_TICKS = 60;

    private final ItemStackHandler slots = ShieldedLoad.slots(SLOTS, this::setChanged);
    private ShieldedLoad load = ShieldedLoad.EMPTY;
    /** Клиент: тик, когда манипуляторы начали движение (событие блока). */
    private long workStart = Long.MIN_VALUE / 2;

    public HotCellBlockEntity(BlockPos pos, BlockState state) {
        super(RadiationBlockEntities.HOT_CELL.get(), pos, state);
    }

    public ItemStackHandler slots() {
        return slots;
    }

    /** Стол камеры в мире: там лежит источник. */
    public Vec3 table() {
        return BoilerGeometry.world(worldPosition, wholeModelFacing(), TABLE_PX, TABLE_PY, TABLE_PZ);
    }

    /** Мощность дозы у окна оператора от содержимого камеры на последнем замере, Зв/ч. */
    public double operatorSvH() {
        return HotCell.operatorSvH(load.source());
    }

    @Override
    public void tickDevice() {
        if (level == null) return;
        if (level.isClientSide) {
            clientTick();
            return;
        }
        if (!(level instanceof ServerLevel s) || level.getGameTime() % 20 != 0) return;
        load = ShieldedLoad.of(slots, s.getGameTime());
        if (!load.source().empty()) RadiationService.source(s, table(), HotCell.outside(load.source()));
        boolean lit = MachinePower.powered(s, worldPosition, POWER_W);
        BlockState bs = getBlockState();
        if (bs.hasProperty(BlockStateProperties.LIT) && bs.getValue(BlockStateProperties.LIT) != lit)
            level.setBlock(worldPosition, bs.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
    }

    private void clientTick() {
        MachineFx fx = MachineFx.of(this);
        if (fx == null) return;
        // вытяжка камеры гудит, пока горят лампы; манипуляторы жужжат лентами в работе
        if (lit() && fx.chance(0.2)) fx.sound(fx.at(-24, 64, 40), SoundEvents.BEACON_AMBIENT, 0.25f, 0.6f);
        if (workPhase(0) >= 0 && fx.chance(4)) fx.sound(fx.at(8, 40, -6), SoundEvents.SPIDER_STEP, 0.25f, 1.8f);
    }

    boolean lit() {
        BlockState bs = getBlockState();
        return bs.hasProperty(BlockStateProperties.LIT) && bs.getValue(BlockStateProperties.LIT);
    }

    /** Манипуляторы в работе (клиент): доля цикла 0..1, иначе −1. */
    public float workPhase(float partialTicks) {
        if (level == null) return -1;
        double t = level.getGameTime() + partialTicks - workStart;
        return t < 0 || t > WORK_TICKS ? -1 : (float) (t / WORK_TICKS);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == 1) {
            if (level != null) workStart = level.getGameTime();
            return true;
        }
        return super.triggerEvent(id, param);
    }

    private void work() {
        level.blockEvent(worldPosition, getBlockState().getBlock(), 1, 0);
        level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.4f, 0.6f);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return slots.isItemValid(0, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        int moved = ShieldedLoad.insert(slots, stack);
        if (moved == 0) {
            player.displayClientMessage(RadProtText.msg("cell.full"), true);
            return ItemInteractionResult.FAIL;
        }
        if (!player.isCreative()) stack.shrink(moved);
        // шибер шлюза: тележка шлюза уходит в камеру
        level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.5f);
        work();
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            ItemStack out = ShieldedLoad.takeLast(slots);
            if (out.isEmpty()) return InteractionResult.PASS;
            if (!player.addItem(out)) player.drop(out, false);
            level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8f, 0.5f);
            work();
            return InteractionResult.SUCCESS;
        }
        if (!MachinePower.powered(level, worldPosition, POWER_W))
            player.displayClientMessage(RadProtText.msg("no_power").withStyle(ChatFormatting.GOLD), true);
        ShieldedLoad l = ShieldedLoad.of(slots, level.getGameTime());
        if (l.items() == 0) {
            player.displayClientMessage(RadProtText.msg("cell.empty"), false);
            return InteractionResult.SUCCESS;
        }
        double table = DoseRate.totalSvH(l.source(), 1, List.of());
        player.displayClientMessage(RadProtText.msg("cell.status", l.items(), RadProtText.num(l.bq() / 1e9), DosimeterItem.rate(table),
                DosimeterItem.rate(HotCell.operatorSvH(l.source()))), false);
        work();
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return slots;
    }

    @Override
    public ItemStackHandler contents() {
        return slots;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("slots", slots.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("slots")) slots.deserializeNBT(registries, tag.getCompound("slots"));
    }
}
