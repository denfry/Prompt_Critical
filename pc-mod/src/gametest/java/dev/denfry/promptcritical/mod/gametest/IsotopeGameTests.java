package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.isotopes.IrradiationChannelBlockEntity;
import dev.denfry.promptcritical.mod.isotopes.IsotopeComponents;
import dev.denfry.promptcritical.mod.isotopes.IsotopeCraftRecipe;
import dev.denfry.promptcritical.mod.isotopes.RtgBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.isotopes.TargetMaterial;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Изотопы (12): капсула кобальта в потоке канала облучения набирает Co-60 и излучает; перегретая капсула
 * разгерметизируется и загрязняет канал; верстак снаряжает капсулу; РИТЭГ на Sr-90 питает светильник без сети и
 * греет спаи. Поток канала задаётся напрямую, как его отдаёт пульт котла T0.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IsotopeGameTests {
    private static final String SMALL = "gametest/empty_12x12x12";
    private static final BlockPos CHANNEL = new BlockPos(3, 2, 3), RTG = new BlockPos(6, 2, 6), LAMP = new BlockPos(8, 2, 6);

    private IsotopeGameTests() {
    }

    private static ItemStack cobaltCapsule() {
        ItemStack s = new ItemStack(PcItems.IRRADIATION_CAPSULE.get());
        s.set(IsotopeComponents.IRRADIATION_TARGET.get(), IsotopeComponents.IrradiationTarget.fresh(TargetMaterial.COBALT_METAL, 0.1, 0));
        return s;
    }

    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void cobaltCapsuleBreedsCobalt60InTheFlux(GameTestHelper h) {
        h.setBlock(CHANNEL, PcBlocks.IRRADIATION_CHANNEL.get());
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> ((IrradiationChannelBlockEntity) h.getBlockEntity(CHANNEL)).panelItems().setStackInSlot(0, cobaltCapsule()))
                .thenExecuteFor(80, () -> ((IrradiationChannelBlockEntity) h.getBlockEntity(CHANNEL)).push(1e14, 400, 10, h.getLevel().getGameTime()))
                .thenWaitUntil(() -> {
                    IrradiationChannelBlockEntity c = h.getBlockEntity(CHANNEL);
                    ItemStack cap = c.capsule();
                    ActivityData a = cap.get(ActivityData.ACTIVITY.get());
                    h.assertTrue(a != null && a.bq().getOrDefault(Emitter.CO60.name(), 0.0) > 1e8, "Co-60 grows: " + a);
                    h.assertTrue(c.source() == IrradiationChannelBlockEntity.Source.PILE, "flux from the pile: " + c.source());
                    h.assertTrue(cap.get(IsotopeComponents.IRRADIATION_TARGET.get()).sealed(), "capsule intact in a cool channel");
                    h.assertTrue(c.getBlockState().getValue(dev.denfry.promptcritical.mod.block.PcProperties.FILLED), "loaded model");
                })
                .thenSucceed();
    }

    @GameTest(template = SMALL, timeoutTicks = 120)
    public static void overheatedCapsuleFailsAndContaminatesTheChannel(GameTestHelper h) {
        h.setBlock(CHANNEL, PcBlocks.IRRADIATION_CHANNEL.get());
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> ((IrradiationChannelBlockEntity) h.getBlockEntity(CHANNEL)).panelItems().setStackInSlot(0, cobaltCapsule()))
                // плотность мощности втрое выше PWR и горячий графит: γ-нагрев 1,5 кВт на 0,5 К/Вт
                .thenExecuteFor(60, () -> ((IrradiationChannelBlockEntity) h.getBlockEntity(CHANNEL)).push(1e14, 650, 300,
                        h.getLevel().getGameTime()))
                .thenWaitUntil(() -> {
                    IrradiationChannelBlockEntity c = h.getBlockEntity(CHANNEL);
                    h.assertTrue(!c.capsule().get(IsotopeComponents.IRRADIATION_TARGET.get()).sealed(), "capsule failed at " + c.capsuleTempK());
                    h.assertTrue(c.contaminationBq() > 0, "channel contaminated");
                    h.assertTrue(RadiationService.rateSvH(h.getLevel(), Vec3.atCenterOf(h.absolutePos(CHANNEL)).add(1.5, 0, 0)) > 0,
                            "the channel is a source");
                })
                .thenSucceed();
    }

    @GameTest(template = SMALL)
    public static void craftingFillsCapsuleAndMakesHeatSource(GameTestHelper h) {
        ItemStack loaded = IsotopeCraftRecipeAccess.result(List.of(new ItemStack(PcItems.IRRADIATION_CAPSULE.get()),
                new ItemStack(PcItems.COBALT_INGOT.get())));
        var t = loaded.get(IsotopeComponents.IRRADIATION_TARGET.get());
        h.assertTrue(t != null && t.massKg() == IsotopeCraftRecipe.COBALT_KG && "COBALT_METAL".equals(t.material()), "capsule filled: " + t);
        // стеклоблок ВАО с Sr-90 + железо → источник тепла; без стронция — не крафтится
        ItemStack glass = new ItemStack(PcItems.HLW_GLASS_CANISTER.get());
        glass.set(ActivityData.ACTIVITY.get(), new ActivityData(0, 50, 2.7, Map.of(Emitter.SR90.name(), 1.3e15, Emitter.Y90.name(), 1.3e15,
                Emitter.CS137.name(), 1.4e15)));
        ItemStack heat = IsotopeCraftRecipeAccess.result(List.of(glass, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)));
        h.assertTrue(heat.is(PcItems.SR90_HEAT_SOURCE.get()), "Sr-90 heat source: " + heat);
        double w = heat.get(ActivityData.ACTIVITY.get()).inventory(0).decayHeatW();
        h.assertTrue(w > 200 && w < 260, "about a Beta-M: " + w + " W");
        h.succeed();
    }

    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void strontiumRtgLightsALampOffGrid(GameTestHelper h) {
        h.setBlock(RTG, PcBlocks.RTG_THERMOELECTRIC.get());
        h.setBlock(LAMP, PcBlocks.INDUSTRIAL_LIGHT_EXIT.get());
        // четыре «Бета-М» в одном источнике: ≈ 40 Вт, хватает на указатель «Выход» 15 Вт
        ItemStack src = new ItemStack(PcItems.SR90_HEAT_SOURCE.get());
        src.set(ActivityData.ACTIVITY.get(), new ActivityData(0, 3, 5.1, Map.of(Emitter.SR90.name(), 5.1e15, Emitter.Y90.name(), 5.1e15)));
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> ((RtgBlockEntity) h.getBlockEntity(RTG)).panelItems().setStackInSlot(0, src))
                .thenWaitUntil(() -> {
                    RtgBlockEntity r = h.getBlockEntity(RTG);
                    h.assertTrue(r.electricW() > 15, "RTG output " + r.electricW());
                    h.assertTrue(r.state().hotK() > r.state().coldK() + 100, "hot junction above the fins");
                    h.assertBlockProperty(LAMP, BlockStateProperties.LIT, true);
                })
                .thenSucceed();
    }

    /**
     * Одиночный «Бета-М» и светильник НПП 60 Вт: без буферного аккумулятора огонь не горит, с заряженным — горит,
     * пока аккумулятор покрывает недостаток (12 §12.5.1, маячный огонь через аккумулятор).
     */
    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void singleBetaMLightsALampFromItsBufferBattery(GameTestHelper h) {
        h.setBlock(RTG, PcBlocks.RTG_THERMOELECTRIC.get());
        h.setBlock(LAMP, PcBlocks.LAMP_BULKHEAD.get());
        ItemStack src = new ItemStack(PcItems.SR90_HEAT_SOURCE.get());
        src.set(ActivityData.ACTIVITY.get(), new ActivityData(0, 0.75, 5.1, Map.of(Emitter.SR90.name(), 1.27e15, Emitter.Y90.name(), 1.27e15)));
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> ((RtgBlockEntity) h.getBlockEntity(RTG)).panelItems().setStackInSlot(0, src))
                .thenWaitUntil(() -> {
                    RtgBlockEntity r = h.getBlockEntity(RTG);
                    h.assertTrue(r.electricW() > 5 && r.electricW() < 60, "a single Beta-M: " + r.electricW());
                })
                .thenExecute(() -> ((RtgBlockEntity) h.getBlockEntity(RTG)).setStoredJ(0))
                .thenIdle(60)
                .thenExecute(() -> h.assertBlockProperty(LAMP, BlockStateProperties.LIT, false))
                .thenExecute(() -> ((RtgBlockEntity) h.getBlockEntity(RTG)).setStoredJ(RtgBlockEntity.BUFFER_J))
                .thenWaitUntil(() -> h.assertBlockProperty(LAMP, BlockStateProperties.LIT, true))
                .thenExecute(() -> {
                    RtgBlockEntity r = h.getBlockEntity(RTG);
                    h.assertTrue(r.storedJ() < RtgBlockEntity.BUFFER_J, "the lamp draws on the buffer");
                })
                .thenSucceed();
    }

    /** Доступ к рецепту изотопных операций без верстака. */
    private static final class IsotopeCraftRecipeAccess {
        static ItemStack result(List<ItemStack> items) {
            return new IsotopeCraftRecipe(net.minecraft.world.item.crafting.CraftingBookCategory.MISC)
                    .assemble(CraftingInput.of(items.size(), 1, items), h());
        }

        private static net.minecraft.core.HolderLookup.Provider h() {
            return net.minecraft.core.RegistryAccess.EMPTY;
        }
    }
}
