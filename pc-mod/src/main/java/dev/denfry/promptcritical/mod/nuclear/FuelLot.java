package dev.denfry.promptcritical.mod.nuclear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Партия ядерного топлива на предмете (компонент {@code fuel_lot}): уран в предмете, кг; доли U-235 и U-234; плотность
 * таблеток (доля теоретической, 0 — порошок); число таблеток, твэлов или 0; конструкция ({@link FuelDesign}, пусто —
 * порошок) и газ под оболочкой ({@code he}, {@code ar} или пусто). Им помечены порошок UO₂, поддоны таблеток, шлам,
 * твэлы и ТВС — так каждый грамм U-235 переходит из предмета в предмет вместе с продуктом.
 */
public record FuelLot(double uKg, double u235, double u234, double density, int count, String design, String gas) {
    public static final Codec<FuelLot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("u_kg").forGetter(FuelLot::uKg),
            Codec.DOUBLE.fieldOf("u235").forGetter(FuelLot::u235),
            Codec.DOUBLE.fieldOf("u234").forGetter(FuelLot::u234),
            Codec.DOUBLE.optionalFieldOf("density", 0.0).forGetter(FuelLot::density),
            Codec.INT.optionalFieldOf("count", 0).forGetter(FuelLot::count),
            Codec.STRING.optionalFieldOf("design", "").forGetter(FuelLot::design),
            Codec.STRING.optionalFieldOf("gas", "").forGetter(FuelLot::gas)).apply(i, FuelLot::new));
    public static final StreamCodec<ByteBuf, FuelLot> STREAM = new StreamCodec<>() {
        @Override
        public FuelLot decode(ByteBuf b) {
            return new FuelLot(b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), ByteBufCodecs.VAR_INT.decode(b),
                    ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.STRING_UTF8.decode(b));
        }

        @Override
        public void encode(ByteBuf b, FuelLot l) {
            b.writeDouble(l.uKg).writeDouble(l.u235).writeDouble(l.u234).writeDouble(l.density);
            ByteBufCodecs.VAR_INT.encode(b, l.count);
            ByteBufCodecs.STRING_UTF8.encode(b, l.design);
            ByteBufCodecs.STRING_UTF8.encode(b, l.gas);
        }
    };

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FuelLot>> TYPE =
            COMPONENTS.registerComponentType("fuel_lot", b -> b.persistent(CODEC).networkSynchronized(STREAM));

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(FuelLot::tooltip);
    }

    /** Порошок без партии — природный уран из конверсии жёлтого кека, 1 кг U на предмет. */
    public static FuelLot of(ItemStack s) {
        FuelLot l = s.get(TYPE.get());
        return l != null ? l : new FuelLot(1, Uf6Charge.NATURAL_U235, Uf6Charge.NATURAL_U234, 0, 0, "", "");
    }

    public static FuelLot powder(double uKg, double u235, double u234) {
        return new FuelLot(uKg, u235, u234, 0, 0, "", "");
    }

    public FuelDesign designOrNull() {
        for (FuelDesign d : FuelDesign.values()) if (d.name().equalsIgnoreCase(design)) return d;
        return null;
    }

    public double u235Kg() {
        return uKg * u235;
    }

    public ItemStack on(ItemStack s) {
        s.set(TYPE.get(), this);
        return s;
    }

    private static void tooltip(ItemTooltipEvent event) {
        FuelLot l = event.getItemStack().get(TYPE.get());
        if (l == null) return;
        List<Component> t = event.getToolTip();
        t.add(NuclearText.msg("lot.uranium", NuclearText.num(l.uKg, 3), NuclearText.num(l.u235 * 100, 3),
                NuclearText.num(l.u235Kg() * 1000, 1)).withStyle(ChatFormatting.GRAY));
        if (l.density > 0)
            t.add(NuclearText.msg("lot.density", NuclearText.num(l.density * 100, 1), l.count).withStyle(ChatFormatting.DARK_GRAY));
        FuelDesign d = l.designOrNull();
        if (d != null) t.add(NuclearText.msg("lot.design", NuclearText.design(d),
                l.gas.isEmpty() ? "" : NuclearText.msg("lot.gas." + l.gas)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
