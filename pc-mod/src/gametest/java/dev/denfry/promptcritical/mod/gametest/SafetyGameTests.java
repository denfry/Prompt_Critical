package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.ops.Permits;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.safety.ConfinedSpaces;
import dev.denfry.promptcritical.mod.safety.FlareIgnitionPanelBlockEntity;
import dev.denfry.promptcritical.mod.safety.FlareKnockoutDrumBlockEntity;
import dev.denfry.promptcritical.mod.safety.FlareSealDrumBlockEntity;
import dev.denfry.promptcritical.mod.safety.Flares;
import dev.denfry.promptcritical.mod.safety.ManwayBlockEntity;
import dev.denfry.promptcritical.mod.safety.WaterIntakeBlockEntity;
import dev.denfry.promptcritical.mod.safety.SignageBlockEntity;
import dev.denfry.promptcritical.mod.safety.VentStackBlockEntity;
import dev.denfry.promptcritical.mod.safety.TurningGearBlockEntity;
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

    /**
     * Факел §13.4.3: щит розжига берёт оголовок, горелки на топливном газе зажигают сброс, без пара — дым; полный сепаратор
     * капель — горящий дождь; мало воды в гидрозатворе — проскок пламени, горелки гаснут, сброс уходит несгоревшим.
     */
    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void flareSystemBurnsSmokesRainsAndFlashesBack(GameTestHelper h) {
        BlockPos tip = new BlockPos(6, 4, 6), panel = new BlockPos(2, 1, 2), ko = new BlockPos(6, 1, 2), seal = new BlockPos(9, 1, 6);
        h.setBlock(tip, PcBlocks.FLARE_TIP.get().defaultBlockState());
        h.setBlock(panel, PcBlocks.FLARE_IGNITION_PANEL.get().defaultBlockState());
        h.setBlock(ko, PcBlocks.FLARE_KNOCKOUT_DRUM.get().defaultBlockState());
        h.setBlock(seal, PcBlocks.FLARE_SEAL_DRUM.get().defaultBlockState());
        BlockPos tipAbs = h.absolutePos(tip);
        h.onEachTick(() -> Flares.offer(h.getLevel(), tipAbs, 0.025));
        FlareIgnitionPanelBlockEntity.rainFire = false;
        h.startSequence().thenIdle(2).thenExecute(() -> {
            FlareIgnitionPanelBlockEntity p = h.getBlockEntity(panel);
            p.fluids(null).fill(new net.neoforged.neoforge.fluids.FluidStack(PcFluids.SALES_GAS.source.get(), 100),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            h.assertTrue(p.ignite(), "pilots did not light");
            ((FlareSealDrumBlockEntity) h.getBlockEntity(seal)).setWater(1000);
        }).thenIdle(50).thenExecute(() -> {
            FlareIgnitionPanelBlockEntity p = h.getBlockEntity(panel);
            h.assertTrue(Math.abs(p.flowNm3s() - 0.5) < 1e-6, "relief " + p.flowNm3s());
            h.assertTrue(p.state().heatReleaseW() > 0 && !p.state().flameOut(), "flare not burning " + p.state());
            h.assertTrue(p.state().smokeIndex() == 1, "smokeless without steam " + p.state());
            h.assertTrue(h.getBlockState(tip).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT), "tip not lit");
            h.assertFalse(p.raining(), "rain with an empty knock-out drum");
            ((FlareKnockoutDrumBlockEntity) h.getBlockEntity(ko)).setLiquid(FlareKnockoutDrumBlockEntity.CAPACITY_MB);
        }).thenIdle(45).thenExecute(() -> {
            FlareIgnitionPanelBlockEntity p = h.getBlockEntity(panel);
            h.assertTrue(p.raining(), "full knock-out drum did not carry liquid over");
            ((FlareSealDrumBlockEntity) h.getBlockEntity(seal)).setWater(100);
        }).thenIdle(45).thenExecute(() -> {
            FlareIgnitionPanelBlockEntity p = h.getBlockEntity(panel);
            h.assertTrue(p.pilots() == 0, "flashback did not put the pilots out");
        }).thenIdle(25).thenExecute(() -> {
            FlareIgnitionPanelBlockEntity p = h.getBlockEntity(panel);
            h.assertTrue(p.state().flameOut() && p.state().ch4UnburntKgS() > 0, "no unburnt release without pilots " + p.state());
            h.assertFalse(h.getBlockState(tip).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT), "tip lit without pilots");
        }).thenExecute(() -> FlareIgnitionPanelBlockEntity.rainFire = true).thenSucceed();
    }

    /**
     * Валоповорот 03a §3a.7.6: горячая остановленная турбина без валоповорота выгибается, дисбаланс в модели вибрации
     * растёт; вал Create на валоповороте проворачивает ротор, прогиб уходит.
     */
    @GameTest(template = LARGE, timeoutTicks = 300)
    public static void turningGearPreventsRotorBow(GameTestHelper h) {
        BlockPos turbine = new BlockPos(5, 2, 5), gear = new BlockPos(6, 2, 5), motor = new BlockPos(7, 2, 5);
        h.setBlock(turbine, PcBlocks.STEAM_TURBINE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH));
        h.setBlock(gear, PcBlocks.TURBINE_TURNING_GEAR.get().defaultBlockState());
        double[] u0 = new double[2];
        h.startSequence().thenIdle(5).thenExecute(() -> {
            TurningGearBlockEntity g = h.getBlockEntity(gear);
            u0[0] = dev.denfry.promptcritical.mod.acoustics.MachineAcoustics.imbalance(h.getLevel(), h.absolutePos(turbine));
            g.setRotor(0, 0); // только что остановлена, горячая
        }).thenIdle(65).thenExecute(() -> {
            TurningGearBlockEntity g = h.getBlockEntity(gear);
            h.assertTrue(g.rotor().bow() > 0, "hot stopped rotor did not bow");
            h.assertFalse(g.turning(), "turning without a shaft");
            double u = dev.denfry.promptcritical.mod.acoustics.MachineAcoustics.imbalance(h.getLevel(), h.absolutePos(turbine));
            h.assertTrue(Math.abs(u - u0[0] * g.rotor().vibrationFactor()) < 1e-3 * u, "imbalance " + u + " vs " + u0[0]);
            u0[1] = g.rotor().bow();
            h.setBlock(motor, com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.WEST));
        }).thenIdle(2).thenExecute(() -> ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) h.getBlockEntity(motor))
                .generatedSpeed.setValue(32)).thenIdle(65).thenExecute(() -> {
            TurningGearBlockEntity g = h.getBlockEntity(gear);
            h.assertTrue(g.turning(), "the shaft does not turn the rotor");
            h.assertTrue(g.rotor().bow() < u0[1], "bow did not straighten: " + g.rotor().bow() + " vs " + u0[1]);
        }).thenSucceed();
    }

    /**
     * Вытяжка зала 03a §3a.9.3: выброс рядом уходит через HEPA и уголь, выходит с верха трубы, фильтры копят активность.
     * Труба — два звена 3 × 3 × 3 (основание и оголовок), фильтры — вплотную к основанию.
     */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void ventStackFiltersHallReleases(GameTestHelper h) {
        BlockPos base = new BlockPos(5, 2, 5);
        h.setBlock(base, PcBlocks.VENT_STACK.get().defaultBlockState());
        h.setBlock(base.above(3), PcBlocks.VENT_STACK.get().defaultBlockState());
        h.setBlock(base.east(2), PcBlocks.HEPA_FILTER.get().defaultBlockState());
        h.setBlock(base.west(2), PcBlocks.CHARCOAL_FILTER.get().defaultBlockState());
        h.startSequence().thenIdle(25).thenExecute(() -> {
            VentStackBlockEntity v = h.getBlockEntity(base);
            v.survey(h.getLevel());
            h.assertTrue(v.bank().hepaStages() == 1 && v.bank().charcoalStages() == 1, "filters " + v.bank().hepaStages() + "/" + v.bank().charcoalStages());
            // вытяжка включена только на время этого шага: испытания в общем мире, чужие выбросы в 16 блоках она бы забрала
            v.setExhaust(true);
            try {
                net.minecraft.world.phys.Vec3 reactor = h.absoluteVec(new net.minecraft.world.phys.Vec3(9.5, 2, 9.5));
                h.assertTrue(VentStackBlockEntity.capture(h.getLevel(), reactor) == v, "the hall release is not captured");
                h.assertTrue(Math.abs(v.releasePoint().y - h.absoluteVec(new net.minecraft.world.phys.Vec3(0, 7.5, 0)).y) < 1e-6, "release height " + v.releasePoint());
                h.assertTrue(h.getBlockState(base).getValue(dev.denfry.promptcritical.mod.block.PcProperties.CHIMNEY_PART)
                        == dev.denfry.promptcritical.mod.block.PcProperties.ChimneyPart.BASE
                        && h.getBlockState(base.above(3)).getValue(dev.denfry.promptcritical.mod.block.PcProperties.CHIMNEY_PART)
                        == dev.denfry.promptcritical.mod.block.PcProperties.ChimneyPart.TOP, "base and cap segments");
                double cs = v.filter(dev.denfry.promptcritical.sim.radiation.Emitter.CS137, 1e9);
                h.assertTrue(Math.abs(cs - 1e9 / dev.denfry.promptcritical.sim.radiation.VentFilterBank.HEPA_DF) < 1, "after HEPA " + cs);
                h.assertTrue(v.bank().retainedCs137Bq() > 9e8, "HEPA did not keep the caesium");
                dev.denfry.promptcritical.mod.environment.RadiationService.airRelease(h.getLevel(), reactor, dev.denfry.promptcritical.sim.radiation.Emitter.I131, 1e9);
                h.assertTrue(v.bank().retainedI131Bq() > 9e8, "charcoal did not keep the iodine through airRelease");
            } finally {
                v.setExhaust(false);
                v.bank().setRetained(0, 0); // не оставлять источник в общем мире
            }
        }).thenSucceed();
    }

    /** Маркировка §13.8: табличка на баке с нефтью читает ООН 1267 и ромб; предписывающий знак проверяет СИЗ. */
    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void placardReadsTheTankAndSignsCheckPpe(GameTestHelper h) {
        BlockPos tank = new BlockPos(5, 2, 5), placard = new BlockPos(5, 2, 4), sign = new BlockPos(7, 2, 4);
        h.setBlock(tank, com.simibubi.create.AllBlocks.FLUID_TANK.getDefaultState());
        h.setBlock(placard, PcBlocks.UN_PLACARD.get().defaultBlockState());
        h.setBlock(sign, PcBlocks.HAZARD_SIGN.get().defaultBlockState());
        FakePlayer p = bare(h);
        h.startSequence().thenIdle(3).thenExecute(() -> {
            var handler = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, h.absolutePos(tank), null);
            h.assertTrue(handler != null, "tank has no fluid handler");
            handler.fill(new net.neoforged.neoforge.fluids.FluidStack(PcFluids.CRUDE_OIL.source.get(), 1000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            SignageBlockEntity pl = h.getBlockEntity(placard);
            var code = pl.product();
            h.assertTrue(code != null && code.un() == 1267 && code.diamond().equals("1-3-0"), "placard reads " + code);
            SignageBlockEntity s = h.getBlockEntity(sign);
            s.setVariant(2, 0);
            h.assertTrue(s.variantKey().equals("wear_hard_hat"), "variant " + s.variantKey());
            h.assertFalse(SignageBlockEntity.wears(p, "wear_hard_hat"), "bare head passes the hard hat sign");
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PcItems.HARD_HAT.get()));
            h.assertTrue(SignageBlockEntity.wears(p, "wear_hard_hat"), "hard hat not recognised");
            h.assertFalse(SignageBlockEntity.wears(p, "wear_gas_mask"), "hard hat is not a gas mask");
        }).thenExecute(() -> strip(p)).thenSucceed();
    }

    /** Водозабор 07 §7.13.4: у пруда забор ограничен объёмом, засор решётки зажигает «засорено», очистка снимает. */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void waterIntakeTakesFromAPondAndClogs(GameTestHelper h) {
        BlockPos intake = new BlockPos(8, 1, 4);
        // пруд 6×6×2 в каменной чаше: вода не должна растечься к соседним испытаниям общего мира
        for (int x = 1; x <= 8; x++)
            for (int z = 1; z <= 8; z++)
                for (int y = 0; y <= 2; y++) {
                    boolean inside = x >= 2 && x <= 7 && z >= 2 && z <= 7 && y >= 1;
                    if (x == 8 && z == 4 && y >= 1) continue;
                    h.setBlock(new BlockPos(x, y, z), inside ? net.minecraft.world.level.block.Blocks.WATER.defaultBlockState()
                            : net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                }
        h.setBlock(intake, PcBlocks.WATER_INTAKE.get().defaultBlockState());
        h.startSequence().thenIdle(25).thenExecute(() -> {
            WaterIntakeBlockEntity w = h.getBlockEntity(intake);
            w.survey(h.getLevel());
            h.assertTrue(w.wet(), "intake not in water");
            h.assertTrue(w.body() == dev.denfry.promptcritical.sim.thermal.WaterIntake.Body.LAKE, "body " + w.body());
            w.setClog(0.8);
        }).thenIdle(25).thenExecute(() -> {
            WaterIntakeBlockEntity w = h.getBlockEntity(intake);
            double lake = dev.denfry.promptcritical.sim.thermal.WaterIntake.bodyLimitM3s(dev.denfry.promptcritical.sim.thermal.WaterIntake.Body.LAKE, 0, 72);
            h.assertTrue(Math.abs(w.capacityM3s() - lake) < 1e-12, "pond limit " + w.capacityM3s() + " vs " + lake);
            h.assertTrue(h.getBlockState(intake).getValue(PcProperties.ALARM), "clogged screen not shown");
            w.intake().clean();
        }).thenIdle(25).thenExecute(() -> h.assertFalse(h.getBlockState(intake).getValue(PcProperties.ALARM), "raked screen still clogged"))
                .thenSucceed();
    }
}
