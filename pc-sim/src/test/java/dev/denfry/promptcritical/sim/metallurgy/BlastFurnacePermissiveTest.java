package dev.denfry.promptcritical.sim.metallurgy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.mining.CoalAssay;
import org.junit.jupiter.api.Test;

/** Разрешение на процесс (план HP2.1): непринятая домна не задувается, но работающую оно не гасит. */
class BlastFurnacePermissiveTest {
    private static final double DT = 72, HOT_BLAST_K = 1373.15;
    private static final CoalAssay COKE = CokeBattery.cokeAssay(0.11, 0.006, 85);

    private static BlastFurnace furnaceWithCokeBlank() {
        BlastFurnace bf = new BlastFurnace(BlastFurnaceSpec.of(54, 8), IronmakingConfig.defaults());
        for (int k = 0; k < 6; k++) bf.charge(BurdenLayer.cokeBlank(2000, COKE));
        return bf;
    }

    private static void run(BlastFurnace bf, double seconds) {
        BlastConditions b = BlastConditions.air(bf.spec().ratedBlastNm3S(), HOT_BLAST_K);
        for (int k = 0; k < seconds / DT; k++) bf.stepSlow(DT, b);
    }

    @Test
    void notPermittedStaysCold() {
        BlastFurnace bf = furnaceWithCokeBlank();
        bf.setPermitted(false);
        run(bf, 3600);
        assertThat(bf.state()).isEqualTo(BlastFurnaceState.COLD);
        bf.setPermitted(true);
        run(bf, DT);
        assertThat(bf.state()).isEqualTo(BlastFurnaceState.BLOWING_IN);
    }

    @Test
    void permissiveDoesNotPutOutAFurnaceThatIsBlowing() {
        BlastFurnace bf = furnaceWithCokeBlank();
        run(bf, DT);
        assertThat(bf.state()).isEqualTo(BlastFurnaceState.BLOWING_IN);
        bf.setPermitted(false);
        run(bf, 3600);
        assertThat(bf.state()).isNotEqualTo(BlastFurnaceState.COLD);
    }
}
