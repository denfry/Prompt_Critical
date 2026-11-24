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
import dev.denfry.promptcritical.mod.heavywater.GradedWaterPort;
import dev.denfry.promptcritical.mod.heavywater.HeavyWaterFluids;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.nuclear.FuelLot;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.accident.PlantAccident;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationSubsystem;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.heavywater.HeavyWater;
import dev.denfry.promptcritical.sim.heavywater.HeavyWaterGrade;
import dev.denfry.promptcritical.sim.npp.LightWaterCore;
import dev.denfry.promptcritical.sim.npp.NpdChannels;
import dev.denfry.promptcritical.sim.npp.NpdFuellingMachine;
import dev.denfry.promptcritical.sim.npp.NpdReactor;
import dev.denfry.promptcritical.sim.radiation.FissionSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * CANDU NPD реального масштаба ({@link NpdReactor}): каландр 7×7×7 с пультом на контроллере. В {@value #LINK_RADIUS}
 * блоках — насосы теплоносителя ({@link PrimaryPumpBlockEntity}), котлы ({@link SteamGeneratorBlockEntity}) и
 * перегрузочные машины ({@link NpdFuellingMachineBlockEntity}): перегрузка идёт, только если машина стоит у каждого
 * торца, обе запитаны и на обе головки подаётся D₂O.
 *
 * <p>Тяжёлая вода — по трубам: в коллекторы теплоносителя (боковые патрубки) — в контур; снизу — в бак сброса
 * замедлителя, откуда её поднимают насосы; сверху — слив из баков слива по сортам. Свежие пучки
 * {@link FuelDesign#NPD_19} из слота щита уходят в лоток (до {@value #BAY}); машины берут из него до восьми. Выгоревшие
 * из приёмного магазина выдаются одним пеналом, когда машины на стоянке и слот выдачи пуст.
 */
public final class NpdReactorBlockEntity extends DeviceBlockEntity implements dev.denfry.promptcritical.mod.environment.SelfEmittingSource,
        FoundryDevice, PanelHost, WiredDevice, dev.denfry.promptcritical.mod.client.whole.WholeModelHost {
    public static final int LINK_RADIUS = 24, BAY = 27, DRAIN_MB = 4000, DRAIN_STEP_L = 1000;

    private final NpdReactor reactor = NpdReactor.dry();
    private final NpdFuellingMachine machine = new NpdFuellingMachine();
    private final ProductTank[] drains = new ProductTank[HeavyWaterGrade.values().length];
    private final GradedWaterPort moderatorPort, coolantPort;
    private final MechanismWiring wiring = new MechanismWiring();
    private final ItemStackHandler fuel = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot != NpdPanels.SLOT_FRESH || !stack.is(PcItems.FUEL_ASSEMBLY.get())) return false;
            FuelLot l = FuelLot.of(stack);
            return l.designOrNull() == FuelDesign.NPD_19 && l.freshThermal() && l.plutonium().totalKg() <= 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final List<BlockPos> pumps = new ArrayList<>(), generators = new ArrayList<>(), machines = new ArrayList<>();
    private final ReactorAccidentSystems accident = new ReactorAccidentSystems(PlantAccident.CANDU_T3);
    private NpdReactor.Trip lastTrip = NpdReactor.Trip.MANUAL;
    private NpdFuellingMachine.Fault lastFault = NpdFuellingMachine.Fault.NONE;
    private NpdFuellingMachine.Phase lastPhase = NpdFuellingMachine.Phase.IDLE;
    private int ticks, shift = 1, linkedMachines;
    private double headSupply;
    /** Лоток свежих пучков: число и средний состав (уран на пучок, доли U-235 и U-234). */
    private int bay;
    private double bayKg, bayU235, bayU234;
    /** Для клиента: мощность, положение машин и пробки, фаза. */
    private float viewPower, sentPower = -1;
    private float viewX = (float) NpdFuellingMachine.HOME_X, viewY = (float) NpdFuellingMachine.HOME_Y, viewSnout, viewPlug;
    private int viewChannel = -1, viewPhase, sentPhase = -1, viewPushed, sentPushed;

    public NpdReactorBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.NPD_REACTOR.get(), pos, state);
        for (HeavyWaterGrade g : HeavyWaterGrade.values()) drains[g.ordinal()] = new ProductTank(HeavyWaterFluids.fluid(g), DRAIN_MB);
        EnumSet<HeavyWaterGrade> any = EnumSet.allOf(HeavyWaterGrade.class);
        List<ProductTank> outlets = List.of(drains);
        moderatorPort = new GradedWaterPort(new GradedWaterPort.Sink() {
            @Override
            public double roomKg() {
                return reactor.moderatorCapacityKg() * 1.1 - reactor.moderator().kg() - reactor.dumpTank().kg();
            }

            @Override
            public void add(double kg, double x) {
                reactor.addModerator(kg, x);
                setChanged();
            }

            @Override
            public int shownMb() {
                return (int) HeavyWater.litres(reactor.moderatorCapacityKg(), HeavyWater.REACTOR_GRADE_X);
            }
        }, any, outlets);
        coolantPort = new GradedWaterPort(new GradedWaterPort.Sink() {
            @Override
            public double roomKg() {
                return reactor.coolantCapacityKg() - reactor.coolant().kg();
            }

            @Override
            public void add(double kg, double x) {
                reactor.addCoolant(kg, x);
                setChanged();
            }

            @Override
            public int shownMb() {
                return (int) HeavyWater.litres(reactor.coolantCapacityKg(), HeavyWater.REACTOR_GRADE_X);
            }
        }, any, outlets);
    }

    /** Боковые патрубки — коллекторы теплоносителя, верх и низ — система замедлителя (бак сброса и слив). */
    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return side != null && side.getAxis().isVertical() ? moderatorPort : coolantPort;
    }

    public NpdReactor reactor() {
        return reactor;
    }

    public NpdFuellingMachine machine() {
        return machine;
    }

    public ProductTank drainTank(HeavyWaterGrade g) {
        return drains[g.ordinal()];
    }

    /** Слить {@value #DRAIN_STEP_L} л из бака сброса в бак слива его сорта. */
    public boolean drainDumpTank(@Nullable ServerPlayer player) {
        double x = reactor.dumpTank().purity();
        ProductTank t = drains[HeavyWaterGrade.atMost(x).ordinal()];
        double litres = Math.min(DRAIN_STEP_L, Math.min(t.room(), HeavyWater.litres(reactor.dumpTank().kg(), x)));
        if (litres < 1) return false;
        double kg = reactor.drainDumpTank(HeavyWater.kgFromLitres(litres, x));
        t.add(HeavyWater.litres(kg, x));
        if (player != null) player.displayClientMessage(NpdText.msg("drained", Math.round(litres)), true);
        setChanged();
        return true;
    }

    private void link(ServerLevel sl) {
        pumps.clear();
        generators.clear();
        machines.clear();
        AABB box = new AABB(worldPosition).inflate(LINK_RADIUS);
        for (PrimaryPumpBlockEntity p : Ironworks.blockEntities(sl, box, PrimaryPumpBlockEntity.class)) pumps.add(p.getBlockPos());
        for (SteamGeneratorBlockEntity g : Ironworks.blockEntities(sl, box, SteamGeneratorBlockEntity.class)) generators.add(g.getBlockPos());
        for (NpdFuellingMachineBlockEntity m : Ironworks.blockEntities(sl, box, NpdFuellingMachineBlockEntity.class)) {
            machines.add(m.getBlockPos());
            m.bind(worldPosition);
        }
        java.util.Map<BlockPos, ReactorAccidentSystems.LoopPart> loop = new java.util.LinkedHashMap<>();
        for (BlockPos p : pumps) loop.put(p, ReactorAccidentSystems.LoopPart.PUMP);
        accident.link(sl, worldPosition, loop, reactor.core().loadedAssemblies() > 0 && reactor.coolantFill() > 0.5);
    }

    @Override
    public void tickDevice() {
        if (level != null && level.isClientSide) {
            clientTick();
            return;
        }
        if (!(level instanceof ServerLevel sl)) return;
        SimLevelBinding b = SimBindings.get(sl);
        if (b == null) return;
        if (ticks++ % 20 == 0) link(sl);
        double flow = 0;
        boolean pumpsDriven = false;
        for (BlockPos p : pumps)
            if (level.getBlockEntity(p) instanceof PrimaryPumpBlockEntity pump) {
                flow += pump.flowShare();
                pumpsDriven |= pump.drive() > 0.5;
            }
        reactor.setFlow(flow);
        PlantAccident acc = accident.accident();
        reactor.setCoolantVoid(acc.active() ? 1 - Math.min(1, acc.levelFraction()) : 0);
        AutomationUnit a = MechanismWiring.unit(level, simId(), AutomationKind.REACTOR_IO);
        reactor.setExternalTrip(a != null && a.loadEnergized(1) || accident.demandsTrip());
        double gameDt = NpdReactor.DT * b.world().clock().kSlow();
        reactor.step(gameDt);
        stepMachines();
        List<SteamGeneratorBlockEntity> sgs = new ArrayList<>();
        for (BlockPos p : generators)
            if (level.getBlockEntity(p) instanceof SteamGeneratorBlockEntity sg && !Double.isNaN(sg.secondaryK())) sgs.add(sg);
        double q = reactor.steamGeneratorW();
        for (SteamGeneratorBlockEntity sg : sgs) sg.deliver(q / sgs.size());
        if (ticks % 100 == 1) accident.scan(sl, worldPosition);
        int breach = accident.step(sl, b, simId(), worldPosition, gameDt, reactor.core().fissionW() + reactor.core().decayHeatW(),
                sgs.isEmpty() ? 0 : q, NpdReactor.P_COOLANT, pumpsDriven, reactor.core().loadedAssemblies(),
                reactor.core().failedAssemblies());
        if (breach > 0 && reactor.core().failHottest(breach) > 0)
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 64 * 64))
                p.displayClientMessage(NppText.msg("accident_clad", reactor.core().failedAssemblies()).withStyle(ChatFormatting.DARK_RED), false);
        accident.radiation(sl, worldPosition, reactor.core().failedAssemblies());
        if (a != null) {
            a.setInput(AutomationUnit.IN_MAIN, reactor.trip() != NpdReactor.Trip.NONE ? 1 : 0);
            a.setInput(AutomationUnit.IN_AUX, reactor.power());
        }
        if (ticks % 20 == 0)
            RadiationService.source(sl, worldPosition.getCenter().add(0, 3, 0),
                    FissionSource.reactor(reactor.core().fissionW(), reactor.core().decayHeatW()));
        NpdReactor.Trip t = reactor.trip();
        if (t != NpdReactor.Trip.NONE && lastTrip == NpdReactor.Trip.NONE) {
            sl.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 0.6f);
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 48 * 48))
                p.displayClientMessage(NpdText.msg("dump", Component.translatable(
                        "panel.promptcritical.npd.trip." + t.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.RED), false);
        }
        dev.denfry.promptcritical.mod.station.Stations.tripState(sl, worldPosition, t != NpdReactor.Trip.NONE, t.name()); // журнал станции 04d §4d.8.2
        lastTrip = t;
        takeBundle();
        if (ticks % 100 == 0) setChanged();
        updateView();
    }

    /** Машины: питание и D₂O головок от блоков машин, зарядка магазина из лотка, выдача выгоревших. */
    private void stepMachines() {
        double supply = 1, d2o = 1;
        int n = 0;
        boolean north = false, south = false;
        boolean busy = machine.phase() != NpdFuellingMachine.Phase.IDLE;
        for (BlockPos p : machines) {
            if (!(level.getBlockEntity(p) instanceof NpdFuellingMachineBlockEntity m)) continue;
            n++;
            supply = Math.min(supply, m.supply(busy));
            d2o = Math.min(d2o, m.headD2oFraction());
            if (face(p) == Direction.NORTH) north = true;
            else if (face(p) == Direction.SOUTH) south = true;
        }
        linkedMachines = n;
        boolean both = north && south;
        headSupply = n > 0 ? d2o : 0;
        machine.step(NpdReactor.DT, reactor, both && supply > 0.5, both && d2o > 0.05);
        if (machine.phase() == NpdFuellingMachine.Phase.IDLE && machine.fresh() < NpdFuellingMachine.MAGAZINE && bay > 0) {
            int moved = machine.loadFresh(Math.min(bay, NpdFuellingMachine.MAGAZINE - machine.fresh()), bayKg, bayU235, bayU234);
            bay -= moved;
        }
        if (machine.phase() == NpdFuellingMachine.Phase.IDLE && machine.spentCount() > 0
                && fuel.getStackInSlot(NpdPanels.SLOT_SPENT).isEmpty()) discharge(machine.takeSpent());
        if (lastPhase != NpdFuellingMachine.Phase.IDLE && machine.phase() == NpdFuellingMachine.Phase.IDLE && machine.pushed() > 0)
            say(NpdText.msg("fuelled", NpdChannels.label(Math.max(0, viewChannel))));
        if (machine.fault() != NpdFuellingMachine.Fault.NONE && lastFault == NpdFuellingMachine.Fault.NONE)
            for (ServerPlayer p : ((ServerLevel) level).getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 48 * 48))
                p.displayClientMessage(NpdText.msg("overheat").withStyle(ChatFormatting.DARK_RED), false);
        lastFault = machine.fault();
        lastPhase = machine.phase();
    }

    /** Торец реактора, у которого стоит машина (мировое направление от контроллера по оси каландра). */
    private Direction face(BlockPos p) {
        Direction f = facing();
        int along = (p.getX() - worldPosition.getX()) * f.getStepX() + (p.getZ() - worldPosition.getZ()) * f.getStepZ();
        return along > 0 ? Direction.NORTH : along < 0 ? Direction.SOUTH : Direction.UP;
    }

    private void discharge(List<LightWaterCore.AssemblyFuel> spent) {
        if (spent.isEmpty()) return;
        double u = 0, u235 = 0, u234 = 0, u236 = 0, p9 = 0, p0 = 0, p1 = 0, p2 = 0, bu = 0, hm = 0;
        for (LightWaterCore.AssemblyFuel f : spent) {
            u += f.uraniumKg();
            u235 += f.u235Kg();
            u234 += f.u234() * f.hm0Kg();
            u236 += f.u236Kg();
            p9 += f.pu239Kg();
            p0 += f.pu240Kg();
            p1 += f.pu241Kg();
            p2 += f.pu242Kg();
            bu += f.burnupMwdKg() * f.hm0Kg();
            hm += f.hm0Kg();
        }
        FuelDesign d = FuelDesign.NPD_19;
        FuelLot lot = FuelLot.irradiated(d, 0.96, d.rods * spent.size(), u, u235, u234, u236, p9, p0, p1, p2, hm > 0 ? bu / hm : 0);
        fuel.setStackInSlot(NpdPanels.SLOT_SPENT, lot.on(new ItemStack(PcItems.FUEL_ASSEMBLY.get())));
        setChanged();
    }

    private void updateView() {
        viewPower = (float) Math.max(0, reactor.power());
        viewX = (float) machine.x();
        viewY = (float) machine.y();
        viewSnout = (float) machine.snout();
        viewPlug = (float) machine.plugOut();
        if (machine.channel() >= 0) viewChannel = machine.channel();
        viewPhase = machine.phase().ordinal();
        viewPushed = machine.phase() == NpdFuellingMachine.Phase.IDLE ? 0 : machine.pushed();
        boolean moving = machine.phase() == NpdFuellingMachine.Phase.TRAVEL || machine.phase() == NpdFuellingMachine.Phase.RETURN
                || viewSnout > 0 && viewSnout < 1 || viewPlug > 0 && viewPlug < 1;
        if (Math.abs(viewPower - sentPower) > 0.02f || viewPhase != sentPhase || viewPushed != sentPushed || moving && ticks % 4 == 0) sync();
    }

    private void sync() {
        sentPower = viewPower;
        sentPhase = viewPhase;
        sentPushed = viewPushed;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        for (BlockPos p : machines)
            if (level.getBlockEntity(p) instanceof NpdFuellingMachineBlockEntity m) m.mirror(this);
    }

    /** Течь уплотнения хобота: капли D₂O у арматуры, пока головки под давлением; стук захвата. */
    private void clientTick() {
        if (viewSnout < 0.999f || viewPhase < NpdFuellingMachine.Phase.PRESSURISE.ordinal()
                || viewPhase > NpdFuellingMachine.Phase.REPLUG.ordinal()) return;
        dev.denfry.promptcritical.mod.fx.MachineFx fx = dev.denfry.promptcritical.mod.fx.MachineFx.of(level, worldPosition, facing());
        if (fx == null || viewChannel < 0) return;
        double cx = 8 + NpdChannels.x(viewChannel) * 16, cy = 56 + NpdChannels.y(viewChannel) * 16;
        if (fx.chance(3)) fx.leak(fx.at(cx, cy - 3, -46));
    }

    private Direction facing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock b ? b.facing(s) : Direction.NORTH;
    }

    private void say(Component c) {
        for (ServerPlayer p : ((ServerLevel) level).getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 24 * 24))
            p.displayClientMessage(c, true);
    }

    /** Пучок из слота — в лоток (до {@value #BAY}); состав усредняется. */
    private void takeBundle() {
        ItemStack s = fuel.getStackInSlot(NpdPanels.SLOT_FRESH);
        if (s.isEmpty() || bay >= BAY) return;
        FuelLot l = FuelLot.of(s);
        double n = bay;
        bayKg = (bayKg * n + l.uKg()) / (n + 1);
        bayU235 = (bayU235 * n + l.u235()) / (n + 1);
        bayU234 = (bayU234 * n + l.u234()) / (n + 1);
        bay++;
        s.shrink(1);
        fuel.setStackInSlot(NpdPanels.SLOT_FRESH, s.isEmpty() ? ItemStack.EMPTY : s);
        setChanged();
    }

    /** Кнопка перегрузки: первая загрузка канала на заглушенном, иначе — машины к самому выгоревшему; в работе — прервать. */
    private void fuel(@Nullable ServerPlayer player) {
        if (machine.phase() != NpdFuellingMachine.Phase.IDLE) {
            machine.abort();
            tell(player, NpdText.msg("aborted"));
            return;
        }
        if (reactor.shutDown()) {
            for (int ch = 0; ch < NpdReactor.CHANNELS; ch++) {
                if (reactor.core().loaded(ch)) continue;
                if (bay < NpdReactor.BUNDLES_PER_CHANNEL) {
                    tell(player, NpdText.msg("need_bundles", bay, NpdReactor.BUNDLES_PER_CHANNEL));
                    return;
                }
                if (reactor.loadChannel(ch, bayKg, bayU235, bayU234)) {
                    bay -= NpdReactor.BUNDLES_PER_CHANNEL;
                    tell(player, NpdText.msg("loaded", NpdChannels.label(ch)));
                    setChanged();
                }
                return;
            }
        }
        if (linkedMachines < 2) {
            tell(player, NpdText.msg("need_machines"));
            return;
        }
        int n = NpdPanels.SHIFTS[shift];
        if (machine.fresh() < n) {
            tell(player, NpdText.msg("need_bundles", machine.fresh() + bay, n));
            return;
        }
        if (machine.spentCount() + n > NpdFuellingMachine.MAGAZINE) {
            tell(player, NpdText.msg("spent_full"));
            return;
        }
        int ch = reactor.mostBurntChannel();
        if (ch >= 0 && machine.start(reactor, ch, n)) {
            viewChannel = ch;
            tell(player, NpdText.msg("fuelling", NpdChannels.label(ch), n));
        }
        setChanged();
    }

    private static void tell(@Nullable ServerPlayer player, Component c) {
        if (player != null) player.displayClientMessage(c, true);
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return fuel;
    }

    /** Лоток пучков для машины: слот свежих и выдача выгоревших. */
    public IItemHandler fuelHandler() {
        return fuel;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return fuel;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return fuel.isItemValid(NpdPanels.SLOT_FRESH, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack left = fuel.insertItem(NpdPanels.SLOT_FRESH, stack.copy(), false);
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
        return NpdPanels.REACTOR;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        NpdReactor r = reactor;
        v[NpdPanels.POWER] = (int) Math.round(Math.min(r.power(), 1.2) * 1000);
        double t = r.core().periodS();
        v[NpdPanels.PERIOD] = t > 0 && t < 1000 ? (int) Math.round(t * 10) : 0;
        v[NpdPanels.RHO_PCM] = (int) Math.round(r.core().reactivity() * 1e5);
        v[NpdPanels.LEVEL] = (int) Math.round(r.moderatorLevel() * 1000);
        v[NpdPanels.LEVEL_RATE] = (int) Math.round(r.levelRate() * 60 * 1000);
        v[NpdPanels.DUMP_TANK] = (int) Math.round(Math.min(1, r.dumpTank().kg() / r.moderatorCapacityKg()) * 1000);
        v[NpdPanels.COOL_FILL] = (int) Math.round(r.coolantFill() * 1000);
        v[NpdPanels.OUT_C] = (int) Math.round(Math.max(0, Math.min(1, (r.outletK() - 273.15) / 320)) * 1000);
        v[NpdPanels.FUEL_C] = (int) Math.round(Math.max(0, Math.min(1, (r.fuelK() - 273.15) / 2000)) * 1000);
        v[NpdPanels.HEAD_D2O] = (int) Math.round(headSupply * 1000);
        v[NpdPanels.MACHINES] = linkedMachines;
        v[NpdPanels.SETPOINT] = (int) Math.round(r.powerSetpoint() * 100);
        v[NpdPanels.MODE] = r.levelMode().ordinal();
        v[NpdPanels.TRIP] = r.trip().ordinal();
        v[NpdPanels.FLOW] = (int) Math.round(r.flowFraction() * 100);
        v[NpdPanels.XE_PCM] = (int) Math.round(r.core().xenonReactivity(NpdReactor.MODERATOR_K) * 1e5);
        v[NpdPanels.LOADED] = r.core().loadedAssemblies();
        v[NpdPanels.BURNUP] = (int) Math.round(r.core().maxBurnup() * 100);
        v[NpdPanels.REFUELLED] = (int) Math.round(r.refuelledBundles());
        v[NpdPanels.BAY] = bay;
        v[NpdPanels.MAG_FRESH] = machine.fresh();
        v[NpdPanels.MAG_SPENT] = machine.spentCount();
        v[NpdPanels.MAG_C] = (int) Math.round(machine.magazineK() - 273.15);
        v[NpdPanels.FM_PHASE] = machine.phase().ordinal();
        v[NpdPanels.FM_CH] = machine.channel();
        v[NpdPanels.FM_PROGRESS] = (int) Math.round(progress() * 1000);
        v[NpdPanels.SHIFT] = NpdPanels.SHIFTS[shift];
        v[NpdPanels.MOD_PURITY] = (int) Math.round(r.moderator().purity() * 10000);
        v[NpdPanels.COOL_PURITY] = (int) Math.round(r.coolant().purity() * 10000);
        v[NpdPanels.TRITIUM_GBQ] = (int) Math.min(Integer.MAX_VALUE, Math.round(r.tritiumBq() / 1e9));
        v[NpdPanels.FLAGS] = (r.trip() != NpdReactor.Trip.NONE ? 1 << NpdPanels.F_TRIPPED : 0)
                | (generators.isEmpty() ? 1 << NpdPanels.F_NO_SG : 0)
                | (machine.fault() != NpdFuellingMachine.Fault.NONE ? 1 << NpdPanels.F_FM_FAULT : 0)
                | (machine.holdNoPower() || machine.holdNoD2o() ? 1 << NpdPanels.F_FM_HOLD : 0)
                | (r.coolantFill() < 0.999 ? 1 << NpdPanels.F_LOW_D2O : 0);
    }

    /** Ход перегрузки 0…1: десять фаз цикла, внутри сдвига — по пучкам. */
    private double progress() {
        NpdFuellingMachine.Phase p = machine.phase();
        if (p == NpdFuellingMachine.Phase.IDLE) return 0;
        double base = p.ordinal() - 1, n = NpdFuellingMachine.Phase.values().length - 1;
        if (p == NpdFuellingMachine.Phase.EXCHANGE && machine.toPush() > 0) base += (double) machine.pushed() / machine.toPush();
        return Math.min(1, base / n);
    }

    /** Сброс АЗ и байпас — право {@code PROTECTION} станции (04d §4d.9.2). */
    @Override
    public dev.denfry.promptcritical.sim.journal.Permission requiredPermission(int id) {
        return id == NpdPanels.B_RESET ? dev.denfry.promptcritical.sim.journal.Permission.PROTECTION : dev.denfry.promptcritical.sim.journal.Permission.CONTROL;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case NpdPanels.B_SDS -> reactor.scram();
            case NpdPanels.B_RESET -> {
                machine.clearFault();
                tell(player, NpdText.msg(reactor.resetTrip() ? "reset" : "reset_refused"));
            }
            case NpdPanels.B_UP -> {
                reactor.setLevelMode(NpdReactor.LevelMode.MANUAL);
                reactor.driveLevel(1);
            }
            case NpdPanels.B_STOP -> reactor.driveLevel(0);
            case NpdPanels.B_DOWN -> {
                reactor.setLevelMode(NpdReactor.LevelMode.MANUAL);
                reactor.driveLevel(-1);
            }
            case NpdPanels.B_SET_DOWN -> reactor.setPowerSetpoint(Math.round(reactor.powerSetpoint() * 20 - 1) / 20.0);
            case NpdPanels.B_SET_UP -> reactor.setPowerSetpoint(Math.round(reactor.powerSetpoint() * 20 + 1) / 20.0);
            case NpdPanels.B_FUEL -> fuel(player);
            case NpdPanels.B_SHIFT -> shift = (shift + 1) % NpdPanels.SHIFTS.length;
            case NpdPanels.B_AUTO -> {
                if (reactor.trip() == NpdReactor.Trip.NONE)
                    reactor.setLevelMode(reactor.levelMode() == NpdReactor.LevelMode.AUTO ? NpdReactor.LevelMode.MANUAL : NpdReactor.LevelMode.AUTO);
            }
            case NpdPanels.B_DRAIN -> drainDumpTank(player);
            default -> {
            }
        }
        setChanged();
    }

    // --- клиент: положение машин и пробки для рендера ---

    public float viewPower() {
        return viewPower;
    }

    public float viewX() {
        return viewX;
    }

    public float viewY() {
        return viewY;
    }

    public float viewSnout() {
        return viewSnout;
    }

    public float viewPlug() {
        return viewPlug;
    }

    public int viewChannel() {
        return viewChannel;
    }

    public int viewPhase() {
        return viewPhase;
    }

    /** Пучков, вдвинутых за этот заход: барабан магазина повёрнут на столько восьмых оборота. */
    public int viewPushed() {
        return viewPushed;
    }

    /** Машин в радиусе связи (по одной у торца нужно две). */
    public int linkedMachines() {
        return linkedMachines;
    }

    public ReactorAccidentSystems accident() {
        return accident;
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
        return facing();
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
        writeView(tag);
        return tag;
    }

    private void writeView(CompoundTag tag) {
        tag.putFloat("view_power", viewPower);
        tag.putFloat("view_x", viewX);
        tag.putFloat("view_y", viewY);
        tag.putFloat("view_snout", viewSnout);
        tag.putFloat("view_plug", viewPlug);
        tag.putInt("view_ch", viewChannel);
        tag.putInt("view_phase", viewPhase);
        tag.putInt("view_pushed", viewPushed);
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
        TreeStateWriter w = new TreeStateWriter(tree);
        reactor.save(w);
        machine.save(w);
        tag.put("reactor", NbtStateTrees.toNbt(tree));
        tag.putInt("bay", bay);
        tag.putDouble("bay_kg", bayKg);
        tag.putDouble("bay_u235", bayU235);
        tag.putDouble("bay_u234", bayU234);
        tag.putInt("shift", shift);
        for (HeavyWaterGrade g : HeavyWaterGrade.values()) drains[g.ordinal()].save(tag, "drain_" + g.name().toLowerCase(Locale.ROOT));
        wiring.save(tag);
        accident.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("reactor")) {
            TreeStateReader in = new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("reactor")));
            reactor.load(in);
            machine.load(in);
        }
        bay = tag.getInt("bay");
        bayKg = tag.getDouble("bay_kg");
        bayU235 = tag.getDouble("bay_u235");
        bayU234 = tag.getDouble("bay_u234");
        shift = Math.floorMod(tag.getInt("shift"), NpdPanels.SHIFTS.length);
        for (HeavyWaterGrade g : HeavyWaterGrade.values()) drains[g.ordinal()].load(tag, "drain_" + g.name().toLowerCase(Locale.ROOT));
        wiring.load(tag);
        lastTrip = reactor.trip();
        lastFault = machine.fault();
        lastPhase = machine.phase();
        if (tag.contains("view_power")) {
            viewPower = tag.getFloat("view_power");
            viewX = tag.getFloat("view_x");
            viewY = tag.getFloat("view_y");
            viewSnout = tag.getFloat("view_snout");
            viewPlug = tag.getFloat("view_plug");
            viewChannel = tag.getInt("view_ch");
            viewPhase = tag.getInt("view_phase");
            viewPushed = tag.getInt("view_pushed");
        }
        accident.load(tag);
    }

    @Override
    public String wholeModelId() {
        return dev.denfry.promptcritical.mod.client.whole.WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        return facing();
    }

    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(facing(), 4).above(3);
    }
}
