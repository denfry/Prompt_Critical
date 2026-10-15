package dev.denfry.promptcritical.sim.mining;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Arrays;

/**
 * Одна шахта мира (08 §8.2): граф выработок, вентиляция, атмосфера и фронт взрыва. Граф строит привязка заливкой
 * и отдаёт через {@link #rebuild}; якоря — опорные блоки, от которых заливка начинается (ключи блоков домену
 * непрозрачны). Все методы — с серверного потока: шаг SLOW здесь дешёвый (≈ 15 мкс на 45 узлов), поэтому идёт
 * прямо в FAST-шаге подсистемы, и показания, фронт и действия игрока видят одно и то же состояние.
 */
public final class MineNetwork {
    /** Итераций вентиляции на шаг SLOW (08 §8.2.7: ≤ 10, тёплый старт). */
    public static final int VENTILATION_ITERATIONS = 10;

    /** Сгоревший узел для привязки: заявка на разрушение, звук, ударная волна. */
    @FunctionalInterface
    public interface BurnSink {
        /** @param origin ключ блока-центра узла (раскладка {@code GridPos.asLong}) */
        void burned(int node, long origin, double energyJ);
    }

    private final long id;
    private final MineVentilation ventilation = new MineVentilation();
    private final MethaneField field;
    private long[] anchors = new long[0];
    private final LongIntMap worked = new LongIntMap(64);
    private long[] nodeOrigin = new long[0];
    private boolean[] nodeFlame = new boolean[0];
    private ExplosionFront front;
    private MineTopology frontTopology;
    private int reported;
    private int explosions;

    public MineNetwork(long id, MiningConfig cfg) {
        this.id = id;
        this.field = new MethaneField(cfg);
    }

    public long id() {
        return id;
    }

    public MethaneField field() {
        return field;
    }

    public MineVentilation ventilation() {
        return ventilation;
    }

    /** Граф уже построен (после загрузки — только когда привязка заново залила выработки). */
    public boolean ready() {
        return field.topology() != null;
    }

    public MineTopology topology() {
        return field.topology();
    }

    void setConfig(MiningConfig cfg) {
        field.setConfig(cfg);
    }

    // --- якоря ---------------------------------------------------------------------------------------------

    public long[] anchors() {
        return anchors.clone();
    }

    public void setAnchors(long[] keys) {
        long[] a = keys.clone();
        Arrays.sort(a);
        anchors = a;
    }

    // --- кровля -------------------------------------------------------------------------------------------

    /**
     * Блок кровли над выработкой, пройденной игроком или машиной. Правило пролёта (08 §8.2.2) проверяется только
     * для таких блоков: своды естественных пещер, в которые вышла выработка, держатся как в ванильном мире.
     */
    public void markWorkedRoof(long blockKey) {
        worked.put(blockKey, 1);
    }

    public void forgetWorkedRoof(long blockKey) {
        worked.remove(blockKey);
    }

    /** Ключи пройденной кровли (порядок не определён). */
    public long[] workedRoof() {
        long[] out = new long[worked.size()];
        int i = 0;
        for (int s = 0; s < worked.capacity(); s++) if (worked.usedAt(s)) out[i++] = worked.keyAt(s);
        return out;
    }

    // --- граф ----------------------------------------------------------------------------------------------

    /**
     * Новый граф: вентиляция и атмосфера переносят состояние по ключам, обнажения сверяются, фронт взрыва в работе
     * обрывается (его узлы больше не существуют).
     *
     * @param origins   блок-центр каждого узла (индекс 0 — поверхность, не используется)
     * @param flames    в узле открытое пламя (факел, костёр) — источник воспламенения
     * @param coalKeys  обнажённые блоки угля, приписанные узлам
     * @param coalXCh4  их газоносность, м³/т
     */
    public void rebuild(MineTopology topology, long[] origins, boolean[] flames, long[] coalKeys, double[] coalXCh4,
            int coalCount) {
        if (origins.length != topology.nodeCount() || flames.length != topology.nodeCount())
            throw new IllegalArgumentException("per-node arrays must match the graph");
        ventilation.rebuild(topology);
        field.rebuild(topology);
        field.syncExposures(coalKeys, coalXCh4, coalCount);
        nodeOrigin = origins.clone();
        nodeFlame = flames.clone();
        if (exploding()) {
            front = null;
            frontTopology = null;
        }
        reported = front == null ? 0 : front.burnedCount();
    }

    /** Блок-центр узла (ключ блока). */
    public long nodeOrigin(int node) {
        return nodeOrigin[node];
    }

    /** Узел блока или −1. */
    public int nodeOf(long blockKey) {
        MineTopology t = field.topology();
        return t == null ? -1 : t.nodeOf(blockKey);
    }

