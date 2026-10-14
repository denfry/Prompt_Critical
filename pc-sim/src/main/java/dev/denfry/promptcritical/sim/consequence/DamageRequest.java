package dev.denfry.promptcritical.sim.consequence;

import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimId;
import java.util.UUID;

/**
 * Запрос на разрушение (04d §4d.2.2). Без ссылок на MC.
 *
 * @param source          блок или объект-источник
 * @param responsibleTeam владелец станции-источника — для claims; {@code null}, если станции нет
 * @param energyJ         энергия, ушедшая в механическое разрушение (уже × f_blast), Дж
 * @param maxRadiusM      физический радиус (оценка) до ограничения бюджетом, м
 * @param priority        0 — наивысший (разрыв оболочки), 9 — косметика
 */
public record DamageRequest(long requestId, SimId source, UUID responsibleTeam, DamageKind kind, GridPos origin,
        double energyJ, double maxRadiusM, int priority) {
    public DamageRequest {
        if (!(energyJ >= 0) || !(maxRadiusM >= 0)) throw new IllegalArgumentException("energy and radius must be ≥ 0");
        if (priority < 0 || priority > 9) throw new IllegalArgumentException("priority out of [0, 9]: " + priority);
    }
}
