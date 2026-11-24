package dev.denfry.promptcritical.sim.journal;

import static dev.denfry.promptcritical.sim.journal.Permission.*;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Роль на станции (04d §4d.9.1) со столбцом матрицы прав §4d.9.2. У игрока может быть несколько ролей; его права —
 * объединение. {@link #OWNER} содержит всё, поэтому в одиночной игре ни одна роль не обязательна (§4d.9.3).
 */
public enum Role {
    OWNER(EnumSet.allOf(Permission.class)),
    SHIFT_SUPERVISOR(EnumSet.of(READ, CONTROL, ACK_ALARM, PROTECTION, TESTS_AND_SETUP, CHEMISTRY, BREAK_PLACE,
            MANAGE_ROLES, SPOT_TRADE)),
    OPERATOR(EnumSet.of(READ, CONTROL, ACK_ALARM)),
    ENGINEER(EnumSet.of(READ, ACK_ALARM, TESTS_AND_SETUP, BREAK_PLACE)),
    CHEMIST(EnumSet.of(READ, ACK_ALARM, CHEMISTRY)),
    HEALTH_PHYSICIST(EnumSet.of(READ, ACK_ALARM, RADIATION_LIMITS)),
    MAINTENANCE(EnumSet.of(READ, Permission.MAINTENANCE, BREAK_PLACE)),
    VISITOR(EnumSet.of(READ));

    private final Set<Permission> permissions;

    Role(EnumSet<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean has(Permission p) {
        return permissions.contains(p);
    }

    /** Объединение прав набора ролей; пустой набор — никаких прав. */
    public static EnumSet<Permission> permissionsOf(Collection<Role> roles) {
        EnumSet<Permission> out = EnumSet.noneOf(Permission.class);
        for (Role r : roles) out.addAll(r.permissions);
        return out;
    }

    /**
     * Может ли носитель {@code granter} выдать или отозвать {@code role}: нужно {@link Permission#MANAGE_ROLES}, а роль
     * {@link #OWNER} выдаёт и отзывает только владелец («✓ кроме OWNER» у старшего смены, §4d.9.2).
     */
    public static boolean canAssign(Collection<Role> granter, Role role) {
        if (granter.contains(OWNER)) return true;
        return role != OWNER && permissionsOf(granter).contains(MANAGE_ROLES);
    }
}
