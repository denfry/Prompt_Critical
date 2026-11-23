package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.DeviceHost;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Блок реального масштаба в симуляции — тот же, что и старый: его BE отдаёт тип устройства старого блока (id без
 * {@code _real}), иначе симуляция не делает ему двойника и машина стоит (плейтест I2: пресс на вращающемся валу).
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RealScaleSimTypeGameTests {
    private RealScaleSimTypeGameTests() {}

    @GameTest(template = "gametest/empty_12x24x12", timeoutTicks = 20)
    public static void everyRealScaleDeviceHasTheOldSimType(GameTestHelper h) {
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            if (!id.getNamespace().equals(PromptCritical.MOD_ID) || !id.getPath().endsWith("_real")) continue;
            ResourceLocation oldId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(0, id.getPath().length() - 5));
            if (!BuiltInRegistries.BLOCK.containsKey(oldId)) continue;
            DeviceHost real = host(BuiltInRegistries.BLOCK.get(id));
            if (real == null) continue;
            DeviceHost old = host(BuiltInRegistries.BLOCK.get(oldId));
            String want = old != null ? old.deviceType() : oldId.toString();
            String got = real.deviceType();
            checked++;
            if (!want.equals(got)) bad.add(id.getPath() + " -> " + got + " (want " + want + ")");
        }
        h.assertTrue(checked > 0, "no real-scale device checked");
        h.assertTrue(bad.isEmpty(), "real-scale devices without the old sim type: " + bad);
        h.succeed();
    }

    /** BE контроллера в состоянии по умолчанию, если это устройство симуляции. */
    private static DeviceHost host(Block b) {
        if (!(b instanceof EntityBlock e)) return null;
        BlockEntity be = e.newBlockEntity(BlockPos.ZERO, b.defaultBlockState());
        return be instanceof DeviceHost d ? d : null;
    }
}
