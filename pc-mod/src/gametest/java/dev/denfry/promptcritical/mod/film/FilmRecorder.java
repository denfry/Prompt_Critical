package dev.denfry.promptcritical.mod.film;

import com.google.gson.Gson;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import dev.denfry.promptcritical.mod.PromptCritical;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/**
 * Покадровая съёмка роликов (только dev-клиент): если в каталоге игры лежит {@code film.json}, после входа в
 * одиночный мир снимает шоты по очереди в {@code <out>/<shot>.mp4} и переименовывает план в {@code film.done}.
 *
 * <p>Время игры на записи — виртуальные часы ({@link Util#timeSource}): между кадрами клиент и встроенный сервер
 * видят ровно 1/fps секунды, сколько бы ни рендерился и ни кодировался кадр. Сервер дожидается тиков, частичный
 * тик кадра выставляется точно, инерция стрелок щитов и подсветка кнопок идут по тем же часам — 60 fps без рывков
 * при любом разрешении. Кадр рендерится в {@code size} (framebuffer подменяется, окно может быть меньше) и
 * читается из основного framebuffer в ffmpeg, который сжимает его до {@code scale} — сглаживание пиксель-арта.
 * Пока сцена не дособрана (секции чанков в очереди), кадр перерисовывается без хода времени.
 *
 * <pre>
 * {"ffmpeg": "ffmpeg", "out": "film", "quit": true, "fps": 60, "size": [3840, 2160], "scale": [1920, 1080],
 *  "shots": [{"name": "boiler", "duration": 6, "time": 18000, "gamma": 0.3, "guiScale": 8, "mode": "spectator",
 *             "setup": ["pc use -20 -59 0 minecraft:coal"], "settle": 60, "ease": "inOut",
 *             "keys": [{"t": 0, "pos": [x, y, z], "look": [x, y, z], "fov": 50, "roll": 0}, {"t": 6, ...}],
 *             "events": [{"t": 1, "cmd": "..."}, {"t": 2, "panel": [x, y, z]}, {"t": 2.5, "cursor": "Fire", "dur": 0.6},
 *                        {"t": 3.2, "click": true}, {"t": 4, "at": [40, 60], "dur": 0.8}, {"t": 5.5, "close": true}]}]}
 * </pre>
 * {@code pos} — глаза камеры, {@code look} — куда смотреть; ключи соединяет кубический сплайн по времени,
 * {@code ease} перераспределяет ход по всей длине шота. {@code panel} открывает щит машины от имени игрока
 * (режим шота должен позволять клики — {@code creative}), {@code cursor} ведёт нарисованный курсор к кнопке
 * с такой подписью, {@code at} — к точке щита в его координатах. {@code stills: true} в шоте пишет JPEG-кадры
 * в {@code <out>/<shot>/} вместо видео — для черновых раскадровок.
 */
@EventBusSubscriber(modid = PromptCritical.MOD_ID, value = Dist.CLIENT)
public final class FilmRecorder {
    private static final Logger LOG = LogUtils.getLogger();
    /** Тиков после входа в мир до первого шота: подгрузка чанков, и GalleryShots к этому времени уже отработал. */
    private static final int WARMUP = 220;
    /** Перерисовок одного кадра в ожидании секций чанков, дальше снимаем как есть. */
    private static final int MAX_RETRY = 40;
    private static final long TICK_NANOS = 50_000_000L;

    record Plan(String ffmpeg, String out, Boolean quit, Integer fps, int[] size, int[] scale, Integer crf, List<Shot> shots) {
    }

    record Shot(String name, Double duration, Integer fps, int[] size, int[] scale, Integer crf, Boolean stills,
                Integer time, Double gamma, Integer guiScale, Integer blur, String mode, List<String> setup, Integer settle,
                String ease, List<Key> keys, List<Event> events) {
    }

    record Key(double t, double[] pos, double[] look, Double fov, Double roll) {
    }

    record Event(double t, String cmd, int[] panel, String cursor, double[] at, Double dur, Boolean click, Boolean close) {
    }

    private enum Phase { SETUP, RECORD }

    private static Plan plan;
    private static Deque<Shot> queue;
    private static Shot shot;
    private static Phase phase;
    private static int inWorld;
    private static int setupTicks;
    private static Path out;

    /** Виртуальные часы: на записи — {@link #vnow}, иначе реальное время со сдвигом после прошлой записи. */
    private static volatile long vnow;
    private static volatile boolean virtual;
    private static long offset;

