package dev.denfry.promptcritical.mod.environment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import io.netty.buffer.ByteBuf;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Компонент {@code promptcritical:activity}: радионуклиды предмета с активностями на игровое время {@code refTick},
 * масса и плотность — для самопоглощения γ. Распад считается при чтении ({@link #inventory(long)}) по игровому
 * времени: тик — {@value #GAME_S_PER_TICK} с. Ключи {@code bq} — имена {@link Emitter}; неизвестные пропускаются.
 * Предмет с этим компонентом — источник излучения для {@link RadiationService}: в инвентаре, на земле, в стеллаже.
 * Формат согласован с топливом T1 (fuel_lot пишет состав и выгорание, сюда — только активность).
 */
public record ActivityData(long refTick, double massKg, double densityGcc, Map<String, Double> bq) {
    public static final double GAME_S_PER_TICK = 3.6;
    public static final Codec<ActivityData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("ref_tick").forGetter(ActivityData::refTick),
            Codec.DOUBLE.fieldOf("mass_kg").forGetter(ActivityData::massKg),
            Codec.DOUBLE.fieldOf("density").forGetter(ActivityData::densityGcc),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("bq").forGetter(ActivityData::bq)).apply(i, ActivityData::new));
    public static final StreamCodec<ByteBuf, ActivityData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ActivityData::refTick, ByteBufCodecs.DOUBLE, ActivityData::massKg, ByteBufCodecs.DOUBLE,
            ActivityData::densityGcc, ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.DOUBLE), ActivityData::bq,
            ActivityData::new);

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ActivityData>> ACTIVITY =
            COMPONENTS.registerComponentType("activity", b -> b.persistent(CODEC).networkSynchronized(STREAM_CODEC));

    public ActivityData {
        bq = Map.copyOf(bq);
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }

    /** Компонент из инвентаря нуклидов на момент {@code tick}. */
    public static ActivityData of(ActivityInventory inv, long tick) {
        Map<String, Double> m = new HashMap<>();
        for (Map.Entry<Emitter, Double> e : inv.activities().entrySet()) m.put(e.getKey().name(), e.getValue());
        return new ActivityData(tick, inv.massKg(), inv.densityGcc(), m);
    }

    /** Инвентарь нуклидов с распадом до игрового времени {@code nowTick}. */
    public ActivityInventory inventory(long nowTick) {
        EnumMap<Emitter, Double> m = new EnumMap<>(Emitter.class);
        for (Map.Entry<String, Double> e : bq.entrySet()) {
            try {
                m.put(Emitter.valueOf(e.getKey()), e.getValue());
            } catch (IllegalArgumentException ignored) {
                // нуклид из более новой версии: пропускаем, а не роняем чтение предмета
            }
        }
        return new ActivityInventory(m, massKg, densityGcc).decayed(Math.max(0, nowTick - refTick) * GAME_S_PER_TICK);
    }
}
