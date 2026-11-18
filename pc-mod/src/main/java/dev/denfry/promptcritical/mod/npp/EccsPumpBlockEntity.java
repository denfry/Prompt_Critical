package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.sim.accident.PlantAccident;
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
 * One pumped ECCS train on a Create shaft (shaft at the back): a high-head and a low-head stage taking borated water
 * from the tank into the cold legs (PWR) or the core spray and LPCI lines (BWR). The shaft stands for the safety bus:
 * an electric motor on the grid or an emergency diesel turning it is AC power; with the shaft still, the train is
 * dead, and with every train and every coolant pump still the reactor is in station blackout. Flow follows the pump
 * curve in {@link PlantAccident#pumpFlow}; rated shaft power is the high-head stage at its design point, 15 kg/s
 * against 14 MPa with an efficiency of 0.7.
 */
public final class EccsPumpBlockEntity extends KineticMachineBlockEntity<ShaftInput> {
    public static final double NOMINAL_RPM = 64;
    /** Shaft power at the design point, W: 15 kg/s × 14 MPa / 1000 kg/m³ / 0.7. */
    public static final double RATED_W = 15 * 14e6 / 1000 / 0.7;

    private final ShaftInput shaft = new ShaftInput();
    private long tick;
    private boolean driven, injecting;

    public EccsPumpBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.ECCS_PUMP.get(), pos, state);
    }

    /** The shaft turns at least at half speed: the train can start on a safety injection signal. */
    public boolean driven() {
        return driven;
    }

    /** Set by the reactor while the train delivers water, for the running state and the shaft load. */
    void reportInjecting(boolean on) {
        injecting = on;
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
        // on standby the pump idles on recirculation; injecting it takes the design power
        return u.rpm(tick) > 0 ? RATED_W * (injecting ? 1 : 0.1) : 0;
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
        driven = u.rpm(tick) >= 0.5 * NOMINAL_RPM;
        if (level.getGameTime() % 10 == 0) setStateFlag(PcProperties.RUNNING, driven && injecting);
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
