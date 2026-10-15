package dev.denfry.promptcritical.mod.boiler;

/**
 * Контроллер машины-потребителя, к которой вал Create подводят через клетку {@code kinetic_port} с {@code input=true}
 * (03a §3a.12.2): вал входит не в контроллер, а в клетку на краю объёма (вентилятор главного проветривания, подъёмная
 * машина, насос водоотлива).
 */
public interface ShaftConsumer {
    /** Скорость вала, об/мин со знаком, исправность сети (нет overstress) и тик мира симуляции. */
    void sampleShaft(double rpm, boolean healthy, long tick);

    /** Мощность на валу сейчас, Вт. */
    double shaftPowerW();

    /** Номинальная мощность и скорость вала — для импульса, пока вал стоит. */
    double ratedShaftW();

    double nominalShaftRpm();
}
