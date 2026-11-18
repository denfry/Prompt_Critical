package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.sim.automation.twin.Challenge;
import dev.denfry.promptcritical.sim.automation.twin.Challenge.Metric;
import dev.denfry.promptcritical.sim.automation.twin.ChallengeBoard;
import dev.denfry.promptcritical.sim.automation.twin.ChallengeRun;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Задачи наладчика в мире (03d §3d.11): итог серверной попытки на инженерной станции — в таблицу лидеров
 * ({@link ChallengeBoard} в {@code SavedData} сервера, 03d §3d.11.4) и в достижения 11 (03d §3d.11.5): решённая задача
 * ({@code challenge_completed}, тег — задача), метрика в верхних 10 % сервера ({@code tuner}), бензин в спецификации
 * не дороже 105 % оптимума ЛП ({@code refinery_lp}, тег {@code cost_ratio}).
 */
public final class TunerChallenges {
    public static final String COMPLETED = "promptcritical:challenge_completed", TUNER = "promptcritical:tuner",
            REFINERY_LP = "promptcritical:refinery_lp";
    public static final String BLENDING = "gasoline_blending_lp";

    /** Итог одной метрики для сообщения игроку. */
    public record Result(Metric metric, double value, ChallengeBoard.Placement placement) {
    }

    private TunerChallenges() {
    }

    static final class Data extends SavedData {
        final ChallengeBoard board = new ChallengeBoard();

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                String key = c.getString("key");
                int slash = key.indexOf('/');
                if (slash <= 0) continue;
                d.board.restore(key.substring(0, slash), key.substring(slash + 1),
                        new ChallengeBoard.Entry(c.getString("player"), c.getDouble("value"), c.getLong("time")));
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Map.Entry<String, List<ChallengeBoard.Entry>> e : board.entries().entrySet()) {
                for (ChallengeBoard.Entry x : e.getValue()) {
                    CompoundTag c = new CompoundTag();
                    c.putString("key", e.getKey());
                    c.putString("player", x.player());
                    c.putDouble("value", x.value());
                    c.putLong("time", x.time());
                    list.add(c);
                }
            }
            tag.put("entries", list);
            return tag;
        }
    }

    static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null),
                PromptCritical.MOD_ID + "_challenges");
    }

    public static ChallengeBoard board(MinecraftServer server) {
        return data(server).board;
    }

    /**
     * Записать прошедшую попытку игрока и выдать события достижений. Время отправки — тик сервера (ничьи — кто раньше).
     * Возвращает места по метрикам; у проваленной попытки — пусто.
     */
    public static List<Result> submit(ServerPlayer player, ChallengeRun run) {
        List<Result> out = new ArrayList<>();
        Map<String, Double> metrics = run.metrics();
        if (metrics.isEmpty()) return out;
        Challenge c = run.challenge();
        Data d = data(player.server);
        boolean top = false;
        for (Metric m : c.metrics()) {
            Double v = metrics.get(m.id());
            if (v == null || !Double.isFinite(v)) continue;
            ChallengeBoard.Placement p = d.board.submit(c, m, player.getGameProfile().getName(), v, player.server.getTickCount());
            top |= p.topTenth();
            out.add(new Result(m, v, p));
        }
        d.setDirty();
        SimAchievements.direct(player, COMPLETED, c.id(), 1);
        if (top) SimAchievements.direct(player, TUNER, c.id(), 1);
        if (BLENDING.equals(c.id()) && run.meetsThreshold()) {
            Double premium = metrics.get(c.metrics().getFirst().id());
            SimAchievements.direct(player, REFINERY_LP, "cost_ratio", 1 + premium / 100);
        }
        return out;
    }

    /** Строки таблицы для команды: «1. игрок — значение». */
    public static List<String> top(MinecraftServer server, String challenge, String metric) {
        List<String> out = new ArrayList<>();
        int i = 1;
        for (ChallengeBoard.Entry e : board(server).top(challenge, metric))
            out.add(i++ + ". " + e.player() + " — " + String.format(java.util.Locale.ROOT, "%.3f", e.value()));
        return out;
    }

    /** Сбросить таблицу задачи (команда администратора). */
    public static void reset(MinecraftServer server, String challenge) {
        Data d = data(server);
        d.board.reset(challenge);
        d.setDirty();
    }
}
