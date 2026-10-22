package dev.denfry.promptcritical.mod.content.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.content.PonderLayouts;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Сцены Ponder мода (11 §11.1.4): ключ сцены — предмет (по нему Ponder открывает сцены, кнопка в справочнике и
 * маршруте передаёт ID предмета), схема — {@code assets/promptcritical/ponder/<сцена>.nbt}.
 */
public final class PcPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return PromptCritical.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(PcBlocks.COKE_OVEN.getId()).addStoryBoard(PonderLayouts.COKE, PcPonderScenes::cokeBattery);
        helper.forComponents(PcBlocks.BLAST_FURNACE_TAPHOLE.getId(), PcBlocks.BLAST_FURNACE_CONTROLLER.getId())
                .addStoryBoard(PonderLayouts.BLAST_FURNACE, PcPonderScenes::blastFurnaceTap);
        helper.forComponents(PcBlocks.INSTRUMENT_SYNCHROSCOPE.getId(), PcBlocks.SHAFT_GENERATOR.getId())
                .addStoryBoard(PonderLayouts.SYNC, PcPonderScenes::synchronization);
        helper.forComponents(PcBlocks.GRID_CONSUMER.getId(), PcItems.RELAY_UFLS.getId())
                .addStoryBoard(PonderLayouts.FREQUENCY, PcPonderScenes::gridFrequency);
        helper.forComponents(PcBlocks.RELAY.getId(), PcBlocks.RELAY_POWER_SUPPLY.getId())
                .addStoryBoard(PonderLayouts.RELAY, PcPonderScenes::relayLadder);
    }
}
