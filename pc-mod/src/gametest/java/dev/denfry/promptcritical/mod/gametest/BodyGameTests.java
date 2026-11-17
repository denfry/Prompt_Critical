package dev.denfry.promptcritical.mod.gametest;

import com.mojang.authlib.GameProfile;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.body.BodyService;
import dev.denfry.promptcritical.sim.body.BodyProfile;
import dev.denfry.promptcritical.sim.body.Physiology;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Тело игрока: выбор один раз на мир, рост и масса — в атрибутах, масса со снаряжением — для клети. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyGameTests {
    private static final String SMALL = "gametest/empty_3x3x3";

    private BodyGameTests() {
    }

    private static FakePlayer player(GameTestHelper h) {
        return new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_body_" + System.nanoTime() % 100_000));
    }

    @GameTest(template = SMALL)
    public static void bodyIsChosenOnceAndShapesThePlayer(GameTestHelper h) {
        FakePlayer p = player(h);
        h.assertFalse(BodyService.body(p).confirmed(), "a new player has not chosen a body");
        h.assertTrue(BodyService.profile(p).equals(BodyProfile.REFERENCE), "until then the reference body applies");

        BodyProfile tall = new BodyProfile(BodyProfile.Sex.MALE, 198, 110, 35);
        h.assertTrue(BodyService.submit(p, tall), "first choice is accepted");
        h.assertTrue(BodyService.body(p).confirmed(), "and confirmed");
        double scale = p.getAttributeValue(Attributes.SCALE);
        h.assertTrue(Math.abs(scale - 1.1) < 1e-9, "198 cm scales the model to 1.1, got " + scale);
        h.assertTrue(p.getBbHeight() <= 2.0f + 1e-6f, "the hitbox still fits a 2-block roadway: " + p.getBbHeight());
        double kb = p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        h.assertTrue(Math.abs(kb - Physiology.knockbackResistance(tall)) < 1e-9, "110 kg resists knockback, got " + kb);
        h.assertTrue(BodyService.loadedMassKg(p) == 120, "a miner of 110 kg weighs 120 kg in the cage");

        BodyProfile other = new BodyProfile(BodyProfile.Sex.FEMALE, 160, 50, 25);
        h.assertFalse(BodyService.submit(p, other), "a confirmed body is not replaced by the client");
        h.assertTrue(BodyService.profile(p).equals(tall), "the first choice stays");

        BodyService.reset(p);
        h.assertFalse(BodyService.body(p).confirmed(), "an operator reset reopens the choice");
        h.assertTrue(BodyService.submit(p, other), "and a new body can be chosen");
        h.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 0, "a light body gets no knockback bonus");
        h.assertTrue(p.getAttributeValue(Attributes.SCALE) < 0.9, "160 cm is smaller than the default model");
        h.succeed();
    }
}
