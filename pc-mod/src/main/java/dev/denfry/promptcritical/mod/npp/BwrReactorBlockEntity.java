package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.boiler.BoilerSounds;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
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
import dev.denfry.promptcritical.sim.accident.PlantAccident;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *
 * <p>Аварии ({@link ReactorAccidentSystems}, {@link PlantAccident#BWR_T2B}): сорванный насос рециркуляции — разрыв
 * всасывающего трубопровода, кнопка «LOCA» — учебное событие, без вращения валов насосов — обесточивание. Насосы САОЗ
 * здесь — HPCS и LPCI, гидроёмкостей у кипящего нет, есть АДС; гермооболочка с бассейном-барботёром инертирована азотом.
 */
public final class BwrReactorBlockEntity extends DeviceBlockEntity implements dev.denfry.promptcritical.mod.environment.SelfEmittingSource, FoundryDevice, PanelHost, WiredDevice, dev.denfry.promptcritical.mod.client.whole.WholeModelHost {
    public static final int LINK_RADIUS = 12, TURBINE_RADIUS = 24;
    /** Реальный масштаб: насосы рециркуляции и барабан в десятке метров от корпуса, турбина — в соседнем зале. */
    public static final int LINK_RADIUS_REAL = 24, TURBINE_RADIUS_REAL = 64;
    /** Доля номинальной мощности, с которой реактор показан работающим (огни пульта, гул). */
    private static final double AT_POWER = 0.005;

    private final BoilingWaterReactor reactor = new BoilingWaterReactor();
    private final MechanismWiring wiring = new MechanismWiring();
    private final ItemStackHandler fuel = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot != BwrPanels.SLOT_FRESH || !stack.is(PcItems.FUEL_ASSEMBLY.get())) return false;
            FuelLot l = FuelLot.of(stack);
            FuelDesign d = l.designOrNull();
            return d != null && LightWaterCore.accepts(d) && l.freshThermal()
                    && (reactor.core().design() == null || reactor.core().design() == d);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final List<BlockPos> pumps = new ArrayList<>(), drums = new ArrayList<>(), turbines = new ArrayList<>();
    private BoilingWaterReactor.Trip lastTrip = BoilingWaterReactor.Trip.MANUAL;
    private final ReactorAccidentSystems accident = new ReactorAccidentSystems(PlantAccident.BWR_T2B);
    private int ticks;
    /** Непрерывная работа без автоматической АЗ (11 §11.2.2 {@code hundred_days}). */
    private final dev.denfry.promptcritical.sim.reactor.ContinuousRun run = new dev.denfry.promptcritical.sim.reactor.ContinuousRun();
    /** Следование суточному графику (11 §11.2.2 {@code load_follow}). */
    private final dev.denfry.promptcritical.sim.reactor.LoadFollowWatch follow = new dev.denfry.promptcritical.sim.reactor.LoadFollowWatch();

    public BwrReactorBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.BWR_REACTOR.get(), pos, state);
    }

    public BoilingWaterReactor reactor() {
        return reactor;
    }

    public ItemStackHandler fuelSlots() {
        return fuel;
    }

    public ReactorAccidentSystems accident() {
        return accident;
    }

    private void link(ServerLevel sl) {
        pumps.clear();
        drums.clear();
        turbines.clear();
        boolean real = wholeModelId() != null;
        AABB box = new AABB(worldPosition).inflate(real ? LINK_RADIUS_REAL : LINK_RADIUS);
        for (PrimaryPumpBlockEntity p : Ironworks.blockEntities(sl, box, PrimaryPumpBlockEntity.class))
            if (pumps.size() < BoilingWaterReactor.PUMPS) pumps.add(p.getBlockPos());
        for (SteamGeneratorBlockEntity g : Ironworks.blockEntities(sl, box, SteamGeneratorBlockEntity.class)) drums.add(g.getBlockPos());
        for (SteamTurbineBlockEntity t : Ironworks.blockEntities(sl, new AABB(worldPosition).inflate(real ? TURBINE_RADIUS_REAL : TURBINE_RADIUS), SteamTurbineBlockEntity.class))
            turbines.add(t.getBlockPos());
        Map<BlockPos, ReactorAccidentSystems.LoopPart> loop = new LinkedHashMap<>();
        for (BlockPos p : pumps) loop.put(p, ReactorAccidentSystems.LoopPart.PUMP);
        accident.link(sl, worldPosition, loop, reactor.core().loadedAssemblies() > 0);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) {
            clientTick();
            return;
        }
        SimLevelBinding b = SimBindings.get(sl);
        if (b == null) return;
        if (ticks++ % 20 == 0) link(sl);
        boolean pumpsDriven = false;
        for (int i = 0; i < BoilingWaterReactor.PUMPS; i++) {
            double drive = 0;
            if (i < pumps.size() && level.getBlockEntity(pumps.get(i)) instanceof PrimaryPumpBlockEntity p) drive = p.drive();
            reactor.setPumpDrive(i, drive);
            pumpsDriven |= drive > 0.5;
        }
        List<SteamGeneratorBlockEntity> out = new ArrayList<>();
        for (BlockPos p : drums)
            if (level.getBlockEntity(p) instanceof SteamGeneratorBlockEntity sg && !Double.isNaN(sg.secondaryK())) out.add(sg);
        reactor.setTurbineAvailable(!out.isEmpty());
        AutomationUnit a = MechanismWiring.unit(level, simId(), AutomationKind.REACTOR_IO);
        reactor.setExternalTrip(a != null && a.loadEnergized(1) || accident.demandsTrip());
        double gameDt = BoilingWaterReactor.DT * b.world().clock().kSlow();
        reactor.step(gameDt);
        if (run.update(gameDt, reactor.power(), reactor.trip() != BoilingWaterReactor.Trip.NONE && reactor.trip() != BoilingWaterReactor.Trip.MANUAL))
            dev.denfry.promptcritical.mod.content.SimAchievements.nearby(sl, worldPosition, dev.denfry.promptcritical.mod.content.SimAchievements.HUNDRED_DAYS, "days", run.days());
        if (reactor.power() >= dev.denfry.promptcritical.mod.content.IndustrialComplex.REACTOR_POWER) dev.denfry.promptcritical.mod.content.IndustrialComplex.running(sl, worldPosition, dev.denfry.promptcritical.mod.content.IndustrialComplex.Part.REACTOR);
        if (follow.update(gameDt, dev.denfry.promptcritical.mod.grid.LoadProfiles.shape(sl), reactor.power(), reactor.trip() != BoilingWaterReactor.Trip.NONE))
            dev.denfry.promptcritical.mod.content.SimAchievements.nearby(sl, worldPosition, dev.denfry.promptcritical.mod.content.SimAchievements.LOAD_FOLLOW, "hours", follow.hours());
        // пар корпуса — в барабаны: тепло, которое он несёт сверх питательной воды
        double steamW = reactor.steamKgS() * 1.85e6;
        for (SteamGeneratorBlockEntity sg : out) sg.deliver(steamW / out.size());
        if (a != null) {
            a.setInput(AutomationUnit.IN_MAIN, reactor.trip() != BoilingWaterReactor.Trip.NONE ? 1 : 0);
            a.setInput(AutomationUnit.IN_AUX, reactor.power());
        }
        if (ticks % 100 == 1) accident.scan(sl, worldPosition);
        // слой аварии: тепло в топливо — от зоны, сток — пар в барабаны; разогрев разгерметизирует горячие ТВС
        int breach = accident.step(sl, b, simId(), worldPosition, gameDt, reactor.core().heatW(), out.isEmpty() ? 0 : steamW,
                reactor.pressure(), pumpsDriven, reactor.core().loadedAssemblies(), reactor.core().failedAssemblies());
        if (breach > 0 && reactor.core().failHottest(breach) > 0) {
            sl.playSound(null, worldPosition, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1f, 0.4f);
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 64 * 64))
                p.displayClientMessage(NppText.msg("accident_clad", reactor.core().failedAssemblies()).withStyle(ChatFormatting.DARK_RED), false);
        }
        accident.radiation(sl, worldPosition, reactor.core().failedAssemblies());
        if (ticks % 20 == 0) {
            var src = FissionSource.reactor(reactor.core().fissionW(), reactor.core().decayHeatW());
            int failed = reactor.core().failedAssemblies();
            PlantAccident acc = accident.accident();
            if (failed > 0 && (acc.breakFlowKgS() > 0 || acc.boilOffKgS() > 0 || acc.adsOpen()))
                src = src.plus(Emitter.I131.source(failed * ReactorAccidentSystems.I131_PER_FAILED_BQ));
            RadiationService.source(sl, worldPosition.getCenter().add(0, 1, 0), src);
            double n16 = reactor.turbineN16Bq();
            if (n16 > 0 && !turbines.isEmpty())
                for (BlockPos t : turbines) RadiationService.source(sl, t.getCenter(), Emitter.N16.source(n16 / turbines.size()));
        }
        events(sl);
        if (ticks % 10 == 0) {
            setStateFlag(BlockStateProperties.LIT, reactor.power() > AT_POWER);
            setStateFlag(BlockStateProperties.OPEN, reactor.reliefKgS() > 0);
        }
        if (reactor.shutDown()) loadFresh();
        if (ticks % 100 == 0) setChanged();
    }

    private void setStateFlag(BooleanProperty prop, boolean value) {
        BlockState s = getBlockState();
        if (s.hasProperty(prop) && s.getValue(prop) != value) level.setBlock(worldPosition, s.setValue(prop, value), Block.UPDATE_ALL);
    }

    /**
     * Сброс пара предохранительными клапанами: по выхлопным трубам в бассейн-барботёр, где пар конденсируется
     * (гермооболочки BWR Mark I–III, T-образные гасители): над гасителями у пола клокочет вода, клапаны шипят. Точки — art/blockbench/core/bwr_reactor.author.js.
     */
    private void clientTick() {
        BlockState s = getBlockState();
        if (!s.hasProperty(BlockStateProperties.OPEN) || !s.getValue(BlockStateProperties.OPEN)) return;
        MachineFx fx = MachineFx.of(level, worldPosition, s.getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (fx == null) return;
        if (wholeModelId() != null) {
            // реальный масштаб: клапан на паровом патрубке (art/blockbench/core/bwr/bwr240_reactor.author.js), сброс уходит вниз
            Vec3 valve = fx.at(8, 184, 41);
            if (fx.chance(3)) fx.puff(valve, new Vec3(0, 0.04, 0), 1);
            if (fx.chance(0.6)) fx.sound(valve, BoilerSounds.steam(), 0.7f, 0.8f);
            return;
        }
        for (double x : new double[] {-6, 22}) {
            Vec3 quencher = fx.at(x, -12, 25);
            if (fx.chance(8)) fx.bubbles(quencher, 0.3, 2);
            if (fx.chance(3)) fx.puff(quencher, new Vec3(0, 0.02, 0), 1);
            if (fx.chance(0.6)) fx.sound(fx.at(x, 61, 25), BoilerSounds.steam(), 0.7f, 0.8f);
        }
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
            if (reactor.load(pos, d, l.uKg(), l.u235(), l.u234(), l.plutonium())) {
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
        FuelDesign d = f.design();
        FuelLot lot = FuelLot.irradiated(d, 0.96, d.rods, f.uraniumKg(), f.u235Kg(), f.u234() * f.hm0Kg(), f.u236Kg(), f.pu239Kg(),
                f.pu240Kg(), f.pu241Kg(), f.pu242Kg(), f.burnupMwdKg());
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
        PlantAccident acc = accident.accident();
        v[BwrPanels.PRESSURE] = (int) Math.round((acc.active() ? acc.pressurePa() : r.pressure()) / 1e4);
        v[BwrPanels.LEVEL] = (int) Math.round((acc.active() ? Math.min(r.level(), acc.levelFraction()) : r.level()) * 1000);
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
                | (r.reliefKgS() > 0 || acc.adsOpen() ? 1 << BwrPanels.F_RELIEF : 0)
                | (r.controlDrive() < 0 ? 1 << BwrPanels.F_UP : 0) | (r.controlDrive() > 0 ? 1 << BwrPanels.F_DOWN : 0);
        accident.readPanel(v, BwrPanels.ACCIDENT);
    }

    /** Сброс АЗ и байпас — право {@code PROTECTION} станции (04d §4d.9.2). */
    @Override
    public dev.denfry.promptcritical.sim.journal.Permission requiredPermission(int id) {
        return id == BwrPanels.B_RESET ? dev.denfry.promptcritical.sim.journal.Permission.PROTECTION : dev.denfry.promptcritical.sim.journal.Permission.CONTROL;
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
            case BwrPanels.B_LOCA -> accident.cycleDrill(player);
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
        tag.putDouble("run_s", run.seconds());
        tag.put("fuel", fuel.serializeNBT(registries));
        MutableStateTree tree = new MutableStateTree();
        reactor.save(new TreeStateWriter(tree));
        tag.put("reactor", NbtStateTrees.toNbt(tree));
        accident.save(tag);
        wiring.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        run.restore(tag.getDouble("run_s"));
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("reactor")) reactor.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("reactor"))));
        accident.load(tag);
        wiring.load(tag);
        lastTrip = reactor.trip();
    }

    // --- цельная модель реального масштаба (спецификация real-scale machines) ---

    @Override
    public String wholeModelId() {
        return dev.denfry.promptcritical.mod.client.whole.WholeModelHost.idOf(getBlockState());
    }

    @Override
    public net.minecraft.core.Direction wholeModelFacing() {
        return getBlockState().getBlock() instanceof dev.denfry.promptcritical.mod.block.PcBlock pc ? pc.facing(getBlockState()) : net.minecraft.core.Direction.NORTH;
    }

    @Override
    public net.minecraft.core.BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing(), 3).above();
    }
}