    // --- шаги ----------------------------------------------------------------------------------------------

    /** Шаг SLOW: сеть, затем газы; открытое пламя во взрывоопасной смеси воспламеняет её. */
    public void stepSlow(double dtPhys) {
        if (!ready()) return;
        ventilation.solveSlow(dtPhys, VENTILATION_ITERATIONS);
        field.stepSlow(dtPhys, ventilation);
        for (int n = 1; n < nodeFlame.length && !exploding(); n++)
            if (nodeFlame[n] && StoneDustRule.methaneExplosive(field.concentration(n, MineGas.CH4)))
                ignite(n, IgnitionSource.OPEN_FLAME);
    }

    /** Шаг фронта (тик): узел за тик по графу. */
    public void stepFront() {
        if (front != null && !front.done()) front.advance();
    }

    /**
     * Источник воспламенения в узле. Пока идёт фронт, второй не начинается.
     *
     * @return взрыв начался
     */
    public boolean ignite(int node, IgnitionSource source) {
        if (!ready() || exploding() || node <= 0 || node >= field.topology().nodeCount()) return false;
        ExplosionFront f = field.ignite(node, source);
        if (!f.ignited()) return false;
        front = f;
        frontTopology = field.topology();
        reported = 0;
        explosions++;
        return true;
    }

    /** Идёт фронт взрыва. */
    public boolean exploding() {
        return front != null && !front.done();
    }

    /** Последний фронт (или {@code null}); индексы его узлов — по {@link #lastFrontTopology()}. */
    public ExplosionFront lastFront() {
        return front;
    }

    /** Граф, по которому шёл последний фронт (после перестройки — прежний). */
    public MineTopology lastFrontTopology() {
        return frontTopology;
    }

    /** Число взрывов с загрузки. */
    public int explosions() {
        return explosions;
    }

    /** Отдать узлы, сгоревшие после прошлого вызова. */
    public int drainBurned(BurnSink sink) {
        if (front == null || frontTopology != field.topology()) return 0;
        int n = front.burnedCount(), from = reported;
        for (int i = from; i < n; i++) {
            int node = front.burnedNode(i);
            sink.burned(node, nodeOrigin[node], front.energyJ(i));
        }
        reported = n;
        return n - from;
    }

    // --- показания -----------------------------------------------------------------------------------------

    /** Наибольшая доля газа по узлам выработок (у метана — у кровли, со слоевыми скоплениями). */
    public double maxConcentration(MineGas gas) {
        if (!ready()) return gas.freshAir;
        double m = 0;
        for (int n = 1; n < field.topology().nodeCount(); n++)
            m = Math.max(m, gas == MineGas.CH4 ? field.roofMethane(n) : field.concentration(n, gas));
        return m;
    }

    /** Наименьшая доля кислорода по узлам выработок. */
    public double minOxygen() {
        if (!ready()) return MineGas.O2.freshAir;
        double m = 1;
        for (int n = 1; n < field.topology().nodeCount(); n++) m = Math.min(m, field.concentration(n, MineGas.O2));
        return m;
    }

    /** Суммарное газовыделение метана, м³/с (до дегазации). */
    public double methaneEmissionM3s() {
        if (!ready()) return 0;
        double s = 0;
        for (int n = 1; n < field.topology().nodeCount(); n++)
            s += field.emissionM3s(n, MineGas.CH4) + field.degasifiedM3s(n);
        return s;
    }

    /** Расход воздуха через поверхность (сумма притоков в выработки), м³/с. */
    public double surfaceAirflowM3s() {
        MineTopology t = field.topology();
        if (t == null) return 0;
        double s = 0;
        for (int j = 0; j < t.branchCount(); j++) {
            double q = ventilation.flow(j);
            if (t.from(j) == MineTopology.ATMOSPHERE && q > 0) s += q;
            else if (t.to(j) == MineTopology.ATMOSPHERE && q < 0) s -= q;
        }
        return s;
    }

    // --- сохранение ----------------------------------------------------------------------------------------

    void save(StateWriter out) {
        out.putLongs("anchors", anchors, anchors.length);
        long[] roof = workedRoof();
        Arrays.sort(roof);
        out.putLongs("worked_roof", roof, roof.length);
        out.beginObject("field");
        field.save(out);
        out.endObject();
    }

    void load(StateReader in) {
        setAnchors(in.getLongs("anchors"));
        worked.clear();
        if (in.has("worked_roof")) for (long k : in.getLongs("worked_roof")) worked.put(k, 1);
        in.beginObject("field");
        field.load(in);
        in.endObject();
    }
}
