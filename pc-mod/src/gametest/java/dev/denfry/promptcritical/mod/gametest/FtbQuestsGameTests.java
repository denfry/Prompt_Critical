package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.compat.ftbquests.FtbQuestsCommand;
import dev.denfry.promptcritical.mod.compat.ftbquests.FtbQuestsExport;
import dev.denfry.promptcritical.mod.content.HandbookManager;
import dev.denfry.promptcritical.mod.content.RouteCondition;
import dev.denfry.promptcritical.mod.content.RouteTask;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Экспорт маршрута в квестбук FTB Quests (16 §16.11.2) без клиента и без самого FTB Quests: формат файлов версии 13,
 * глава на эпоху, квест на задание, зависимости ссылаются на существующие квесты, тексты на всех языках справочника,
 * повторный экспорт идёт поверх своего и не трогает чужой квестбук.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FtbQuestsGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final Pattern ID = Pattern.compile("^\\t*id: \"([0-9A-F]{16})\"$", Pattern.MULTILINE);
    private static final Pattern DEPS = Pattern.compile("^\\t*dependencies: \\[(.*)]$", Pattern.MULTILINE);

    private FtbQuestsGameTests() {
    }

    private static FtbQuestsExport.Result export(MinecraftServer server) {
        return FtbQuestsExport.export(HandbookManager.data().route(), id -> server.getAdvancements().get(id) == null ? null : id);
    }

    @GameTest(template = LARGE, timeoutTicks = 20)
    public static void routeExportsAsAnFtbQuestBook(GameTestHelper h) {
        List<RouteTask.Era> route = HandbookManager.data().route();
        h.assertTrue(!route.isEmpty(), "the handbook route is loaded");
        FtbQuestsExport.Result r = export(h.getLevel().getServer());
        Map<String, String> files = r.files();
        h.assertTrue(files.get(FtbQuestsExport.DATA_FILE).contains("version: " + FtbQuestsExport.FILE_VERSION), "data.snbt has file version 13");
        h.assertTrue(files.containsKey(FtbQuestsExport.GROUP_FILE), "chapter group file");
        long chapters = files.keySet().stream().filter(k -> k.startsWith("chapters/")).count();
        h.assertTrue(chapters == route.size(), "one chapter per era: " + chapters + " for " + route.size());
        int tasks = route.stream().mapToInt(e -> e.tasks().size()).sum();
        h.assertTrue(r.types().size() == tasks, "one quest per task: " + r.types().size() + " for " + tasks);

        Set<String> ids = new HashSet<>();
        StringBuilder all = new StringBuilder();
        for (Map.Entry<String, String> f : files.entrySet()) {
            String s = f.getValue();
            h.assertTrue(depthOk(s), f.getKey() + ": braces and brackets balance outside strings");
            if (!f.getKey().startsWith("chapters/")) continue;
            all.append(s);
            Matcher m = ID.matcher(s);
            while (m.find()) h.assertTrue(ids.add(m.group(1)), "duplicate object id " + m.group(1));
        }
        Matcher d = DEPS.matcher(all);
        while (d.find()) {
            for (String dep : d.group(1).split(", ")) {
                String hex = dep.replace("\"", "");
                h.assertTrue(ids.contains(hex), "dependency " + hex + " points at a quest of the book");
            }
        }

        for (RouteTask.Era era : route) {
            for (RouteTask t : era.tasks()) {
                FtbQuestsExport.TaskType type = r.types().get(t.id());
                if (t.condition() instanceof RouteCondition.Obtain o && !o.tag())
                    h.assertTrue(type == FtbQuestsExport.TaskType.ITEM, t.id() + ": obtain of an item is an item task, got " + type);
                else h.assertTrue(type != FtbQuestsExport.TaskType.ITEM, t.id() + ": only obtain is an item task");
                if (type == FtbQuestsExport.TaskType.ADVANCEMENT)
                    h.assertTrue(all.indexOf("advancement: \"") >= 0, "advancement tasks name their advancement");
            }
        }
        RouteTask first = route.getFirst().tasks().getFirst();
        String questKey = "\"quest." + FtbQuestsExport.hex("quest/" + first.id()) + ".title\"";
        h.assertTrue(files.containsKey("lang/en_us.snbt") && files.get("lang/en_us.snbt").contains(questKey), "English quest titles");
        h.assertTrue(files.containsKey("lang/ru_ru.snbt") && files.get("lang/ru_ru.snbt").contains(questKey), "Russian quest titles");
        h.assertTrue(export(h.getLevel().getServer()).files().equals(files), "the export is deterministic");
        h.succeed();
    }

    @GameTest(template = LARGE, timeoutTicks = 20)
    public static void exportOverwritesItsOwnBookButNotAnother(GameTestHelper h) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("pc-ftbq");
            MinecraftServer server = h.getLevel().getServer();
            FtbQuestsCommand.write(server, dir);
            FtbQuestsCommand.write(server, dir); // своё — поверх
            h.assertTrue(Files.isRegularFile(dir.resolve("lang/en_us.snbt")), "lang file written");
            Files.writeString(dir.resolve(FtbQuestsExport.DATA_FILE), "{\n\tversion: 13\n}\n", StandardCharsets.UTF_8);
            boolean refused = false;
            try {
                FtbQuestsCommand.write(server, dir);
            } catch (IllegalStateException e) {
                refused = true;
            }
            h.assertTrue(refused, "another quest book is left alone");
            h.succeed();
        } catch (IOException e) {
            h.fail("I/O: " + e.getMessage());
        } finally {
            if (dir != null) delete(dir);
        }
    }

    /** Скобки {} и [] сходятся вне строк. */
    private static boolean depthOk(String s) {
        int depth = 0;
        boolean str = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (str) {
                if (c == '\\') i++;
                else if (c == '"') str = false;
            } else if (c == '"') str = true;
            else if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') {
                if (--depth < 0) return false;
            }
        }
        return depth == 0 && !str;
    }

    private static void delete(Path dir) {
        try (Stream<Path> w = Files.walk(dir)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // временная папка теста; не удалилась — не повод валить тест
        }
    }
}
