package dev.denfry.promptcritical.mod.boiler;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.core.CommandSource;
import dev.denfry.promptcritical.sim.core.SimCommand;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Механическая цепная решётка {@code grate_stoker} (07 §7.9.4) — забрасыватель на валу Create: бункер угля (воронки,
 * желоба, рука), цепь подаёт топливо в топку котла, к которому решётка примыкает задом, со скоростью
 * {@link #NOMINAL_KG_S}·n/{@link #NOMINAL_RPM}. Слой на решётке горит в домене котла; машина топлива не держит —
 * порции уходят командой {@link SimCommand.ChargeFuel} раз в секунду. Пока слой больше
 * {@link FireTubeBoilerBlockEntity#MAX_BED_KG} (котёл погас), цепь не подаёт.
 */
public class GrateStokerBlockEntity extends KineticMachineBlockEntity<GrateStokerBlockEntity> implements PanelHost {
    /** Привод цепи, Вт, при номинальных оборотах. */
    public static final double RATED_W = 1_500;
    public static final double NOMINAL_RPM = 32;
    /** Подача при номинальных оборотах — номинал котла 07 §7.9.4, кг/с. */
    public static final double NOMINAL_KG_S = 0.3;
    private static final int FLUSH_PERIOD = 20;

    private final ShaftInput shaft = new ShaftInput();
    private final ItemStackHandler hopper = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return BoilerFuels.key(stack) != null;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    @Nullable
    private String itemKey, pendingKey;
    private double itemLeftKg, pendingKg, feedKgS, rpmNow;
    private int ticks;

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
        ticks++;
        double rpm = shaft.rpm(b.world().clock().tick());
        rpmNow = rpm;
        Direction back = getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        FireTubeBoilerBlockEntity boiler = FireTubeBoilerBlockEntity.at(level, worldPosition.relative(back));
        var u = boiler == null ? null : boiler.unit();
        feedKgS = 0;
        if (rpm > 0 && u != null && u.boiler().bedKg() + pendingKg < FireTubeBoilerBlockEntity.MAX_BED_KG) {
            double want = NOMINAL_KG_S * rpm / NOMINAL_RPM * 0.05;
            while (want > 1e-9) {
                if (itemLeftKg <= 1e-9) {
                    ItemStack next = hopper.getStackInSlot(0);
                    String key = BoilerFuels.key(next);
                    if (key == null) break;
                    hopper.extractItem(0, 1, false);
                    itemKey = key;
                    itemLeftKg = BoilerFuels.itemMassKg();
                }
                if (pendingKey != null && !pendingKey.equals(itemKey)) flush(b, boiler);
                double d = Math.min(want, itemLeftKg);
                pendingKey = itemKey;
                pendingKg += d;
                itemLeftKg -= d;
                want -= d;
                feedKgS += d / 0.05;
            }
        }
        if (boiler != null && (ticks % FLUSH_PERIOD == 0 || feedKgS == 0)) flush(b, boiler);
        setStateFlag(PcProperties.RUNNING, feedKgS > 0);
    }

    private void flush(SimLevelBinding b, FireTubeBoilerBlockEntity boiler) {
        if (pendingKg <= 0 || pendingKey == null || boiler.simId().isNone()) return;
        b.submit(new SimCommand.ChargeFuel(boiler.simId(), pendingKey, pendingKg), new CommandSource.Automatic("grate_stoker"));
        pendingKg = 0;
        pendingKey = null;
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return hopper;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return BoilerFuels.key(stack) != null;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, hopper.insertItem(0, stack, false));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        Direction back = getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        if (FireTubeBoilerBlockEntity.at(level, worldPosition.relative(back)) == null) {
            player.displayClientMessage(Component.translatable(BoilerText.STOKER_NO_BOILER), true);
        } else {
            ItemStack s = hopper.getStackInSlot(0);
            player.displayClientMessage(Component.translatable(BoilerText.STOKER_STATUS,
                    s.isEmpty() ? "—" : s.getCount() + " × " + s.getHoverName().getString(),
                    String.format(Locale.ROOT, "%.2f", feedKgS)), true);
        }
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
        return hopper;
    }

    @Override
    public void readPanel(int[] out) {
        Direction back = getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        FireTubeBoilerBlockEntity boiler = FireTubeBoilerBlockEntity.at(level, worldPosition.relative(back));
        var u = boiler == null ? null : boiler.unit();
        out[BoilerPanels.S_FEED_KGH] = (int) Math.round(feedKgS * 3600);
        out[BoilerPanels.S_RPM10] = (int) Math.round(rpmNow * 10);
        out[BoilerPanels.S_BED_KG10] = u == null ? 0 : (int) Math.round(u.boiler().bedKg() * 10);
        out[BoilerPanels.S_FLAGS] = (feedKgS > 0 ? 1 << BoilerPanels.SF_RUNNING : 0) | (boiler != null ? 1 << BoilerPanels.SF_BOILER : 0);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) return;
        tag.put("hopper", hopper.serializeNBT(registries));
        tag.putDouble("item_left_kg", itemLeftKg);
        if (itemKey != null) tag.putString("item_key", itemKey);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        if (tag.contains("hopper")) hopper.deserializeNBT(registries, tag.getCompound("hopper"));
        itemLeftKg = tag.getDouble("item_left_kg");
        itemKey = tag.contains("item_key") ? tag.getString("item_key") : null;
    }
}
