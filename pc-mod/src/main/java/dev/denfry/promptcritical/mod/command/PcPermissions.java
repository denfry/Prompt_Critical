package dev.denfry.promptcritical.mod.command;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Узлы PermissionAPI NeoForge (16 §16.11.4): что игроку разрешено на сервере. LuckPerms и другие обработчики
 * подхватывают их сами. Запрос незарегистрированного узла бросает исключение, поэтому все узлы — в {@link #ALL}.
 * Консоль и командные блоки решают по уровню прав 2, как раньше весь {@code /pc}.
 */
public final class PcPermissions {
    public static final PermissionNode<Boolean> STATION_CREATE = everyone("station.create");
    public static final PermissionNode<Boolean> STATION_JOIN_ANY = operators("station.join_any");
    public static final PermissionNode<Boolean> LOTO_OVERRIDE = operators("ops.loto_override");
    public static final PermissionNode<Boolean> SOURCES_ADMIN = operators("sources.admin");
    public static final PermissionNode<Boolean> EXTERNAL_GATEWAY = operators("external.gateway");
    public static final PermissionNode<Boolean> WEBMAP_PUBLISH = everyone("webmap.publish");
    public static final PermissionNode<Boolean> COMMAND_ENV = everyone("command.env");
    public static final PermissionNode<Boolean> COMMAND_REGISTERS = everyone("command.registers");
    public static final PermissionNode<Boolean> COMMAND_STATS = everyone("command.stats");

    public static final List<PermissionNode<Boolean>> ALL = List.of(STATION_CREATE, STATION_JOIN_ANY, LOTO_OVERRIDE, SOURCES_ADMIN,
            EXTERNAL_GATEWAY, WEBMAP_PUBLISH, COMMAND_ENV, COMMAND_REGISTERS, COMMAND_STATS);

    private PcPermissions() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PermissionGatherEvent.Nodes.class, e -> e.addNodes(List.copyOf(ALL)));
    }

    /** Администратор сервера: уровень прав 2. */
    public static boolean admin(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    /** Разрешено ли источнику команды по узлу; не игрок — по уровню 2. */
    public static boolean allows(CommandSourceStack source, PermissionNode<Boolean> node) {
        ServerPlayer player = source.getPlayer();
        return player == null ? admin(source) : allows(player, node);
    }

    public static boolean allows(ServerPlayer player, PermissionNode<Boolean> node) {
        return Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
    }

    private static PermissionNode<Boolean> everyone(String name) {
        return new PermissionNode<>(PromptCritical.MOD_ID, name, PermissionTypes.BOOLEAN, (player, uuid, ctx) -> true);
    }

    private static PermissionNode<Boolean> operators(String name) {
        return new PermissionNode<>(PromptCritical.MOD_ID, name, PermissionTypes.BOOLEAN,
                (player, uuid, ctx) -> player != null && player.hasPermissions(2));
    }
}
