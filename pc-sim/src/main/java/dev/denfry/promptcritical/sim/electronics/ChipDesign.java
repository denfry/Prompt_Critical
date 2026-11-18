package dev.denfry.promptcritical.sim.electronics;

/**
 * Что делает фабрика (11 §11.5.1, 15 §15.6) и как техпроцесс превращается в кристалл.
 *
 * <p>Площадь кристалла — число транзисторов на площадь ячейки ≈ 125·CD² (Intel 8086: 29 000 транзисторов на 33 мм² при
 * 3 мкм → 1140 мкм² = 127·CD²) плюс контактные площадки 1 мм². Частота по правилу масштабирования Деннарда растёт как
 * 1/CD: 8086 при 3 мкм — 5 МГц, отсюда 15 МГц при 1 мкм (80386 при 1,5 мкм — 16–33 МГц).
 *
 * <p>Окно процесса — грубые правила проектирования МОП эпохи: подзатворный окисел 15–50 нм на микрон элемента (1 мкм —
 * 20–25 нм, 3 мкм HMOS — 70 нм, 10 мкм p-МОП — 120–150 нм); глубина стока-истока 0,1–0,6 CD (мельче — большое
 * сопротивление и пробой, глубже — смыкание каналов). Вне окна кристаллы работают на пластине, но не по параметрам:
 * годных нет.
 */
public enum ChipDesign {
    /** Дискретный МОП-транзистор в корпусе. */
    TRANSISTOR(1, 0.5, 10),
    /** Логическая микросхема малой степени интеграции (десятки вентилей). */
    LOGIC(400, 1, 10),
    /** Память: ОЗУ 4 Кбит (одна ячейка — транзистор с конденсатором, плюс периферия). */
    MEMORY(8000, 1, 5),
    /** Микропроцессор класса 8086. */
    MICROPROCESSOR(29000, 1, 3);

    public final int transistors;
    /** Площадь площадок и периферии, мм². */
    public final double padsMm2;
    /** Наибольший размер элемента, при котором изделие ещё имеет смысл (иначе кристалл слишком велик), мкм. */
    public final double maxCdUm;

    public static final double CELL_FACTOR = 125, REF_CD_UM = 3, REF_CLOCK_MHZ = 5;
    public static final double OXIDE_NM_PER_UM_MIN = 15, OXIDE_NM_PER_UM_MAX = 50, XJ_MIN = 0.1, XJ_MAX = 0.6;

    ChipDesign(int transistors, double padsMm2, double maxCdUm) {
        this.transistors = transistors;
        this.padsMm2 = padsMm2;
        this.maxCdUm = maxCdUm;
    }

    /** Площадь кристалла, мм². */
    public double dieAreaMm2(double cdUm) {
        return transistors * CELL_FACTOR * cdUm * cdUm * 1e-6 + padsMm2;
    }

    /** Тактовая частота, МГц (для дискретных и малой логики — предельная частота переключения). */
    public static double clockMhz(double cdUm) {
        return REF_CLOCK_MHZ * REF_CD_UM / cdUm;
    }

    /** Причина брака по параметрам или {@code null}, если окисел и переход в окне. */
    public static Reject window(double cdUm, double oxideNm, double junctionUm) {
        if (oxideNm < OXIDE_NM_PER_UM_MIN * cdUm) return Reject.OXIDE_THIN;
        if (oxideNm > OXIDE_NM_PER_UM_MAX * cdUm) return Reject.OXIDE_THICK;
        if (junctionUm < XJ_MIN * cdUm) return Reject.JUNCTION_SHALLOW;
        if (junctionUm > XJ_MAX * cdUm) return Reject.JUNCTION_DEEP;
        return null;
    }

    public enum Reject { OXIDE_THIN, OXIDE_THICK, JUNCTION_SHALLOW, JUNCTION_DEEP, TOO_LARGE }
}
