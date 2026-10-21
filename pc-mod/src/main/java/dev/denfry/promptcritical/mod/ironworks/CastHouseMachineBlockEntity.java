package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Машина литейного двора {@code cast_house_machine} (08 §8.5.5) у лётки домны (до {@value #REACH} блоков): вид —
 * бур ({@code drill}: вскрывает лётку) или пушка ({@code mud_gun}: забивает лётку, {@value #CLAY_PER_PLUG} ед.
 * {@code taphole_clay}). Срабатывает от ПКМ или фронта сигнала красного камня.
 */
public final class CastHouseMachineBlockEntity extends IronworksBlockEntity {
    public static final int REACH = 4, CLAY_PER_PLUG = 2;
    private final MachineInventory clay = new MachineInventory(new MachineInventory.Role[] {MachineInventory.Role.INPUT},
            (slot, s) -> s.is(PcItems.TAPHOLE_CLAY.get()), this::setChanged);
    private boolean powered;

    public CastHouseMachineBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.CAST_HOUSE_MACHINE.get(), pos, state);
    }

    public PcProperties.CastHouseKind kind() {
        BlockState s = getBlockState();
        return s.hasProperty(PcProperties.CAST_HOUSE_KIND) ? s.getValue(PcProperties.CAST_HOUSE_KIND) : PcProperties.CastHouseKind.DRILL;
    }

    @Nullable
    private BlastFurnaceControllerBlockEntity furnace() {
        return BlastFurnaceControllerBlockEntity.find(level, worldPosition, s -> s.taphole().distManhattan(worldPosition) <= REACH);
    }

    /** Сработать; ключ результата для сообщения. */
    public String act() {
        BlastFurnaceControllerBlockEntity f = furnace();
        if (f == null) return "cast_house_no_taphole";
        if (kind() == PcProperties.CastHouseKind.DRILL) return f.drill() ? "cast_house_drilled" : "cast_house_drill_failed";
        if (clay.getStackInSlot(0).getCount() < CLAY_PER_PLUG) return "cast_house_no_clay";
        if (!f.plug()) return "cast_house_plug_idle";
        clay.getStackInSlot(0).shrink(CLAY_PER_PLUG);
        return "cast_house_plugged";
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        boolean now = level.hasNeighborSignal(worldPosition);
        if (now && !powered) act();
        if (now != powered) {
            powered = now;
            setChanged();
        }
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return clay.external();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.TAPHOLE_CLAY.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, clay.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        BlockState s = getBlockState();
        if (player.isShiftKeyDown() && s.hasProperty(PcProperties.CAST_HOUSE_KIND)) {
            PcProperties.CastHouseKind k = kind() == PcProperties.CastHouseKind.DRILL ? PcProperties.CastHouseKind.MUD_GUN
                    : PcProperties.CastHouseKind.DRILL;
            level.setBlock(worldPosition, s.setValue(PcProperties.CAST_HOUSE_KIND, k), net.minecraft.world.level.block.Block.UPDATE_ALL);
            player.displayClientMessage(IronworksText.msg("cast_house." + k.getSerializedName()), true);
            return InteractionResult.SUCCESS;
        }
        Component m = IronworksText.msg(act(), clay.getStackInSlot(0).getCount());
        player.displayClientMessage(m, true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("clay", clay.save(registries));
        tag.putBoolean("powered", powered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("clay")) clay.load(registries, tag.getCompound("clay"));
        powered = tag.getBoolean("powered");
    }
}
