package dev.denfry.promptcritical.sim.automation.plc;

/**
 * Функциональный блок {@code PC_ANTISURGE} — антипомпажный регулятор компрессора (03d §3d.5.12, R12 00 §0.13). Входы:
 * перепад на диафрагме всаса {@code DPO}, давления всаса и нагнетания {@code PS}, {@code PD}, линия помпажа из паспорта
 * компрессора как наклон {@code KS} прямой q²_surge = KS·(R_c − 1) (паспорт 09 даёт таблицу {@code CompressorMap};
 * у центробежных машин в приведённых координатах она почти прямая — блок берёт её наклон), запасы {@code BCTRL} и
 * {@code BRTL}, настройки ПИ {@code KP}, {@code TI}, скорость закрытия {@code RATE} (доля хода в секунду),
 * ручной режим {@code MAN} с выходом {@code XM}. Выходы: {@code Y} — задание антипомпажному клапану, В шкалы 0–10
 * (10 — открыт), {@code SS} — запас S_s, {@code DEV}, {@code CNT} — счётчик помпажей.
 *
 * <pre>
 * q²_r = DPO/PS,  R_c = PD/PS,  S_s = q²_surge(R_c)/q²_r,  DEV = 1/(1 + b_ctrl) − S_s
 * </pre>
 *
 * <ol>
 *   <li>ПИ по DEV с заданием 0: DEV &lt; 0 открывает клапан. Открытие без ограничения, закрытие — не быстрее RATE
 *       (по умолчанию 1 %/с); интеграл следит за фактическим выходом — без насыщения.</li>
 *   <li>Линия отскока: при S_s &gt; 1/(1 + b_rtl) выход в том же скане скачком растёт на {@value #STEP} хода; повторное
 *       пересечение в течение {@value #RTL_WINDOW_S} с удваивает шаг (T-3d-19).</li>
 *   <li>Обнаружение помпажа: обвал q²_r быстрее {@value #K_DET} 1/с (доля от прошлого скана) или реверс (q²_r = 0 при
 *       сжатии) — счётчик помпажей, b_ctrl растёт на {@value #BIAS_STEP} (до {@value #BIAS_MAX} раз): «смещение
 *       безопасности».</li>
 * </ol>
 *
 * По умолчанию b_ctrl = 0,10, b_rtl = 0,03 [BALANCE] (03d §3d.5.12). При первом скане после пуска ПЛК клапан открыт
 * (безопасное положение рецикла). {@code K_DET} 03d не задаёт — выбран так, чтобы обвал расхода в цикле помпажа
 * (≈ 1 с) ловился за 1–2 скана, а переходы режима (секунды) — нет.
 */
public final class AntiSurge {
    public static final int DPO = 0, PS = 1, PD = 2, KS = 3, BCTRL = 4, BRTL = 5, KP = 6, TI = 7, RATE = 8, MAN = 9, XM = 10;
    public static final int Y = 0, SS = 1, DEV = 2, CNT = 3;
    public static final int INPUTS = 11, OUTPUTS = 4, STATES = 8;
    /** Шаг линии отскока, доля хода; окно удвоения, с. */
    public static final double STEP = 0.30, RTL_WINDOW_S = 10;
    /** Обнаружение: относительная скорость обвала q²_r, 1/с; смещение b_ctrl после помпажа и предел числа смещений. */
    public static final double K_DET = 8, BIAS_STEP = 0.02;
    public static final int BIAS_MAX = 3;
    /** Значения входов по умолчанию (холодный пуск). */
    public static final double DEF_BCTRL = 0.10, DEF_BRTL = 0.03, DEF_KP = 10, DEF_TI = 5, DEF_RATE = 0.01;
    private static final double V = 10;
    // состояние: 0 интеграл, 1 прошлый выход, 2 прошлое q², 3 время от последнего отскока, 4 множитель шага,
    // 5 был за линией отскока, 6 число смещений, 7 инициализирован (1) и защёлка помпажа (2)
    private static final int S_I = 0, S_Y = 1, S_Q2 = 2, S_RTL_T = 3, S_MUL = 4, S_BEYOND = 5, S_BIAS = 6, S_FLAGS = 7;

    private AntiSurge() {
    }

    /** Значения входов при холодном пуске: всё, что игрок не подаёт, берётся отсюда. */
    public static void defaults(double[] in, int ib) {
        in[ib + BCTRL] = DEF_BCTRL;
        in[ib + BRTL] = DEF_BRTL;
        in[ib + KP] = DEF_KP;
        in[ib + TI] = DEF_TI;
        in[ib + RATE] = DEF_RATE;
        in[ib + XM] = V;
    }

