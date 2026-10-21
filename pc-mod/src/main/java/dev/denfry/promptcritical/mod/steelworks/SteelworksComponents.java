package dev.denfry.promptcritical.mod.steelworks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.metallurgy.AirCooling;
import dev.denfry.promptcritical.sim.metallurgy.DefectKind;
import dev.denfry.promptcritical.sim.metallurgy.Ferroalloy;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.PipeDefect;
import dev.denfry.promptcritical.sim.metallurgy.PipeKind;
import dev.denfry.promptcritical.sim.metallurgy.PipeSpec;
import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import dev.denfry.promptcritical.sim.metallurgy.StockShape;
import dev.denfry.promptcritical.sim.metallurgy.StockState;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Компоненты предметов стали (08 §8.17–8.18): {@code steel_stock} — марка ({@code steel_grade}), сечение и
 * унаследованные дефекты проката, у горячего — T₀ и тик выдачи (температура считается по {@link AirCooling});
 * {@code pipe_spec} труб; вид {@code ferroalloy}. Химия квантована маркой, сечение — миллиметрами, чтобы холодный
 * прокат одной плавки стекался.
 */
public final class SteelworksComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);
    /** Ниже этой температуры металл считается остывшим: горячие поля снимаются, К (60 °C). */
    public static final double COLD_K = 333.15, AMBIENT_K = 293.15;

    /**
     * Прокат и слиток.
     *
     * @param family      семейство (ordinal {@link SteelFamily})
     * @param chemPct     химия марки, %
     * @param state       термообработка (ordinal {@link HeatTreatState})
     * @param thicknessMm толщина (у трубы — стенка), мм
     * @param widthMm     ширина (у трубы — наружный диаметр), мм
     * @param porous      осевая пористость нераскисленной стали или плены холодной разливки
     * @param cracked     трещины: прокатка ниже Ar₃, пережог
     * @param heatNo      номер плавки
     * @param hotK        температура при выдаче, К; 0 — холодный
     * @param hotTick     игровое время выдачи, тики
     */
    public record SteelStock(int family, Map<String, Float> chemPct, int state, int thicknessMm, int widthMm, boolean porous,
            boolean cracked, long heatNo, int hotK, long hotTick) {
        public static final Codec<SteelStock> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("family").forGetter(SteelStock::family),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("chem").forGetter(SteelStock::chemPct),
                Codec.INT.fieldOf("state").forGetter(SteelStock::state),
                Codec.INT.fieldOf("thickness_mm").forGetter(SteelStock::thicknessMm),
                Codec.INT.fieldOf("width_mm").forGetter(SteelStock::widthMm),
                Codec.BOOL.optionalFieldOf("porous", false).forGetter(SteelStock::porous),
                Codec.BOOL.optionalFieldOf("cracked", false).forGetter(SteelStock::cracked),
                Codec.LONG.optionalFieldOf("heat", 0L).forGetter(SteelStock::heatNo),
                Codec.INT.optionalFieldOf("hot_k", 0).forGetter(SteelStock::hotK),
                Codec.LONG.optionalFieldOf("hot_tick", 0L).forGetter(SteelStock::hotTick)).apply(i, SteelStock::new));

        public SteelStock {
            chemPct = Map.copyOf(chemPct);
        }

        /** Из марки и заготовки; {@code tempK} выше {@link #COLD_K} — горячий с тика {@code now}. */
        public static SteelStock of(SteelGrade g, double thicknessM, double widthM, boolean porous, boolean cracked, long heatNo,
                double tempK, long now) {
            boolean hot = tempK > COLD_K;
            return new SteelStock(g.family().ordinal(), new LinkedHashMap<>(g.chemPct()), g.state().ordinal(),
                    Math.max(1, (int) Math.round(thicknessM * 1000)), Math.max(1, (int) Math.round(widthM * 1000)), porous, cracked,
                    heatNo, hot ? (int) Math.round(tempK) : 0, hot ? now : 0);
        }

        public SteelGrade grade() {
            SteelFamily f = SteelFamily.values()[Math.floorMod(family, SteelFamily.values().length)];
            HeatTreatState s = HeatTreatState.values()[Math.floorMod(state, HeatTreatState.values().length)];
            return SteelGrade.of(f, f.designation, chemPct, s);
        }

        public double thicknessM() {
            return thicknessMm / 1000.0;
        }

        public double widthM() {
            return widthMm / 1000.0;
        }

        /** Температура сейчас, К: остывание на воздухе от выдачи. */
        public double tempK(long now) {
            if (hotK <= 0) return AMBIENT_K;
            return AirCooling.tempAfter(hotK, AMBIENT_K, thicknessM(), Math.max(0, now - hotTick) * 0.05);
        }

        public boolean hot() {
            return hotK > 0;
        }

        /** Тот же металл при температуре {@code tempK} с тика {@code now} (ниже {@link #COLD_K} — холодный). */
        public SteelStock withTemp(double tempK, long now) {
            boolean h = tempK > COLD_K;
            return new SteelStock(family, chemPct, state, thicknessMm, widthMm, porous, cracked, heatNo,
                    h ? (int) Math.round(tempK) : 0, h ? now : 0);
        }

        /** Другая марка (термообработка). */
        public SteelStock withGrade(SteelGrade g) {
            return new SteelStock(g.family().ordinal(), new LinkedHashMap<>(g.chemPct()), g.state().ordinal(), thicknessMm, widthMm,
                    porous, cracked, heatNo, hotK, hotTick);
        }

        /** Заготовка для доменных расчётов: масса {@code kg}, длина — из сечения. */
        public StockState stock(StockShape shape, double kg, long now) {
            double t = thicknessM(), w = widthM();
            return new StockState(shape, t, w, kg / (StockState.DENSITY * t * w), tempK(now), grade(), cracked, porous);
        }

        /** Компонент после доменного пропуска или печи. */
        public static SteelStock of(StockState s, long heatNo, long now) {
            return of(s.grade(), s.thicknessM(), s.widthM(), s.porous(), s.cracked(), heatNo, s.tempK(), now);
        }
    }

    /** Труба: {@link PipeSpec} целиком (09 §9.15 поля первыми). */
    public record PipeData(String grade, double odM, double wallM, double smysPa, double testPressurePa, boolean seamless,
            boolean sourService, double utsPa, int kind, int inspectedMask, int defectKind, double defectDepth, double defectLengthM,
            long heatNo) {
        public static final Codec<PipeData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("grade").forGetter(PipeData::grade),
                Codec.DOUBLE.fieldOf("od_m").forGetter(PipeData::odM),
                Codec.DOUBLE.fieldOf("wall_m").forGetter(PipeData::wallM),
                Codec.DOUBLE.fieldOf("smys_pa").forGetter(PipeData::smysPa),
                Codec.DOUBLE.fieldOf("test_pa").forGetter(PipeData::testPressurePa),
                Codec.BOOL.fieldOf("seamless").forGetter(PipeData::seamless),
                Codec.BOOL.optionalFieldOf("sour", false).forGetter(PipeData::sourService),
                Codec.DOUBLE.fieldOf("uts_pa").forGetter(PipeData::utsPa),
                Codec.INT.fieldOf("kind").forGetter(PipeData::kind),
                Codec.INT.optionalFieldOf("inspected", 0).forGetter(PipeData::inspectedMask),
                Codec.INT.optionalFieldOf("defect", 0).forGetter(PipeData::defectKind),
                Codec.DOUBLE.optionalFieldOf("defect_depth", 0.0).forGetter(PipeData::defectDepth),
                Codec.DOUBLE.optionalFieldOf("defect_length_m", 0.0).forGetter(PipeData::defectLengthM),
                Codec.LONG.optionalFieldOf("heat", 0L).forGetter(PipeData::heatNo)).apply(i, PipeData::new));

        public static PipeData of(PipeSpec p) {
            return new PipeData(p.grade(), p.odM(), p.wallM(), p.smysPa(), p.testPressurePa(), p.seamless(), p.sourService(),
                    p.utsPa(), p.kind().ordinal(), p.inspectedMask(), p.defect().kind().ordinal(), p.defect().depthFrac(),
                    p.defect().lengthM(), p.heatNo());
        }

        public PipeSpec spec() {
            DefectKind dk = DefectKind.values()[Math.floorMod(defectKind, DefectKind.values().length)];
            PipeDefect d = dk == DefectKind.NONE ? PipeDefect.NONE : new PipeDefect(dk, defectDepth, defectLengthM);
            return new PipeSpec(grade, odM, wallM, smysPa, testPressurePa, seamless, sourService, false, 0,
                    PipeKind.values()[Math.floorMod(kind, PipeKind.values().length)], Math.max(utsPa, smysPa), inspectedMask, d, heatNo);
        }

        /** Масса метра трубы, кг. */
        public double kgPerM() {
            return kgPerM(odM, wallM);
        }

        public static double kgPerM(double odM, double wallM) {
            return Math.PI * (odM - wallM) * wallM * StockState.DENSITY;
        }
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SteelStock>> STEEL_STOCK =
            COMPONENTS.registerComponentType("steel_stock", b -> b.persistent(SteelStock.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(SteelStock.CODEC)));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PipeData>> PIPE_SPEC =
            COMPONENTS.registerComponentType("pipe_spec", b -> b.persistent(PipeData.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(PipeData.CODEC)));
    /** Вид ферросплава — {@link Ferroalloy#id}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> FERROALLOY =
            COMPONENTS.registerComponentType("ferroalloy", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private SteelworksComponents() {
    }

    /** Ферросплав по id; {@code null} — неизвестный. */
    public static Ferroalloy alloy(String id) {
        for (Ferroalloy a : Ferroalloy.values()) if (a.id.equals(id)) return a;
        return null;
    }
}
