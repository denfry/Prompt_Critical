package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Гидроворонка: через неё в раствор рабочих ёмкостей засыпают барит. Каждый мешок — {@link #SACK_KG} кг (мешки
 * 50 кг, API 13A) и наполнитель поглощения; воронка передаёт их пульту бурильщика ближайшей буровой.
 */
public final class MudHopperBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    public static final double SACK_KG = 50;
    /** Наполнитель поглощения (тег {@code c:loss_circulation_material}, 09 §9.5.10): предмет — 10 кг [допущение]. */
    public static final double LCM_KG = 10;

    public MudHopperBlockEntity(BlockPos pos, BlockState state) {
        super(DrillingBlockEntities.MUD_MIXING_HOPPER.get(), pos, state);
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
        return stack.is(PcItems.BARITE_POWDER.get()) || stack.is(dev.denfry.promptcritical.mod.registry.PcTags.Items.LOSS_CIRCULATION_MATERIAL);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        DrillersConsoleBlockEntity console = DrillersConsoleBlockEntity.near(level, worldPosition);
        if (console == null || console.well() == null) {
            player.displayClientMessage(DrillingText.msg("barite_no_rig"), true);
            return ItemInteractionResult.CONSUME;
        }
        int n = stack.getCount();
        if (stack.is(PcItems.BARITE_POWDER.get())) console.addBarite(n * SACK_KG, player);
        else console.addLcm(n * LCM_KG, player);
        if (!player.getAbilities().instabuild) stack.shrink(n);
        level.playSound(null, worldPosition, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }
}
