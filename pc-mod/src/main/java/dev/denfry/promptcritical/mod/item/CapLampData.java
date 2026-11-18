package dev.denfry.promptcritical.mod.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.sim.battery.CapLamp;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Компонент {@code cap_lamp} головного светильника: состояние батареи (отданный заряд, эквивалентные циклы,
 * температура), целость стекла и лампы, игровой тик последнего пересчёта (саморазряд лежащей лампы считается при
 * следующем касании) и поток фары для подсказки и света вокруг игрока.
 *
 * @param flags      бит 0 — стекло разбито, бит 1 — лампа перегорела
 * @param fluxPermil поток последнего шага, ‰ номинального
 */
public record CapLampData(double removedAh, double cycles, double tempC, int flags, long lastTick, int fluxPermil) {
    /** Новая лампа: заряжена, на складе при 20 °C. */
    public static final CapLampData FRESH = new CapLampData(0, 0, 20, 0, -1, 0);
    public static final int GLASS_BROKEN = 1, BULB_BURNT = 2;

    public static final Codec<CapLampData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("removed_ah").forGetter(CapLampData::removedAh),
            Codec.DOUBLE.fieldOf("cycles").forGetter(CapLampData::cycles),
            Codec.DOUBLE.fieldOf("temp_c").forGetter(CapLampData::tempC),
            Codec.INT.fieldOf("flags").forGetter(CapLampData::flags),
            Codec.LONG.fieldOf("last_tick").forGetter(CapLampData::lastTick),
            Codec.INT.fieldOf("flux_permil").forGetter(CapLampData::fluxPermil)).apply(i, CapLampData::new));
    public static final StreamCodec<ByteBuf, CapLampData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, CapLampData::removedAh, ByteBufCodecs.DOUBLE, CapLampData::cycles,
            ByteBufCodecs.DOUBLE, CapLampData::tempC, ByteBufCodecs.VAR_INT, CapLampData::flags,
            ByteBufCodecs.VAR_LONG, CapLampData::lastTick, ByteBufCodecs.VAR_INT, CapLampData::fluxPermil, CapLampData::new);

    public boolean glassBroken() {
        return (flags & GLASS_BROKEN) != 0;
    }

    /** Модель светильника в этом состоянии. */
    public CapLamp toLamp() {
        CapLamp l = CapLamp.standard();
        l.restore(removedAh, cycles, tempC, (flags & GLASS_BROKEN) != 0, (flags & BULB_BURNT) != 0);
        return l;
    }

    /** Снять состояние со светильника после шага. */
    public static CapLampData of(CapLamp l, long tick) {
        int f = (l.glassBroken() ? GLASS_BROKEN : 0) | (l.bulbBurnt() ? BULB_BURNT : 0);
        return new CapLampData(l.battery().removedAh(), l.battery().cycles(), l.battery().temperatureC(), f, tick,
                (int) Math.round(1000 * l.relativeFlux()));
    }
}
