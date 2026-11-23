package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.sim.metallurgy.CyclicMachine;
import org.jetbrains.annotations.Nullable;

/**
 * Эксгаустер коксового газа (08 §8.4.4) у газосборника батареи: старый {@code gas_exhauster} на своём валу и
 * {@code gas_exhauster_real} с цельной моделью и валом через порт. Батарея делит газ по скорости их валов.
 */
public interface GasExhauster {
    /** Машина симуляции на валу; {@code null} — на клиенте или пока не привязана. */
    @Nullable
    CyclicMachine machine();

    /** Бак откачанного газа, откуда его забирают трубы Create. */
    GasBuffer outlet();
}
