package dev.denfry.promptcritical.mod.scada.device;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.grid.BreakerCellBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.grid.TransformerBlockEntity;
import dev.denfry.promptcritical.sim.grid.BreakerResponse;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.GridUnit;
import dev.denfry.promptcritical.sim.grid.MachineParams;
import dev.denfry.promptcritical.sim.grid.RelaySetting;
import dev.denfry.promptcritical.sim.grid.SwitchUnit;
import dev.denfry.promptcritical.sim.grid.SyncScope;
import dev.denfry.promptcritical.sim.grid.TapChanger;
import dev.denfry.promptcritical.sim.grid.TransformerUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Периферии электросети с собственными методами (07 §7.17.1–7.17.3) поверх общего {@code pc_device}: генератор,
 * ячейка или щит с выключателем, распределительный трансформатор. Методы отдают то, что есть у машины в моде; режимы
 * АРВ, кроме регулирования напряжения, регулятор скорости, PSS, ручное возбуждение и РПН у машин I1 нет — такие вызовы
 * отвечают {@code E_RANGE} с причиной, а не делают вид, что сработали.
 */
final class GridProfiles {
    private GridProfiles() {
    }

    /** {@code pc_generator}: генератор на валу Create и дизель-генератор (§7.17.1). */
    static DeviceProfile generator(String subtype) {
        return DeviceProfile.builder("pc_generator", subtype)
                .read("getState", 4, (b, s, a) -> generatorState(b, gen(b), syncSwitch(b)))
                .read("getCapability", 1, (b, s, a) -> capability(gen(b)))
                .read("getSyncScope", 1, (b, s, a) -> scope(b, syncSwitch(b) == null ? null : syncSwitch(b).syncScope()))
                .command("setVoltageTarget", a -> {
                    double v = LuaArgs.finite(a, 0);
                    return t -> {
                        GeneratorUnit u = gen(t);
                        double pu = v / u.voltageClass().volts;
                        if (pu < 0.9 || pu > 1.1)
                            throw new DeviceException(DeviceException.RANGE, "voltage target must be 0.9-1.1 of " + u.voltageClass().volts + " V");
                        u.setVoltageReferencePu(pu);
                    };
                })
                .command("setExcitationMode", a -> {
                    String mode = LuaArgs.string(a, 0).toUpperCase(Locale.ROOT);
                    if (!List.of("VOLTAGE", "REACTIVE", "POWER_FACTOR", "MANUAL").contains(mode))
                        throw new DeviceException(DeviceException.ARG, "mode must be VOLTAGE, REACTIVE, POWER_FACTOR or MANUAL");
                    if (!mode.equals("VOLTAGE"))
                        throw new DeviceException(DeviceException.RANGE, "this exciter regulates voltage only (07 §7.3.3)");
                    return t -> gen(t);
                })
                .command("fieldBreaker", a -> {
                    boolean on = LuaArgs.bool(a, 0);
                    return t -> {
                        GeneratorUnit u = gen(t);
                        if (on && !u.exciterPresent()) throw new DeviceException(DeviceException.FAULT, "no exciter on the generator");
                        u.setFieldOn(on);
                    };
                })
                .build();
    }

    /** {@code pc_breaker}: ячейка КРУ 6 кВ и щит 0,4 кВ (§7.17.2). */
    static DeviceProfile breaker(String subtype) {
        return DeviceProfile.builder("pc_breaker", subtype)
                .read("getState", 4, (b, s, a) -> breakerState(cell(b)))
                .read("getRelays", 4, (b, s, a) -> relays(cell(b)))
                .read("getSyncScope", 1, (b, s, a) -> scope(b, cell(b).syncScope()))
                .command("close", a -> t -> operate(t, true))
                .command("open", a -> t -> operate(t, false))
                .command("resetLockout", a -> {
                    // 03b §3b.1.8: сброс блокировки после 87/63 — у начальника смены; компьютер в сети — оператор
                    throw new DeviceException(DeviceException.ACCESS, "resetLockout needs SHIFT_SUPERVISOR, a computer is OPERATOR");
                })
                .build();
    }

