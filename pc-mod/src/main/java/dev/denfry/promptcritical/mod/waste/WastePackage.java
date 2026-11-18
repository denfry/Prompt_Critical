package dev.denfry.promptcritical.mod.waste;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.waste.ScalingFactors;
import dev.denfry.promptcritical.sim.waste.WasteForm;
import dev.denfry.promptcritical.sim.waste.WasteInventory;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Компонент {@code promptcritical:waste_package}: паспорт упаковки РАО — форма (матрица), происхождение (для
 * масштабирующих коэффициентов), соответствие компаунда нормам и объём. Активность — в {@link ActivityData}. Стеклоблок
 * PUREX паспорта не несёт: он боросиликатный, из рафината переработки.
 */
public record WastePackage(String form, String stream, boolean compliant, double volumeL) {
    public static final Codec<WastePackage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("form").forGetter(WastePackage::form),
            Codec.STRING.fieldOf("stream").forGetter(WastePackage::stream),
            Codec.BOOL.fieldOf("compliant").forGetter(WastePackage::compliant),
            Codec.DOUBLE.fieldOf("volume_l").forGetter(WastePackage::volumeL)).apply(i, WastePackage::new));
    public static final StreamCodec<ByteBuf, WastePackage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WastePackage::form, ByteBufCodecs.STRING_UTF8, WastePackage::stream, ByteBufCodecs.BOOL,
            WastePackage::compliant, ByteBufCodecs.DOUBLE, WastePackage::volumeL, WastePackage::new);

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WastePackage>> TYPE =
            COMPONENTS.registerComponentType("waste_package", b -> b.persistent(CODEC).networkSynchronized(STREAM_CODEC));

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }

    public WasteForm wasteForm() {
        try {
            return WasteForm.valueOf(form);
        } catch (IllegalArgumentException e) {
            return WasteForm.CEMENT;
        }
    }

    public ScalingFactors.Stream wasteStream() {
        try {
            return ScalingFactors.Stream.valueOf(stream);
        } catch (IllegalArgumentException e) {
            return ScalingFactors.Stream.REACTOR_OPERATIONAL;
        }
    }

    // ------------------------------------------------------------------ предметы-упаковки

    /** Упаковка, которую принимают пункты захоронения: бочки компаунда, прессованные отходы, стеклоблоки. */
    public static boolean isPackage(ItemStack s) {
        if (!s.has(ActivityData.ACTIVITY.get())) return false;
        return s.is(PcItems.CEMENTED_WASTE_DRUM.get()) || s.is(PcItems.BITUMEN_WASTE_DRUM.get()) || s.is(PcItems.COMPACTED_WASTE_DRUM.get())
                || s.is(PcItems.HLW_GLASS_CANISTER.get());
    }

    /** Паспорт предмета; у стеклоблока PUREX без паспорта — боросиликатное стекло из рафината. */
    @Nullable
    public static WastePackage of(ItemStack s) {
        WastePackage p = s.get(TYPE.get());
        if (p != null) return p;
        ActivityData a = s.get(ActivityData.ACTIVITY.get());
        if (s.is(PcItems.HLW_GLASS_CANISTER.get()) && a != null)
            return new WastePackage(WasteForm.BOROSILICATE_GLASS.name(), ScalingFactors.Stream.REPROCESSING_HLW.name(), true,
                    a.massKg() / WasteForm.BOROSILICATE_GLASS.densityKgL);
        return null;
    }

    /** Полный состав упаковки на тик {@code now}: γ-излучатели из компонента плюс трудноизмеряемые по коэффициентам. */
    public static WasteInventory inventory(ItemStack s, long now) {
        ActivityData a = s.get(ActivityData.ACTIVITY.get());
        if (a == null) return WasteInventory.EMPTY;
        WastePackage p = of(s);
        ScalingFactors.Stream stream = p != null ? p.wasteStream() : ScalingFactors.Stream.REACTOR_OPERATIONAL;
        double volM3 = p != null ? p.volumeL() / 1000 : a.massKg() / (a.densityGcc() * 1000);
        ActivityInventory g = a.inventory(now);
        return ScalingFactors.characterize(stream, g, volM3);
    }

    /** Новая упаковка с активностью на тик {@code now}. */
    public static ItemStack make(Item item, ActivityInventory gamma, long now, WasteForm form, ScalingFactors.Stream stream, boolean compliant,
            double volumeL) {
        ItemStack s = new ItemStack(item);
        s.set(ActivityData.ACTIVITY.get(), ActivityData.of(gamma, now));
        s.set(TYPE.get(), new WastePackage(form.name(), stream.name(), compliant, volumeL));
        return s;
    }
}
