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
import dev.denfry.promptcritical.sim.consequence.ConsequenceRouter;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.npp.PressurizedWaterReactor;
import dev.denfry.promptcritical.sim.protection.TripReason;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.radiation.FissionSource;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Реакторная установка ВВЭР T2a ({@link PressurizedWaterReactor}): корпус на 19 ТВС с блоком приводов СУЗ 3×6×3 и
 * блочный щит на контроллере. В {@value #LINK_RADIUS} блоках — компенсатор давления ({@link PressurizerBlockEntity}),
 * ГЦН двух петель ({@link PrimaryPumpBlockEntity}: вал Create только держит обороты, выбег на маховике считает реактор) и
 * парогенераторы ({@link SteamGeneratorBlockEntity}) у барабанов водотрубных котлов — пар идёт в существующие турбины.
 *
 * <p>Свежие ТВС ВВЭР-440, ВВЭР-1000 или PWR 17×17 (одна конструкция на корпус) — в слот щита или воронкой: на
 * заглушенном реакторе загружаются от центра; выгрузка — по одной самой выгоревшей ТВС. Клеммы СУЗ
 * ({@link AutomationKind#REACTOR_IO}): «SCRAM» — АЗ от ПАЗ/ПЛК, «13–14» — контакт «АЗ сработала», «N» — мощность.
 * Разгерметизированные ТВС выносят I-131 в зал, если теплоноситель уходит через течь или ИПУ; разрыв контура от
 * давления — через {@code DamageBudget}.
 */
public final class PwrReactorBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost, WiredDevice {
    public static final int LINK_RADIUS = 12;
    /** I-131 из одной разгерметизированной ТВС, уходящий с теплоносителем через течь или ИПУ, Бк [оценка: 1 % газового зазора]. */
    public static final double I131_PER_FAILED_BQ = 5e12;
    /** Энергия разрыва контура: работа расширения ≈ 7 т воды при 15 МПа до вскипания, Дж [оценка]. */
    public static final double RUPTURE_J = 3e8;
    /** Шаг водообмена с пульта, кг/с. */
    public static final double EXCHANGE_STEP = 0.5;

    private static long requestSeq;

    private final PressurizedWaterReactor reactor = new PressurizedWaterReactor();
    private final MechanismWiring wiring = new MechanismWiring();
    private final ItemStackHandler fuel = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot != PwrPanels.SLOT_FRESH || !stack.is(PcItems.FUEL_ASSEMBLY.get())) return false;
            FuelLot l = FuelLot.of(stack);
            FuelDesign d = l.designOrNull();
            return d != null && PressurizedWaterReactor.accepts(d) && l.burnup() <= 0 && l.pu() <= 0
                    && (reactor.design() == null || reactor.design() == d);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final List<BlockPos> pumps = new ArrayList<>(), generators = new ArrayList<>();
    @Nullable
    private BlockPos pressurizer;
    private boolean wasTripped, concentrate;
    private int ticks;

    public PwrReactorBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.PWR_REACTOR.get(), pos, state);
    }

    public PressurizedWaterReactor reactor() {
        return reactor;
    }

    public ItemStackHandler fuelSlots() {
        return fuel;
    }

    private Direction facing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock b ? b.facing(s) : Direction.NORTH;
    }

    private void link(ServerLevel sl) {
        pumps.clear();
        generators.clear();
        pressurizer = null;
        AABB box = new AABB(worldPosition).inflate(LINK_RADIUS);
        for (PrimaryPumpBlockEntity p : Ironworks.blockEntities(sl, box, PrimaryPumpBlockEntity.class)) {
            if (pumps.size() < PressurizedWaterReactor.LOOPS) pumps.add(p.getBlockPos());
        }
        for (SteamGeneratorBlockEntity g : Ironworks.blockEntities(sl, box, SteamGeneratorBlockEntity.class)) {
            if (generators.size() < PressurizedWaterReactor.LOOPS) generators.add(g.getBlockPos());
        }
        for (PressurizerBlockEntity k : Ironworks.blockEntities(sl, box, PressurizerBlockEntity.class)) {
            pressurizer = k.getBlockPos();
            break;
        }
    }

    @Nullable
    private PressurizerBlockEntity pressurizerBlock() {
        return pressurizer != null && level != null && level.getBlockEntity(pressurizer) instanceof PressurizerBlockEntity k ? k : null;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        SimLevelBinding b = SimBindings.get(sl);
        if (b == null) return;
        if (ticks++ % 20 == 0) link(sl);
        double gameDt = PressurizedWaterReactor.DT * b.world().clock().kSlow();
        // ГЦН: петля i — i-й найденный насос; нет насоса — петля без привода
        for (int l = 0; l < PressurizedWaterReactor.LOOPS; l++) {
            double drive = 0;
            if (l < pumps.size() && level.getBlockEntity(pumps.get(l)) instanceof PrimaryPumpBlockEntity p) drive = p.drive();
            reactor.setPumpDrive(l, drive);
        }
        // ПГ: средняя температура котлов, наименьший уровень барабанов, тепло — поровну
        List<SteamGeneratorBlockEntity> sgs = new ArrayList<>();
        double sum = 0, level = Double.NaN;
        for (BlockPos p : generators)
            if (this.level.getBlockEntity(p) instanceof SteamGeneratorBlockEntity sg && !Double.isNaN(sg.secondaryK())) {
                sgs.add(sg);
                sum += sg.secondaryK();
                double lv = sg.levelFrac();
                if (!Double.isNaN(lv)) level = Double.isNaN(level) ? lv : Math.min(level, lv);
            }
        reactor.setSecondary(sgs.isEmpty() ? Double.NaN : sum / sgs.size(), level, sgs.size());
        PressurizerBlockEntity kd = pressurizerBlock();
        AutomationUnit a = MechanismWiring.unit(this.level, simId(), AutomationKind.REACTOR_IO);
        boolean ioScram = a != null && a.loadEnergized(1);
        // без КД контур не держит давление: защита держит реактор заглушенным
        reactor.setExternalTrip(ioScram || kd == null);
        reactor.step(gameDt);
        double q = reactor.steamGeneratorW();
        for (SteamGeneratorBlockEntity sg : sgs) sg.deliver(q / sgs.size());
        if (kd != null) kd.report(reactor.heaterW() > 1e3, reactor.pressurizer().reliefOpen() || reactor.pressurizer().safetyOpen());
        if (a != null) {
            a.setInput(AutomationUnit.IN_MAIN, reactor.protection().isTripped() ? 1 : 0);
            a.setInput(AutomationUnit.IN_AUX, reactor.power());
        }
        radiation(sl);
        events(sl, b);
        if (reactor.shutDown()) loadFresh();
        if (ticks % 100 == 0) setChanged();
    }

    private void radiation(ServerLevel sl) {
        if (ticks % 20 != 0) return;
        Vec3 center = worldPosition.getCenter().add(0, 1, 0);
        PointSource src = FissionSource.reactor(reactor.fissionW(), reactor.decayHeatW());
        int failed = reactor.failedAssemblies();
        boolean escaping = reactor.leakKgS() > 0 || reactor.pressurizer().reliefOpen() || reactor.pressurizer().safetyOpen();
        if (failed > 0 && escaping) src = src.plus(Emitter.I131.source(failed * I131_PER_FAILED_BQ));
        RadiationService.source(sl, center, src);
    }

    private void events(ServerLevel sl, SimLevelBinding b) {
        boolean tripped = reactor.protection().isTripped();
        if (tripped && !wasTripped) {
            sl.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 0.6f);
            Component why = reasonName(reactor.protection().firstOut());
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 48 * 48))
                p.displayClientMessage(NppText.msg("scram", why).withStyle(ChatFormatting.RED), false);
        }
        wasTripped = tripped;
        if (reactor.leakKgS() > 0 && ticks % 4 == 0)
            sl.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    (int) Math.min(20, 1 + reactor.leakKgS()), 1.2, 0.3, 1.2, 0.05);
        if (reactor.newlyFailed() > 0) {
            sl.playSound(null, worldPosition, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1f, 0.5f);
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 64 * 64))
                p.displayClientMessage(NppText.msg("pwr_dnb", reactor.newlyFailed()).withStyle(ChatFormatting.RED), false);
        }
        if (reactor.ruptureEvent()) {
            GridPos o = new GridPos(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
            // R6: the rupture goes through the consequence router; there is no containment block yet,
            // so the route is "no containment" and the burst blast reaches the budget unchanged
            ConsequenceRouter.Routed routed = ConsequenceRouter.route(ConsequenceRouter.Event.VESSEL_RUPTURE,
                    requestSeq++, simId(), null, o, RUPTURE_J, false, reactor.newlyFailed() > 0);
            if (routed.damage() != null) b.damage().submit(routed.damage());
            sl.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2f, 0.7f);
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 96 * 96))
                p.displayClientMessage(NppText.msg("pwr_rupture").withStyle(ChatFormatting.DARK_RED), false);
        }
    }

    static Component reasonName(@Nullable TripReason r) {
        String key = r == null ? "manual" : r.name().toLowerCase(Locale.ROOT);
        return Component.translatable("panel.promptcritical.pwr.reason." + key);
    }

    /** Свежая ТВС из слота — в первую пустую позицию от центра. */
    private void loadFresh() {
        ItemStack s = fuel.getStackInSlot(PwrPanels.SLOT_FRESH);
        if (s.isEmpty()) return;
        FuelLot l = FuelLot.of(s);
        FuelDesign d = l.designOrNull();
        if (d == null) return;
        for (int pos = 0; pos < PressurizedWaterReactor.POSITIONS; pos++) {
            if (reactor.loaded(pos)) continue;
            if (reactor.load(pos, d, l.uKg(), l.u235(), l.u234())) {
                s.shrink(1);
                fuel.setStackInSlot(PwrPanels.SLOT_FRESH, s);
                setChanged();
            }
            return;
        }
    }

    /** Выгрузка самой выгоревшей ТВС в слот отработанного топлива. */
    private void unload(@Nullable ServerPlayer player) {
        if (!reactor.shutDown()) {
            if (player != null) player.displayClientMessage(NppText.msg("not_shut_down"), true);
            return;
        }
        if (!fuel.getStackInSlot(PwrPanels.SLOT_SPENT).isEmpty()) return;
        int best = -1;
        for (int pos = 0; pos < PressurizedWaterReactor.POSITIONS; pos++)
            if (reactor.loaded(pos) && (best < 0 || reactor.burnup(pos) > reactor.burnup(best))) best = pos;
        if (best < 0) return;
        dev.denfry.promptcritical.sim.npp.LightWaterCore.AssemblyFuel f = reactor.unload(best);
        if (f == null) return;
        double u = f.uraniumKg(), pu = f.plutoniumKg();
        FuelDesign d = f.design();
        FuelLot lot = new FuelLot(u, f.u235Kg() / u, f.u234() * f.hm0Kg() / u, 0.96, d.rods, d.name(), "", f.burnupMwdKg(), f.u236Kg() / u,
                pu, pu > 0 ? f.pu239Kg() / pu : 0, pu > 0 ? f.pu240Kg() / pu : 0, 0, 0);
        fuel.setStackInSlot(PwrPanels.SLOT_SPENT, lot.on(new ItemStack(PcItems.FUEL_ASSEMBLY.get())));
        if (player != null)
            player.displayClientMessage(NppText.msg("pwr_unloaded", best + 1, String.format(Locale.ROOT, "%.2f", f.burnupMwdKg())), true);
        setChanged();
    }

    // --- руки ---

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
        return fuel.isItemValid(PwrPanels.SLOT_FRESH, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack left = fuel.insertItem(PwrPanels.SLOT_FRESH, stack.copy(), false);
        int moved = stack.getCount() - left.getCount();
        if (moved == 0) return ItemInteractionResult.FAIL;
        if (!player.isCreative()) stack.shrink(moved);
        return ItemInteractionResult.SUCCESS;
    }

    /** Shift + ПКМ: кнопка АЗ. */
    @Override
    public InteractionResult use(Player player) {
        reactor.scram();
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return PwrPanels.REACTOR;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        PressurizedWaterReactor r = reactor;
        v[PwrPanels.POWER] = (int) Math.round(Math.min(r.power(), 9.9) * 1000);
        double t = r.periodS();
        v[PwrPanels.PERIOD] = t > 0 && t < 1000 ? (int) Math.round(t * 10) : 0;
        v[PwrPanels.RHO_PCM] = (int) Math.round(r.reactivity() * 1e5);
        v[PwrPanels.AZ] = (int) Math.round((1 - r.az()) * 1000);
        v[PwrPanels.RG] = (int) Math.round((1 - r.rg()) * 1000);
        v[PwrPanels.FUEL_C] = (int) Math.round(r.fuelK() - 273.15);
        v[PwrPanels.HOT_C] = (int) Math.round(r.hotLegK() - 273.15);
        v[PwrPanels.COLD_C] = (int) Math.round(r.coldLegK() - 273.15);
        v[PwrPanels.PRESSURE] = (int) Math.round(r.pressure() / 1e4);
        v[PwrPanels.PZR_LEVEL] = (int) Math.round(r.pressurizerLevel() * 1000);
        v[PwrPanels.BORON] = (int) Math.round(r.boronGkg() * 100);
        v[PwrPanels.HEATER_KW] = (int) Math.round(r.heaterW() / 1e3);
        v[PwrPanels.SPRAY] = (int) Math.round(r.sprayKgS() * 10);
        v[PwrPanels.CHARGE] = (int) Math.round((r.chargingKgS() + r.safetyInjectionKgS()) * 10);
        v[PwrPanels.FLOW] = (int) Math.round(r.flowFraction() * 100);
        double dnbr = r.calculatedDnbr();
        v[PwrPanels.DNBR] = Double.isFinite(dnbr) ? (int) Math.round(Math.min(dnbr, 99) * 100) : 9900;
        v[PwrPanels.SG_MW] = (int) Math.round(r.steamGeneratorW() / 1e5);
        v[PwrPanels.XE_PCM] = (int) Math.round(r.xenonReactivity() * 1e5);
        v[PwrPanels.BURNUP] = (int) Math.round(r.maxBurnup() * 100);
        v[PwrPanels.LOADED] = r.loadedAssemblies();
        v[PwrPanels.FAILED] = r.failedAssemblies();
        v[PwrPanels.SETPOINT] = (int) Math.round(r.setpoint() * 100);
        v[PwrPanels.FIRST_OUT] = r.protection().isTripped() ? PwrPanels.reasonCode(r.protection().firstOut()) : 0;
        v[PwrPanels.EXCHANGE] = (int) Math.round(r.exchangeKgS() * 10);
        v[PwrPanels.EXCHANGE_G] = (int) Math.round(r.exchangeGkg() * 10);
        v[PwrPanels.LEAK] = (int) Math.round(r.leakKgS() * 10);
        v[PwrPanels.PUMP1] = (int) Math.round(r.pumpSpeed(0) * 100);
        v[PwrPanels.PUMP2] = (int) Math.round(r.pumpSpeed(1) * 100);
        v[PwrPanels.FLAGS] = (r.protection().isTripped() ? 1 << PwrPanels.F_TRIPPED : 0)
                | (r.autoRegulator() ? 1 << PwrPanels.F_AUTO : 0)
                | (pressurizer == null ? 1 << PwrPanels.F_NO_PZR : 0)
                | (generators.isEmpty() || r.steamGeneratorW() <= 0 && r.fissionW() > 1e6 ? 1 << PwrPanels.F_NO_SG : 0)
                | (r.failedAssemblies() > 0 ? 1 << PwrPanels.F_FAILED : 0)
                | (r.rgDrive() < 0 ? 1 << PwrPanels.F_RG_UP : 0) | (r.rgDrive() > 0 ? 1 << PwrPanels.F_RG_DOWN : 0)
                | (r.pressurizer().reliefOpen() || r.pressurizer().safetyOpen() ? 1 << PwrPanels.F_RELIEF : 0)
                | (r.safetyInjection() ? 1 << PwrPanels.F_SI : 0)
                | (r.leakKgS() > 0 ? 1 << PwrPanels.F_LEAK : 0)
                | (r.heaterW() > 1e3 ? 1 << PwrPanels.F_HEATERS : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        PressurizedWaterReactor r = reactor;
        switch (id) {
            case PwrPanels.B_SCRAM -> r.scram();
            case PwrPanels.B_RESET -> player.displayClientMessage(NppText.msg(r.resetTrip() ? "reset" : "reset_refused"), true);
            case PwrPanels.B_RG_UP -> r.driveRg(-1);
            case PwrPanels.B_RG_STOP -> r.driveRg(0);
            case PwrPanels.B_RG_DOWN -> r.driveRg(1);
            case PwrPanels.B_AUTO -> r.setAutoRegulator(!r.autoRegulator());
            case PwrPanels.B_SP_DOWN -> r.setSetpoint(r.setpoint() - 0.05);
            case PwrPanels.B_SP_UP -> r.setSetpoint(r.setpoint() + 0.05);
            case PwrPanels.B_LOW_FLUX -> player.displayClientMessage(NppText.msg(r.blockLowFluxSetpoint() ? "pwr_permit" : "pwr_permit_refused"), true);
            case PwrPanels.B_XCH_DOWN -> r.setWaterExchange(r.exchangeKgS() - EXCHANGE_STEP, exchangeG());
            case PwrPanels.B_XCH_UP -> r.setWaterExchange(r.exchangeKgS() + EXCHANGE_STEP, exchangeG());
            case PwrPanels.B_XCH_SOURCE -> {
                concentrate = !concentrate;
                r.setWaterExchange(r.exchangeKgS(), exchangeG());
            }
            case PwrPanels.B_UNLOAD -> unload(player);
            default -> {
            }
        }
        setChanged();
    }

    private double exchangeG() {
        return concentrate ? PressurizedWaterReactor.CONCENTRATE_G_KG : 0;
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
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    // --- сохранение ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fuel", fuel.serializeNBT(registries));
        MutableStateTree tree = new MutableStateTree();
        reactor.save(new TreeStateWriter(tree));
        tag.put("reactor", NbtStateTrees.toNbt(tree));
        tag.putBoolean("concentrate", concentrate);
        wiring.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("reactor")) reactor.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("reactor"))));
        concentrate = tag.getBoolean("concentrate");
        wiring.load(tag);
        wasTripped = reactor.protection().isTripped();
    }
}
