package dev.denfry.promptcritical.sim.hydraulics;

/** Фасад-представление узла: индекс в SoA-массивах сети (01 §1.13.2). Единицы — СИ. */
public interface FluidNode {
    long id();

    NodeKind kind();

    double mass();

    double internalEnergy();

    double volume();

    /** У {@code SEPARATED} — давление паровой подушки на уровне смеси, у остальных — в центре узла. */
    double pressure();

    double temperature();

    double enthalpy();

    double density();

    /** Равновесное паросодержание x_e: может быть &lt; 0 (недогрев) и &gt; 1 (перегрев). */
    double quality();

    /** Объёмное паросодержание (гомогенное равновесие). */
    double voidFraction();

    /** Уровень двухфазной смеси над низом узла, м ({@code SEPARATED}; иначе NaN). */
    double mixtureLevel();

    /** «Коллапсированный» уровень V_liq/A над низом узла, м ({@code SEPARATED}; иначе NaN). */
    double collapsedLevel();

    /** Масса скаляра, кг (Бк для активности). */
    double scalar(PassiveScalar s);

    /** Начальные условия и тесты; не для горячего цикла. */
    void setScalar(PassiveScalar s, double amount);

    double boronPpm();

    /** Внешний источник S_M, S_E на текущий тик; при massRate &lt; 0 уходит энтальпия узла. */
    void addSource(double massRate, double enthalpy);

    /** Тепло на текущий тик, Вт (&gt; 0 — нагрев). */
    void addHeat(double watts);

    /** Только {@code BOUNDARY}: новые p, h. */
    void setBoundaryState(double pressure, double enthalpy);

    /** Узел осушен до пара (после LOCA, §1.2.9). */
    boolean isVoided();
}
