package dev.denfry.promptcritical.mod.item;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Регулятор проветривания (08 §8.2.7): тот же блок {@code ventilation_door}, но ставится с {@code kind=regulator} —
 * стальной щит с настраиваемым окном. Предметом блока остаётся дверь, поэтому себя за блоком не регистрирует.
 */
public class VentilationRegulatorItem extends PcItems.PcBlockItem {
    public VentilationRegulatorItem(Block door, Properties props) {
        super(door, props);
    }

    @Override
    @Nullable
    protected BlockState getPlacementState(BlockPlaceContext ctx) {
        BlockState s = super.getPlacementState(ctx);
        return s == null ? null : s.setValue(PcProperties.DOOR_KIND, PcProperties.DoorKind.REGULATOR);
    }

    @Override
    public void registerBlocks(Map<Block, Item> map, Item item) {
    }

    @Override
    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }
}
