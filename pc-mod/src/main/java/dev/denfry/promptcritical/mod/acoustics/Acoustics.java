package dev.denfry.promptcritical.mod.acoustics;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Привязка акустики (04a): вибродиагностика машин Create, их износ и шум у игроков, виброметр, вибродатчик,
 * обслуживание и противошумные наушники.
 */
public final class Acoustics {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VibrationTransmitterBlockEntity>> VIBRATION_TRANSMITTER =
            BLOCK_ENTITIES.register("instrument_vibration", () -> BlockEntityType.Builder.of(VibrationTransmitterBlockEntity::new,
                    PcBlocks.INSTRUMENT_VIBRATION.get()).build(null));

    private Acoustics() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(Acoustics::payloads);
        NoiseService.register();
        // последним из слушателей: отменённая ломка сюда не доходит, и машина сохраняет свой подшипник
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (BlockEvent.BreakEvent e) -> {
            if (e.getLevel() instanceof ServerLevel s && s.getBlockEntity(e.getPos()) instanceof KineticBlockEntity)
                MachineAcoustics.forget(s, e.getPos());
        });
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PromptCritical.MOD_ID).versioned("1").playToClient(VibrationReading.TYPE, VibrationReading.STREAM_CODEC,
                (payload, ctx) -> {
                    if (FMLEnvironment.dist.isClient())
                        ctx.enqueueWork(() -> dev.denfry.promptcritical.mod.acoustics.client.VibrometerScreen.open(payload));
                });
    }

    /** Тексты: сообщения, экран прибора, подсказки предметов. */
    public static Map<String, String[]> lang() {
        Map<String, String[]> m = new LinkedHashMap<>(NoiseService.lang());
        put(m, "message.promptcritical.machine_service.needs_running", "Start the machine: balancing is done on the run",
                "Запустите машину: балансировка — на ходу");
        put(m, "message.promptcritical.machine_service.needs_stopped", "Stop the shaft first", "Сначала остановите вал");
        put(m, "message.promptcritical.machine_service.done.bearing_kit", "Bearing replaced", "Подшипник заменён");
        put(m, "message.promptcritical.machine_service.done.balancing_weights", "Rotor balanced: imbalance down fivefold",
                "Ротор отбалансирован: дисбаланс меньше впятеро");
        put(m, "message.promptcritical.machine_service.done.alignment_tool", "Shafts aligned to 0.05 mm",
                "Валы отцентрованы до 0,05 мм");
        put(m, "item.promptcritical.vibrometer.hint", "Right-click a turning machine: spectrum, ISO zone, bearing temperature, noise",
                "ПКМ по вращающейся машине: спектр, зона ISO, температура подшипника, шум");
        put(m, "item.promptcritical.hearing_protection.hint", "Head slot: %s dB less noise at the ear",
                "Слот головы: шум у уха на %s дБ тише");
        put(m, "item.promptcritical.bearing_kit.hint", "Hold on a stopped machine for %s s: new rolling bearing",
                "Удерживайте на остановленной машине %s с: новый подшипник качения");
        put(m, "item.promptcritical.balancing_weights.hint", "Hold on a running machine for %s s: trial-weight balancing",
                "Удерживайте на работающей машине %s с: балансировка пробным грузом");
        put(m, "item.promptcritical.alignment_tool.hint", "Hold on a stopped machine for %s s: align the coupling",
                "Удерживайте на остановленной машине %s с: центровка муфты");
        put(m, "screen.promptcritical.vibrometer.title", "Vibration analyser", "Виброанализатор");
        put(m, "screen.promptcritical.vibrometer.shaft", "Shaft %s rpm (%s Hz) · %s kW · class %s",
                "Вал %s об/мин (%s Гц) · %s кВт · класс %s");
        put(m, "screen.promptcritical.vibrometer.stopped", "Shaft is not turning", "Вал не вращается");
        put(m, "screen.promptcritical.vibrometer.overall", "Overall %s mm/s RMS, %s–1000 Hz", "Общий уровень %s мм/с СКЗ, %s–1000 Гц");
        put(m, "screen.promptcritical.vibrometer.zone.a", "Zone A: as new", "Зона A: как новая");
        put(m, "screen.promptcritical.vibrometer.zone.b", "Zone B: fit for long-term operation", "Зона B: длительная эксплуатация");
        put(m, "screen.promptcritical.vibrometer.zone.c", "Zone C: limited operation, plan repairs",
                "Зона C: ограниченная эксплуатация, планируйте ремонт");
        put(m, "screen.promptcritical.vibrometer.zone.d", "Zone D: damaging vibration, stop the machine",
                "Зона D: вибрация разрушает машину, остановите");
        put(m, "screen.promptcritical.vibrometer.envelope", "HF envelope %s m/s² · bearing housing %s °C",
                "Огибающая ВЧ %s м/с² · корпус подшипника %s °C");
        put(m, "screen.promptcritical.vibrometer.noise", "Noise here %s dB(A)", "Шум здесь %s дБ(А)");
        put(m, "screen.promptcritical.vibrometer.quiet", "Noise here below 30 dB(A)", "Шум здесь ниже 30 дБ(А)");
        return m;
    }

    private static void put(Map<String, String[]> m, String key, String en, String ru) {
        m.put(key, new String[] {en, ru});
    }
}
