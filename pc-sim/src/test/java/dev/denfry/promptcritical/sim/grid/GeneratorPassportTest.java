package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.manufacturing.assembly.MachinePassport;
import org.junit.jupiter.api.Test;

/** КПД генератора на валу и двигателя по паспорту сборки (18 §18.8.3). */
class GeneratorPassportTest {
    private static GeneratorUnit unit(double k) {
        GeneratorUnit u = new GeneratorUnit(SimId.of(SimId.Kind.DEVICE, 1));
        u.setPassportEfficiency(k);
        return u;
    }

    @Test
    void nominalPassportAddsNoLoss() {
        assertThat(unit(1).passportLoss(1, 1)).isZero();
    }

    @Test
    void poorCorePassesLessShaftPowerToTheRotor() {
        // k = 0.9: добавочные потери η_ном·(1 − k) от момента привода
        assertThat(unit(0.9).passportLoss(1, 1)).isCloseTo(MachinePassport.NOMINAL_WINDING_EFFICIENCY * 0.1, offset(1e-12));
    }

    @Test
    void betterThanNominalNeverCreatesPower() {
        double friction = GeneratorUnit.FRICTION_PU + GeneratorUnit.VISCOUS_PU;
        assertThat(unit(1.05).passportLoss(2, 1)).isEqualTo(-friction);
        assertThat(unit(1.05).passportLoss(0.01, 1)).isGreaterThan(-friction);
    }

    @Test
    void rejectsOutOfRangeFactor() {
        assertThatThrownBy(() -> unit(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> unit(1.2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void motorWithAPoorCoreTakesMoreFromTheGridForTheSameShaftLoad() {
        MotorUnit m = new MotorUnit(SimId.of(SimId.Kind.DEVICE, 2));
        assertThat(m.passportLossShare()).isZero();
        m.setPassportEfficiency(0.9);
        assertThat(m.passportLossShare()).isCloseTo(MachinePassport.NOMINAL_WINDING_EFFICIENCY * 0.1, offset(1e-12));
        m.setPassportEfficiency(1.05);
        assertThat(m.passportLossShare()).isZero();                 // лучше штатной модели не бывает
    }
}
