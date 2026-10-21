package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.grid.RelaySetting;
import dev.denfry.promptcritical.sim.grid.SwitchUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Шкаф РЗА ({@code relay_cabinet}, 1×2×1, 07 §7.5.2): шесть мест под модули {@code relay_*}; действует на ячейку КРУ
 * или щит, к которой примыкает. Ручки щита меняют уставки выбранного модуля; блинкер «сработала защита» держится до
 * сброса.
 */
public class RelayCabinetBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int SLOTS = 6;

    private final ItemStackHandler modules = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return RelayModuleItem.setting(stack) != null && !(stack.getItem() instanceof RelayModuleItem m && m.kind().consumerOnly());
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int selected, ticks, seenTrips = -1;
    private boolean tripped;

    public RelayCabinetBlockEntity(BlockPos pos, BlockState state) {
        super(GridBlockEntities.RELAY_CABINET.get(), pos, state);
    }

    /** Шкаф, клеткой которого является {@code pos}; {@code null} — не шкаф. */
    @Nullable
    public static RelayCabinetBlockEntity at(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        BlockPos c = s.getBlock() instanceof MultiblockPartBlock ? PcBlock.structureController(level, MultiblockPartBlock.anchor(s, pos))
                : s.getBlock() instanceof PcBlock pc ? pc.controllerPos(s, pos) : null;
        return c != null && level.getBlockEntity(c) instanceof RelayCabinetBlockEntity r ? r : null;
    }

    /** Уставки установленных модулей. */
    public List<RelaySetting> settings() {
        List<RelaySetting> out = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            RelaySetting s = RelayModuleItem.setting(modules.getStackInSlot(i));
            if (s != null) out.add(s);
        }
        return out;
    }

    /** Ячейка, на которую действует шкаф. */
    @Nullable
    private BreakerCellBlockEntity target() {
        for (int y = 0; y <= 1; y++) {
            for (Direction d : Direction.values()) {
                BlockPos p = worldPosition.above(y).relative(d);
                BlockState s = level.getBlockState(p);
                BlockPos c = s.getBlock() instanceof MultiblockPartBlock ? PcBlock.structureController(level, MultiblockPartBlock.anchor(s, p))
                        : s.getBlock() instanceof PcBlock pc ? pc.controllerPos(s, p) : null;
                if (c != null && level.getBlockEntity(c) instanceof BreakerCellBlockEntity cell) return cell;
            }
        }
        return null;
    }

    @Override
    public void tickDevice() {
        if (level == null || level.isClientSide || ++ticks % 10 != 0) return;
        BreakerCellBlockEntity cell = target();
        SwitchUnit u = cell == null ? null : cell.unit();
        if (u != null) {
            if (seenTrips >= 0 && u.protectionTrips() > seenTrips) tripped = true;
            seenTrips = u.protectionTrips();
        }
        BlockState s = getBlockState();
        if (s.getValue(PcProperties.ALARM) != tripped) level.setBlock(worldPosition, s.setValue(PcProperties.ALARM, tripped), Block.UPDATE_CLIENTS);
    }

    // --- руки ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return RelayModuleItem.setting(stack) != null;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        for (int i = 0; i < SLOTS; i++) {
            if (!modules.getStackInSlot(i).isEmpty()) continue;
            if (!modules.isItemValid(i, stack)) {
                player.displayClientMessage(GridText.msg(GridText.MODULE_REFUSED), true);
                return ItemInteractionResult.CONSUME;
            }
            modules.setStackInSlot(i, stack.split(1));
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    public InteractionResult use(Player player) {
        tripped = false;
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return GridPanels.CABINET;
    }

    @Override
    @Nullable
    public IItemHandlerModifiable panelItems() {
        return modules;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[GridPanels.R_SELECTED] = selected;
        out[GridPanels.R_FLAGS] = (tripped ? 1 : 0) | (target() != null ? 2 : 0);
        ItemStack s = modules.getStackInSlot(selected);
        if (s.getItem() instanceof RelayModuleItem m) {
            GridComponents.Settings v = RelayModuleItem.settings(s);
            out[GridPanels.R_KIND] = m.kind().ordinal() + 1;
            out[GridPanels.R_A1000] = (int) Math.round(v.a() * 1000);
            out[GridPanels.R_B1000] = (int) Math.round(v.b() * 1000);
        }
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        ItemStack s = modules.getStackInSlot(selected);
        switch (id) {
            case GridPanels.BUTTON_SELECT -> selected = (selected + 1) % SLOTS;
            case GridPanels.BUTTON_A_DOWN -> RelayModuleItem.adjust(s, 0, -1);
            case GridPanels.BUTTON_A_UP -> RelayModuleItem.adjust(s, 0, 1);
            case GridPanels.BUTTON_B_DOWN -> RelayModuleItem.adjust(s, 1, -1);
            case GridPanels.BUTTON_B_UP -> RelayModuleItem.adjust(s, 1, 1);
            case GridPanels.BUTTON_RESET -> tripped = false;
            default -> {
            }
        }
        setChanged();
    }

    // --- сохранение ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("modules", modules.serializeNBT(registries));
        tag.putInt("selected", selected);
        tag.putBoolean("tripped", tripped);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("modules")) modules.deserializeNBT(registries, tag.getCompound("modules"));
        selected = Math.floorMod(tag.getInt("selected"), SLOTS);
        tripped = tag.getBoolean("tripped");
    }
}
