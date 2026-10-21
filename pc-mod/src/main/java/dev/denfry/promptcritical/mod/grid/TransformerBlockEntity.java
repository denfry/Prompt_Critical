package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.TransformerUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Распределительный трансформатор ТМ-630/6 ({@code distribution_transformer}, 1×2×1, 07 §7.4.3): ВН — верхняя клетка
 * (сзади, по бокам и сверху), НН — нижняя (сзади, по бокам и снизу). ПБВ ±2×2,5 % — только без напряжения.
 */
public class TransformerBlockEntity extends GridDeviceBlockEntity<TransformerUnit> implements PanelHost {
    public TransformerBlockEntity(BlockPos pos, BlockState state) {
        super(GridBlockEntities.DISTRIBUTION_TRANSFORMER.get(), pos, state);
    }

    @Override
    protected TransformerUnit unitOf(GridSubsystem grid) {
        return grid.transformer(simId());
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> hv = new ArrayList<>(), lv = new ArrayList<>();
        Direction f = facing();
        GridDevice.faces(hv, worldPosition, f, 0, 1, 0, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP);
        GridDevice.faces(lv, worldPosition, f, 0, 0, 0, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN);
        return List.of(hv, lv);
    }

    @Override
    public PanelLayout panelLayout() {
        return GridPanels.TRANSFORMER;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        TransformerUnit u = unit();
        if (u == null) return;
        out[GridPanels.T_LOAD_PERMIL] = (int) Math.round(u.loading() * 1000);
        out[GridPanels.T_HOTSPOT_C10] = (int) Math.round(u.hotSpotC() * 10);
        out[GridPanels.T_CURRENT_A10] = (int) Math.round(u.currentA() * 10);
        out[GridPanels.T_POWER_KW10] = (int) Math.round(u.powerW() / 100);
        out[GridPanels.T_TAP] = u.tap();
        out[GridPanels.T_LIFE_H] = (int) Math.min(Integer.MAX_VALUE, Math.round(u.lossOfLifeH()));
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        TransformerUnit u = unit();
        if (u == null) return;
        int step = id == GridPanels.BUTTON_TAP_DOWN ? -1 : id == GridPanels.BUTTON_TAP_UP ? 1 : 0;
        if (step == 0) return;
        if (!u.setTap(u.tap() + step)) player.displayClientMessage(GridText.msg(GridText.TAP_LIVE), true);
        else player.displayClientMessage(GridText.msg(GridText.TAP, u.tap(), u.tap() * 2.5), true);
    }
}
