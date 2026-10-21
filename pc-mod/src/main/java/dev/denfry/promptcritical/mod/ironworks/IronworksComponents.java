package dev.denfry.promptcritical.mod.ironworks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.metallurgy.CokeBattery;
import dev.denfry.promptcritical.sim.metallurgy.ElectrodeState;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Компоненты предметов коксохима и домны (08 §8.17, 08 §8.20): {@code coke_assay} кокса, {@code sinter_assay}
 * агломерата, {@code electrode_state} электрода. Квантованы (02 §2.0.3), чтобы одинаковые куски стекались.
 */
public final class IronworksComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);

    /**
     * Анализ кокса: зола A^d и влага — с шагом 1 %, сера — 0,1 %, M₂₅; горячий — только что из камеры, до тушения.
     */
    public record CokeAssay(int ashPct, int sulfurPm, int m25, int moisturePct, boolean hot) {
        public static final CokeAssay DEFAULT = new CokeAssay(11, 6, 82, 4, false);
        public static final Codec<CokeAssay> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("ash_pct").forGetter(CokeAssay::ashPct),
                Codec.INT.fieldOf("sulfur_pm").forGetter(CokeAssay::sulfurPm),
                Codec.INT.fieldOf("m25").forGetter(CokeAssay::m25),
                Codec.INT.optionalFieldOf("moisture_pct", 0).forGetter(CokeAssay::moisturePct),
                Codec.BOOL.optionalFieldOf("hot", false).forGetter(CokeAssay::hot)).apply(i, CokeAssay::new));
        public static final StreamCodec<ByteBuf, CokeAssay> STREAM = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CokeAssay::ashPct, ByteBufCodecs.VAR_INT, CokeAssay::sulfurPm,
                ByteBufCodecs.VAR_INT, CokeAssay::m25, ByteBufCodecs.VAR_INT, CokeAssay::moisturePct,
                ByteBufCodecs.BOOL, CokeAssay::hot, CokeAssay::new);

        public static CokeAssay of(CoalAssay a, boolean hot) {
            double m25 = Double.isNaN(a.strengthM25()) ? 0 : a.strengthM25();
            return new CokeAssay((int) Math.round(a.ashDryFrac() * 100), (int) Math.round(a.sulfurDryFrac() * 1000),
                    (int) Math.round(m25), (int) Math.round(a.moistureFrac() * 100), hot);
        }

        public CoalAssay toAssay() {
            return CokeBattery.cokeAssay(ashPct / 100.0, sulfurPm / 1000.0, m25 > 0 ? m25 : Double.NaN, moisturePct / 100.0);
        }

        /** Кокс после мокрого тушения: холодный, влага 4 % (08 §8.4.3). */
        public CokeAssay quenched() {
            return new CokeAssay(ashPct, sulfurPm, m25, 4, false);
        }
    }

    /** Агломерат: Fe, SiO₂, CaO с шагом 1 %. */
    public record SinterAssay(int fePct, int sio2Pct, int caoPct) {
        public static final SinterAssay DEFAULT = new SinterAssay(56, 9, 12);
        public static final Codec<SinterAssay> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("fe_pct").forGetter(SinterAssay::fePct),
                Codec.INT.fieldOf("sio2_pct").forGetter(SinterAssay::sio2Pct),
                Codec.INT.fieldOf("cao_pct").forGetter(SinterAssay::caoPct)).apply(i, SinterAssay::new));
        public static final StreamCodec<ByteBuf, SinterAssay> STREAM = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SinterAssay::fePct, ByteBufCodecs.VAR_INT, SinterAssay::sio2Pct,
                ByteBufCodecs.VAR_INT, SinterAssay::caoPct, SinterAssay::new);

        public static SinterAssay of(double fe, double sio2, double cao) {
            return new SinterAssay((int) Math.round(fe * 100), (int) Math.round(sio2 * 100), (int) Math.round(cao * 100));
        }
    }

    /** Электрод: стадия ({@link ElectrodeState#GREEN}, {@code BAKED}), трещины, диаметр и длина в сантиметрах. */
    public record Electrode(int stage, boolean cracked, int diameterCm, int lengthCm) {
        public static final Codec<Electrode> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("stage").forGetter(Electrode::stage),
                Codec.BOOL.optionalFieldOf("cracked", false).forGetter(Electrode::cracked),
                Codec.INT.fieldOf("diameter_cm").forGetter(Electrode::diameterCm),
                Codec.INT.fieldOf("length_cm").forGetter(Electrode::lengthCm)).apply(i, Electrode::new));
        public static final StreamCodec<ByteBuf, Electrode> STREAM = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Electrode::stage, ByteBufCodecs.BOOL, Electrode::cracked,
                ByteBufCodecs.VAR_INT, Electrode::diameterCm, ByteBufCodecs.VAR_INT, Electrode::lengthCm, Electrode::new);

        public static Electrode of(ElectrodeState e) {
            return new Electrode(e.stage(), e.cracked(), (int) Math.round(e.diameterM() * 100), (int) Math.round(e.lengthM() * 100));
        }

        public ElectrodeState toState() {
            return new ElectrodeState(lengthCm / 100.0, diameterCm / 100.0, cracked, stage);
        }
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CokeAssay>> COKE_ASSAY =
            COMPONENTS.registerComponentType("coke_assay", b -> b.persistent(CokeAssay.CODEC).networkSynchronized(CokeAssay.STREAM));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SinterAssay>> SINTER_ASSAY =
            COMPONENTS.registerComponentType("sinter_assay", b -> b.persistent(SinterAssay.CODEC).networkSynchronized(SinterAssay.STREAM));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Electrode>> ELECTRODE_STATE =
            COMPONENTS.registerComponentType("electrode_state", b -> b.persistent(Electrode.CODEC).networkSynchronized(Electrode.STREAM));

    private IronworksComponents() {
    }
}
