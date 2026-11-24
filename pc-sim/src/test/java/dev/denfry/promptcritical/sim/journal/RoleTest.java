package dev.denfry.promptcritical.sim.journal;

import static dev.denfry.promptcritical.sim.journal.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Матрица прав 04d §4d.9.2 и роли станции §4d.9.1. */
class RoleTest {
    /** Матрица построчно, как в таблице §4d.9.2: разрешение → роли с «✓». */
    private static final Map<Permission, Set<Role>> MATRIX = Map.ofEntries(
            Map.entry(READ, EnumSet.allOf(Role.class)),
            Map.entry(CONTROL, EnumSet.of(Role.OWNER, Role.SHIFT_SUPERVISOR, Role.OPERATOR)),
            Map.entry(ACK_ALARM, EnumSet.of(Role.OWNER, Role.SHIFT_SUPERVISOR, Role.OPERATOR, Role.ENGINEER,
                    Role.CHEMIST, Role.HEALTH_PHYSICIST)),
            Map.entry(PROTECTION, EnumSet.of(Role.OWNER, Role.SHIFT_SUPERVISOR)),
            Map.entry(TESTS_AND_SETUP, EnumSet.of(Role.OWNER, Role.SHIFT_SUPERVISOR, Role.ENGINEER)),
            Map.entry(CHEMISTRY, EnumSet.of(Role.OWNER, Role.SHIFT_SUPERVISOR, Role.CHEMIST)),
            Map.entry(RADIATION_LIMITS, EnumSet.of(Role.OWNER, Role.HEALTH_PHYSICIST)),
            Map.entry(MAINTENANCE, EnumSet.of(Role.OWNER, Role.MAINTENANCE)),
            Map.entry(BREAK_PLACE, EnumSet.of(Role.OWNER, Role.SHIFT_SUPERVISOR, Role.ENGINEER, Role.MAINTENANCE)),
            Map.entry(MANAGE_ROLES, EnumSet.of(Role.OWNER, Role.SHIFT_SUPERVISOR)),
            Map.entry(SPOT_TRADE, EnumSet.of(Role.OWNER, Role.SHIFT_SUPERVISOR)),
            Map.entry(CONTRACTS, EnumSet.of(Role.OWNER)));

    @Test
    void matrixMatchesGddTable() {
        assertThat(MATRIX.keySet()).containsExactlyInAnyOrder(Permission.values());
        assertThat(Role.values()).hasSize(8);
        for (Permission p : Permission.values())
            for (Role r : Role.values())
                assertThat(r.has(p)).as("%s has %s", r, p).isEqualTo(MATRIX.get(p).contains(r));
    }

    @Test
    void ownerHoldsEverythingSoNoRoleIsMandatoryAlone() {
        assertThat(Role.OWNER.permissions()).containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void permissionsOfSeveralRolesAreTheUnion() {
        assertThat(Role.permissionsOf(List.of(Role.OPERATOR, Role.MAINTENANCE)))
                .containsExactlyInAnyOrder(READ, CONTROL, ACK_ALARM, MAINTENANCE, BREAK_PLACE);
        assertThat(Role.permissionsOf(List.of())).isEmpty();
    }

    @Test
    void shiftSupervisorAssignsEverythingButOwner() {
        assertThat(Role.canAssign(List.of(Role.SHIFT_SUPERVISOR), Role.OPERATOR)).isTrue();
        assertThat(Role.canAssign(List.of(Role.SHIFT_SUPERVISOR), Role.SHIFT_SUPERVISOR)).isTrue();
        assertThat(Role.canAssign(List.of(Role.SHIFT_SUPERVISOR), Role.OWNER)).isFalse();
        assertThat(Role.canAssign(List.of(Role.OWNER), Role.OWNER)).isTrue();
        assertThat(Role.canAssign(List.of(Role.OPERATOR, Role.ENGINEER), Role.VISITOR)).isFalse();
    }

    @Test
    void stationOwnerAndOwnerTeamAreOwners() {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), eve = UUID.randomUUID();
        UUID team = StationRecord.teamId("reds");
        StationRecord byTeam = StationRecord.create(UUID.randomUUID(), "NPP-1", team, 0, 42L, 100);
        assertThat(byTeam.rolesOf(alice, team)).containsExactly(Role.OWNER);
        assertThat(byTeam.rolesOf(eve, StationRecord.teamId("blues"))).isEmpty();
        assertThat(byTeam.isMember(eve, null)).isFalse();

        StationRecord byPlayer = StationRecord.create(UUID.randomUUID(), "NPP-2", alice, 0, 42L, 100)
                .withRole(bob, Role.OPERATOR).withRole(bob, Role.CHEMIST);
        assertThat(byPlayer.has(alice, null, CONTRACTS)).isTrue();
        assertThat(byPlayer.rolesOf(bob, null)).containsExactlyInAnyOrder(Role.OPERATOR, Role.CHEMIST);
        assertThat(byPlayer.has(bob, null, CONTROL)).isTrue();
        assertThat(byPlayer.has(bob, null, BREAK_PLACE)).isFalse();

        StationRecord revoked = byPlayer.withoutRole(bob, Role.OPERATOR);
        assertThat(revoked.rolesOf(bob, null)).containsExactly(Role.CHEMIST);
        assertThat(revoked.withoutRole(bob, null).members()).doesNotContainKey(bob);
        // Исходная запись не изменилась.
        assertThat(byPlayer.rolesOf(bob, null)).hasSize(2);
    }

    @Test
    void teamIdIsStable() {
        assertThat(StationRecord.teamId("reds")).isEqualTo(StationRecord.teamId("reds"))
                .isNotEqualTo(StationRecord.teamId("blues"));
    }
}
