package dev.denfry.promptcritical.mod.boiler;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Механическая цепная решётка {@code grate_stoker} (07 §7.9.4) — забрасыватель на валу Create: бункер угля (воронки,
 * желоба, рука), цепь подаёт топливо в топку котла, к которому решётка примыкает задом, со скоростью
 * {@link #NOMINAL_KG_S}·n/{@link #NOMINAL_RPM} ({@link StokerFeed}). Слой на решётке горит в домене котла.
 */
public class GrateStokerBlockEntity extends KineticMachineBlockEntity<GrateStokerBlockEntity> implements PanelHost {
    /** Привод цепи, Вт, при номинальных оборотах. */
    public static final double RATED_W = 1_500;
    public static final double NOMINAL_RPM = 32;
    /** Подача при номинальных оборотах — номинал котла 07 §7.9.4, кг/с. */
    public static final double NOMINAL_KG_S = 0.3;

    private final ShaftInput shaft = new ShaftInput();
    private final StokerFeed feed = new StokerFeed(NOMINAL_KG_S, NOMINAL_RPM, "grate_stoker", this::setChanged);
    private double rpmNow;

    public GrateStokerBlockEntity(BlockPos pos, BlockState state) {
        super(BoilerBlockEntities.GRATE_STOKER.get(), pos, state);
    }

    @Override
    @Nullable
    protected GrateStokerBlockEntity unit(SimLevelBinding binding) {
        return this;
    }

    @Override
    protected ShaftInput shaft(GrateStokerBlockEntity unit) {
        return shaft;
    }

    @Override
    protected double powerW(GrateStokerBlockEntity unit) {
        return RATED_W * rpmNow / NOMINAL_RPM;
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
    protected void serverTick(SimLevelBinding b, GrateStokerBlockEntity unit) {
        rpmNow = shaft.rpm(b.world().clock().tick());
        feed.tick(b, rpmNow, boiler());
        setStateFlag(PcProperties.RUNNING, feed.feedKgS() > 0);
    }

    @Nullable
    private FireTubeBoilerBlockEntity boiler() {
        Direction back = getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        return FireTubeBoilerBlockEntity.at(level, worldPosition.relative(back));
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return feed.hopper();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return BoilerFuels.key(stack) != null;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, feed.insert(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        feed.tell(player, boiler() != null);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void clientTick() {
        MachineFx fx = fx();
        if (fx == null || !runningState()) return;
        var r = fx.random();
        if (fx.chance(6)) fx.flame(fx.at(3 + r.nextDouble() * 10, 8.8, 8 + r.nextDouble() * 7), 0.05);
        if (fx.chance(1)) fx.wisp(fx.at(8, 9, 12), 0.4);
    }

    @Override
    public PanelLayout panelLayout() {
        return BoilerPanels.STOKER;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return feed.hopper();
    }

    @Override
    public void readPanel(int[] out) {
        feed.readPanel(out, rpmNow, boiler());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) return;
        feed.save(tag, registries);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        feed.load(tag, registries);
    }
}
