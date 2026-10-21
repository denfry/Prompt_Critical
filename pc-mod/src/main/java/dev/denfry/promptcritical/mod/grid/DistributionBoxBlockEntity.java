package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.SwitchUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Щиток участка 0,4 кВ ({@code lv_distribution_box}, 07 §7.4.6): плавкая вставка {@code lv_fuse} между вводом (стена за
 * щитком) и участком (остальные грани). Перегоревшая вставка меняется рукой; Shift + ПКМ вынимает её — видимый разрыв.
 */
public class DistributionBoxBlockEntity extends GridDeviceBlockEntity<SwitchUnit> implements PanelHost {
    public DistributionBoxBlockEntity(BlockPos pos, BlockState state) {
        super(GridBlockEntities.LV_DISTRIBUTION_BOX.get(), pos, state);
    }

    @Override
    protected SwitchUnit unitOf(GridSubsystem grid) {
        return grid.switchUnit(simId(), SwitchUnit.Kind.FUSE);
    }

    @Override
    public List<List<Face>> terminals() {
        Direction back = facing().getOpposite();
        List<Face> in = List.of(new Face(worldPosition, back)), out = new ArrayList<>();
        for (Direction d : Direction.values()) if (d != back) out.add(new Face(worldPosition, d));
        return List.of(in, out);
    }

    @Override
    protected void serverTick(SwitchUnit u) {
        if (ticks % 5 == 0) setFlag(PcProperties.ALARM, u.fuseBlown() || !u.fuseInstalled());
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.LV_FUSE.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        SwitchUnit u = unit();
        if (u == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (u.fuseInstalled() && !u.fuseBlown()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        u.installFuse();
        stack.consume(1, player);
        player.displayClientMessage(GridText.msg(GridText.FUSE_FITTED, u.ratedA()), true);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, 1.2f);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        SwitchUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        if (!u.fuseInstalled()) return InteractionResult.PASS;
        boolean blown = u.fuseBlown();
        u.removeFuse();
        if (!blown) player.getInventory().placeItemBackInInventory(new ItemStack(PcItems.LV_FUSE.get()));
        player.displayClientMessage(GridText.msg(blown ? GridText.FUSE_BLOWN : GridText.FUSE_REMOVED), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return GridPanels.FUSE_BOX;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        SwitchUnit u = unit();
        if (u != null) GridPanels.readSwitch(u, out);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        SwitchUnit u = unit();
        if (u != null && id == GridPanels.BUTTON_RATING) u.cycleRating();
    }
}
