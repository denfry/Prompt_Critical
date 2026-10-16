package dev.denfry.promptcritical.mod.film;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import dev.denfry.promptcritical.mod.PromptCritical;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import org.slf4j.Logger;

/**
 * Съёмка через Flashback (только dev-клиент, Flashback грузится через Sinytra Connector): запись реплея
 * во время пролёта filmcam и покадровый экспорт с той же траекторией камеры, независимо от fps игры.
 *
 * <pre>
 * /film take &lt;shot&gt;      — запись реплея &lt;shot&gt;.zip, пока идёт /function filmcam:play {shot:"&lt;shot&gt;"}
 * /film render &lt;shot&gt;... — открыть каждый реплей и отрендерить &lt;shot&gt;.mp4 (1080p60) в -Dfilm.out (иначе run/film),
 *                          после очереди — выход из реплея в главное меню
 * /film cancel           — остановить take/render (запись отбрасывается)
 * </pre>
 *
 * Поза камеры снимается на сервере каждый тик пролёта (item_display с тегом filmcam, время — счёт #t filmcam),
 * поэтому подходят и follow-шоты; длина пролёта — из shot/&lt;shot&gt;.mcfunction. Траектория кладётся рядом
 * с реплеем в &lt;shot&gt;.film.json вместе с тиком реплея, на котором начался пролёт.
 * Flashback вызывается через reflection: его jar в промежуточных маппингах, компилировать против него нельзя.
 * Ход работы — строки "[film]" в логе клиента.
 */
@EventBusSubscriber(modid = PromptCritical.MOD_ID, value = Dist.CLIENT)
public final class FilmFlashback {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** "matches N" или "matches A..N" (follow-шоты); "N.." — уже после пролёта, не считается. */
    private static final Pattern MATCHES = Pattern.compile("matches (?:-?\\d+\\.\\.)?(\\d+) ");
    /** Выдержка первого кадра в filmcam:play (#t от -20) и задержка schedule 1t. */
    private static final int HOLD = 20;
    private static final int SCHEDULE_DELAY = 1;
    /** Тиков записи до старта пролёта (снимок мира) и после конца. */
    private static final int LEAD_IN = 10;
    private static final int TAIL = 10;
    /** Тиков на подгрузку мира реплея перед экспортом. */
    private static final int SETTLE = 40;

    private static final String FB = "com.moulberry.flashback.";

    private static volatile Take take;
    private static final Deque<String> renderQueue = new ArrayDeque<>();
    private static Render render;

    private FilmFlashback() {
    }

    /** Сайдкар реплея: тик реплея для t=0 пролёта и поза камеры по тикам шота [t, x, y, z, yaw, pitch]. */
    record ShotPath(String shot, int base, int hold, List<double[]> keys) {
    }

    private static final class Take {
        final String shot;
        final int length;
        /** t пролёта -> [t, x, y, z, yaw, pitch]; пишет поток сервера. */
        final ConcurrentSkipListMap<Integer, double[]> sampled = new ConcurrentSkipListMap<>();
        int ticks;
        int base = -1;

        Take(String shot, int length) {
            this.shot = shot;
            this.length = length;
        }
    }

    private static final class Render {
        final String shot;
        final ShotPath path;
        int waited;
        boolean started;

        Render(String shot, ShotPath path) {
            this.shot = shot;
            this.path = path;
        }
    }