    /** {@code pc_transformer}: распределительный трансформатор 6/0,4 кВ с ПБВ (§7.17.3). */
    static DeviceProfile transformer() {
        return DeviceProfile.builder("pc_transformer", "DISTRIBUTION")
                .read("getState", 4, (b, s, a) -> transformerState(trafo(b)))
                .command("setTap", a -> {
                    TapChanger taps = TransformerUnit.tapChanger();
                    int n = LuaArgs.integer(a, 0, taps.minStep(), taps.maxStep());
                    return t -> {
                        TransformerUnit u = trafo(t);
                        if (!u.setTap(n))
                            throw new DeviceException(DeviceException.INTERLOCK, "off-circuit tap changer: de-energize the transformer first");
                    };
                })
                .command("setTapMode", a -> {
                    String mode = LuaArgs.string(a, 0).toUpperCase(Locale.ROOT);
                    if (mode.equals("MANUAL")) return t -> trafo(t);
                    if (mode.equals("AUTO")) throw new DeviceException(DeviceException.RANGE, "no on-load tap changer on this transformer");
                    throw new DeviceException(DeviceException.ARG, "mode must be MANUAL or AUTO");
                })
                .build();
    }

    // ------------------------------------------------------------------ генератор

    static GeneratorUnit gen(BlockEntity be) throws DeviceException {
        GeneratorUnit u = be instanceof ShaftGeneratorBlockEntity sg ? sg.unit()
                : be instanceof GridDeviceBlockEntity<?> d && d.unit() instanceof GeneratorUnit g ? g : null;
        if (u == null) throw new DeviceException(DeviceException.UNFORMED, "generator is not in the grid yet");
        return u;
    }

    /** Ячейка, к присоединению (выводу 1) которой подключён генератор: по ней синхроноскоп и состояние выключателя. */
    @Nullable
    static SwitchUnit syncSwitch(BlockEntity be) throws DeviceException {
        GeneratorUnit u = gen(be);
        GridSubsystem g = be.getLevel() instanceof ServerLevel sl ? grid(sl) : null;
        if (g == null || u.node(0) == GridUnit.NO_NODE) return null;
        for (GridUnit x : g.units())
            if (x instanceof SwitchUnit s && s.node(1) == u.node(0)) return s;
        return null;
    }

    @Nullable
    private static GridSubsystem grid(ServerLevel level) {
        SimLevelBinding b = SimBindings.get(level);
        return b == null ? null : b.world().subsystem(GridSubsystem.class);
    }

    static Map<String, Object> generatorState(BlockEntity be, GeneratorUnit u, @Nullable SwitchUnit cb) throws DeviceException {
        double p = u.electricalPowerW(), q = u.reactivePowerVar(), sVa = Math.hypot(p, q), vt = u.terminalVoltageV();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("state", u.state().name());
        m.put("p", p);
        m.put("q", q);
        m.put("s", sVa);
        m.put("pf", sVa > 0 ? p / sVa : 1.0);
        m.put("vt", vt);
        m.put("it", vt > 0 ? sVa / (Math.sqrt(3) * vt) : 0.0);
        m.put("freq", u.frequencyHz());
        // электрическая угловая скорость ротора, рад/с (о.е. от 2π·50)
        m.put("speed", u.speedPu() * 2 * Math.PI * 50);
        m.put("excMode", u.fieldOn() ? "VOLTAGE" : "OFF");
        m.put("vref", u.voltageReferencePu() * u.voltageClass().volts);
        m.put("breaker", cb == null ? "NONE" : cb.breakerState().name());
        if (cb != null) {
            SyncScope sc = cb.syncScope();
            if (sc != null) m.put("sync", scope(be, sc));
        }
        m.put("shaftDamage", u.shaftDamage());
        m.put("statorDamage", u.statorDamage());
        return m;
    }

    /** Диаграмма мощности §7.3.4 при текущем напряжении на зажимах (или номинальном, если машина стоит). */
    static Map<String, Object> capability(GeneratorUnit u) {
        MachineParams mp = u.params();
        double sRated = u.ratedVa(), vt = u.terminalVoltageV(), v = vt > 0 ? vt / u.voltageClass().volts : 1;
        double pMaxPu = 0.8; // номинальный cos φ = 0,8 (§7.3.1): первичный двигатель рассчитан на 0,8·S_ном
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sRated", sRated);
        m.put("pMax", pMaxPu * sRated);
        List<Object> curve = new ArrayList<>();
        for (int i = 0; i <= 8; i++) {
            double p = pMaxPu * i / 8;
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("p", p * sRated);
            pt.put("qMax", mp.qMaxPu(p, v) * sRated);
            pt.put("qMin", mp.qMinPu(p, v) * sRated);
            curve.add(pt);
        }
        m.put("curve", curve);
        return m;
    }

