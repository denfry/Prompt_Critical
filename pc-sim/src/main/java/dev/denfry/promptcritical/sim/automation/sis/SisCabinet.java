package dev.denfry.promptcritical.sim.automation.sis;

/**
 * Шкаф ПАЗ как матрица причин и следствий (03d §3d.6.3) в проводном исполнении: логические блоки
 * {@code sis_logic_solver}, запитанные от одной цепи +24 В, — строки-причины одной МПС. Каждый блок — одна причина со
 * своим голосованием, уставкой и защёлкой; столбцы-следствия — то, что разрывают их выходные контакты.
 *
 * @param causes       причин под питанием в шкафу
 * @param twoOutOfThree из них с голосованием 2oo3 и всеми тремя подключёнными датчиками
 * @param bypassed     причин в байпасе
 * @param overdue      причин с просроченной периодической проверкой (§3d.6.8)
 */
public record SisCabinet(int causes, int twoOutOfThree, int bypassed, int overdue) {
    public static final SisCabinet NONE = new SisCabinet(0, 0, 0, 0);
    /** Причин в «настоящей» МПС для достижения 11 {@code sis_matrix}. */
    public static final int MATRIX_CAUSES = 10;

    /** МПС не меньше {@link #MATRIX_CAUSES} причин, среди них есть голосование 2oo3 (11 {@code sis_matrix}). */
    public boolean matrix() {
        return causes >= MATRIX_CAUSES && twoOutOfThree >= 1;
    }
}