    /** Один скан длиной {@code dt}. */
    public static void scan(double[] in, int ib, double[] out, int ob, double[] st, int sb, double dt) {
        double ps = in[ib + PS], pd = in[ib + PD], dpo = Math.max(0, in[ib + DPO]);
        double q2 = ps > 0 ? dpo / ps : 0, rc = ps > 0 ? pd / ps : 1;
        double q2s = Math.max(0, in[ib + KS]) * Math.max(0, rc - 1);
        double ss = q2 > 1e-9 ? q2s / q2 : (q2s > 0 ? 10 : 0);
        int flags = (int) st[sb + S_FLAGS];
        boolean init = (flags & 1) != 0, latched = (flags & 2) != 0;
        if (!init) {
            st[sb + S_Y] = V;
            st[sb + S_Q2] = q2;
            st[sb + S_RTL_T] = Double.MAX_VALUE;
            st[sb + S_MUL] = 1;
        }
        // обнаружение помпажа по обвалу расхода или реверсу при сжатии
        double q2p = st[sb + S_Q2];
        boolean crash = init && dt > 0 && q2p > 1e-6 && (q2p - q2) / dt > K_DET * q2p;
        boolean reversal = init && q2p > 1e-6 && q2 <= 1e-9 && rc > 1.05;
        if ((crash || reversal) && !latched) {
            out[ob + CNT] += 1;
            if (st[sb + S_BIAS] < BIAS_MAX) st[sb + S_BIAS] += 1;
            latched = true;
        }
        double b = Math.max(0, in[ib + BCTRL]) + BIAS_STEP * st[sb + S_BIAS];
        if (latched && ss < 1 / (1 + b)) latched = false;
        double dev = 1 / (1 + b) - ss;
        double e = -dev, kp = in[ib + KP], ti = in[ib + TI], yPrev = st[sb + S_Y];
        if (!init) st[sb + S_I] = yPrev - kp * e;
        double y;
        st[sb + S_RTL_T] = Math.min(Double.MAX_VALUE, st[sb + S_RTL_T] + dt);
        if (in[ib + MAN] != 0) {
            y = clamp(in[ib + XM]);
            st[sb + S_BEYOND] = 0;
        } else {
            if (ti > 0) st[sb + S_I] += kp * dt / ti * e;
            double raw = kp * e + st[sb + S_I];
            double rate = Math.max(0, in[ib + RATE]) * V * dt;
            y = Math.max(raw, yPrev - rate);
            boolean beyond = ss > 1 / (1 + Math.max(0, in[ib + BRTL]));
            if (beyond && st[sb + S_BEYOND] == 0) {
                st[sb + S_MUL] = st[sb + S_RTL_T] <= RTL_WINDOW_S ? st[sb + S_MUL] * 2 : 1;
                st[sb + S_RTL_T] = 0;
                y = Math.max(y, yPrev + STEP * V * st[sb + S_MUL]);
            }
            st[sb + S_BEYOND] = beyond ? 1 : 0;
            y = clamp(y);
        }
        st[sb + S_I] = y - kp * e; // внешний сброс: интеграл следит за выходом (скорость закрытия, пределы, ручной)
        st[sb + S_Y] = y;
        st[sb + S_Q2] = q2;
        st[sb + S_FLAGS] = 1 | (latched ? 2 : 0);
        out[ob + Y] = y;
        out[ob + SS] = ss;
        out[ob + DEV] = dev;
    }

    private static double clamp(double y) {
        return Double.isNaN(y) ? V : Math.max(0, Math.min(V, y));
    }

    /** Отдельный экземпляр блока вне ПЛК (встроенный регулятор компрессора в мире): те же входы и логика. */
    public static final class Instance {
        public final double[] in = new double[INPUTS], out = new double[OUTPUTS];
        private final double[] st = new double[STATES];

        public Instance() {
            defaults(in, 0);
        }

        public void scan(double dt) {
            AntiSurge.scan(in, 0, out, 0, st, 0, dt);
        }

        public double y() {
            return out[Y];
        }

        public double[] state() {
            double[] s = new double[STATES + OUTPUTS];
            System.arraycopy(st, 0, s, 0, STATES);
            System.arraycopy(out, 0, s, STATES, OUTPUTS);
            return s;
        }

        public void restore(double[] s) {
            if (s == null || s.length < STATES + OUTPUTS) return;
            System.arraycopy(s, 0, st, 0, STATES);
            System.arraycopy(s, STATES, out, 0, OUTPUTS);
        }
    }
}
