package dev.denfry.promptcritical.sim.acoustics;

/**
 * Звукоизоляция одного блока (1 м) на луче источник → слушатель, дБ (04a §4a.2.2). Закон массы TL ≈ 20·lg(m''·f) − 47
 * (Bies D. A., Hansen C. H., «Engineering Noise Control», 4th ed., §7.2) для тяжёлых стен даёт 70+ дБ, но обходные пути
 * через стыки ограничивают реальную изоляцию 55–65 дБ — поэтому табличные значения на блок и общий потолок
 * {@link SoundLevel#MAX_OCCLUSION_DB}.
 */
public enum BarrierMaterial {
    AIR(0), PLANT(2), PANE(4), GLASS(12), WOOD(15),
    /** Шерсть, ковры: лёгкий пористый материал, m'' ≈ 100 кг/м² — меньше дерева [BALANCE]. */
    SOFT(8),
    METAL(18), WATER(20), STONE(22), CONCRETE(25);

    public final double tlDb;

    BarrierMaterial(double tlDb) {
        this.tlDb = tlDb;
    }
}
