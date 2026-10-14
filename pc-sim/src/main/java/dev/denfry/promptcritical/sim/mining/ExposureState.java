package dev.denfry.promptcritical.sim.mining;

/** Состояние канарейки или отравленного игрока (08 §8.2.5). */
public enum ExposureState {
    CALM,
    /** B ≥ 0,3: беспокойство, звук */
    AGITATED,
    /** B ≥ 0,6: птица падает с жёрдочки; вынесенную на свежий воздух можно откачать */
    FALLEN,
    /** B = 1 */
    DEAD
}
