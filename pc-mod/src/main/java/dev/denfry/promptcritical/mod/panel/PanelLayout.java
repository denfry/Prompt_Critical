package dev.denfry.promptcritical.mod.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Раскладка местного щита A0 (11 §11.5.4): стрелочные приборы, водомерное стекло, шкалы, сигнальные лампы, табло,
 * кнопки и слоты машины на крашеной панели над инвентарём игрока. Общая для сервера и клиента: сервер строит по ней
 * слоты меню и число каналов, клиент рисует. Показания приходят каналами — целыми, которые хост пишет в
 * {@link PanelHost#readPanel}; масштаб канала (сколько единиц в единице прибора) знает элемент.
 */
public final class PanelLayout {
    /** Высота инвентаря игрока под щитом с подписью и отступами. */
    public static final int INVENTORY_HEIGHT = 96;

    public final String id;
    /** Ширина и высота поля машины (над инвентарём). */
    public final int width, height;
    public final int channels;
    /** Инвентарь игрока под щитом: у щитов без слотов (насосы, диспетчерская) его нет. */
    public final boolean inventory;
    public final List<Element> elements;
    public final List<SlotSpec> slots;

    private PanelLayout(Builder b) {
        this.id = b.id;
        this.width = b.width;
        this.height = b.height;
        this.channels = b.channels;
        this.inventory = b.inventory;
        this.elements = List.copyOf(b.elements);
        this.slots = List.copyOf(b.slots);
    }

    /** Ширина отсека инвентаря справа от поля, когда щит с инвентарём снизу не помещается по высоте экрана. */
    public static final int SIDE_WIDTH = 176;

    public int imageHeight() {
        return imageHeight(false);
    }

    /** Высота экрана: инвентарь снизу или справа ({@code side}). */
    public int imageHeight(boolean side) {
        if (!inventory) return height;
        return side ? Math.max(height, INVENTORY_HEIGHT + 8) : height + INVENTORY_HEIGHT;
    }

    public int imageWidth(boolean side) {
        return inventory && side ? width + SIDE_WIDTH : width;
    }

    /** Левый край инвентаря игрока (слот 9×18 по центру). */
    public int inventoryX() {
        return inventoryX(false);
    }

    public int inventoryY() {
        return inventoryY(false);
    }

    public int inventoryX(boolean side) {
        return side ? width + (SIDE_WIDTH - 162) / 2 + 1 : (width - 162) / 2 + 1;
    }

    public int inventoryY(boolean side) {
        return side ? (imageHeight(true) - INVENTORY_HEIGHT) / 2 + 14 : height + 14;
    }

    /** Наибольший индекс слота машины + 1 — размер склада клиента. */
    public int itemSlots() {
        int n = 0;
        for (SlotSpec s : slots) n = Math.max(n, s.index() + 1);
        return n;
    }

    public static Builder builder(String id, int width, int height, int channels) {
        return new Builder(id, width, height, channels);
    }

    /** Подсказка по наведению; {@code null} — без подсказки. */
    public interface Tip extends Function<PanelValues, Component> {
    }

    public sealed interface Element permits Dial, Scope, Glass, Bar, Lamp, Readout, Caption, Button {
        int x();

        int y();
    }

    /**
     * Стрелочный прибор: шкала 270°, 10 делений от 0 до {@code max}; красная зона от {@code redFrom} (NaN — нет).
     * Канал делится на {@code scale}; стрелка догоняет показание с инерцией прибора.
     */
    public record Dial(int x, int y, boolean large, int ch, double scale, double max, double redFrom, int decimals,
            Component unit, Component label, @Nullable Tip tip) implements Element {
        public int size() {
            return large ? 64 : 48;
        }
    }

    /**
     * Синхроноскоп (07 §7.3.5): стрелка на полный оборот, канал — угол в десятых градуса (0 — «12 часов», по часовой —
     * генератор быстрее); вне синхронизации (канал {@link Integer#MIN_VALUE}) стрелка стоит внизу и не светится.
     */
    public record Scope(int x, int y, int ch, Component label, @Nullable Tip tip) implements Element {
        public static final int SIZE = 64;
        public static final int OFF = Integer.MIN_VALUE;
    }

    /** Водомерное стекло или указатель уровня: канал — доля в тысячных; метки — доли шкалы (NaN — нет). */
    public record Glass(int x, int y, int h, int ch, int color, double lowMark, double normalMark, Component label,
            @Nullable Tip tip) implements Element {
    }

    /** Горизонтальная шкала с подписью сверху: канал / scale из {@code max}. */
    public record Bar(int x, int y, int w, int ch, double scale, double max, int color, Component label,
            @Nullable Tip tip) implements Element {
    }

    /** Сигнальная лампа: горит, когда установлен бит {@code bit} канала; подпись справа. */
    public record Lamp(int x, int y, int ch, int bit, LampColor color, Component label) implements Element {
    }

    /** Табло: строка в тёмном окне; текст и цвет — по показаниям. */
    public record Readout(int x, int y, int w, Function<PanelValues, Component> text, @Nullable Tip tip) implements Element {
    }

    /** Надпись краской по панели. */
    public record Caption(int x, int y, Component text) implements Element {
    }

    /** Кнопка щита: {@code id} уходит хосту ({@link PanelHost#onPanelButton}). */
    public record Button(int x, int y, int w, int id, Component label, @Nullable Component tip) implements Element {
    }

    public enum LampColor { GREEN, RED, AMBER, WHITE }

    /** Слот машины: индекс в складе хоста, выход (класть нельзя), что принимает (проверяется и на клиенте). */
    public record SlotSpec(int x, int y, int index, boolean output, Predicate<ItemStack> accepts, Component tip) {
    }

    public static final class Builder {
        private final String id;
        private final int width, height, channels;
        private final List<Element> elements = new ArrayList<>();
        private final List<SlotSpec> slots = new ArrayList<>();
        private boolean inventory = true;

        private Builder(String id, int width, int height, int channels) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.channels = channels;
        }

        public Builder dial(int x, int y, boolean large, int ch, double scale, double max, double redFrom, int decimals,
                Component unit, Component label, @Nullable Tip tip) {
            elements.add(new Dial(x, y, large, ch, scale, max, redFrom, decimals, unit, label, tip));
            return this;
        }

        public Builder scope(int x, int y, int ch, Component label, @Nullable Tip tip) {
            elements.add(new Scope(x, y, ch, label, tip));
            return this;
        }

        public Builder glass(int x, int y, int h, int ch, int color, double lowMark, double normalMark, Component label,
                @Nullable Tip tip) {
            elements.add(new Glass(x, y, h, ch, color, lowMark, normalMark, label, tip));
            return this;
        }

        public Builder bar(int x, int y, int w, int ch, double scale, double max, int color, Component label, @Nullable Tip tip) {
            elements.add(new Bar(x, y, w, ch, scale, max, color, label, tip));
            return this;
        }

        public Builder lamp(int x, int y, int ch, int bit, LampColor color, Component label) {
            elements.add(new Lamp(x, y, ch, bit, color, label));
            return this;
        }

        public Builder readout(int x, int y, int w, Function<PanelValues, Component> text, @Nullable Tip tip) {
            elements.add(new Readout(x, y, w, text, tip));
            return this;
        }

        public Builder caption(int x, int y, Component text) {
            elements.add(new Caption(x, y, text));
            return this;
        }

        public Builder button(int x, int y, int w, int id, Component label, @Nullable Component tip) {
            elements.add(new Button(x, y, w, id, label, tip));
            return this;
        }

        public Builder slot(int x, int y, int index, Predicate<ItemStack> accepts, Component tip) {
            slots.add(new SlotSpec(x, y, index, false, accepts, tip));
            return this;
        }

        public Builder output(int x, int y, int index, Component tip) {
            slots.add(new SlotSpec(x, y, index, true, s -> false, tip));
            return this;
        }

        /** Щит только с приборами и кнопками — без инвентаря игрока. */
        public Builder noInventory() {
            inventory = false;
            return this;
        }

        public PanelLayout build() {
            if (!inventory && !slots.isEmpty()) throw new IllegalStateException(id + ": slots need the player inventory");
            return new PanelLayout(this);
        }
    }
}
