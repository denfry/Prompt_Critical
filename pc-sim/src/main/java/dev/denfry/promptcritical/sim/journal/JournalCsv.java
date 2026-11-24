package dev.denfry.promptcritical.sim.journal;

import java.util.List;
import java.util.Locale;

/** CSV для {@code /pc journal export} (04d §4d.8.2): RFC 4180, разделитель запятая, одна запись на строку. */
public final class JournalCsv {
    private JournalCsv() {}

    public static final String HEADER = "seq,game_tick,sim_time_s,station,actor_kind,actor,actor_uuid,action,target,detail,severity";

    public static String format(List<JournalEntry> entries) {
        StringBuilder sb = new StringBuilder(HEADER).append("\r\n");
        for (JournalEntry e : entries) {
            sb.append(e.seq()).append(',')
                    .append(e.gameTick()).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", e.simTime())).append(',')
                    .append(e.stationId()).append(',')
                    .append(e.actor().getClass().getSimpleName().toUpperCase(Locale.ROOT)).append(',')
                    .append(quote(e.actor().display())).append(',')
                    .append(e.actor().responsible() == null ? "" : e.actor().responsible()).append(',')
                    .append(e.action()).append(',')
                    .append(quote(e.target())).append(',')
                    .append(quote(e.detail())).append(',')
                    .append(e.severity()).append("\r\n");
        }
        return sb.toString();
    }

    /**
     * Поле в кавычках при запятой, кавычке или переводе строки. Ведущие {@code = + - @} экранируются апострофом, чтобы
     * табличный редактор не принял имя или текст игрока за формулу.
     */
    static String quote(String s) {
        if (s == null) return "";
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.indexOf(',') < 0 && s.indexOf('"') < 0 && s.indexOf('\n') < 0 && s.indexOf('\r') < 0) return s;
        return '"' + s.replace("\"", "\"\"") + '"';
    }
}
