package dev.denfry.promptcritical.mod.body.client;

import dev.denfry.promptcritical.mod.body.BodyPayloads;
import dev.denfry.promptcritical.sim.body.BodyProfile;
import dev.denfry.promptcritical.sim.body.ElectricShock;
import dev.denfry.promptcritical.sim.body.Physiology;
import java.util.Locale;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Выбор тела: пол, рост, масса, возраст и что из них следует — справа считается тут же, по тем же формулам, что
 * на сервере. На титульном экране профиль только сохраняется; в мире — отправляется серверу и закрепляется, и
 * закрыть экран, не выбрав, нельзя.
 */
final class BodyScreen extends Screen {
    private static final int COLUMN = 200, ROW = 24;

    private final Screen parent;
    private final boolean inWorld;
    private BodyProfile.Sex sex;
    private int height, mass, age;

    BodyScreen(Screen parent, BodyProfile start, boolean inWorld) {
        super(Component.translatable("promptcritical.body.title"));
        this.parent = parent;
        this.inWorld = inWorld;
        sex = start.sex();
        height = start.heightCm();
        mass = start.massKg();
        age = start.ageYears();
    }

    @Override
    protected void init() {
        int x = width / 2 - COLUMN - 10, y = 48;
        addRenderableWidget(CycleButton.<BodyProfile.Sex>builder(s -> Component.translatable(
                        "promptcritical.body.sex." + s.name().toLowerCase(Locale.ROOT)))
                .withValues(BodyProfile.Sex.values()).withInitialValue(sex)
                .create(x, y, COLUMN, 20, Component.translatable("promptcritical.body.sex"), (b, v) -> sex = v));
        addRenderableWidget(new IntSlider(x, y + ROW, "promptcritical.body.height", BodyProfile.MIN_HEIGHT_CM,
                BodyProfile.MAX_HEIGHT_CM, height, v -> height = v));
        addRenderableWidget(new IntSlider(x, y + 2 * ROW, "promptcritical.body.mass", BodyProfile.MIN_MASS_KG,
                BodyProfile.MAX_MASS_KG, mass, v -> mass = v));
        addRenderableWidget(new IntSlider(x, y + 3 * ROW, "promptcritical.body.age", BodyProfile.MIN_AGE,
                BodyProfile.MAX_AGE, age, v -> age = v));
        addRenderableWidget(Button.builder(Component.translatable(inWorld ? "promptcritical.body.confirm" : "gui.done"),
                b -> done()).bounds(x, y + 5 * ROW, COLUMN, 20).build());
    }

    private BodyProfile profile() {
        return BodyProfile.clamped(sex, height, mass, age);
    }

    private void done() {
        BodyProfile p = profile();
        BodyClient.save(p);
        if (inWorld) PacketDistributor.sendToServer(new BodyPayloads.Submit(p));
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !inWorld;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable(inWorld ? "promptcritical.body.hint_world"
                : "promptcritical.body.hint_title").withStyle(ChatFormatting.GRAY), width / 2, 30, 0xFFFFFF);
        BodyProfile p = profile();
        int x = width / 2 + 10, y = 52;
        line(g, x, y, "promptcritical.body.info.bmi", fmt(Physiology.bodyMassIndex(p), 1));
        line(g, x, y += 12, "promptcritical.body.info.blood", fmt(Physiology.bloodVolumeL(p), 2));
        line(g, x, y += 12, "promptcritical.body.info.bmr", fmt(Physiology.basalMetabolicW(p), 0));
        line(g, x, y += 12, "promptcritical.body.info.hitbox", fmt(Physiology.modelScale(p) * Physiology.DEFAULT_MODEL_HEIGHT_M, 2));
        line(g, x, y += 12, "promptcritical.body.info.stride", fmt(100 * (Physiology.strideSpeedFactor(p) - 1), 1));
        line(g, x, y += 12, "promptcritical.body.info.gas_rest", fmt(Physiology.gasClock(p, Physiology.Activity.REST), 2));
        line(g, x, y += 12, "promptcritical.body.info.gas_sprint", fmt(Physiology.gasClock(p, Physiology.Activity.SPRINT), 2));
        line(g, x, y += 12, "promptcritical.body.info.knockback", fmt(100 * Physiology.knockbackResistance(p), 0));
        line(g, x, y += 12, "promptcritical.body.info.cage", fmt(Physiology.loadedMassKg(p), 0));
        line(g, x, y += 12, "promptcritical.body.info.let_go", fmt(1000 * ElectricShock.letGoA(p.sex()), 1));
    }

    private void line(GuiGraphics g, int x, int y, String key, String value) {
        g.drawString(font, Component.translatable(key, value), x, y, 0xE0E0E0);
    }

    private static String fmt(double v, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", v);
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final String key;
        private final int min, max;
        private final IntConsumer sink;

        IntSlider(int x, int y, String key, int min, int max, int value, IntConsumer sink) {
            super(x, y, COLUMN, 20, Component.empty(), (value - min) / (double) (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.sink = sink;
            updateMessage();
        }

        private int current() {
            return Mth.clamp((int) Math.round(min + value * (max - min)), min, max);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key, current()));
        }

        @Override
        protected void applyValue() {
            sink.accept(current());
        }
    }
}
