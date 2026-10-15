package dev.denfry.promptcritical.mod.content;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Упрощённый Markdown страниц справочника (11 §11.1.2): {@code # заголовок}, {@code ## подзаголовок}, {@code - пункт},
 * пустая строка — новый абзац; в строке — {@code **жирный**}, {@code *курсив*}, ссылка на главу
 * {@code [текст](promptcritical:i0/boiler)} и на предмет {@code [текст](item:minecraft:coal)} (наведение показывает
 * предмет). Строки абзаца склеиваются через пробел.
 */
public final class HandbookMarkdown {
    /** Префикс значения {@link ClickEvent} ссылки на главу. */
    public static final String ENTRY_LINK = "entry:";
    public static final int HEADING_COLOR = 0x1F3A5F, LINK_COLOR = 0x2A5DB0, ITEM_COLOR = 0x2E6B30;

    public enum Kind { HEADING, SUBHEADING, PARAGRAPH, BULLET, BREAK }

    public record Block(Kind kind, Component text) {
    }

    private HandbookMarkdown() {
    }

    public static List<Block> parse(String md) {
        List<Block> out = new ArrayList<>();
        StringBuilder para = new StringBuilder();
        for (String raw : md.split("\n", -1)) {
            String line = raw.strip();
            if (line.isEmpty()) {
                flush(out, para);
                if (!out.isEmpty() && out.getLast().kind() != Kind.BREAK) out.add(new Block(Kind.BREAK, Component.empty()));
            } else if (line.startsWith("## ")) {
                flush(out, para);
                out.add(new Block(Kind.SUBHEADING, inline(line.substring(3), Style.EMPTY.withBold(true).withColor(HEADING_COLOR))));
            } else if (line.startsWith("# ")) {
                flush(out, para);
                out.add(new Block(Kind.HEADING, inline(line.substring(2), Style.EMPTY.withBold(true).withColor(HEADING_COLOR))));
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                flush(out, para);
                out.add(new Block(Kind.BULLET, inline(line.substring(2), Style.EMPTY)));
            } else {
                if (!para.isEmpty()) para.append(' ');
                para.append(line);
            }
        }
        flush(out, para);
        while (!out.isEmpty() && out.getLast().kind() == Kind.BREAK) out.removeLast();
        return out;
    }

    private static void flush(List<Block> out, StringBuilder para) {
        if (para.isEmpty()) return;
        out.add(new Block(Kind.PARAGRAPH, inline(para.toString(), Style.EMPTY)));
        para.setLength(0);
    }

    /** Строка с выделениями и ссылками. */
    public static MutableComponent inline(String s, Style base) {
        MutableComponent out = Component.empty();
        StringBuilder run = new StringBuilder();
        boolean bold = false, italic = false;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '*' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                emit(out, run, base, bold, italic);
                bold = !bold;
                i += 2;
            } else if (c == '*') {
                emit(out, run, base, bold, italic);
                italic = !italic;
                i++;
            } else if (c == '[') {
                int close = s.indexOf("](", i);
                int end = close < 0 ? -1 : s.indexOf(')', close + 2);
                if (close < 0 || end < 0) {
                    run.append(c);
                    i++;
                    continue;
                }
                emit(out, run, base, bold, italic);
                out.append(link(s.substring(i + 1, close), s.substring(close + 2, end), style(base, bold, italic)));
                i = end + 1;
            } else {
                run.append(c);
                i++;
            }
        }
        emit(out, run, base, bold, italic);
        return out;
    }

    private static void emit(MutableComponent out, StringBuilder run, Style base, boolean bold, boolean italic) {
        if (run.isEmpty()) return;
        out.append(Component.literal(run.toString()).withStyle(style(base, bold, italic)));
        run.setLength(0);
    }

    private static Style style(Style base, boolean bold, boolean italic) {
        Style s = base;
        if (bold) s = s.withBold(true);
        if (italic) s = s.withItalic(true);
        return s;
    }

    private static Component link(String text, String target, Style style) {
        if (target.startsWith("item:")) {
            ResourceLocation id = ResourceLocation.tryParse(target.substring(5));
            Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
            Style s = style.withColor(ITEM_COLOR);
            if (item != Items.AIR) s = s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(new ItemStack(item))));
            return Component.literal(text).withStyle(s);
        }
        String entry = target.startsWith(ENTRY_LINK) ? target.substring(ENTRY_LINK.length()) : target;
        return Component.literal(text).withStyle(style.withColor(LINK_COLOR).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, ENTRY_LINK + entry)));
    }
}
