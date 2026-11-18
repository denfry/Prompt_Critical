package dev.denfry.promptcritical.mod.uranium;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.item.ExtractOnlyItemHandler;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import java.util.function.Predicate;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Общее у установок передела урана: склад загрузки (воронкой или рукой, кроме нижней грани) и склад продукта
 * (воронкой снизу), пуск/останов Shift + ПКМ, шаг процесса с dt мира, флаг работы на блоке. Жидкости, щит и
 * собственно процесс — у наследника.
 */
public abstract class UraniumProcessBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    protected final ItemStackHandler input, output;
    private final IItemHandler outputView;
    private final IItemHandlerModifiable panelItems;
    protected boolean on = true;

    protected UraniumProcessBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int inSlots, int outSlots,
            Predicate<ItemStack> accepts) {
        super(type, pos, state);
        input = new ItemStackHandler(inSlots) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return accepts.test(stack);
            }

            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
        output = new ItemStackHandler(outSlots) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
        outputView = new ExtractOnlyItemHandler(output);
        panelItems = new CombinedInvWrapper(input, output);
    }

    /** Шаг процесса: {@code dtS} — секунды процесса (тик × kSlow). */
    protected abstract void process(ServerLevel level, double dtS);

    /** Установка работает (для флага на блоке и щита). */
    protected abstract boolean running();

    protected abstract void saveExtra(CompoundTag tag, HolderLookup.Provider registries);

    protected abstract void loadExtra(CompoundTag tag, HolderLookup.Provider registries);

    public boolean on() {
        return on;
    }

    public ItemStackHandler input() {
        return input;
    }

    public ItemStackHandler output() {
        return output;
    }

    @Override
    public final void tickDevice() {
        if (level != null && level.isClientSide) {
            clientTick();
            return;
        }
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        process(s, 0.05 * b.world().clock().kSlow());
        if (level.getGameTime() % 10 == 0) {
            setFlag(PcProperties.RUNNING, running());
            setFlag(BlockStateProperties.LIT, running());
        }
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    /** Клиентский тик: частицы работы по состоянию блока ({@code running}). */
    protected void clientTick() {
    }

    /** Эффекты в модельных координатах (только клиент; на сервере {@code null}). */
    @org.jetbrains.annotations.Nullable
    protected dev.denfry.promptcritical.mod.fx.MachineFx fx() {
        BlockState st = getBlockState();
        return dev.denfry.promptcritical.mod.fx.MachineFx.of(level, worldPosition,
                st.getBlock() instanceof dev.denfry.promptcritical.mod.block.PcBlock b ? b.facing(st) : Direction.NORTH);
    }

    /** Работает (свойство {@code running} блока, видно и клиенту). */
    protected boolean runningState() {
        BlockState st = getBlockState();
        return st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING);
    }

    private void setFlag(BooleanProperty prop, boolean v) {
        BlockState st = getBlockState();
        if (st.hasProperty(prop) && st.getValue(prop) != v) level.setBlock(worldPosition, st.setValue(prop, v), Block.UPDATE_CLIENTS);
    }

    /** Взять один предмет загрузки, подходящий под условие; пустой стек — нечего брать. */
    protected ItemStack takeOne(Predicate<ItemStack> which) {
        for (int i = 0; i < input.getSlots(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (!s.isEmpty() && which.test(s)) return input.extractItem(i, 1, false);
        }
        return ItemStack.EMPTY;
    }

    /** Количество предметов загрузки под условие. */
    protected int count(Predicate<ItemStack> which) {
        int n = 0;
        for (int i = 0; i < input.getSlots(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (which.test(s)) n += s.getCount();
        }
        return n;
    }

    /** Выдать предмет в склад продукта; false — склад полон. */
    protected boolean emit(Item item) {
        return emit(new ItemStack(item));
    }

    protected boolean emit(ItemStack stack) {
        ItemStack left = stack;
        for (int i = 0; i < output.getSlots() && !left.isEmpty(); i++) left = output.insertItem(i, left, false);
        return left.isEmpty();
    }

    /** Есть ли место под ещё один предмет. */
    protected boolean hasRoom(Item item) {
        ItemStack left = new ItemStack(item);
        for (int i = 0; i < output.getSlots() && !left.isEmpty(); i++) left = output.insertItem(i, left, true);
        return left.isEmpty();
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return side == Direction.DOWN ? outputView : input;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return panelItems;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return input.isItemValid(0, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack left = stack.copy();
        for (int i = 0; i < input.getSlots() && !left.isEmpty(); i++) left = input.insertItem(i, left, false);
        int moved = stack.getCount() - left.getCount();
        if (moved == 0) return ItemInteractionResult.FAIL;
        if (!player.isCreative()) stack.shrink(moved);
        return ItemInteractionResult.SUCCESS;
    }

    /** Shift + ПКМ: пуск или останов. */
    @Override
    public InteractionResult use(Player player) {
        on = !on;
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        player.displayClientMessage(UraniumText.msg(on ? "on" : "off", getBlockState().getBlock().getName()), true);
        setChanged();
        return InteractionResult.SUCCESS;
    }

    @Override
    protected final void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("in", input.serializeNBT(registries));
        tag.put("out", output.serializeNBT(registries));
        tag.putBoolean("on", on);
        saveExtra(tag, registries);
    }

    @Override
    protected final void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("in")) input.deserializeNBT(registries, tag.getCompound("in"));
        if (tag.contains("out")) output.deserializeNBT(registries, tag.getCompound("out"));
        on = !tag.contains("on") || tag.getBoolean("on");
        loadExtra(tag, registries);
    }
}
