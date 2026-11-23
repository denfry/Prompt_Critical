package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/**
 * Команда игрока для профиля licensing: партия FTB Teams, если мод установлен и игрок в партии, иначе сам игрок. FTB
 * Teams читается через отражение по его публичному API, без жёсткой зависимости (16 R62); любая ошибка — игрок сам себе
 * команда, с одним предупреждением в журнале.
 */
public final class TeamResolver {
    private static volatile boolean warned;

    private TeamResolver() {}

    public static UUID team(Player p) {
        return team(p.getUUID());
    }

    public static UUID team(UUID player) {
        if (player == null) return null;
        if (!ModList.get().isLoaded("ftbteams")) return player;
        try {
            return ftbParty(player).orElse(player);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                PromptCritical.LOGGER.warn("FTB Teams present but its API could not be read; players count as their own teams", e);
            }
            return player;
        }
    }

    private static Optional<UUID> ftbParty(UUID player) throws ReflectiveOperationException {
        Class<?> apiHolder = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
        Object api = apiHolder.getMethod("api").invoke(null);
        Class<?> apiType = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
        if (!(Boolean) apiType.getMethod("isManagerLoaded").invoke(api)) return Optional.empty();
        Object manager = apiType.getMethod("getManager").invoke(api);
        Class<?> managerType = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
        Optional<?> team = (Optional<?>) managerType.getMethod("getTeamForPlayerID", UUID.class).invoke(manager, player);
        if (team.isEmpty()) return Optional.empty();
        Class<?> teamType = Class.forName("dev.ftb.mods.ftbteams.api.Team");
        Method party = teamType.getMethod("isPartyTeam");
        if (!(Boolean) party.invoke(team.get())) return Optional.empty();
        return Optional.of((UUID) teamType.getMethod("getId").invoke(team.get()));
    }
}
