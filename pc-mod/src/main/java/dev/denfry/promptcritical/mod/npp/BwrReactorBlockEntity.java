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
import dev.denfry.promptcritical.mod.powerhouse.SteamTurbineBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationSubsystem;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.npp.BoilingWaterReactor;
import dev.denfry.promptcritical.sim.npp.LightWaterCore;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.radiation.FissionSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
 * Кипящий реактор T2b ({@link BoilingWaterReactor}) 3×6×3 с пультом на контроллере. В {@value #LINK_RADIUS} блоках —
 * насосы рециркуляции ({@link PrimaryPumpBlockEntity}: вал держит обороты, выбег на маховике считает реактор) и
 * барабаны-сепараторы ({@link SteamGeneratorBlockEntity} у барабана водотрубного котла): пар корпуса отдаётся в барабан,
 * откуда идёт в существующие турбины. Барабан здесь — паровой объём корпуса, а не второй контур: по нему реактор знает,
 * есть ли отбор пара. N-16 из пара светит на паровых турбинах ({@link SteamTurbineBlockEntity}) в радиусе
 * {@value #TURBINE_RADIUS} блоков. Клеммы СУЗ — как у T1/T2a.
 */
public final class BwrReactorBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost, WiredDevice {
    public static final int LINK_RADIUS = 12, TURBINE_RADIUS = 24;

    private final BoilingWaterReactor reactor = new BoilingWaterReactor();
    private final MechanismWiring wiring = new MechanismWiring();
    private final ItemStackHandler fuel = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot != BwrPanels.SLOT_FRESH || !stack.is(PcItems.FUEL_ASSEMBLY.get())) return false;
            FuelLot l = FuelLot.of(stack);
            FuelDesign d = l.designOrNull();
            return d != null && LightWaterCore.accepts(d) && l.burnup() <= 0 && l.pu() <= 0
                    && (reactor.core().design() == null || reactor.core().design() == d);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final List<BlockPos> pumps = new ArrayList<>(), drums = new ArrayList<>(), turbines = new ArrayList<>();
    private BoilingWaterReactor.Trip lastTrip = BoilingWaterReactor.Trip.MANUAL;
    private int ticks;

    public BwrReactorBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.BWR_REACTOR.get(), pos, state);
    }

    public BoilingWaterReactor reactor() {
        return reactor;
    }

    public ItemStackHandler fuelSlots() {
        return fuel;
    }

    private void link(ServerLevel sl) {
        pumps.clear();
        drums.clear();
        turbines.clear();
        AABB box = new AABB(worldPosition).inflate(LINK_RADIUS);
        for (PrimaryPumpBlockEntity p : Ironworks.blockEntities(sl, box, PrimaryPumpBlockEntity.class))
            if (pumps.size() < BoilingWaterReactor.PUMPS) pumps.add(p.getBlockPos());
        for (SteamGeneratorBlockEntity g : Ironworks.blockEntities(sl, box, SteamGeneratorBlockEntity.class)) drums.add(g.getBlockPos());
        for (SteamTurbineBlockEntity t : Ironworks.blockEntities(sl, new AABB(worldPosition).inflate(TURBINE_RADIUS), SteamTurbineBlockEntity.class))
            turbines.add(t.getBlockPos());
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        SimLevelBinding b = SimBindings.get(sl);
        if (b == null) return;
        if (ticks++ % 20 == 0) link(sl);
        for (int i = 0; i < BoilingWaterReactor.PUMPS; i++) {
            double drive = 0;
            if (i < pumps.size() && level.getBlockEntity(pumps.get(i)) instanceof PrimaryPumpBlockEntity p) drive = p.drive();
            reactor.setPumpDrive(i, drive);
        }
        List<SteamGeneratorBlockEntity> out = new ArrayList<>();
        for (BlockPos p : drums)
            if (level.getBlockEntity(p) instanceof SteamGeneratorBlockEntity sg && !Double.isNaN(sg.secondaryK())) out.add(sg);
        reactor.setTurbineAvailable(!out.isEmpty());
        AutomationUnit a = MechanismWiring.unit(level, simId(), AutomationKind.REACTOR_IO);
        reactor.setExternalTrip(a != null && a.loadEnergized(1));
        reactor.step(BoilingWaterReactor.DT * b.world().clock().kSlow());
        // пар корпуса — в барабаны: тепло, которое он несёт сверх питательной воды
        double steamW = reactor.steamKgS() * 1.85e6;
        for (SteamGeneratorBlockEntity sg : out) sg.deliver(steamW / out.size());
        if (a != null) {
            a.setInput(AutomationUnit.IN_MAIN, reactor.trip() != BoilingWaterReactor.Trip.NONE ? 1 : 0);
            a.setInput(AutomationUnit.IN_AUX, reactor.power());
        }
        if (ticks % 20 == 0) {
            RadiationService.source(sl, worldPosition.getCenter().add(0, 1, 0),
                    FissionSource.reactor(reactor.core().fissionW(), reactor.core().decayHeatW()));
            double n16 = reactor.turbineN16Bq();
            if (n16 > 0 && !turbines.isEmpty())
                for (BlockPos t : turbines) RadiationService.source(sl, t.getCenter(), Emitter.N16.source(n16 / turbines.size()));
        }
        events(sl);
        if (reactor.shutDown()) loadFresh();
        if (ticks % 100 == 0) setChanged();
    }

    private void events(ServerLevel sl) {
        BoilingWaterReactor.Trip t = reactor.trip();
        if (t != BoilingWaterReactor.Trip.NONE && lastTrip == BoilingWaterReactor.Trip.NONE) {
            sl.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 0.6f);
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 48 * 48))
                p.displayClientMessage(NppText.msg("scram", net.minecraft.network.chat.Component.translatable(
                        "panel.promptcritical.bwr.reason." + t.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.RED), false);
        }
        lastTrip = t;
    }

    private void loadFresh() {
        ItemStack s = fuel.getStackInSlot(BwrPanels.SLOT_FRESH);
        if (s.isEmpty()) return;
        FuelLot l = FuelLot.of(s);
        FuelDesign d = l.designOrNull();
        if (d == null) return;
        for (int pos = 0; pos < BoilingWaterReactor.POSITIONS; pos++) {
            if (reactor.core().loaded(pos)) continue;
            if (reactor.load(pos, d, l.uKg(), l.u235(), l.u234())) {
                s.shrink(1);
                fuel.setStackInSlot(BwrPanels.SLOT_FRESH, s);
                setChanged();
            }
            return;
        }
    }

    private void unload(@Nullable ServerPlayer player) {
        if (!reactor.shutDown()) {
            if (player != null) player.displayClientMessage(NppText.msg("not_shut_down"), true);
            return;
        }
        if (!fuel.getStackInSlot(BwrPanels.SLOT_SPENT).isEmpty()) return;
        int best = -1;
        for (int pos = 0; pos < BoilingWaterReactor.POSITIONS; pos++)
            if (reactor.core().loaded(pos) && (best < 0 || reactor.core().burnup(pos) > reactor.core().burnup(best))) best = pos;
        if (best < 0) return;
        LightWaterCore.AssemblyFuel f = reactor.unload(best);
        if (f == null) return;
        double u = f.uraniumKg(), pu = f.plutoniumKg();
        FuelDesign d = f.design();
        FuelLot lot = new FuelLot(u, f.u235Kg() / u, f.u234() * f.hm0Kg() / u, 0.96, d.rods, d.name(), "", f.burnupMwdKg(), f.u236Kg() / u,
                pu, pu > 0 ? f.pu239Kg() / pu : 0, pu > 0 ? f.pu240Kg() / pu : 0, 0, 0);
        fuel.setStackInSlot(BwrPanels.SLOT_SPENT, lot.on(new ItemStack(PcItems.FUEL_ASSEMBLY.get())));
        if (player != null)
            player.displayClientMessage(NppText.msg("pwr_unloaded", best + 1, String.format(Locale.ROOT, "%.2f", f.burnupMwdKg())), true);
        setChanged();
    }

    // --- руки и щит ---

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
        return fuel.isItemValid(BwrPanels.SLOT_FRESH, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack left = fuel.insertItem(BwrPanels.SLOT_FRESH, stack.copy(), false);
        int moved = stack.getCount() - left.getCount();
        if (moved == 0) return ItemInteractionResult.FAIL;
        if (!player.isCreative()) stack.shrink(moved);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        reactor.scramButton();
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return BwrPanels.REACTOR;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        BoilingWaterReactor r = reactor;
        v[BwrPanels.POWER] = (int) Math.round(Math.min(r.power(), 9.9) * 1000);
        double t = r.core().periodS();
        v[BwrPanels.PERIOD] = t > 0 && t < 1000 ? (int) Math.round(t * 10) : 0;
        v[BwrPanels.RHO_PCM] = (int) Math.round(r.core().reactivity() * 1e5);
        v[BwrPanels.SCRAM_ROD] = (int) Math.round((1 - r.scramPosition()) * 1000);
        v[BwrPanels.CONTROL_ROD] = (int) Math.round((1 - r.controlPosition()) * 1000);
        v[BwrPanels.PRESSURE] = (int) Math.round(r.pressure() / 1e4);
        v[BwrPanels.LEVEL] = (int) Math.round(r.level() * 1000);
        v[BwrPanels.FLOW] = (int) Math.round(r.flowFraction() * 100);
        v[BwrPanels.VOID] = (int) Math.round(r.voidFraction() * 100);
        v[BwrPanels.STEAM] = (int) Math.round(r.steamKgS() * 10);
        v[BwrPanels.FUEL_C] = (int) Math.round(r.fuelK() - 273.15);
        v[BwrPanels.XE_PCM] = (int) Math.round(r.core().xenonReactivity(558) * 1e5);
        v[BwrPanels.LOADED] = r.core().loadedAssemblies();
        v[BwrPanels.BURNUP] = (int) Math.round(r.core().maxBurnup() * 100);
        v[BwrPanels.TRIP] = r.trip().ordinal();
        v[BwrPanels.N16_GBQ] = (int) Math.round(r.turbineN16Bq() / 1e9);
        v[BwrPanels.FLAGS] = (r.trip() != BoilingWaterReactor.Trip.NONE ? 1 << BwrPanels.F_TRIPPED : 0)
                | (drums.isEmpty() ? 1 << BwrPanels.F_NO_TURBINE : 0)
                | (r.reliefKgS() > 0 ? 1 << BwrPanels.F_RELIEF : 0)
                | (r.controlDrive() < 0 ? 1 << BwrPanels.F_UP : 0) | (r.controlDrive() > 0 ? 1 << BwrPanels.F_DOWN : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case BwrPanels.B_SCRAM -> reactor.scramButton();
            case BwrPanels.B_RESET -> player.displayClientMessage(NppText.msg(reactor.resetTrip() ? "reset" : "reset_refused"), true);
            case BwrPanels.B_UP -> reactor.driveControl(-1);
            case BwrPanels.B_STOP -> reactor.driveControl(0);
            case BwrPanels.B_DOWN -> reactor.driveControl(1);
            case BwrPanels.B_UNLOAD -> unload(player);
            default -> {
            }
        }
        setChanged();
    }

    // --- клеммы ---

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
        wiring.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("reactor")) reactor.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("reactor"))));
        wiring.load(tag);
        lastTrip = reactor.trip();
    }
}
