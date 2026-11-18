package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.TideService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Мареограф (футшток с самописцем): показывает уровень моря у берега по модели прилива ({@link TideService}), амплитуду
 * M2 места и фазу Луны. Видимая вода не двигается — уровень живёт на приборе.
 */
public class TideGaugeBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public TideGaugeBlockEntity(BlockPos pos, BlockState state) {
        super(PowerhouseBlockEntities.TIDE_GAUGE.get(), pos, state);
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
        return InteractionResult.PASS;
    }

    @Override
    public PanelLayout panelLayout() {
        return TidalPanels.GAUGE_PANEL;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        if (!(level instanceof ServerLevel sl)) return;
        double m2 = TideService.m2AmplitudeM(sl, worldPosition);
        double sea = TideService.seaLevelM(sl, worldPosition);
        out[TidalPanels.SEA_PERMIL] = TidalPanels.glass(sea, Math.max(0.5, 1.6 * m2));
        out[TidalPanels.SEA_CM] = (int) Math.round(sea * 100);
        out[TidalPanels.M2_CM] = (int) Math.round(m2 * 100);
        out[TidalPanels.MOON] = TideService.moonPhase(sl);
    }
}
