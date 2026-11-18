package dev.denfry.promptcritical.mod.hazard;

import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.hazard.OilSpill;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Сорбирующий мат {@code sorbent_pad} (14 §14.5.4): ПКМ по плёнке {@code oil_slick} собирает до
 * {@value OilSpill#SORBENT_KG_PER_KG} кг нефти на килограмм мата (мат — {@value #PAD_KG} кг [BALANCE]), по
 * замазученному песку или гравию — очищает берег. Мат становится отходом {@code oiled_sorbent} (сжигают в котле).
 */
public class SorbentPadItem extends Item {
    /** Масса одного мата, кг. */
    public static final double PAD_KG = 1;

    public SorbentPadItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState st = level.getBlockState(pos);
        Player player = ctx.getPlayer();
        boolean slick = st.is(PcBlocks.OIL_SLICK.get()), shore = st.getBlock() instanceof SpillBlocks.OiledShoreBlock;
        if (!slick && !shore) return InteractionResult.PASS;
        if (level instanceof ServerLevel sl) {
            if (slick) {
                OilSpills.Spill s = OilSpills.spillAt(sl, pos);
                double kg = s == null ? 0 : OilSpills.recover(sl, s, PAD_KG * OilSpill.SORBENT_KG_PER_KG);
                if (s == null) level.removeBlock(pos, false); // ничья плёнка: остаток старого пятна
                if (player != null) player.displayClientMessage(HazardText.msg("sorbent", Math.round(kg * 10) / 10.0), true);
            } else {
                level.setBlock(pos, ((SpillBlocks.OiledShoreBlock) st.getBlock()).clean(), Block.UPDATE_ALL);
                if (player != null) player.displayClientMessage(HazardText.msg("shore_cleaned"), true);
            }
            level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8f, 0.8f);
            ItemStack stack = ctx.getItemInHand();
            if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
            ItemStack waste = new ItemStack(PcItems.OILED_SORBENT.get());
            if (player != null && !player.getInventory().add(waste)) player.drop(waste, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
