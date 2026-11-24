package dev.denfry.promptcritical.mod.manufacturing;

import static dev.denfry.promptcritical.mod.panel.PanelText.tr;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

/**
 * Щит сборочного стенда (18 §18.8.2): позиции спецификации по строкам, состояние каждой, оценка будущего паспорта,
 * обкатка. Названия деталей и машины приходят id предмета в реестре, имя переводит клиент.
 */
public final class AssemblyPanels {
    public static final int FLAGS = 0, C_SPEC_INDEX = 1, C_SPEC_COUNT = 2, C_OUTPUT = 3, C_SCORE = 4, C_IMBALANCE_X10 = 5,
            C_WEAR_X100 = 6, C_RUN_PCT = 7;
    public static final int P_ITEM = 8, P_COUNT = P_ITEM + AssemblyStandBlockEntity.POSITIONS,
            P_STATE = P_COUNT + AssemblyStandBlockEntity.POSITIONS, CHANNELS = P_STATE + AssemblyStandBlockEntity.POSITIONS;
    public static final int F_RUNNING = 0, F_READY = 1, F_TIGHT = 2, F_BALANCED = 3;
    public static final int BUTTON_PREV = 0, BUTTON_NEXT = 1, BUTTON_ASSEMBLE = 2;

    private static final Tr SPEC = tr("assembly.spec", "Spec %s of %s: %s", "Спецификация %s из %s: %s"),
            NO_SPEC = tr("assembly.no_spec", "No machine selected: pick one with the arrows", "Машина не выбрана: выберите стрелками"),
            PREV = tr("assembly.prev", "◀", "◀"), NEXT = tr("assembly.next", "▶", "▶"),
            ASSEMBLE = tr("assembly.assemble", "Assemble", "Собрать"),
            ASSEMBLE_TIP = tr("assembly.assemble_tip",
                    "Takes the parts, balances the rotor by hand on knife edges (G16) and starts the run-in",
                    "Забирает детали, балансирует ротор вручную на ножах (G16) и начинает обкатку"),
            SCORE = tr("assembly.score", "Passport %s/100 · bearing wear ×%s · vibration %s mm/s",
                    "Паспорт %s/100 · износ подшипника ×%s · вибрация %s мм/с"),
            NOT_READY = tr("assembly.preview_none", "Fill every position to see the passport", "Заполните все позиции, чтобы увидеть паспорт"),
            RUN_IN = tr("assembly.run_in", "Run-in", "Обкатка"),
            READY = tr("assembly.ready", "Fits", "Посадки"),
            TIGHT = tr("assembly.tight", "Tightening", "Затяжка"),
            BALANCED = tr("assembly.balanced", "Balance", "Балансировка"),
            PROBLEM = tr("assembly.problem", "%s ×%s: %s", "%s ×%s: %s"),
            OUTPUT = tr("assembly.output", "Assembled machine", "Собранная машина"),
            PART = tr("assembly.part_slot", "Part for this position", "Деталь для этой позиции");
    /** Состояния позиции в порядке {@link AssemblyStandBlockEntity.PositionState}. */
    private static final Tr[] STATES = {
            tr("assembly.state.unused", "", ""),
            tr("assembly.state.empty", "empty", "пусто"),
            tr("assembly.state.ok", "fits", "подходит"),
            tr("assembly.state.wrong_kind", "wrong part", "не та деталь"),
            tr("assembly.state.wrong_material", "wrong material", "не та марка"),
            tr("assembly.state.gate_defect", "rejected: crack or burnt", "брак: трещина или пережог"),
            tr("assembly.state.short", "not enough", "не хватает")};

    public static final PanelLayout STAND;

    /** Раскладка — фон assembly_stand.author.js (арт 27): плита с Т-пазами 3×3, выход в латунном кольце. */
    static {
        PanelLayout.Builder b = PanelLayout.builder("assembly_stand", 220, 152, CHANNELS)
                .art(PromptCritical.id("textures/gui/panel/assembly_stand.png"))
                .button(4, 4, 14, BUTTON_PREV, PREV.get(), null)
                .button(202, 4, 14, BUTTON_NEXT, NEXT.get(), null);
        for (int i = 0; i < AssemblyStandBlockEntity.POSITIONS; i++) {
            b.slot(16 + 20 * (i % 3), 30 + 20 * (i / 3), i, s -> true, PART.get());
        }
        STAND = b.output(100, 50, AssemblyStandBlockEntity.OUTPUT_SLOT, OUTPUT.get())
                .lamp(130, 30, FLAGS, F_READY, LampColor.GREEN, READY.get())
                .lamp(130, 50, FLAGS, F_TIGHT, LampColor.GREEN, TIGHT.get())
                .lamp(130, 70, FLAGS, F_BALANCED, LampColor.GREEN, BALANCED.get())
                .readout(10, 88, 200, AssemblyPanels::specLine, null)
                .bar(14, 108, 192, C_RUN_PCT, 1, 100, 0xFF60A060, RUN_IN.get(), null)
                .readout(10, 128, 140, AssemblyPanels::statusLine, null)
                .button(156, 126, 54, BUTTON_ASSEMBLE, ASSEMBLE.get(), ASSEMBLE_TIP.get())
                .build();
    }

    private AssemblyPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(STAND);
    }

    private static Component itemName(int rawId) {
        return rawId < 0 ? Component.empty() : BuiltInRegistries.ITEM.byId(rawId).getDescription();
    }

    private static Component specLine(PanelValues v) {
        return v.raw(C_SPEC_INDEX) <= 0 ? NO_SPEC.get()
                : SPEC.get(v.raw(C_SPEC_INDEX), v.raw(C_SPEC_COUNT), itemName(v.raw(C_OUTPUT)));
    }

    private static Component scoreLine(PanelValues v) {
        return v.raw(C_SCORE) < 0 ? NOT_READY.get()
                : SCORE.get(v.raw(C_SCORE), v.num(C_WEAR_X100, 100, 2), v.num(C_IMBALANCE_X10, 10, 1));
    }

    /** Первая позиция с замечанием, иначе оценка будущего паспорта. */
    private static Component statusLine(PanelValues v) {
        if (v.raw(C_SPEC_INDEX) <= 0) return Component.empty();
        for (int i = 0; i < AssemblyStandBlockEntity.POSITIONS; i++) {
            int state = v.raw(P_STATE + i);
            if (state > AssemblyStandBlockEntity.PositionState.OK.ordinal()
                    || state == AssemblyStandBlockEntity.PositionState.EMPTY.ordinal()) {
                return PROBLEM.get(itemName(v.raw(P_ITEM + i)), v.raw(P_COUNT + i), STATES[state].get());
            }
        }
        return scoreLine(v);
    }
}
