package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.grid.MachineLoads;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Энергопроект 2: у каждой машины мода решено, откуда она берёт энергию ({@link MachineLoads}). Тест создаёт BE
 * каждого блока мода (первое состояние, у которого BE есть: у сборок BE только на контроллере) и собирает все
 * неразобранные в одно сообщение.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineLoadGameTests {
    private MachineLoadGameTests() {
    }

    @GameTest(template = "gametest/empty_3x3x3", batch = "machine_loads")
    public static void everyMachineHasALoadClass(GameTestHelper h) {
        BlockPos pos = h.absolutePos(BlockPos.ZERO);
        Set<String> missing = new TreeSet<>();
        int checked = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!PromptCritical.MOD_ID.equals(id.getNamespace()) || !(block instanceof EntityBlock eb)) continue;
            BlockEntity be = null;
            for (BlockState s : block.getStateDefinition().getPossibleStates()) {
                be = eb.newBlockEntity(pos, s);
                if (be != null) break;
            }
            if (be == null) continue;
            checked++;
            if (MachineLoads.of(be, id) == null) missing.add(id.getPath() + " (" + be.getClass().getSimpleName() + ")");
        }
        if (!missing.isEmpty())
            h.fail(missing.size() + " of " + checked + " machines have no load class in MachineLoads: " + String.join(", ", missing));
        h.succeed();
    }
}
