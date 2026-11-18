package dev.denfry.promptcritical.mod.drilling;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Компонент {@code seismic_section}: интерпретация съёмки — свод структуры, время и глубина, амплитуда, яркое пятно,
 * прогноз пластового давления ({@link dev.denfry.promptcritical.sim.drilling.SeismicSurvey.Reading}).
 *
 * @param crest     свод (Y — 0: глубина в {@code depthM})
 * @param twtMs     время двойного пробега до свода, мс
 * @param depthM    глубина свода после пересчёта, м
 * @param closureM  амплитуда замыкания, м
 * @param poreKgM3  прогноз эквивалентной плотности пластового давления, кг/м³
 * @param bright    яркое пятно над газом
 */
public record SeismicSectionData(BlockPos crest, int twtMs, int depthM, int closureM, int poreKgM3, boolean bright) {
    public static final Codec<SeismicSectionData> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("crest").forGetter(SeismicSectionData::crest),
            Codec.INT.fieldOf("twt_ms").forGetter(SeismicSectionData::twtMs),
            Codec.INT.fieldOf("depth_m").forGetter(SeismicSectionData::depthM),
            Codec.INT.fieldOf("closure_m").forGetter(SeismicSectionData::closureM),
            Codec.INT.fieldOf("pore_kg_m3").forGetter(SeismicSectionData::poreKgM3),
            Codec.BOOL.fieldOf("bright").forGetter(SeismicSectionData::bright)).apply(i, SeismicSectionData::new));
    public static final StreamCodec<ByteBuf, SeismicSectionData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
