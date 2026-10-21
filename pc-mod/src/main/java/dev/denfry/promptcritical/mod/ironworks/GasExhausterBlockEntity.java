package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.metallurgy.CyclicMachine;
import dev.denfry.promptcritical.sim.metallurgy.GasMain;
import dev.denfry.promptcritical.sim.metallurgy.ProcessGas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Эксгаустер коксового газа {@code gas_exhauster} (08 §8.4.4) на валу Create: касается газосборника батареи,
 * отсасывает Q₀·s, s — скорость вала к номинальной (давление считает {@link GasMain}); газ — в свой бак, откуда его
 * забирают трубы Create.
 */
public final class GasExhausterBlockEntity extends KineticMachineBlockEntity<CyclicMachine> {
    private final GasBuffer outlet = new GasBuffer(16_000, s -> s.is(GasBuffer.fluid(ProcessGas.COKE_OVEN.fluidId)), this::setChanged);

    public GasExhausterBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.GAS_EXHAUSTER.get(), pos, state);
    }

    @Override
    @Nullable
    protected CyclicMachine unit(SimLevelBinding b) {
        return Ironworks.shaftMachine(b, simId());
    }

    @Nullable
    public CyclicMachine machine() {
        return unit();
    }

    public GasBuffer outlet() {
        return outlet;
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
        return GasMain.EXHAUSTER_RATED_W;
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
    public IFluidHandler fluids(@Nullable Direction side) {
        return outlet.output();
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
        CyclicMachine m = unit();
        double s = m == null ? 0 : m.speedRatio();
        player.displayClientMessage(IronworksText.msg("exhauster", IronworksText.num(GasMain.EXHAUSTER_NM3S * s * 3600),
                outlet.amount()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void clientTick() {
        MachineFx fx = fx();
        if (fx != null && runningState() && fx.chance(1)) fx.sound(fx.at(16, 16, 8),
                net.minecraft.sounds.SoundEvents.MINECART_INSIDE, 0.15f, 0.6f);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) tag.put("outlet", outlet.save(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket && tag.contains("outlet")) outlet.load(registries, tag.getCompound("outlet"));
    }
}
