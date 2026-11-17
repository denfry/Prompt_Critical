package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.sim.grid.CapacitorBankUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Батарея статических конденсаторов 6 кВ ({@link CapacitorBankUnit}): кабель или шина — к любой грани, кроме фасада;
 * правый клик переключает ступени 0…4 по 150 квар. Лампа горит, когда батарея под напряжением.
 */
public class CapacitorBankBlockEntity extends GridDeviceBlockEntity<CapacitorBankUnit> {
    public CapacitorBankBlockEntity(BlockPos pos, BlockState state) {
        super(GridBlockEntities.CAPACITOR_BANK.get(), pos, state);
    }

    @Override
    protected CapacitorBankUnit unitOf(GridSubsystem grid) {
        return grid.capacitorBank(simId());
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        for (Direction d : Direction.values()) if (d != facing()) out.add(new Face(worldPosition, d));
        return List.of(out);
    }

    @Override
    protected void serverTick(CapacitorBankUnit u) {
        if (ticks % 10 == 0) setFlag(BlockStateProperties.LIT, u.steps() > 0 && u.reactiveVar() > 1);
    }

    @Override
    public InteractionResult use(Player player) {
        CapacitorBankUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        u.cycleSteps();
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        player.displayClientMessage(Component.translatable("message.promptcritical.capacitor_bank", u.steps(),
                CapacitorBankUnit.MAX_STEPS, String.format(Locale.ROOT, "%.0f", u.ratedVar() / 1e3)), true);
        return InteractionResult.SUCCESS;
    }
}
