package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.combustion.Combustor;
import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Двойник коксовой батареи (08 §8.4): {@link CokeBattery}, газосборник {@link GasMain} и регулятор обогрева. Форму
 * батареи (число камер, длина) находит мир и передаёт {@link #form}; камеры при перестройке переносятся по карте.
 *
 * <p>Регулятор (моё): держит уставку простенков, скорость изменения не больше {@code ramp} К/ч физ. — разогрев
 * динаса по §8.4.1 идёт сам при уставке разогрева. Тепло сначала из газа (сколько мир подал по заявке
 * {@link #gasDemandNm3S}), остаток — топки разогрева на твёрдом топливе (пока своего газа нет).
 */
public final class CokeBatteryUnit implements IronmakingUnit {
    /** Насыпная плотность шихты, кг/м³; высота камеры, м. */
    public static final double BULK_DENSITY = 750, CHAMBER_HEIGHT_M = 3;
    /** Предел топок разогрева на камеру, Вт [BALANCE]. */
    public static final double FIREBOX_MAX_W_PER_OVEN = 150_000;
    /** Запас тепла в топке, ниже которого мир подкладывает топливо, Дж. */
    public static final double FIREBOX_BUFFER_J = 2e9;
    public static final double DEFAULT_SETPOINT_K = 1573.15, DEFAULT_RAMP_K_H = 5, DEFAULT_AIR_EXCESS = 1.15;
    private static final double REG_TAU_S = 7_200;

    private final IronmakingLog log = new IronmakingLog();
    private final GasMain main = new GasMain();
    private CokeBattery battery;
    private double chargeKg;
    private double setpointK = DEFAULT_SETPOINT_K, rampKH = DEFAULT_RAMP_K_H, airExcess = DEFAULT_AIR_EXCESS;
    private ProcessGas gas;
    private double gasNm3S, fireboxJ, fireboxW, demandW;
    private Combustor combustor;
    private ProcessGas combustorGas;
    private double exhCapacity, exhSpeed, outletRoom;

    @Override
    public String deviceType() {
        return IronmakingSubsystem.COKE_OVEN;
    }

    /** Проектная загрузка камеры длиной {@code lengthM} и шириной {@code widthM}, кг. */
    public static double chargeKgFor(double lengthM, double widthM) {
        return BULK_DENSITY * widthM * CHAMBER_HEIGHT_M * lengthM;
    }

    /**
     * Батарея найдена или перестроена.
     *
     * @param map камера новой батареи → камера прежней или −1
     */
    public void form(IronmakingConfig cfg, int ovens, double chargeKg, int[] map) {
        CokeBattery old = battery;
        if (old != null && old.ovenCount() == ovens && this.chargeKg == chargeKg && isIdentity(map)) return;
        CokeBattery b = new CokeBattery(cfg, ovens, chargeKg, old != null ? old.wallTempK() : 293.15);
        if (old != null) b.inherit(old, map);
        b.setEventSink(log);
        battery = b;
        this.chargeKg = chargeKg;
    }

    private static boolean isIdentity(int[] map) {
        for (int i = 0; i < map.length; i++) if (map[i] != i) return false;
        return true;
    }

    @Override
    public void step(IronmakingSubsystem works, FastContext ctx, double slowDt) {
        if (battery == null) return;
        if (slowDt > 0) heat(slowDt);
        main.update(battery.gasMakeNm3S(), exhCapacity, exhSpeed, outletRoom, ctx.dt());
        if (main.takeExplosion()) log.add(IronmakingLog.Kind.GAS_MAIN_EXPLOSION, 0, main.pressurePa());
    }

    private void heat(double dt) {
        double t = battery.wallTempK();
        double rate = Math.max(-rampKH / 3600, Math.min(rampKH / 3600, (setpointK - t) / REG_TAU_S));
        demandW = Math.max(0, battery.wallHeatCapacityJK() * rate + battery.coolingW());
        double fuelKgS = gas == null ? 0 : gasNm3S * gas.densityKgNm3;
        Combustor c = combustor();
        double gasHeat = c == null ? 0 : CokeBattery.HEATING_EFFICIENCY * fuelKgS * c.lhv();
        fireboxW = Math.min(Math.max(0, demandW - gasHeat),
                Math.min(FIREBOX_MAX_W_PER_OVEN * battery.ovenCount(), fireboxJ / dt));
        fireboxJ = Math.max(0, fireboxJ - fireboxW * dt);
        battery.setHeating(fuelKgS, c == null ? 0 : c.airFor(fuelKgS, airExcess));
        battery.setExtraHeat(fireboxW);
        battery.stepSlow(dt, c);
    }

    private Combustor combustor() {
        if (gas == null) return null;
        if (combustorGas != gas) {
            combustorGas = gas;
            combustor = gas.combustor(0.2);
        }
        return combustor;
    }

    /** Заявка регулятора на газ {@code g}, нм³/с физ. (вся потребность тепла). */
    public double gasDemandNm3S(ProcessGas g) {
        double perNm3 = CokeBattery.HEATING_EFFICIENCY * g.fuel.lhv() * g.densityKgNm3;
        return perNm3 > 0 ? demandW / perNm3 : 0;
    }

    /** Газ, поданный миром на горелки простенков; {@code null} — газа нет. */
    public void setGasSupply(ProcessGas g, double nm3S) {
        gas = nm3S > 0 ? g : null;
        gasNm3S = Math.max(0, nm3S);
    }

    /** Топки разогрева просят топливо. */
    public boolean fireboxHungry() {
        return battery != null && fireboxJ < FIREBOX_BUFFER_J && demandW > 0;
    }

    /** Забросить в топки разогрева тепло сожжённого топлива, Дж. */
    public void addFireboxHeat(double joules) {
        fireboxJ += Math.max(0, joules);
    }

    /**
     * Эксгаустеры на газосборнике.
     *
     * @param capacityNm3S   Σ Q₀·s, нм³/с физ.
     * @param meanSpeed      средняя s работающих
     * @param outletRoomNm3S приём сети после эксгаустеров, нм³/с физ.
     */
    public void setExhausters(double capacityNm3S, double meanSpeed, double outletRoomNm3S) {
        exhCapacity = Math.max(0, capacityNm3S);
        exhSpeed = Math.max(0, meanSpeed);
        outletRoom = Math.max(0, outletRoomNm3S);
    }

    public void setSetpoint(double wallK, double rampKPerH) {
        setpointK = Math.max(293.15, Math.min(1673.15, wallK));
        rampKH = Math.max(0.5, Math.min(200, rampKPerH));
    }

    public double setpointK() {
        return setpointK;
    }

    public double rampKH() {
        return rampKH;
    }

    /** Батарея сформирована (мир её хоть раз нашёл). */
    public CokeBattery battery() {
        return battery;
    }

    public GasMain main() {
        return main;
    }

    public IronmakingLog log() {
        return log;
    }

    public double demandW() {
        return demandW;
    }

    public double fireboxW() {
        return fireboxW;
    }

    public double gasSupplyNm3S() {
        return gas == null ? 0 : gasNm3S;
    }

    @Override
    public void save(StateWriter out) {
        out.putDouble("setpoint_k", setpointK);
        out.putDouble("ramp_k_h", rampKH);
        out.putDouble("firebox_j", fireboxJ);
        main.save(out);
        if (battery == null) return;
        out.putInt("ovens", battery.ovenCount());
        out.putDouble("charge_kg", chargeKg);
        out.beginObject("battery");
        battery.save(out);
        out.endObject();
    }

    @Override
    public void load(StateReader in) {
        setpointK = in.getDouble("setpoint_k");
        rampKH = in.getDouble("ramp_k_h");
        fireboxJ = in.getDouble("firebox_j");
        main.load(in);
        if (!in.has("battery")) return;
        chargeKg = in.getDouble("charge_kg");
        battery = new CokeBattery(IronmakingConfig.defaults(), in.getInt("ovens"), chargeKg, 293.15);
        battery.setEventSink(log);
        in.beginObject("battery");
        battery.load(in);
        in.endObject();
    }
}
