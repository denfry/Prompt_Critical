package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.gear.ElectricGearItem;
import dev.denfry.promptcritical.mod.gear.ElectricGearService;
import dev.denfry.promptcritical.mod.item.ElectricGearData;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.gear.PoweredRespirator;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Электроснаряжение §15.8 на игроке: надетый респиратор без питания ослабляет облако в 50 раз, после шага батареи
 * вентилятор работает и защита — в 1000 раз; фильтр набирает поглощённый газ.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElectricGearGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private ElectricGearGameTests() {
    }

    @GameTest(template = LARGE)
    public static void respiratorFiltersTheCloudOnceTheBlowerRuns(GameTestHelper h) {
        FakePlayer p = TestPlayers.of(h);
        ItemStack mask = new ItemStack(PcItems.POWERED_RESPIRATOR.get());
        p.setItemSlot(EquipmentSlot.CHEST, mask);
        try {
            double unpowered = ElectricGearService.inhaledPpm(p, 1000, 1);
            h.assertTrue(Math.abs(unpowered - 1000 / PoweredRespirator.APF_UNPOWERED) < 1e-6, "unpowered mask: " + unpowered);
            ElectricGearService.tick(h.getLevel(), p);
            ItemStack worn = p.getItemBySlot(EquipmentSlot.CHEST);
            ElectricGearData d = ElectricGearItem.data(worn);
            h.assertTrue(d.powerW10() == (int) Math.round(PoweredRespirator.BLOWER_W * 10), "blower power: " + d.powerW10());
            double powered = ElectricGearService.inhaledPpm(p, 1000, 1);
            h.assertTrue(Math.abs(powered - 1000 / PoweredRespirator.APF_POWERED) < 1e-6, "powered mask: " + powered);
            h.assertTrue(ElectricGearItem.data(worn).filterMol() > 0, "filter did not load");
        } finally {
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }
        h.succeed();
    }
}
