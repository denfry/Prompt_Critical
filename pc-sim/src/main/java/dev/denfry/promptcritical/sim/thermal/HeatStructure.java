package dev.denfry.promptcritical.sim.thermal;

/**
 * Тепловая структура: одномерная теплопроводность ТВЭЛа или стенки с конвективными
 * поверхностями к узлам гидросети (01 §1.2.3, §1.13.2). Сторона 0 — внутренняя (центр ТВЭЛа,
 * внутренняя поверхность трубы), сторона 1 — наружная. Единицы — СИ, температуры — К.
 */
public interface HeatStructure {
    long id();

    Geometry geometry();

    /** Шаг теплопроводности и связи с жидкостью; тепло уходит в узлы на текущий тик сети. */
    void step(double dt);

    /** Топливо: q''' таблетки, Вт/м³ (из ReactorCore); стенки — объёмный нагрев металла. */
    void setVolumetricPower(double wattsPerM3);

    /** Температуры узлов от стороны 0 к стороне 1 (копия). */
    double[] radialTemperatures();

    double centerlineTemperature();

    double surfaceTemperature(int side);

    /** Средний за шаг тепловой поток через сторону в жидкость, Вт/м². */
    double heatFlux(int side);

    /** Тепло через сторону в жидкость, Вт (одна структура, без кратности). */
    double heatRate(int side);

    HeatTransferMode mode(int side);

    /** Горячий ТВЭЛ — DNBR наружной поверхности, иначе NaN. */
    double dnbr();

    /** Проводимость зазора, Вт/(м²·К); не ТВЭЛ — NaN. */
    double gapConductance();

    double meltFraction();

    /** Давление газа под оболочкой, Па — вход 04c (раздутие); не ТВЭЛ — NaN. */
    double rodInternalPressure();

    boolean dnbOccurred();

    /** Тепло, запасённое в структуре относительно 0 К, Дж (баланс энергии в тестах). */
    double storedEnergy();
}
