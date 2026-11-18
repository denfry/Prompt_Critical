package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.drilling.FormationFluid;
import dev.denfry.promptcritical.sim.drilling.WellLogging;
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
 * Станция анализа керна (09 §9.2.6): гелиевый порозиметр (±0,005), газовый пермеаметр с поправкой Клинкенберга
 * (±10 %), Дин–Старк (±0,03) — {@link WellLogging#analyse}. Керн в руке — измерение пишется в его компонент
 * [допущение: время анализа и расход гелия не моделируются].
 */
public final class CoreAnalysisStationBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    private int busyTicks, done;

    public CoreAnalysisStationBlockEntity(BlockPos pos, BlockState state) {
        super(DrillingBlockEntities.CORE_ANALYSIS_STATION.get(), pos, state);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.RESERVOIR_CORE.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        ReservoirCoreItem.Data d = stack.get(PcDataComponents.CORE_DATA.get());
        if (d == null) return ItemInteractionResult.CONSUME;
        WellLogging.Point truth = new WellLogging.Point(d.sand() ? 0 : 1, d.porosity(), d.sw(),
                d.fluorescent() ? FormationFluid.OIL : FormationFluid.WATER, 0);
        WellLogging.CoreMeasurement m = WellLogging.analyse(truth, d.permMd(), worldPosition.asLong() ^ Float.floatToIntBits(d.topM()) ^ done);
        stack.set(PcDataComponents.CORE_DATA.get(), new ReservoirCoreItem.Data(d.topM(), d.sand(), d.porosity(), d.permMd(), d.sw(),
                d.fluorescent(), true, (float) m.porosity(), (float) m.permeabilityMd(), (float) m.waterSaturation()));
        done++;
        busyTicks = 40;
        player.displayClientMessage(DrillingText.msg("core_measured", DrillingText.num(m.porosity(), 3),
                DrillingText.num(m.permeabilityMd(), 0), DrillingText.num(m.waterSaturation(), 2)), true);
        level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8f, 1.2f);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public void tickDevice() {
        if (level == null || level.isClientSide) return;
        if (busyTicks > 0) busyTicks--;
        if (level.getGameTime() % 10 == 0) {
            BlockState st = getBlockState();
            boolean busy = busyTicks > 0;
            if (st.getValue(PcProperties.RUNNING) != busy) level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, busy), Block.UPDATE_ALL);
        }
    }
}
