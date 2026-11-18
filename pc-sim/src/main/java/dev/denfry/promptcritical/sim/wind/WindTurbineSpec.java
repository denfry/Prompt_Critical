package dev.denfry.promptcritical.sim.wind;

/**
 * Паспорт сетевой ветроэлектрической установки с асинхронным генератором постоянной скорости.
 *
 * @param radiusM          радиус ветроколеса, м
 * @param hubRadiusM       радиус втулки, м
 * @param blades           число лопастей
 * @param hubHeightM       высота оси ветроколеса над землёй, м
 * @param tsrDesign        расчётная быстроходность лопасти (по ней построена крутка и хорда)
 * @param maxChordM        наибольшая хорда у корня, м
 * @param ratedPowerW      номинальная мощность генератора (активная, на выводах), Вт
 * @param polePairs        пар полюсов генератора
 * @param gearRatio        передаточное число мультипликатора (вал генератора / вал колеса)
 * @param gearEfficiency   КПД мультипликатора при номинальном моменте
 * @param rotorInertiaKgM2 момент инерции колеса со ступицей, кг·м²
 * @param generatorInertiaKgM2 момент инерции ротора генератора, кг·м²
 * @param cutInMs          скорость включения (минутное среднее), м/с
 * @param cutOutMs         скорость остановки (10-минутное среднее), м/с
 * @param restartMs        повторный пуск после штормовой остановки — когда 10-минутное среднее ниже, м/с
 * @param runPitchDeg      рабочий угол установки лопасти, °
 * @param startPitchDeg    пусковой угол установки, °
 * @param pitchRateDegS    скорость поворота лопастей регулятором, °/с
 * @param featherRateDegS  скорость аварийного ввода во флюгер, °/с
 * @param overspeed        уставка разноса, доля синхронной скорости
 * @param brakeTorqueNm    момент механического тормоза на валу колеса, Н·м
 * @param thrustLimitN     допустимая осевая сила на колесо (расчётная нагрузка башни), Н
 * @param yawRateDegS      скорость поворота на ветер, °/с
 * @param yawDeadBandDeg   рассогласование с ветром, при котором включается поворот, °
 */
