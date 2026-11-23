package dev.denfry.promptcritical.mod.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.content.ContentRegistries;
import dev.denfry.promptcritical.mod.content.client.WallClockRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryClient;
import dev.denfry.promptcritical.mod.manufacturing.client.ManufacturingClient;
import dev.denfry.promptcritical.mod.panel.PanelLayouts;
import dev.denfry.promptcritical.mod.panel.PanelMenu;
import dev.denfry.promptcritical.mod.panel.client.PanelScreen;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Клиентская регистрация: текстуры жидкостей, слой отрисовки моделей с прозрачностью. Только на клиенте. */
public final class PcClient {
    private PcClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(PcClient::registerClientExtensions);
        modBus.addListener(PcClient::clientSetup);
        FoundryClient.register(modBus);
        ManufacturingClient.register(modBus);
        dev.denfry.promptcritical.mod.ironworks.client.IronworksClient.register(modBus);
        dev.denfry.promptcritical.mod.powerhouse.client.PowerhouseClient.register(modBus);
        dev.denfry.promptcritical.mod.steelworks.client.SteelworksClient.register(modBus);
        dev.denfry.promptcritical.mod.boiler.client.BoilerClient.register(modBus);
        dev.denfry.promptcritical.mod.mine.client.MineClient.register(modBus);
        dev.denfry.promptcritical.mod.grid.client.GridClient.register(modBus);
        dev.denfry.promptcritical.mod.npp.client.NppClient.register(modBus);
        dev.denfry.promptcritical.mod.fx.client.ProcessArtClient.register(modBus);
        dev.denfry.promptcritical.mod.automation.client.AutomationClient.register(modBus);
        dev.denfry.promptcritical.mod.chlor.client.ChlorClient.register(modBus);
        dev.denfry.promptcritical.mod.chem.client.ChemClient.register(modBus);
        dev.denfry.promptcritical.mod.npp.client.NppClient.register(modBus);
        dev.denfry.promptcritical.mod.body.client.BodyClient.register();
        dev.denfry.promptcritical.mod.sound.client.MachineSoundscape.register();
        modBus.addListener(PcClient::renderers);
        modBus.addListener(PcClient::screens);
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(PanelLayouts.MENU.get(), PanelScreen::new);
        // щит с инвентарём снизу выше экрана — инвентарь справа, если по ширине помещается
        PanelMenu.sideInventory = l -> {
            var w = net.minecraft.client.Minecraft.getInstance().getWindow();
            return l.imageHeight(false) > w.getGuiScaledHeight() - 4 && l.imageWidth(true) <= w.getGuiScaledWidth() - 4;
        };
    }

    static {
        // Na-катионитовый фильтр реального масштаба: цельная модель — до запекания
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("ion_exchange_column_real");
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        // водоподготовка: у одноблочных ступеней — модель блока, у ion_exchange_column_real — цельная модель
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.ops.OpsBlockEntities.WATER_TREATMENT.get(),
                c -> new dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<>());
        event.registerBlockEntityRenderer(ContentRegistries.WALL_CLOCK.get(), WallClockRenderer::new);
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.ops.OpsBlockEntities.MECHANICAL_DISCONNECT.get(),
                dev.denfry.promptcritical.mod.ops.client.MechanicalDisconnectRenderer::new);
    }

    private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        for (PcFluids.Entry e : PcFluids.all()) {
            ResourceLocation still = PromptCritical.id("block/fluid/" + e.name + "_still");
            ResourceLocation flow = PromptCritical.id("block/fluid/" + e.name + "_flow");
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return still;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return flow;
                }
            }, e.type.get());
        }
    }

    /** Плейсхолдер-модели не задают {@code render_type}; решётки, стекло и сетка клети требуют cutout. */
    @SuppressWarnings("deprecation")
    private static void clientSetup(FMLClientSetupEvent event) {
        net.createmod.ponder.foundation.PonderIndex.addPlugin(new dev.denfry.promptcritical.mod.content.client.PcPonderPlugin());
        event.enqueueWork(() -> {
            for (var b : PcBlocks.withItems()) {
                if (!PcBlocks.oreDrops().containsKey(b)) ItemBlockRenderTypes.setRenderLayer(b.get(), RenderType.cutout());
            }
        });
    }
}
