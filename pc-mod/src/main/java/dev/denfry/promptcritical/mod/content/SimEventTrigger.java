package dev.denfry.promptcritical.mod.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Триггер достижений {@code promptcritical:sim_event} (11 §11.2.1): событие домена с предикатами по его величине.
 * {@code station_member: true} — засчитывается всем, кто рядом с источником ({@link SimAchievements#RADIUS_M} м:
 * станций 04d §4d.9 пока нет), иначе — только игроку, который сделал действие сам.
 */
public final class SimEventTrigger extends SimpleCriterionTrigger<SimEventTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /**
     * @param direct игрок — автор действия; {@code false} — член смены рядом с источником
     */
    public void trigger(ServerPlayer player, String event, String tag, double value, boolean direct) {
        trigger(player, i -> i.matches(event, tag, value, direct));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, ResourceLocation event,
            Map<String, MinMaxBounds.Doubles> predicate, boolean stationMember) implements SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ResourceLocation.CODEC.fieldOf("event").forGetter(TriggerInstance::event),
                Codec.unboundedMap(Codec.STRING, MinMaxBounds.Doubles.CODEC).optionalFieldOf("predicate", Map.of())
                        .forGetter(TriggerInstance::predicate),
                Codec.BOOL.optionalFieldOf("station_member", false).forGetter(TriggerInstance::stationMember))
                .apply(i, TriggerInstance::new));

        public static Criterion<TriggerInstance> of(ResourceLocation event, Map<String, MinMaxBounds.Doubles> predicate,
                boolean stationMember) {
            return PcTriggers.SIM_EVENT.get().createCriterion(new TriggerInstance(Optional.empty(), event, predicate, stationMember));
        }

        boolean matches(String e, String tag, double value, boolean direct) {
            if (!event.toString().equals(e) || !(direct || stationMember)) return false;
            for (Map.Entry<String, MinMaxBounds.Doubles> p : predicate.entrySet()) {
                if (!p.getKey().equals(tag) || !p.getValue().matches(value)) return false;
            }
            return true;
        }
    }
}
