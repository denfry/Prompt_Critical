package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.radiation.DeconMethod;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Щит ультразвуковой ванны дезактивации: растворы, бак ЖРО, ход цикла, КД последней детали. */
public final class DeconPanels {
    public static final int WATER = 0, CITRIC = 1, KMNO4 = 2, WASTE = 3, PROGRESS = 4, LAST_DF = 5, WASTE_KBQ = 6, METHOD = 7,
            FLAGS = 8, CHANNELS = 9;
    public static final int F_RUN = 0, F_DRY = 1, F_FULL = 2;

    private static final Tr WATER_L = PanelText.tr("decon.water", "Water", "Вода"),
            CITRIC_L = PanelText.tr("decon.citric", "Citric", "Лимон."),
            KMNO4_L = PanelText.tr("decon.kmno4", "KMnO₄", "KMnO₄"),
            WASTE_L = PanelText.tr("decon.waste", "LRW", "ЖРО"),
            CYCLE = PanelText.tr("decon.cycle", "Cycle", "Цикл"),
            RUN = PanelText.tr("decon.run", "Washing", "Мойка"),
            DRY = PanelText.tr("decon.dry", "No solution", "Нет раствора"),
            FULL = PanelText.tr("decon.full", "Waste full", "ЖРО полон"),
            IN = PanelText.tr("decon.in", "Contaminated part or tool", "Загрязнённая деталь или инструмент"),
            OUT = PanelText.tr("decon.out", "Decontaminated", "После дезактивации"),
            IDLE = PanelText.tr("decon.idle", "No part washed yet · waste %s MBq", "Деталей ещё не мыли · в ЖРО %s МБк"),
            STATUS = PanelText.tr("decon.status", "Last part: DF %s in %s · waste %s MBq",
                    "Последняя деталь: КД %s в растворе «%s» · в ЖРО %s МБк");
    private static final Tr[] METHODS = {
            PanelText.tr("decon.m.water", "water", "вода"),
            PanelText.tr("decon.m.citric", "citric + oxalic", "лимонная + щавелевая"),
            PanelText.tr("decon.m.kmno4", "alkaline KMnO₄, then acid", "щелочной KMnO₄, затем кислота")};

    public static final PanelLayout BATH;

    static {
        PanelLayout.Builder b = PanelLayout.builder("ultrasonic_bath", 232, 128, CHANNELS)
                .glass(8, 24, 56, WATER, 0xFF4060C0, Double.NaN, Double.NaN, WATER_L.get(), null)
                .glass(30, 24, 56, CITRIC, 0xFFE8E0A0, Double.NaN, Double.NaN, CITRIC_L.get(), null)
                .glass(52, 24, 56, KMNO4, 0xFF8A2BA0, Double.NaN, Double.NaN, KMNO4_L.get(), null)
                .glass(80, 24, 56, WASTE, 0xFF8A8060, Double.NaN, Double.NaN, WASTE_L.get(), null)
                .bar(104, 24, 100, PROGRESS, 10, 100, 0xFF60C0E0, CYCLE.get(), null)
                .lamp(104, 40, FLAGS, F_RUN, LampColor.GREEN, RUN.get())
                .lamp(104, 54, FLAGS, F_DRY, LampColor.AMBER, DRY.get())
                .lamp(170, 40, FLAGS, F_FULL, LampColor.RED, FULL.get());
        for (int i = 0; i < UltrasonicBathBlockEntity.IN; i++)
            b.slot(104 + 18 * i, 72, i, s -> s.has(ContaminationData.TYPE.get()), IN.get());
        for (int i = 0; i < UltrasonicBathBlockEntity.OUT; i++) b.output(104 + 18 * i, 92, UltrasonicBathBlockEntity.IN + i, OUT.get());
        BATH = b.readout(8, 114, 216, DeconPanels::status, null).build();
    }

    private DeconPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(BATH);
    }

    private static Component status(PanelValues v) {
        String mbq = PanelText.num(v.raw(WASTE_KBQ) / 1000.0, 2);
        int m = v.raw(METHOD);
        if (m < 0) return IDLE.get(mbq);
        Tr name = m == DeconMethod.ULTRASONIC_PERMANGANATE.ordinal() ? METHODS[2]
                : m == DeconMethod.ULTRASONIC_CITRIC_OXALIC.ordinal() ? METHODS[1] : METHODS[0];
        return STATUS.get(PanelText.num(v.raw(LAST_DF) / 10.0, 1), name.get(), mbq);
    }
}
