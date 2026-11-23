package dev.denfry.promptcritical.mod;

import com.mojang.logging.LogUtils;
import dev.denfry.promptcritical.api.ApiHolder;
import dev.denfry.promptcritical.api.PromptCriticalApi;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.foundry.FoundryBinding;
import dev.denfry.promptcritical.mod.binding.SimConfigs;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcFeatures;
import dev.denfry.promptcritical.mod.registry.PcLootFunctions;
import dev.denfry.promptcritical.sim.mining.MineSubsystem;
import dev.denfry.promptcritical.sim.mining.DrainageConfig;
import dev.denfry.promptcritical.sim.mining.MiningConfig;
import dev.denfry.promptcritical.mod.client.PcClient;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.data.PcDataGen;
import dev.denfry.promptcritical.mod.manufacturing.ManufacturingBinding;
import dev.denfry.promptcritical.mod.registry.PcBlockEntities;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcCreativeTabs;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(PromptCritical.MOD_ID)
public final class PromptCritical {
    public static final String MOD_ID = "promptcritical";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PromptCritical(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        PcBlocks.BLOCKS.register(modBus);
        PcItems.ITEMS.register(modBus);
        PcBlockEntities.BLOCK_ENTITIES.register(modBus);
        PcDataComponents.COMPONENTS.register(modBus);
        PcLootFunctions.FUNCTIONS.register(modBus);
        PcFeatures.FEATURES.register(modBus);
        dev.denfry.promptcritical.mod.worldgen.WorldFinds.register();
        PcFluids.TYPES.register(modBus);
        PcFluids.FLUIDS.register(modBus);
        PcCreativeTabs.TABS.register(modBus);
        dev.denfry.promptcritical.mod.pa.PaSounds.SOUNDS.register(modBus);
        dev.denfry.promptcritical.mod.sound.PcSfx.SOUNDS.register(modBus);
        SimBindings.register(modBus);
        FoundryBinding.register(modBus);
        ManufacturingBinding.register(modBus);
        dev.denfry.promptcritical.mod.ironworks.IronworksBinding.register(modBus);
        dev.denfry.promptcritical.mod.reactor.ReactorBinding.register(modBus);
        dev.denfry.promptcritical.mod.powerhouse.PowerhouseBinding.register(modBus);
        dev.denfry.promptcritical.mod.steelworks.SteelworksBinding.register(modBus);
        dev.denfry.promptcritical.mod.ops.OpsBinding.register(modBus);
        dev.denfry.promptcritical.mod.safety.SafetyBinding.register(modBus);
        dev.denfry.promptcritical.mod.boiler.BoilerBinding.register(modBus);
        dev.denfry.promptcritical.mod.grid.GridBinding.register(modBus);
        dev.denfry.promptcritical.mod.automation.AutomationBinding.register(modBus);
        dev.denfry.promptcritical.mod.mine.MineBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.drilling.DrillingBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.production.ProductionBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.scada.ScadaBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.mes.MesBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.gas.GasBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.chem.ChemBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.heavywater.HeavyWaterBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.nonferrous.NonferrousBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.chlor.ChlorBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.uranium.UraniumBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.refinery.RefineryBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.hazard.HazardBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.nitrogen.NitrogenBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.methanol.MethanolBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.environment.ToxicReleaseService.register();
        dev.denfry.promptcritical.mod.environment.RadiationService.register();
        dev.denfry.promptcritical.mod.environment.ContainerActivityScan.register();
        modBus.addListener(dev.denfry.promptcritical.mod.environment.ContainerShielding::register);
        dev.denfry.promptcritical.mod.environment.AirQualityService.register();
        dev.denfry.promptcritical.mod.market.PlantAccounting.register();
        dev.denfry.promptcritical.mod.environment.EnvironmentBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.environment.DosimeterItem.register(modBus);
        dev.denfry.promptcritical.mod.acoustics.Acoustics.register(modBus);
        dev.denfry.promptcritical.mod.environment.ActivityData.register(modBus);
        dev.denfry.promptcritical.mod.radiation.RadiationBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.reprocessing.ReprocessingBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.waste.WasteBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.radiation.RadiationBlocksText.register();
        dev.denfry.promptcritical.mod.nuclear.Uf6Components.register(modBus);
        dev.denfry.promptcritical.mod.nuclear.NuclearBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.nuclear.FuelLot.register(modBus);
        dev.denfry.promptcritical.mod.npp.NppBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.isotopes.IsotopeBlockEntities.register(modBus);
        dev.denfry.promptcritical.mod.electronics.ElectronicsBlocks.register(modBus);
        dev.denfry.promptcritical.mod.market.MarketBlockEntities.register(modBus);
        SimBindings.registerSubsystem(level -> new MineSubsystem(
                SimConfigs.fromServerConfig().section(MiningConfig.class, MiningConfig.defaults()),
                SimConfigs.fromServerConfig().section(DrainageConfig.class, DrainageConfig.defaults())));
        dev.denfry.promptcritical.mod.mine.MineHoisting.register(modBus);
        dev.denfry.promptcritical.mod.panel.PanelLayouts.register(modBus);
        dev.denfry.promptcritical.mod.mine.MineLongwall.register(modBus);
        dev.denfry.promptcritical.mod.content.ContentRegistries.register(modBus);
        dev.denfry.promptcritical.mod.paperwork.Paperwork.register(modBus);
        dev.denfry.promptcritical.mod.environment.RiverService.register();
        dev.denfry.promptcritical.mod.body.BodyAttachments.register(modBus);
        modBus.addListener(PcDataGen::gather);
        dev.denfry.promptcritical.mod.compat.OpenCompatPack.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) PcClient.register(modBus);
        ApiHolder.install(new ApiImpl());
        LOGGER.info("Prompt Critical initialized (API v{})", PromptCriticalApi.CURRENT_API_VERSION);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static final class ApiImpl implements PromptCriticalApi {
        @Override
        public int apiVersion() {
            return CURRENT_API_VERSION;
        }
    }
}
