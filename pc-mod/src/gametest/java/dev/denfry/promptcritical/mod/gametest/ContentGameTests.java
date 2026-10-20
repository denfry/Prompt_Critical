package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.boiler.SteamEngineBlock;
import dev.denfry.promptcritical.mod.boiler.SteamEngineBlockEntity;
import dev.denfry.promptcritical.mod.content.DocumentLecternBlockEntity;
import dev.denfry.promptcritical.mod.content.GeologicalMapItem;
import dev.denfry.promptcritical.mod.content.HandbookEntry;
import dev.denfry.promptcritical.mod.content.HandbookManager;
import dev.denfry.promptcritical.mod.content.HandbookMarkdown;
import dev.denfry.promptcritical.mod.content.HandbookPage;
import dev.denfry.promptcritical.mod.content.HandbookService;
import dev.denfry.promptcritical.mod.content.MapTargetData;
import dev.denfry.promptcritical.mod.content.RouteCondition;
import dev.denfry.promptcritical.mod.content.RouteService;
import dev.denfry.promptcritical.mod.content.RouteTask;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.content.UnlockCondition;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.CommandSource;
import dev.denfry.promptcritical.sim.core.SimCommand;
import dev.denfry.promptcritical.sim.core.SimEvent;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.plant.PlantFuels;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Контент 11 в мире (11 §11.13): выдача справочника (T11.1), открытие главы (T11.2), целостность справочника
 * (по образцу T11.17), триггер {@code sim_event} — «Первый пар» у смены рядом и не у дальнего игрока (T11.4), награды —
 * только опыт (T11.5), инструкция и пюпитр, геологическая карта, декор; «Маршрут» — целостность (T11.17), задание
 * {@code reach} (T11.18), предметы, постройки и развилка.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContentGameTests {
    private static final String SMALL = "gametest/empty_3x3x3";
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos BOILER = new BlockPos(2, 2, 2);
    private static final BlockPos ENGINE = new BlockPos(5, 2, 2);
    private static final CommandSource TEST = new CommandSource.Automatic("gametest");
    private static final AtomicInteger SEQ = new AtomicInteger();

    private ContentGameTests() {
    }

    /**
     * Игрок-заглушка {@link FakePlayer}: mock-игрок GameTest входит без согласованных каналов, и Create роняет тик,
     * отправляя ему свои пакеты. Заглушки нет среди игроков измерения — рассылку проверяем по явному списку.
     */
    private static ServerPlayer player(GameTestHelper h, BlockPos rel) {
        FakePlayer p = new TestPlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_test_" + SEQ.incrementAndGet()));
        Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(rel));
        p.moveTo(at.x, at.y, at.z);
        p.getInventory().clearContent();
        return p;
    }

    /** У {@link FakePlayer} достижения не слушают триггеры; здесь — настоящие, во временном файле. */
    private static final class TestPlayer extends FakePlayer {
        private PlayerAdvancements advancements;

        TestPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public PlayerAdvancements getAdvancements() {
            if (advancements == null) {
                try {
                    Path file = Files.createTempDirectory("pc_adv").resolve(getUUID() + ".json");
                    advancements = new PlayerAdvancements(server.getFixerUpper(), server.getPlayerList(), server.getAdvancements(), file, this);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return advancements;
        }
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static boolean done(ServerPlayer p, String advancement) {
        AdvancementHolder a = p.server.getAdvancements().get(PromptCritical.id(advancement));
        return a != null && p.getAdvancements().getOrStartProgress(a).isDone();
    }

    @GameTest(template = SMALL)
    public static void handbookIsGivenOnceOnFirstJoin(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(1, 1, 1));
        h.assertTrue(HandbookService.giveOnJoin(p), "first join gives the handbook");
        h.assertTrue(count(p, PcItems.ENGINEERING_HANDBOOK.get()) == 1, "one handbook after the first join");
        h.assertFalse(HandbookService.giveOnJoin(p), "second join gives nothing");
        h.assertTrue(count(p, PcItems.ENGINEERING_HANDBOOK.get()) == 1, "still one handbook");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void chapterOpensWhenItemObtained(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(1, 1, 1));
        HandbookEntry coal = HandbookManager.data().entry(PromptCritical.id("i0/coal"));
        h.assertTrue(coal != null && coal.unlock() instanceof UnlockCondition.ItemObtained, "coal chapter opens by item");
        h.assertFalse(HandbookService.unlocked(p, coal.unlock()), "closed before the coal");
        p.getInventory().add(new ItemStack(PcItems.RAW_COAL.get()));
        h.assertTrue(HandbookService.unlocked(p, coal.unlock()), "open once the coal is in the inventory");
        var view = HandbookService.view(p, null, false);
        boolean sentPages = false;
        for (var e : view.getAsJsonArray("entries")) {
            var o = e.getAsJsonObject();
            if (o.get("id").getAsString().equals(coal.id().toString())) sentPages = o.has("pages") && o.get("unlocked").getAsBoolean();
            if (!o.get("unlocked").getAsBoolean()) h.assertFalse(o.has("pages"), "closed chapters go without pages");
        }
        h.assertTrue(sentPages, "the open chapter is sent with its pages");
        h.succeed();
    }

    /**
     * Справочник грузится без ошибок, ссылки на рецепты, достижения, предметы и блоки существуют, тексты на двух языках,
     * у формул есть PNG из сборки.
     */
    @GameTest(template = SMALL)
    public static void handbookIsConsistent(GameTestHelper h) {
        HandbookManager.Data d = HandbookManager.data();
        h.assertTrue(d.problems().isEmpty(), "handbook problems: " + d.problems());
        h.assertTrue(d.entries().size() >= 20 && d.categories().size() >= 3, "I0 chapters loaded: " + d.entries().size());
        var server = h.getLevel().getServer();
        for (HandbookEntry e : d.entries().values()) {
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(e.icon()), e.id() + ": icon " + e.icon());
            switch (e.unlock()) {
                case UnlockCondition.AdvancementDone a -> h.assertTrue(server.getAdvancements().get(a.advancement()) != null,
                        e.id() + ": advancement " + a.advancement());
                case UnlockCondition.ItemObtained i -> h.assertTrue(BuiltInRegistries.ITEM.containsKey(i.item()), e.id() + ": item " + i.item());
                case UnlockCondition.BlockPlaced b -> h.assertTrue(BuiltInRegistries.BLOCK.containsKey(b.block()), e.id() + ": block " + b.block());
                case UnlockCondition.Always ignored -> {
                }
            }
            for (HandbookPage p : e.pages()) {
                switch (p) {
                    case HandbookPage.RecipePage r -> h.assertTrue(server.getRecipeManager().byKey(r.recipe()).isPresent(),
                            e.id() + ": recipe " + r.recipe());
                    case HandbookPage.TextPage t -> {
                        for (String lang : List.of("ru_ru", "en_us")) {
                            Map<String, String> byFile = d.texts().getOrDefault(e.id(), Map.of()).get(lang);
                            h.assertTrue(byFile != null && byFile.containsKey(t.file()) && !byFile.get(t.file()).isBlank(),
                                    e.id() + ": " + lang + " text " + t.file());
                        }
                    }
                    case HandbookPage.MultiblockPage m -> {
                        for (HandbookPage.Placed b : m.blocks()) {
                            try {
                                BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), b.state(), false);
                            } catch (Exception ex) {
                                throw new net.minecraft.gametest.framework.GameTestAssertException(e.id() + ": state " + b.state());
                            }
                        }
                    }
                    case HandbookPage.ImagePage ignored -> {
                    }
                    case HandbookPage.FormulaPage f -> h.assertTrue(ContentGameTests.class.getResource("/assets/"
                            + f.texture().getNamespace() + "/" + f.texture().getPath()) != null && !f.vars().isEmpty(),
                            e.id() + ": formula " + f.formula() + " (renderHandbookFormulas)");
                    case HandbookPage.PonderPage pp -> h.assertTrue(BuiltInRegistries.ITEM.containsKey(pp.item()),
                            e.id() + ": ponder item " + pp.item());
                }
            }
        }
        for (var procedure : HandbookService.PROCEDURES.values())
            h.assertTrue(d.entry(procedure) != null, "procedure chapter " + procedure);
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void markdownLinksAndEmphasis(GameTestHelper h) {
        List<HandbookMarkdown.Block> b = HandbookMarkdown.parse("# Котёл\n\nПервая строка\nвторая **жирная** [глава](promptcritical:i0/boiler).\n\n- пункт");
        h.assertTrue(b.size() == 5, "heading, break, paragraph, break, bullet: " + b.size());
        h.assertTrue(b.get(0).kind() == HandbookMarkdown.Kind.HEADING && b.get(4).kind() == HandbookMarkdown.Kind.BULLET, "kinds");
        Component para = b.get(2).text();
        h.assertTrue(para.getString().equals("Первая строка вторая жирная глава."), "joined: " + para.getString());
        boolean[] bold = {false}, link = {false};
        para.visit((Style s, String text) -> {
            if (text.equals("жирная") && s.isBold()) bold[0] = true;
            ClickEvent c = s.getClickEvent();
            if (text.equals("глава") && c != null && c.getValue().equals(HandbookMarkdown.ENTRY_LINK + "promptcritical:i0/boiler")) link[0] = true;
            return java.util.Optional.empty();
        }, Style.EMPTY);
        h.assertTrue(bold[0] && link[0], "bold run and chapter link");
        h.succeed();
    }

    /** T11.4 для I0: машина набрала обороты → «Первый пар» у смены рядом с ней, у игрока за 200 м — нет. */
    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void firstSteamGoesToTheShiftNearTheEngine(GameTestHelper h) {
        h.setBlock(BOILER, PcBlocks.FIRE_TUBE_BOILER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(ENGINE, PcBlocks.STEAM_ENGINE_CYLINDER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos shaft = ENGINE.offset(SteamEngineBlock.PORT_X, SteamEngineBlock.PORT_Y, SteamEngineBlock.PORT_Z).east();
        h.setBlock(shaft, AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        ServerPlayer near = player(h, new BlockPos(8, 1, 8));
        ServerPlayer far = player(h, new BlockPos(8, 1, 8));
        far.moveTo(far.getX() + 200, far.getY(), far.getZ());
        SimLevelBinding b = SimBindings.get(h.getLevel());
        h.startSequence()
                .thenWaitUntil(() -> {
                    FireTubeBoilerBlockEntity boiler = h.getBlockEntity(BOILER);
                    SteamEngineBlockEntity e = h.getBlockEntity(ENGINE);
                    h.assertTrue(!boiler.simId().isNone() && !e.simId().isNone(), "devices attached");
                })
                .thenExecute(() -> {
                    FireTubeBoilerBlockEntity boiler = h.getBlockEntity(BOILER);
                    SimId id = boiler.simId();
                    SteamPlant plant = b.world().subsystem(SteamPlant.class);
                    plant.presetWater(id, 1.2e6, 0.67);
                    b.submit(new SimCommand.ChargeFuel(id, PlantFuels.COAL, 0.3 * 600), TEST);
                    b.submit(new SimCommand.Operate(id, SteamPlant.IGNITE), TEST);
                    SteamEngineBlockEntity e = h.getBlockEntity(ENGINE);
                    b.submit(new SimCommand.Operate(e.simId(), SteamPlant.TOGGLE_STOP_VALVE), TEST);
                })
                .thenWaitUntil(() -> {
                    SteamEngineBlockEntity e = h.getBlockEntity(ENGINE);
                    var u = b.world().subsystem(SteamPlant.class).engine(e.simId());
                    h.assertTrue(u.omega() >= 0.2 * u.nominalOmega(), "engine runs: " + e.describe());
                })
                .thenExecute(() -> {
                    // событие, которое домен отдал биндингу на этом пуске (сам выпуск — SteamPlantTest)
                    SteamEngineBlockEntity e = h.getBlockEntity(ENGINE);
                    var m = new SimEvent.Milestone(b.world().clock().tick(), e.simId(), SteamPlant.ENGINE_RUNNING, "omega_pu", 0.25);
                    SimAchievements.onMilestone(List.of(near, far), b.world().topology(), m);
                    h.assertTrue(done(near, "first_steam"), "first steam for the shift near the engine");
                    h.assertFalse(done(far, "first_steam"), "no first steam 200 m away");
                })
                .thenSucceed();
    }

    @GameTest(template = SMALL)
    public static void canaryInAMineAndRewardsAreOnlyExperience(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(1, 1, 1));
        SimAchievements.direct(p, SimAchievements.CANARY_PLACED, "", 0);
        h.assertTrue(done(p, "canary"), "canary placed by the player");
        SimAchievements.nearby(List.of(p), h.absolutePos(new BlockPos(1, 1, 1)), SimAchievements.CANARY_REVIVED, "", 0);
        h.assertTrue(done(p, "canary_saved"), "canary revived near the player");
        int ours = 0;
        for (AdvancementHolder a : h.getLevel().getServer().getAdvancements().getAllAdvancements()) {
            // recipes/* — служебные достижения разблокировки рецептов, не каталог 11 §11.2.2
            if (!a.id().getNamespace().equals(PromptCritical.MOD_ID) || a.id().getPath().startsWith("recipes/")) continue;
            ours++;
            var r = a.value().rewards();
            h.assertTrue(r.loot().isEmpty() && r.recipes().isEmpty() && r.function().isEmpty(), a.id() + ": reward is experience only");
        }
        h.assertTrue(ours >= 4, "I0 advancements: " + ours);
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void procedureGoesOnTheLecternAndBack(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(0, 1, 0));
        HandbookService.issueProcedure(p, PcBlocks.FIRE_TUBE_BOILER.get());
        ItemStack doc = ItemStack.EMPTY;
        for (ItemStack s : p.getInventory().items) if (s.is(PcItems.OPERATING_PROCEDURE.get())) doc = s;
        h.assertTrue(PromptCritical.id("i0/procedure_boiler").equals(doc.get(PcDataComponents.PROCEDURE_REF.get())),
                "boiler procedure issued");
        BlockPos lectern = new BlockPos(1, 1, 1);
        h.setBlock(lectern, PcBlocks.DOCUMENT_LECTERN.get().defaultBlockState());
        p.setItemInHand(InteractionHand.MAIN_HAND, doc.copy());
        doc.setCount(0);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(lectern)), Direction.UP, h.absolutePos(lectern), false);
        h.useBlock(lectern, p, hit);
        h.assertBlockProperty(lectern, PcProperties.HAS_DOCUMENT, true);
        DocumentLecternBlockEntity be = h.getBlockEntity(lectern);
        h.assertTrue(be.document().is(PcItems.OPERATING_PROCEDURE.get()) && p.getMainHandItem().isEmpty(), "document on the lectern");
        p.setShiftKeyDown(true);
        h.useBlock(lectern, p, hit);
        h.assertBlockProperty(lectern, PcProperties.HAS_DOCUMENT, false);
        h.assertTrue(count(p, PcItems.OPERATING_PROCEDURE.get()) == 1, "taken back");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void geologicalMapPointsToTheNearestBody(GameTestHelper h) {
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        MapTargetData t = GeologicalMapItem.nearest(h.getLevel(), at);
        h.assertTrue(t != null, "a vein body within the survey area");
        var f = dev.denfry.promptcritical.mod.worldgen.VeinFields.get(h.getLevel().getSeed());
        double dx = t.pos().getX() - at.getX(), dz = t.pos().getZ() - at.getZ();
        h.assertTrue(Math.sqrt(dx * dx + dz * dz) <= (GeologicalMapItem.SEARCH_CELLS + 1.5) * f.config().cellBlocks(), "target within the area");
        h.assertTrue(dev.denfry.promptcritical.sim.mining.VeinCatalog.M2.stream().anyMatch(v -> v.id().equals(t.vein())), "known vein " + t.vein());
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void decorLightSwitchesAndLadderClimbs(GameTestHelper h) {
        BlockPos lamp = new BlockPos(1, 1, 1);
        h.setBlock(lamp, PcBlocks.INDUSTRIAL_LIGHT.get().defaultBlockState());
        h.assertBlockProperty(lamp, BlockStateProperties.LIT, true);
        h.assertTrue(h.getBlockState(lamp).getLightEmission() == 15, "lit lamp gives light 15");
        ServerPlayer p = player(h, new BlockPos(0, 1, 0));
        h.useBlock(lamp, p);
        h.assertBlockProperty(lamp, BlockStateProperties.LIT, false);
        BlockPos ladder = new BlockPos(0, 1, 1);
        h.setBlock(ladder, PcBlocks.CAGE_LADDER.get().defaultBlockState());
        BlockState ls = h.getBlockState(ladder);
        h.assertTrue(ls.isLadder(h.getLevel(), h.absolutePos(ladder), p), "caged ladder climbs");
        for (var form : PcBlocks.decorForms().keySet()) {
            Block block = form.get();
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(BuiltInRegistries.BLOCK.getKey(block)), "item of " + form.getId());
        }
        h.succeed();
    }

    // --- «Маршрут» (11 §11.1.5) ---

    /** События домена и мира, на которые могут ссылаться задания маршрута. */
    private static final Set<String> ROUTE_EVENTS = Set.of(SteamPlant.ENGINE_RUNNING, SteamPlant.BOILER_STATE, SteamPlant.ENGINE_STATE,
            SimAchievements.CANARY_PLACED, SimAchievements.CANARY_REVIVED);

    private static RouteTask task(String id) {
        RouteTask t = HandbookManager.data().tasks().get(PromptCritical.id(id));
        if (t == null) throw new net.minecraft.gametest.framework.GameTestAssertException("no route task " + id);
        return t;
    }

    /**
     * T11.17: файлы маршрута грузятся без ошибок, {@code requires} и варианты развилок существуют, граф без циклов, ID
     * предметов, блоков, тегов, событий и глав есть; в I0 не меньше 30 заданий с текстом на двух языках.
     */
    @GameTest(template = SMALL)
    public static void routeIsConsistent(GameTestHelper h) {
        HandbookManager.Data d = HandbookManager.data();
        h.assertTrue(d.problems().isEmpty(), "handbook problems: " + d.problems());
        h.assertTrue(HandbookManager.routeCycle(d.tasks()) == null, "route graph has no cycles");
        long i0 = d.tasks().values().stream().filter(t -> t.era().equals("I0")).count();
        h.assertTrue(i0 >= 30, "I0 route tasks: " + i0);
        for (RouteTask t : d.tasks().values()) {
            for (String ref : RouteService.registryRefs(t)) {
                String kind = ref.substring(0, ref.indexOf(':'));
                ResourceLocation id = ResourceLocation.parse(ref.substring(kind.length() + 1));
                boolean ok = switch (kind) {
                    case "item" -> BuiltInRegistries.ITEM.containsKey(id);
                    case "block" -> BuiltInRegistries.BLOCK.containsKey(id);
                    case "item_tag" -> BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).map(n -> n.size() > 0).orElse(false);
                    case "block_tag" -> BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id)).map(n -> n.size() > 0).orElse(false);
                    default -> false;
                };
                h.assertTrue(ok, t.id() + ": " + ref);
            }
            if (t.condition() instanceof RouteCondition.Event e)
                h.assertTrue(ROUTE_EVENTS.contains(e.event().toString()), t.id() + ": event " + e.event());
            h.assertTrue(t.entry() != null && d.entry(t.entry()) != null, t.id() + ": chapter " + t.entry());
            for (String lang : List.of("ru_ru", "en_us"))
                h.assertTrue(t.title().byLang().containsKey(lang) && t.text().byLang().containsKey(lang), t.id() + ": " + lang + " text");
        }
        // цикл и висячая ссылка ловятся
        Map<ResourceLocation, RouteTask> loop = new LinkedHashMap<>();
        for (String[] e : new String[][] {{"a", "c"}, {"b", "a"}, {"c", "b"}}) {
            JsonObject o = JsonParser.parseString("{\"id\": \"t:" + e[0] + "\", \"requires\": [\"t:" + e[1]
                    + "\"], \"type\": \"obtain\", \"condition\": {\"item\": \"minecraft:coal\"}}").getAsJsonObject();
            RouteTask t = HandbookManager.parseTask("I0", o);
            loop.put(t.id(), t);
        }
        h.assertTrue(HandbookManager.routeCycle(loop) != null, "a → c → b → a is a cycle");
        loop.remove(ResourceLocation.parse("t:a"));
        h.assertTrue(HandbookManager.routeCycle(loop) == null, "without a there is no cycle");
        h.succeed();
    }

    /**
     * T11.18: давление барабана 1,2 МПа держится 9 мин — задание не выполнено, 10 мин — выполнено, награда только
     * опыт; промах мимо диапазона и перерыв в замерах начинают отсчёт заново.
     */
    @GameTest(template = SMALL)
    public static void routeReachNeedsTheWholeHold(GameTestHelper h) {
        RouteTask t = task("i0/steady_pressure");
        h.assertTrue(t.condition() instanceof RouteCondition.Event e && e.durationS() == 600, "steady_pressure holds 10 min");
        ServerPlayer p = player(h, new BlockPos(1, 1, 1));
        int xp0 = p.totalExperience;
        long t0 = 1_000;
        for (int s = 0; s <= 540; s++)
            RouteService.onEvent(p, SteamPlant.BOILER_STATE, "drum_pressure_mpa", 1.2, t0 + s * 20L);
        h.assertFalse(RouteService.done(p, t.id()), "9 minutes are not enough");
        h.assertTrue(Math.abs(RouteService.heldSeconds(p, t.id()) - 540) < 1e-9, "held 540 s: " + RouteService.heldSeconds(p, t.id()));
        for (int s = 541; s <= 600; s++)
            RouteService.onEvent(p, SteamPlant.BOILER_STATE, "drum_pressure_mpa", 1.2, t0 + s * 20L);
        h.assertTrue(RouteService.done(p, t.id()), "10 minutes complete the task");
        h.assertTrue(p.totalExperience == xp0 + t.xp(), "reward is experience only: " + (p.totalExperience - xp0));
        h.assertTrue(p.getInventory().isEmpty(), "no items given");

        ServerPlayer q = player(h, new BlockPos(1, 1, 1));
        for (int s = 0; s <= 300; s++) RouteService.onEvent(q, SteamPlant.BOILER_STATE, "drum_pressure_mpa", 1.25, t0 + s * 20L);
        RouteService.onEvent(q, SteamPlant.BOILER_STATE, "drum_pressure_mpa", 1.4, t0 + 301 * 20L);
        h.assertTrue(RouteService.heldSeconds(q, t.id()) == 0, "a miss resets the hold");
        for (int s = 302; s <= 700; s++) RouteService.onEvent(q, SteamPlant.BOILER_STATE, "drum_pressure_mpa", 1.2, t0 + s * 20L);
        RouteService.onEvent(q, SteamPlant.BOILER_STATE, "drum_pressure_mpa", 1.2, t0 + 760 * 20L);
        h.assertTrue(RouteService.heldSeconds(q, t.id()) == 0, "a gap in samples restarts the hold");
        h.assertFalse(RouteService.done(q, t.id()), "no task after a broken hold");
        h.succeed();
    }

    /** Предметы и постройки засчитываются раз в секунду, в любом порядке; развилка закрывается любым вариантом. */
    @GameTest(template = SMALL)
    public static void routeObtainBuildAndChoice(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(1, 1, 1));
        p.getInventory().add(new ItemStack(PcItems.RAW_COAL.get(), 15));
        RouteService.poll(p);
        h.assertFalse(RouteService.done(p, task("i0/raw_coal").id()), "15 raw coal are not 16");
        p.getInventory().add(new ItemStack(PcItems.RAW_COAL.get(), 1));
        RouteService.poll(p);
        h.assertTrue(RouteService.done(p, task("i0/raw_coal").id()), "16 raw coal, even before the seam task");
        h.assertFalse(RouteService.done(p, task("i0/coal_seam").id()), "earlier step stays open");
        h.assertTrue(count(p, PcItems.RAW_COAL.get()) == 16, "the route takes nothing");
        p.getStats().setValue(p, Stats.ITEM_USED.get(PcBlocks.CRUCIBLE_FURNACE.get().asItem()), 1); // FakePlayer не пишет статистику
        RouteService.poll(p);
        h.assertTrue(RouteService.done(p, task("i0/crucible").id()) && RouteService.done(p, task("i0/melting").id()),
                "a placed crucible closes the furnace choice");
        h.assertFalse(RouteService.done(p, task("i0/cupola").id()), "the other option stays undone");
        JsonObject v = RouteService.view(p, "ru_ru");
        h.assertTrue(v.getAsJsonArray("eras").size() >= 1, "route view has eras");
        h.succeed();
    }
}
