package dev.denfry.promptcritical.mod.data;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.content.SimEventTrigger;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
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
 * Достижения эпохи I0 (11 §11.2.2): ванильные, условия — через {@code promptcritical:sim_event}, награда — только
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
