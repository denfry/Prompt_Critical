package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.radiation.ContaminationData;
import dev.denfry.promptcritical.mod.radiation.ContaminationMap;
import dev.denfry.promptcritical.mod.radiation.ContaminationService;
import dev.denfry.promptcritical.mod.radiation.DeconShowerBlockEntity;
import dev.denfry.promptcritical.mod.radiation.DeconSprayerItem;
import dev.denfry.promptcritical.mod.radiation.HandFootMonitorBlockEntity;
import dev.denfry.promptcritical.mod.radiation.PortalMonitorBlockEntity;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import dev.denfry.promptcritical.mod.radiation.UltrasonicBathBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.radiation.ContaminationLimits;
import dev.denfry.promptcritical.sim.radiation.SurfaceContamination;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Поверхностное загрязнение в мире: пролив ложится на пол и виден радиометром сверх нормы, обувь разносит его по
 * чистому полу, душ отмывает руки и даёт смыв в отходы, установка контроля ловит грязные руки, ультразвуковая ванна
 * чистит деталь и копит ЖРО, покрытие сдирается с рыхлым загрязнением, перманганат с кислотой берёт фиксированное,
 * загрязнение пола даёт ожидаемую дозу без острой, а β-пол светит γ.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContaminationGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private ContaminationGameTests() {
    }

    /** Общий FakePlayer уровня: начать с чистыми руками и обувью. */
    private static FakePlayer cleanPlayer(GameTestHelper h) {
        FakePlayer p = TestPlayers.of(h);
        ContaminationService.setHands(p, SurfaceContamination.NONE);
        ContaminationService.setShoes(p, SurfaceContamination.NONE);
        p.getInventory().clearContent();
        return p;
    }

    private static void floor(GameTestHelper h, int y) {
        for (int x = 1; x <= 10; x++)
            for (int z = 1; z <= 10; z++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
    }

    @GameTest(template = LARGE)
    public static void spillLandsOnTheFloorAboveTheLimit(GameTestHelper h) {
        floor(h, 1);
        BlockPos center = h.absolutePos(new BlockPos(5, 2, 5));
        int n = ContaminationService.spill(h.getLevel(), center, 2, 1e6, 1e8);
        // круг радиуса 2 — 13 столбцов, пол каждого — камень на y = 1
        h.assertTrue(n == 13, "spill covered " + n + " blocks");
        SurfaceContamination c = ContaminationService.surface(h.getLevel(), center.below());
        double area = 13 * 1e4;
        h.assertTrue(Math.abs(c.alpha() - 1e6 / area) < 1e-6 * c.alpha() + 1e-9, "alpha " + c.alpha());
        h.assertTrue(c.betaLoose() > c.betaFixed(), "fresh spill is mostly loose");
        h.assertTrue(ContaminationLimits.Surface.PERMANENT_ROOM.ratio(c) > 1, "over the room limit: " + c);
        h.assertTrue(ContaminationService.surface(h.getLevel(), center).negligible(), "air above the floor is not a surface");
        ContaminationService.setSurface(h.getLevel(), center.below(), SurfaceContamination.NONE);
        h.succeed();
    }

    @GameTest(template = LARGE)
    public static void shoesCarryContaminationAndTheShowerWashesHands(GameTestHelper h) {
        floor(h, 1);
        FakePlayer p = cleanPlayer(h);
        BlockPos dirty = h.absolutePos(new BlockPos(2, 1, 2)), clean = h.absolutePos(new BlockPos(8, 1, 8));
        ContaminationService.setSurface(h.getLevel(), dirty, SurfaceContamination.of(5, 500, 1));
        ContaminationService.walk(h.getLevel(), p, dirty, 7);
        h.assertTrue(ContaminationService.shoes(p).betaLoose() > 1, "shoes picked up: " + ContaminationService.shoes(p));
        ContaminationService.walk(h.getLevel(), p, clean, 7);
        h.assertTrue(ContaminationService.surface(h.getLevel(), clean).betaLoose() > 0.01, "shoes left a trail");

        BlockPos shower = new BlockPos(5, 2, 5);
        h.setBlock(shower, PcBlocks.DECON_SHOWER.get());
        DeconShowerBlockEntity s = h.getBlockEntity(shower);
        h.assertTrue(!s.wash(p, 1), "no water, no wash");
        s.fluids(Direction.NORTH).fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
        ContaminationService.setHands(p, SurfaceContamination.of(1, 100, 1));
        double before = ContaminationService.hands(p).total();
        for (int i = 0; i < 60; i++) h.assertTrue(s.wash(p, 1), "washes with water");
        double after = ContaminationService.hands(p).total();
        h.assertTrue(after < before / 9, "a minute with soap: " + before + " -> " + after);
        h.assertTrue(Math.abs(s.waste().amount() - 12) < 1e-6, "12 l of wash water: " + s.waste().amount());
        h.assertTrue(s.wasteBq() > 0, "removed activity is in the waste");
        ContaminationService.setSurface(h.getLevel(), dirty, SurfaceContamination.NONE);
        ContaminationService.setSurface(h.getLevel(), clean, SurfaceContamination.NONE);
        h.succeed();
    }

    @GameTest(template = LARGE)
    public static void handFootMonitorCatchesDirtyHands(GameTestHelper h) {
        FakePlayer p = cleanPlayer(h);
        h.setBlock(new BlockPos(5, 2, 5), PcBlocks.HAND_FOOT_MONITOR.get());
        h.assertTrue(HandFootMonitorBlockEntity.worstRatio(p) <= 1, "clean player passes");
        // 1 Бк/см² Pu на ладонях — в 7,5 раз выше нормы для кожи
        ContaminationService.setHands(p, SurfaceContamination.of(1, 0, 1));
        double r = HandFootMonitorBlockEntity.worstRatio(p);
        h.assertTrue(r > 5 && r < 10, "dirty hands ratio " + r);
        // ниже МДА прибор загрязнения не видит
        ContaminationService.setHands(p, SurfaceContamination.of(0.01, 0, 1));
        h.assertTrue(HandFootMonitorBlockEntity.worstRatio(p) == 0, "below MDA reads clean");
        ContaminationService.setHands(p, SurfaceContamination.NONE);
        h.succeed();
    }

    /** Портальный монитор 04b §4b.7.2: 37 кБк Cs-137 в кармане — тревога и редстоун, пустые карманы — чисто. */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void portalMonitorCatchesCaesiumInAPocket(GameTestHelper h) {
        FakePlayer p = cleanPlayer(h);
        BlockPos at = new BlockPos(5, 2, 5);
        h.setBlock(at, PcBlocks.PORTAL_MONITOR.get());
        PortalMonitorBlockEntity m = h.getBlockEntity(at);
        long now = h.getLevel().getGameTime();
        h.assertTrue(PortalMonitorBlockEntity.ratio(p, m.backgroundUSvH(), now) == 0, "empty pockets read zero");
        m.pass(p);
        h.assertFalse(m.alarming(), "clean player passes");
        ItemStack speck = new ItemStack(Items.IRON_NUGGET);
        speck.set(ActivityData.ACTIVITY.get(), new ActivityData(now, 0.001, 1, java.util.Map.of(Emitter.CS137.name(), 37e3)));
        p.getInventory().add(speck);
        double r = PortalMonitorBlockEntity.ratio(p, m.backgroundUSvH(), now);
        h.assertTrue(r > 1 && r < 2, "a microcurie of Cs-137 is just above the threshold: " + r);
        m.pass(p);
        h.assertTrue(m.alarming(), "alarm on a microcurie");
        h.assertBlockProperty(at, BlockStateProperties.POWERED, true);
        p.getInventory().clearContent();
        // проход пуст: тревога держится 2 с и гаснет
        h.runAfterDelay(60, () -> {
            h.assertBlockProperty(at, BlockStateProperties.POWERED, false);
            h.succeed();
        });
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void ultrasonicBathCleansPartsIntoLiquidWaste(GameTestHelper h) {
        BlockPos at = new BlockPos(5, 2, 5);
        h.setBlock(at, PcBlocks.ULTRASONIC_BATH.get());
        SurfaceContamination dirty = SurfaceContamination.of(10, 1000, 0.5);
        double[] bqBefore = new double[1];
        h.startSequence()
                .thenExecute(() -> {
                    UltrasonicBathBlockEntity b = h.getBlockEntity(at);
                    b.setBenchPower(true);
                    ItemStack part = new ItemStack(Items.IRON_INGOT);
                    ContaminationData.set(part, dirty);
                    h.assertTrue(b.accepts(part) && !b.accepts(new ItemStack(Items.IRON_INGOT)), "only contaminated parts go in");
                    b.input().insertItem(0, part, false);
                    IFluidHandler in = b.fluids(Direction.NORTH);
                    in.fill(new FluidStack(PcFluids.DECON_SOLUTION.source.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
                    in.fill(new FluidStack(PcFluids.DECON_PERMANGANATE.source.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
                })
                .thenWaitUntil(() -> {
                    UltrasonicBathBlockEntity b = h.getBlockEntity(at);
                    h.assertTrue(!b.output().getStackInSlot(0).isEmpty(), "part not out yet");
                })
                .thenExecute(() -> {
                    UltrasonicBathBlockEntity b = h.getBlockEntity(at);
                    SurfaceContamination after = ContaminationData.get(b.output().getStackInSlot(0));
                    h.assertTrue(b.lastMethod() == dev.denfry.promptcritical.sim.radiation.DeconMethod.ULTRASONIC_PERMANGANATE,
                            "permanganate then acid: " + b.lastMethod());
                    h.assertTrue(dirty.total() / Math.max(1e-12, after.total()) > 30, "DF " + dirty.total() / after.total());
                    // по 2 л перманганата и кислоты — 4 л ЖРО
                    h.assertTrue(Math.abs(b.waste().amount() - 4) < 1e-6, "liquid waste " + b.waste().amount());
                    h.assertTrue(b.wasteBq() > 0.9 * dirty.total() * 100, "activity moved to waste: " + b.wasteBq());
                    h.assertTrue(b.fluids(Direction.DOWN).drain(4, IFluidHandler.FluidAction.SIMULATE).getFluid()
                            == PcFluids.CONTAMINATED_DECON_SOLUTION.source.get(), "waste comes out of the bottom");
                    bqBefore[0] = b.wasteBq();
                    // половину смыва слили трубой — с ней ушла половина активности бака
                    b.fluids(Direction.DOWN).drain(2, IFluidHandler.FluidAction.EXECUTE);
                })
                .thenExecuteAfter(2, () -> {
                    UltrasonicBathBlockEntity b = h.getBlockEntity(at);
                    h.assertTrue(Math.abs(b.wasteBq() - bqBefore[0] / 2) < 1e-6 * bqBefore[0],
                            "drained waste took its activity: " + b.wasteBq() + " of " + bqBefore[0]);
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE)
    public static void strippableCoatingPeelsLooseContamination(GameTestHelper h) {
        floor(h, 1);
        FakePlayer p = cleanPlayer(h);
        BlockPos pos = h.absolutePos(new BlockPos(4, 1, 4));
        SurfaceContamination c = new SurfaceContamination(2, 1, 200, 100);
        ContaminationService.setSurface(h.getLevel(), pos, c);
        ContaminationMap m = ContaminationMap.of(h.getLevel());
        m.coat(pos, h.getLevel().getGameTime() + 100);
        h.assertTrue(ContaminationService.peel(h.getLevel(), pos, p), "coating is there");
        h.assertTrue(ContaminationService.surface(h.getLevel(), pos).equals(c), "wet coating does not come off");
        m.coat(pos, h.getLevel().getGameTime() - 1);
        ContaminationService.peel(h.getLevel(), pos, p);
        SurfaceContamination after = ContaminationService.surface(h.getLevel(), pos);
        h.assertTrue(Math.abs(after.betaLoose() - 10) < 1e-9, "loose beta down 20 times: " + after.betaLoose());
        h.assertTrue(Math.abs(after.betaFixed() - 100 / 1.5) < 1e-9, "fixed barely touched: " + after.betaFixed());
        h.assertTrue(m.coating(pos) < 0, "film is gone");
        ItemStack film = p.getInventory().getItem(0);
        h.assertTrue(film.is(PcItems.STRIPPED_COATING.get()), "film in the inventory: " + film);
        // активность сохранилась: снятое с 1 м² — на плёнке
        double filmBq = ContaminationData.get(film).total() * 100, removedBq = (c.total() - after.total()) * 1e4;
        h.assertTrue(Math.abs(filmBq - removedBq) < 1e-6 * removedBq, "film " + filmBq + " Bq vs removed " + removedBq);
        ContaminationService.setSurface(h.getLevel(), pos, SurfaceContamination.NONE);
        p.getInventory().clearContent();
        h.succeed();
    }

    @GameTest(template = LARGE)
    public static void sprayerNeedsPermanganateBeforeAcidForFixedContamination(GameTestHelper h) {
        floor(h, 1);
        BlockPos pos = h.absolutePos(new BlockPos(6, 1, 6));
        SurfaceContamination fixed = new SurfaceContamination(0, 1, 0, 100);
        ContaminationService.setSurface(h.getLevel(), pos, fixed);
        ItemStack sprayer = new ItemStack(PcItems.DECON_SPRAYER.get());
        sprayer.set(ContaminationData.SPRAYER.get(), new ContaminationData.SprayerCharge("promptcritical:decon_permanganate", 40, 0, 0));
        h.assertTrue(DeconSprayerItem.spray(h.getLevel(), pos, sprayer, null), "permanganate sprayed");
        h.assertTrue(ContaminationMap.of(h.getLevel()).oxidised(pos), "film oxidised");
        sprayer.set(ContaminationData.SPRAYER.get(), new ContaminationData.SprayerCharge("promptcritical:decon_solution", 40,
                DeconSprayerItem.charge(sprayer).wasteL(), DeconSprayerItem.charge(sprayer).wasteBq()));
        h.assertTrue(DeconSprayerItem.spray(h.getLevel(), pos, sprayer, null), "acid sprayed");
        SurfaceContamination after = ContaminationService.surface(h.getLevel(), pos);
        h.assertTrue(Math.abs(fixed.total() / after.total() - 10) < 1e-6, "two steps take fixed down 10x: " + after);
        ContaminationData.SprayerCharge ch = DeconSprayerItem.charge(sprayer);
        h.assertTrue(Math.abs(ch.wasteL() - 30) < 1e-9, "10 + 20 l collected: " + ch.wasteL());
        h.assertTrue(ch.wasteBq() > 0, "collected waste carries the activity");
        // смыв сдаётся в бак, раствор набирается из бака
        FluidTank wasteTank = new FluidTank(1000);
        DeconSprayerItem.exchange(sprayer, wasteTank);
        h.assertTrue(wasteTank.getFluidAmount() == 30 && wasteTank.getFluid().getFluid() == PcFluids.CONTAMINATED_DECON_SOLUTION.source.get(),
                "waste handed over: " + wasteTank.getFluid());
        h.assertTrue(DeconSprayerItem.charge(sprayer).wasteL() == 0 && DeconSprayerItem.charge(sprayer).wasteBq() == 0, "collector empty");
        FluidTank supply = new FluidTank(1000);
        supply.fill(new FluidStack(PcFluids.DECON_SOLUTION.source.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        DeconSprayerItem.exchange(sprayer, supply);
        h.assertTrue(DeconSprayerItem.charge(sprayer).litres() == DeconSprayerItem.CAPACITY_L, "refilled: " + DeconSprayerItem.charge(sprayer));
        ContaminationService.setSurface(h.getLevel(), pos, SurfaceContamination.NONE);
        h.succeed();
    }

    @GameTest(template = LARGE)
    public static void contaminationGivesCommittedNotAcuteDose(GameTestHelper h) {
        FakePlayer p = cleanPlayer(h);
        double total0 = RadiationService.totalSv(p), acute0 = RadiationService.acuteSv(p), internal0 = RadiationService.internalSv(p);
        // 10 Бк/см² Pu на полу, час: вдох ≈ 3,8 мкЗв, заглатывание ≈ 2,8 мкЗв
        double sv = ContaminationService.expose(p, SurfaceContamination.of(10, 0, 1), 3600);
        h.assertTrue(sv > 5e-6 && sv < 1e-5, "committed dose " + sv);
        h.assertTrue(Math.abs(RadiationService.internalSv(p) - internal0 - sv) < 1e-12, "internal dose recorded");
        h.assertTrue(Math.abs(RadiationService.totalSv(p) - total0 - sv) < 1e-12, "in the total");
        h.assertTrue(RadiationService.acuteSv(p) == acute0, "no acute dose from intake");
        h.succeed();
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void betaContaminatedFloorShinesGamma(GameTestHelper h) {
        floor(h, 1);
        BlockPos center = h.absolutePos(new BlockPos(5, 2, 5));
        Vec3 above = Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 3, 5)));
        double before = RadiationService.rateSvH(h.getLevel(), above);
        // 10 ГБк Cs-137 на полу в круге радиуса 3
        ContaminationService.spill(h.getLevel(), center, 3, 0, 1e10);
        h.runAfterDelay(41, () -> {
            double rate = RadiationService.rateSvH(h.getLevel(), above);
            h.assertTrue(rate - before > 1e-7, "gamma above the floor " + rate + " Sv/h (was " + before + ")");
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) ContaminationService.setSurface(h.getLevel(), center.offset(dx, -1, dz), SurfaceContamination.NONE);
            h.succeed();
        });
    }
}
