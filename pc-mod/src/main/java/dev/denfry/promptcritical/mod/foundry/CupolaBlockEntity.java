package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.metallurgy.CupolaUnit;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Вагранка {@code cupola_furnace} (08 §8.9): колоша — металл и кокс по предмету в 100 кг, дутьё — воздуходувкой на
 * валу Create, выпуск — сам, в заформованную форму у подножия (рядом с нижней клеткой или ниже на блок). Сигнал
 * красного камня держит лётку закрытой.
 */
public final class CupolaBlockEntity extends KineticMachineBlockEntity<CupolaUnit> implements PanelHost {
    static final int POUR_PERIOD_TICKS = 10;

    private final MachineInventory inv = CupolaLogic.inventory(this::setChanged);

    public CupolaBlockEntity(BlockPos pos, BlockState state) {
        super(FoundryBlockEntities.CUPOLA.get(), pos, state);
    }

    @Override
    @Nullable
    protected CupolaUnit unit(SimLevelBinding b) {
        return b.world().subsystem(FoundrySubsystem.class).unit(b.world().topology(), simId()) instanceof CupolaUnit u ? u : null;
    }

    @Override
    protected ShaftInput shaft(CupolaUnit u) {
        return u.shaft;
    }

    @Override
    protected double powerW(CupolaUnit u) {
        return u.powerW();
    }

    @Override
    protected double ratedW() {
        return CupolaUnit.BLOWER_NOMINAL_W;
    }

    @Override
    protected double nominalRpm() {
        return CupolaUnit.NOMINAL_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding b, CupolaUnit u) {
        CupolaLogic.charge(inv, u);
        if (level.getGameTime() % POUR_PERIOD_TICKS == 0 && !level.hasNeighborSignal(worldPosition)
                && !level.hasNeighborSignal(worldPosition.below())) {
            FoundrySubsystem shop = b.world().subsystem(FoundrySubsystem.class);
            BlockPos bottom = worldPosition.below();
            for (Direction d : Direction.Plane.HORIZONTAL)
                if (CupolaLogic.pour(level, bottom.relative(d), shop, u)) break;
        }
        setStateFlag(BlockStateProperties.LIT, u.lit());
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return CupolaLogic.accepts(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        CupolaLogic.status(player, unit());
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void clientTick() {
        MachineFx fx = fx();
        if (fx == null || !getBlockState().getValue(BlockStateProperties.LIT)) return;
        if (fx.chance(3)) fx.sparks(fx.at(8, 32, 8), 1);
        if (fx.chance(4)) fx.smoke(fx.at(8, 32, 8), false, 0.3);
        if (fx.chance(3)) fx.flame(fx.at(8, -4, 0.6), 0.1);
        if (fx.chance(0.6)) fx.drip(fx.at(8, -6, -2.5));
    }

    @Override
    public PanelLayout panelLayout() {
        return FoundryPanels.CUPOLA;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        boolean held = level.hasNeighborSignal(worldPosition) || level.hasNeighborSignal(worldPosition.below());
        CupolaLogic.readPanel(out, unit(), shaftRpm10(), held);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) tag.put("inventory", inv.save(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket && tag.contains("inventory")) inv.load(registries, tag.getCompound("inventory"));
    }
}
