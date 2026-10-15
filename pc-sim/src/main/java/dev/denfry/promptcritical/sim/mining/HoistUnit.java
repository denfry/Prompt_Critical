package dev.denfry.promptcritical.sim.mining;

import dev.denfry.promptcritical.sim.core.DeviceSubsystem;
import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.Severity;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import java.util.Arrays;
import java.util.Objects;

/**
 * Двойник {@code mine_hoist} (08 §8.2.8): подъёмная машина Ц-2,5 на валу Create, канат и клеть {@link Hoist}.
 * Игрок выбирает посадку на панели, тахограмму строит машина: разгон и замедление с проектным ускорением, скорость
 * хода — меньшая из предела панели и той, что тянет привод: v = 0,9·P/(m·g). Вал Create даёт мощность
 * P = P_ном·min(1, n/n_ном), но не больше свободной мощности сети (её пишет биндинг) — сеть не перегружается.
 * Регулятор скорости — пропорциональный с постоянной {@link #SPEED_TAU_S}, момент на барабане — из баланса сил;
 * без мощности на ходу срабатывает защита по потере питания. Спуск груза двигатель не тормозит током: энергия
 * уходит в реостат [допущение], валу — только холостые потери.
 *
 * <p>Глубины посадок отсчитываются от верхней (0) вниз, их и глубину ствола находит привязка по стволу под копром.
 * Канат ставится предметами {@code wire_rope}; без каната или ствола подъём не собран.
 */
public final class HoistUnit implements DeviceSubsystem.Unit {
    public static final String DEVICE_TYPE = "promptcritical:mine_hoist";
    public static final double NOMINAL_RPM = 64;
    /** Мощность привода на номинале вала, Вт [BALANCE: Ц-2,5 — до 1 МВт, I0 — паровая машина]. */
    public static final double RATED_W = 400_000;
    public static final double IDLE_SHARE = 0.02;
    /** Ц-2,5: барабан ⌀ 2,5 м, J с редуктором, шкив копра 3 м (D/d ≥ 79 до 38 мм каната). */
    public static final double DRUM_RADIUS_M = 1.25, DRUM_INERTIA = 20_000, SHEAVE_DIAMETER_M = 3.0;
    /** Клеть 3 т (T8.6), тормоз 300 кН, трение в проводниках, проектное ускорение. */
    public static final double CAGE_MASS_KG = 3000, BRAKE_N = 300_000, GUIDE_FRICTION_N = 500, ACCEL_MS2 = 1.0;
    /** Клеть стоит на посадке: ближе 2 см и медленнее 5 см/с. */
    public static final double ARRIVAL_M = 0.02, ARRIVAL_MS = 0.05;
    /** Постоянная контура скорости, с; коэффициент дотягивания до посадки v = K·d, 1/с. */
    public static final double SPEED_TAU_S = 0.3, CREEP_GAIN = 1.0;

    public final ShaftInput shaft = new ShaftInput();
    private HoistConfig cfg;
    private RopeState rope;
    private Hoist hoist;
    private double[] landings = new double[0];
    private int target = -1, lastLanding = -1;
    private double speedLimit = Hoist.MAX_SPEED;
    private double spareW = Double.POSITIVE_INFINITY;
    private double powerW, availableW;
    private HoistState reported = HoistState.HELD;
    private boolean wearReported, unsafePending;

