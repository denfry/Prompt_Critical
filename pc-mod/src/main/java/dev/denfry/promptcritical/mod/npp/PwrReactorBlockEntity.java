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
import dev.denfry.promptcritical.sim.accident.PlantAccident;
import dev.denfry.promptcritical.sim.accident.ReactivityInitiatedAccident;
import dev.denfry.promptcritical.sim.accident.SteamGeneratorTubeRupture;
import dev.denfry.promptcritical.sim.reactor.pwr.SecondarySide;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *
 * <p>Аварии ({@link ReactorAccidentSystems}, {@link PlantAccident#PWR_T2A}): сорванный с петли ГЦН или КД — разрыв
 * трубопровода, кнопка «LOCA» — учебное исходное событие; без вращения валов ГЦН и насосов САОЗ — полное обесточивание.
 * Слой аварии берёт тепло зоны и отбор ПГ у реактора, держит АЗ, показывает давление, уровень и температуру оболочек,
 * разгерметизирует ТВС при разогреве и отправляет горение водорода и разрыв гермооболочки через маршрутизатор
 * последствий.
 */
public final class PwrReactorBlockEntity extends DeviceBlockEntity implements dev.denfry.promptcritical.mod.environment.SelfEmittingSource, FoundryDevice, PanelHost, WiredDevice, dev.denfry.promptcritical.mod.client.whole.WholeModelHost {
    public static final int LINK_RADIUS = 12;
    /** Реальный масштаб: петли ВВЭР-1000 лежат в десятках метров от корпуса. */
    public static final int LINK_RADIUS_REAL = 32;
    /** I-131 из одной разгерметизированной ТВС, уходящий с теплоносителем через течь или ИПУ, Бк [оценка: 1 % газового зазора]. */
    public static final double I131_PER_FAILED_BQ = 5e12;
    /**
     * Cs-137, осевший из разлитого теплоносителя, на единицу I-131 выхода из разрушенной сборки: зазорный инвентарь Cs
     * сопоставим с иодным (оба — летучие продукты деления под оболочкой), большая часть остаётся в воде и уходит с ней
     * в приямок; на полу — порядка десятой доли. Игровое допущение.
     */
    public static final double CS_SPILL_PER_I131 = 0.1;
    public static final int RUPTURE_SPILL_RADIUS = 6;
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
            return d != null && PressurizedWaterReactor.accepts(d) && l.freshThermal()
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
    /** LOCA, blackout and containment (04c M10): the accident layer and its world glue. */
    private final ReactorAccidentSystems accident = new ReactorAccidentSystems(PlantAccident.PWR_T2A);
    /** Tube rupture in the first steam generator (04c §4c.6): the damaged generator's side of the leak. */
    private final SteamGeneratorTubeRupture sgtr = new SteamGeneratorTubeRupture(SecondarySide.P_NOMINAL);
    /**
     * I-131 in the primary coolant of intact fuel, Bq/kg [оценка: ≈ 0.03 µCi/g, a typical PWR well under the 1 µCi/g
     * dose-equivalent limit]; failed assemblies add their {@link #I131_PER_FAILED_BQ} over the primary inventory.
     */
    public static final double COOLANT_I131_BQ_PER_KG = 1e6;
    /** Iodine carried with steam out of boiling water ({@code accident.iodine_partition}, 04c §4c.8.4) [BALANCE]. */
    public static final double IODINE_PARTITION = 1e-3;
    /** Ticks of airborne release left after a rupture outside an intact containment. */
    private int releaseTicks;
    private double releaseI131BqS;
    /** Cs-137 to I-131 activity ratio in an equilibrium LWR core (≈ 0.1, NUREG-1465 inventories). */
    static final double CS137_PER_I131 = 0.1;
    private int ticks;
    /** Непрерывная работа без автоматической АЗ (11 §11.2.2 {@code hundred_days}). */
    private final dev.denfry.promptcritical.sim.reactor.ContinuousRun run = new dev.denfry.promptcritical.sim.reactor.ContinuousRun();
    /** Следование суточному графику (11 §11.2.2 {@code load_follow}). */
    private final dev.denfry.promptcritical.sim.reactor.LoadFollowWatch follow = new dev.denfry.promptcritical.sim.reactor.LoadFollowWatch();

    public PwrReactorBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.PWR_REACTOR.get(), pos, state);
    }

    public PressurizedWaterReactor reactor() {
        return reactor;
    }

    public ItemStackHandler fuelSlots() {
        return fuel;
    }

    public ReactorAccidentSystems accident() {
        return accident;
    }

    private Direction facing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock b ? b.facing(s) : Direction.NORTH;
    }

    private void link(ServerLevel sl) {
        pumps.clear();
        generators.clear();
        pressurizer = null;
        AABB box = new AABB(worldPosition).inflate(getBlockState().is(dev.denfry.promptcritical.mod.registry.PcBlocks.VVER_REACTOR.get()) ? LINK_RADIUS_REAL : LINK_RADIUS);
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
        Map<BlockPos, ReactorAccidentSystems.LoopPart> loop = new LinkedHashMap<>();
        for (BlockPos p : pumps) loop.put(p, ReactorAccidentSystems.LoopPart.PUMP);
        if (pressurizer != null) loop.put(pressurizer, ReactorAccidentSystems.LoopPart.PRESSURIZER);
        accident.link(sl, worldPosition, loop, reactor.loadedAssemblies() > 0);
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
        boolean pumpsDriven = false;
        for (int l = 0; l < PressurizedWaterReactor.LOOPS; l++) {
            double drive = 0;
            if (l < pumps.size() && level.getBlockEntity(pumps.get(l)) instanceof PrimaryPumpBlockEntity p) drive = p.drive();
            reactor.setPumpDrive(l, drive);
            pumpsDriven |= drive > 0.5;
        }
        // ПГ: средняя температура котлов, наименьший уровень барабанов, тепло — поровну
        List<SteamGeneratorBlockEntity> sgs = new ArrayList<>();
        double sum = 0, level = Double.NaN;
        for (int gi = 0; gi < generators.size(); gi++) {
            // ПГ-1 с разрывом трубок, отсечённый по пару и питанию, тепло не отводит
            if (gi == 0 && sgtr.isolated()) continue;
            if (this.level.getBlockEntity(generators.get(gi)) instanceof SteamGeneratorBlockEntity sg && !Double.isNaN(sg.secondaryK())) {
                sgs.add(sg);
                sum += sg.secondaryK();
                double lv = sg.levelFrac();
                if (!Double.isNaN(lv)) level = Double.isNaN(level) ? lv : Math.min(level, lv);
            }
        }
        reactor.setSecondary(sgs.isEmpty() ? Double.NaN : sum / sgs.size(), level, sgs.size());
        PressurizerBlockEntity kd = pressurizerBlock();
        AutomationUnit a = MechanismWiring.unit(this.level, simId(), AutomationKind.REACTOR_IO);
        boolean ioScram = a != null && a.loadEnergized(1);
        // без КД контур не держит давление: защита держит реактор заглушенным; авария — тоже
        reactor.setExternalTrip(ioScram || kd == null || accident.demandsTrip());
        reactor.setTubeRupture(generators.isEmpty() ? 0 : sgtr.breakAreaM2(), sgtr.sgPressurePa());
        reactor.step(gameDt);
        tubeRupture(sl);
        if (run.update(gameDt, reactor.power(), reactor.tripped() && reactor.protection().firstOut() != dev.denfry.promptcritical.sim.protection.TripReason.MANUAL))
            dev.denfry.promptcritical.mod.content.SimAchievements.nearby(sl, worldPosition, dev.denfry.promptcritical.mod.content.SimAchievements.HUNDRED_DAYS, "days", run.days());
        if (reactor.power() >= dev.denfry.promptcritical.mod.content.IndustrialComplex.REACTOR_POWER) dev.denfry.promptcritical.mod.content.IndustrialComplex.running(sl, worldPosition, dev.denfry.promptcritical.mod.content.IndustrialComplex.Part.REACTOR);
        if (follow.update(gameDt, dev.denfry.promptcritical.mod.grid.LoadProfiles.shape(sl), reactor.power(), reactor.tripped()))
            dev.denfry.promptcritical.mod.content.SimAchievements.nearby(sl, worldPosition, dev.denfry.promptcritical.mod.content.SimAchievements.LOAD_FOLLOW, "hours", follow.hours());
        double q = reactor.steamGeneratorW();
        for (SteamGeneratorBlockEntity sg : sgs) sg.deliver(q / sgs.size());
        if (kd != null) kd.report(reactor.heaterW() > 1e3, reactor.pressurizer().reliefOpen() || reactor.pressurizer().safetyOpen());
        if (a != null) {
            a.setInput(AutomationUnit.IN_MAIN, reactor.protection().isTripped() ? 1 : 0);
            a.setInput(AutomationUnit.IN_AUX, reactor.power());
        }
        if (ticks % 100 == 1) accident.scan(sl, worldPosition);
        radiation(sl);
        events(sl, b);
        // слой аварии: тепло в топливо и отбор ПГ — от реактора; разогрев зоны разгерметизирует горячие ТВС
        int breach = accident.step(sl, b, simId(), worldPosition, gameDt, reactor.core().heatW(), reactor.steamGeneratorW(),
                reactor.pressure(), pumpsDriven, reactor.loadedAssemblies(), reactor.failedAssemblies());
        if (breach > 0 && reactor.failCladding(breach) > 0) {
            sl.playSound(null, worldPosition, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1f, 0.4f);
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 64 * 64))
                p.displayClientMessage(NppText.msg("accident_clad", reactor.failedAssemblies()).withStyle(ChatFormatting.DARK_RED), false);
        }
        accident.radiation(sl, worldPosition, reactor.failedAssemblies());
        if (reactor.shutDown()) loadFresh();
        if (ticks % 100 == 0) setChanged();
        syncRods(sl);
    }

    /** Указатели положения стержней на цельной модели: клиенту — выведенная доля регулирующей группы, раз в секунду. */
    private float rodsOut, rodsSent = -1;

    private void syncRods(ServerLevel sl) {
        if (ticks % 20 != 0 || wholeModelId() == null) return;
        rodsOut = (float) Math.max(0, Math.min(1, 1 - reactor.rg()));
        if (Math.abs(rodsOut - rodsSent) < 0.02f) return;
        rodsSent = rodsOut;
        sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    /** Выведенная доля регулирующей группы СУЗ (0 — введена, 1 — выведена) для partial-модели указателей. */
    public float rodsOut() {
        return rodsOut;
    }

    private void radiation(ServerLevel sl) {
        if (ticks % 20 != 0) return;
        Vec3 center = worldPosition.getCenter().add(0, 1, 0);
        PointSource src = FissionSource.reactor(reactor.fissionW(), reactor.decayHeatW());
        int failed = reactor.failedAssemblies();
        boolean escaping = reactor.leakKgS() > 0 || reactor.pressurizer().reliefOpen() || reactor.pressurizer().safetyOpen()
                || accident.accident().breakFlowKgS() > 0 || accident.accident().boilOffKgS() > 0;
        if (failed > 0 && escaping) src = src.plus(Emitter.I131.source(failed * I131_PER_FAILED_BQ));
        RadiationService.source(sl, center, src);
        if (releaseTicks > 0) {
            releaseTicks -= 20;
            Vec3 top = worldPosition.getCenter().add(0, 6, 0);
            RadiationService.airRelease(sl, top, Emitter.I131, releaseI131BqS);
            RadiationService.airRelease(sl, top, Emitter.CS137, releaseI131BqS * CS137_PER_I131);
        }
    }

    /**
     * Steps the damaged generator on the reactor's real-time step (the leak is the reactor's) and sends the coolant it
     * passes out: with the steam to the condenser off-gas (iodine partition 10⁻³), through the SG relief straight to the
     * atmosphere — steam with the same partition, water with all of it (§4c.6.2, the containment bypass).
     */
    private void tubeRupture(ServerLevel sl) {
        if (!sgtr.ruptured()) return;
        java.util.SplittableRandom rnd = new java.util.SplittableRandom(sl.getSeed() ^ worldPosition.asLong() ^ 0x5617L);
        // the stuck-open draw happens once per valve lift: derive it from the world and the plant, not the tick
        sgtr.step(PressurizedWaterReactor.DT, reactor.tubeLeakKgS(), reactor.coldLegK(), reactor.power(), rnd);
        Vec3 c = worldPosition.getCenter();
        if (sgtr.alarmEvent()) {
            sl.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 2f, 1.6f);
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(c) < 64 * 64))
                p.displayClientMessage(NppText.msg("sgtr_n16").withStyle(ChatFormatting.RED), false);
        }
        if (sgtr.stuckEvent())
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(c) < 96 * 96))
                p.displayClientMessage(NppText.msg("sgtr_stuck").withStyle(ChatFormatting.DARK_RED), false);
        if (ticks % 20 != 0) return;
        double perKg = COOLANT_I131_BQ_PER_KG + reactor.failedAssemblies() * I131_PER_FAILED_BQ / Math.max(1, reactor.primaryKg());
        double bqS = perKg * (sgtr.condenserKgS() * IODINE_PARTITION + sgtr.atmosphereKgS() * (sgtr.waterRelief() ? 1 : IODINE_PARTITION));
        if (bqS > 0) {
            BlockPos sgPos = generators.isEmpty() ? worldPosition : generators.get(0);
            Vec3 top = sgPos.getCenter().add(0, 4, 0);
            RadiationService.airRelease(sl, top, Emitter.I131, bqS);
            RadiationService.airRelease(sl, top, Emitter.CS137, bqS * CS137_PER_I131);
        }
        if (sgtr.atmosphereKgS() > 0 && !generators.isEmpty()) {
            Vec3 v = generators.get(0).getCenter();
            sl.sendParticles(ParticleTypes.CLOUD, v.x, v.y + 2, v.z, (int) Math.min(20, 2 + sgtr.atmosphereKgS()), 0.4, 1.0, 0.4, 0.1);
        }
    }

    public SteamGeneratorTubeRupture tubeRuptureModel() {
        return sgtr;
    }

    public boolean containmentIntact() { return accident.containment().intact(); }
    public int containmentVolumeM3() { return accident.containment().volumeM3(); }
    public int recombiners() { return accident.containment().recombiners(); }

    private static final int SCRAM_CLEAN_TICKS = 1200;
    private double tripPower;
    private int tripFailed;
    private long tripTick = -1;

    private void events(ServerLevel sl, SimLevelBinding b) {
        boolean tripped = reactor.protection().isTripped();
        if (tripped && !wasTripped) {
            tripPower = reactor.power();
            tripFailed = reactor.failedAssemblies();
            tripTick = ticks;
            sl.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 0.6f);
            Component why = reasonName(reactor.protection().firstOut());
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 48 * 48))
                p.displayClientMessage(NppText.msg("scram", why).withStyle(ChatFormatting.RED), false);
        }
        wasTripped = tripped;
        // «Чистая АЗ» (11 §11.2.2): с мощности выше 50 % и без новых разгерметизаций за минуту после срабатывания
        if (tripTick >= 0 && ticks - tripTick == SCRAM_CLEAN_TICKS) {
            if (tripPower > 0.5 && reactor.failedAssemblies() == tripFailed)
                dev.denfry.promptcritical.mod.content.SimAchievements.nearby(sl, worldPosition, dev.denfry.promptcritical.mod.content.SimAchievements.SCRAM_CLEAN, "power_pct", 100 * tripPower);
            tripTick = -1;
        }
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
            // R6: the rupture goes through the consequence router. Inside an intact containment the
            // activity stays in the building (the failed-fuel point source above doses those inside);
            // otherwise the failed fuel's I-131/Cs-137 goes up as an airborne plume for a minute.
            int failed = reactor.failedAssemblies();
            ConsequenceRouter.Routed routed = ConsequenceRouter.route(ConsequenceRouter.Event.VESSEL_RUPTURE,
                    requestSeq++, simId(), null, o, RUPTURE_J, containmentIntact(), failed > 0);
            if (routed.damage() != null) b.damage().submit(routed.damage());
            if (routed.release() == ConsequenceRouter.ReleasePath.ENVIRONMENT) {
                releaseTicks = 60 * 20;
                releaseI131BqS = failed * I131_PER_FAILED_BQ / 60.0;
            }
            // теплоноситель первого контура разлит по полу: с разрушенным топливом в нём Cs-137 и следы α
            double beta = failed * I131_PER_FAILED_BQ * CS_SPILL_PER_I131;
            dev.denfry.promptcritical.mod.radiation.ContaminationService.spill(sl, worldPosition, RUPTURE_SPILL_RADIUS,
                    beta * dev.denfry.promptcritical.sim.radiation.SurfaceContamination.FUEL_ALPHA_PER_BETA, beta);
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
            // МОКС: плутоний идёт в зону по изотопам — ячейка видит его сечения и β (LightWaterCore)
            if (reactor.load(pos, d, l.uKg(), l.u235(), l.u234(), l.plutonium())) {
                if (dev.denfry.promptcritical.mod.nuclear.MoxShare.of(l).isMox()) dev.denfry.promptcritical.mod.content.SimAchievements.once(this, dev.denfry.promptcritical.mod.content.SimAchievements.MOX_LOADED, "pu_kg", 1);
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
        FuelDesign d = f.design();
        FuelLot lot = FuelLot.irradiated(d, 0.96, d.rods, f.uraniumKg(), f.u235Kg(), f.u234() * f.hm0Kg(), f.u236Kg(), f.pu239Kg(),
                f.pu240Kg(), f.pu241Kg(), f.pu242Kg(), f.burnupMwdKg());
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
        PlantAccident acc = accident.accident();
        v[PwrPanels.PRESSURE] = (int) Math.round((acc.active() ? acc.pressurePa() : r.pressure()) / 1e4);
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
        v[PwrPanels.LEAK] = (int) Math.round((r.leakKgS() + acc.breakFlowKgS()) * 10);
        v[PwrPanels.PUMP1] = (int) Math.round(r.pumpSpeed(0) * 100);
        v[PwrPanels.PUMP2] = (int) Math.round(r.pumpSpeed(1) * 100);
        v[PwrPanels.FLAGS] = (r.protection().isTripped() ? 1 << PwrPanels.F_TRIPPED : 0)
                | (r.autoRegulator() ? 1 << PwrPanels.F_AUTO : 0)
                | (pressurizer == null ? 1 << PwrPanels.F_NO_PZR : 0)
                | (generators.isEmpty() || r.steamGeneratorW() <= 0 && r.fissionW() > 1e6 ? 1 << PwrPanels.F_NO_SG : 0)
                | (r.failedAssemblies() > 0 ? 1 << PwrPanels.F_FAILED : 0)
                | (r.rgDrive() < 0 ? 1 << PwrPanels.F_RG_UP : 0) | (r.rgDrive() > 0 ? 1 << PwrPanels.F_RG_DOWN : 0)
                | (r.pressurizer().reliefOpen() || r.pressurizer().safetyOpen() ? 1 << PwrPanels.F_RELIEF : 0)
                | (r.safetyInjection() || acc.eccsFlowKgS() + acc.accumulatorFlowKgS() > 0 ? 1 << PwrPanels.F_SI : 0)
                | (r.leakKgS() > 0 || acc.breakArea() > 0 ? 1 << PwrPanels.F_LEAK : 0)
                | (r.heaterW() > 1e3 ? 1 << PwrPanels.F_HEATERS : 0);
        accident.readPanel(v, PwrPanels.ACCIDENT);
        v[PwrPanels.SGTR_KGS10] = (int) Math.round(r.tubeLeakKgS() * 10);
        v[PwrPanels.SG_P100] = (int) Math.round(sgtr.sgPressurePa() / 1e4);
        v[PwrPanels.SG_EXCESS_KG] = (int) Math.round(sgtr.excessWaterKg());
        v[PwrPanels.SG_VENTED_KG] = (int) Math.round(sgtr.toAtmosphereKg());
        v[PwrPanels.SG_FLAGS] = (sgtr.ruptured() ? 1 << PwrPanels.S_RUPTURED : 0) | (sgtr.n16Alarm() ? 1 << PwrPanels.S_N16 : 0)
                | (sgtr.isolated() ? 1 << PwrPanels.S_ISOLATED : 0) | (sgtr.reliefOpen() ? 1 << PwrPanels.S_RELIEF : 0)
                | (sgtr.stuckOpen() ? 1 << PwrPanels.S_STUCK : 0) | (r.porvOpen() ? 1 << PwrPanels.S_PORV : 0);
        ReactivityInitiatedAccident.Pulse p = r.lastPulse();
        if (r.rodClusterEjected() && p != null) {
            ReactivityInitiatedAccident.Damage d = r.lastRiaDamage();
            v[PwrPanels.RIA_CENTS] = (int) Math.round(p.dollars() * 100);
            v[PwrPanels.RIA_MJ] = (int) Math.round(p.energyJ() / 1e6);
            v[PwrPanels.RIA_FWHM_MS10] = (int) Math.round(p.fwhmS() * 1e4);
            v[PwrPanels.RIA_PEAK_CALG] = (int) Math.round(d.peakCalG());
            v[PwrPanels.RIA_LIMIT_CALG] = (int) Math.round(Math.min(ReactivityInitiatedAccident.HIGH_TEMP_CAL_G,
                    ReactivityInitiatedAccident.pcmiThresholdCalG(r.maxBurnup())));
            v[PwrPanels.RIA_FLAGS] = 1 << PwrPanels.R_EJECTED | (p.prompt() ? 1 << PwrPanels.R_PROMPT : 0)
                    | (d.cladFailedFraction() > 0 ? 1 << PwrPanels.R_FAILED : 0) | (d.fragmentedFraction() > 0 ? 1 << PwrPanels.R_FRAGMENTED : 0);
        } else if (r.rodClusterEjected()) {
            v[PwrPanels.RIA_FLAGS] = 1 << PwrPanels.R_EJECTED;
        }
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
            case PwrPanels.B_LOCA -> accident.cycleDrill(player);
            case PwrPanels.B_RIA -> rodEjectionDrill(player);
            case PwrPanels.B_SGTR -> {
                sgtr.rupture(!sgtr.ruptured());
                player.displayClientMessage(NppText.msg(sgtr.ruptured() ? "sgtr_on" : "sgtr_off")
                        .withStyle(sgtr.ruptured() ? ChatFormatting.RED : ChatFormatting.GREEN), true);
            }
            case PwrPanels.B_SG_ISOLATE -> {
                sgtr.setIsolated(!sgtr.isolated());
                player.displayClientMessage(NppText.msg(sgtr.isolated() ? "sg_isolated" : "sg_restored"), true);
            }
            case PwrPanels.B_PORV -> {
                r.setPorvOpen(!r.porvOpen());
                player.displayClientMessage(NppText.msg(r.porvOpen() ? "porv_open" : "porv_closed"), true);
            }
            default -> {
            }
        }
        setChanged();
    }

    /** Rod-ejection drill: eject a cluster, or put it back once the reactor is shut down. */
    private void rodEjectionDrill(ServerPlayer player) {
        if (reactor.rodClusterEjected()) {
            boolean back = reactor.reseatRodCluster();
            if (back) accident.setRodHousingBreak(false);
            player.displayClientMessage(NppText.msg(back ? "ria_reseated" : "ria_reseat_refused"), true);
            return;
        }
        ejectRod(PressurizedWaterReactor.EJECTED_RG_SHARE);
    }

    /**
     * Rod ejection (04c §4c.5): the cluster leaves the core with {@code share} of the control group's worth, the housing
     * nozzle opens a small break, a prompt burst fails the fuel past its enthalpy limits, and fuel past 230 cal/g
     * fragments into the coolant — a steam explosion in the core through the consequence router.
     *
     * @return the burst, or null when no cluster could be ejected
     */
    @Nullable
    public ReactivityInitiatedAccident.Pulse ejectRod(double share) {
        if (!(level instanceof ServerLevel sl)) return null;
        int failedBefore = reactor.failedAssemblies();
        ReactivityInitiatedAccident.Pulse p = reactor.ejectRodCluster(share);
        if (p == null) return null;
        accident.setRodHousingBreak(true);
        Vec3 c = worldPosition.getCenter();
        List<ServerPlayer> near = sl.getPlayers(pl -> pl.distanceToSqr(c) < 96 * 96);
        sl.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 2f, 0.5f);
        for (ServerPlayer pl : near)
            pl.displayClientMessage(NppText.msg("ria_eject", String.format(Locale.ROOT, "%.2f", p.dollars())).withStyle(ChatFormatting.RED), false);
        if (p.prompt()) {
            ReactivityInitiatedAccident.Damage d = reactor.lastRiaDamage();
            for (ServerPlayer pl : near)
                pl.displayClientMessage(NppText.msg("ria_burst", String.format(Locale.ROOT, "%.0f", p.energyJ() / 1e6),
                        String.format(Locale.ROOT, "%.1f", p.fwhmS() * 1e3), String.format(Locale.ROOT, "%.0f", d.peakCalG())).withStyle(ChatFormatting.DARK_RED), false);
            int failedNow = reactor.failedAssemblies() - failedBefore;
            if (failedNow > 0)
                for (ServerPlayer pl : near) pl.displayClientMessage(NppText.msg("ria_failed", failedNow).withStyle(ChatFormatting.DARK_RED), false);
            double fragmentedKg = d.fragmentedFraction() * reactor.core().heavyMetalKg() * ReactivityInitiatedAccident.UO2_PER_HM;
            if (fragmentedKg > 0) {
                SimLevelBinding b = SimBindings.get(sl);
                java.util.SplittableRandom rnd = new java.util.SplittableRandom(sl.getSeed() ^ worldPosition.asLong() ^ sl.getGameTime());
                if (b != null) accident.steamExplosion(sl, b, simId(), worldPosition, ReactivityInitiatedAccident.fciEnergyJ(fragmentedKg, rnd),
                        reactor.failedAssemblies());
            }
        }
        setChanged();
        return p;
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
        tag.putFloat("rods_out", (float) Math.max(0, Math.min(1, 1 - reactor.rg())));
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
        tag.putDouble("run_s", run.seconds());
        tag.put("fuel", fuel.serializeNBT(registries));
        MutableStateTree tree = new MutableStateTree();
        reactor.save(new TreeStateWriter(tree));
        tag.put("reactor", NbtStateTrees.toNbt(tree));
        tag.putBoolean("concentrate", concentrate);
        accident.save(tag);
        MutableStateTree sg = new MutableStateTree();
        sgtr.save(new TreeStateWriter(sg));
        tag.put("sgtr", NbtStateTrees.toNbt(sg));
        wiring.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        run.restore(tag.getDouble("run_s"));
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("reactor")) reactor.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("reactor"))));
        concentrate = tag.getBoolean("concentrate");
        accident.load(tag);
        if (tag.contains("sgtr")) sgtr.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("sgtr"))));
        wiring.load(tag);
        wasTripped = reactor.protection().isTripped();
        if (tag.contains("rods_out")) rodsOut = tag.getFloat("rods_out");
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
        return worldPosition.relative(wholeModelFacing(), 4).above();
    }
}
