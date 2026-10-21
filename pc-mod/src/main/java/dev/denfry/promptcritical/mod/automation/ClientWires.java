package dev.denfry.promptcritical.mod.automation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Блоки с клеммами, загруженные на клиенте: их провода и клеммы рисует {@code WireRenderer}. Заполняется в
 * {@code onLoad}/{@code setRemoved} на клиентской стороне; на сервере пуст.
 */
public final class ClientWires {
    private static final Set<WiredDevice> LOADED = Collections.synchronizedSet(new LinkedHashSet<>());

    private ClientWires() {
    }

    public static void add(WiredDevice d) {
        LOADED.add(d);
    }

    public static void remove(WiredDevice d) {
        LOADED.remove(d);
    }

    /** Снимок для кадра (рендер на главном потоке клиента). */
    public static WiredDevice[] snapshot() {
        synchronized (LOADED) {
            return LOADED.toArray(new WiredDevice[0]);
        }
    }
}
