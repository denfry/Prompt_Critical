package dev.denfry.promptcritical.mod.electronics;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.electronics.Czochralski;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Установка Чохральского {@code czochralski_puller} (11 §11.5.2): поликремний плавится в кварцевом тигле, затравка
 * вращается от вала Create через редуктор ÷ {@value #GEAR} и поднимается со скоростью, заданной на щите. Камера
 * продувается аргоном ВРУ ({@value #ARGON_NM3_PER_MIN} нм³/мин). Если хоть раз за рост скорость или вращение вышли
 * из окна или аргона не хватило — слиток выходит поликристаллом, его можно переплавить. Легирование бором —
 * лигатурой в загрузке, голова слитка 10 Ом·см; сопротивление к хвосту падает по Шейлю.
 */
public final class CzochralskiPullerBlockEntity extends KineticMachineBlockEntity<ShaftInput> implements PanelHost {
    public static final double GEAR = 4, ARGON_NM3_PER_MIN = 0.05, HEAD_BORON_CM3 = 1.3e15, TOLERANCE = 0.2, RATED_W = 1500;
    public static final double LENGTH_MM = Czochralski.ingotLengthMm(ElectronicsComponents.POLY_KG_PER_ITEM, ElectronicsComponents.INGOT_MM);

    private final ShaftInput shaft = new ShaftInput();
    private final FeedTank argon = new FeedTank(PcFluids.ARGON.source.get(), 50);
    private final ItemStackHandler inv = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack s) {
            return slot == 0 && (s.is(PcItems.POLYSILICON.get()) || s.is(PcItems.SILICON_INGOT.get()));
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler external = new ElectronicsBlocks.SlotsIo(inv, 1, 1);
    /** Скорость вытягивания, сотые мм/мин. */
    private int pull = 100;
    private double grownMm;
    private boolean mono = true, running, window, gas;
    private double rpm;
    private long tick;

    public CzochralskiPullerBlockEntity(BlockPos pos, BlockState state) {
        super(ElectronicsBlocks.CZOCHRALSKI_PULLER.get(), pos, state);
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

    private double pullMmMin() {
        return pull / 100.0;
    }

    @Override
    protected void serverTick(SimLevelBinding binding, ShaftInput u) {
        tick = binding.world().clock().tick();
        rpm = u.rpm(tick) / GEAR;
        ItemStack feed = inv.getStackInSlot(0);
        running = !feed.isEmpty() && rpm > 0 && inv.getStackInSlot(1).isEmpty();
        if (running) {
            double physMin = 0.05 * binding.world().clock().kSlow() / 60;
            double needArgon = ARGON_NM3_PER_MIN * physMin;
            gas = argon.amount() >= needArgon;
            argon.take(needArgon);
            window = Czochralski.singleCrystal(pullMmMin(), rpm);
            if (!window || !gas) mono = false;
            grownMm += pullMmMin() * physMin;
            if (grownMm >= LENGTH_MM) {
                feed.shrink(1);
                double c0 = HEAD_BORON_CM3 / Czochralski.K_BORON;
                ItemStack ingot = new ItemStack(PcItems.SILICON_INGOT.get());
                ingot.set(ElectronicsComponents.INGOT.get(), new ElectronicsComponents.Ingot(ElectronicsComponents.POLY_KG_PER_ITEM, mono,
                        Czochralski.resistivityP(Czochralski.scheil(Czochralski.K_BORON, c0, 0)),
                        Czochralski.resistivityP(Czochralski.scheil(Czochralski.K_BORON, c0, 0.9)),
                        Czochralski.usableFraction(Czochralski.K_BORON, TOLERANCE)));
                inv.setStackInSlot(1, ingot);
                grownMm = 0;
                mono = true;
            }
        }
        if (level.getGameTime() % 10 == 0) setStateFlag(PcProperties.RUNNING, running);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return argon;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return external;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return inv.isItemValid(0, stack);
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
        return ElectronicsPanels.CZ;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] o) {
        Arrays.fill(o, 0);
        o[ElectronicsPanels.K_RPM] = (int) Math.round(rpm);
        o[ElectronicsPanels.K_PULL] = pull;
        o[ElectronicsPanels.K_PROGRESS] = (int) Math.round(1000 * grownMm / LENGTH_MM);
        o[ElectronicsPanels.K_ARGON] = (int) Math.round(argon.amount() * 10);
        int f = 0;
        if (running) f |= 1 << ElectronicsPanels.KF_RUNNING;
        if (mono) f |= 1 << ElectronicsPanels.KF_MONO;
        if (Czochralski.singleCrystal(pullMmMin(), rpm)) f |= 1 << ElectronicsPanels.KF_WINDOW;
        if (argon.amount() > 0) f |= 1 << ElectronicsPanels.KF_ARGON;
        o[ElectronicsPanels.K_FLAGS] = f;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == ElectronicsPanels.BTN_PULL_DOWN) pull = Math.max(25, pull - 25);
        else if (id == ElectronicsPanels.BTN_PULL_UP) pull = Math.min(400, pull + 25);
        setChanged();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("inv", inv.serializeNBT(registries));
        tag.putInt("pull", pull);
        tag.putDouble("grown", grownMm);
        tag.putBoolean("mono", mono);
        argon.save(tag, "argon");
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("inv")) inv.deserializeNBT(registries, tag.getCompound("inv"));
        pull = tag.contains("pull") ? tag.getInt("pull") : 100;
        grownMm = tag.getDouble("grown");
        mono = !tag.contains("mono") || tag.getBoolean("mono");
        argon.load(tag, "argon");
    }
}
