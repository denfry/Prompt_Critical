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
import dev.denfry.promptcritical.mod.heavywater.HeavyWaterText;
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
import dev.denfry.promptcritical.sim.nuclide.PlutoniumCharge;
import dev.denfry.promptcritical.sim.heavywater.HeavyWater;
import dev.denfry.promptcritical.sim.heavywater.HeavyWaterGrade;
import dev.denfry.promptcritical.sim.npp.CanduReactor;
import dev.denfry.promptcritical.sim.npp.LightWaterCore;
import dev.denfry.promptcritical.sim.radiation.FissionSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
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
 *
 * <p>Тяжёлая вода — по трубам: снизу — в контур теплоносителя, с остальных сторон — в каландр (замедлитель).
 * Принимается любой сорт воды, даже обычная: она честно разбавляет D₂O, и реактивность падает. Новый реактор сухой
 * ({@link CanduReactor#dry}): без замедлителя он не критичен. Кнопка «Слив» отбирает кубометр замедлителя в бак
 * сорта не выше его чистоты ({@link HeavyWaterGrade#atMost}) — оттуда трубой на колонну доводки; уровень и
 * реактивность при этом падают, как при сливе замедлителя в бак на Пикеринге.
 */
public final class CanduReactorBlockEntity extends DeviceBlockEntity implements dev.denfry.promptcritical.mod.environment.SelfEmittingSource, FoundryDevice, PanelHost, WiredDevice, dev.denfry.promptcritical.mod.client.whole.WholeModelHost {
    public static final int LINK_RADIUS = 12;
    /** Реальный масштаб: насосы и парогенераторы CANDU 6 стоят над сводом в десятках метров от оси каландра. */
    public static final int LINK_RADIUS_REAL = 32;

    public static final int DRAIN_MB = 4000, DRAIN_STEP_L = 1000;

    private final CanduReactor reactor = CanduReactor.dry();
    /** Баки слива по сортам (индекс — {@link HeavyWaterGrade#ordinal}). */
    private final ProductTank[] drains = new ProductTank[HeavyWaterGrade.values().length];
    private final GradedWaterPort moderatorPort, coolantPort;
    private final MechanismWiring wiring = new MechanismWiring();
    private final ItemStackHandler fuel = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot != CanduPanels.SLOT_FRESH || !stack.is(PcItems.FUEL_ASSEMBLY.get())) return false;
            FuelLot l = FuelLot.of(stack);
            return l.designOrNull() == FuelDesign.CANDU_37 && l.freshThermal();
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final List<BlockPos> pumps = new ArrayList<>(), generators = new ArrayList<>();
    private CanduReactor.Trip lastTrip = CanduReactor.Trip.MANUAL;
    /** LOCA и обесточивание (04c §4c.11.2): слой аварии {@link PlantAccident#CANDU_T3}; паровой эффект — у реактора. */
    private final ReactorAccidentSystems accident = new ReactorAccidentSystems(PlantAccident.CANDU_T3);

    public ReactorAccidentSystems accident() {
        return accident;
    }
    private int ticks;
    /** Непрерывная работа без автоматической АЗ (11 §11.2.2 {@code hundred_days}). */
    private final dev.denfry.promptcritical.sim.reactor.ContinuousRun run = new dev.denfry.promptcritical.sim.reactor.ContinuousRun();
    /** Следование суточному графику (11 §11.2.2 {@code load_follow}). */
    private final dev.denfry.promptcritical.sim.reactor.LoadFollowWatch follow = new dev.denfry.promptcritical.sim.reactor.LoadFollowWatch();
    /** Магазин перегрузочной машины: свежие пучки (предмет — один пучок), их уран на пучок и доли U-235, U-234. */
    private int magazine;
    private double magKg, magU235, magU234;
    /** Плутоний среднего пучка магазина (МОКС), кг по изотопам. */
    private PlutoniumCharge magPu = PlutoniumCharge.NONE;
    /** Для клиента: доля номинальной мощности и последняя перегрузка (игровое время, канал; −1 — не было). */
    private float viewPower, sentPower = -1;
    private long refuelTick = Long.MIN_VALUE / 2;
    private int refuelChannel = -1;

    public CanduReactorBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.CANDU_REACTOR.get(), pos, state);
        for (HeavyWaterGrade g : HeavyWaterGrade.values()) drains[g.ordinal()] = new ProductTank(HeavyWaterFluids.fluid(g), DRAIN_MB);
        EnumSet<HeavyWaterGrade> any = EnumSet.allOf(HeavyWaterGrade.class);
        List<ProductTank> outlets = List.of(drains);
        moderatorPort = new GradedWaterPort(new GradedWaterPort.Sink() {
            @Override
            public double roomKg() {
                return reactor.moderatorCapacityKg() - reactor.moderator().kg();
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

    /** Патрубок: снизу — контур теплоносителя, иначе — каландр; слив — из баков слива. */
    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return side == Direction.DOWN ? coolantPort : moderatorPort;
    }

    public ProductTank drainTank(HeavyWaterGrade g) {
        return drains[g.ordinal()];
    }

    /** Слить {@value #DRAIN_STEP_L} л замедлителя в бак его сорта; false — бак полон или каландр пуст. */
    public boolean drainModerator(@Nullable ServerPlayer player) {
        double x = reactor.moderator().purity();
        HeavyWaterGrade g = HeavyWaterGrade.atMost(x);
        ProductTank t = drains[g.ordinal()];
        double litres = Math.min(DRAIN_STEP_L, Math.min(t.room(), HeavyWater.litres(reactor.moderator().kg(), x)));
        if (litres < 1) {
            if (player != null && reactor.moderator().kg() > 0)
                player.displayClientMessage(HeavyWaterText.msg("candu.drain_full").withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        double kg = reactor.drainModerator(HeavyWater.kgFromLitres(litres, x));
        t.add(HeavyWater.litres(kg, x));
        if (player != null) player.displayClientMessage(HeavyWaterText.msg("candu.drained", Math.round(litres),
                net.minecraft.network.chat.Component.translatable(HeavyWaterFluids.fluid(g).getFluidType().getDescriptionId())), true);
        setChanged();
        return true;
    }

    public CanduReactor reactor() {
        return reactor;
    }

    private void link(ServerLevel sl) {
        pumps.clear();
        generators.clear();
        AABB box = new AABB(worldPosition).inflate(wholeModelId() != null ? LINK_RADIUS_REAL : LINK_RADIUS);
        for (PrimaryPumpBlockEntity p : Ironworks.blockEntities(sl, box, PrimaryPumpBlockEntity.class)) pumps.add(p.getBlockPos());
        for (SteamGeneratorBlockEntity g : Ironworks.blockEntities(sl, box, SteamGeneratorBlockEntity.class)) generators.add(g.getBlockPos());
        // насос, сорванный с контура под давлением, — разрыв коллектора
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
        // потеря теплоносителя по слою аварии — осушение каналов: пустотный эффект CANDU положительный (04c §4c.11.2)
        PlantAccident acc = accident.accident();
        reactor.setCoolantVoid(acc.active() ? 1 - Math.min(1, acc.levelFraction()) : 0);
        AutomationUnit a = MechanismWiring.unit(level, simId(), AutomationKind.REACTOR_IO);
        reactor.setExternalTrip(a != null && a.loadEnergized(1) || accident.demandsTrip());
        double gameDt = CanduReactor.DT * b.world().clock().kSlow();
        reactor.step(gameDt);
        if (run.update(gameDt, reactor.power(), reactor.trip() != CanduReactor.Trip.NONE && reactor.trip() != CanduReactor.Trip.MANUAL))
            dev.denfry.promptcritical.mod.content.SimAchievements.nearby(sl, worldPosition, dev.denfry.promptcritical.mod.content.SimAchievements.HUNDRED_DAYS, "days", run.days());
        if (reactor.power() >= dev.denfry.promptcritical.mod.content.IndustrialComplex.REACTOR_POWER) dev.denfry.promptcritical.mod.content.IndustrialComplex.running(sl, worldPosition, dev.denfry.promptcritical.mod.content.IndustrialComplex.Part.REACTOR);
        if (follow.update(gameDt, dev.denfry.promptcritical.mod.grid.LoadProfiles.shape(sl), reactor.power(), reactor.trip() != CanduReactor.Trip.NONE))
            dev.denfry.promptcritical.mod.content.SimAchievements.nearby(sl, worldPosition, dev.denfry.promptcritical.mod.content.SimAchievements.LOAD_FOLLOW, "hours", follow.hours());
        List<SteamGeneratorBlockEntity> sgs = new ArrayList<>();
        for (BlockPos p : generators)
            if (level.getBlockEntity(p) instanceof SteamGeneratorBlockEntity sg && !Double.isNaN(sg.secondaryK())) sgs.add(sg);
        double q = reactor.steamGeneratorW();
        for (SteamGeneratorBlockEntity sg : sgs) sg.deliver(q / sgs.size());
        if (ticks % 100 == 1) accident.scan(sl, worldPosition);
        int breach = accident.step(sl, b, simId(), worldPosition, gameDt, reactor.core().fissionW() + reactor.core().decayHeatW(),
                sgs.isEmpty() ? 0 : q, CanduReactor.P_COOLANT, pumpsDriven, reactor.core().loadedAssemblies(),
                reactor.core().failedAssemblies());
        if (breach > 0 && reactor.core().failHottest(breach) > 0)
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 64 * 64))
                p.displayClientMessage(NppText.msg("accident_clad", reactor.core().failedAssemblies()).withStyle(ChatFormatting.DARK_RED), false);
        accident.radiation(sl, worldPosition, reactor.core().failedAssemblies());
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
        viewPower = (float) Math.max(0, reactor.power());
        if (Math.abs(viewPower - sentPower) > 0.02f) sync();
    }

    private void sync() {
        sentPower = viewPower;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    /** Мощность реактора, доля номинальной (на клиенте — последняя присланная). */
    public float viewPower() {
        return viewPower;
    }

    /** Игровое время последней перегрузки канала. */
    public long refuelTick() {
        return refuelTick;
    }

    /** Канал последней перегрузки; −1 — перегрузок не было. */
    public int refuelChannel() {
        return refuelChannel;
    }

    private void refuelled(int ch) {
        refuelTick = level.getGameTime();
        refuelChannel = ch;
        sync();
    }

    /**
     * Перегрузочная машина у канала: пока захват сидит на концевой арматуре, из уплотнения хобота капает тяжёлая вода —
     * у машины CANDU свой контур D₂O под давлением теплоносителя, и утечки собирает дренаж торца.
     */
    private void clientTick() {
        double t = level.getGameTime() - refuelTick;
        if (!CanduFuelling.latched(refuelChannel, t)) return;
        dev.denfry.promptcritical.mod.fx.MachineFx fx = dev.denfry.promptcritical.mod.fx.MachineFx.of(level, worldPosition, facing());
        if (fx == null) return;
        if (wholeModelId() != null) {
            // реальный масштаб: хобот машины у закрывающей пробки канала на северном торце
            double[] r = CanduFuelling.offsetReal(refuelChannel, t);
            if (fx.chance(2)) fx.leak(fx.at(8 + r[0], 80 + r[1] - 3, -97));
            if (t - (CanduFuelling.TRAVEL + 10) < 1) fx.sound(fx.at(8 + r[0], 80 + r[1], -98), SoundEvents.PISTON_EXTEND, 0.6f, 0.6f);
            return;
        }
        double[] p = CanduFuelling.offset(refuelChannel, t);
        if (fx.chance(2)) fx.leak(fx.at(8 + p[0], 9 + p[1] - 1.4, -9.8));
        if (t - (CanduFuelling.TRAVEL + 10) < 1)
            fx.sound(fx.at(8 + p[0], 9 + p[1], -10), SoundEvents.PISTON_EXTEND, 0.6f, 0.6f);
    }

    private Direction facing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock b ? b.facing(s) : Direction.NORTH;
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
        magPu = magPu.scaled(n).plus(l.plutonium()).scaled(1 / (n + 1));
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
                if (reactor.loadChannel(ch, magKg, magU235, magU234, magPu)) {
                    magazine -= CanduReactor.BUNDLES_PER_CHANNEL;
                    refuelled(ch);
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
        LightWaterCore.AssemblyFuel out = reactor.refuel(ch, CanduReactor.SHIFT_BUNDLES, magKg, magU235, magU234, magPu);
        magazine -= CanduReactor.SHIFT_BUNDLES;
        refuelled(ch);
        if (out != null) {
            FuelDesign d = FuelDesign.CANDU_37;
            FuelLot lot = FuelLot.irradiated(d, 0.96, d.rods * CanduReactor.SHIFT_BUNDLES, out.uraniumKg(), out.u235Kg(),
                    out.u234() * out.hm0Kg(), out.u236Kg(), out.pu239Kg(), out.pu240Kg(), out.pu241Kg(), out.pu242Kg(), out.burnupMwdKg());
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
        v[CanduPanels.MOD_LEVEL] = (int) Math.round(r.moderatorLevel() * 1000);
        v[CanduPanels.MOD_PURITY] = (int) Math.round(r.moderator().purity() * 10000);
        v[CanduPanels.COOL_FILL] = (int) Math.round(r.coolantFill() * 1000);
        v[CanduPanels.COOL_PURITY] = (int) Math.round(r.coolant().purity() * 10000);
        int drained = 0;
        for (ProductTank tank : drains) drained += (int) Math.floor(tank.amount());
        v[CanduPanels.DRAIN_L] = drained;
        v[CanduPanels.FLAGS] = (r.trip() != CanduReactor.Trip.NONE ? 1 << CanduPanels.F_TRIPPED : 0)
                | (generators.isEmpty() ? 1 << CanduPanels.F_NO_SG : 0)
                | (r.moderator().kg() > 0 && r.moderator().purity() < HeavyWater.REACTOR_GRADE_X - 5e-4 ? 1 << CanduPanels.F_DOWNGRADED : 0)
                | (r.moderatorLevel() < 0.999 || r.coolantFill() < 0.999 ? 1 << CanduPanels.F_LOW_D2O : 0);
        accident.readPanel(v, CanduPanels.ACCIDENT);
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
            case CanduPanels.B_DRAIN -> drainModerator(player);
            case CanduPanels.B_LOCA -> accident.cycleDrill(player);
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
        tag.putFloat("view_power", viewPower);
        tag.putLong("view_refuel_t", refuelTick);
        tag.putInt("view_refuel_ch", refuelChannel);
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
        tag.putInt("magazine", magazine);
        tag.putDouble("mag_kg", magKg);
        tag.putDouble("mag_u235", magU235);
        tag.putDouble("mag_u234", magU234);
        tag.putDouble("mag_pu239", magPu.pu239Kg());
        tag.putDouble("mag_pu240", magPu.pu240Kg());
        tag.putDouble("mag_pu241", magPu.pu241Kg());
        tag.putDouble("mag_pu242", magPu.pu242Kg());
        for (HeavyWaterGrade g : HeavyWaterGrade.values()) drains[g.ordinal()].save(tag, "drain_" + g.name().toLowerCase(java.util.Locale.ROOT));
        wiring.save(tag);
        accident.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        run.restore(tag.getDouble("run_s"));
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("reactor")) reactor.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("reactor"))));
        magazine = tag.getInt("magazine");
        magKg = tag.getDouble("mag_kg");
        magU235 = tag.getDouble("mag_u235");
        magU234 = tag.getDouble("mag_u234");
        magPu = new PlutoniumCharge(tag.getDouble("mag_pu239"), tag.getDouble("mag_pu240"), tag.getDouble("mag_pu241"),
                tag.getDouble("mag_pu242"));
        for (HeavyWaterGrade g : HeavyWaterGrade.values()) drains[g.ordinal()].load(tag, "drain_" + g.name().toLowerCase(java.util.Locale.ROOT));
        wiring.load(tag);
        lastTrip = reactor.trip();
        if (tag.contains("view_power")) {
            viewPower = tag.getFloat("view_power");
            refuelTick = tag.getLong("view_refuel_t");
            refuelChannel = tag.getInt("view_refuel_ch");
        }
        accident.load(tag);
    }

    // --- цельная модель реального масштаба (спецификация real-scale machines) ---

    @Override
    public String wholeModelId() {
        return dev.denfry.promptcritical.mod.client.whole.WholeModelHost.idOf(getBlockState());
    }

    @Override
    public net.minecraft.core.Direction wholeModelFacing() {
        return facing();
    }

    @Override
    public net.minecraft.core.BlockPos wholeModelLightPos() {
        return worldPosition.relative(facing(), 9).above();
    }
}
