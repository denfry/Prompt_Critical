package dev.denfry.promptcritical.sim.reactor;

/**
 * Ключи {@code pile.*} графитового котла T0 (01 §1.14.4, CP-1 и X-10). Физические значения — с источником в
 * комментарии; [BALANCE] — подобраны под игру.
 *
 * @param nominalPowerW        нормировка точечной кинетики P_nom, Вт (n = P/P_nom)
 * @param sourceNeutronsPerS   Ra-Be источник в кладке, нейтр./с (≈ 1,5·10⁷ на кюри радия)
 * @param rodAbsorberFraction  доля объёма узла 1 м³, занятая кадмием полностью введённого стержня [BALANCE]: полосы
 *                             кадмиевого листа на деревянной рейке; в узле 1 м кадмий всё равно чёрный, так что доля
 *                             задаёт вес лишь слабо: центральная колонна в кладке 4³–5³ стоит 1–3 % Δk
 * @param controlRodSpeedMS    скорость привода регулирующих стержней, м/с
 * @param zipFallSpeedMS       падение аварийного стержня «zip» после сброса верёвки, м/с (≈ 1 с на высоту котла)
 * @param zipWindSpeedMS       взвод аварийного стержня лебёдкой, м/с
 * @param periodTripS          уставка аварийной защиты по периоду, с
 * @param powerTripW           уставка аварийной защиты по мощности, Вт [BALANCE]
 * @param detectorEfficiency   доля нейтронов котла, зарегистрированных борным счётчиком у поверхности [BALANCE]
 * @param detectorDeadTimeS    мёртвое время счётчика BF₃, с (непарализуемая модель)
 * @param backgroundCps        фон счётчика без котла (космика, примеси), имп./с
 * @param fuelToGraphiteWK     теплообмен блочков одного узла с кладкой через воздушный зазор, Вт/К [BALANCE]
 * @param surfaceCoolingWK     естественная конвекция и излучение с грани кладки 1 м², Вт/К [BALANCE]
 * @param cladFailureK         разрушение алюминиевой оболочки блочков (Al плавится при 933 К), К
 * @param graphiteFireK        самоподдерживающееся окисление графита на воздухе (Windscale, 1957), К [BALANCE]
 * @param ambientK             температура зала, К
 */
public record PileConfig(double nominalPowerW, double sourceNeutronsPerS, double rodAbsorberFraction,
        double controlRodSpeedMS, double zipFallSpeedMS, double zipWindSpeedMS, double periodTripS, double powerTripW,
        double detectorEfficiency, double detectorDeadTimeS, double backgroundCps, double fuelToGraphiteWK,
        double surfaceCoolingWK, double cladFailureK, double graphiteFireK, double ambientK) {
    public PileConfig {
        if (!(nominalPowerW > 0)) throw new IllegalArgumentException("nominalPowerW");
        if (!(sourceNeutronsPerS >= 0)) throw new IllegalArgumentException("sourceNeutronsPerS");
        if (!(rodAbsorberFraction > 0 && rodAbsorberFraction < 0.1)) throw new IllegalArgumentException("rodAbsorberFraction");
        if (!(controlRodSpeedMS > 0) || !(zipFallSpeedMS > 0) || !(zipWindSpeedMS > 0))
            throw new IllegalArgumentException("rod speed");
        if (!(periodTripS > 0)) throw new IllegalArgumentException("periodTripS");
        if (!(powerTripW > 0)) throw new IllegalArgumentException("powerTripW");
        if (!(detectorEfficiency > 0 && detectorEfficiency < 1)) throw new IllegalArgumentException("detectorEfficiency");
        if (!(detectorDeadTimeS >= 0)) throw new IllegalArgumentException("detectorDeadTimeS");
        if (!(backgroundCps >= 0)) throw new IllegalArgumentException("backgroundCps");
        if (!(fuelToGraphiteWK > 0) || !(surfaceCoolingWK > 0)) throw new IllegalArgumentException("heat transfer");
        if (!(ambientK > 200 && cladFailureK > ambientK && graphiteFireK > ambientK))
            throw new IllegalArgumentException("temperatures");
    }

    public static PileConfig defaults() {
        return new PileConfig(1e6, 1e7, 0.004, 0.05, 5, 0.1, 5, 200_000, 1e-5, 2e-6, 0.5, 110, 10, 873, 723,
                293.15);
    }
}