    /** {@code {df, dtheta, dv, advanceAngle, inWindow}}: окно — уставки реле 25 мира {@code grid.sync_max_*} (IEEE C50.13). */
    static Map<String, Object> scope(BlockEntity be, @Nullable SyncScope sc) throws DeviceException {
        GridSubsystem g = be.getLevel() instanceof ServerLevel sl ? grid(sl) : null;
        if (sc == null || g == null) throw new DeviceException(DeviceException.FAULT, "no breaker with voltage on both sides");
        var cfg = g.config();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("df", sc.slipHz());
        m.put("dtheta", sc.angleRad());
        m.put("dv", sc.dvPu());
        m.put("advanceAngle", sc.advanceAngleRad());
        m.put("inWindow", sc.inWindow(Math.toRadians(cfg.syncMaxAngleDeg()), cfg.syncMaxDv(), cfg.syncMaxSlipHz()));
        return m;
    }

    // ------------------------------------------------------------------ выключатель

    static SwitchUnit cell(BlockEntity be) throws DeviceException {
        SwitchUnit u = be instanceof BreakerCellBlockEntity c ? c.unit() : null;
        if (u == null) throw new DeviceException(DeviceException.UNFORMED, "breaker is not in the grid yet");
        return u;
    }

    static Map<String, Object> breakerState(SwitchUnit u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("state", u.breakerState().name());
        m.put("current", u.currentA());
        m.put("voltage", u.voltageV(0));
        m.put("p", u.powerW());
        m.put("freq", u.frequencyHz(0));
        m.put("ratedA", u.ratedA());
        m.put("dcOk", u.controlPower());
        m.put("interlock", u.interlocked());
        m.put("earthSwitch", u.earthed());
        m.put("loto", u.lockedOut());   // замок LOTO на приводе (13 §13.1.3)
        m.put("protectionTrips", u.protectionTrips());
        m.put("energyForwardKwh", u.energyForwardKwh());
        m.put("energyReverseKwh", u.energyReverseKwh());
        return m;
    }

    /** Модули РЗА присоединения: уставки только на чтение (§7.17.2, меняются в шкафу руками). */
    static List<Object> relays(SwitchUnit u) {
        List<Object> out = new ArrayList<>();
        for (RelaySetting r : u.relays()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("func", ansi(r.kind()));
            Map<String, Object> set = new LinkedHashMap<>();
            set.put("a", r.a());
            set.put("b", r.b());
            m.put("setting", set);
            m.put("gen", r.generation().name());
            out.add(m);
        }
        return out;
    }

    static String ansi(RelaySetting.Kind k) {
        return switch (k) {
            case OVERCURRENT -> "51";
            case SYNC_CHECK -> "25";
            case REVERSE_POWER -> "32R";
            case UNDERFREQUENCY -> "81U";
            case UNDERVOLTAGE -> "27";
            case THERMAL -> "49";
            case UFLS -> "UFLS";
            case AUTORECLOSE -> "79";
            case BREAKER_FAILURE -> "50BF";
        };
    }

    private static void operate(BlockEntity be, boolean close) throws DeviceException {
        if (!(be instanceof BreakerCellBlockEntity c)) throw new DeviceException(DeviceException.UNFORMED, "not a breaker");
        BreakerResponse r = c.operate(null, close);
        switch (r) {
            case ACCEPTED -> {
            }
            case INTERLOCK -> throw new DeviceException(DeviceException.INTERLOCK, "interlock");
            case PERMIT -> throw new DeviceException(DeviceException.INTERLOCK, "a work permit is open on this feeder");
            case LOCKED_OUT -> throw new DeviceException(DeviceException.INTERLOCK, "LOCKED_OUT");
            case BUSY -> throw new DeviceException(DeviceException.BUSY, "the drive is still operating");
            case NO_CONTROL_POWER -> throw new DeviceException(DeviceException.FAULT, "no control power");
            case UNAVAILABLE -> throw new DeviceException(DeviceException.FAULT, "breaker unavailable");
        }
    }

    // ------------------------------------------------------------------ трансформатор

    static TransformerUnit trafo(BlockEntity be) throws DeviceException {
        TransformerUnit u = be instanceof TransformerBlockEntity t ? t.unit() : null;
        if (u == null) throw new DeviceException(DeviceException.UNFORMED, "transformer is not in the grid yet");
        return u;
    }

    static Map<String, Object> transformerState(TransformerUnit u) {
        TapChanger taps = TransformerUnit.tapChanger();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("load", u.loading());
        m.put("hotSpot", u.hotSpotC() + 273.15);
        m.put("tap", u.tap());
        m.put("tapRange", Map.of("min", taps.minStep(), "max", taps.maxStep()));
        m.put("tapMode", "MANUAL");
        m.put("energized", u.energized());
        m.put("current", u.currentA());
        m.put("p", u.powerW());
        m.put("lossOfLifeH", u.lossOfLifeH());
        m.put("cooling", "AN");
        return m;
    }
}
