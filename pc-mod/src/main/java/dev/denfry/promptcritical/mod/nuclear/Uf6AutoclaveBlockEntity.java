package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.grid.MachinePower;
import dev.denfry.promptcritical.mod.radiation.ShieldedContents;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Автоклав баллона UF₆ ({@code uf6_autoclave_real}, fuelcycle-dimensions §2c): баллон закатывают через дверь, паром
 * прогревают выше тройной точки UF₆ (64 °C, 152 кПа) — твёрдый гексафторид плавится и даёт газ под давлением. Прогретый
 * баллон ({@code lit}) питает каскад: пульт каскада в радиусе {@value CascadeBlockEntity#RADIUS} блоков берёт UF₆ из него,
 * если слот питания на щите пуст. Тепло — пар заводской котельной: в модели это {@value #HEAT_W} Вт от щита питания.
 * Нагрев — теплоёмкость UF₆ и стальной обечайки баллона, потери — через изоляцию автоклава.
 *
 * <p>ПКМ баллоном — закатить (дверь закрывается, {@code occupied}); Shift + ПКМ пустой рукой — выкатить; ПКМ — показания.
 */
public final class Uf6AutoclaveBlockEntity extends DeviceBlockEntity implements FoundryDevice, RealScaleBlockEntity, ShieldedContents {
    /** Тепловая мощность пара на баллон, Вт: полный 30B прогревается около часа [BALANCE: порядок паровых автоклавов питания]. */
    public static final double HEAT_W = 20e3;
    /** Тройная точка UF₆, К; насыщенный пар 0,2 МПа (120 °C) — предел нагрева. */
    public static final double TRIPLE_K = 337.2, STEAM_K = 393.15, AMBIENT_K = 293.15;
    /** Удельные теплоёмкости, Дж/(кг·К): UF₆ (тв.) 0,48 кДж, сталь 0,49 кДж; потери через изоляцию, Вт/К. */
    static final double CP_UF6 = 480, CP_STEEL = 490, LOSS_W_PER_K = 150;

    private final ItemStackHandler slot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int s, ItemStack stack) {
            return stack.getItem() instanceof Uf6CylinderItem;
        }

        @Override
        public int getSlotLimit(int s) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };
    private double tempK = AMBIENT_K;

    public Uf6AutoclaveBlockEntity(BlockPos pos, BlockState state) {
        super(NuclearBlockEntities.UF6_AUTOCLAVE.get(), pos, state);
    }

    public ItemStackHandler slot() {
        return slot;
    }

    public double tempK() {
        return tempK;
    }

    private boolean flag(BooleanProperty p) {
        BlockState s = getBlockState();
        return s.hasProperty(p) && s.getValue(p);
    }

    /** Баллон внутри (дверь закрыта). */
    public boolean loaded() {
        return flag(BlockStateProperties.OCCUPIED);
    }

    /** Баллон прогрет выше тройной точки: UF₆ идёт в каскад. */
    public boolean ready() {
        return flag(BlockStateProperties.LIT);
    }

    /** Баллон питания для каскада: прогретый и не пустой, иначе пустой стек. */
    public ItemStack feed() {
        ItemStack s = slot.getStackInSlot(0);
        return ready() && !Uf6CylinderItem.charge(s).empty() ? s : ItemStack.EMPTY;
    }

    /** Теплоёмкость баллона с содержимым, Дж/К: UF₆ плюс стальная обечайка (порожняя масса типа). */
    public static double heatCapacity(ItemStack s) {
        if (!(s.getItem() instanceof Uf6CylinderItem c)) return 0;
        double tare = switch (c.type()) {
            case Y48 -> 2_360;
            case B30 -> 635;
            case A5 -> 25;
        };
        return Uf6CylinderItem.charge(s).uf6Kg() * CP_UF6 + tare * CP_STEEL;
    }

    /** Температура баллона после {@code dt} с: нагрев паром ({@code heatW}) против потерь, не выше пара. */
    public static double step(double tempK, double capJK, double heatW, double dt) {
        if (capJK <= 0) return AMBIENT_K;
        double q = heatW * Math.max(0, Math.min(1, (STEAM_K - tempK) / 10)) - LOSS_W_PER_K * (tempK - AMBIENT_K);
        return Math.max(AMBIENT_K, Math.min(STEAM_K, tempK + q * dt / capJK));
    }

    @Override
    public void tickDevice() {
        if (level == null) return;
        if (level.isClientSide) {
            clientTick();
            return;
        }
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        ItemStack cyl = slot.getStackInSlot(0);
        double dt = 0.05 * b.world().clock().kSlow();
        double heat = cyl.isEmpty() ? 0 : HEAT_W * MachinePower.supply(s, worldPosition, HEAT_W);
        tempK = cyl.isEmpty() ? AMBIENT_K : step(tempK, heatCapacity(cyl), heat, dt);
        if (level.getGameTime() % 10 == 0) {
            setFlag(BlockStateProperties.OCCUPIED, !cyl.isEmpty());
            setFlag(BlockStateProperties.LIT, !cyl.isEmpty() && tempK >= TRIPLE_K);
        }
        if (level.getGameTime() % 20 == 0) setChanged();
    }

    private void setFlag(BooleanProperty p, boolean on) {
        BlockState st = getBlockState();
        if (st.hasProperty(p) && st.getValue(p) != on) level.setBlock(worldPosition, st.setValue(p, on), Block.UPDATE_CLIENTS);
    }

    /** Прогретый автоклав: пар сочится у патрубка на верху, изредка шипит. */
    private void clientTick() {
        if (!ready()) return;
        MachineFx fx = MachineFx.of(this);
        if (fx == null) return;
        if (fx.chance(1.5)) fx.wisp(fx.at(8, 32, 8), 0.1);
        if (fx.chance(0.1)) fx.sound(fx.at(8, 32, 8), SoundEvents.FIRE_EXTINGUISH, 0.2f, 1.6f);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return slot.isItemValid(0, stack) && slot.getStackInSlot(0).isEmpty();
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!slot.isItemValid(0, stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!slot.getStackInSlot(0).isEmpty()) {
            player.displayClientMessage(NuclearText.msg("autoclave.busy"), true);
            return ItemInteractionResult.FAIL;
        }
        slot.setStackInSlot(0, stack.copyWithCount(1));
        if (!player.isCreative()) stack.shrink(1);
        tempK = AMBIENT_K;
        level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.6f);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack cyl = slot.getStackInSlot(0);
        if (player.isShiftKeyDown() && !cyl.isEmpty()) {
            ItemStack out = slot.extractItem(0, 1, false);
            if (!player.addItem(out)) player.drop(out, false);
            tempK = AMBIENT_K;
            level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1f, 0.6f);
            return InteractionResult.SUCCESS;
        }
        if (cyl.isEmpty()) {
            player.displayClientMessage(NuclearText.msg("autoclave.empty"), true);
            return InteractionResult.SUCCESS;
        }
        Uf6Charge c = Uf6CylinderItem.charge(cyl);
        player.displayClientMessage(NuclearText.msg(ready() ? "autoclave.ready" : "autoclave.heating", NuclearText.num(tempK - 273.15, 0),
                NuclearText.num(c.uf6Kg(), 0), NuclearText.num(100 * c.u235(), 3)).withStyle(ready() ? ChatFormatting.GREEN : ChatFormatting.GOLD), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return slot;
    }

    @Override
    public ItemStackHandler contents() {
        return slot;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("cylinder", slot.serializeNBT(registries));
        tag.putDouble("temp", tempK);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("cylinder")) slot.deserializeNBT(registries, tag.getCompound("cylinder"));
        if (tag.contains("temp")) tempK = tag.getDouble("temp");
    }
}
