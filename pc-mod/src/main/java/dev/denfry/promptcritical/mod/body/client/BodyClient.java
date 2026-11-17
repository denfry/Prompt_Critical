package dev.denfry.promptcritical.mod.body.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.denfry.promptcritical.sim.body.BodyProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Клиент тела игрока: кнопка «Тело персонажа» на титульном экране — профиль задаётся до входа в мир и хранится в
 * {@code config/promptcritical-body.json}; при первом входе в мир сервер открывает тот же экран, заполненный этим
 * профилем, и игрок подтверждает его.
 */
public final class BodyClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE = "promptcritical-body.json";
    private static final Gson GSON = new Gson();

    private BodyClient() {
    }

    /** Просьба сервера, пришедшая во время загрузки мира: экран откроется, когда мир показан. */
    private static BodyProfile pending;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BodyClient::titleButton);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> pending = null);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            if (pending != null && mc.player != null && mc.screen == null) {
                BodyProfile p = pending;
                pending = null;
                mc.setScreen(new BodyScreen(null, p, true));
            }
        });
    }

    private static void titleButton(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen title)) return;
        event.addListener(Button.builder(Component.translatable("promptcritical.body.title_button"),
                b -> Minecraft.getInstance().setScreen(new BodyScreen(title, stored(), false))).bounds(4, 4, 120, 20).build());
    }

    /** Сервер просит выбрать тело: экран заполнен сохранённым профилем, если он есть, иначе тем, что прислал сервер. */
    public static void open(BodyProfile fromServer) {
        BodyProfile saved = load();
        pending = saved != null ? saved : fromServer;
    }

    static BodyProfile stored() {
        BodyProfile p = load();
        return p != null ? p : BodyProfile.REFERENCE;
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }

    static BodyProfile load() {
        Path f = path();
        if (!Files.isRegularFile(f)) return null;
        try {
            JsonObject o = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            BodyProfile.Sex sex = "FEMALE".equals(o.get("sex").getAsString()) ? BodyProfile.Sex.FEMALE : BodyProfile.Sex.MALE;
            return BodyProfile.clamped(sex, o.get("height_cm").getAsInt(), o.get("mass_kg").getAsInt(), o.get("age").getAsInt());
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Cannot read {}: {}", f, e.toString());
            return null;
        }
    }

    static void save(BodyProfile p) {
        JsonObject o = new JsonObject();
        o.addProperty("sex", p.sex().name());
        o.addProperty("height_cm", p.heightCm());
        o.addProperty("mass_kg", p.massKg());
        o.addProperty("age", p.ageYears());
        try {
            Files.writeString(path(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Cannot write {}: {}", path(), e.toString());
        }
    }
}
