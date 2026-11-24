package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.radiation.ContaminationData;
import dev.denfry.promptcritical.mod.radiation.ContaminationService;
import dev.denfry.promptcritical.mod.radiation.LockerBlockEntity;
import dev.denfry.promptcritical.mod.radiation.RadGear;
import dev.denfry.promptcritical.mod.radiation.SanitaryBlocks;
import dev.denfry.promptcritical.mod.radiation.ZoneControllerBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.safety.PpeService;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.radiation.LeadApron;
import dev.denfry.promptcritical.sim.radiation.RadRespirator;
import dev.denfry.promptcritical.sim.radiation.RadZone;
import dev.denfry.promptcritical.sim.radiation.SurfaceContamination;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Радиационные СИЗ, дозиметрия и санпропускник 04b §4b.7, §4b.12 на игроке и в мире. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadProtectionGameTests {
    static final String LARGE = "gametest/empty_12x12x12";

    private RadProtectionGameTests() {
    }

    /** Полумаска держит аэрозоли, но не йод; йодный патрон закрывает и йод; пневмокостюм — только на шланге. */
    @GameTest(template = LARGE)
    public static void masksStopIodineOnlyWithACartridgeAndTheSuitOnlyOnAHose(GameTestHelper h) {
        FakePlayer p = SafetyGameTests.bare(h);
        try {
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PcItems.RESPIRATOR.get()));
            h.assertTrue(Math.abs(PpeService.aerosolFraction(p) - 0.1) < 1e-12, "half mask aerosol " + PpeService.aerosolFraction(p));
            h.assertTrue(RadGear.respirator(p).inhaledFraction(Emitter.I131) == 1, "half mask stopped iodine");

            ItemStack full = new ItemStack(PcItems.FULL_FACE_RESPIRATOR.get());
            full.set(RadGear.IODINE_CARTRIDGE.get(), RadRespirator.IODINE_CARTRIDGE_PHYS_S);
            p.setItemSlot(EquipmentSlot.HEAD, full);
            h.assertTrue(Math.abs(RadGear.respirator(p).inhaledFraction(Emitter.I131) - 1 / 50.0) < 1e-12, "cartridge did not stop iodine");

            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PcItems.PNEUMOSUIT_HELMET.get()));
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(PcItems.PNEUMOSUIT_JACKET.get()));
            p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(PcItems.PNEUMOSUIT_TROUSERS.get()));
            p.setItemSlot(EquipmentSlot.FEET, new ItemStack(PcItems.PNEUMOSUIT_BOOTS.get()));
            h.assertTrue(PpeService.aerosolFraction(p) == 1, "suit protects breathing without a hose");
            BlockPos port = h.absolutePos(new BlockPos(1, 2, 1));
            h.getLevel().setBlockAndUpdate(port, PcBlocks.AIR_SUPPLY_PORT.get().defaultBlockState());
            p.moveTo(port.getX() + 2.5, port.getY(), port.getZ() + 0.5);
            p.getItemBySlot(EquipmentSlot.CHEST).set(RadGear.AIR_HOSE.get(), GlobalPos.of(h.getLevel().dimension(), port));
            h.assertTrue(Math.abs(PpeService.aerosolFraction(p) - 1 / 2000.0) < 1e-12, "suit on a hose " + PpeService.aerosolFraction(p));
            p.moveTo(port.getX() + 30.5, port.getY(), port.getZ() + 0.5);
            h.assertTrue(!RadGear.hoseConnected(p), "hose reaches 30 m");
        } finally {
            SafetyGameTests.strip(p);
        }
        h.succeed();
    }

    /** Фартук пропускает мягкое γ на несколько процентов, жёсткое — почти целиком. */
    @GameTest(template = LARGE)
    public static void leadApronOnlyTamesSoftPhotons(GameTestHelper h) {
        FakePlayer p = SafetyGameTests.bare(h);
        try {
            h.assertTrue(RadGear.armorTransmission(p).applyAsDouble(0.1) == 1, "bare chest attenuates");
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(PcItems.LEAD_APRON.get()));
            h.assertTrue(RadGear.armorTransmission(p).applyAsDouble(0.1) < 0.05, "apron vs 100 keV");
            h.assertTrue(RadGear.armorTransmission(p).applyAsDouble(1.25) == LeadApron.transmission(1.25), "apron vs Co-60");
        } finally {
            SafetyGameTests.strip(p);
        }
        h.succeed();
    }

    /**
     * Комната 3×3×3 за каменными стенами с контроллером ЗКД и дверью: снаружи без ЭПД не пускает, с ЭПД пускает;
     * изнутри с грязной обувью не выпускает.
     */
    @GameTest(template = LARGE)
    public static void zoneDoorChecksTheDosimeterOnTheWayInAndShoesOnTheWayOut(GameTestHelper h) {
        var level = h.getLevel();
        // каменная коробка 5×5×5 с пустотой 3×3×3 внутри, проём двери в западной стене
        for (int x = 1; x <= 5; x++)
            for (int y = 1; y <= 5; y++)
                for (int z = 1; z <= 5; z++) {
                    boolean inner = x >= 2 && x <= 4 && y >= 2 && y <= 4 && z >= 2 && z <= 4;
                    h.setBlock(new BlockPos(x, y, z), inner ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState());
                }
        var door = PcBlocks.ZONE_ACCESS_DOOR.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        h.setBlock(new BlockPos(1, 2, 3), door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
        h.setBlock(new BlockPos(1, 3, 3), door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        // контроллер на восточной стене изнутри смотрит на запад, в комнату
        h.setBlock(new BlockPos(4, 3, 3), PcBlocks.ZONE_CONTROLLER.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        // onLoad блок-сущности, поставленной setBlock, NeoForge вызывает на следующем тике: контроллер ещё не в реестре зон
        h.runAfterDelay(1, () -> {
            FakePlayer p = SafetyGameTests.bare(h);
            try {
                ZoneControllerBlockEntity z = ZoneControllerBlockEntity.zoneAt(level, h.absolutePos(new BlockPos(2, 2, 3)));
                h.assertTrue(z != null && z.enclosed() && z.zone() == RadZone.CONTROLLED_SERVICED, "room not registered as a controlled zone");
                BlockPos lower = h.absolutePos(new BlockPos(1, 2, 3));
                BlockPos outside = h.absolutePos(new BlockPos(0, 2, 3));
                p.moveTo(outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5);
                h.assertTrue(SanitaryBlocks.check(level, lower, Direction.EAST, p).verdict() == RadZone.Verdict.NO_DOSIMETER, "let in without an EPD");
                p.getInventory().add(new ItemStack(PcItems.PERSONAL_DOSIMETER_EPD.get()));
                h.assertTrue(SanitaryBlocks.check(level, lower, Direction.EAST, p).verdict() == RadZone.Verdict.OK, "kept out with an EPD");
                BlockPos inside = h.absolutePos(new BlockPos(2, 2, 3));
                p.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
                ContaminationService.setShoes(p, SurfaceContamination.of(0, 50, 0.5));
                h.assertTrue(SanitaryBlocks.check(level, lower, Direction.EAST, p).verdict() == RadZone.Verdict.CONTAMINATED, "let out with dirty shoes");
                ContaminationService.setShoes(p, SurfaceContamination.NONE);
                h.assertTrue(SanitaryBlocks.check(level, lower, Direction.EAST, p).verdict() == RadZone.Verdict.OK, "kept in with clean shoes");
            } finally {
                SafetyGameTests.strip(p);
                ContaminationService.setShoes(p, SurfaceContamination.NONE);
            }
            h.succeed();
        });
    }

    /** Грязный шкаф забирает зонные вещи вместе с загрязнением, чистый меняет одежду местами. */
    @GameTest(template = LARGE)
    public static void lockersTakeTheZoneClothesAndSwapOutfits(GameTestHelper h) {
        h.setBlock(new BlockPos(2, 1, 2), PcBlocks.DIRTY_LOCKER.get().defaultBlockState());
        h.setBlock(new BlockPos(4, 1, 2), PcBlocks.CLEAN_LOCKER.get().defaultBlockState());
        FakePlayer p = SafetyGameTests.bare(h);
        try {
            ItemStack covers = new ItemStack(PcItems.BOOT_COVERS.get());
            ContaminationData.set(covers, SurfaceContamination.of(0, 20, 0.5));
            p.setItemSlot(EquipmentSlot.FEET, covers);
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(PcItems.PROTECTIVE_OVERALLS.get()));
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PcItems.HARD_HAT.get()));
            LockerBlockEntity dirty = (LockerBlockEntity) h.getBlockEntity(new BlockPos(2, 1, 2));
            h.assertTrue(dirty.depositZoneClothing(p) == 2, "dirty locker took the wrong items");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.FEET).isEmpty() && p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "zone clothes still on");
            h.assertTrue(!p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "hard hat taken");
            ItemStack back = dirty.takeLast();
            back = back.is(PcItems.BOOT_COVERS.get()) ? back : dirty.takeLast();
            h.assertTrue(ContaminationData.get(back).beta() > 1, "covers lost their contamination");

            LockerBlockEntity clean = (LockerBlockEntity) h.getBlockEntity(new BlockPos(4, 1, 2));
            clean.swapOutfit(p);
            h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "hard hat not stored");
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(PcItems.PROTECTIVE_OVERALLS.get()));
            clean.swapOutfit(p);
            h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).is(PcItems.HARD_HAT.get()), "hard hat not returned");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "overalls not stored");
        } finally {
            SafetyGameTests.strip(p);
        }
        h.succeed();
    }
}
