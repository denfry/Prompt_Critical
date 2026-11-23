package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.command.PcPermissions;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;

/** Узлы PermissionAPI (16 §16.11.4): все объявлены, значения по умолчанию — как в таблице раздела. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PermissionGameTests {
    private PermissionGameTests() {
    }

    @GameTest(template = "gametest/empty_12x12x12")
    public static void permissionNodesAreRegistered(GameTestHelper h) {
        for (PermissionNode<Boolean> node : PcPermissions.ALL)
            h.assertTrue(PermissionAPI.getRegisteredNodes().contains(node), "node not registered: " + node.getNodeName());
        h.assertTrue(PcPermissions.ALL.stream().allMatch(n -> n.getNodeName().startsWith(PromptCritical.MOD_ID + ".")),
                "nodes must live under " + PromptCritical.MOD_ID);
        ServerPlayer player = TestPlayers.of(h);
        h.assertTrue(PcPermissions.allows(player, PcPermissions.COMMAND_ENV), "command.env is open to everyone");
        h.assertTrue(PcPermissions.allows(player, PcPermissions.STATION_CREATE), "station.create is open to everyone");
        h.assertFalse(PcPermissions.allows(player, PcPermissions.LOTO_OVERRIDE), "ops.loto_override is for operators");
        h.assertFalse(PcPermissions.allows(player, PcPermissions.SOURCES_ADMIN), "sources.admin is for operators");
        h.succeed();
    }
}
