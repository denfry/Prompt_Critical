package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.compat.XaeroWaypoints;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Строка путевой точки Xaero's Minimap (16 §16.5.1): поля, экранирование и имя измерения, как их пишет и читает сам
 * Xaero.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class XaeroWaypointGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private XaeroWaypointGameTests() {
    }

    @GameTest(template = LARGE)
    public static void waypointLineFollowsXaeroFormat(GameTestHelper h) {
        h.assertValueEqual(XaeroWaypoints.line("Source 1a2b3c4d", 120, 64, -340, XaeroWaypoints.RED, "minecraft:overworld"),
                "xaero-waypoint:Source 1a2b3c4d:S:120:64:-340:12:false:0:Internal-overworld-waypoints", "overworld source");
        h.assertValueEqual(XaeroWaypoints.line("Hub", -12, 70, 345, 13, "minecraft:the_nether"),
                "xaero-waypoint:Hub:H:-12:70:345:13:false:0:Internal-the-nether-waypoints", "the nether as Xaero writes it");
        h.assertValueEqual(XaeroWaypoints.line("Iron-ore_body: *A*", 0, -60, 0, XaeroWaypoints.GOLD, "minecraft:the_end"),
                "xaero-waypoint:Iron^min^ore-body^col^ ^ast^A^ast^:I:0:-60:0:6:false:0:Internal-the-end-waypoints",
                "colons, minus signs, underscores and stars escaped like Xaero");
        h.assertValueEqual(XaeroWaypoints.line("Магнетит", 5, 40, 7, 6, "promptcritical:deep_mine/lower.level"),
                "xaero-waypoint:Магнетит:М:5:40:7:6:false:0:Internal-dim%promptcritical$deep-mine%lower,level-waypoints",
                "a modded dimension by its Xaero folder name");
        h.assertValueEqual(XaeroWaypoints.line("#7", 1, 2, 3, 20, "minecraft:overworld"),
                "xaero-waypoint:#7:7:1:2:3:4:false:0:Internal-overworld-waypoints", "initial skips symbols, colour wraps into 0-15");
        h.succeed();
    }
}