public record WindTurbineSpec(double radiusM, double hubRadiusM, int blades, double hubHeightM, double tsrDesign,
        double maxChordM, double ratedPowerW, int polePairs, double gearRatio, double gearEfficiency,
        double rotorInertiaKgM2, double generatorInertiaKgM2, double cutInMs, double cutOutMs, double restartMs,
        double runPitchDeg, double startPitchDeg, double pitchRateDegS, double featherRateDegS, double overspeed,
        double brakeTorqueNm, double thrustLimitN, double yawRateDegS, double yawDeadBandDeg) {

    public WindTurbineSpec {
        if (!(radiusM > hubRadiusM && hubRadiusM > 0)) throw new IllegalArgumentException("radius");
        if (blades < 1) throw new IllegalArgumentException("blades");
        if (!(hubHeightM > radiusM)) throw new IllegalArgumentException("hubHeightM");
        if (!(ratedPowerW > 0 && polePairs > 0 && gearRatio > 0)) throw new IllegalArgumentException("drive");
        if (!(gearEfficiency > 0 && gearEfficiency <= 1)) throw new IllegalArgumentException("gearEfficiency");
        if (!(rotorInertiaKgM2 > 0 && generatorInertiaKgM2 > 0)) throw new IllegalArgumentException("inertia");
        if (!(cutInMs > 0 && cutOutMs > cutInMs && restartMs > cutInMs && restartMs < cutOutMs))
            throw new IllegalArgumentException("wind limits");
        if (!(pitchRateDegS > 0 && featherRateDegS >= pitchRateDegS)) throw new IllegalArgumentException("pitch rates");
        if (!(overspeed > 1)) throw new IllegalArgumentException("overspeed");
        if (!(brakeTorqueNm > 0 && thrustLimitN > 0 && yawRateDegS > 0 && yawDeadBandDeg >= 0))
            throw new IllegalArgumentException("limits");
    }

    /**
     * Балаклавская ВЭС ЦАГИ (ВИМЭ) Д-30, 1931: первая в мире ВЭУ, работавшая параллельно с сетью (сеть
     * Севастополя, с Севастопольской ГРЭС), до 1942 г.
     *
     * <p>Из источников: ветроколесо Ø30 м, три лопасти, поворот лопастей стабилизатором Сабинина, колесо,
     * мультипликатор и генератор соосно в гондоле, 100 кВт (Бутузов, Безруких, Грибков, «Сто лет развития
     * ветроэнергетики в России. Ч. 1», ОСЭ № 3, 2021, doi:10.5281/zenodo.5547003; Секторов В.Р.,
     * «Балаклавская опытная ветроэлектростанция», Электричество, 1933); башня 30 м, поворот на ветер тележкой
     * по круговому рельсу (P. Gipe, wind-works.org); асинхронный генератор, двухступенчатый мультипликатор,
     * колесо до 30 об/мин, генератор до 650 об/мин (каталог wind-turbine-models.com, «Krasnovsky WIME D-30»).
     *
     * <p>Восстановлено: генератор 10-полюсный (600 об/мин синхронно при 50 Гц) — ближайший к 650 об/мин
     * максимума; мультипликатор 20 — 30 об/мин колеса при синхронной скорости; расчётная быстроходность 6 (окружная
     * скорость 47 м/с при 30 об/мин против расчётного ветра ≈ 8 м/с); втулка 1,5 м и корневая хорда 1,8 м;
     * момент инерции — масса лопасти по масштабной формуле NREL m = 0,1452·R^2,9158 (Fingersh, Hand, Laxson,
     * NREL/TP-500-40566, 2006), ≈ 390 кг, распределённая как J ≈ m·R²/4. Порог включения 4 м/с, штормовая
     * остановка 20 м/с и повторный пуск ниже 16 м/с — типичная логика сетевых ВЭУ постоянной скорости
     * (Burton et al., «Wind Energy Handbook», 2-е изд., 2011, §8.2–8.3). Ход лопастей 5 °/с, флюгер 10 °/с
     * (порядок скоростей приводов шага у Burton et al., §8.3), разнос 1,15 ω_с, тормоз — 1,5 номинального момента
     * колеса, допустимая осевая сила 60 кН — вдвое выше рабочей при номинале [BALANCE].
     */
    public static WindTurbineSpec balaklavaD30() {
        double r = 15, blades = 3;
        double bladeMass = 0.1452 * Math.pow(r, 2.9158);
        double rotorJ = blades * bladeMass * r * r / 4;
        double ratedRotorTorque = 100e3 / 0.9 / (2 * Math.PI * 30 / 60);
        return new WindTurbineSpec(r, 1.5, 3, 30, 6, 1.8, 100e3, 5, 20, 0.95, rotorJ, 4, 4, 20, 16,
                0, 20, 5, 10, 1.15, 1.5 * ratedRotorTorque, 60e3, 0.5, 10);
    }

    /** Синхронная скорость генератора при частоте сети, рад/с. */
    public double syncOmegaGenerator(double frequencyHz) {
        return 2 * Math.PI * frequencyHz / polePairs;
    }

    /** Синхронная частота вращения генератора, об/мин. */
    public double syncRpm(double frequencyHz) {
        return 60 * frequencyHz / polePairs;
    }

    /** Момент инерции привода, приведённый к валу генератора: J_г + J_к/i², кг·м². */
    public double inertiaAtGeneratorKgM2() {
        return generatorInertiaKgM2 + rotorInertiaKgM2 / (gearRatio * gearRatio);
    }

    /** Ометаемая площадь, м². */
    public double sweptAreaM2() {
        return Math.PI * radiusM * radiusM;
    }

    /** Геометрия лопасти: оптимум Глауэрта при {@link #tsrDesign}, C_l,d = 1,0 при α_d = 6° (профиль {@link #airfoil}). */
    public RotorGeometry geometry() {
        return RotorGeometry.glauertOptimum(radiusM, hubRadiusM, blades, tsrDesign, 1.0, Math.toRadians(6), maxChordM, 24);
    }

    public Airfoil airfoil() {
        RotorGeometry g = geometry();
        return Airfoil.roughCambered((radiusM - hubRadiusM) / g.meanChordM());
    }
}
