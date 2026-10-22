package dev.denfry.promptcritical.mod.film;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayouts;
import dev.denfry.promptcritical.mod.panel.PanelMenu;
import dev.denfry.promptcritical.mod.panel.client.PanelFit;
import dev.denfry.promptcritical.mod.panel.client.PanelScreen;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.slf4j.Logger;

/**
 * Проверка экранов щитов для разработки: {@code /pcgui lint} — наложения всех раскладок на текущем языке в лог и
 * {@code pcgui/lint_<язык>.txt}; {@code /pcgui shots} — для ru_ru и en_us открывает каждый щит, снимает скриншот в
 * {@code screenshots/pcgui/} и пишет отчёт; {@code /pcgui open <id>} — открыть щит с нулевыми показаниями.
 */
@EventBusSubscriber(modid = PromptCritical.MOD_ID, value = Dist.CLIENT)
public final class GuiCheck {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int SETTLE_TICKS = 4;
    private static final Deque<Runnable> steps = new ArrayDeque<>();
    private static int wait;
    private static boolean shoot;
    private static String shotName;
    private static String restoreLang;

    private GuiCheck() {
    }

    @SubscribeEvent
    static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pcgui")
                .then(Commands.literal("lint").executes(c -> {
                    int n = lint();
                    c.getSource().sendSystemMessage(Component.literal("pcgui: " + n + " problems"));
                    return n;
                }))
                .then(Commands.literal("dump").executes(c -> {
                    dump();
                    return 1;
                }))
                .then(Commands.literal("shots").executes(c -> {
                    shots(List.of("ru_ru", "en_us"));
                    return 1;
                }))
                .then(Commands.literal("open").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    String id = StringArgumentType.getString(c, "id");
                    Minecraft.getInstance().tell(() -> open(id));
                    return 1;
                }))));
    }

    static int lint() {
        Minecraft mc = Minecraft.getInstance();
        String lang = mc.getLanguageManager().getSelected();
        List<String> all = new ArrayList<>();
        for (PanelLayout l : PanelLayouts.all().values()) all.addAll(PanelFit.of(l, mc.font, title(l.id)).problems());
        all.forEach(p -> LOG.warn("[pcgui {}] {}", lang, p));
        try {
            Path dir = mc.gameDirectory.toPath().resolve("pcgui");
            Files.createDirectories(dir);
            Files.write(dir.resolve("lint_" + lang + ".txt"), all);
        } catch (IOException e) {
            LOG.error("pcgui lint", e);
        }
        return all.size();
    }

    /** Геометрия всех раскладок и ширины подписей на текущем языке — {@code pcgui/layouts_<язык>.json}. */
    static void dump() {
        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        JsonObject root = new JsonObject();
        for (PanelLayout l : PanelLayouts.all().values()) {
            JsonObject o = new JsonObject();
            o.addProperty("w", l.width);
            o.addProperty("h", l.height);
            o.addProperty("inventory", l.inventory);
            o.addProperty("title", font.width(title(l.id)));
            JsonArray els = new JsonArray();
            for (PanelLayout.Element e : l.elements) {
                JsonObject j = new JsonObject();
                j.addProperty("x", e.x());
                j.addProperty("y", e.y());
                switch (e) {
                    case PanelLayout.Dial d -> {
                        j.addProperty("k", "dial");
                        j.addProperty("size", d.size());
                        j.addProperty("tw", font.width(d.label()));
                        j.addProperty("t", d.label().getString());
                    }
                    case PanelLayout.Scope sc -> {
                        j.addProperty("k", "scope");
                        j.addProperty("tw", font.width(sc.label()));
                        j.addProperty("t", sc.label().getString());
                    }
                    case PanelLayout.Glass gl -> {
                        j.addProperty("k", "glass");
                        j.addProperty("h", gl.h());
                        j.addProperty("tw", font.width(gl.label()));
                        j.addProperty("t", gl.label().getString());
                    }
                    case PanelLayout.Bar b -> {
                        j.addProperty("k", "bar");
                        j.addProperty("w", b.w());
                        j.addProperty("tw", font.width(b.label()));
                        j.addProperty("t", b.label().getString());
                    }
                    case PanelLayout.Lamp lp -> {
                        j.addProperty("k", "lamp");
                        j.addProperty("tw", font.width(lp.label()));
                        j.addProperty("t", lp.label().getString());
                    }
                    case PanelLayout.Readout r -> {
                        j.addProperty("k", "readout");
                        j.addProperty("w", r.w());
                    }
                    case PanelLayout.Caption c -> {
                        j.addProperty("k", "caption");
                        j.addProperty("tw", font.width(c.text()));
                        j.addProperty("t", c.text().getString());
                    }
                    case PanelLayout.Button b -> {
                        j.addProperty("k", "button");
                        j.addProperty("w", b.w());
                        j.addProperty("tw", font.width(b.label()));
                        j.addProperty("t", b.label().getString());
                    }
                }
                els.add(j);
            }
            o.add("elements", els);
            JsonArray slots = new JsonArray();
            for (PanelLayout.SlotSpec sl : l.slots) {
                JsonObject j = new JsonObject();
                j.addProperty("x", sl.x());
                j.addProperty("y", sl.y());
                slots.add(j);
            }
            o.add("slots", slots);
            root.add(l.id, o);
        }
        try {
            Path dir = mc.gameDirectory.toPath().resolve("pcgui");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("layouts_" + mc.getLanguageManager().getSelected() + ".json"), root.toString());
        } catch (IOException e) {
            LOG.error("pcgui dump", e);
        }
    }

    static Component title(String id) {
        String key = "block." + PromptCritical.MOD_ID + "." + id;
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(id);
    }

    static void open(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), mc.player.registryAccess());
        buf.writeUtf(id);
        buf.writeBlockPos(BlockPos.ZERO);
        PanelMenu menu = new PanelMenu(0, mc.player.getInventory(), buf);
        mc.setScreen(new PanelScreen(menu, mc.player.getInventory(), title(id)));
    }

    static void shots(List<String> langs) {
        Minecraft mc = Minecraft.getInstance();
        restoreLang = mc.getLanguageManager().getSelected();
        steps.clear();
        for (String lang : langs) {
            steps.add(() -> language(lang));
            steps.add(() -> LOG.info("[pcgui] {}: {} problems", lang, lint()));
            steps.add(GuiCheck::dump);
            for (String id : PanelLayouts.all().keySet()) {
                steps.add(() -> open(id));
                steps.add(() -> {
                    shotName = "pcgui/" + lang + "_" + id + ".png";
                    shoot = true;
                });
            }
        }
        steps.add(() -> {
            mc.setScreen(null);
            language(restoreLang);
        });
        steps.add(() -> LOG.info("[pcgui] shots done"));
    }

    private static void language(String code) {
        Minecraft mc = Minecraft.getInstance();
        if (code.equals(mc.getLanguageManager().getSelected())) return;
        mc.getLanguageManager().setSelected(code);
        mc.options.languageCode = code;
        mc.reloadResourcePacks();
        wait = 40;
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (steps.isEmpty() || shoot || mc.getOverlay() != null) return;
        if (wait > 0) {
            wait--;
            return;
        }
        steps.poll().run();
        if (!shoot) wait = SETTLE_TICKS;
    }

    @SubscribeEvent
    static void frame(RenderFrameEvent.Post event) {
        if (!shoot) return;
        shoot = false;
        Minecraft mc = Minecraft.getInstance();
        try {
            Files.createDirectories(mc.gameDirectory.toPath().resolve("screenshots/pcgui"));
        } catch (IOException e) {
            LOG.error("pcgui shots", e);
        }
        Screenshot.grab(mc.gameDirectory, shotName, mc.getMainRenderTarget(), msg -> {
        });
    }
}
