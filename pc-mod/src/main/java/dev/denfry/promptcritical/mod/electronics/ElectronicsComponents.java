package dev.denfry.promptcritical.mod.electronics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Компоненты электроники (11 §11.5): {@code silicon_ingot} — слиток Чохральского, {@code chip_spec} — техпроцесс и
 * параметры кристалла; условие рецептов {@code promptcritical:cc_recipe_override} для подмены рецептов CC: Tweaked.
 */
public final class ElectronicsComponents {
    /** Масса предмета: технический кремний (кусок), поликремний (стержень), кг. */
    public static final double MG_SI_KG_PER_ITEM = 4, POLY_KG_PER_ITEM = 1;
    /** Кварц и кокс на предмет: 4 кварца по 2,5 кг SiO₂ и 2 кокса по 2 кг на кусок кремния 4 кг. */
    public static final int QUARTZ_PER_ITEM = 4, COKE_PER_ITEM = 2;
    /** Диаметр слитка и пластины, мм. */
    public static final double INGOT_MM = 50;

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, PromptCritical.MOD_ID);

    /**
     * Слиток: масса, монокристалл ли, удельное сопротивление головы и хвоста, Ом·см, доля слитка в допуске по
     * сопротивлению (остальное — в оборот).
     */
    public record Ingot(double kg, boolean monocrystal, double rhoHead, double rhoTail, double usable) {
        public static final Codec<Ingot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("kg").forGetter(Ingot::kg),
                Codec.BOOL.fieldOf("mono").forGetter(Ingot::monocrystal),
                Codec.DOUBLE.fieldOf("rho_head").forGetter(Ingot::rhoHead),
                Codec.DOUBLE.fieldOf("rho_tail").forGetter(Ingot::rhoTail),
                Codec.DOUBLE.fieldOf("usable").forGetter(Ingot::usable)).apply(i, Ingot::new));
        public static final StreamCodec<ByteBuf, Ingot> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.DOUBLE, Ingot::kg,
                ByteBufCodecs.BOOL, Ingot::monocrystal, ByteBufCodecs.DOUBLE, Ingot::rhoHead, ByteBufCodecs.DOUBLE, Ingot::rhoTail,
                ByteBufCodecs.DOUBLE, Ingot::usable, Ingot::new);
    }

    /** Кристалл: изделие ({@code ChipDesign}), размер элемента, мкм, частота, МГц, транзисторов, площадь, мм². */
    public record Chip(String design, double cdUm, double clockMhz, int transistors, double dieMm2) {
        public static final Codec<Chip> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("design").forGetter(Chip::design),
                Codec.DOUBLE.fieldOf("cd_um").forGetter(Chip::cdUm),
                Codec.DOUBLE.fieldOf("clock_mhz").forGetter(Chip::clockMhz),
                Codec.INT.fieldOf("transistors").forGetter(Chip::transistors),
                Codec.DOUBLE.fieldOf("die_mm2").forGetter(Chip::dieMm2)).apply(i, Chip::new));
        public static final StreamCodec<ByteBuf, Chip> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Chip::design,
                ByteBufCodecs.DOUBLE, Chip::cdUm, ByteBufCodecs.DOUBLE, Chip::clockMhz, ByteBufCodecs.VAR_INT, Chip::transistors,
                ByteBufCodecs.DOUBLE, Chip::dieMm2, Chip::new);
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Ingot>> INGOT =
            COMPONENTS.registerComponentType("silicon_ingot", b -> b.persistent(Ingot.CODEC).networkSynchronized(Ingot.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Chip>> CHIP =
            COMPONENTS.registerComponentType("chip_spec", b -> b.persistent(Chip.CODEC).networkSynchronized(Chip.STREAM_CODEC));

    /**
     * Условие рецепта: ключ {@code content.cc_recipe_override}. Датапаки грузятся раньше серверного конфига мира при
     * первом входе — тогда действует значение по умолчанию (включено); после {@code /reload} — значение из конфига.
     */
    public record CcOverrideCondition() implements ICondition {
        public static final MapCodec<CcOverrideCondition> CODEC = MapCodec.unit(new CcOverrideCondition());

        @Override
        public boolean test(IContext context) {
            return !ServerConfig.SPEC.isLoaded() || ServerConfig.CONTENT_CC_RECIPE_OVERRIDE.get();
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<CcOverrideCondition>> CC_OVERRIDE =
            CONDITIONS.register("cc_recipe_override", () -> CcOverrideCondition.CODEC);

    private ElectronicsComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        CONDITIONS.register(modBus);
    }
}
