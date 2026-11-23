package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.compat.create.AlarmListDisplaySource;
import dev.denfry.promptcritical.mod.compat.create.CreateDisplaySources;
import dev.denfry.promptcritical.mod.compat.create.PanelReadings;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Display Link Create на приборах мода (16 §16.2.1). */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DisplayLinkGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private DisplayLinkGameTests() {
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void instrumentsAreDisplaySources(GameTestHelper h) {
        BlockPos meter = new BlockPos(2, 2, 2), gauge = new BlockPos(4, 2, 2), annunciator = new BlockPos(6, 2, 2);
        h.setBlock(meter, PcBlocks.ENERGY_METER.get());
        h.setBlock(gauge, PcBlocks.INSTRUMENT_PRESSURE.get());
        h.setBlock(annunciator, PcBlocks.ANNUNCIATOR_PANEL.get());
        h.runAfterDelay(5, () -> {
            for (BlockPos p : List.of(meter, gauge, annunciator)) {
                List<DisplaySource> sources = DisplaySource.getAll(h.getLevel(), h.absolutePos(p));
                h.assertTrue(sources.contains(CreateDisplaySources.PANEL_READING.get()), "no instrument reading source at " + p);
                BlockEntity be = h.getBlockEntity(p);
                h.assertFalse(PanelReadings.names(be).isEmpty(), "no instruments listed at " + p);
                String line = PanelReadings.reading(be, 0).getString();
                h.assertFalse(line.isEmpty() || line.equals(PanelReadings.DASHES), "no reading at " + p + ": " + line);
            }
            h.assertTrue(DisplaySource.getAll(h.getLevel(), h.absolutePos(annunciator)).contains(CreateDisplaySources.ALARMS.get()),
                    "no alarm source on the annunciator");
            h.assertFalse(DisplaySource.getAll(h.getLevel(), h.absolutePos(gauge)).contains(CreateDisplaySources.ALARMS.get()),
                    "alarm source on a gauge");
            List<MutableComponent> alarms = AlarmListDisplaySource.lines(h.getBlockEntity(annunciator), 4);
            h.assertTrue(alarms.size() == 1 && !alarms.get(0).getString().equals(PanelReadings.DASHES), "idle annunciator lines: " + alarms);
            h.assertTrue(PanelReadings.reading(h.getBlockEntity(meter), 999).getString().equals(PanelReadings.DASHES),
                    "instrument number outside the panel must read dashes");
            h.succeed();
        });
    }
}
