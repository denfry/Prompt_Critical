package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.sim.drilling.BopStack;
import java.util.Locale;
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
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Плашечный превентор стека ПВО (09 §9.5.7): вид плашек — состояние блока; пустой рукой на открытом превенторе плашки
 * меняют (трубные → глухие → срезающие). Закрывает его аккумуляторная установка по команде пульта бурильщика.
 */
public final class BopRamBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    public BopRamBlockEntity(BlockPos pos, BlockState state) {
        super(DrillingBlockEntities.BOP_RAM.get(), pos, state);
    }

    /** Элемент стека по виду плашек. */
    public static BopStack.Component component(BlockState s) {
        return switch (s.getValue(PcProperties.RAM_KIND)) {
            case PIPE -> BopStack.Component.PIPE_RAM;
            case BLIND -> BopStack.Component.BLIND_RAM;
            case SHEAR -> BopStack.Component.SHEAR_RAM;
        };
    }

    @Override
    public void tickDevice() {
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
        BlockState s = getBlockState();
        if (s.getValue(PcProperties.SHUT)) {
            player.displayClientMessage(DrillingText.msg("ram_closed"), true);
            return InteractionResult.SUCCESS;
        }
        PcProperties.RamKind[] kinds = PcProperties.RamKind.values();
        PcProperties.RamKind next = kinds[(s.getValue(PcProperties.RAM_KIND).ordinal() + 1) % kinds.length];
        level.setBlock(worldPosition, s.setValue(PcProperties.RAM_KIND, next), Block.UPDATE_ALL);
        level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 0.7f);
        player.displayClientMessage(DrillingText.msg("ram_kind",
                DrillingText.msg("component." + component(level.getBlockState(worldPosition)).name().toLowerCase(Locale.ROOT))), true);
        return InteractionResult.SUCCESS;
    }
}
