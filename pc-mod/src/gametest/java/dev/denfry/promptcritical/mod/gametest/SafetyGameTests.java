package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.safety.GasMaskFilterItem;
import dev.denfry.promptcritical.mod.safety.PpeService;
import dev.denfry.promptcritical.mod.safety.SafetyComponents;
import dev.denfry.promptcritical.sim.ops.Contaminant;
import dev.denfry.promptcritical.sim.ops.FilterType;
import dev.denfry.promptcritical.sim.ops.Ppe;
import dev.denfry.promptcritical.sim.ops.Scba;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Промышленная безопасность 13: СИЗ §13.7 на игроке. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SafetyGameTests {
    static final String LARGE = "gametest/empty_12x12x12";

    private SafetyGameTests() {
    }

    static FakePlayer bare(GameTestHelper h) {
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        for (EquipmentSlot s : EquipmentSlot.values()) p.setItemSlot(s, ItemStack.EMPTY);
        p.getInventory().clearContent();
        return p;
    }

    static void strip(FakePlayer p) {
        for (EquipmentSlot s : EquipmentSlot.values()) p.setItemSlot(s, ItemStack.EMPTY);
        p.getInventory().clearContent();
    }

    static ItemStack mask(FilterType type) {
        ItemStack m = new ItemStack(PcItems.GAS_MASK.get());
        m.set(SafetyComponents.FILTER_TYPE.get(), GasMaskFilterItem.filter(GasMaskFilterItem.of(type)));
        return m;
    }

    /** Противогаз с фильтром B: хлор ×1/50 и фильтр нагружается; CO, нехватка O₂ и удушающий газ проходят. */
    @GameTest(template = LARGE)
    public static void gasMaskStopsOnlyItsFilterGroup(GameTestHelper h) {
        FakePlayer p = bare(h);
        try {
            p.setItemSlot(EquipmentSlot.HEAD, mask(FilterType.B));
            double cl = PpeService.inhaled(p, Contaminant.INORGANIC_GAS, 1000, 0.209, 1);
            h.assertTrue(Math.abs(cl - 1000 / Ppe.GAS_MASK_FACTOR) < 1e-9, "chlorine under the mask " + cl);
            h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).get(SafetyComponents.FILTER_TYPE.get()).loaded() > 0, "filter not loaded");
            h.assertTrue(PpeService.inhaled(p, Contaminant.CARBON_MONOXIDE, 500, 0.209, 1) == 500, "B filter stopped CO");
            h.assertTrue(PpeService.inhaled(p, Contaminant.INORGANIC_GAS, 500, 0.15, 1) == 500, "mask protected at 15 % O2");
            h.assertTrue(PpeService.inhaled(p, Contaminant.ASPHYXIANT, 1e5, 0.209, 1) == 1e5, "mask stopped nitrogen");
            h.assertTrue(PpeService.aerosolFraction(p) == 1, "B filter stopped aerosol");
        } finally {
            strip(p);
        }
        h.succeed();
    }

    /** Каска с противогазом P во вставке: аэрозоль ×1/50, нагрузка пишется во вставку; наушники во вставке — уши закрыты. */
    @GameTest(template = LARGE)
    public static void hardHatCarriesTheMaskOrTheEarMuffs(GameTestHelper h) {
        FakePlayer p = bare(h);
        try {
            ItemStack hat = new ItemStack(PcItems.HARD_HAT.get());
            SafetyComponents.setInsert(hat, mask(FilterType.P));
            p.setItemSlot(EquipmentSlot.HEAD, hat);
            h.assertTrue(Math.abs(PpeService.aerosolFraction(p) - 1 / Ppe.GAS_MASK_FACTOR) < 1e-12, "aerosol through the hat mask");
            double pm = PpeService.inhaled(p, Contaminant.PARTICLES, 5000, 0.209, 60);
            h.assertTrue(Math.abs(pm - 100) < 1e-9, "dust under the hat mask " + pm);
            ItemStack insert = SafetyComponents.insert(p.getItemBySlot(EquipmentSlot.HEAD));
            h.assertTrue(insert.get(SafetyComponents.FILTER_TYPE.get()).loaded() > 0, "insert filter not loaded");
            h.assertFalse(PpeService.earsProtected(p), "a mask is not ear muffs");
            SafetyComponents.setInsert(hat, new ItemStack(PcItems.HEARING_PROTECTION.get()));
            h.assertTrue(PpeService.earsProtected(p), "ear muffs in the hat");
            h.assertTrue(dev.denfry.promptcritical.mod.acoustics.NoiseService.protectedEars(p), "noise service ignores the hat insert");
            double fall = PpeService.damageFactor(p, h.getLevel().damageSources().fallingBlock(p));
            h.assertTrue(Math.abs(fall * 0.75 - Ppe.HARD_HAT_FALLING) < 1e-9, "falling block factor " + fall);
        } finally {
            strip(p);
        }
        h.succeed();
    }

    /** Аппарат: изолирует (×1/10 000) при любом O₂, тратит 40 л/мин, пустой не защищает. */
    @GameTest(template = LARGE)
    public static void scbaIsolatesWhileItHasAir(GameTestHelper h) {
        FakePlayer p = bare(h);
        try {
            ItemStack scba = new ItemStack(PcItems.SCBA.get());
            scba.set(SafetyComponents.AIR_REMAINING.get(), Scba.AIR_L);
            p.setItemSlot(EquipmentSlot.CHEST, scba);
            h.assertTrue(PpeService.inhaled(p, Contaminant.ASPHYXIANT, 1e4, 0.05, 1) == 1, "SCBA did not isolate");
            PpeService.tick(p, 60);
            double air = p.getItemBySlot(EquipmentSlot.CHEST).get(SafetyComponents.AIR_REMAINING.get());
            h.assertTrue(Math.abs(air - (Scba.AIR_L - Scba.CONSUMPTION_LPM)) < 1e-6, "air after a minute " + air);
            p.getItemBySlot(EquipmentSlot.CHEST).set(SafetyComponents.AIR_REMAINING.get(), 0.0);
            h.assertFalse(PpeService.isolated(p), "empty SCBA isolates");
            h.assertTrue(PpeService.inhaled(p, Contaminant.INORGANIC_GAS, 100, 0.209, 1) == 100, "empty SCBA filters");
        } finally {
            strip(p);
        }
        h.succeed();
    }

    /** Костюм: огонь ×0,2, излучение ×0,1; привязь с фалом у опоры в 16 м держит падение, без фала — нет. */
    @GameTest(template = LARGE)
    public static void fireSuitAndHarnessWork(GameTestHelper h) {
        FakePlayer p = bare(h);
        try {
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(PcItems.FIRE_SUIT.get()));
            h.assertTrue(Math.abs(PpeService.damageFactor(p, h.getLevel().damageSources().inFire()) - Ppe.FIRE_SUIT_BURN) < 1e-9, "fire factor");
            h.assertTrue(PpeService.radiantFactor(p) == Ppe.FIRE_SUIT_RADIANT, "radiant factor");
            p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(5, 2, 5)));
            p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(PcItems.SAFETY_HARNESS.get()));
            h.assertFalse(PpeService.arrested(p), "harness without a lifeline");
            ItemStack line = new ItemStack(PcItems.LIFELINE.get());
            line.set(SafetyComponents.LIFELINE_ANCHOR.get(), GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new net.minecraft.core.BlockPos(5, 8, 5))));
            p.getInventory().setItem(0, line);
            h.assertTrue(PpeService.arrested(p), "clipped lifeline did not arrest");
            line.set(SafetyComponents.LIFELINE_ANCHOR.get(), GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new net.minecraft.core.BlockPos(5, 40, 5))));
            h.assertFalse(PpeService.arrested(p), "anchor beyond 16 m");
        } finally {
            strip(p);
        }
        h.succeed();
    }
}
