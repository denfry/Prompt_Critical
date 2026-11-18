package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.reactor.ChannelReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Главный циркуляционный насос первого контура на валу Create (вал сзади): при номинальных оборотах даёт половину
 * номинального расхода реактора — у АМ-1 их работало два. Мощность — гидравлическая (≈ 70 л/с на 1 МПа) с КПД 0,7.
 * Обороты ниже номинальных — расход пропорционально ниже; остановленный насос выбегает на маховике.
 */
public final class PrimaryPumpBlockEntity extends KineticMachineBlockEntity<ShaftInput> {
    public static final double NOMINAL_RPM = 64, SHARE = 0.5;
    /** Мощность насоса на полный свой расход, Вт. */
    public static final double RATED_W = ChannelReactor.NOMINAL_FLOW_KG_S * SHARE / 870 * 1.0e6 / 0.7;

    private final ShaftInput shaft = new ShaftInput();
    private long tick;
    private double flow;

    public PrimaryPumpBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.PRIMARY_PUMP.get(), pos, state);
    }

    /** Расход насоса в долях номинала реактора. */
    public double flowShare() {
        return flow;
    }

    @Override
    protected ShaftInput unit(SimLevelBinding binding) {
        return shaft;
    }

    @Override
    protected ShaftInput shaft(ShaftInput u) {
        return u;
    }

    @Override
    protected double powerW(ShaftInput u) {
        return u.rpm(tick) > 0 ? RATED_W * flow / SHARE : 0;
    }

    @Override
    protected double ratedW() {
        return RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return NOMINAL_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding binding, ShaftInput u) {
        tick = binding.world().clock().tick();
        flow = SHARE * Math.min(1, u.rpm(tick) / NOMINAL_RPM);
        if (level.getGameTime() % 10 == 0) setStateFlag(PcProperties.RUNNING, flow > 0.05);
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
}
