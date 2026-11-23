package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LicenceRegistryTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);

    @Test
    void i0IsAlwaysLicensed() {
        LicenceRegistry reg = new LicenceRegistry();
        UUID team = UUID.randomUUID();
        assertThat(reg.licensed(team, PlantEra.I0)).isTrue();
        assertThat(reg.licensed(team, PlantEra.I1)).isFalse();
    }

    @Test
    void grantIsPerTeam() {
        LicenceRegistry reg = new LicenceRegistry();
        reg.grant(A, PlantEra.I1, 10);
        assertThat(reg.licensed(A, PlantEra.I1)).isTrue();
        assertThat(reg.licensed(B, PlantEra.I1)).isFalse();
        assertThat(reg.licences(A)).containsExactly(PlantEra.I1);
    }

    @Test
    void firstOwnerWins() {
        LicenceRegistry reg = new LicenceRegistry();
        SimId u = new SimId(42);
        reg.owner(u, A);
        reg.owner(u, B);
        assertThat(reg.owner(u)).contains(A);
        reg.forget(u);
        assertThat(reg.owner(u)).isEmpty();
    }

    @Test
    void saveLoadRoundTrip() {
        LicenceRegistry reg = new LicenceRegistry();
        reg.grant(A, PlantEra.I1, 5);
        reg.taskDone(A, "promptcritical:i0/first_steam");
        reg.accepted(A, UnitKind.FIRE_TUBE_BOILER);
        reg.owner(new SimId(7), A);
        MutableStateTree tree = new MutableStateTree();
        reg.save(new TreeStateWriter(tree));
        LicenceRegistry back = new LicenceRegistry();
        back.load(new TreeStateReader(tree));
        assertThat(back.licences(A)).containsExactly(PlantEra.I1);
        assertThat(back.tasksDone(A)).containsExactly("promptcritical:i0/first_steam");
        assertThat(back.acceptedKinds(A)).containsExactly(UnitKind.FIRE_TUBE_BOILER);
        assertThat(back.owner(new SimId(7))).contains(A);
    }

    @Test
    void loadWithoutStateIsEmpty() {
        LicenceRegistry back = new LicenceRegistry();
        back.load(new TreeStateReader(new MutableStateTree()));
        assertThat(back.licences(A)).isEmpty();
        assertThat(back.owner(new SimId(7))).isEmpty();
    }

    @Test
    void previousEra() {
        assertThat(PlantEra.I0.previous()).isNull();
        assertThat(PlantEra.I3.previous()).isEqualTo(PlantEra.I2);
        assertThat(PlantEra.of("I4")).isEqualTo(PlantEra.I4);
        assertThatThrownBy(() -> PlantEra.of("I9")).isInstanceOf(IllegalArgumentException.class);
        for (UnitKind k : new UnitKind[] {UnitKind.FIRE_TUBE_BOILER, UnitKind.STEAM_ENGINE, UnitKind.MAIN_MINE_FAN,
                UnitKind.MINE_DRAINAGE_PUMP, UnitKind.MINE_HOIST}) assertThat(k.era()).isEqualTo(PlantEra.I0);
    }
}