    public HoistUnit(HoistConfig cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    @Override
    public String deviceType() {
        return DEVICE_TYPE;
    }

    void setConfig(HoistConfig cfg) {
        this.cfg = cfg;
    }

    // --- сборка ---------------------------------------------------------------------------------------------

    private static Hoist.Spec spec(double depthM) {
        return new Hoist.Spec(depthM, DRUM_RADIUS_M, DRUM_INERTIA, SHEAVE_DIAMETER_M, CAGE_MASS_KG, BRAKE_N,
                GUIDE_FRICTION_N, ACCEL_MS2, true, false);
    }

    /**
     * Ствол под копром: глубины посадок от верхней, по возрастанию, первая — 0. Меньше двух посадок — ствола нет.
     * Если клеть ехала, а ствол изменился, ход прекращается.
     */
    public void setShaft(double[] depthsM) {
        double[] d = depthsM.clone();
        Arrays.sort(d);
        if (Arrays.equals(d, landings)) return;
        landings = d;
        target = -1;
        rebuild();
    }

    /** Повесить канат (новый или снятый с другой машины). Оборванный канат при этом заменяется. */
    public void installRope(RopeState rope) {
        this.rope = Objects.requireNonNull(rope, "rope");
        hoist = null;
        target = -1;
        wearReported = false;
        rebuild();
    }

    private void rebuild() {
        if (rope == null || landings.length < 2 || !(landings[landings.length - 1] > 0)) {
            if (hoist != null) rope = hoist.rope();
            hoist = null;
            return;
        }
        Hoist.Spec s = spec(landings[landings.length - 1]);
        hoist = hoist == null ? new Hoist(s, rope, cfg) : new Hoist(s, hoist);
        hoist.setSpeedLimit(speedLimit);
    }

    /** Длина каната на этот ствол: глубина плюс подвеска через копёр к барабану, м. */
    public static double ropeLengthM(double depthM, double reachM) {
        return depthM + reachM;
    }

    // --- команды панели (серверный поток между тиками) ----------------------------------------------------

    /**
     * Отправить клеть на посадку с грузом. Нормы запаса прочности проверяет {@link Hoist#load}.
     *
     * @return вердикт загрузки; {@code null} — подъём не собран, канат оборван, стоит защита или посадки нет
     */
    public Hoist.Verdict depart(int landing, HoistDuty duty, double payloadKg) {
        if (hoist == null || landing < 0 || landing >= landings.length) return null;
        HoistState s = hoist.state();
        if (s.broken() || s.tripped()) return null;
        Hoist.Verdict v = hoist.load(duty, payloadKg);
        if (v == Hoist.Verdict.FORBIDDEN) return v;
        if (v == Hoist.Verdict.UNSAFE_OPERATION) unsafePending = true;
        target = landing;
        return v;
    }

    /**
     * Клеть стоит блоками в стволе на глубине {@code depthM} (поставлена игроком или разобрана после хода): привести
     * двойник к ней. Только пока клеть стоит.
     */
    public void seatCage(double depthM) {
        if (hoist == null || target >= 0 || hoist.speedMs() != 0) return;
        hoist.reposition(depthM);
        for (int i = 0; i < landings.length; i++) if (Math.abs(landings[i] - depthM) < 0.25) lastLanding = i;
    }

    /** Остановить ход: замедление по тахограмме и тормоз. */
    public void stop() {
        target = -1;
    }

    /** Сброс защиты. */
    public void reset() {
        if (hoist != null) hoist.reset();
    }

    public void setSpeedLimit(double ms) {
        speedLimit = Math.max(0.5, Math.min(Hoist.MAX_SPEED, ms));
        if (hoist != null) hoist.setSpeedLimit(speedLimit);
    }

    /** Свободная мощность сети Create сверх нагрузок других машин (с учётом своей), Вт. */
    public void setSparePowerW(double w) {
        spareW = Math.max(0, w);
    }

    // --- шаг ------------------------------------------------------------------------------------------------

    public void step(FastContext ctx, SimId id) {
        if (hoist == null) {
            powerW = 0;
            availableW = 0;
            return;
        }
        double rpm = shaft.rpm(ctx.tick());
        step(ctx.dt(), rpm <= 0 ? 0 : Math.min(RATED_W * Math.min(1, rpm / NOMINAL_RPM), spareW));
        report(ctx, id);
    }

    /** Шаг FAST без событий при мощности привода {@code available}, Вт. */
    void step(double dt, double available) {
        availableW = available;
        double v = hoist.speedMs();
        double m = hoist.suspendedMassKg();
        HoistState st = hoist.state();
        double vRef = 0, ff = 0;
        if (target >= 0 && !st.tripped() && !st.broken()) {
            double dist = landings[target] - hoist.positionM();          // + — вниз
            if (Math.abs(dist) < ARRIVAL_M && Math.abs(v) < ARRIVAL_MS) {
                lastLanding = target;
                target = -1;
            } else if (availableW <= 0) {
                if (v != 0) hoist.tripPowerLoss();         // на ходу — защита; стоящая клеть ждёт вала на тормозе
                if (hoist.state().tripped()) target = -1;
            } else {
                boolean up = dist < 0;
                double cruise = up ? Math.min(speedLimit, 0.9 * availableW / (m * Hoist.G)) : speedLimit;
                // ход → замедление по √(2ad) → дотягивание v = K·d (без перебега: защита переподъёма строгая)
                double d = Math.abs(dist), braking = Math.sqrt(2 * ACCEL_MS2 * d), creep = CREEP_GAIN * d;
                double vAbs = Math.min(cruise, Math.min(braking, creep));
                double decel = vAbs == cruise ? 0 : vAbs == braking ? ACCEL_MS2 : CREEP_GAIN * Math.abs(v);
                vRef = up ? vAbs : -vAbs;
                ff = up ? -decel : decel;
            }
        }
        boolean moving = target >= 0 || Math.abs(v) >= ARRIVAL_MS;
        boolean brake = !moving || hoist.state().tripped() || availableW <= 0;
        double force = 0;
        if (!brake) {
            double aCmd = Math.max(-1.5 * ACCEL_MS2, Math.min(1.5 * ACCEL_MS2, ff + (vRef - v) / SPEED_TAU_S));
            force = hoist.effectiveMassKg() * aCmd + m * Hoist.G + Math.signum(v == 0 ? vRef : v) * GUIDE_FRICTION_N;
            if (v > 0.1 && force * v > availableW) force = availableW / v;
        }
        hoist.stepFast(dt, force * DRUM_RADIUS_M, brake);
        double motoring = Math.max(0, force * hoist.speedMs());
        powerW = (availableW > 0 ? IDLE_SHARE * RATED_W : 0) + motoring;
    }

    private void report(FastContext ctx, SimId id) {
        HoistState s = hoist.state();
        if (s != reported) {
            if (s.tripped()) ctx.events().journal(ctx.tick(), id, Severity.WARNING, "hoist " + s.name());
            if (s.broken()) ctx.events().journal(ctx.tick(), id, Severity.CRITICAL, "hoist rope broken: " + s.name());
            reported = s;
        }
        if (hoist.ropeWearAlarm() && !wearReported) {
            ctx.events().journal(ctx.tick(), id, Severity.WARNING, "ROPE_WEAR");
            wearReported = true;
        }
        if (unsafePending) {
            ctx.events().journal(ctx.tick(), id, Severity.WARNING, "UNSAFE_OPERATION: rope safety factor below norm");
            unsafePending = false;
        }
    }

    // --- показания ------------------------------------------------------------------------------------------

    /** Подъём собран (есть канат и ствол). */
    public boolean ready() {
        return hoist != null;
    }

    public Hoist hoist() {
        return hoist;
    }

    /** Канат (висящий или снятый); {@code null} — каната нет. */
    public RopeState rope() {
        return hoist != null ? hoist.rope() : rope;
    }

    public double[] landings() {
        return landings.clone();
    }

    /** Посадка, куда идёт клеть; −1 — стоит. */
    public int target() {
        return target;
    }

    /** Посадка, на которой клеть остановилась последней; −1 — ещё не останавливалась. */
    public int lastLanding() {
        return lastLanding;
    }

    /** Посадка, на которой клеть стоит сейчас (с точностью {@link #ARRIVAL_M}), или −1. */
    public int landingAtCage() {
        if (hoist == null || target >= 0 || Math.abs(hoist.speedMs()) >= ARRIVAL_MS) return -1;
        for (int i = 0; i < landings.length; i++) if (Math.abs(landings[i] - hoist.positionM()) < 0.25) return i;
        return -1;
    }

    /** Глубина клети, м (0 — верхняя посадка). */
    public double positionM() {
        return hoist == null ? 0 : hoist.positionM();
    }

    public double speedMs() {
        return hoist == null ? 0 : hoist.speedMs();
    }

    public double speedLimitMs() {
        return speedLimit;
    }

    /** Мощность на валу сейчас, Вт (→ стресс Create). */
    public double powerW() {
        return powerW;
    }

    /** Мощность, которую привод может взять с вала, Вт. */
    public double availableW() {
        return availableW;
    }

    // --- сохранение -----------------------------------------------------------------------------------------

    @Override
    public void save(StateWriter out) {
        out.putDoubles("landings", landings, landings.length);
        out.putInt("target", target);
        out.putInt("last_landing", lastLanding);
        out.putDouble("speed_limit", speedLimit);
        out.putBoolean("wear_reported", wearReported);
        RopeState r = rope();
        if (r != null) {
            out.beginObject("rope");
            out.putDouble("d", r.diameterMm());
            out.putDouble("grade", r.gradeMpa());
            out.putString("construction", r.construction().name());
            out.endObject();
        }
        if (hoist != null) {
            out.beginObject("hoist");
            hoist.save(out);
            out.endObject();
        } else if (r != null) {
            out.beginObject("wear");
            out.putDouble("damage", r.damage());
            out.putLong("bends", r.bendCycles());
            out.putDouble("inspected", r.lastInspectedT());
            out.putDouble("tonne_km", r.tonneKm());
            out.endObject();
        }
    }

    @Override
    public void load(StateReader in) {
        landings = in.getDoubles("landings");
        speedLimit = in.getDouble("speed_limit");
        wearReported = in.getBoolean("wear_reported");
        rope = null;
        hoist = null;
        if (in.has("rope")) {
            in.beginObject("rope");
            RopeState fresh = RopeState.fresh(in.getDouble("d"), in.getDouble("grade"),
                    RopeConstruction.valueOf(in.getString("construction")));
            in.endObject();
            rope = fresh;
            if (in.has("wear")) {
                in.beginObject("wear");
                rope = new RopeState(fresh.diameterMm(), fresh.gradeMpa(), fresh.construction(), in.getDouble("damage"),
                        in.getLong("bends"), in.getDouble("inspected"), in.getDouble("tonne_km"));
                in.endObject();
            }
        }
        rebuild();
        if (hoist != null && in.has("hoist")) {
            in.beginObject("hoist");
            hoist.load(in);
            in.endObject();
            reported = hoist.state();
        }
        target = in.getInt("target");
        lastLanding = in.getInt("last_landing");
    }
}
