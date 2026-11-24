package dev.denfry.promptcritical.mod.compat;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Путевые точки Xaero's Minimap строкой чата (16 §16.5.1). Публичного API у Xaero нет, но миникарта читает системные
 * сообщения: строку {@code xaero-waypoint:…} она прячет и показывает вместо неё «сервер поделился путевой точкой
 * [Добавить]». Поэтому строка уходит отдельным системным сообщением (не в строку действий) и без другого текста: всё
 * после префикса Xaero разбирает как поля.
 *
 * <pre>
 * xaero-waypoint:&lt;имя&gt;:&lt;инициалы&gt;:&lt;x&gt;:&lt;y&gt;:&lt;z&gt;:&lt;цвет 0–15&gt;:false:0:Internal-&lt;измерение&gt;-waypoints
 * </pre>
 * Формат восстановлен сторонними проектами по декомпилированному {@code WaypointSharingHandler} Xaero и может
 * сломаться при его обновлении. Текстовые поля Xaero экранирует сам ({@code :} → {@code ^col^}, {@code -} →
 * {@code ^min^}, {@code _} → {@code -}, {@code *} → {@code ^ast^}); измерение — {@code overworld}, {@code the_nether},
 * {@code the_end} или {@code dim%<пространство>$<путь>} у модовых, через то же экранирование.
 *
 * <p>Xaero — клиентский мод: на выделенном сервере его не видно в {@link ModList}, поэтому ключ
 * {@code mod.compat.xaero_chat_waypoints}: {@code auto} — только когда Xaero загружен в этой же игре (одиночная игра,
 * хост LAN), {@code always} — сервер, чьи игроки играют с Xaero, {@code off} — никогда. Без Xaero строка видна в чате
 * как есть.
 */
public final class XaeroWaypoints {
    public static final String PREFIX = "xaero-waypoint:";
    /** Цвета Xaero 0–15 совпадают с цветами чата §0–§f. */
    public static final int RED = 12, GOLD = 6;

    private XaeroWaypoints() {
    }

    /** Отправить ли строку точек этому серверу. */
    public static boolean enabled() {
        ServerConfig.XaeroChat mode = ServerConfig.SPEC.isLoaded() ? ServerConfig.COMPAT_XAERO_CHAT_WAYPOINTS.get() : ServerConfig.XaeroChat.AUTO;
        return switch (mode) {
            case OFF -> false;
            case ALWAYS -> true;
            case AUTO -> ModList.get().isLoaded("xaerominimap") || ModList.get().isLoaded("xaerominimapfair");
        };
    }

    /** Предложить игроку путевую точку {@code name} в {@code pos}, если строки точек включены. */
    public static void offer(ServerPlayer player, String name, BlockPos pos, int color, ResourceKey<Level> dimension) {
        if (enabled()) player.sendSystemMessage(Component.literal(line(name, pos.getX(), pos.getY(), pos.getZ(), color,
                dimension.location().toString())));
    }

    /** Строка чата Xaero для точки; {@code dimensionId} — id измерения, например {@code minecraft:the_nether}. */
    public static String line(String name, int x, int y, int z, int color, String dimensionId) {
        return PREFIX + encode(name) + ':' + encode(initials(name)) + ':' + x + ':' + y + ':' + z + ':' + Math.floorMod(color, 16)
                + ":false:0:Internal-" + encode(dimension(dimensionId)) + "-waypoints";
    }

    /** Имя измерения у Xaero: ванильные по имени, модовые — по папке {@code dim%<пространство>$<путь>}. */
    static String dimension(String dimensionId) {
        int colon = dimensionId.indexOf(':');
        String ns = colon >= 0 ? dimensionId.substring(0, colon) : "minecraft", path = colon >= 0 ? dimensionId.substring(colon + 1) : dimensionId;
        if (ns.equals("minecraft") && (path.equals("overworld") || path.equals("the_nether") || path.equals("the_end"))) return path;
        return "dim%" + ns + "$" + path.replace('/', '%').replace('.', ',');
    }

    /** Буква значка: первая буква или цифра имени в верхнем регистре. */
    static String initials(String name) {
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) return new String(Character.toChars(cp)).toUpperCase(Locale.ROOT);
            i += Character.charCount(cp);
        }
        return name.isEmpty() ? "X" : new String(Character.toChars(name.codePointAt(0)));
    }

    /** Экранирование текстовых полей, как у Xaero: сначала двоеточия, потом минусы, подчёркивания и звёздочки. */
    static String encode(String text) {
        return text.replace(":", "^col^").replace("-", "^min^").replace("_", "-").replace("*", "^ast^");
    }
}
