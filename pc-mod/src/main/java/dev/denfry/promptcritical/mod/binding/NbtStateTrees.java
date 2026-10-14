package dev.denfry.promptcritical.mod.binding;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Перевод дерева состояния домена в NBT и обратно (05 §5.2.8). Домен пишет в {@code StateWriter}, миграции
 * работают над {@link MutableStateTree}, а здесь — только формат: массивы {@code double} хранятся как
 * {@code long[]} сырых битов ({@code Double.doubleToRawLongBits}) под ключом с префиксом {@value #DOUBLES}.
 */
public final class NbtStateTrees {
    /** Префикс ключа массива double: в NBT нет типа {@code double[]}. */
    static final String DOUBLES = "d!";

    private NbtStateTrees() {
    }

    public static CompoundTag toNbt(MutableStateTree tree) {
        CompoundTag tag = new CompoundTag();
        for (String k : tree.keys()) {
            Object v = tree.get(k);
            switch (v) {
                case Integer i -> tag.put(k, IntTag.valueOf(i));
                case Long l -> tag.put(k, LongTag.valueOf(l));
                case Double d -> tag.put(k, DoubleTag.valueOf(d));
                case String s -> tag.put(k, StringTag.valueOf(s));
                case double[] a -> {
                    long[] bits = new long[a.length];
                    for (int j = 0; j < a.length; j++) bits[j] = Double.doubleToRawLongBits(a[j]);
                    tag.put(DOUBLES + k, new LongArrayTag(bits));
                }
                case long[] a -> tag.put(k, new LongArrayTag(a));
                case int[] a -> tag.put(k, new IntArrayTag(a));
                case byte[] a -> tag.put(k, new ByteArrayTag(a));
                case MutableStateTree t -> tag.put(k, toNbt(t));
                default -> throw new IllegalStateException("unsupported state value " + v.getClass());
            }
        }
        return tag;
    }

    public static MutableStateTree fromNbt(CompoundTag tag) {
        MutableStateTree tree = new MutableStateTree();
        for (String k : tag.getAllKeys()) {
            Tag v = tag.get(k);
            switch (v) {
                case IntTag i -> tree.put(k, i.getAsInt());
                case LongTag l -> tree.put(k, l.getAsLong());
                case DoubleTag d -> tree.put(k, d.getAsDouble());
                case StringTag s -> tree.put(k, s.getAsString());
                case LongArrayTag a when k.startsWith(DOUBLES) -> {
                    long[] bits = a.getAsLongArray();
                    double[] out = new double[bits.length];
                    for (int j = 0; j < bits.length; j++) out[j] = Double.longBitsToDouble(bits[j]);
                    tree.put(k.substring(DOUBLES.length()), out);
                }
                case LongArrayTag a -> tree.put(k, a.getAsLongArray());
                case IntArrayTag a -> tree.put(k, a.getAsIntArray());
                case ByteArrayTag a -> tree.put(k, a.getAsByteArray());
                case CompoundTag c -> tree.put(k, fromNbt(c));
                default -> throw new IllegalStateException("unsupported NBT tag " + v.getType().getName() + " at '" + k + "'");
            }
        }
        return tree;
    }
}
