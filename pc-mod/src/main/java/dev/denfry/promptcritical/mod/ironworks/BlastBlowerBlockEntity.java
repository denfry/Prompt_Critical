package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnaceUnit;
import dev.denfry.promptcritical.sim.metallurgy.CyclicMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Воздуходувка {@code blast_blower} 2×2×2 (08 §8.5.4) на валу Create: холодное дутьё Q₀·s, s — скорость вала к
 * номинальной, Q₀ = {@value BlastFurnaceUnit#BLOWER_NM3S} нм³/с физ.; мощность на валу — сжатие до π = 3. Дутьё
 * забирает домна в пределах {@value BlastFurnaceControllerBlockEntity#LINK_RANGE} блоков.
 */
public final class BlastBlowerBlockEntity extends KineticMachineBlockEntity<CyclicMachine> {
    public BlastBlowerBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.BLAST_BLOWER.get(), pos, state);
    }

    @Override
    @Nullable
    protected CyclicMachine unit(SimLevelBinding b) {
        return Ironworks.shaftMachine(b, simId());
    }

    /** Холодное дутьё сейчас, нм³/с физ. */
    public double coldNm3S() {
        CyclicMachine m = unit();
        return m == null ? 0 : BlastFurnaceUnit.BLOWER_NM3S * m.speedRatio();
    }

    @Override
    protected ShaftInput shaft(CyclicMachine u) {
        return u.shaft;
    }

    @Override
    protected double powerW(CyclicMachine u) {
        return u.powerW();
    }

    @Override
    protected double ratedW() {
        return BlastFurnaceUnit.BLOWER_RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return CyclicMachine.NOMINAL_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding b, CyclicMachine u) {
        u.setLoaded(true);
        setStateFlag(PcProperties.RUNNING, u.running());
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
        player.displayClientMessage(IronworksText.msg("blower", IronworksText.num(coldNm3S() * 3600)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void clientTick() {
        MachineFx fx = fx();
        if (runningState() && fx.chance(1)) fx.sound(fx.at(16, 16, 16), SoundEvents.ELYTRA_FLYING, 0.08f, 0.5f);
    }
}
