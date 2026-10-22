package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.mine.MineHoistBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.ops.CheckInput;
import dev.denfry.promptcritical.sim.ops.CheckResult;
import dev.denfry.promptcritical.sim.ops.CommissioningCheck;
import dev.denfry.promptcritical.sim.ops.CommissioningMode;
import dev.denfry.promptcritical.sim.ops.CommissioningService;
import dev.denfry.promptcritical.sim.ops.CommissioningState;
import dev.denfry.promptcritical.sim.ops.MaintenanceFaultKind;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.PlainBearing;
import dev.denfry.promptcritical.sim.ops.Probe;
import dev.denfry.promptcritical.sim.ops.UnitComponent;
import dev.denfry.promptcritical.sim.ops.UnitKind;
import dev.denfry.promptcritical.sim.plant.BoilerUnit;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Работы инструментами и запчастями среза (13 §13.6.5–13.6.7) по машине, её щиту обслуживания или манометру:
 * замеры (стетоскоп, рука), проверки пусконаладки (контрольный манометр, ключ на ПК и тормоз), подчеканка шва,
 * замена ПК, смазка и ремонты. Условие среза одно — {@code STOP}: в {@code ENFORCED} работа на ходу запрещена, в
 * {@code ADVISORY} возможна с травмой и записью {@code UNSAFE_MAINTENANCE}.
 */
public final class OpsActions {
    /** Урон от вращающихся частей при ремонте на ходу и от горячего корпуса. */
    public static final float UNSAFE_DAMAGE = 4, SCALD_DAMAGE = 1;
    public static final double AMBIENT_K = 288.15, HYDRO_COLD_K = 333, DRAINED_KG = 200;
    private static final double REACH_SQ = 6 * 6;

    /** Начатая долгая работа игрока. */
    private record Pending(BlockPos pos, OpsItem.Kind kind, boolean unsafe) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private OpsActions() {
    }

    // --- события ---

