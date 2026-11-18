package dev.denfry.promptcritical.mod.reprocessing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Компонент {@code promptcritical:pu_lot}: плутоний на предмете (PuO₂ после PUREX) — масса Pu, кг, и изотопные доли.
 * Его читает будущая фабрикация МОКС-топлива; учёт плутония ведётся по этой массе.
 */
public record PuLot(double puKg, double pu239, double pu240, double pu241, double pu242) {
    public static final Codec<PuLot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("pu_kg").forGetter(PuLot::puKg),
            Codec.DOUBLE.fieldOf("pu239").forGetter(PuLot::pu239),
            Codec.DOUBLE.fieldOf("pu240").forGetter(PuLot::pu240),
            Codec.DOUBLE.fieldOf("pu241").forGetter(PuLot::pu241),
            Codec.DOUBLE.fieldOf("pu242").forGetter(PuLot::pu242)).apply(i, PuLot::new));
    public static final StreamCodec<ByteBuf, PuLot> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.DOUBLE, PuLot::puKg,
            ByteBufCodecs.DOUBLE, PuLot::pu239, ByteBufCodecs.DOUBLE, PuLot::pu240, ByteBufCodecs.DOUBLE, PuLot::pu241, ByteBufCodecs.DOUBLE,
            PuLot::pu242, PuLot::new);

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PuLot>> TYPE =
            COMPONENTS.registerComponentType("pu_lot", b -> b.persistent(CODEC).networkSynchronized(STREAM_CODEC));

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent e) -> {
            PuLot l = e.getItemStack().get(TYPE.get());
            if (l == null) return;
            e.getToolTip().add(Component.translatable("message.promptcritical.reprocessing.pu_lot", String.format(Locale.ROOT, "%.3f", l.puKg),
                    String.format(Locale.ROOT, "%.1f", l.pu239 * 100), String.format(Locale.ROOT, "%.1f", l.pu240 * 100))
                    .withStyle(ChatFormatting.GRAY));
        });
    }

    /** Смешать с другой партией по массе. */
    public PuLot plus(PuLot o) {
        double m = puKg + o.puKg;
        if (m <= 0) return this;
        return new PuLot(m, (pu239 * puKg + o.pu239 * o.puKg) / m, (pu240 * puKg + o.pu240 * o.puKg) / m,
                (pu241 * puKg + o.pu241 * o.puKg) / m, (pu242 * puKg + o.pu242 * o.puKg) / m);
    }
}
