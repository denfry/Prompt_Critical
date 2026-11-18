package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.sim.ops.EnergyKind;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry.LockResult;
import java.util.UUID;
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
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Групповой ящик блокировки {@code lockout_box} (13 §13.1.2) для сложных изоляций: ответственный вешает замки на
 * точки, кладёт ключи в ящик (ПКМ пустой рукой; второй ПКМ — забрать, только с пустого ящика), бригада вешает личные
 * замки на сам ящик (до шести). Пока на ящике висит хоть один замок, ответственный не снимет свои замки с точек.
 */
public class LockoutBoxBlockEntity extends DeviceBlockEntity implements FoundryDevice, IsolationPoint {
    private int ticks;

    public LockoutBoxBlockEntity(BlockPos pos, BlockState state) {
        super(OpsBlockEntities.LOCKOUT_BOX.get(), pos, state);
    }

    @Override
    public InteractionResult use(Player player) {
        LockoutRegistry r = Lockouts.registry(level);
        if (r == null) return InteractionResult.PASS;
        var key = Lockouts.key(worldPosition);
        UUID me = player.getUUID();
        LockResult res = r.keysIn(key).contains(me) ? r.withdrawKeys(me, key) : r.depositKeys(me, key);
        if (res == LockResult.OK) {
            boolean in = r.keysIn(key).contains(me);
            level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7f, 1.2f);
            player.displayClientMessage(LockoutText.msg(in ? "keys_in" : "keys_out"), true);
        } else {
            player.displayClientMessage(Lockouts.result(res).withStyle(ChatFormatting.RED), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tickDevice() {
        if (level == null || level.isClientSide || ++ticks % 10 != 0) return;
        LockoutRegistry r = Lockouts.registry(level);
        if (r == null) return;
        int n = r.locks(Lockouts.key(worldPosition)).size();
        BlockState s = getBlockState();
        if (s.hasProperty(PcProperties.LOCKS) && s.getValue(PcProperties.LOCKS) != n)
            level.setBlock(worldPosition, s.setValue(PcProperties.LOCKS, Math.min(6, n)), Block.UPDATE_CLIENTS);
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

    // --- LOTO: ящик сам — точка с постоянной накладкой ---

    @Override
    public EnergyKind energy() {
        return EnergyKind.MECHANICAL;
    }

    @Override
    public boolean isolatedPosition() {
        return true;
    }

    @Override
    public boolean noEnergy() {
        return true;
    }
}