    /** ПКМ по блоку предметом среза или Shift + пустой рукой по машине. */
    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        ItemStack stack = e.getItemStack();
        boolean hand = stack.isEmpty() && p.isShiftKeyDown() && e.getHand() == InteractionHand.MAIN_HAND;
        if (!(stack.getItem() instanceof OpsItem) && !hand) return;
        Level level = e.getLevel();
        OpsMachine m = OpsWorld.machine(level, e.getPos());
        if (m == null) return;
        if (hand && OpsWorld.unit(level, m.simId()) == null && !level.isClientSide) return;
        InteractionResult r;
        if (level.isClientSide) {
            if (stack.getItem() instanceof OpsItem item && item.kind().lasting()) p.startUsingItem(e.getHand());
            r = InteractionResult.SUCCESS;
        } else {
            r = hand ? feel(p, m) : use(p, e.getHand(), stack, m, e.getPos());
        }
        if (r == InteractionResult.PASS) return;
        e.setCanceled(true);
        e.setCancellationResult(r);
    }

    // --- работы ---

    /** Применить предмет к машине (сервер). Долгая работа начинается; мгновенная — выполняется. */
    public static InteractionResult use(Player p, InteractionHand hand, ItemStack stack, OpsMachine m, BlockPos pos) {
        if (!(stack.getItem() instanceof OpsItem item)) return InteractionResult.PASS;
        OpsSubsystem ops = OpsWorld.ops(p.level());
        OpsSubsystem.Unit u = ops == null ? null : ops.unit(m.simId());
        if (u == null) return InteractionResult.PASS;
        OpsItem.Kind kind = item.kind();
        if (!kind.lasting()) {
            instant(p, stack, kind, ops, u, m);
            return InteractionResult.CONSUME;
        }
        Boolean unsafe = admit(p, kind, ops, u, m);
        if (unsafe == null) {
            p.stopUsingItem();
            return InteractionResult.CONSUME;
        }
        PENDING.put(p.getUUID(), new Pending(pos, kind, unsafe));
        p.startUsingItem(hand);
        p.displayClientMessage(OpsText.msg("working", stack.getHoverName()), true);
        return InteractionResult.CONSUME;
    }

    /**
     * Можно ли начать долгую работу: null — нельзя (сообщение уже показано), иначе — «на ходу» ли она.
     */
    @Nullable
    private static Boolean admit(Player p, OpsItem.Kind kind, OpsSubsystem ops, OpsSubsystem.Unit u, OpsMachine m) {
        MaintenanceFaultKind fault = fault(kind);
        if (fault == null || OpsWorld.index(u, fault) < 0) {
            p.displayClientMessage(OpsText.msg("no_fault", OpsText.of(fault == null ? MaintenanceFaultKind.BEARING_WEAR : fault)), true);
            return null;
        }
        if (kind == OpsItem.Kind.OIL_CAN) {
            if (!has(p, PcItems.TALLOW.get().getDefaultInstance())) {
                p.displayClientMessage(OpsText.msg("need_tallow"), true);
                return null;
            }
            return false;
        }
        if (kind == OpsItem.Kind.BOILER_SCRAPER) {
            if (!(m instanceof FireTubeBoilerBlockEntity b) || !drainedAndCold(b)) {
                p.displayClientMessage(OpsText.msg("scrape_need"), true);
                return null;
            }
            return false;
        }
        if (m.opsStopped()) return false;
        if (ops.config().commissioning() == CommissioningMode.ENFORCED) {
            p.displayClientMessage(OpsText.msg("need_stop"), true);
            return null;
        }
        return true;
    }

    /** Долгая работа держится, пока игрок рядом с машиной. */
    public static boolean stillValid(Player p) {
        Pending w = PENDING.get(p.getUUID());
        return w != null && p.distanceToSqr(w.pos.getCenter()) <= REACH_SQ;
    }

    public static void cancel(Player p) {
        PENDING.remove(p.getUUID());
    }

    /** Долгая работа доделана (сервер). */
    public static void finish(Player p, ItemStack stack) {
        Pending w = PENDING.remove(p.getUUID());
        if (w == null) return;
        OpsMachine m = OpsWorld.machine(p.level(), w.pos);
        OpsSubsystem ops = OpsWorld.ops(p.level());
        OpsSubsystem.Unit u = m == null || ops == null ? null : ops.unit(m.simId());
        if (u == null) return;
        complete(p, stack, w.kind, w.unsafe, ops, u, m);
    }

    /** Выполнить работу сразу (GameTest, долгие работы по окончании). */
    public static boolean complete(Player p, ItemStack stack, OpsItem.Kind kind, boolean unsafe, OpsSubsystem ops,
            OpsSubsystem.Unit u, OpsMachine m) {
        MaintenanceFaultKind fault = fault(kind);
        int idx = fault == null ? -1 : OpsWorld.index(u, fault);
        if (idx < 0) return false;
        if (kind == OpsItem.Kind.OIL_CAN && !take(p, PcItems.TALLOW.get().getDefaultInstance())) {
            p.displayClientMessage(OpsText.msg("need_tallow"), true);
            return false;
        }
        if (unsafe) p.hurt(p.damageSources().generic(), UNSAFE_DAMAGE);
        ops.repair(m.simId(), idx, p.getUUID(), unsafe);
        if (kind != OpsItem.Kind.OIL_CAN && kind != OpsItem.Kind.BOILER_SCRAPER && !p.getAbilities().instabuild) stack.shrink(1);
        p.displayClientMessage(unsafe ? OpsText.msg("unsafe") : OpsText.msg("repaired", OpsText.of(fault)), true);
        play(p, m.opsBearingPos(), SoundEvents.ANVIL_USE, 0.5f, 1.4f);
        return true;
    }

    @Nullable
    private static MaintenanceFaultKind fault(OpsItem.Kind kind) {
        return switch (kind) {
            case OIL_CAN -> MaintenanceFaultKind.LUBRICATION;
            case BOILER_SCRAPER -> MaintenanceFaultKind.HX_FOULING;
            case BEARING_SHELL -> MaintenanceFaultKind.BEARING_WEAR;
            case IMPELLER -> MaintenanceFaultKind.IMPELLER_EROSION;
            case GLAND_PACKING -> MaintenanceFaultKind.PACKING_LEAK;
            default -> null;
        };
    }

    /** Мгновенные работы: замеры и проверки. */
    public static void instant(Player p, ItemStack stack, OpsItem.Kind kind, OpsSubsystem ops, OpsSubsystem.Unit u,
            OpsMachine m) {
        switch (kind) {
            case STETHOSCOPE -> stethoscope(p, ops, u, m);
            case TORQUE_WRENCH -> {
                if (m instanceof FireTubeBoilerBlockEntity b) reliefValve(p, ops, b);
                else if (m instanceof MineHoistBlockEntity h) report(p, ops.perform(m.simId(),
                        new CheckInput.BrakeTest(h.cageLoadFraction()), p.getUUID()));
                else tighten(p, ops, m);
            }
            case TEST_GAUGE -> {
                if (m instanceof FireTubeBoilerBlockEntity) report(p, ops.perform(m.simId(), new CheckInput.InstrumentCheck(), p.getUUID()));
            }
            case CAULKING_HAMMER -> {
                if (!(m instanceof FireTubeBoilerBlockEntity b)) return;
                CommissioningService c = ops.commissioning();
                if (!c.has(m.simId(), dev.denfry.promptcritical.sim.ops.CommissioningDefectKind.SEAM_WEEP)) {
                    p.displayClientMessage(OpsText.msg("caulk_none"), true);
                } else if (c.caulk(m.simId(), drainedAndCold(b))) {
                    p.displayClientMessage(OpsText.msg("caulked"), true);
                    play(p, m.opsBearingPos(), SoundEvents.ANVIL_PLACE, 0.6f, 1.6f);
                } else {
                    p.displayClientMessage(OpsText.msg("caulk_need"), true);
                }
            }
            case SAFETY_VALVE -> {
                if (!(m instanceof FireTubeBoilerBlockEntity b)) return;
                BoilerUnit bu = b.unit();
                if (bu == null || !(bu.pressurePa() < SteamPlant.OPEN_PRESSURE_PA)) {
                    p.displayClientMessage(OpsText.msg("psv_need"), true);
                    return;
                }
                ops.replaceComponent(m.simId(), UnitComponent.RELIEF_VALVE, p.getUUID());
                if (!p.getAbilities().instabuild) stack.shrink(1);
                p.displayClientMessage(OpsText.msg("psv_replaced"), true);
            }
            default -> {
            }
        }
    }

    private static void stethoscope(Player p, OpsSubsystem ops, OpsSubsystem.Unit u, OpsMachine m) {
        if (u.wear() == null) return;
        if (m.opsStopped()) {
            p.displayClientMessage(OpsText.msg("stopped"), true);
            return;
        }
        int bearing = OpsWorld.index(u, MaintenanceFaultKind.BEARING_WEAR);
        int packing = OpsWorld.index(u, MaintenanceFaultKind.PACKING_LEAK);
        if (bearing >= 0) ops.measure(m.simId(), bearing, Probe.STETHOSCOPE);
        if (packing >= 0) ops.measure(m.simId(), packing, Probe.STETHOSCOPE);
        var s = u.wear().stethoscope();
        p.displayClientMessage(OpsText.of(s), true);
        SoundEvent sound = switch (s) {
            case HUM -> SoundEvents.BEACON_AMBIENT;
            case RUSTLE -> SoundEvents.GRAVEL_STEP;
            case SCRAPE -> SoundEvents.GRINDSTONE_USE;
            case KNOCK -> SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR;
            case HISS -> SoundEvents.FIRE_EXTINGUISH;
        };
        p.playNotifySound(sound, SoundSource.BLOCKS, 0.6f, 0.8f);
    }

    /** Рукой по корпусу (Shift + пустая рука): подшипник со стадии 3; обжигает — 1 ед. урона. */
    private static InteractionResult feel(Player p, OpsMachine m) {
        OpsSubsystem ops = OpsWorld.ops(p.level());
        OpsSubsystem.Unit u = ops == null ? null : ops.unit(m.simId());
        if (u == null || u.wear() == null) return InteractionResult.PASS;
        PlainBearing b = u.wear().bearing();
        double k = m.opsStopped() ? AMBIENT_K : b.housingTempK(AMBIENT_K);
        int idx = OpsWorld.index(u, MaintenanceFaultKind.BEARING_WEAR);
        if (idx >= 0 && !m.opsStopped()) ops.measure(m.simId(), idx, Probe.HAND);
        PlainBearing.HandFeel f = PlainBearing.feel(k);
        p.displayClientMessage(OpsText.of(f), true);
        if (f == PlainBearing.HandFeel.SCALDING) p.hurt(p.damageSources().hotFloor(), SCALD_DAMAGE);
        return InteractionResult.CONSUME;
    }

    /** Ключом по котлу при подрыве ПК или на уставке: проверка ПК с регулировкой. */
    private static void reliefValve(Player p, OpsSubsystem ops, FireTubeBoilerBlockEntity b) {
        BoilerUnit u = b.unit();
        if (u == null) return;
        double set = u.spec().reliefSetPressure();
        if (!u.reliefOpen() && !(u.pressurePa() >= set)) {
            p.displayClientMessage(OpsText.msg("relief_not_lifting", OpsText.num(set / 1e6, 2), OpsText.num(u.pressurePa() / 1e6, 2)), true);
            return;
        }
        CheckResult r = ops.perform(b.simId(), new CheckInput.ReliefValveSet(has(p, PcItems.TEST_PRESSURE_GAUGE.get().getDefaultInstance())),
                p.getUUID());
        if (r != null && r.outcome() != CheckResult.Outcome.REFUSED) {
            p.displayClientMessage(OpsText.msg("relief_lift", OpsText.num(r.readings().getOrDefault(CheckResult.READ_LIFT, set) / 1e6, 2),
                    OpsText.num(r.readings().getOrDefault(CheckResult.READ_RESEAT, set) / 1e6, 2), OpsText.num(set / 1e6, 2)), false);
        }
        report(p, r);
    }

    private static void tighten(Player p, OpsSubsystem ops, OpsMachine m) {
        OpsSubsystem.Unit u = ops.unit(m.simId());
        if (u == null || u.wear() == null || u.wear().packing() == null) return;
        if (ops.tightenPacking(m.simId(), p.getUUID())) {
            p.displayClientMessage(OpsText.msg("packing_tightened", u.wear().packing().tightenings()), true);
            play(p, m.opsBearingPos(), SoundEvents.CHAIN_PLACE, 0.6f, 1.2f);
        } else {
            p.displayClientMessage(OpsText.msg("packing_spent"), true);
        }
    }

    /** Итог проверки игроку: пройдена, найденные дефекты, причины отказа; приёмка. */
    public static void report(Player p, @Nullable CheckResult r) {
        if (r == null) return;
        Component check = OpsText.of(r.check());
        switch (r.outcome()) {
            case PASSED -> p.displayClientMessage(OpsText.msg("check_passed", check), false);
            case REFUSED -> p.displayClientMessage(OpsText.msg("check_refused", check, OpsText.list(r.unmet())), false);
            case FAILED -> {
                var found = java.util.EnumSet.noneOf(dev.denfry.promptcritical.sim.ops.CommissioningDefectKind.class);
                for (var d : r.found()) found.add(d.kind());
                p.displayClientMessage(OpsText.msg("check_failed", check, OpsText.list(found)), false);
            }
        }
        OpsSubsystem ops = OpsWorld.ops(p.level());
        if (r.passed() && ops != null) {
            for (var d : r.found()) p.displayClientMessage(OpsText.of(d.kind()), false);
        }
    }

    /** Сообщение об итоге приёмки после проверки, если установка только что принята. */
    public static void announceAcceptance(Player p, OpsSubsystem ops, OpsMachine m, CommissioningState before) {
        if (before != CommissioningState.ACCEPTED
                && ops.commissioning().state(m.simId()).orElse(null) == CommissioningState.ACCEPTED)
            p.displayClientMessage(OpsText.msg("accepted"), false);
    }

    /** Котёл без давления, остывший и спущенный (чистка, подчеканка). */
    public static boolean drainedAndCold(FireTubeBoilerBlockEntity b) {
        BoilerUnit u = b.unit();
        return u != null && !u.lit() && !(u.waterTempK() >= HYDRO_COLD_K) && u.waterMassKg() < DRAINED_KG;
    }

    // --- предметы игрока ---

    private static boolean has(Player p, ItemStack like) {
        if (p.getAbilities().instabuild) return true;
        for (ItemStack s : p.getInventory().items) if (ItemStack.isSameItem(s, like)) return true;
        return ItemStack.isSameItem(p.getOffhandItem(), like);
    }

    private static boolean take(Player p, ItemStack like) {
        if (p.getAbilities().instabuild) return true;
        for (ItemStack s : p.getInventory().items) {
            if (ItemStack.isSameItem(s, like)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static void play(Player p, Vec3 at, SoundEvent s, float volume, float pitch) {
        p.level().playSound(null, at.x, at.y, at.z, s, SoundSource.BLOCKS, volume, pitch);
    }

    /** Проверки, которые ведёт сама машина (толчок, заливка), — тем же отчётом. */
    public static void perform(Player p, OpsMachine m, CheckInput input) {
        OpsSubsystem ops = OpsWorld.ops(p.level());
        if (ops == null) return;
        CommissioningState before = ops.commissioning().state(m.simId()).orElse(null);
        report(p, ops.perform(m.simId(), input, p.getUUID()));
        announceAcceptance(p, ops, m, before);
    }

    /**
     * Розжиг или пуск привода (13 §13.6.6): в {@code ENFORCED} без проверок — отказ с перечнем; непринятая установка в
     * {@code ADVISORY} пускается с предупреждением и записью в журнал. {@code player} — null, если пускает автоматика.
     */
    public static boolean startAllowed(@Nullable Player player, Level level, OpsMachine m) {
        OpsSubsystem ops = OpsWorld.ops(level);
        if (ops == null || ops.unit(m.simId()) == null) return true;
        var permit = ops.requestStart(m.simId());
        if (player != null) {
            if (!permit.allowed()) player.displayClientMessage(OpsText.msg("start_refused", OpsText.list(permit.missing())), false);
            else if (!permit.accepted()) player.displayClientMessage(OpsText.msg("start_unaccepted"), true);
        }
        return permit.allowed();
    }

    static boolean isCheck(CommissioningCheck c, UnitKind kind) {
        return kind.checklist().contains(c);
    }
}
