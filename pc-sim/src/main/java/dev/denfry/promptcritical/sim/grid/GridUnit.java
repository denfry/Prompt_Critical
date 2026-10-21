package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Двойник устройства сети мира в {@link GridSubsystem}: выводы (узлы — ключи связных частей кабелей и шин,
 * которые считает мод), уставки игрока и перенос состояния модели при пересборке. Смена узлов или уставок,
 * меняющих схему, пересобирает сеть в начале следующего тика.
 */
public abstract sealed class GridUnit permits GeneratorUnit, SwitchUnit, TransformerUnit, MotorUnit, ConsumerUnit,
        ConnectorUnit {
    /** Узел вывода, ещё не найденный миром. */
    public static final long NO_NODE = 0;

    final SimId id;
    GridSubsystem owner;
    private long[] nodes;
    List<RelaySetting> relays = List.of();

    GridUnit(SimId id, int terminals) {
        this.id = Objects.requireNonNull(id, "id");
        this.nodes = new long[terminals];
    }

    public SimId id() {
        return id;
    }

    public int terminals() {
        return nodes.length;
    }

    public long node(int terminal) {
        return nodes[terminal];
    }

    /** Узлы выводов (по одному на вывод); {@link #NO_NODE} — вывод ни с чем не соединён. */
    public void setNodes(long... keys) {
        if (keys.length != nodes.length) throw new IllegalArgumentException("terminals: " + nodes.length);
        if (Arrays.equals(nodes, keys)) return;
        nodes = keys.clone();
        changed();
    }

    /** Модули РЗА устройства (шкаф присоединения, слоты потребителя). */
    public void setRelays(List<RelaySetting> settings) {
        List<RelaySetting> next = List.copyOf(settings);
        if (next.equals(relays)) return;
        relays = next;
        changed();
    }

    public List<RelaySetting> relays() {
        return relays;
    }

    /** Класс напряжения вывода. */
    abstract VoltageClass terminalClass(int terminal);

    /** Вывод задаёт класс своей шины (генератор, обмотка трансформатора, ячейка КРУ, щит 0,4 кВ). */
    boolean definesClass(int terminal) {
        return false;
    }

    /** Добавить свои элементы в собранную сеть. */
    abstract void build(GridSubsystem.Build b);

    /** Снять переносимое состояние с модели перед пересборкой или сохранением. */
    void capture(PowerGrid g) {
    }

    /** Ссылки на элементы прежней сети больше не действуют. */
    void unbind() {
    }

    void changed() {
        if (owner != null) owner.markDirty();
    }

    void saveCommon(StateWriter out) {
        out.putLongs("nodes", nodes, nodes.length);
        out.putInt("relays", relays.size());
        for (int r = 0; r < relays.size(); r++) {
            RelaySetting s = relays.get(r);
            out.beginObject("relay" + r);
            out.putString("kind", s.kind().name());
            out.putString("generation", s.generation().name());
            out.putDouble("a", s.a());
            out.putDouble("b", s.b());
            out.endObject();
        }
    }

    void loadCommon(StateReader in) {
        long[] n = in.getLongs("nodes");
        if (n.length == nodes.length) nodes = n;
        int count = in.optInt("relays", 0);
        RelaySetting[] list = new RelaySetting[count];
        int k = 0;
        for (int r = 0; r < count; r++) {
            in.beginObject("relay" + r);
            try {
                list[k++] = new RelaySetting(RelaySetting.Kind.valueOf(in.getString("kind")),
                        RelayGeneration.valueOf(in.getString("generation")), in.getDouble("a"), in.getDouble("b"));
            } catch (IllegalArgumentException e) {
                k--; // модуль неизвестного вида из будущей версии — пропустить
            }
            in.endObject();
        }
        relays = List.of(Arrays.copyOf(list, k));
    }

    abstract void save(StateWriter out);

    abstract void load(StateReader in);
}
