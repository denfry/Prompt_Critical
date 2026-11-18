package dev.denfry.promptcritical.mod.acoustics;

/**
 * Насос, который может кавитировать (04a §4a.3): к его шуму добавляется слой «гравия» L_W + 15κ − 6 дБ, к вибрации —
 * широкополосный фон и удары в огибающей ВЧ ({@link MachineAcoustics}).
 */
public interface CavitationSource {
    /** Индекс интенсивности кавитации κ, 0–1 (04a §4a.3.3). */
    double cavitationIntensity();
}
