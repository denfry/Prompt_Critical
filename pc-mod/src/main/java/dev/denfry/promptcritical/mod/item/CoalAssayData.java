package dev.denfry.promptcritical.mod.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import dev.denfry.promptcritical.sim.mining.VeinField;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Компонент {@code coal_assay} (08 §8.2.1) в квантованном виде (02 §2.0.3): технический анализ с шагом 1 % (сера —
 * 0,1 %), элементный состав и пластический слой выводятся из него так же, как у пласта ({@link CoalAssay#typical}).
 * Два куска угля с одинаковым квантованным анализом стекуются.
 *
 * @param volatilePct V^daf, %
 * @param ashPct      A^d, %
 * @param moisturePct W^r, %
 * @param sulfurPm    S_t^d, ‰
 */
public record CoalAssayData(int volatilePct, int ashPct, int moisturePct, int sulfurPm) {
    public static final Codec<CoalAssayData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("volatile_pct").forGetter(CoalAssayData::volatilePct),
            Codec.INT.fieldOf("ash_pct").forGetter(CoalAssayData::ashPct),
            Codec.INT.fieldOf("moisture_pct").forGetter(CoalAssayData::moisturePct),
            Codec.INT.fieldOf("sulfur_pm").forGetter(CoalAssayData::sulfurPm)).apply(i, CoalAssayData::new));
    public static final StreamCodec<ByteBuf, CoalAssayData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CoalAssayData::volatilePct, ByteBufCodecs.VAR_INT, CoalAssayData::ashPct,
            ByteBufCodecs.VAR_INT, CoalAssayData::moisturePct, ByteBufCodecs.VAR_INT, CoalAssayData::sulfurPm,
            CoalAssayData::new);

    public static CoalAssayData of(CoalAssay a) {
        return new CoalAssayData((int) Math.round(a.volatileDaf() * 100), (int) Math.round(a.ashDryFrac() * 100),
                (int) Math.round(a.moistureFrac() * 100), (int) Math.round(a.sulfurDryFrac() * 1000));
    }

    public CoalAssay toAssay() {
        double v = volatilePct / 100.0;
        return CoalAssay.typical(v, ashPct / 100.0, moisturePct / 100.0, sulfurPm / 1000.0, VeinField.plasticLayerMm(v));
    }
}
