package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.sim.metallurgy.CyclicMachine;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Дутьевой вентилятор {@code forced_draft_fan} и дымосос {@code induced_draft_fan} котла I1 (07 §7.9.5) на валу
 * Create: расход Q₀·s, s — скорость вала к номинальной ({@value CyclicMachine#NOMINAL_RPM} об/мин); Q₀ — 3 кг/с воздуха
 * или 3,5 кг/с газов. Работают на котёл в пределах {@value WaterTubeBoilerBlockEntity#LINK_RANGE} блоков: щит котла
 * берёт их расход каждый тик, направляющий аппарат (доля расхода) ставят регуляторы котла.
 */
public final class DraftFanBlockEntity extends KineticMachineBlockEntity<CyclicMachine> {
    public static final double FORCED_KG_S = 3.0, INDUCED_KG_S = 3.5;
    /** Без вызова щита дольше этого вентилятор считается не подключённым к котлу, тиков. */
    private static final int LINK_TIMEOUT = 10;
    private final boolean forced;
    private long linkedAt = Long.MIN_VALUE;

    private DraftFanBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean forced) {
        super(type, pos, state);
        this.forced = forced;
    }

    public static DraftFanBlockEntity forced(BlockPos pos, BlockState state) {
        return new DraftFanBlockEntity(PowerhouseBlockEntities.FORCED_DRAFT_FAN.get(), pos, state, true);
    }

    public static DraftFanBlockEntity induced(BlockPos pos, BlockState state) {
        return new DraftFanBlockEntity(PowerhouseBlockEntities.INDUCED_DRAFT_FAN.get(), pos, state, false);
    }

    public boolean forcedDraft() {
        return forced;
    }

    /** Расход при текущих оборотах, кг/с. */
    public double flowKgS() {
        CyclicMachine m = unit();
        return m == null ? 0 : (forced ? FORCED_KG_S : INDUCED_KG_S) * m.speedRatio();
    }

    /** Щит котла взял расход этого вентилятора на тике {@code now}. */
    void link(long now) {
        linkedAt = now;
    }

    private boolean linked() {
        return level != null && level.getGameTime() - linkedAt <= LINK_TIMEOUT;
    }

    @Override
    @Nullable
    protected CyclicMachine unit(SimLevelBinding b) {
        return Ironworks.shaftMachine(b, simId());
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
        return forced ? FoundrySubsystem.FORCED_DRAFT_FAN_W : FoundrySubsystem.INDUCED_DRAFT_FAN_W;
    }

    @Override
    protected double nominalRpm() {
        return CyclicMachine.NOMINAL_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding b, CyclicMachine u) {
        u.setLoaded(linked());
        setStateFlag(PcProperties.RUNNING, u.speedRatio() > 0);
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
        player.displayClientMessage(PowerhouseText.msg("fan", getBlockState().getBlock().getName(),
                PowerhouseText.num(flowKgS(), 2), PowerhouseText.num(Math.abs(getSpeed()), 0),
                PowerhouseText.msg(linked() ? "fan.linked" : "fan.unlinked")), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void clientTick() {
        MachineFx fx = fx();
        if (runningState() && fx.chance(1)) fx.sound(fx.at(8, 8, 8), SoundEvents.ELYTRA_FLYING, 0.05f, forced ? 0.7f : 0.5f);
    }
}
