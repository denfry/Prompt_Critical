package dev.denfry.promptcritical.sim.automation.plc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сетка графического редактора LD (03d §3d.5.10): ступени по {@value #COLUMNS} клеток контактов и столбец катушки,
 * основная линия (строка 0) и до {@value #BRANCH_ROWS} параллельных ветвей. Ветвь — непрерывный отрезок строки
 * {@code r > 0} с {@code a} по {@code b}: она параллельна отрезку основной линии в тех же столбцах; ветви с одним
 * началом — альтернативы одной группы. Сетка переводится в текстовую запись LD ({@link LdCompiler}) и обратно —
 * компилирует её тот же процессор, что и текст. Пустая клетка основной линии под ветвью — перемычка.
 */
public final class LadderGrid {
    public static final int COLUMNS = 8, BRANCH_ROWS = 2, ROWS = 1 + BRANCH_ROWS, MAX_RUNGS = 24;

    /** Элемент клетки: контакты и блоки в цепи. */
    public enum Kind {
        EMPTY(""), NO("XIC"), NC("XIO"), TON("TON"), TOF("TOF"), TP("TP"), CTU("CTU");

        public final String op;

        Kind(String op) {
            this.op = op;
        }

        public boolean block() {
            return this == TON || this == TOF || this == TP || this == CTU;
        }
    }

    /** Катушка ступени. */
    public enum Coil { NONE, OTE, OTL, OTU, RES }

    /** Клетка: элемент, операнд (адрес или имя блока) и уставка блока ({@code T#5S}, число). */
    public record Cell(Kind kind, String operand, String preset) {
        public static final Cell EMPTY = new Cell(Kind.EMPTY, "", "");

        public boolean empty() {
            return kind == Kind.EMPTY;
        }

        String token() {
            return kind.block() ? kind.op + "(" + operand + ", " + preset + ")" : kind.op + "(" + operand + ")";
        }
    }

    /** Ступень: клетки [строка][столбец] и катушка. */
    public static final class Rung {
        public final Cell[][] cells = new Cell[ROWS][COLUMNS];
        public Coil coil = Coil.OTE;
        public String coilOperand = "%QX0.0";

        public Rung() {
            for (Cell[] row : cells) java.util.Arrays.fill(row, Cell.EMPTY);
        }

        /** Отрезок ветви строки {@code r}: {начало, конец}, {@code null} — строка пуста. */
        public int[] span(int r) {
            int a = -1, b = -1;
            for (int c = 0; c < COLUMNS; c++)
                if (!cells[r][c].empty()) {
                    if (a < 0) a = c;
                    b = c;
                }
            return a < 0 ? null : new int[] {a, b};
        }
    }

    private final List<Rung> rungs = new ArrayList<>();

    public List<Rung> rungs() {
        return rungs;
    }

    public Rung addRung() {
        if (rungs.size() >= MAX_RUNGS) throw new IllegalStateException("too many rungs");
        Rung r = new Rung();
        rungs.add(r);
        return r;
    }

    // ------------------------------------------------------------------ сетка → текст

    /** Текст LD; ступень без катушки пропускается. Ветвь с разрывом внутри отрезка — ошибка с номером ступени. */
    public String toText() {
        StringBuilder sb = new StringBuilder("(*LD*)\n");
        for (int i = 0; i < rungs.size(); i++) {
            Rung r = rungs.get(i);
            if (r.coil == Coil.NONE) continue;
            sb.append(rungText(r, i + 1)).append('\n');
        }
        return sb.toString();
    }

    static String rungText(Rung r, int number) {
        int[][] spans = new int[ROWS][];
        for (int row = 1; row < ROWS; row++) {
            spans[row] = r.span(row);
            if (spans[row] != null)
                for (int c = spans[row][0]; c <= spans[row][1]; c++)
                    if (r.cells[row][c].empty()) throw new IllegalArgumentException("rung " + number + ": gap in branch " + row);
        }
        List<String> out = new ArrayList<>();
        int c = 0;
        while (c < COLUMNS) {
            List<Integer> group = new ArrayList<>();
            int end = c;
            for (int row = 1; row < ROWS; row++)
                if (spans[row] != null && spans[row][0] == c) {
                    group.add(row);
                    end = Math.max(end, spans[row][1]);
                }
            if (group.isEmpty()) {
                for (int row = 1; row < ROWS; row++)
                    if (spans[row] != null && spans[row][0] < c && spans[row][1] >= c)
                        throw new IllegalArgumentException("rung " + number + ": overlapping branches");
                if (!r.cells[0][c].empty()) out.add(r.cells[0][c].token());
                c++;
                continue;
            }
            StringBuilder b = new StringBuilder("BST");
            appendSeries(b, r.cells[0], c, end);
            for (int row : group) {
                b.append(" NXB");
                appendSeries(b, r.cells[row], c, end);
            }
            out.add(b.append(" BND").toString());
            c = end + 1;
        }
        out.add(r.coil.name() + "(" + r.coilOperand + ")");
        return String.join(" ", out);
    }

    private static void appendSeries(StringBuilder b, Cell[] row, int from, int to) {
        for (int c = from; c <= to; c++) if (!row[c].empty()) b.append(' ').append(row[c].token());
    }

    // ------------------------------------------------------------------ текст → сетка

    private static final Pattern TOKEN = Pattern.compile("([A-Z]+)(?:\\(([^)]*)\\))?");

    /**
     * Разобрать текст LD в сетку. Представимы ступени без вложенных ветвей, с одной катушкой и не длиннее
     * {@value #COLUMNS} клеток; иначе — {@link IllegalArgumentException} (текст остаётся в текстовом редакторе).
     */
    public static LadderGrid fromText(String src) {
        LadderGrid g = new LadderGrid();
        String[] lines = src.split("\\R");
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            int cmt = line.indexOf("//");
            if (cmt >= 0) line = line.substring(0, cmt);
            line = line.replace("(*LD*)", "").strip();
            if (line.isEmpty()) continue;
            Rung r = g.addRung();
            int col = 0;
            Matcher m = TOKEN.matcher(line);
            List<List<Cell>> alts = null;
            boolean coilSeen = false;
            while (m.find()) {
                String op = m.group(1);
                String[] args = m.group(2) == null ? new String[0] : m.group(2).split(",");
                switch (op) {
                    case "BST" -> {
                        if (alts != null) throw new IllegalArgumentException("line " + (n + 1) + ": nested branch");
                        alts = new ArrayList<>();
                        alts.add(new ArrayList<>());
                    }
                    case "NXB" -> {
                        if (alts == null) throw new IllegalArgumentException("line " + (n + 1) + ": NXB outside a branch");
                        alts.add(new ArrayList<>());
                    }
                    case "BND" -> {
                        if (alts == null || alts.size() > ROWS) throw new IllegalArgumentException("line " + (n + 1) + ": branch");
                        int width = 0;
                        for (List<Cell> a : alts) width = Math.max(width, Math.max(1, a.size()));
                        if (col + width > COLUMNS) throw new IllegalArgumentException("line " + (n + 1) + ": too long");
                        for (int row = 0; row < alts.size(); row++)
                            for (int k = 0; k < alts.get(row).size(); k++) r.cells[row][col + k] = alts.get(row).get(k);
                        col += width;
                        alts = null;
                    }
                    case "OTE", "OTL", "OTU", "RES" -> {
                        if (coilSeen) throw new IllegalArgumentException("line " + (n + 1) + ": one coil per rung in the grid");
                        coilSeen = true;
                        r.coil = Coil.valueOf(op);
                        r.coilOperand = args.length > 0 ? args[0].strip() : "";
                    }
                    default -> {
                        Kind k = null;
                        for (Kind kk : Kind.values()) if (kk != Kind.EMPTY && kk.op.equals(op)) k = kk;
                        if (k == null) throw new IllegalArgumentException("line " + (n + 1) + ": " + op);
                        Cell cell = new Cell(k, args.length > 0 ? args[0].strip() : "", args.length > 1 ? args[1].strip() : "");
                        if (alts != null) alts.getLast().add(cell);
                        else {
                            if (col >= COLUMNS) throw new IllegalArgumentException("line " + (n + 1) + ": too long");
                            r.cells[0][col++] = cell;
                        }
                    }
                }
            }
            if (!coilSeen) throw new IllegalArgumentException("line " + (n + 1) + ": rung without a coil");
        }
        return g;
    }

    // ------------------------------------------------------------------ онлайн

    /**
     * «Поток энергии» ступени (онлайн-режим): есть ли питание на правой стороне каждой клетки при состояниях
     * операндов {@code state} (адрес → значение; {@code null} — неизвестно, считается разомкнутым). Блоки пропускают
     * питание, если оно есть на входе и их выход {@code имя.Q} известен как TRUE. Последний элемент массива —
     * питание катушки.
     */
    public static boolean[][] flow(Rung r, Function<String, Boolean> state) {
        boolean[][] out = new boolean[ROWS + 1][COLUMNS];
        int[][] spans = new int[ROWS][];
        for (int row = 1; row < ROWS; row++) spans[row] = r.span(row);
        boolean power = true;
        int c = 0;
        while (c < COLUMNS) {
            int end = c;
            List<Integer> group = new ArrayList<>();
            for (int row = 1; row < ROWS; row++)
                if (spans[row] != null && spans[row][0] == c) {
                    group.add(row);
                    end = Math.max(end, spans[row][1]);
                }
            if (group.isEmpty()) {
                power = pass(r.cells[0][c], power, state);
                out[0][c] = power;
                c++;
                continue;
            }
            boolean any = false;
            for (int row : java.util.stream.Stream.concat(java.util.stream.Stream.of(0), group.stream()).toList()) {
                boolean p = power;
                for (int k = c; k <= end; k++) {
                    p = pass(r.cells[row][k], p, state);
                    out[row][k] = p;
                }
                any |= p;
            }
            power = any;
            c = end + 1;
        }
        out[ROWS][0] = power;
        return out;
    }

    private static boolean pass(Cell cell, boolean in, Function<String, Boolean> state) {
        if (!in) return false;
        return switch (cell.kind()) {
            case EMPTY -> true;
            case NO -> Boolean.TRUE.equals(state.apply(cell.operand()));
            case NC -> Boolean.FALSE.equals(state.apply(cell.operand()));
            default -> Boolean.TRUE.equals(state.apply(cell.operand() + ".Q"));
        };
    }
}
