package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.automation.MechanismWiring;
import dev.denfry.promptcritical.mod.automation.WiredDevice;
import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.nuclear.FuelLot;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationSubsystem;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.npp.CanduReactor;
import dev.denfry.promptcritical.sim.npp.LightWaterCore;
import dev.denfry.promptcritical.sim.radiation.FissionSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * CANDU T3 ({@link CanduReactor}) 3×6×3 с пультом на контроллере. В {@value #LINK_RADIUS} блоках — насосы
 * теплоносителя ({@link PrimaryPumpBlockEntity}, их доли расхода складываются) и парогенераторы
 * ({@link SteamGeneratorBlockEntity}) у котлов второго контура. Пучки — {@link FuelDesign#CANDU_37} природного урана
 * из слота щита уходят в магазин перегрузочной машины (до 24): кнопка «Перегрузка» на заглушенном реакторе заполняет следующий пустой канал (12 пучков), на мощности
 * вдвигает 8 свежих в самый выгоревший канал и выдаёт 8 выгоревших одним пеналом (перегрузка на ходу). Тритий в замедлителе —
 * на щите: чистый β-излучатель, внешней дозы не даёт.
 */
public final class CanduReactorBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost, WiredDevice {
    public static final int LINK_RADIUS = 12;

    private final CanduReactor reactor = new CanduReactor();
    private final MechanismWiring wiring = new MechanismWiring();
    private final ItemStackHandler fuel = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot != CanduPanels.SLOT_FRESH || !stack.is(PcItems.FUEL_ASSEMBLY.get())) return false;
            FuelLot l = FuelLot.of(stack);
            return l.designOrNull() == FuelDesign.CANDU_37 && l.burnup() <= 0 && l.pu() <= 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final List<BlockPos> pumps = new ArrayList<>(), generators = new ArrayList<>();
    private CanduReactor.Trip lastTrip = CanduReactor.Trip.MANUAL;
    private int ticks;
    /** Магазин перегрузочной машины: свежие пучки (предмет — один пучок), их уран на пучок и доли U-235, U-234. */
    private int magazine;
    private double magKg, magU235, magU234;

    public CanduReactorBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.CANDU_REACTOR.get(), pos, state);
    }

    public CanduReactor reactor() {
        return reactor;
    }

    private void link(ServerLevel sl) {
        pumps.clear();
        generators.clear();
        AABB box = new AABB(worldPosition).inflate(LINK_RADIUS);
        for (PrimaryPumpBlockEntity p : Ironworks.blockEntities(sl, box, PrimaryPumpBlockEntity.class)) pumps.add(p.getBlockPos());
        for (SteamGeneratorBlockEntity g : Ironworks.blockEntities(sl, box, SteamGeneratorBlockEntity.class)) generators.add(g.getBlockPos());
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        SimLevelBinding b = SimBindings.get(sl);
        if (b == null) return;
        if (ticks++ % 20 == 0) link(sl);
        double flow = 0;
        for (BlockPos p : pumps) if (level.getBlockEntity(p) instanceof PrimaryPumpBlockEntity pump) flow += pump.flowShare();
        reactor.setFlow(flow);
        AutomationUnit a = MechanismWiring.unit(level, simId(), AutomationKind.REACTOR_IO);
        reactor.setExternalTrip(a != null && a.loadEnergized(1));
        reactor.step(CanduReactor.DT * b.world().clock().kSlow());
        List<SteamGeneratorBlockEntity> sgs = new ArrayList<>();
        for (BlockPos p : generators)
            if (level.getBlockEntity(p) instanceof SteamGeneratorBlockEntity sg && !Double.isNaN(sg.secondaryK())) sgs.add(sg);
        double q = reactor.steamGeneratorW();
        for (SteamGeneratorBlockEntity sg : sgs) sg.deliver(q / sgs.size());
        if (a != null) {
            a.setInput(AutomationUnit.IN_MAIN, reactor.trip() != CanduReactor.Trip.NONE ? 1 : 0);
            a.setInput(AutomationUnit.IN_AUX, reactor.power());
        }
        if (ticks % 20 == 0)
            RadiationService.source(sl, worldPosition.getCenter().add(0, 1, 0),
                    FissionSource.reactor(reactor.core().fissionW(), reactor.core().decayHeatW()));
        CanduReactor.Trip t = reactor.trip();
        if (t != CanduReactor.Trip.NONE && lastTrip == CanduReactor.Trip.NONE) {
            sl.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 0.6f);
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 48 * 48))
                p.displayClientMessage(NppText.msg("scram", net.minecraft.network.chat.Component.translatable(
                        "panel.promptcritical.npp.trip." + t.name().toLowerCase(java.util.Locale.ROOT))).withStyle(ChatFormatting.RED), false);
        }
        lastTrip = t;
        takeBundle();
        if (ticks % 100 == 0) setChanged();
    }

    /** Пучок из слота — в магазин машины (до 24 штук); состав магазина усредняется. */
    private void takeBundle() {
        ItemStack s = fuel.getStackInSlot(CanduPanels.SLOT_FRESH);
        if (s.isEmpty() || magazine >= 24) return;
        FuelLot l = FuelLot.of(s);
        double n = magazine;
        magKg = (magKg * n + l.uKg()) / (n + 1);
        magU235 = (magU235 * n + l.u235()) / (n + 1);
        magU234 = (magU234 * n + l.u234()) / (n + 1);
        magazine++;
        fuel.setStackInSlot(CanduPanels.SLOT_FRESH, ItemStack.EMPTY);
        setChanged();
    }

    public int magazine() {
        return magazine;
    }

    /** Кнопка перегрузочной машины. */
    private void refuel(@Nullable ServerPlayer player) {
        if (reactor.shutDown()) {
            for (int ch = 0; ch < CanduReactor.CHANNELS; ch++) {
                if (reactor.core().loaded(ch)) continue;
                if (magazine < CanduReactor.BUNDLES_PER_CHANNEL) return;
                if (reactor.loadChannel(ch, magKg, magU235, magU234)) {
                    magazine -= CanduReactor.BUNDLES_PER_CHANNEL;
                    if (player != null) player.displayClientMessage(NppText.msg("candu_refuelled", ch + 1), true);
                    setChanged();
                }
                return;
            }
        }
        // на ходу (или зона полна): сдвиг 8 пучков в самый выгоревший канал; выгруженные — одним пеналом
        if (magazine < CanduReactor.SHIFT_BUNDLES || !fuel.getStackInSlot(CanduPanels.SLOT_SPENT).isEmpty()) return;
        int ch = reactor.mostBurntChannel();
        if (ch < 0) return;
        LightWaterCore.AssemblyFuel out = reactor.refuel(ch, CanduReactor.SHIFT_BUNDLES, magKg, magU235, magU234);
        magazine -= CanduReactor.SHIFT_BUNDLES;
        if (out != null) {
            double u = out.uraniumKg(), pu = out.plutoniumKg();
            FuelDesign d = FuelDesign.CANDU_37;
            FuelLot lot = new FuelLot(u, out.u235Kg() / u, out.u234() * out.hm0Kg() / u, 0.96, d.rods * CanduReactor.SHIFT_BUNDLES, d.name(), "",
                    out.burnupMwdKg(), out.u236Kg() / u, pu, pu > 0 ? out.pu239Kg() / pu : 0, pu > 0 ? out.pu240Kg() / pu : 0, 0, 0);
            fuel.setStackInSlot(CanduPanels.SLOT_SPENT, lot.on(new ItemStack(PcItems.FUEL_ASSEMBLY.get())));
        }
        if (player != null) player.displayClientMessage(NppText.msg("candu_refuelled", ch + 1), true);
        setChanged();
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return fuel;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return fuel;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return fuel.isItemValid(CanduPanels.SLOT_FRESH, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack left = fuel.insertItem(CanduPanels.SLOT_FRESH, stack.copy(), false);
        int moved = stack.getCount() - left.getCount();
        if (moved == 0) return ItemInteractionResult.FAIL;
        if (!player.isCreative()) stack.shrink(moved);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        reactor.scram();
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return CanduPanels.REACTOR;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        CanduReactor r = reactor;
        v[CanduPanels.POWER] = (int) Math.round(Math.min(r.power(), 9.9) * 1000);
        double t = r.core().periodS();
        v[CanduPanels.PERIOD] = t > 0 && t < 1000 ? (int) Math.round(t * 10) : 0;
        v[CanduPanels.RHO_PCM] = (int) Math.round(r.core().reactivity() * 1e5);
        v[CanduPanels.SHUTOFF] = (int) Math.round((1 - r.shutoffPosition()) * 1000);
        v[CanduPanels.CONTROL] = (int) Math.round((1 - r.controlPosition()) * 1000);
        v[CanduPanels.OUT_C] = (int) Math.round(r.outletK() - 273.15);
        v[CanduPanels.FLOW] = (int) Math.round(r.flowFraction() * 100);
        v[CanduPanels.FUEL_C] = (int) Math.round(r.fuelK() - 273.15);
        v[CanduPanels.XE_PCM] = (int) Math.round(r.core().xenonReactivity(CanduReactor.MODERATOR_K) * 1e5);
        v[CanduPanels.LOADED] = r.core().loadedAssemblies();
        v[CanduPanels.BURNUP] = (int) Math.round(r.core().maxBurnup() * 100);
        v[CanduPanels.REFUELLED] = (int) Math.round(r.refuelledBundles());
        v[CanduPanels.TRITIUM_GBQ] = (int) Math.min(Integer.MAX_VALUE, Math.round(r.tritiumBq() / 1e9));
        v[CanduPanels.TRIP] = r.trip().ordinal();
        v[CanduPanels.MAGAZINE] = magazine;
        v[CanduPanels.FLAGS] = (r.trip() != CanduReactor.Trip.NONE ? 1 << CanduPanels.F_TRIPPED : 0)
                | (generators.isEmpty() ? 1 << CanduPanels.F_NO_SG : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case CanduPanels.B_SCRAM -> reactor.scram();
            case CanduPanels.B_RESET -> player.displayClientMessage(NppText.msg(reactor.resetTrip() ? "reset" : "reset_refused"), true);
            case CanduPanels.B_UP -> reactor.driveControl(-1);
            case CanduPanels.B_STOP -> reactor.driveControl(0);
            case CanduPanels.B_DOWN -> reactor.driveControl(1);
            case CanduPanels.B_REFUEL -> refuel(player);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    public BlockPos wiredPos() {
        return worldPosition;
    }

    @Override
    public AutomationKind wiredKind() {
        return AutomationKind.REACTOR_IO;
    }

    @Override
    public Direction wiredFacing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock bl ? bl.facing(s) : Direction.NORTH;
    }

    @Override
    public List<WiredDevice.Wire> wires() {
        return wiring.wires();
    }

    @Override
    public void wiresChanged() {
        MechanismWiring.changed(this);
    }

    @Override
    @Nullable
    public AutomationUnit automationUnit(AutomationSubsystem automation) {
        return simId().isNone() ? null : automation.unit(simId(), AutomationKind.REACTOR_IO);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        wiring.onLoad(this, this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        wiring.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        wiring.setRemoved(this, this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        wiring.save(tag);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fuel", fuel.serializeNBT(registries));
        MutableStateTree tree = new MutableStateTree();
        reactor.save(new TreeStateWriter(tree));
        tag.put("reactor", NbtStateTrees.toNbt(tree));
        tag.putInt("magazine", magazine);
        tag.putDouble("mag_kg", magKg);
        tag.putDouble("mag_u235", magU235);
        tag.putDouble("mag_u234", magU234);
        wiring.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("reactor")) reactor.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("reactor"))));
        magazine = tag.getInt("magazine");
        magKg = tag.getDouble("mag_kg");
        magU235 = tag.getDouble("mag_u235");
        magU234 = tag.getDouble("mag_u234");
        wiring.load(tag);
        lastTrip = reactor.trip();
    }
}