    @SubscribeEvent
    static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("film")
                .then(Commands.literal("take")
                        .then(Commands.argument("shot", StringArgumentType.word()).executes(FilmFlashback::take)))
                .then(Commands.literal("render")
                        .then(Commands.argument("shots", StringArgumentType.greedyString()).executes(FilmFlashback::render)))
                .then(Commands.literal("cancel").executes(ctx -> {
                    cancel();
                    return 1;
                })));
    }

    private static int take(CommandContext<CommandSourceStack> ctx) {
        String shot = StringArgumentType.getString(ctx, "shot");
        if (take != null || render != null) {
            return fail(ctx, "занято: " + (take != null ? "take " + take.shot : "render " + render.shot));
        }
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return fail(ctx, "нужен одиночный мир");
        }
        int length;
        try {
            length = shotLength(server.getWorldPath(LevelResource.DATAPACK_DIR)
                    .resolve("filmcam/data/filmcam/function/shot/" + shot + ".mcfunction"));
        } catch (IOException e) {
            return fail(ctx, "нет шота " + shot + ": " + e.getMessage());
        }
        if (length <= 0) {
            return fail(ctx, "в шоте " + shot + " нет тиков пролёта");
        }
        try {
            call(cls("Flashback"), "startRecordingReplay");
            if (recorder() == null) {
                return fail(ctx, "Flashback не начал запись (несовместимые моды?)");
            }
        } catch (ReflectiveOperationException e) {
            return fail(ctx, "Flashback недоступен: " + e);
        }
        take = new Take(shot, length);
        log("take " + shot + ": запись, пролёт " + take.length + " тиков");
        return 1;
    }

    private static int render(CommandContext<CommandSourceStack> ctx) {
        if (take != null || render != null) {
            return fail(ctx, "занято");
        }
        renderQueue.addAll(Arrays.asList(StringArgumentType.getString(ctx, "shots").trim().split("\\s+")));
        log("render: очередь " + renderQueue);
        return 1;
    }

    private static void cancel() {
        try {
            if (take != null && recorder() != null) {
                call(cls("Flashback"), "cancelRecordingReplay");
            }
            if (render != null && render.started) {
                setStatic(cls("Flashback"), "EXPORT_JOB", null);
            }
        } catch (ReflectiveOperationException e) {
            LOG.error("[film] cancel", e);
        }
        take = null;
        render = null;
        renderQueue.clear();
        log("отменено");
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        try {
            if (take != null) {
                tickTake();
            } else if (render != null) {
                tickRender();
            } else if (!renderQueue.isEmpty()) {
                openNext();
            }
        } catch (Exception e) {
            LOG.error("[film] ошибка, задача снята", e);
            take = null;
            render = null;
            renderQueue.clear();
        }
    }

    private static void tickTake() throws ReflectiveOperationException, IOException {
        Take t = take;
        Object recorder = recorder();
        if (recorder == null) {
            log("take " + t.shot + ": запись прервана извне");
            take = null;
            return;
        }
        t.ticks++;
        if (t.ticks == LEAD_IN) {
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            String cmd = "function filmcam:play {shot:\"" + t.shot + "\"}";
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd));
            int written = getField(recorder, "writtenTicks");
            // play выполняется на следующем тике сервера, t=0 пролёта — через выдержку и schedule 1t
            t.base = written + 1 + SCHEDULE_DELAY + HOLD;
        }
        if (t.ticks < LEAD_IN + 1 + SCHEDULE_DELAY + HOLD + t.length + TAIL) {
            return;
        }
        Class<?> flashback = cls("Flashback");
        setStatic(flashback, "RECORDER", null);
        invoke(recorder, "endTick", new Class<?>[] {boolean.class}, true);
        Path folder = (Path) invoke(recorder, "finish", new Class<?>[0]);
        Path replays = (Path) call(flashback, "getReplayFolder");
        Files.createDirectories(replays);
        Path zip = replays.resolve(t.shot + ".zip");
        Files.deleteIfExists(zip);
        cls("record.ReplayExporter").getMethod("export", Path.class, Path.class, String.class).invoke(null, folder, zip, t.shot);
        List<double[]> keys = new ArrayList<>(t.sampled.values());
        if (keys.size() < t.length) {
            log("take " + t.shot + ": камера найдена на " + keys.size() + " из " + (t.length + 1) + " тиков");
        }
        Files.writeString(replays.resolve(t.shot + ".film.json"), GSON.toJson(new ShotPath(t.shot, t.base, HOLD, keys)));
        log("take " + t.shot + ": реплей " + zip + ", пролёт с тика " + t.base);
        take = null;
    }

    private static void openNext() throws ReflectiveOperationException, IOException {
        String shot = renderQueue.poll();
        Class<?> flashback = cls("Flashback");
        Path replays = (Path) call(flashback, "getReplayFolder");
        Path zip = replays.resolve(shot + ".zip");
        Path side = replays.resolve(shot + ".film.json");
        if (!Files.exists(zip) || !Files.exists(side)) {
            log("render " + shot + ": нет " + zip + " или " + side + ", пропуск");
            return;
        }
        ShotPath path = GSON.fromJson(Files.readString(side), ShotPath.class);
        render = new Render(shot, path);
        flashback.getMethod("openReplayWorld", Path.class).invoke(null, zip);
        log("render " + shot + ": открыт реплей");
    }

    private static void tickRender() throws ReflectiveOperationException, IOException {
        Render r = render;
        Class<?> flashback = cls("Flashback");
        Minecraft mc = Minecraft.getInstance();
        if (r.started) {
            if (getStatic(flashback, "EXPORT_JOB") == null) {
                log("render " + r.shot + ": готово");
                render = null;
                if (renderQueue.isEmpty() && mc.level != null) {
                    mc.level.disconnect();
                    mc.disconnect();
                    mc.setScreen(new TitleScreen());
                }
            }
            return;
        }
        boolean ready = (boolean) call(flashback, "isInReplay") && mc.level != null && mc.player != null
                && call(flashback, "getReplayServer") != null;
        if (!ready || ++r.waited < SETTLE) {
            return;
        }
        Object editorState = call(cls("state.EditorStateManager"), "getCurrent");
        if (editorState == null) {
            throw new IllegalStateException("нет EditorState реплея");
        }
        writeCameraTrack(editorState, r.path, mc.player.getEyeHeight());

        String outDir = System.getProperty("film.out");
        Path out = (outDir != null ? Path.of(outDir) : mc.gameDirectory.toPath().resolve("film")).resolve(r.shot + ".mp4");
        Files.createDirectories(out.getParent());
        Files.deleteIfExists(out);
        Object settings = exportSettings(editorState, r.path, out);
        Object job = cls("exporting.ExportJob").getConstructor(cls("exporting.ExportSettings")).newInstance(settings);
        setStatic(flashback, "EXPORT_JOB", job);
        r.started = true;
        log("render " + r.shot + ": экспорт в " + out);
    }

    /** Заменяет дорожки камеры текущей сцены одной дорожкой с ключом на каждый тик пролёта. */
    private static void writeCameraTrack(Object editorState, ShotPath path, float eyeHeight) throws ReflectiveOperationException {
        Object cameraType = getStatic(cls("keyframe.types.CameraKeyframeType"), "INSTANCE");
        Class<?> interpolation = cls("keyframe.interpolation.InterpolationType");
        Object smooth = enumValue(interpolation, "SMOOTH");
        Class<?> keyframeType = cls("keyframe.KeyframeType");
        Constructor<?> keyframeCtor = cls("keyframe.impl.CameraKeyframe")
                .getConstructor(Vector3d.class, float.class, float.class, float.class, interpolation);
        Constructor<?> trackCtor = cls("state.KeyframeTrack").getConstructor(keyframeType);

        Object track = trackCtor.newInstance(cameraType);
        @SuppressWarnings("unchecked")
        TreeMap<Integer, Object> byTick = getField(track, "keyframesByTick");
        double prevYaw = Double.NaN;
        double[] first = path.keys().get(0);
        // выдержка: та же поза, что на t=0
        byTick.put(path.base() - path.hold(), keyframeCtor.newInstance(
                new Vector3d(first[1], first[2] - eyeHeight, first[3]), (float) first[4], (float) first[5], 0f, smooth));
        for (double[] k : path.keys()) {
            double yaw = k[4];
            if (!Double.isNaN(prevYaw)) {
                yaw = prevYaw + wrap(yaw - prevYaw); // без скачка через ±180
            }
            prevYaw = yaw;
            byTick.put(path.base() + (int) k[0], keyframeCtor.newInstance(
                    new Vector3d(k[1], k[2] - eyeHeight, k[3]), (float) yaw, (float) k[5], 0f, smooth));
        }

        long stamp = (long) invoke(editorState, "acquireWrite", new Class<?>[0]);
        try {
            Object scene = invoke(editorState, "getCurrentScene", new Class<?>[] {long.class}, stamp);
            List<Object> tracks = getField(scene, "keyframeTracks");
            tracks.removeIf(tr -> {
                try {
                    return getField(tr, "keyframeType") == cameraType;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            tracks.add(track);
        } finally {
            invoke(editorState, "release", new Class<?>[] {long.class}, stamp);
        }
        setField(editorState, "hideAllSpectators", true);
        invoke(editorState, "markDirty", new Class<?>[0]);
    }

    private static Object exportSettings(Object editorState, ShotPath path, Path out) throws ReflectiveOperationException {
        Minecraft mc = Minecraft.getInstance();
        Object codec = enumValue(cls("combo_options.VideoCodec"), "H264");
        List<String> encoders = Arrays.asList((String[]) invoke(codec, "getEncoders", new Class<?>[0]));
        String encoder = encoders.contains("libx264") ? "libx264" : encoders.get(0);
        int start = path.base() - path.hold();
        int end = path.base() + (int) path.keys().get(path.keys().size() - 1)[0];
        Constructor<?> ctor = cls("exporting.ExportSettings").getDeclaredConstructors()[0];
        return ctor.newInstance(
                path.shot(), invoke(editorState, "copy", new Class<?>[0]),
                mc.player.position(), mc.player.getYRot(), mc.player.getXRot(),
                1920, 1080, start, end, 60.0,
                false,
                enumValue(cls("combo_options.VideoContainer"), "MP4"), codec, encoder, 40_000_000, false, false, true,
                false, false, null,
                out, null);
    }

    /** Последний тик пролёта: наибольшее "matches N" в функции шота. */
    static int shotLength(Path function) throws IOException {
        int max = -1;
        for (String line : Files.readAllLines(function)) {
            Matcher m = MATCHES.matcher(line);
            if (m.find()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        return max;
    }

    /** Поза камеры filmcam на конце тика сервера: tp шота за этот тик уже применён, #t — его время. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        Take t = take;
        if (t == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        Objective objective = server.getScoreboard().getObjective("filmcam");
        if (objective == null) {
            return;
        }
        ReadOnlyScoreInfo score = server.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly("#t"), objective);
        if (score == null || score.value() < 0 || score.value() > t.length) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Display.ItemDisplay cam : level.getEntities(EntityTypeTest.forClass(Display.ItemDisplay.class),
                    e -> e.getTags().contains("filmcam"))) {
                t.sampled.put(score.value(), new double[] {score.value(), cam.getX(), cam.getY(), cam.getZ(),
                        cam.getYRot(), cam.getXRot()});
                return;
            }
        }
    }

    private static double wrap(double deg) {
        deg %= 360;
        if (deg >= 180) {
            deg -= 360;
        } else if (deg < -180) {
            deg += 360;
        }
        return deg;
    }

    private static Object recorder() throws ReflectiveOperationException {
        return getStatic(cls("Flashback"), "RECORDER");
    }

    private static Class<?> cls(String name) throws ClassNotFoundException {
        return Class.forName(FB + name);
    }

    private static Object call(Class<?> owner, String method) throws ReflectiveOperationException {
        return owner.getMethod(method).invoke(null);
    }

    private static Object invoke(Object target, String method, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method, types);
        return m.invoke(target, args);
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // ищем в родителе
            }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws ReflectiveOperationException {
        return (T) field(target.getClass(), name).get(target);
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        field(target.getClass(), name).set(target, value);
    }

    private static Object getStatic(Class<?> owner, String name) throws ReflectiveOperationException {
        return field(owner, name).get(null);
    }

    private static void setStatic(Class<?> owner, String name, Object value) throws ReflectiveOperationException {
        field(owner, name).set(null, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendFailure(Component.literal("[film] " + message));
        LOG.warn("[film] {}", message);
        return 0;
    }

    private static void log(String message) {
        LOG.info("[film] {}", message);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[film] " + message), false);
        }
    }
}
