package dev.denfry.promptcritical.sim.mining;

/**
 * Выемка одним шнеком комбайна столбца блоков (08 §8.2.3): мощность шнека набирает энергию резания E, но не быстрее
 * предела подачи — столбец не короче {@link Longwall#minColumnTimeS}. Пока выемка идёт, контрапция стоит.
 */
public final class ShearerCut {
    private double needJ, doneJ, elapsedS, minTimeS;
    private boolean active;

    /** Начать столбец с энергией резания needJ и наименьшим временем minTimeS. */
    public void start(double needJ, double minTimeS) {
        this.needJ = Math.max(0, needJ);
        this.minTimeS = Math.max(0, minTimeS);
        doneJ = 0;
        elapsedS = 0;
        active = true;
    }

    public void clear() {
        active = false;
        needJ = doneJ = elapsedS = minTimeS = 0;
    }

    public boolean active() {
        return active;
    }

    /** Недобранная энергия резания, Дж. */
    public double remainingJ() {
        return active ? needJ - doneJ : 0;
    }

    /**
     * Мощность, которую шнек берёт сейчас, Вт: остаток энергии растягивается на оставшееся наименьшее время, иначе
     * берётся всё, что дают.
     */
    public double demandW(double powerW) {
        if (!active || doneJ >= needJ) return 0;
        double left = needJ - doneJ, tLeft = minTimeS - elapsedS;
        return Math.max(0, tLeft > 0 ? Math.min(powerW, left / tLeft) : powerW);
    }

    /** Шаг dt с мощностью шнека powerW; {@code true}, когда столбец вынут. */
    public boolean advance(double powerW, double dtS) {
        if (!active) return false;
        elapsedS += dtS;
        doneJ = Math.min(needJ, doneJ + Math.max(0, powerW) * dtS);
        return done();
    }

    public boolean done() {
        return active && doneJ >= needJ && elapsedS >= minTimeS;
    }

    /** Доля выемки 0–1 (трещины на блоках): меньшая из долей энергии и времени. */
    public double progress() {
        if (!active) return 0;
        double e = needJ <= 0 ? 1 : doneJ / needJ, t = minTimeS <= 0 ? 1 : elapsedS / minTimeS;
        return Math.min(1, Math.min(e, t));
    }
}
