package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Газовая центрифуга и секция каскада реального масштаба ({@code gas_centrifuge_real},
 * {@code centrifuge_cascade_section_real}): своей логики у них нет — их считает пульт каскада ({@link CascadeBlockEntity})
 * в радиусе {@value CascadeBlockEntity#RADIUS} блоков: одиночная машина — 1, секция — {@value #SECTION_MACHINES}. Пока
 * пульт их питает ({@code powered}), они гудят. Разрыв ротора вскрывает корпус одиночной машины ({@code open}, ротор на
 * виду, машина из каскада выпадает); Shift + ПКМ — заменить ротор и закрыть корпус. Секцию разрыв разбивает целиком.
 * Устройство машины внутри не показывается и не моделируется: каскад считает только разделительную работу.
 */
public final class CentrifugeRealBlockEntity extends DeviceBlockEntity implements FoundryDevice, RealScaleBlockEntity {
    /** Машин в секции каскада: 16 корпусов в два ряда (fuelcycle-dimensions §1b). */
    public static final int SECTION_MACHINES = 16;

    public CentrifugeRealBlockEntity(BlockPos pos, BlockState state) {
        super(NuclearBlockEntities.CENTRIFUGE_REAL.get(), pos, state);
    }

    private boolean flag(net.minecraft.world.level.block.state.properties.BooleanProperty p) {
        BlockState s = getBlockState();
        return s.hasProperty(p) && s.getValue(p);
    }

    public boolean open() {
        return flag(BlockStateProperties.OPEN);
    }

    public boolean spinning() {
        return flag(BlockStateProperties.POWERED) && !open();
    }

    public int machines() {
        return CascadeBlockEntity.machinesAt(getBlockState());
    }

    @Override
    public void tickDevice() {
        if (level == null || !level.isClientSide || !spinning()) return;
        // ровный высокий гул зала: тихий, чтобы ряд машин не глушил всё вокруг
        MachineFx fx = MachineFx.of(this);
        if (fx != null && fx.chance(0.25)) fx.sound(fx.at(8, 16, 8), SoundEvents.BEACON_AMBIENT, 0.25f, 1.8f);
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
        if (open() && player.isShiftKeyDown()) {
            BlockState s = getBlockState();
            level.setBlock(worldPosition, s.setValue(BlockStateProperties.OPEN, false), Block.UPDATE_ALL);
            level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
            player.displayClientMessage(NuclearText.msg("centrifuge.repaired").withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.SUCCESS;
        }
        String state = open() ? "centrifuge.open" : spinning() ? "centrifuge.spinning" : "centrifuge.idle";
        boolean section = getBlockState().is(PcBlocks.CENTRIFUGE_CASCADE_SECTION_REAL.get());
        player.displayClientMessage(NuclearText.msg(section ? "centrifuge.section" : "centrifuge.single", SECTION_MACHINES,
                NuclearText.msg(state)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }
}
