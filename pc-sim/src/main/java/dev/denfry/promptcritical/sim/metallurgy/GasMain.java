package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Газосборник коксовой батареи (08 §8.4.4): держится +50…200 Па над атмосферой. Газ отсасывают эксгаустеры на валу
 * Create; давление — квазистационарное по характеристике вентилятора Δp = Δp₀·s²·(1 − (Q/(Q₀·s))²):
 * P = Δp₀·s̄²·((Q/C)² − 1), C = Σ Q₀·s (моё). Без отсоса или при заполненной сети после эксгаустеров давление
 * растёт до открытия свечей ({@value #BLEEDER_PA} Па) — газ сгорает на стояках. Разрежение подсасывает воздух:
 * ниже {@value #VACUUM_PA} Па дольше {@value #VACUUM_TRIP_S} с реальных — взрыв в газосборнике
 * ({@code COKE_GAS_MAIN_PRESSURE_LOW}) [BALANCE].
 */
public final class GasMain {
    /** Напор эксгаустера при номинальных оборотах, Па [BALANCE: реальные 20–30 кПа, игровой — чтобы обороты Create
     * давали шаг ≈ 10 Па]. */
    public static final double HEAD_PA = 500;
    public static final double BLEEDER_PA = 300, VACUUM_PA = -50, VACUUM_TRIP_S = 30;
    /** Отсос одного эксгаустера при номинальных оборотах, нм³/с физ.; мощность на валу при номинальных, Вт. */
    public static final double EXHAUSTER_NM3S = 0.5, EXHAUSTER_RATED_W = 5_000;

    private double pressurePa, vacuumS, deliveredNm3S, ventedNm3S;
    private boolean explosionPending;

    /**
     * @param makeNm3S       выход газа батареи, нм³/с физ.
     * @param capacityNm3S   C = Σ Q₀·s работающих эксгаустеров; 0 — отсоса нет
     * @param meanSpeed      средняя относительная скорость работающих эксгаустеров
     * @param outletRoomNm3S сколько газа сеть после эксгаустеров примет, нм³/с физ.
     * @param dtReal         с реальных (выдержка разрежения)
     */
    public void update(double makeNm3S, double capacityNm3S, double meanSpeed, double outletRoomNm3S, double dtReal) {
        double make = Math.max(0, makeNm3S);
        double head = HEAD_PA * meanSpeed * meanSpeed;
        double through = capacityNm3S > 0 ? Math.min(Math.max(0, outletRoomNm3S),
                capacityNm3S * Math.sqrt(1 + BLEEDER_PA / Math.max(head, 1e-9))) : 0;
        if (capacityNm3S <= 0 && make <= 0) {
            pressurePa = deliveredNm3S = ventedNm3S = 0;
        } else if (make > through + 1e-12) {
            pressurePa = BLEEDER_PA;
            deliveredNm3S = through;
            ventedNm3S = make - through;
        } else {
            double r = make / capacityNm3S;
            pressurePa = Math.max(-head, head * (r * r - 1));
            deliveredNm3S = make;
            ventedNm3S = 0;
        }
        if (pressurePa < VACUUM_PA) {
            vacuumS += dtReal;
            if (vacuumS >= VACUUM_TRIP_S) {
                explosionPending = true;
                vacuumS = 0;
            }
        } else {
            vacuumS = 0;
        }
    }

    /** Избыточное давление, Па. */
    public double pressurePa() {
        return pressurePa;
    }

    /** Газ через эксгаустеры в сеть, нм³/с физ. */
    public double deliveredNm3S() {
        return deliveredNm3S;
    }

    /** Газ, сгорающий на свечах, нм³/с физ. */
    public double ventedNm3S() {
        return ventedNm3S;
    }

    /** Сколько ещё держится разрежение, с реальных. */
    public double vacuumSeconds() {
        return vacuumS;
    }

    /** Взрыв от подсоса воздуха; сбрасывается чтением. */
    public boolean takeExplosion() {
        boolean e = explosionPending;
        explosionPending = false;
        return e;
    }

    public void save(StateWriter out) {
        out.putDouble("pressure_pa", pressurePa);
        out.putDouble("vacuum_s", vacuumS);
    }

    public void load(StateReader in) {
        pressurePa = in.getDouble("pressure_pa");
        vacuumS = in.getDouble("vacuum_s");
    }
}
