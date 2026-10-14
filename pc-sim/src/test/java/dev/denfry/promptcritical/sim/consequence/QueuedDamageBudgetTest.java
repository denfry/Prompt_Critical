package dev.denfry.promptcritical.sim.consequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class QueuedDamageBudgetTest {
    private static final SimId SRC = SimId.of(SimId.Kind.DEVICE, 1);
    private static final UUID TEAM = new UUID(7, 7);
    /** Энергия, заведомо дающая P_MC выше любого лимита: радиус задаёт maxRadiusM. */
    private static final double HUGE_J = 1e15;

    /** Мир из сплошного камня с сопротивлением 0; бедрок ниже y = 0. */
    private static final class SolidWorld implements DamageBudget.BlockMutator {
        final List<GridPos> destroyed = new ArrayList<>();
        final Set<GridPos> gone = new HashSet<>();
        int drops;

        @Override
        public BlockInfo inspect(GridPos p) {
            if (gone.contains(p)) return BlockInfo.AIR;
            if (p.y() < 0) return new BlockInfo(false, true, 3_600_000f);
            return new BlockInfo(false, false, 0f);
        }

        @Override
        public void destroy(GridPos p, boolean dropItems) {
            destroyed.add(p);
            gone.add(p);
            if (dropItems) drops++;
        }

        @Override
        public void replace(GridPos p, String blockId) {
            throw new UnsupportedOperationException();
        }
    }

    private static DamageRequest request(long id, GridPos origin, double radius, int priority) {
        return new DamageRequest(id, SRC, TEAM, DamageKind.BLAST, origin, HUGE_J, radius, priority);
    }

    private static DamageBudgetConfig noAreaCaps(ServerMode mode) {
        DamageBudgetConfig d = DamageBudgetConfig.defaults(mode);
        return new DamageBudgetConfig(mode, d.maxBlocksPerTick(), d.maxBlocksPerEvent(), Integer.MAX_VALUE,
                Integer.MAX_VALUE, d.maxPending(), 24.0, d.tntReferenceKg(), d.itemDropFraction(),
                d.maxItemEntitiesPerEvent());
    }

    @Test
    @Tag("T-4d-07")
    void tenThousandBlocksAreCappedPerEventAndAppliedSixtyFourPerTick() {
        QueuedDamageBudget budget = new QueuedDamageBudget(noAreaCaps(ServerMode.REALISTIC));
        double r = 13.4;
        int requested = QueuedDamageBudget.Offsets.countWithin(r);
        assertThat(requested).isBetween(9_900, 10_200);

        DamageGrant g = budget.submit(request(1, new GridPos(100, 100, 100), r, 1));
        assertThat(g.blocksGranted()).isEqualTo(4096);
        assertThat(g.blocksDenied()).isEqualTo(requested - 4096);
        assertThat(g.reason()).isEqualTo(DenyReason.PER_EVENT_CAP);

        SolidWorld world = new SolidWorld();
        int ticks = 0;
        while (budget.stats().queueLength() > 0) {
            int before = world.destroyed.size();
            budget.drain(ticks, world, DamageBudget.TerritoryOracle.EVERYWHERE);
            assertThat(world.destroyed.size() - before).isEqualTo(64);
            ticks++;
        }
        assertThat(ticks).isEqualTo(64);
        assertThat(world.destroyed).hasSize(g.blocksGranted());
        assertThat(budget.stats().blocksAppliedTotal()).isEqualTo(g.blocksGranted());
        // обрушение идёт от центра: первым разрушен центр, расстояния не убывают
        assertThat(world.destroyed.getFirst()).isEqualTo(new GridPos(100, 100, 100));
        GridPos o = new GridPos(100, 100, 100);
        for (int i = 1; i < world.destroyed.size(); i++)
            assertThat(world.destroyed.get(i).distanceSq(o)).isGreaterThanOrEqualTo(world.destroyed.get(i - 1).distanceSq(o));
        assertThat(world.drops).isEqualTo(32);                    // 10 % от 4096, но не более 32 сущностей
    }

    @Test
    @Tag("T-4d-08")
    void secondRequestIntoSameChunkIsTrimmedToChunkDailyCap() {
        QueuedDamageBudget budget = new QueuedDamageBudget(DamageBudgetConfig.defaults(ServerMode.REALISTIC));
        GridPos centre = new GridPos(8, 64, 8);                  // сфера r = 6 целиком в чанке (0, 0)
        int n = QueuedDamageBudget.Offsets.countWithin(6.0);
        assertThat(n).isBetween(880, 950);

        DamageGrant first = budget.submit(request(1, centre, 6.0, 1));
        DamageGrant second = budget.submit(request(2, centre.offset(0, 20, 0), 6.0, 1));
        assertThat(first.blocksGranted()).isEqualTo(n);
        assertThat(second.blocksGranted()).isEqualTo(1024 - n);
        assertThat(second.reason()).isEqualTo(DenyReason.PER_CHUNK_CAP);

        // новые сутки — счётчики чанков обнуляются
        SolidWorld world = new SolidWorld();
        budget.drain(QueuedDamageBudget.TICKS_PER_DAY, world, DamageBudget.TerritoryOracle.EVERYWHERE);
        assertThat(budget.submit(request(3, centre.offset(0, 40, 0), 6.0, 1)).blocksGranted()).isEqualTo(n);
    }

    @Test
    @Tag("T-4d-10")
    void creativeSafeChangesNoBlocks() {
        QueuedDamageBudget budget = new QueuedDamageBudget(DamageBudgetConfig.defaults(ServerMode.CREATIVE_SAFE));
        DamageGrant g = budget.submit(new DamageRequest(1, SRC, TEAM, DamageKind.BLAST, new GridPos(0, 64, 0),
                182 * BlastScaling.TNT_J_PER_KG, 50, 2));
        assertThat(g.blocksGranted()).isZero();
        assertThat(g.reason()).isEqualTo(DenyReason.MODE_FORBIDS);
        SolidWorld world = new SolidWorld();
        budget.drain(0, world, DamageBudget.TerritoryOracle.EVERYWHERE);
        assertThat(world.destroyed).isEmpty();
    }

    @Test
    void containedModeRespectsTerritoryAndImmuneBlocks() {
        QueuedDamageBudget budget = new QueuedDamageBudget(noAreaCaps(ServerMode.CONTAINED));
        budget.submit(request(1, new GridPos(0, 0, 0), 3.0, 1));        // нижняя половина — бедрок
        SolidWorld world = new SolidWorld();
        DamageBudget.TerritoryOracle eastOnly = (team, p) -> team.equals(TEAM) && p.x() >= 0;
        for (int t = 0; t < 10; t++) budget.drain(t, world, eastOnly);
        assertThat(world.destroyed).isNotEmpty().allMatch(p -> p.x() >= 0 && p.y() >= 0);
        assertThat(budget.stats().deniedByReason()).containsKeys(DenyReason.OUTSIDE_TERRITORY, DenyReason.PROTECTED_BLOCK);
    }

    @Test
    void higherPriorityRequestIsAppliedFirst() {
        QueuedDamageBudget budget = new QueuedDamageBudget(noAreaCaps(ServerMode.REALISTIC));
        budget.submit(request(1, new GridPos(0, 10, 0), 2.0, 9));
        budget.submit(request(2, new GridPos(100, 10, 0), 2.0, 0));
        SolidWorld world = new SolidWorld();
        budget.drain(0, world, DamageBudget.TerritoryOracle.EVERYWHERE);
        assertThat(world.destroyed.getFirst()).isEqualTo(new GridPos(100, 10, 0));
    }

    @Test
    void blastPowerFollowsHopkinsonCranzScaling() {
        // 04d §4d.2.4: 182 кг ТНТ-экв. ⇒ P_MC = 4·(182/50)^{1/3} = 6,2
        assertThat(BlastScaling.power(182 * BlastScaling.TNT_J_PER_KG, 50, 12)).isCloseTo(6.15, within(0.01));
        assertThat(BlastScaling.power(1e12, 50, 8)).isEqualTo(8.0);
        assertThat(BlastScaling.radiusBlocks(4)).isEqualTo(5.0);
        // ванильный ТНТ: камень (6) у границы радиуса 5 устоит, на 4 блоках — разрушится
        assertThat(BlastScaling.breaks(4, 5, 6f)).isFalse();
        assertThat(BlastScaling.breaks(4, 4, 6f)).isTrue();
    }
}