    private static int frame, frames, retry, nextEvent, fps;
    /** Статистика шота: перерисовки без хода времени и наносекунды на рендер, чтение кадра, ожидание сервера. */
    private static long retries, nsRender, nsRead, nsServer, preAt;
    private static long t0;
    /** Время шота, в котором сейчас стоит камера (на подготовке — точка пролёта). */
    private static double camT;
    private static Spline path;
    private static Encoder encoder;
    private static Saved saved;

    /** Нарисованный курсор щита в координатах GUI: из точки from в to за [c0, c1] секунд шота. */
    private static boolean cursorShown;
    private static double fromX, fromY, toX, toY, c0, c1;

    private FilmRecorder() {
    }

    // ---------------------------------------------------------------- очередь шотов

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            inWorld = 0;
            return;
        }
        if (queue == null) {
            if (++inWorld < WARMUP) return;
            queue = load(mc.gameDirectory.toPath().resolve("film.json"));
            if (queue.isEmpty()) return;
            saved = Saved.capture(mc);
        }
        try {
            if (shot == null) {
                shot = queue.poll();
                if (shot == null) {
                    finish(mc);
                    return;
                }
                beginSetup(mc);
            } else if (phase == Phase.SETUP) {
                tickSetup(mc);
            }
        } catch (Exception e) {
            LOG.error("[film] шот {} сорван", shot != null ? shot.name() : "?", e);
            abortShot(mc);
        }
    }

    private static Deque<Shot> load(Path file) {
        Deque<Shot> q = new ArrayDeque<>();
        if (!Files.exists(file)) return q;
        try {
            plan = new Gson().fromJson(Files.readString(file), Plan.class);
            q.addAll(plan.shots());
            Path dir = Path.of(plan.out() != null ? plan.out() : "film");
            out = dir.isAbsolute() ? dir : Minecraft.getInstance().gameDirectory.toPath().resolve(dir);
            Files.createDirectories(out);
            LOG.info("[film] {} шотов в {}", q.size(), out);
        } catch (IOException | RuntimeException e) {
            LOG.error("[film] не прочитать {}", file, e);
        }
        return q;
    }

    private static void beginSetup(Minecraft mc) {
        Shot s = shot;
        path = new Spline(s.keys());
        fps = s.fps() != null ? s.fps() : plan.fps() != null ? plan.fps() : 60;
        int[] size = s.size() != null ? s.size() : plan.size() != null ? plan.size() : new int[] {1920, 1080};
        saved.apply(mc, size, s.guiScale(), s.gamma(), s.blur());
        cursorShown = false;
        if (mc.screen != null) mc.player.closeContainer();
        IntegratedServer server = mc.getSingleplayerServer();
        String name = mc.player.getGameProfile().getName();
        List<String> cmds = new ArrayList<>();
        cmds.add("gamemode " + (s.mode() != null ? s.mode() : "spectator") + " " + name);
        if (s.time() != null) cmds.add("time set " + s.time());
        if (s.setup() != null) cmds.addAll(s.setup());
        Pose p = path.at(0);
        cmds.add(String.format(Locale.ROOT, "tp %s %.4f %.4f %.4f", name, p.x, p.y - mc.player.getEyeHeight(), p.z));
        server.execute(() -> cmds.forEach(c -> run(server, c)));
        phase = Phase.SETUP;
        setupTicks = 0;
        LOG.info("[film] {}: подготовка", s.name());
    }

    /** Подготовка в реальном времени: камера быстро пролетает путь (секции чанков по пути собираются), потом стоит в начале. */
    private static void tickSetup(Minecraft mc) throws IOException {
        int settle = shot.settle() != null ? shot.settle() : 60;
        setupTicks++;
        int fly = settle / 2;
        camT = setupTicks < fly ? path.duration() * setupTicks / fly : 0;
        place(mc.player, path.at(camT));
        mc.options.hideGui = true;
        if (mc.player.isCreative() && !mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
        if (setupTicks < settle || !mc.levelRenderer.hasRenderedAllSections() && setupTicks < settle * 4) return;
        beginRecord(mc);
    }

    private static void beginRecord(Minecraft mc) throws IOException {
        Shot s = shot;
        double duration = s.duration() != null ? s.duration() : path.duration();
        frames = Math.max(1, (int) Math.round(duration * fps));
        frame = 0;
        retry = 0;
        retries = nsRender = nsRead = nsServer = 0;
        nextEvent = 0;
        RenderTarget target = mc.getMainRenderTarget();
        int[] scale = s.scale() != null ? s.scale() : plan.scale() != null ? plan.scale() : new int[] {target.width, target.height};
        int crf = s.crf() != null ? s.crf() : plan.crf() != null ? plan.crf() : 12;
        encoder = new Encoder(ffmpegArgs(s, scale, crf), out.resolve(s.name() + ".ffmpeg.log"), target, scale[0], scale[1]);
        // виртуальное время стартует со следующей границы тика: кадры 0, 3, 6... совпадают с тиками
        long now = Util.getNanos();
        t0 = (now / TICK_NANOS + 1) * TICK_NANOS;
        vnow = t0;
        virtual = true;
        Util.timeSource = () -> vnow;
        phase = Phase.RECORD;
        LOG.info("[film] {}: запись {} кадров {}x{} -> {}x{}", s.name(), frames, target.width, target.height, scale[0], scale[1]);
    }

    private static List<String> ffmpegArgs(Shot s, int[] scale, int crf) {
        String ff = plan.ffmpeg() != null ? plan.ffmpeg() : "ffmpeg";
        String vf = "vflip,scale=" + scale[0] + ":" + scale[1];
        List<String> a = new ArrayList<>(List.of(ff, "-y", "-hide_banner", "-loglevel", "warning",
                "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", scale[0] + "x" + scale[1], "-r", String.valueOf(fps), "-i", "-"));
        if (Boolean.TRUE.equals(s.stills())) {
            Path dir = out.resolve(s.name());
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            a.addAll(List.of("-vf", vf, "-q:v", "2", dir.resolve("%03d.jpg").toString()));
        } else {
            a.addAll(List.of("-vf", vf + ":out_color_matrix=bt709:out_range=tv,format=yuv420p", "-c:v", "libx264", "-preset", "medium", "-crf", String.valueOf(crf),
                    "-g", "30", "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709",
                    out.resolve(s.name() + ".mp4").toString()));
        }
        return a;
    }

    // ---------------------------------------------------------------- кадр записи

    @SubscribeEvent
    static void framePre(RenderFrameEvent.Pre event) {
        if (phase != Phase.RECORD || shot == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double t = (double) frame / fps;
        camT = t;
        place(mc.player, path.at(t));
        setResidual(mc, (frame * 20.0 / fps) % 1.0);
        List<Event> events = shot.events();
        while (events != null && nextEvent < events.size() && events.get(nextEvent).t() <= t + 1e-9) {
            fire(mc, events.get(nextEvent++), t);
        }
        moveMouse(mc, t);
        mc.options.hideGui = true;
        mc.getToasts().clear();
        preAt = System.nanoTime();
    }

    @SubscribeEvent
    static void framePost(RenderFrameEvent.Post event) {
        if (phase != Phase.RECORD || shot == null) return;
        Minecraft mc = Minecraft.getInstance();
        long rendered = System.nanoTime();
        nsRender += rendered - preAt;
        if (!mc.levelRenderer.hasRenderedAllSections() && retry++ < MAX_RETRY) {
            retries++;
            return;
        }
        retry = 0;
        try {
            encoder.push();
            nsRead += System.nanoTime() - rendered;
        } catch (IOException | InterruptedException e) {
            LOG.error("[film] {}: ffmpeg", shot.name(), e);
            abortShot(mc);
            return;
        }
        frame++;
        if (frame >= frames) {
            endRecord(mc);
            return;
        }
        vnow = t0 + frame * 1_000_000_000L / fps;
        long waitAt = System.nanoTime();
        awaitServer(mc.getSingleplayerServer());
        nsServer += System.nanoTime() - waitAt;
    }

    /** Ждём, пока встроенный сервер отработает все тики до {@link #vnow} и встанет в ожидание следующего. */
    private static void awaitServer(MinecraftServer server) {
        if (server == null) return;
        long deadline = System.nanoTime() + 5_000_000_000L;
        try {
            Field next = field(MinecraftServer.class, "nextTickTimeNanos");
            while (next.getLong(server) <= vnow && System.nanoTime() < deadline) {
                LockSupport.parkNanos(20_000);
            }
            server.submit(() -> { }).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.warn("[film] сервер не догнал кадр {}: {}", frame, e.toString());
        }
        // пакеты тика доходят до клиента через локальный канал
        LockSupport.parkNanos(1_000_000);
    }

    private static void endRecord(Minecraft mc) {
        Shot s = shot;
        try {
            encoder.close();
        } catch (IOException | InterruptedException e) {
            LOG.error("[film] {}: ffmpeg не закрылся", s.name(), e);
        }
        encoder = null;
        stopClock();
        if (mc.screen != null) mc.player.closeContainer();
        cursorShown = false;
        LOG.info("[film] {}: готово; перерисовок {}, на кадр: рендер {} мс, чтение {} мс, сервер {} мс", s.name(), retries,
                nsRender / 1_000_000 / (frames + retries), nsRead / 1_000_000 / frames, nsServer / 1_000_000 / frames);
        shot = null;
        phase = null;
    }

    private static void abortShot(Minecraft mc) {
        if (encoder != null) {
            encoder.kill();
            encoder = null;
        }
        stopClock();
        cursorShown = false;
        if (mc.player != null && mc.screen != null) mc.player.closeContainer();
        shot = null;
        phase = null;
    }

    /** Возврат к реальному времени без скачка назад: часы продолжают идти от виртуального момента. */
    private static void stopClock() {
        if (!virtual) return;
        virtual = false;
        offset = System.nanoTime() - vnow;
        long off = offset;
        Util.timeSource = () -> System.nanoTime() - off;
    }

    private static void finish(Minecraft mc) {
        saved.restore(mc);
        Path file = mc.gameDirectory.toPath().resolve("film.json");
        try {
            Files.move(file, file.resolveSibling("film.done"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error("[film] не переименовать {}", file, e);
        }
        LOG.info("[film] все шоты сняты");
        queue = new ArrayDeque<>();
        if (Boolean.TRUE.equals(plan.quit())) mc.stop();
    }

    // ---------------------------------------------------------------- события шота

    private static void fire(Minecraft mc, Event e, double t) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (e.cmd() != null) server.execute(() -> run(server, e.cmd()));
        if (e.panel() != null) openPanel(server, mc.player.getUUID(), new BlockPos(e.panel()[0], e.panel()[1], e.panel()[2]));
        if (e.cursor() != null || e.at() != null) {
            double[] target = cursorTarget(mc.screen, e);
            if (target != null) {
                Screen sc = mc.screen;
                if (!cursorShown) {
                    fromX = sc.width * 0.82;
                    fromY = sc.height * 0.92;
                } else {
                    double[] cur = cursorAt(t);
                    fromX = cur[0];
                    fromY = cur[1];
                }
                toX = target[0];
                toY = target[1];
                c0 = t;
                c1 = t + (e.dur() != null ? e.dur() : 0.6);
                cursorShown = true;
            } else {
                LOG.warn("[film] {}: нет цели курсора {}", shot.name(), e.cursor());
            }
        }
        if (Boolean.TRUE.equals(e.click()) && mc.screen != null && cursorShown) {
            double[] c = cursorAt(t);
            mc.screen.mouseClicked(c[0], c[1], 0);
            mc.screen.mouseReleased(c[0], c[1], 0);
        }
        if (Boolean.TRUE.equals(e.close()) && mc.screen != null) {
            mc.player.closeContainer();
            cursorShown = false;
        }
    }

    /** Щит открывает сам блок на сервере, как по клику пустой рукой, — без проверки досягаемости клика. */
    private static void openPanel(IntegratedServer server, java.util.UUID id, BlockPos pos) {
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null) return;
            var level = sp.serverLevel();
            Vec3 d = sp.getEyePosition().subtract(Vec3.atCenterOf(pos));
            Direction face = Direction.getNearest(d.x, d.y, d.z);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
            level.getBlockState(pos).useWithoutItem(level, sp, hit);
        });
    }

    private static double[] cursorTarget(Screen sc, Event e) {
        if (sc == null) return null;
        if (e.at() != null) {
            int left = sc instanceof AbstractContainerScreen<?> acs ? acs.getGuiLeft() : 0;
            int top = sc instanceof AbstractContainerScreen<?> acs ? acs.getGuiTop() : 0;
            return new double[] {left + e.at()[0], top + e.at()[1]};
        }
        for (GuiEventListener l : sc.children()) {
            if (l instanceof AbstractWidget w && w.getMessage().getString().equalsIgnoreCase(e.cursor())) {
                return new double[] {w.getX() + w.getWidth() * 0.55, w.getY() + w.getHeight() * 0.6};
            }
        }
        return null;
    }

    private static double[] cursorAt(double t) {
        double u = c1 <= c0 ? 1 : Math.clamp((t - c0) / (c1 - c0), 0, 1);
        u = u * u * u * (u * (u * 6 - 15) + 10);
        // лёгкая дуга, как у руки с мышью
        double arc = Math.sin(Math.PI * u) * Math.hypot(toX - fromX, toY - fromY) * 0.08;
        return new double[] {fromX + (toX - fromX) * u + arc, fromY + (toY - fromY) * u - arc * 0.5};
    }

    /** Мышь игры там же, где нарисованный курсор: подсветка кнопок и подсказки приборов. */
    private static void moveMouse(Minecraft mc, double t) {
        if (!cursorShown || mc.screen == null) return;
        double[] c = cursorAt(t);
        Window w = mc.getWindow();
        try {
            setField(mc.mouseHandler, "xpos", c[0] * w.getScreenWidth() / w.getGuiScaledWidth());
            setField(mc.mouseHandler, "ypos", c[1] * w.getScreenHeight() / w.getGuiScaledHeight());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String[] ARROW = {
        "X..........", "XX.........", "XWX........", "XWWX.......", "XWWWX......", "XWWWWX.....", "XWWWWWX....",
        "XWWWWWWX...", "XWWWWWWWX..", "XWWWWWWWWX.", "XWWWWWXXXXX", "XWWXWWX....", "XWX.XWWX...", "XX..XWWX...",
        "X....XWWX..", ".....XWWX..", "......XX...",
    };

    @SubscribeEvent
    static void drawCursor(ScreenEvent.Render.Post event) {
        if (phase != Phase.RECORD || !cursorShown) return;
        double[] c = cursorAt(camT);
        GuiGraphics g = event.getGuiGraphics();
        // клетка курсора — 2 пикселя кадра 1080p (или 1080x1920) при любом размере рендера
        RenderTarget rt = Minecraft.getInstance().getMainRenderTarget();
        float cell = (float) (Math.min(rt.width, rt.height) / 540.0 / Minecraft.getInstance().getWindow().getGuiScale());
        g.pose().pushPose();
        g.pose().translate(c[0], c[1], 900);
        g.pose().scale(cell, cell, 1);
        for (int y = 0; y < ARROW.length; y++) {
            for (int x = 0; x < ARROW[y].length(); x++) {
                char ch = ARROW[y].charAt(x);
                if (ch != '.') g.fill(x, y, x + 1, y + 1, ch == 'X' ? 0xFF101010 : 0xFFFFFFFF);
            }
        }
        g.pose().popPose();
    }

    // ---------------------------------------------------------------- камера

    @SubscribeEvent
    static void fov(ViewportEvent.ComputeFov event) {
        if (shot == null || path == null) return;
        event.setFOV(path.at(camT).fov);
    }

    @SubscribeEvent
    static void angles(ViewportEvent.ComputeCameraAngles event) {
        if (shot == null || path == null) return;
        Pose p = path.at(camT);
        event.setYaw((float) p.yaw);
        event.setPitch((float) p.pitch);
        event.setRoll((float) p.roll);
    }

    private static void place(LocalPlayer pl, Pose p) {
        double y = p.y - pl.getEyeHeight();
        pl.setPos(p.x, y, p.z);
        pl.xo = pl.xOld = p.x;
        pl.yo = pl.yOld = y;
        pl.zo = pl.zOld = p.z;
        pl.setYRot((float) p.yaw);
        pl.yRotO = (float) p.yaw;
        pl.setXRot((float) p.pitch);
        pl.xRotO = (float) p.pitch;
        pl.setDeltaMovement(Vec3.ZERO);
    }

    private static void setResidual(Minecraft mc, double partial) {
        DeltaTracker timer = mc.getTimer();
        try {
            field(timer.getClass(), "deltaTickResidual").setFloat(timer, (float) partial);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static final class Pose {
        double x, y, z, yaw, pitch, fov, roll;
    }

    /** Кубический сплайн Эрмита по времени ключей: положение и точка взгляда, FOV и крен. */
    static final class Spline {
        private final List<Key> keys;
        private final String ease;
        private final double length;

        Spline(List<Key> keys) {
            if (keys == null || keys.isEmpty()) throw new IllegalArgumentException("в шоте нет ключей камеры");
            this.keys = keys;
            this.ease = shot != null && shot.ease() != null ? shot.ease() : "inOut";
            this.length = keys.get(keys.size() - 1).t();
        }

        double duration() {
            return length;
        }

        Pose at(double t) {
            double u = length <= 0 ? 0 : Math.clamp(t / length, 0, 1);
            u = switch (ease) {
                case "linear" -> u;
                case "in" -> u * u;
                case "out" -> 1 - (1 - u) * (1 - u);
                default -> u * u * (3 - 2 * u);
            };
            double tt = u * length;
            int n = keys.size();
            int i = 0;
            while (i < n - 2 && keys.get(i + 1).t() < tt) i++;
            Pose p = new Pose();
            double[] pos = new double[3], look = new double[3];
            for (int a = 0; a < 3; a++) {
                final int axis = a;
                pos[a] = hermite(i, tt, k -> k.pos()[axis]);
                look[a] = hermite(i, tt, k -> k.look()[axis]);
            }
            p.x = pos[0];
            p.y = pos[1];
            p.z = pos[2];
            double dx = look[0] - pos[0], dy = look[1] - pos[1], dz = look[2] - pos[2];
            p.yaw = Math.toDegrees(Math.atan2(-dx, dz));
            p.pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            p.fov = hermite(i, tt, k -> k.fov() != null ? k.fov() : 70.0);
            p.roll = hermite(i, tt, k -> k.roll() != null ? k.roll() : 0.0);
            return p;
        }

        private double hermite(int i, double t, java.util.function.ToDoubleFunction<Key> f) {
            int n = keys.size();
            if (n == 1) return f.applyAsDouble(keys.get(0));
            Key k0 = keys.get(i), k1 = keys.get(i + 1);
            double h = k1.t() - k0.t();
            if (h <= 0) return f.applyAsDouble(k1);
            double s = Math.clamp((t - k0.t()) / h, 0, 1);
            double p0 = f.applyAsDouble(k0), p1 = f.applyAsDouble(k1);
            double m0 = slope(i, f) * h, m1 = slope(i + 1, f) * h;
            double s2 = s * s, s3 = s2 * s;
            return (2 * s3 - 3 * s2 + 1) * p0 + (s3 - 2 * s2 + s) * m0 + (-2 * s3 + 3 * s2) * p1 + (s3 - s2) * m1;
        }

        /** Касательная в ключе: центральная разность по времени, на концах — односторонняя. */
        private double slope(int i, java.util.function.ToDoubleFunction<Key> f) {
            int n = keys.size();
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            double dt = keys.get(b).t() - keys.get(a).t();
            return dt <= 0 ? 0 : (f.applyAsDouble(keys.get(b)) - f.applyAsDouble(keys.get(a))) / dt;
        }
    }

    // ---------------------------------------------------------------- захват кадра

    /**
     * Кадр сжимается на GPU (blit с линейной фильтрацией: при ровно 2x это среднее 2x2), читается в пул буферов,
     * поток-писатель отдаёт его в stdin ffmpeg — через трубу идёт уже кадр итогового размера.
     */
    private static final class Encoder {
        private final Process process;
        private final RenderTarget source;
        private final TextureTarget small;
        private final int w, h;
        private final BlockingQueue<ByteBuffer> free = new ArrayBlockingQueue<>(3);
        private final BlockingQueue<ByteBuffer> full = new ArrayBlockingQueue<>(3);
        private final List<ByteBuffer> all = new ArrayList<>();
        private final Thread writer;
        private volatile IOException failure;
        private static final ByteBuffer END = ByteBuffer.allocate(0);

        Encoder(List<String> args, Path log, RenderTarget source, int w, int h) throws IOException {
            this.source = source;
            this.w = w;
            this.h = h;
            this.small = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            source.bindWrite(true);
            int bytes = w * h * 3;
            process = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            for (int i = 0; i < 3; i++) {
                ByteBuffer b = MemoryUtil.memAlloc(bytes);
                all.add(b);
                free.add(b);
            }
            OutputStream os = process.getOutputStream();
            WritableByteChannel ch = Channels.newChannel(os);
            writer = new Thread(() -> {
                try {
                    while (true) {
                        ByteBuffer b = full.take();
                        if (b == END) break;
                        b.rewind();
                        while (b.hasRemaining()) ch.write(b);
                        free.put(b);
                    }
                    os.close();
                } catch (IOException e) {
                    failure = e;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "film-encoder");
            writer.setDaemon(true);
            writer.start();
        }

        void push() throws IOException, InterruptedException {
            if (failure != null) throw failure;
            ByteBuffer b = free.poll(30, TimeUnit.SECONDS);
            if (b == null) throw new IOException("ffmpeg не принимает кадры");
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, small.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, source.width, source.height, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, small.frameBufferId);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            b.clear();
            GL11.glReadPixels(0, 0, w, h, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, b);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, source.frameBufferId);
            full.put(b);
        }

        void close() throws IOException, InterruptedException {
            full.put(END);
            writer.join();
            int code = process.waitFor();
            all.forEach(MemoryUtil::memFree);
            small.destroyBuffers();
            if (failure != null) throw failure;
            if (code != 0) throw new IOException("ffmpeg завершился с кодом " + code);
        }

        void kill() {
            process.destroyForcibly();
            writer.interrupt();
            small.destroyBuffers();
        }
    }

    // ---------------------------------------------------------------- настройки клиента на время съёмки

    /** Настройки и размер framebuffer до съёмки; на шот — свой размер, масштаб GUI, гамма и размытие фона меню. */
    private static final class Saved {
        boolean hideGui, bob, pause;
        int guiScale, fpsLimit, blur;
        double gamma;
        boolean vsync;

        static Saved capture(Minecraft mc) {
            Saved s = new Saved();
            var o = mc.options;
            s.hideGui = o.hideGui;
            s.bob = o.bobView().get();
            s.pause = o.pauseOnLostFocus;
            s.guiScale = o.guiScale().get();
            s.fpsLimit = o.framerateLimit().get();
            s.blur = o.menuBackgroundBlurriness().get();
            s.gamma = o.gamma().get();
            s.vsync = o.enableVsync().get();
            return s;
        }

        void apply(Minecraft mc, int[] size, Integer gui, Double gammaV, Integer blurV) {
            var o = mc.options;
            o.hideGui = true;
            o.bobView().set(false);
            o.pauseOnLostFocus = false;
            o.framerateLimit().set(260);
            o.enableVsync().set(false);
            o.gamma().set(gammaV != null ? gammaV : gamma);
            o.menuBackgroundBlurriness().set(blurV != null ? blurV : blur);
            o.guiScale().set(gui != null ? gui : 0);
            resize(mc, size[0], size[1]);
            // ванильный предел (ширина/320) не даёт крупный щит в вертикальном кадре — масштаб ставим сами
            if (gui != null && mc.getWindow().getGuiScale() != gui) {
                mc.getWindow().setGuiScale(gui);
                if (mc.screen != null) mc.screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            }
        }

        void restore(Minecraft mc) {
            var o = mc.options;
            o.hideGui = hideGui;
            o.bobView().set(bob);
            o.pauseOnLostFocus = pause;
            o.framerateLimit().set(fpsLimit);
            o.enableVsync().set(vsync);
            o.gamma().set(gamma);
            o.menuBackgroundBlurriness().set(blur);
            o.guiScale().set(guiScale);
            long win = mc.getWindow().getWindow();
            int[] fw = new int[1], fh = new int[1], ww = new int[1], wh = new int[1];
            GLFW.glfwGetFramebufferSize(win, fw, fh);
            GLFW.glfwGetWindowSize(win, ww, wh);
            setSize(mc.getWindow(), ww[0], wh[0], fw[0], fh[0]);
            mc.resizeDisplay();
        }

        /** Framebuffer нужного размера независимо от окна: окно показывает лишь его угол, кадр читается целиком. */
        private static void resize(Minecraft mc, int w, int h) {
            setSize(mc.getWindow(), w, h, w, h);
            mc.resizeDisplay();
        }

        private static void setSize(Window window, int w, int h, int fw, int fh) {
            try {
                setField(window, "width", w);
                setField(window, "height", h);
                setField(window, "framebufferWidth", fw);
                setField(window, "framebufferHeight", fh);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    // ---------------------------------------------------------------- помощники

    private static void run(MinecraftServer server, String cmd) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
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

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        field(target.getClass(), name).set(target, value);
    }
}
