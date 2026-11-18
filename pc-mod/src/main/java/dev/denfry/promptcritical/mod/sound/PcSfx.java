package dev.denfry.promptcritical.mod.sound;

import dev.denfry.promptcritical.mod.PromptCritical;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Звуки машин: {@code sfx.<key>} → {@code sounds/sfx/<key>.ogg}. Звук собирает art/sfx/generate.py — синтезом ffmpeg
 * по рецепту или из записи CC0 по art/sfx/sources.json (он же дописывает sounds.json); ключи здесь и там должны
 * совпадать. Петли работы машин играет {@link MachineLoopSound}.
 */
public enum PcSfx {
    /** Двухтональная сирена АГЗ «Метан»: 660/880 Гц попеременно по 0,25 с, цикл 2 с. */
    AGZ_SIREN("agz_siren", "Gas alarm siren wails", "Воет сирена газовой защиты"),
    // синтез ffmpeg: гул сети 50 Гц (петли 2 с)
    ELECTRIC_MOTOR("electric_motor", "Electric motor hums", "Гудит электродвигатель"),
    TRANSFORMER_HUM("transformer_hum", "Transformer hums", "Гудит трансформатор"),
    // записи CC0 (art/sfx/sources.json, SOURCES.md): петли работы и разовые звуки
    STEAM_ENGINE_CHUFF("steam_engine_chuff", "Steam engine exhausts", "Выхлоп паровой машины"),
    BOILER_FIRE("boiler_fire", "Firebox roars", "Гудит топка"),
    CUPOLA_BLAST("cupola_blast", "Cupola blast roars", "Ревёт дутьё вагранки"),
    MINE_FAN("mine_fan", "Main fan drones", "Гудит главный вентилятор"),
    PUMP("pump", "Pump runs", "Работает насос"),
    SHEARER_CUTTING("shearer_cutting", "Shearer cuts coal", "Комбайн режет уголь"),
    HOIST_WINCH("hoist_winch", "Hoist winds", "Работает подъёмная машина"),
    HOIST_BRAKE("hoist_brake", "Hoist brake releases air", "Тормоз подъёмной машины"),
    LATHE("lathe", "Lathe turns", "Работает токарный станок"),
    MILLING("milling", "Milling cutter cuts", "Режет фреза"),
    SHAPER_STROKE("shaper_stroke", "Shaper tool scrapes", "Строгает резец"),
    GRINDER("grinder", "Grinding wheel screams", "Визжит шлифовальный круг"),
    METAL_POUR("metal_pour", "Molten metal hisses", "Шипит жидкий металл"),
    SHAKEOUT_RATTLE("shakeout_rattle", "Shakeout grate rattles", "Трясётся выбивная решётка"),
    MOLDING_JOLT("molding_jolt", "Moulding table jolts", "Стучит стол формовочной машины"),
    BREAKER_OPERATE("breaker_operate", "Breaker operates", "Срабатывает выключатель"),
    RELAY_CLICK("relay_click", "Relay clicks", "Щёлкает реле"),
    /** Щелчок газоразрядного счётчика дозиметра: синтез ffmpeg, разряд 30 мс. */
    GEIGER_CLICK("geiger_click", "Dosimeter clicks", "Щёлкает дозиметр"),
    ROOF_SUPPORT("roof_support", "Hydraulic support sets", "Шипит гидравлика крепи");

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, PromptCritical.MOD_ID);

    public final String key, en, ru;
    private DeferredHolder<SoundEvent, SoundEvent> holder;

    PcSfx(String key, String en, String ru) {
        this.key = key;
        this.en = en;
        this.ru = ru;
    }

    static {
        for (PcSfx s : values()) {
            String id = "sfx." + s.key;
            s.holder = SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(PromptCritical.MOD_ID, id)));
        }
    }

    public SoundEvent get() {
        return holder.get();
    }

    public String subtitleKey() {
        return "subtitles.promptcritical.sfx." + key;
    }
}
