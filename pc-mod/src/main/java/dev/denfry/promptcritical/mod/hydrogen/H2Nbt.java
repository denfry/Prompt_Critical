package dev.denfry.promptcritical.mod.hydrogen;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;

/** Состояние модели водородной станции в NBT блока. */
final class H2Nbt {
    private H2Nbt() {
    }

    static CompoundTag save(Consumer<StateWriter> saver) {
        MutableStateTree tree = new MutableStateTree();
        saver.accept(new TreeStateWriter(tree));
        return NbtStateTrees.toNbt(tree);
    }

    static StateReader reader(CompoundTag tag) {
        return new TreeStateReader(NbtStateTrees.fromNbt(tag));
    }
}
