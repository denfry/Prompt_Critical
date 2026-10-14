package dev.denfry.promptcritical.sim.hydraulics;

/** Фасад-представление связи (01 §1.13.2). Единицы — СИ. */
public interface FlowLink {
    long id();

    LinkKind kind();

    LinkRegime regime();

    /** кг/с, &gt; 0 — от {@code from} к {@code to}. */
    double massFlow();

    double velocity();

    double reynolds();

    /** Потери трения и местные на последнем шаге, Па (знак — по расходу). */
    double pressureDrop();

    /** Команда положения штока: 0 — закрыто, 1 — открыто; ход — со скоростью 1/strokeTime. */
    void setValvePosition(double theta01);

    /** Фактическое положение штока (1 у связей без арматуры). */
    double valvePosition();

    /** null, если связь не насос. */
    PumpModel pump();
}
