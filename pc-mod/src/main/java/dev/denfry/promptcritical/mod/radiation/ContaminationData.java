package dev.denfry.promptcritical.mod.radiation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.radiation.SurfaceContamination;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Компонент {@code promptcritical:surface_contamination}: поверхностное загрязнение предмета, Бк/см², на условной поверхности
 * {@link dev.denfry.promptcritical.sim.radiation.ContaminationTransfer#ITEM_CM2} см² каждого предмета стопки. Пишется
 * касанием грязными руками, разбором загрязнённого блока, содранной плёнкой; снимается дезактивацией.
 */
public record ContaminationData(double alphaLoose, double alphaFixed, double betaLoose, double betaFixed) {
    public static final Codec<ContaminationData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("a").forGetter(ContaminationData::alphaLoose),
            Codec.DOUBLE.fieldOf("af").forGetter(ContaminationData::alphaFixed),
            Codec.DOUBLE.fieldOf("b").forGetter(ContaminationData::betaLoose),
            Codec.DOUBLE.fieldOf("bf").forGetter(ContaminationData::betaFixed)).apply(i, ContaminationData::new));
    public static final StreamCodec<ByteBuf, ContaminationData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, ContaminationData::alphaLoose, ByteBufCodecs.DOUBLE, ContaminationData::alphaFixed,
            ByteBufCodecs.DOUBLE, ContaminationData::betaLoose, ByteBufCodecs.DOUBLE, ContaminationData::betaFixed, ContaminationData::new);

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ContaminationData>> TYPE =
            COMPONENTS.registerComponentType("surface_contamination", b -> b.persistent(CODEC).networkSynchronized(STREAM_CODEC));

    /** Заправка распылителя дезактивации: раствор и собранный смыв ({@link DeconSprayerItem}). */
    public record SprayerCharge(String fluid, double litres, double wasteL, double wasteBq) {
        public static final SprayerCharge EMPTY = new SprayerCharge("", 0, 0, 0);
        public static final Codec<SprayerCharge> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("fluid").forGetter(SprayerCharge::fluid),
                Codec.DOUBLE.fieldOf("l").forGetter(SprayerCharge::litres),
                Codec.DOUBLE.fieldOf("waste_l").forGetter(SprayerCharge::wasteL),
                Codec.DOUBLE.fieldOf("waste_bq").forGetter(SprayerCharge::wasteBq)).apply(i, SprayerCharge::new));
        public static final StreamCodec<ByteBuf, SprayerCharge> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SprayerCharge::fluid, ByteBufCodecs.DOUBLE, SprayerCharge::litres,
                ByteBufCodecs.DOUBLE, SprayerCharge::wasteL, ByteBufCodecs.DOUBLE, SprayerCharge::wasteBq, SprayerCharge::new);
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SprayerCharge>> SPRAYER =
            COMPONENTS.registerComponentType("decon_sprayer", b -> b.persistent(SprayerCharge.CODEC).networkSynchronized(SprayerCharge.STREAM_CODEC));

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }

    public static ContaminationData of(SurfaceContamination c) {
        return new ContaminationData(c.alphaLoose(), c.alphaFixed(), c.betaLoose(), c.betaFixed());
    }

    public SurfaceContamination surface() {
        return new SurfaceContamination(Math.max(0, alphaLoose), Math.max(0, alphaFixed), Math.max(0, betaLoose), Math.max(0, betaFixed));
    }

    /** Загрязнение предмета; чистый — {@link SurfaceContamination#NONE}. */
    public static SurfaceContamination get(ItemStack stack) {
        ContaminationData d = stack.isEmpty() ? null : stack.get(TYPE.get());
        return d == null ? SurfaceContamination.NONE : d.surface();
    }

    /** Записать загрязнение; пренебрежимое — снять компонент (чистый предмет снова складывается в стопку с чистыми). */
    public static void set(ItemStack stack, SurfaceContamination c) {
        if (stack.isEmpty()) return;
        if (c.negligible()) stack.remove(TYPE.get());
        else stack.set(TYPE.get(), of(c));
    }
}
