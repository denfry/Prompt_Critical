package dev.denfry.promptcritical.mod.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.sim.mining.OreAssay;
import dev.denfry.promptcritical.sim.mining.OreGrade;
import dev.denfry.promptcritical.sim.mining.VeinCatalog;
import dev.denfry.promptcritical.sim.mining.VeinType;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Компонент {@code ore_assay} (08 §8.17) в квантованном виде (02 §2.0.3): класс содержания G0–G5 и попутный
 * компонент с шагом четверть порядка; содержание и сера восстанавливаются серединой класса закона руды.
 *
 * @param veinId       жила-источник
 * @param mineral      ID руды
 * @param gradeClass   класс G0–G5
 * @param byproduct    попутный компонент или пустая строка
 * @param byproductQ   round(4·lg ppm) попутного
 */
public record OreAssayData(String veinId, String mineral, int gradeClass, String byproduct, int byproductQ) {
    public static final Codec<OreAssayData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("vein").forGetter(OreAssayData::veinId),
            Codec.STRING.fieldOf("mineral").forGetter(OreAssayData::mineral),
            Codec.INT.fieldOf("grade_class").forGetter(OreAssayData::gradeClass),
            Codec.STRING.optionalFieldOf("byproduct", "").forGetter(OreAssayData::byproduct),
            Codec.INT.optionalFieldOf("byproduct_q", 0).forGetter(OreAssayData::byproductQ)).apply(i, OreAssayData::new));
    public static final StreamCodec<ByteBuf, OreAssayData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OreAssayData::veinId, ByteBufCodecs.STRING_UTF8, OreAssayData::mineral,
            ByteBufCodecs.VAR_INT, OreAssayData::gradeClass, ByteBufCodecs.STRING_UTF8, OreAssayData::byproduct,
            ByteBufCodecs.VAR_INT, OreAssayData::byproductQ, OreAssayData::new);

    public static OreAssayData of(OreAssay a) {
        String by = "";
        int q = 0;
        for (Map.Entry<String, Double> e : a.byproductsPpm().entrySet()) {
            if (e.getValue() <= 0) continue;
            by = e.getKey();
            q = (int) Math.round(4 * Math.log10(e.getValue()));
        }
        return new OreAssayData(a.veinId(), a.mineral(), a.gradeClass(), by, q);
    }

    /** Закон содержания руды в жиле или {@code null}, если жилы/руды нет в каталоге. */
    @Nullable
    public static OreGrade grade(String veinId, String mineral) {
        VeinType t = VeinCatalog.byId(veinId);
        if (t == null) return null;
        for (OreGrade g : t.roles()) if (g != null && g.mineral().equals(mineral)) return g;
        return null;
    }

    public OreAssay toAssay() {
        OreGrade g = grade(veinId, mineral);
        double frac = g == null ? 0 : g.classCenter(gradeClass);
        double sulfur = g == null ? 0 : Math.min(1, frac * g.sulfurPerGrade());
        Map<String, Double> by = byproduct.isEmpty() ? Map.of() : Map.of(byproduct, Math.pow(10, byproductQ / 4.0));
        double purity = g != null && "SiO2".equals(g.component()) ? frac : Double.NaN;
        return new OreAssay(veinId, mineral, (byte) Math.max(0, Math.min(OreGrade.CLASSES - 1, gradeClass)), frac, by,
                sulfur, null, purity);
    }
}
