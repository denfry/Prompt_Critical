package dev.denfry.promptcritical.sim.automation.plc;

import java.util.List;

/**
 * Скомпилированная программа ПЛК на языке IL (Instruction List, IEC 61131-3 §3.2): плоский список инструкций с
 * разрешёнными переходами. Не меняется после компиляции; одна программа на процессор.
 *
 * @param source  исходный текст, как его ввёл игрок
 * @param code    инструкции по порядку исполнения
 * @param errors  ошибки компиляции; непустой список — программу нельзя запустить
 */
public record IlProgram(String source, List<Instr> code, List<IlError> errors) {
    /** Пустая программа: ПЛК в RUN ничего не делает, выходы сброшены. */
    public static final IlProgram EMPTY = new IlProgram("", List.of(), List.of());

    public boolean ok() {
        return errors.isEmpty();
    }

    /** Операторы IL (таблица 52 IEC 61131-3) и вызов функционального блока. */
    public enum Op {
        LD, ST, S, R, AND, OR, XOR, NOT, ADD, SUB, MUL, DIV, GT, GE, EQ, NE, LE, LT, JMP, RET, CAL,
        /** Закрывающая скобка: применить отложенный оператор к сохранённому и текущему результату. */
        CLOSE,
        /** Остаток от деления (IEC MOD): знак делимого, на ноль — 0. */
        MOD
    }

    /** Вид операнда: константа, биты и слова образа процесса и памяти, выход функционального блока. */
    public enum Area { CONST, IX, QX, MX, IW, QW, MW, FB }

    /**
     * Операнд. {@code index} — номер бита или слова; для {@link Area#FB} — номер экземпляра в {@link Fb} и
     * {@code member} — номер выхода блока.
     */
    public record Operand(Area area, int index, int member, double value) {
        public static Operand constant(double v) {
            return new Operand(Area.CONST, 0, 0, v);
        }

        public boolean bool() {
            return area == Area.IX || area == Area.QX || area == Area.MX
                    || (area == Area.FB && Fb.kindOf(index).boolOutput(member));
        }

        /** Можно записать: биты выходов и памяти, слова выходов и памяти. */
        public boolean writable() {
            return area == Area.QX || area == Area.MX || area == Area.QW || area == Area.MW;
        }
    }

    /**
     * Инструкция. {@code negate} — модификатор N, {@code conditional} — модификатор C (для JMP, RET, CAL),
     * {@code open} — модификатор «(»: операнд начинает вложенное выражение. Для CAL — экземпляр {@code fb} и
     * пары (вход, операнд) в {@code params}/{@code args}; для JMP — индекс цели {@code target}.
     */
    public record Instr(Op op, boolean negate, boolean conditional, boolean open, Operand operand, int fb, int[] params,
            Operand[] args, int target, int line) {
    }

    /** Ошибка компиляции на строке {@code line} (с 1); {@code token} — что не понравилось. */
    public record IlError(int line, Kind kind, String token) {
        public enum Kind {
            UNKNOWN_OPERATOR, BAD_OPERAND, NOT_WRITABLE, MISSING_OPERAND, UNEXPECTED_OPERAND, UNKNOWN_LABEL,
            DUPLICATE_LABEL, UNBALANCED_PARENTHESIS, NESTING_TOO_DEEP, BAD_CALL, TOO_LONG,
            /** Синтаксис ST: {@code token} — что ожидалось и что встретилось. */
            SYNTAX
        }
    }
}
