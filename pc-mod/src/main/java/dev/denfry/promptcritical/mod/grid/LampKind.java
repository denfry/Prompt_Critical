package dev.denfry.promptcritical.mod.grid;

/**
 * Светильник группы щитка освещения: установленная мощность и запас аккумулятора аварийного блока.
 *
 * @param watts        мощность лампы с пускорегулирующим аппаратом, Вт
 * @param batteryTicks сколько светит без сети (0 — аккумулятора нет)
 * @param light        уровень освещённости во включённом состоянии
 */
public enum LampKind {
    /** ГСП с лампой ДРЛ-250 на подвесе. */
    PENDANT(250, 0, 15),
    /** Консольный уличный ЖКУ-150. */
    STREET(150, 0, 15),
    /** Прожектор с лампой ДРИ-400. */
    FLOODLIGHT(400, 0, 15),
    /** Световой указатель «Выход» с аккумулятором на 1 ч (5 мин игры). */
    EXIT(15, 6000, 8),
    /** Светильник подвесного потолка 4×18 Вт. */
    CEILING_PANEL(72, 0, 15),
    /** Люминесцентный ЛСП 2×36 Вт. */
    FLUORESCENT(72, 0, 14),
    /** Пылевлагозащищённый НПП «тарелка» 60 Вт. */
    BULKHEAD(60, 0, 12),
    /** Взрывозащищённый ВЗГ-200. */
    EXPLOSION_PROOF(200, 0, 14),
    /** Аварийный светильник с аккумулятором на 1 ч (5 мин игры). */
    EMERGENCY(8, 6000, 10),
    /** «Колокол» РСП с лампой ДРЛ-400 под фермой. */
    HIGH_BAY(400, 0, 15),
    /** Проблесковый маячок. */
    BEACON(20, 0, 8);

    private final int watts, batteryTicks, light;

    LampKind(int watts, int batteryTicks, int light) {
        this.watts = watts;
        this.batteryTicks = batteryTicks;
        this.light = light;
    }

    public int watts() {
        return watts;
    }

    public int batteryTicks() {
        return batteryTicks;
    }

    public int light() {
        return light;
    }
}
