package dev.denfry.promptcritical.sim.hydraulics;

/**
 * Обратимая радиально-осевая насос-турбина на синхронной скорости (двигатель-генератор в сети): одно рабочее колесо
 * качает воду наверх в насосном режиме и отдаёт мощность в турбинном.
 *
 * <p>Насосный режим при постоянной скорости нерегулируем: подачу задаёт пересечение напорной характеристики
 * H_н(Q) = H_р·(1,35 − 0,35·(Q/Q_н)²) (напор на закрытую задвижку 1,35 расчётного — центробежные колёса низкой
 * быстроходности, Stepanoff, «Centrifugal and Axial Flow Pumps», 1957, гл. 9) с характеристикой сети
 * H_ст + k·Q². КПД насоса η_н(Q) = η_н,max·(1 − 0,8·(Q/Q_н − 1)²).
 *
 * <p>Турбинный режим регулируется направляющим аппаратом: расход при полном открытии Q_т,max = Q_т·√(H/H_р),
 * КПД η_т = η_т,max·(1 − 0,6·(q − 0,9)²)·(1 − 0,5·(H/H_р − 1)²), где q = Q/Q_т,max — открытие (максимум КПД при 90 %
 * открытия [оценка по типовым универсальным характеристикам радиально-осевых турбин]). Пиковые КПД
 * обратимой машины ниже, чем у отдельных насоса и турбины: 0,91 и 0,92 [порядок для насос-турбин Загорской ГАЭС и
 * Ludington, оценка]. КПД двигателя-генератора — 0,98.
 *
 * @param ratedHeadM          расчётный напор H_р, м
 * @param turbineFlowM3s      расчётный расход турбины Q_т при H_р и полном открытии, м³/с
 * @param pumpFlowM3s         расчётная подача насоса Q_н при напоре H_р, м³/с
 * @param turbineEfficiency   η_т,max
 * @param pumpEfficiency      η_н,max
 * @param machineEfficiency   КПД двигателя-генератора
 */
public record PumpTurbine(double ratedHeadM, double turbineFlowM3s, double pumpFlowM3s, double turbineEfficiency,
        double pumpEfficiency, double machineEfficiency) {
    public static final double RHO = HydroTurbine.RHO, G = HydroTurbine.G;
    /** Напор насоса на закрытую задвижку, доля расчётного. */
    public static final double SHUT_OFF_HEAD = 1.35;

    public PumpTurbine {
        if (!(ratedHeadM > 0 && turbineFlowM3s > 0 && pumpFlowM3s > 0)) throw new IllegalArgumentException("rating");
        if (!(turbineEfficiency > 0 && turbineEfficiency <= 1 && pumpEfficiency > 0 && pumpEfficiency <= 1
                && machineEfficiency > 0 && machineEfficiency <= 1)) throw new IllegalArgumentException("efficiency");
    }

    /**
     * Агрегат Загорской ГАЭС: 200 МВт в турбинном и 220 МВт в насосном режиме при расчётном напоре 100 м (РусГидро).
     * Расходы — из мощностей при пиковых КПД: Q_т = 200 МВт/(η_т·η_г·ρ·g·H), Q_н = 220 МВт·η_н·η_д/(ρ·g·H).
     */
    public static PumpTurbine zagorsk() {
        double h = 100, et = 0.92, ep = 0.91, em = 0.98;
        return new PumpTurbine(h, 200e6 / (et * em * RHO * G * h), 220e6 * ep * em / (RHO * G * h), et, ep, em);
    }

    /** Агрегат того же склада под напор {@code headM} и мощность турбины {@code turbinePowerW}. */
    public static PumpTurbine scaled(double headM, double turbinePowerW) {
        PumpTurbine z = zagorsk();
        double qt = turbinePowerW / (z.turbineEfficiency * z.machineEfficiency * RHO * G * headM);
        return new PumpTurbine(headM, qt, qt * z.pumpFlowM3s / z.turbineFlowM3s, z.turbineEfficiency, z.pumpEfficiency,
                z.machineEfficiency);
    }

    /** Напор насоса при подаче Q, м. */
    public double pumpHeadM(double flowM3s) {
        double q = flowM3s / pumpFlowM3s;
        return ratedHeadM * (SHUT_OFF_HEAD - (SHUT_OFF_HEAD - 1) * q * q);
    }

    /** КПД насоса при подаче Q. */
    public double pumpEfficiencyAt(double flowM3s) {
        double x = flowM3s / pumpFlowM3s - 1;
        return pumpEfficiency * Math.max(0.05, 1 - 0.8 * x * x);
    }

    /** Расход турбины при полном открытии и напоре нетто H, м³/с. */
    public double turbineFullFlowM3s(double netHeadM) {
        return netHeadM <= 0 ? 0 : turbineFlowM3s * Math.sqrt(netHeadM / ratedHeadM);
    }

    /** КПД турбины при открытии q (доля расхода полного открытия) и напоре нетто H. */
    public double turbineEfficiencyAt(double opening, double netHeadM) {
        double x = opening - 0.9, y = netHeadM / ratedHeadM - 1;
        return turbineEfficiency * Math.max(0, 1 - 0.6 * x * x) * Math.max(0, 1 - 0.5 * y * y);
    }

    /** Номинальная электрическая мощность турбинного режима, Вт. */
    public double turbinePowerW() {
        return turbineEfficiencyAt(1, ratedHeadM) * machineEfficiency * RHO * G * turbineFlowM3s * ratedHeadM;
    }

    /** Мощность двигателя в насосном режиме при расчётном напоре, Вт. */
    public double pumpPowerW() {
        return RHO * G * pumpFlowM3s * pumpHeadM(pumpFlowM3s) / (pumpEfficiencyAt(pumpFlowM3s) * machineEfficiency);
    }
}
