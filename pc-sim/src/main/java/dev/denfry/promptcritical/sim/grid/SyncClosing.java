package dev.denfry.promptcritical.sim.grid;

/**
 * Включение машины на параллельную работу (07 §7.3.5) — полезная нагрузка {@code pc_sync_closed}.
 *
 * @param torquePu      пиковый момент на валу, о.е. на S_ном машины
 * @param shaftDamage   прирост усталостного повреждения валопровода ΔD_shaft
 * @param createShockW  скачок нагрузки на сеть Create T_pk·T_ном·ω, Вт-эквивалент (мод делит на
 *                      W_PER_SU); 0 — машина не на валу Create или срезной штифт спас вал
 */
public record SyncClosing(int machine, int branch, SyncScope scope, double torquePu, double shaftDamage,
        SyncResult result, double createShockW) {}
