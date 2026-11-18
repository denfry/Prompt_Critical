package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.fuel.MaterialBalance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Участок топливного цеха: склад слотов (щит и воронки), пуск/стоп, учёт ядерных материалов
 * ({@link MaterialBalance}: уран и U-235) и сохранение. Наследники задают слоты, шаг и щит.
 */
public abstract class FuelShopBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    protected final ItemStackHandler items;
    protected final MaterialBalance balance = new MaterialBalance();
    protected boolean running;

    protected FuelShopBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        this.items = new ItemStackHandler(slots) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return valid(slot, stack);
            }

            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
    }

    /** Что принимает слот (выходные — ничего). */
    protected abstract boolean valid(int slot, ItemStack stack);

    /** Шаг участка по игровому времени {@code dt}, с. */
    protected abstract void step(ServerLevel level, double dt);

    /** Ключ сообщений пуска и останова: {@code <key>.start}, {@code <key>.stop}. */
    protected abstract String messageKey();

    public ItemStackHandler items() {
        return items;
    }

    public MaterialBalance balance() {
        return balance;
    }

    public boolean running() {
        return running;
    }

    public void setRunning(boolean r) {
        running = r;
        setChanged();
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        if (running) step(s, 0.05 * b.world().clock().kSlow());
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    protected void setLit(boolean lit) {
        BlockState st = getBlockState();
        if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT) != lit)
            level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
    }

    /** Положить продукт в выходной слот; false — некуда (слот занят другой партией или полон). */
    protected boolean output(int slot, ItemStack stack) {
        ItemStack in = items.getStackInSlot(slot);
        if (in.isEmpty()) {
            items.setStackInSlot(slot, stack);
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(in, stack) || in.getCount() + stack.getCount() > in.getMaxStackSize()) return false;
        in.grow(stack.getCount());
        setChanged();
        return true;
    }

    /** Есть ли место под продукт в выходном слоте. */
    protected boolean room(int slot, ItemStack stack) {
        ItemStack in = items.getStackInSlot(slot);
        return in.isEmpty() || ItemStack.isSameItemSameComponents(in, stack) && in.getCount() + stack.getCount() <= in.getMaxStackSize();
    }

    // --- руки ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return items;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return items;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        for (int i = 0; i < items.getSlots(); i++) if (valid(i, stack)) return true;
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack left = stack.copy();
        for (int i = 0; i < items.getSlots() && !left.isEmpty(); i++) left = items.insertItem(i, left, false);
        int moved = stack.getCount() - left.getCount();
        if (moved == 0) return ItemInteractionResult.FAIL;
        if (!player.isCreative()) stack.shrink(moved);
        return ItemInteractionResult.SUCCESS;
    }

    /** Shift + ПКМ: пуск или останов. */
    @Override
    public InteractionResult use(Player player) {
        toggle(player);
        return InteractionResult.SUCCESS;
    }

    protected void toggle(Player player) {
        running = !running;
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, running ? 1.1f : 0.8f);
        player.displayClientMessage(NuclearText.msg(messageKey() + (running ? ".start" : ".stop")), true);
        if (!running) setLit(false);
        setChanged();
    }

    /** Общая кнопка щита пуска — 0. */
    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == 0) toggle(player);
        else button(id);
        setChanged();
    }

    protected void button(int id) {
    }

    /** Учёт U-235 в каналы щита, г: пришло, ушло, потери, в аппаратах. */
    protected void ledger(int[] v, int from) {
        v[from] = (int) Math.round(balance().u235().inKg() * 1000);
        v[from + 1] = (int) Math.round(balance().u235().outKg() * 1000);
        v[from + 2] = (int) Math.round(balance().u235().lossKg() * 1000);
        v[from + 3] = (int) Math.round(balance().holdupU235Kg() * 1000);
    }

    // --- сохранение ---

    protected void saveState(StateWriter out) {
    }

    protected void loadState(StateReader in) {
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putBoolean("running", running);
        MutableStateTree tree = new MutableStateTree();
        TreeStateWriter w = new TreeStateWriter(tree);
        balance.save(w);
        saveState(w);
        tag.put("state", NbtStateTrees.toNbt(tree));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
        running = tag.getBoolean("running");
        if (tag.contains("state")) {
            TreeStateReader r = new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("state")));
            balance.load(r);
            loadState(r);
        }
    }
}
