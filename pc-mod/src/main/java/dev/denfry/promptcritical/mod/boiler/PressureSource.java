package dev.denfry.promptcritical.mod.boiler;

/** BE, к которому подключается местный манометр: избыточное давление среды на стороне клиента и сервера. */
public interface PressureSource {
    /** Избыточное давление, Па (≥ 0: трубка Бурдона упирается в штифт нуля). */
    double gaugePressurePa();
}
