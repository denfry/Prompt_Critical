package dev.denfry.promptcritical.sim.consequence;

import dev.denfry.promptcritical.sim.core.GridPos;
import java.util.UUID;

/**
 * Единственный путь, по которому домен меняет блоки мира в результате аварий (04d §4d.2): домен подаёт запрос,
 * бюджет сразу решает, сколько блоков и когда будет изменено, а биндинг применяет изменения на серверном потоке.
 */
public interface DamageBudget {
    /** Вызывается доменом (серверный поток, после FAST). Возвращает решение сразу; блоки применяются позже. */
    DamageGrant submit(DamageRequest request);

    /** Вызывается биндингом каждый тик: применяет не более {@code damage.max_blocks_per_tick} операций очереди. */
    void drain(long gameTick, BlockMutator mutator, TerritoryOracle territory);

    BudgetStats stats();

    /** Реализация в {@code pc-mod}: setBlock, выпадение предметов, пакетный пересчёт света. */
    interface BlockMutator {
        BlockInfo inspect(GridPos p);

        void destroy(GridPos p, boolean dropItems);

        /** Заменить блок, например {@code "promptcritical:corium"}, {@code "minecraft:fire"}. */
        void replace(GridPos p, String blockId);
    }

    /** Claims, радиус станции, спавн. */
    interface TerritoryOracle {
        TerritoryOracle EVERYWHERE = (team, p) -> true;

        boolean mayDamage(UUID responsibleTeam, GridPos p);
    }
}
