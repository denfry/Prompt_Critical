package dev.denfry.promptcritical.mod.drilling;

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
 * Ротор Р-560: вращает бурильную колонну через квадратную штангу (ведущую трубу). Вал Create входит слева, редуктор
 * даёт 100 об/мин стола при 64 об/мин вала. Момент — сопротивление долота и колонны из модели скважины, мощность на
 * валу — Mω; её сообщает пульт бурильщика.
 */
public final class RotaryTableBlockEntity extends KineticMachineBlockEntity<ShaftInput> {
    public static final double NOMINAL_RPM = 64, GEAR = 100 / NOMINAL_RPM;
    /** Номинал: 20 кН·м при 100 об/мин. */
    public static final double RATED_W = 20e3 * 100 * 2 * Math.PI / 60;

    private final ShaftInput shaft = new ShaftInput();
    private long tick;
    private double loadW;

    public RotaryTableBlockEntity(BlockPos pos, BlockState state) {
        super(DrillingBlockEntities.ROTARY_TABLE.get(), pos, state);
    }

    /** Частота вращения стола, об/мин. */
    public double tableRpm() {
        return shaft.rpm(tick) * GEAR;
    }

    /** Нагрузка от колонны, Вт. */
    public void load(double watts) {
        loadW = Math.max(0, watts);
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
        return u.rpm(tick) > 0 ? loadW : 0;
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
        if (level.getGameTime() % 10 == 0) setStateFlag(PcProperties.RUNNING, u.rpm(tick) > 0);
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
