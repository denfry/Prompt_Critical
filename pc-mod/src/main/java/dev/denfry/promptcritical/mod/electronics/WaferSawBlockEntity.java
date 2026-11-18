package dev.denfry.promptcritical.mod.electronics;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.electronics.Czochralski;
import dev.denfry.promptcritical.sim.electronics.Wafering;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import java.util.Arrays;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Резка и полировка пластин {@code wafer_saw} (15 §15.6): диск с внутренней алмазной кромкой режет монокристалл на
 * 50-миллиметровые пластины (280 мкм плюс пропил 300 мкм), полировальник на том же валу снимает нарушенный слой по
 * Престону — {@value #BATCH} пластин за цикл. Скорость полировальника — радиус {@value #PAD_RADIUS_M} м на обороты вала.
 * Из слитка идёт только часть в допуске по сопротивлению (Шейль), хвост — в оборот. Поликристалл не режут.
 */
public final class WaferSawBlockEntity extends KineticMachineBlockEntity<ShaftInput> implements PanelHost {
    public static final int BATCH = 10;
    public static final double PAD_RADIUS_M = 0.15, RATED_W = 3000;

    private final ShaftInput shaft = new ShaftInput();
    private final ItemStackHandler inv = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack s) {
            return slot == 0 ? ElectronicsBlocks.isMonoIngot(s) : s.is(PcItems.SILICON_WAFER.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler external = new ElectronicsBlocks.SlotsIo(inv, 1, 1);
    private int left;
    private double polishMin, polishNeed;
    private boolean running;
    private double rpm;

    public WaferSawBlockEntity(BlockPos pos, BlockState state) {
        super(ElectronicsBlocks.WAFER_SAW.get(), pos, state);
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
        return running ? RATED_W : 0;
    }

    @Override
    protected double ratedW() {
        return RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return 64;
    }

    /** Пластин из слитка в допуске. */
    static int wafersOf(ItemStack ingot) {
        var spec = ingot.get(ElectronicsComponents.INGOT.get());
        if (spec == null || !spec.monocrystal()) return 0;
        double length = Czochralski.ingotLengthMm(spec.kg(), ElectronicsComponents.INGOT_MM) * spec.usable();
        return Wafering.wafersPerIngot(length);
    }

    @Override
    protected void serverTick(SimLevelBinding binding, ShaftInput u) {
        rpm = u.rpm(binding.world().clock().tick());
        double v = 2 * Math.PI * PAD_RADIUS_M * rpm / 60;
        polishNeed = Wafering.polishMinutes(v);
        ItemStack ingot = inv.getStackInSlot(0);
        if (left <= 0 && !ingot.isEmpty()) {
            left = wafersOf(ingot);
            ingot.shrink(1);
        }
        ItemStack out = inv.getStackInSlot(1);
        running = left > 0 && rpm > 0 && out.getCount() + BATCH <= 64;
        if (running) {
            polishMin += 0.05 * binding.world().clock().kSlow() / 60;
            if (polishMin >= polishNeed) {
                polishMin = 0;
                int n = Math.min(BATCH, left);
                left -= n;
                inv.insertItem(1, new ItemStack(PcItems.SILICON_WAFER.get(), n), false);
            }
        }
        if (level.getGameTime() % 10 == 0) setStateFlag(PcProperties.RUNNING, running);
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return external;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return ElectronicsBlocks.isMonoIngot(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertItem(0, stack, false));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        ItemStack s = inv.extractItem(1, 64, false);
        if (!s.isEmpty() && !player.addItem(s)) player.drop(s, false);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return ElectronicsPanels.SAW;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] o) {
        Arrays.fill(o, 0);
        o[ElectronicsPanels.K_RPM] = (int) Math.round(rpm);
        o[ElectronicsPanels.K_PROGRESS] = polishNeed > 0 && Double.isFinite(polishNeed) ? (int) Math.round(1000 * polishMin / polishNeed) : 0;
        o[ElectronicsPanels.K_PULL] = Double.isFinite(polishNeed) ? (int) Math.round(polishNeed * 10) : 0;
        o[ElectronicsPanels.K_LEFT] = left;
        o[ElectronicsPanels.K_FLAGS] = running ? 1 << ElectronicsPanels.KF_RUNNING : 0;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("inv", inv.serializeNBT(registries));
        tag.putInt("left", left);
        tag.putDouble("polish", polishMin);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("inv")) inv.deserializeNBT(registries, tag.getCompound("inv"));
        left = tag.getInt("left");
        polishMin = tag.getDouble("polish");
    }
}
