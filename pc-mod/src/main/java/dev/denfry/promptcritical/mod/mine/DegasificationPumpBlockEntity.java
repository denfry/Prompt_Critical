package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
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
 * Вакуум-насос дегазации {@code degasification_pump} (08 §8.2.4) на валу Create, вал сзади. Объёмный насос: подача и
 * мощность ∝ n при неизменном разрежении; каптаж скважины растёт с подачей до {@link MineWorld#DEGAS_CAPTURE} на
 * номинале и выше не поднимается (газоотдача пласта).
 */
public final class DegasificationPumpBlockEntity extends KineticMachineBlockEntity<ShaftInput> {
    public static final double NOMINAL_RPM = 64;
    /** Мощность на номинале, Вт [BALANCE: ВВН-12 ≈ 20–30 кВт на группу скважин, на одну — меньше]. */
    public static final double RATED_W = 15_000;

    private final ShaftInput shaft = new ShaftInput();
    private long tick;

    public DegasificationPumpBlockEntity(BlockPos pos, BlockState state) {
        super(MineBlockEntities.DEGASIFICATION_PUMP.get(), pos, state);
    }

    /** Каптаж, доля газовыделения узла скважины, на тике мира симуляции {@code simTick}. */
    public double capture(long simTick) {
        return MineWorld.DEGAS_CAPTURE * Math.min(1, shaft.rpm(simTick) / NOMINAL_RPM);
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
        return RATED_W * u.rpm(tick) / NOMINAL_RPM;
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
        setStateFlag(PcProperties.RUNNING, u.rpm(tick) > 0);
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
