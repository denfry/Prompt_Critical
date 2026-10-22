package dev.denfry.promptcritical.mod.data;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.content.SimEventTrigger;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.automation.AutomationSubsystem;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Достижения эпох I0–I1 и автоматизации A1–A2 (11 §11.2.2): ванильные, условия — через {@code promptcritical:sim_event}, награда — только
 * опыт (C2, T11.5). Остальные ветки каталога добавляются в своих вехах.
 */
public final class PcAdvancements implements AdvancementProvider.AdvancementGenerator {
    /** id → {название en, ru, описание en, ru}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        LANG.put("root", new String[] {"Prompt Critical", "Prompt Critical", "Open the Engineering Handbook: the plant starts with coal and steam",
                "Откройте справочник инженера: комплекс начинается с угля и пара"});
        LANG.put("first_steam", new String[] {"First Steam", "Первый пар", "A steam engine turns a Create shaft",
                "Паровая машина отдаёт момент на вал Create"});
        LANG.put("canary", new String[] {"Canary in a Cage", "Канарейка в клетке", "Hang a canary cage in a mine working",
                "Поставьте клетку с канарейкой в шахте"});
        LANG.put("canary_saved", new String[] {"The Bird Lives", "Птичка жива", "A canary that fell from its perch came round in fresh air",
                "Упавшая с жёрдочки канарейка очнулась на свежем воздухе — её вынесли вовремя"});
        LANG.put("coke", new String[] {"Coke and Gas", "Кокс и газ", "Push the first coke out of a coke oven battery",
                "Выдайте первую партию кокса из коксовой батареи"});
        LANG.put("pig_iron", new String[] {"Pig Iron", "Чугун", "Tap the first iron from a blast furnace",
                "Первый выпуск чугуна из домны"});
        LANG.put("steel", new String[] {"Steel", "Сталь", "Tap the first heat from a converter",
                "Первая плавка в конвертере"});
        LANG.put("seamless", new String[] {"No Seam", "Без шва", "Pierce the first seamless pipe",
                "Первая бесшовная труба"});
        LANG.put("synchronized", new String[] {"On the Grid", "В сеть",
                "Close a generator onto the grid with a torque shock of no more than 10 %",
                "Генератор включён в сеть без броска момента больше 10 %"});
        LANG.put("relay_logic", new String[] {"Click-Clack", "Щёлк-щёлк", "A circuit of at least five relays drives a mechanism",
                "Схема из пяти и более реле управляет механизмом"});
        LANG.put("analog_pid", new String[] {"No Computer Needed", "Без компьютера",
                "A PI controller built from analog blocks holds its setpoint for a whole day",
                "ПИ-регулятор на аналоговых блоках держит параметр целые сутки"});
    }

    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver, ExistingFileHelper efh) {
        AdvancementHolder root = Advancement.Builder.advancement()
                .display(PcItems.ENGINEERING_HANDBOOK.get(), title("root"), description("root"),
                        ResourceLocation.fromNamespaceAndPath(PromptCritical.MOD_ID, "textures/block/decor/painted_concrete_two_tone.png"),
                        AdvancementType.TASK, false, false, false)
                .addCriterion("handbook", InventoryChangeTrigger.TriggerInstance.hasItems(PcItems.ENGINEERING_HANDBOOK.get()))
                .save(saver, id("root"), efh);
        AdvancementHolder steam = task(root, PcBlocks.STEAM_ENGINE_CYLINDER.get(), "first_steam", AdvancementType.TASK, false, 50)
                .addCriterion("engine_running", SimEventTrigger.TriggerInstance.of(ResourceLocation.parse(SteamPlant.ENGINE_RUNNING),
                        Map.of("omega_pu", MinMaxBounds.Doubles.atLeast(0.2)), true))
                .save(saver, id("first_steam"), efh);
        AdvancementHolder canary = task(steam, PcBlocks.CANARY_CAGE.get(), "canary", AdvancementType.TASK, false, 20)
                .addCriterion("canary_placed", SimEventTrigger.TriggerInstance.of(ResourceLocation.parse(SimAchievements.CANARY_PLACED),
                        Map.of(), false))
                .save(saver, id("canary"), efh);
        task(canary, PcItems.CANARY.get(), "canary_saved", AdvancementType.TASK, true, 30)
                .addCriterion("canary_revived", SimEventTrigger.TriggerInstance.of(ResourceLocation.parse(SimAchievements.CANARY_REVIVED),
                        Map.of(), true))
                .save(saver, id("canary_saved"), efh);
        // I1: сталь и ток
        AdvancementHolder coke = task(steam, PcItems.COKE.get(), "coke", AdvancementType.TASK, false, 30)
                .addCriterion("coke_pushed", event(SimAchievements.COKE_PUSHED, Map.of()))
                .save(saver, id("coke"), efh);
        AdvancementHolder iron = task(coke, PcItems.PIG_IRON.get(), "pig_iron", AdvancementType.TASK, false, 40)
                .addCriterion("iron_tapped", event(SimAchievements.IRON_TAPPED, Map.of()))
                .save(saver, id("pig_iron"), efh);
        AdvancementHolder steel = task(iron, PcItems.STEEL_INGOT.get(), "steel", AdvancementType.GOAL, false, 60)
                .addCriterion("steel_tapped", event(SimAchievements.STEEL_TAPPED, Map.of()))
                .save(saver, id("steel"), efh);
        task(steel, PcItems.SEAMLESS_PIPE.get(), "seamless", AdvancementType.TASK, false, 40)
                .addCriterion("seamless_pipe", event(SimAchievements.SEAMLESS_PIPE, Map.of()))
                .save(saver, id("seamless"), efh);
        task(steam, PcBlocks.SHAFT_GENERATOR.get(), "synchronized", AdvancementType.GOAL, false, 50)
                .addCriterion("sync_closed", event(GridSubsystem.SYNC_CLOSED, Map.of("ok", MinMaxBounds.Doubles.atMost(0.10))))
                .save(saver, id("synchronized"), efh);
        // автоматизация A1–A2
        AdvancementHolder relays = task(root, PcBlocks.RELAY.get(), "relay_logic", AdvancementType.TASK, false, 30)
                .addCriterion("relay_logic", event(AutomationSubsystem.RELAY_LOGIC,
                        Map.of("relays", MinMaxBounds.Doubles.atLeast(AutomationSubsystem.RELAY_LOGIC_MIN))))
                .save(saver, id("relay_logic"), efh);
        task(relays, PcBlocks.MANUAL_STATION.get(), "analog_pid", AdvancementType.GOAL, false, 60)
                .addCriterion("analog_loop_held", event(AutomationSubsystem.ANALOG_LOOP_HELD, Map.of()))
                .save(saver, id("analog_pid"), efh);
    }

    /** Событие мира или домена у игрока в радиусе смены. */
    private static net.minecraft.advancements.Criterion<SimEventTrigger.TriggerInstance> event(String event,
            Map<String, MinMaxBounds.Doubles> predicate) {
        return SimEventTrigger.TriggerInstance.of(ResourceLocation.parse(event), predicate, true);
    }

    private static Advancement.Builder task(AdvancementHolder parent, ItemLike icon, String name, AdvancementType type,
            boolean hidden, int xp) {
        return Advancement.Builder.advancement().parent(parent)
                .display(icon, title(name), description(name), null, type, true, true, hidden)
                .rewards(AdvancementRewards.Builder.experience(xp));
    }

    private static Component title(String name) {
        return Component.translatable("advancements.promptcritical." + name + ".title");
    }

    private static Component description(String name) {
        return Component.translatable("advancements.promptcritical." + name + ".description");
    }

    private static ResourceLocation id(String name) {
        return PromptCritical.id(name);
    }
}
