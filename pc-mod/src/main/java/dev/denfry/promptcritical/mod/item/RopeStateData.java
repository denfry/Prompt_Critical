package dev.denfry.promptcritical.mod.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.sim.mining.RopeConstruction;
import dev.denfry.promptcritical.sim.mining.RopeState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Компонент {@code rope_state} предмета {@code wire_rope} (08 §8.17): конструкция каната. Износ живёт в подъёмной
 * машине; бухта на складе — новый канат.
 *
 * @param diameterMm   d, мм
 * @param gradeMpa     маркировочная группа R_r, МПа
 * @param construction {@link RopeConstruction}
 */
public record RopeStateData(int diameterMm, int gradeMpa, String construction) {
    /** Канат 6×19 ОС ⌀ 32 мм, 1770 МПа [BALANCE: людской подъём до ≈ 80 м с полной клетью]. */
    public static final RopeStateData DEFAULT = new RopeStateData(32, 1770, RopeConstruction.R6X19_FC.name());

    public static final Codec<RopeStateData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("diameter_mm").forGetter(RopeStateData::diameterMm),
            Codec.INT.fieldOf("grade_mpa").forGetter(RopeStateData::gradeMpa),
            Codec.STRING.fieldOf("construction").forGetter(RopeStateData::construction)).apply(i, RopeStateData::new));
    public static final StreamCodec<ByteBuf, RopeStateData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RopeStateData::diameterMm, ByteBufCodecs.VAR_INT, RopeStateData::gradeMpa,
            ByteBufCodecs.STRING_UTF8, RopeStateData::construction, RopeStateData::new);

    public RopeConstruction constructionOrDefault() {
        try {
            return RopeConstruction.valueOf(construction);
        } catch (IllegalArgumentException e) {
            return RopeConstruction.R6X19_FC;
        }
    }

    /** Новый канат этой конструкции. */
    public RopeState fresh() {
        return RopeState.fresh(diameterMm, gradeMpa, constructionOrDefault());
    }
}
