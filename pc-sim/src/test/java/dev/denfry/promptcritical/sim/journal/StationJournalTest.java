package dev.denfry.promptcritical.sim.journal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.core.Severity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Журнал станции 04d §4d.8: кольцевой буфер, отбор, прореживание отказов, серии блоков, CSV, шкала. */
class StationJournalTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
    private static final UUID ALICE = new UUID(1, 1), BOB = new UUID(1, 2);

    private static JournalEntry e(UUID station, Actor actor, JournalAction a, Severity s, long tick) {
        return new JournalEntry(0, tick, tick / 20.0, station, actor, a, "dev", "", s);
    }

    @Test
    void ringBufferKeepsTheLastEntriesPerStationAndSeqKeepsGrowing() {
        StationJournal j = new StationJournal(3);
        Actor auto = new Actor.Automatic("PROTECTION");
        for (int i = 0; i < 5; i++) j.append(e(A, auto, JournalAction.COMMAND, Severity.INFO, i));
        JournalEntry other = j.append(e(B, auto, JournalAction.TRIP, Severity.CRITICAL, 9));

        assertThat(j.entries(A)).extracting(JournalEntry::gameTick).containsExactly(2L, 3L, 4L);
        assertThat(j.entries(A)).extracting(JournalEntry::seq).containsExactly(3L, 4L, 5L);
        assertThat(other.seq()).isEqualTo(6);
        assertThat(j.entries(B)).containsExactly(other);
        assertThat(new StationJournal().maxEntriesPerStation()).isEqualTo(10_000);
    }

    @Test
    void queryReturnsTheNewestMatchesInOrder() {
        StationJournal j = new StationJournal();
        Actor alice = new Actor.Player(ALICE, "alice"), bob = new Actor.Player(BOB, "bob");
        j.append(e(A, alice, JournalAction.COMMAND, Severity.INFO, 1));
        j.append(e(A, bob, JournalAction.UNAUTHORIZED_ACCESS, Severity.WARNING, 2));
        j.append(e(A, alice, JournalAction.BYPASS_ON, Severity.WARNING, 3));
        j.append(e(A, new Actor.Computer(7, BOB, "SCADA"), JournalAction.COMMAND, Severity.INFO, 4));

        assertThat(j.query(A, JournalFilter.ALL, 2)).extracting(JournalEntry::gameTick).containsExactly(3L, 4L);
        assertThat(j.query(A, JournalFilter.ownActions(BOB), 10)).extracting(JournalEntry::gameTick)
                .containsExactly(2L, 4L);
        assertThat(j.query(A, new JournalFilter(Set.of(), Severity.WARNING, null), 10))
                .extracting(JournalEntry::gameTick).containsExactly(2L, 3L);
        assertThat(j.query(A, new JournalFilter(Set.of(JournalAction.COMMAND), null, null), 10)).hasSize(2);
        assertThat(j.query(B, JournalFilter.ALL, 10)).isEmpty();
    }

    @Test
    void restoreContinuesNumbering() {
        StationJournal j = new StationJournal();
        j.restore(new JournalEntry(41, 5, 0, A, new Actor.Automatic("X"), JournalAction.TRIP, "", "", Severity.CRITICAL));
        assertThat(j.append(e(A, new Actor.Automatic("X"), JournalAction.TRIP_RESET, Severity.NOTICE, 6)).seq())
                .isEqualTo(42);
    }

    @Test
    void accessDenialIsLoggedOncePerMinutePerActorAndDevice() {
        AccessDenialLimiter lim = new AccessDenialLimiter();
        assertThat(lim.shouldLog(ALICE, "panel@1,2,3", 0)).isTrue();
        assertThat(lim.shouldLog(ALICE, "panel@1,2,3", 1199)).isFalse();
        assertThat(lim.shouldLog(ALICE, "panel@9,9,9", 10)).isTrue();
        assertThat(lim.shouldLog(BOB, "panel@1,2,3", 10)).isTrue();
        assertThat(lim.shouldLog(ALICE, "panel@1,2,3", 1200)).isTrue();
    }

    @Test
    void blockSeriesOfOnePlayerWithinTenSecondsIsOneEntry() {
        BlockSeriesAggregator agg = new BlockSeriesAggregator();
        Actor.Player alice = new Actor.Player(ALICE, "alice");
        for (long t = 0; t <= 400; t += 100)
            assertThat(agg.note(A, alice, JournalAction.BLOCK_BREAK, "0,64,0", true, t)).isEmpty();
        assertThat(agg.flush(600, false)).isEmpty();

        List<JournalEntry> closed = agg.note(A, alice, JournalAction.BLOCK_BREAK, "5,64,0", true, 601);
        assertThat(closed).singleElement().satisfies(x -> {
            assertThat(x.detail()).startsWith("count=5 ");
            assertThat(x.severity()).isEqualTo(Severity.INFO);
            assertThat(x.target()).isEqualTo("0,64,0");
        });

        agg.note(A, new Actor.Player(BOB, "bob"), JournalAction.BLOCK_PLACE, "1,64,0", false, 610);
        List<JournalEntry> flushed = agg.flush(900, false);
        assertThat(flushed).hasSize(2);
        assertThat(flushed).filteredOn(x -> x.action() == JournalAction.BLOCK_PLACE).singleElement()
                .extracting(JournalEntry::severity).isEqualTo(Severity.WARNING);
        assertThat(agg.flush(10_000, true)).isEmpty();
    }

    @Test
    void csvQuotesAndDefusesFormulas() {
        JournalEntry x = new JournalEntry(1, 20, 1.0, A, new Actor.Player(ALICE, "=cmd"), JournalAction.COMMAND,
                "rod, group 1", "say \"hi\"", Severity.INFO);
        String csv = JournalCsv.format(List.of(x));
        assertThat(csv).startsWith(JournalCsv.HEADER + "\r\n");
        assertThat(csv).contains(",PLAYER,'=cmd," + ALICE + ",COMMAND,\"rod, group 1\",\"say \"\"hi\"\"\",INFO\r\n");
        assertThat(csv).contains(",1.000,");
    }

    @Test
    void incidentScaleFollowsReleaseThresholds() {
        assertThat(IncidentScale.levelForRelease(1e12)).isZero();
        assertThat(IncidentScale.levelForRelease(2e12)).isEqualTo(3);
        assertThat(IncidentScale.levelForRelease(2e13)).isEqualTo(4);
        assertThat(IncidentScale.levelForRelease(2e14)).isEqualTo(5);
        assertThat(IncidentScale.levelForRelease(2e15)).isEqualTo(6);
        assertThat(IncidentScale.levelForRelease(2e16)).isEqualTo(7);
        assertThat(IncidentScale.audience(IncidentScale.LEVEL_TRIP)).isEqualTo(IncidentScale.Audience.STATION);
        assertThat(IncidentScale.audience(3)).isEqualTo(IncidentScale.Audience.STATION_AND_NEIGHBOURS);
        assertThat(IncidentScale.audience(4)).isEqualTo(IncidentScale.Audience.SERVER);
        assertThat(IncidentScale.audience(5)).isEqualTo(IncidentScale.Audience.SERVER_SOUND);
        assertThat(IncidentScale.audience(7)).isEqualTo(IncidentScale.Audience.SERVER_TITLE);
        assertThat(IncidentScale.announce(2, IncidentScale.DEFAULT_ANNOUNCE_MIN_LEVEL)).isFalse();
        assertThat(IncidentScale.announce(3, IncidentScale.DEFAULT_ANNOUNCE_MIN_LEVEL)).isTrue();
    }
}
