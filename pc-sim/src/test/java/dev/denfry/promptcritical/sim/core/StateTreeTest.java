package dev.denfry.promptcritical.sim.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StateTreeTest {
    @Test
    void writerAndReaderAreSymmetric() {
        TreeStateWriter w = new TreeStateWriter();
        w.putInt("i", 3);
        w.putLong("l", 1L << 40);
        w.putDouble("d", -0.0);
        w.putString("s", "котёл");
        w.beginObject("arrays");
        w.putDoubles("dd", new double[] {1.5, Double.NaN, 3}, 2);
        w.putLongs("ll", new long[] {7, 8}, 2);
        w.putInts("ii", new int[] {1, 2, 3}, 3);
        w.putBytes("bb", new byte[] {-1}, 1);
        w.endObject();

        TreeStateReader r = new TreeStateReader(w.root());
        assertThat(r.getInt("i")).isEqualTo(3);
        assertThat(r.getLong("l")).isEqualTo(1L << 40);
        assertThat(Double.doubleToRawLongBits(r.getDouble("d"))).isEqualTo(Double.doubleToRawLongBits(-0.0));
        assertThat(r.getString("s")).isEqualTo("котёл");
        assertThat(r.optDouble("missing", 2.5)).isEqualTo(2.5);
        r.beginObject("arrays");
        assertThat(r.getDoubles("dd")).hasSize(2).startsWith(1.5);
        assertThat(Double.isNaN(r.getDoubles("dd")[1])).isTrue();
        assertThat(r.getLongs("ll")).containsExactly(7, 8);
        assertThat(r.getInts("ii")).containsExactly(1, 2, 3);
        assertThat(r.getBytes("bb")).containsExactly(-1);
        r.endObject();
        assertThatThrownBy(() -> r.getDouble("i")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hashIsSensitiveToEveryBitAndStable() {
        MutableStateTree a = new MutableStateTree().put("x", 1.0).put("y", new long[] {1, 2});
        MutableStateTree b = new MutableStateTree().put("x", 1.0).put("y", new long[] {1, 2});
        MutableStateTree c = new MutableStateTree().put("x", Math.nextUp(1.0)).put("y", new long[] {1, 2});
        assertThat(StateHash.of(a)).isEqualTo(StateHash.of(b)).isNotEqualTo(StateHash.of(c));
    }

    @Test
    void duplicateKeysAreRejected() {
        TreeStateWriter w = new TreeStateWriter();
        w.putInt("k", 1);
        assertThatThrownBy(() -> w.putInt("k", 2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void migrationChainNeedsEveryStep() {
        MigrationRegistry reg = new MigrationRegistry();
        reg.register(new Migration() {
            @Override
            public int from() {
                return 0;
            }

            @Override
            public void apply(MutableStateTree tree) {
                tree.rename("old", "new");
            }
        });
        MutableStateTree t = new MutableStateTree().put("old", 5);
        reg.upgrade(t, 0, 1);
        assertThat(t.has("old")).isFalse();
        assertThat(t.get("new")).isEqualTo(5);
        assertThatThrownBy(() -> reg.upgrade(new MutableStateTree(), 0, 2)).hasMessageContaining("no migration from schema 1");
        assertThatThrownBy(() -> reg.upgrade(new MutableStateTree(), 3, 1)).hasMessageContaining("newer");
    }
}
