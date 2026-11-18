package dev.denfry.promptcritical.mod.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Компонент {@code electric_gear} носимого снаряжения §15.8: батарея (отданный заряд, эквивалентные циклы,
 * температура), поглощённое фильтром (у респиратора), мощность последнего шага для подсказки и тик последнего
 * пересчёта.
 *
 * @param filterMol поглощено фильтром, моль
 * @param powerW10  мощность нагрузки на последнем шаге, десятые Вт; 0 — выключено или батарея села
 */
public record ElectricGearData(double removedAh, double cycles, double tempC, double filterMol, int powerW10, long lastTick) {
    public static final ElectricGearData FRESH = new ElectricGearData(0, 0, 20, 0, 0, -1);

    public static final Codec<ElectricGearData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("removed_ah").forGetter(ElectricGearData::removedAh),
            Codec.DOUBLE.fieldOf("cycles").forGetter(ElectricGearData::cycles),
            Codec.DOUBLE.fieldOf("temp_c").forGetter(ElectricGearData::tempC),
            Codec.DOUBLE.fieldOf("filter_mol").forGetter(ElectricGearData::filterMol),
            Codec.INT.fieldOf("power_w10").forGetter(ElectricGearData::powerW10),
            Codec.LONG.fieldOf("last_tick").forGetter(ElectricGearData::lastTick)).apply(i, ElectricGearData::new));
    public static final StreamCodec<ByteBuf, ElectricGearData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, ElectricGearData::removedAh, ByteBufCodecs.DOUBLE, ElectricGearData::cycles,
            ByteBufCodecs.DOUBLE, ElectricGearData::tempC, ByteBufCodecs.DOUBLE, ElectricGearData::filterMol,
            ByteBufCodecs.VAR_INT, ElectricGearData::powerW10, ByteBufCodecs.VAR_LONG, ElectricGearData::lastTick,
            ElectricGearData::new);

    public ElectricGearData withFilter(double mol) {
        return new ElectricGearData(removedAh, cycles, tempC, mol, powerW10, lastTick);
    }
}
