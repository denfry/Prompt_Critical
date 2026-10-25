package dev.denfry.promptcritical.mod.film;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import dev.denfry.promptcritical.mod.PromptCritical;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Кадры для галереи (только dev-клиент): если в каталоге игры лежит {@code gallery.json}, после входа в одиночный
 * мир по очереди ставит камеру в каждый ракурс (наблюдатель, время суток), прячет HUD, при необходимости открывает
 * щит машины и сохраняет {@code screenshots/<name>.png}. В конце переименовывает файл в {@code gallery.done}.
 *
 * <pre>
 * [{"name": "boiler", "pos": [x, y, z], "look": [x, y, z], "time": 6000, "panel": [x, y, z]}]
 * </pre>
 * {@code pos} — глаза камеры, {@code look} — точка, куда смотреть; {@code panel} (необязательно) — клетка машины,
 * щит которой открыть (игрок переводится в творческий режим, иначе клик не дойдёт до блока).
 */
@EventBusSubscriber(modid = PromptCritical.MOD_ID, value = Dist.CLIENT)
public final class GalleryShots {
    private static final Logger LOG = LogUtils.getLogger();
    /** Тиков после входа в мир до первого кадра, на подгрузку чанков. */
    private static final int WARMUP = 200;
    /** Тиков на перестановку камеры, частицы и открытие щита перед снимком. */
    private static final int SETTLE = 60;

    record Shot(String name, double[] pos, double[] look, Integer time, int[] panel) {
    }

    private static Deque<Shot> queue;
    private static Shot current;
    private static boolean done;
    private static int waited;
    private static int inWorld;

    private GalleryShots() {
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (done) return;
        if (mc.level == null || mc.player == null || server == null) {
            inWorld = 0;
            return;
        }
        if (queue == null) {
            if (++inWorld < WARMUP) return;
            queue = load(mc.gameDirectory.toPath().resolve("gallery.json"));
            if (queue.isEmpty()) return;
            mc.options.hideGui = true;
        }
        if (current == null) {
            current = queue.poll();
            if (current == null) {
                finish(mc, server);
                return;
            }
            place(mc, server, current);
            waited = 0;
            return;
        }
        waited++;
        if (current.panel() != null && waited == SETTLE / 2) openPanel(mc, current.panel());
        // курсор в угол: подсказки щита не попадают в кадр
        if (current.panel() != null && waited == SETTLE - 5) GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 2, 2);
        if (waited < SETTLE) return;
        Screenshot.grab(mc.gameDirectory, current.name() + ".png", mc.getMainRenderTarget(), msg -> LOG.info("[gallery] {}", msg.getString()));
        if (mc.screen != null) mc.player.closeContainer();
        current = null;
    }

    private static Deque<Shot> load(Path file) {
        Deque<Shot> out = new ArrayDeque<>();
        if (!Files.exists(file)) return out;
        try {
            List<Shot> shots = new Gson().fromJson(Files.readString(file), new TypeToken<List<Shot>>() {}.getType());
            out.addAll(shots);
            LOG.info("[gallery] {} shots", out.size());
        } catch (IOException | RuntimeException e) {
            LOG.error("[gallery] cannot read {}", file, e);
        }
        return out;
    }

    private static void place(Minecraft mc, IntegratedServer server, Shot s) {
        double dx = s.look()[0] - s.pos()[0], dy = s.look()[1] - s.pos()[1], dz = s.look()[2] - s.pos()[2];
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        String name = mc.player.getGameProfile().getName();
        double eye = mc.player.getEyeHeight();
        String mode = s.panel() != null ? "creative" : "spectator";
        server.execute(() -> {
            var src = server.createCommandSourceStack().withSuppressedOutput();
            var cmd = server.getCommands();
            cmd.performPrefixedCommand(src, "gamemode " + mode + " " + name);
            if (s.time() != null) cmd.performPrefixedCommand(src, "time set " + s.time());
            cmd.performPrefixedCommand(src, String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f %.2f %.2f", name,
                    s.pos()[0], s.pos()[1] - eye, s.pos()[2], yaw, pitch));
        });
        LOG.info("[gallery] {}", s.name());
    }

    /** Щит открывает сервер по клику пустой рукой; клик — от имени игрока по центру ближайшей грани. */
    private static void openPanel(Minecraft mc, int[] p) {
        BlockPos pos = new BlockPos(p[0], p[1], p[2]);
        Vec3 d = mc.player.getEyePosition().subtract(Vec3.atCenterOf(pos));
        Direction face = Direction.getNearest(d.x, d.y, d.z);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }

    private static void finish(Minecraft mc, IntegratedServer server) {
        mc.options.hideGui = false;
        String name = mc.player.getGameProfile().getName();
        server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), "gamemode creative " + name));
        Path file = mc.gameDirectory.toPath().resolve("gallery.json");
        try {
            Files.move(file, file.resolveSibling("gallery.done"));
        } catch (IOException e) {
            LOG.error("[gallery] cannot rename {}", file, e);
        }
        LOG.info("[gallery] done");
        done = true;
    }
}
