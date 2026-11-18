package dev.denfry.promptcritical.sim.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Скребок (поршень) в участке между камерами запуска и приёма (09 §9.8.2). Идёт со скоростью потока с
 * проскальзыванием 5 %, перепад на нём 0,05–0,1 МПа. Застревает, если отложения больше 20 % диаметра, колено круче 3D
 * или арматура неполнопроходная. Очистной снимает отложения (парафин — в камеру приёма), диагностический
 * (магнитный дефектоскоп) находит дефекты глубже 10 % стенки с вероятностью 0,9, погрешность глубины ±10 % стенки.
 */
public final class Pig {
    public static final double SLIP = 0.05;
    /** Перепад на поршне, середина 0,05–0,1 МПа [моё], Па. */
    public static final double DIFFERENTIAL_PA = 0.075e6;
    public static final double STUCK_DEPOSIT_FRACTION = 0.2, MIN_BEND_RATIO = 3;
    public static final double ILI_THRESHOLD = 0.1, ILI_POD = 0.9, ILI_SIZING = 0.1;
    /** Плотность парафиновых отложений, кг/м³ [сверить]. */
    public static final double WAX_DENSITY = 900;

    /** Дефект стенки: место по длине участка, м, и глубина в долях стенки. */
    public record Defect(double positionM, double depthFraction) {}

    private Pig() {
    }

    public static double speedMs(double flowVelocityMs) {
        return Math.max(0, flowVelocityMs) * (1 - SLIP);
    }

    /** Время прохода участка длиной {@code lengthM}, с (бесконечность — поток стоит). */
    public static double transitS(double lengthM, double flowVelocityMs) {
        double v = speedMs(flowVelocityMs);
        return v <= 0 ? Double.POSITIVE_INFINITY : lengthM / v;
    }

    /** Застрянет ли поршень. */
    public static boolean sticks(double depositFraction, double minBendRatio, boolean fullBore) {
        return depositFraction > STUCK_DEPOSIT_FRACTION || minBendRatio < MIN_BEND_RATIO || !fullBore;
    }

    /** Масса отложений слоем {@code depositM} в трубе с внутренним диаметром {@code idM} на длине {@code lengthM}, кг. */
    public static double waxKg(double idM, double depositM, double lengthM) {
        double d = Math.max(0, Math.min(depositM, idM / 2));
        return WAX_DENSITY * Math.PI * (idM * d - d * d) * lengthM;
    }

    /** Отчёт внутритрубной диагностики: найденные дефекты с измеренной глубиной. */
    public static List<Defect> inspect(List<Defect> actual, SplittableRandom rnd) {
        List<Defect> out = new ArrayList<>();
        for (Defect d : actual) {
            if (d.depthFraction() <= ILI_THRESHOLD || rnd.nextDouble() >= ILI_POD) continue;
            double measured = d.depthFraction() + (rnd.nextDouble() * 2 - 1) * ILI_SIZING;
            out.add(new Defect(d.positionM(), Math.clamp(measured, 0, 1)));
        }
        return out;
    }
}
