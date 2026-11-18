package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.ops.Permits;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.safety.ConfinedSpaces;
import dev.denfry.promptcritical.mod.safety.ManwayBlockEntity;
import dev.denfry.promptcritical.mod.safety.PatrolCheckpointBlockEntity;
import dev.denfry.promptcritical.mod.safety.PatrolReaderItem;
import dev.denfry.promptcritical.mod.safety.Patrols;
import dev.denfry.promptcritical.mod.safety.RescueTripodBlockEntity;
import dev.denfry.promptcritical.sim.ops.ConfinedSpaceAtmosphere;
import dev.denfry.promptcritical.mod.safety.GasMaskFilterItem;
import dev.denfry.promptcritical.mod.safety.PpeService;
import dev.denfry.promptcritical.mod.safety.SafetyComponents;
import dev.denfry.promptcritical.sim.ops.Contaminant;
import dev.denfry.promptcritical.sim.ops.FilterType;
import dev.denfry.promptcritical.sim.ops.PatrolRound;
import dev.denfry.promptcritical.sim.ops.Ppe;
import dev.denfry.promptcritical.sim.ops.Scba;
import net.minecraft.core.BlockPos;
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

    /** Ёмкость 3×2×3 из камня с люком в стене: 18 м³ за люком — замкнутое пространство. */
    static BlockPos vessel(GameTestHelper h) {
        for (int x = 2; x <= 6; x++)
            for (int y = 1; y <= 4; y++)
                for (int z = 2; z <= 6; z++) {
                    boolean shell = x == 2 || x == 6 || y == 1 || y == 4 || z == 2 || z == 6;
                    h.setBlock(new BlockPos(x, y, z), shell ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                            : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
        BlockPos manway = new BlockPos(4, 2, 2);
        h.setBlock(manway, PcBlocks.MANWAY.get().defaultBlockState());
        return manway;
    }

    /** Люк находит объём, азот через штуцер вытесняет кислород, открытый люк с вентилятором проветривает. */
    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void manwayPurgeAndVentilation(GameTestHelper h) {
        BlockPos manway = vessel(h);
        h.startSequence().thenIdle(2).thenExecute(() -> {
            ManwayBlockEntity m = h.getBlockEntity(manway);
            m.rescan(h.getLevel());
            h.assertTrue(m.volume() == 18, "volume " + m.volume());
            m.fluids(null).fill(new net.neoforged.neoforge.fluids.FluidStack(PcFluids.NITROGEN.source.get(), 1000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }).thenIdle(140).thenExecute(() -> {
            ManwayBlockEntity m = h.getBlockEntity(manway);
            double o2 = m.atmosphere().fraction(ConfinedSpaceAtmosphere.Gas.O2);
            h.assertTrue(o2 < 0.207, "nitrogen did not displace oxygen: " + o2);
            Permits.Air air = Permits.air(h.getLevel(), h.absolutePos(new BlockPos(4, 2, 4)));
            h.assertTrue(Math.abs(air.o2() - o2) < 1e-6, "gas test does not see the vessel: " + air.o2());
            h.assertFalse(m.ventM3s() > 0, "closed hatch ventilates");
            m.setHatch(true);
            h.setBlock(new BlockPos(4, 2, 1), PcBlocks.PORTABLE_VENTILATOR.get().defaultBlockState().setValue(PcProperties.RUNNING, true));
        }).thenIdle(30).thenExecute(() -> {
            ManwayBlockEntity m = h.getBlockEntity(manway);
            double v = m.ventM3s();
            h.assertTrue(Math.abs(v - ConfinedSpaceAtmosphere.VENTILATOR_M3S - ConfinedSpaceAtmosphere.NATURAL_VENT_M3S) < 1e-9, "ventilation " + v);
        }).thenSucceed();
    }

    /** Внутри при 5 % O₂ — потеря сознания без аппарата, противогаз не помогает, аппарат спасает; тренога вытягивает. */
    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void lowOxygenFaintsAndTheTripodPullsOut(GameTestHelper h) {
        BlockPos manway = vessel(h);
        BlockPos tripod = new BlockPos(4, 1, 1);
        h.setBlock(tripod, PcBlocks.RESCUE_TRIPOD.get().defaultBlockState());
        FakePlayer in = bare(h);
        FakePlayer rescuer = FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "attendant"));
        h.startSequence().thenIdle(2).thenExecute(() -> {
            ManwayBlockEntity m = h.getBlockEntity(manway);
            m.rescan(h.getLevel());
            m.setHatch(true);
            m.setAtmosphere(new double[] {0.05, 0.95, 0, 0, 0, 0});
            h.assertTrue(m.enter(in), "entry refused in advisory mode");
            h.assertTrue(m.inside(in), "not inside after entry");
            in.setItemSlot(EquipmentSlot.HEAD, mask(FilterType.B));
            int[] st = ConfinedSpaces.breathe(in, m.atmosphere(), h.getLevel().getGameTime());
            h.assertTrue(st[0] == 4, "5 % O2 stage " + st[0]);
            ItemStack scba = new ItemStack(PcItems.SCBA.get());
            scba.set(SafetyComponents.AIR_REMAINING.get(), Scba.AIR_L);
            in.setItemSlot(EquipmentSlot.CHEST, scba);
            h.assertTrue(ConfinedSpaces.breathe(in, m.atmosphere(), h.getLevel().getGameTime())[0] == 0, "SCBA did not protect");
            in.setItemSlot(EquipmentSlot.LEGS, new ItemStack(PcItems.SAFETY_HARNESS.get()));
            ItemStack line = new ItemStack(PcItems.LIFELINE.get());
            line.set(SafetyComponents.LIFELINE_ANCHOR.get(), GlobalPos.of(h.getLevel().dimension(), h.absolutePos(tripod)));
            in.getInventory().setItem(0, line);
            rescuer.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 1, 0.5)));
            RescueTripodBlockEntity t = h.getBlockEntity(tripod);
            h.assertTrue(t.startWinch(rescuer, java.util.List.of(rescuer, in)), "winch did not find the tethered man");
        }).thenIdle(RescueTripodBlockEntity.WINCH_TICKS + 5).thenExecute(() -> {
            ManwayBlockEntity m = h.getBlockEntity(manway);
            h.assertFalse(m.inside(in), "still inside after the winch");
        }).thenExecute(() -> strip(in)).thenSucceed();
    }

    /** Обход §13.3.1: круг по порядку, просрочка зажигает лампу метки, на слух — изношенный подшипник вентилятора. */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void patrolRoundOverdueAndListening(GameTestHelper h) {
        BlockPos a = new BlockPos(2, 1, 2), b = new BlockPos(8, 1, 2), motor = new BlockPos(2, 2, 4), fan = new BlockPos(3, 2, 4);
        h.setBlock(a, PcBlocks.PATROL_CHECKPOINT.get().defaultBlockState());
        h.setBlock(b, PcBlocks.PATROL_CHECKPOINT.get().defaultBlockState());
        h.setBlock(motor, com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST));
        h.setBlock(fan, com.simibubi.create.AllBlocks.ENCASED_FAN.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST));
        h.runAfterDelay(1, () -> ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        FakePlayer p = bare(h);
        ItemStack reader = new ItemStack(PcItems.PATROL_READER.get());
        h.startSequence().thenIdle(20).thenExecute(() -> {
            PatrolCheckpointBlockEntity ca = h.getBlockEntity(a), cb = h.getBlockEntity(b);
            h.assertTrue(PatrolReaderItem.program(p, reader, h.absolutePos(a)), "first checkpoint");
            h.assertTrue(PatrolReaderItem.program(p, reader, h.absolutePos(b)), "second checkpoint");
            h.assertTrue(PatrolReaderItem.visit(p, reader, cb) == PatrolRound.Result.OUT_OF_ORDER, "started at the end");
            h.assertTrue(PatrolReaderItem.visit(p, reader, ca) == PatrolRound.Result.STARTED, "start");
            h.assertTrue(PatrolReaderItem.visit(p, reader, cb) == PatrolRound.Result.COMPLETE, "round");
            h.assertFalse(ca.overdue(), "fresh visit overdue");
            ca.setLastVisit(h.getLevel().getGameTime() - (long) (PatrolRound.PERIOD_S * PatrolRound.TICKS_PER_S) - 10);
            h.assertTrue(ca.overdue(), "overdue checkpoint");
            h.assertTrue(h.getBlockState(a).getValue(PcProperties.ALARM), "no alarm lamp");
            dev.denfry.promptcritical.mod.acoustics.MachineAcoustics.setBearingDamage(h.getLevel(), h.absolutePos(fan), 0.6);
            Patrols.Card card = Patrols.card(h.getLevel(), h.absolutePos(a));
            h.assertTrue(card.findings().contains(Patrols.Finding.BEARING_NOISE), "bearing not heard: " + card);
        }).thenExecute(() -> strip(p)).thenSucceed();
    }
}
