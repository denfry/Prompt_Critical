package dev.denfry.promptcritical.mod.refinery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.refining.CatalystState;
import dev.denfry.promptcritical.sim.refining.GasolineBlending;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Компоненты предметов НПЗ (09 §9.9.11–9.9.13): {@code catalyst_state} на мешках катализатора, {@code sample_data}
 * на пробоотборниках и паспортах партии (качество {@code fuel_quality}: истинное — скрыто, измеренное — после
 * лаборатории).
 */
public final class RefineryComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);

    /** {@link CatalystState} на предмете: активности a_c, a_S, a_perm, кокс, яды, спекание, возраст, стадия. */
    public static final Codec<CatalystState> CATALYST_STATE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("a_coke").forGetter(CatalystState::activityCoke),
            Codec.DOUBLE.fieldOf("a_sulfur").forGetter(CatalystState::activitySulfur),
            Codec.DOUBLE.fieldOf("a_perm").forGetter(CatalystState::activityPerm),
            Codec.DOUBLE.fieldOf("coke").forGetter(CatalystState::cokeFrac),
            Codec.DOUBLE.listOf().fieldOf("poisons").forGetter(c -> List.of(c.poisonS(), c.poisonN(), c.poisonMetals(), c.sinterLoss())),
            Codec.DOUBLE.fieldOf("age_h").forGetter(CatalystState::ageH),
            Codec.STRING.fieldOf("state").forGetter(c -> c.state().name())).apply(i, (ac, as, ap, coke, p, age, st) ->
            new CatalystState(ac, as, ap, coke, p.size() > 0 ? p.get(0) : 0, p.size() > 1 ? p.get(1) : 0, p.size() > 2 ? p.get(2) : 0,
                    p.size() > 3 ? p.get(3) : 0, age, stage(st))));
    public static final StreamCodec<ByteBuf, CatalystState> CATALYST_STATE_STREAM = ByteBufCodecs.fromCodec(CATALYST_STATE_CODEC);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CatalystState>> CATALYST_STATE =
            COMPONENTS.registerComponentType("catalyst_state",
                    b -> b.persistent(CATALYST_STATE_CODEC).networkSynchronized(CATALYST_STATE_STREAM));

    /**
     * {@code fuel_quality} бензина (09 §9.9.12): ОЧИ, ОЧМ, ДНП (кПа), сера (ppm), бензол, ароматика, олефины (% об.),
     * плотность (кг/м³).
     */
    public record FuelQuality(double ron, double mon, double rvpKpa, double sulfurPpm, double benzenePct, double aromaticsPct,
            double olefinsPct, double density) {
        public static final Codec<FuelQuality> CODEC = Codec.DOUBLE.listOf().xmap(
                l -> new FuelQuality(at(l, 0), at(l, 1), at(l, 2), at(l, 3), at(l, 4), at(l, 5), at(l, 6), at(l, 7)),
                q -> List.of(q.ron, q.mon, q.rvpKpa, q.sulfurPpm, q.benzenePct, q.aromaticsPct, q.olefinsPct, q.density));

        private static double at(List<Double> l, int i) {
            return i < l.size() ? l.get(i) : 0;
        }

        public static FuelQuality of(GasolineBlending.Properties p) {
            return new FuelQuality(p.ron(), p.mon(), p.rvpKpa(), p.sulfurPpm(), p.benzenePct(), p.aromaticsPct(), p.olefinsPct(), p.density());
        }

        public static FuelQuality of(GasolineBlending.Component c) {
            return new FuelQuality(c.ron(), c.mon(), c.rvpKpa(), c.sulfurPpm(), c.benzenePct(), c.aromaticsPct(), c.olefinsPct(), c.density());
        }

        /** В спецификации марки (допуск — как у {@link GasolineBlending.Properties#onSpec}). */
        public boolean onSpec(GasolineBlending.Grade g) {
            return new GasolineBlending.Properties(1, ron, mon, rvpKpa, sulfurPpm, benzenePct, aromaticsPct, olefinsPct, density, 0).onSpec(g);
        }
    }

    /**
     * Проба ({@code sample_data}, 09 §9.9.13): жидкость, откуда и когда взята, истинное качество (игроку не видно), а
     * после анализа — измеренное с воспроизводимостью ASTM и время анализа ({@code certifiedAt}); {@code grade} — марка,
     * на которую проверяли партию станции смешения (пусто — не партия).
     */
    public record SampleData(String fluid, long source, long takenAt, FuelQuality truth, java.util.Optional<FuelQuality> measured,
            long certifiedAt, String grade) {
        public static final Codec<SampleData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("fluid").forGetter(SampleData::fluid),
                Codec.LONG.fieldOf("source").forGetter(SampleData::source),
                Codec.LONG.fieldOf("taken_at").forGetter(SampleData::takenAt),
                FuelQuality.CODEC.fieldOf("truth").forGetter(SampleData::truth),
                FuelQuality.CODEC.optionalFieldOf("measured").forGetter(SampleData::measured),
                Codec.LONG.fieldOf("certified_at").forGetter(SampleData::certifiedAt),
                Codec.STRING.fieldOf("grade").forGetter(SampleData::grade)).apply(i, SampleData::new));
        public static final StreamCodec<ByteBuf, SampleData> STREAM = ByteBufCodecs.fromCodec(CODEC);

        public boolean analysed() {
            return measured.isPresent();
        }

        public SampleData withResult(FuelQuality m, long at) {
            return new SampleData(fluid, source, takenAt, truth, java.util.Optional.of(m), at, grade);
        }
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SampleData>> SAMPLE_DATA =
            COMPONENTS.registerComponentType("sample_data", b -> b.persistent(SampleData.CODEC).networkSynchronized(SampleData.STREAM));

    private RefineryComponents() {
    }

    private static CatalystState.Stage stage(String s) {
        try {
            return CatalystState.Stage.valueOf(s);
        } catch (IllegalArgumentException e) {
            return CatalystState.Stage.ACTIVE;
        }
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
