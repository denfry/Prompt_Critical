package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.nuclear.CascadeBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.EnrichmentPanels;
import dev.denfry.promptcritical.mod.nuclear.Uf6Charge;
import dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.enrichment.CentrifugeHall;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Обогащение: баллоны UF₆ доливаются с усреднением изотопов и не принимают лишнего; каскад из секций центрифуг вокруг
 * пульта делает из природного UF₆ 4,5 % продукт в 30B и 0,25 % отвал в 48Y; разгон выше разрывной скорости разбивает
 * секции.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnrichmentGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos CASCADE = new BlockPos(6, 2, 6);

    private EnrichmentGameTests() {
    }

    @GameTest(template = LARGE, timeoutTicks = 40)
    public static void cylindersMixAndRefuseOverfill(GameTestHelper h) {
        ItemStack y48 = new ItemStack(PcItems.UF6_CYLINDER_48Y.get());
        h.assertTrue(Uf6CylinderItem.roomKg(y48) == 12_501, "empty 48Y room");
        Uf6CylinderItem.fill(y48, 1000, Uf6Charge.NATURAL_U235, Uf6Charge.NATURAL_U234);
        Uf6CylinderItem.fill(y48, 1000, 0.0025, 0.00001);
        Uf6Charge c = Uf6CylinderItem.charge(y48);
        h.assertTrue(Math.abs(c.uf6Kg() - 2000) < 1e-9, "masses add: " + c.uf6Kg());
        h.assertTrue(Math.abs(c.u235() - (0.00711 + 0.0025) / 2) < 1e-5, "isotopics average: " + c.u235());
        h.assertTrue(Math.abs(Uf6CylinderItem.roomKg(y48) - 10_501) < 1e-6, "room left");
        boolean over = false, rich = false;
        try {
            Uf6CylinderItem.fill(y48, 20_000, Uf6Charge.NATURAL_U235, Uf6Charge.NATURAL_U234);
        } catch (IllegalArgumentException e) {
            over = true;
        }
        try {
            Uf6CylinderItem.fill(new ItemStack(PcItems.UF6_CYLINDER_30B.get()), 10, 0.06, 0.0005);
        } catch (IllegalArgumentException e) {
            rich = true;
        }
        h.assertTrue(over, "overfill refused");
        h.assertTrue(rich, "30B refuses 6 % uranium");
        Uf6Charge taken = Uf6CylinderItem.take(y48, 500);
        h.assertTrue(Math.abs(taken.uf6Kg() - 500) < 1e-9 && Math.abs(Uf6CylinderItem.charge(y48).uf6Kg() - 1500) < 1e-9, "take");
        h.succeed();
    }

    private static void placeHall(GameTestHelper h, int sections) {
        h.setBlock(CASCADE, PcBlocks.CENTRIFUGE_CASCADE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos motor = CASCADE.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        int placed = 0;
        for (int x = 1; x <= 10 && placed < sections; x++)
            for (int z = 1; z <= 3 && placed < sections; z++, placed++)
                h.setBlock(new BlockPos(x, 2, z), PcBlocks.GAS_CENTRIFUGE.get().defaultBlockState());
    }

    private static void loadCylinders(CascadeBlockEntity c) {
        ItemStack feed = Uf6CylinderItem.fill(new ItemStack(PcItems.UF6_CYLINDER_48Y.get()), 2000, Uf6Charge.NATURAL_U235,
                Uf6Charge.NATURAL_U234);
        c.cylinders().setStackInSlot(EnrichmentPanels.SLOT_FEED, feed);
        c.cylinders().setStackInSlot(EnrichmentPanels.SLOT_PRODUCT, new ItemStack(PcItems.UF6_CYLINDER_30B.get()));
        c.cylinders().setStackInSlot(EnrichmentPanels.SLOT_TAILS, new ItemStack(PcItems.UF6_CYLINDER_48Y.get()));
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void cascadeEnrichesNaturalUranium(GameTestHelper h) {
        placeHall(h, 20);
        h.startSequence()
                .thenIdle(25)
                .thenExecute(() -> {
                    CascadeBlockEntity c = h.getBlockEntity(CASCADE);
                    loadCylinders(c);
                    c.hall().setDesign(CentrifugeHall.Design.CARBON);
                    c.hall().setSpeedFraction(0.8);
                    c.hall().setRunning(true);
                })
                .thenWaitUntil(() -> {
                    CascadeBlockEntity c = h.getBlockEntity(CASCADE);
                    h.assertTrue(c.machines() == 400, "machines counted: " + c.machines());
                    Uf6Charge p = Uf6CylinderItem.charge(c.cylinders().getStackInSlot(EnrichmentPanels.SLOT_PRODUCT));
                    Uf6Charge w = Uf6CylinderItem.charge(c.cylinders().getStackInSlot(EnrichmentPanels.SLOT_TAILS));
                    h.assertTrue(p.uf6Kg() > 0.01, "product " + p.uf6Kg() + " kg UF6");
                    h.assertTrue(Math.abs(p.u235() - 0.045) < 1e-6, "product 4.5 %: " + p.u235());
                    h.assertTrue(p.u234() > 3e-4 && p.u234() < 5e-4, "U-234 follows: " + p.u234());
                    h.assertTrue(w.uf6Kg() > 0.05 && Math.abs(w.u235() - 0.0025) < 1e-6, "tails " + w.uf6Kg() + " kg at " + w.u235());
                    h.assertTrue(Uf6CylinderItem.charge(c.cylinders().getStackInSlot(EnrichmentPanels.SLOT_FEED)).uf6Kg() < 2000, "feed drawn");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void overspeedWrecksTheSections(GameTestHelper h) {
        placeHall(h, 6);
        h.startSequence()
                .thenIdle(25)
                .thenExecute(() -> {
                    CascadeBlockEntity c = h.getBlockEntity(CASCADE);
                    loadCylinders(c);
                    c.hall().setDesign(CentrifugeHall.Design.CARBON);
                    c.hall().setSpeedFraction(1.05);
                    c.hall().setRunning(true);
                })
                .thenWaitUntil(() -> {
                    CascadeBlockEntity c = h.getBlockEntity(CASCADE);
                    int left = 0;
                    for (int x = 1; x <= 2; x++)
                        for (int z = 1; z <= 3; z++)
                            if (h.getBlockState(new BlockPos(x, 2, z)).is(PcBlocks.GAS_CENTRIFUGE.get())) left++;
                    h.assertTrue(left == 0, "burst rotors wreck every section: " + left + " left");
                    h.assertTrue(c.machines() == 0, "no machines left");
                })
                .thenSucceed();
    }
}
