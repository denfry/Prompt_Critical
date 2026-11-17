package dev.denfry.promptcritical.sim.body;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.body.BodyProfile.Sex;
import dev.denfry.promptcritical.sim.body.Physiology.Activity;
import dev.denfry.promptcritical.sim.mining.GasExposure;
import org.junit.jupiter.api.Test;

class PhysiologyTest {
    private static final BodyProfile REF = BodyProfile.REFERENCE;
    private static final BodyProfile SMALL = new BodyProfile(Sex.FEMALE, 160, 50, 25);
    private static final BodyProfile BIG = new BodyProfile(Sex.MALE, 185, 120, 40);

    /** Надлер: 175 см, 75 кг, муж. — 0,3669·1,75³ + 0,03219·75 + 0,6041 = 4,98 л; жен. 160 см, 50 кг — 3,30 л. */
    @Test
    void bloodVolumeFollowsNadler() {
        assertThat(Physiology.bloodVolumeL(REF)).isCloseTo(4.984, within(0.002));
        assertThat(Physiology.bloodVolumeL(SMALL)).isCloseTo(3.296, within(0.002));
    }

    /** Миффлин–Сан Жеор: 10·75 + 6,25·175 − 5·30 + 5 = 1698,75 ккал/сут = 82,3 Вт. */
    @Test
    void basalMetabolismFollowsMifflin() {
        assertThat(Physiology.basalMetabolicW(REF)).isCloseTo(1698.75 * 4184 / 86_400, within(1e-9));
        assertThat(Physiology.basalMetabolicW(SMALL)).isCloseTo(1214 * 4184.0 / 86_400, within(1e-9));
        assertThat(Physiology.bodyMassIndex(REF)).isCloseTo(24.49, within(0.01));
        assertThat(Physiology.bodySurfaceM2(REF)).isCloseTo(1.90, within(0.01));
    }

    /**
     * Опорный человек в покое — единица; бег — в 8/1,3 раза быстрее. Лёгкое тело (обмен на литр крови выше)
     * травится быстрее, тяжёлое — медленнее, но в пределах ±10 %: главное — что делает игрок, а не его вес.
     */
    @Test
    void gasClockScalesWithMetabolismPerBloodVolume() {
        assertThat(Physiology.gasClock(REF, Activity.REST)).isCloseTo(1, within(1e-12));
        assertThat(Physiology.gasClock(REF, Activity.SPRINT)).isCloseTo(8 / 1.3, within(1e-12));
        assertThat(Physiology.gasClock(SMALL, Activity.REST)).isCloseTo(1.080, within(0.003));
        assertThat(Physiology.gasClock(BIG, Activity.REST)).isCloseTo(0.933, within(0.003));
    }

    /** Бег в загазованной выработке: то же равновесие, но набирается быстрее — и отпускает на воздухе тоже быстрее. */
    @Test
    void sprintingReachesTheSameSaturationSooner() {
        GasExposure resting = GasExposure.human(20), running = GasExposure.human(20);
        double clock = Physiology.gasClock(REF, Activity.SPRINT);
        for (int s = 0; s < 120; s++) {
            resting.step(1, 0.0004, 0.209);
            running.step(clock, 0.0004, 0.209);
        }
        assertThat(running.saturation()).isGreaterThan(4 * resting.saturation());
        for (int s = 0; s < 20_000; s++) {
            resting.step(1, 0.0004, 0.209);
            running.step(clock, 0.0004, 0.209);
        }
        assertThat(running.saturation()).isCloseTo(resting.saturation(), within(1e-4));
    }

    @Test
    void bodyShapesTheModel() {
        assertThat(Physiology.modelScale(new BodyProfile(Sex.MALE, 180, 75, 30))).isCloseTo(1, within(1e-12));
        assertThat(1.8 * Physiology.modelScale(new BodyProfile(Sex.MALE, 200, 75, 30))).isLessThanOrEqualTo(2.0);
        assertThat(Physiology.strideSpeedFactor(new BodyProfile(Sex.MALE, 196, 75, 30)))
                .isCloseTo(Math.sqrt(196 / 175.0), within(1e-12));
        assertThat(Physiology.knockbackResistance(REF)).isZero();
        assertThat(Physiology.knockbackResistance(SMALL)).isZero();
        assertThat(Physiology.knockbackResistance(BIG)).isCloseTo(0.375, within(1e-12));
        assertThat(Physiology.loadedMassKg(REF)).isEqualTo(85);
    }

    @Test
    void untrustedInputIsClampedAndBadValuesRejected() {
        BodyProfile p = BodyProfile.clamped(null, 400, 1, 200);
        assertThat(p).isEqualTo(new BodyProfile(Sex.MALE, BodyProfile.MAX_HEIGHT_CM, BodyProfile.MIN_MASS_KG,
                BodyProfile.MAX_AGE));
        assertThatThrownBy(() -> new BodyProfile(Sex.FEMALE, 120, 60, 30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BodyProfile(null, 170, 60, 30)).isInstanceOf(IllegalArgumentException.class);
    }
}
