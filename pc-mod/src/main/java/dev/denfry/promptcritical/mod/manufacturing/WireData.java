package dev.denfry.promptcritical.mod.manufacturing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.sim.manufacturing.Wire;

/**
 * Компонент {@code wire} (18 §18.6.7): металл, диаметр и длина в миллиметрах и метрах, наклёп ε. Металл — по имени,
 * неизвестный читается как медь, размеры ограничены снизу, чтобы предмет из другой версии не ронял разбор.
 */
public record WireData(String metal, float diameterMm, float lengthM, float strain) {
    public static final Codec<WireData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("metal").forGetter(WireData::metal),
            Codec.FLOAT.fieldOf("d_mm").forGetter(WireData::diameterMm),
            Codec.FLOAT.fieldOf("length_m").forGetter(WireData::lengthM),
            Codec.FLOAT.optionalFieldOf("strain", 0f).forGetter(WireData::strain)).apply(i, WireData::new));

    public static WireData of(Wire w) {
        return new WireData(w.metal().name(), (float) (w.diameterM() * 1000), (float) w.lengthM(), (float) w.strain());
    }

    public Wire wire() {
        Wire.Metal m;
        try {
            m = Wire.Metal.valueOf(metal);
        } catch (IllegalArgumentException e) {
            m = Wire.Metal.COPPER;
        }
        double d = Float.isFinite(diameterMm) && diameterMm > 0.01f ? diameterMm / 1000.0 : 1e-5;
        double l = Float.isFinite(lengthM) && lengthM > 0.001f ? lengthM : 0.001;
        double e = Float.isFinite(strain) && strain > 0 ? strain : 0;
        return new Wire(m, d, l, e);
    }
}
