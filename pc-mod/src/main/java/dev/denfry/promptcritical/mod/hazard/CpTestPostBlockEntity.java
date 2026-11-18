package dev.denfry.promptcritical.mod.hazard;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.sim.pipeline.CathodicProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Контрольно-измерительный пункт {@code cp_test_post} (09 §9.8.3): ПКМ — потенциал ближайшего участка трубопровода в
 * {@value #RADIUS} блоках по медно-сульфатному электроду, «вкл» (с падением IR в грунте) и «выкл» (с разрывом тока), и
 * вывод: защищён (−0,85 В и отрицательнее), не защищён, перезащита (отрицательнее −1,2 В).
 */
public class CpTestPostBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    public static final int RADIUS = 32;

    public CpTestPostBlockEntity(BlockPos pos, BlockState state) {
        super(HazardBlockEntities.CP_TEST_POST.get(), pos, state);
    }

    @Nullable
    public PigTrapBlockEntity nearest(ServerLevel sl) {
        PigTrapBlockEntity best = null;
        double bd = Double.MAX_VALUE;
        for (PigTrapBlockEntity t : Ironworks.blockEntities(sl, new AABB(worldPosition).inflate(RADIUS), PigTrapBlockEntity.class)) {
            double d = t.getBlockPos().distSqr(worldPosition);
            if (t.launcher() && d < bd) {
                bd = d;
                best = t;
            }
        }
        return best;
    }

    @Override
    public void tickDevice() {
    }

    @Override
    public InteractionResult use(Player player) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        PigTrapBlockEntity t = nearest(sl);
        if (t == null) {
            player.displayClientMessage(HazardText.msg("test_post.none"), true);
            return InteractionResult.SUCCESS;
        }
        double off = t.offV(), on = CathodicProtection.onPotentialV(off, t.currentA(), PigTrapBlockEntity.SOIL_IR_OHM);
        String verdict = CathodicProtection.overprotected(off) ? "test_post.over" : CathodicProtection.protectedAt(off) ? "test_post.ok" : "test_post.low";
        player.displayClientMessage(HazardText.msg("test_post", Math.round(on * 1000) / 1000.0, Math.round(off * 1000) / 1000.0,
                HazardText.msg(verdict).getString()), false);
        return InteractionResult.SUCCESS;
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
    @Nullable
    public IFluidHandler fluids(@Nullable Direction side) {
        return null;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }
}
