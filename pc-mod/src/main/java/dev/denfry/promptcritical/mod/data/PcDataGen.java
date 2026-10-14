package dev.denfry.promptcritical.mod.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Генераторы данных (05 §5.3.7); запуск — {@code ./gradlew :pc-mod:runData}, вывод в {@code src/generated/resources}. */
public final class PcDataGen {
    private PcDataGen() {
    }

    public static void gather(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();
        PackOutput out = gen.getPackOutput();
        ExistingFileHelper efh = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        gen.addProvider(event.includeClient(), new PcBlockStateProvider(out, efh));
        gen.addProvider(event.includeClient(), new PcLanguageProvider(out, "en_us"));
        gen.addProvider(event.includeClient(), new PcLanguageProvider(out, "ru_ru"));

        gen.addProvider(event.includeServer(), new LootTableProvider(out, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(PcBlockLoot::new, LootContextParamSets.BLOCK)), lookup));
        PcTagProviders.BlockTagProvider blockTags = gen.addProvider(event.includeServer(),
                new PcTagProviders.BlockTagProvider(out, lookup, efh));
        gen.addProvider(event.includeServer(), new PcTagProviders.ItemTagProvider(out, lookup, blockTags.contentsGetter(), efh));
    }
}
