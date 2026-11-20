package dev.denfry.promptcritical.mod.film;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.drilling.DrillersConsoleBlockEntity;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.BlastFurnaceControllerBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.CokeOvenBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksComponents;
import dev.denfry.promptcritical.sim.metallurgy.CokeBattery;
import dev.denfry.promptcritical.sim.metallurgy.CokeBatteryUnit;
import dev.denfry.promptcritical.sim.metallurgy.IronmakingConfig;
import net.neoforged.neoforge.items.IItemHandler;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.npp.PwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.CascadeBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.EnrichmentPanels;
import dev.denfry.promptcritical.mod.nuclear.Uf6Charge;
import dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem;
import dev.denfry.promptcritical.mod.powerhouse.GasTurbineBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.drilling.BopStack;
import dev.denfry.promptcritical.sim.drilling.FormationFluid;
import dev.denfry.promptcritical.sim.drilling.Prospect;
import dev.denfry.promptcritical.sim.drilling.Trap;
import dev.denfry.promptcritical.sim.enrichment.CentrifugeHall;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import dev.denfry.promptcritical.sim.npp.PressurizedWaterReactor;
import dev.denfry.promptcritical.sim.petrochem.PyrolysisFeed;
import dev.denfry.promptcritical.sim.petrochem.PyrolysisFurnace;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Рабочий завод для роликов (только dev): {@code /film prime <сцена> <pos>} запускает процесс так же, как его
 * запускают игровые тесты — топливо в реактор, газ в турбину, пропан в печь, пласт с АВПД под буровую, цилиндры UF₆
 * в каскад. Командами датапака этого не сделать: нужны жидкости в баках и состояние двойников. Вызывается из
 * {@code filmset:prime} и из событий шотов {@code film.json}.
 */
@EventBusSubscriber(modid = PromptCritical.MOD_ID)
public final class FilmPlant {
    private FilmPlant() {
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("film").requires(s -> s.hasPermission(2)).then(Commands.literal("prime")
                .then(scene("reactor_fuel", FilmPlant::reactorFuel))
                .then(scene("reactor_power", FilmPlant::reactorPower))
                .then(scene("pyrolysis", FilmPlant::pyrolysis))
                .then(scene("turbine", FilmPlant::turbine))
                .then(scene("boiler_water", FilmPlant::boilerWater))
                .then(scene("kick", FilmPlant::kick))
                .then(scene("drill", FilmPlant::drill))
                .then(scene("cascade", FilmPlant::cascade))
                .then(scene("generator", FilmPlant::generator))
                .then(scene("coke", FilmPlant::coke))
                .then(scene("blast_furnace", FilmPlant::blastFurnace))
                .then(scene("refinery", FilmPlant::refinery))
                .then(scene("wind", FilmPlant::wind))
                .then(scene("wind_park", FilmPlant::windPark))
                .then(scene("load", FilmPlant::load))
                .then(scene("volts", FilmPlant::volts))
                .then(scene("unit", FilmPlant::unit))));
    }

    private interface Scene {
        String run(ServerLevel level, BlockPos pos);
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> scene(String name, Scene s) {
        return Commands.literal(name).then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> run(ctx, name, s)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String name, Scene s) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        String r;
        try {
            r = s.run(ctx.getSource().getLevel(), pos);
        } catch (RuntimeException ex) {
            r = "failed: " + ex;
        }
        String msg = "[film] " + name + " " + pos.toShortString() + ": " + r;
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        PromptCritical.LOGGER.info(msg);
        return 1;
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> T be(ServerLevel level, BlockPos pos, Class<T> type) {
        BlockEntity b = level.getBlockEntity(pos);
        if (!type.isInstance(b)) throw new IllegalStateException("no " + type.getSimpleName() + " at " + pos.toShortString());
        return (T) b;
    }

    /** Полная загрузка ВВЭР-440, стержни внизу (PwrReactorGameTests.losingPumpDriveAtPowerTrips). */
    private static String reactorFuel(ServerLevel level, BlockPos pos) {
        PressurizedWaterReactor r = be(level, pos, PwrReactorBlockEntity.class).reactor();
        double kg = FuelDesign.VVER_440.uKgPerAssembly(0.96, 0.044);
        r.setRods(1, 1);
        for (int i = 0; i < PressurizedWaterReactor.POSITIONS; i++) r.load(i, FuelDesign.VVER_440, kg, 0.044, 0.0003);
        return "loaded " + PressurizedWaterReactor.POSITIONS + " assemblies";
    }

    /** Сброс АЗ и горячий выход на 50 %: бор на критику при рабочей группе на 30 %. */
    private static String reactorPower(ServerLevel level, BlockPos pos) {
        PressurizedWaterReactor r = be(level, pos, PwrReactorBlockEntity.class).reactor();
        if (!r.resetTrip()) return "trip not reset: " + r.protection().activeReasons();
        r.setThermalState(800, 588, 563);
        r.setRods(0, 0.3);
        double lo = 0, hi = 30;
        for (int i = 0; i < 40; i++) {
            double m = (lo + hi) / 2;
            r.setBoronGkg(m);
            if (r.evaluateReactivity() > 0) lo = m;
            else hi = m;
        }
        r.equilibrate(0.5);
        return "at 50 %";
    }

    /** Пропан, питательная вода и топливный газ в печь, режим RUN (PyrolysisGameTests). */
    private static String pyrolysis(ServerLevel level, BlockPos pos) {
        PyrolysisFurnaceBlockEntity f = be(level, pos, PyrolysisFurnaceBlockEntity.class);
        f.furnace().setFeed(PyrolysisFeed.PROPANE);
        f.furnace().setCotK(1103.15);
        f.furnace().setMode(PyrolysisFurnace.Mode.RUN);
        IFluidHandler in = f.fluids(Direction.NORTH);
        in.fill(new FluidStack(PcFluids.PROPANE.source.get(), 16000), IFluidHandler.FluidAction.EXECUTE);
        in.fill(new FluidStack(PcFluids.BOILER_FEEDWATER.source.get(), 16000), IFluidHandler.FluidAction.EXECUTE);
        in.fill(new FluidStack(PcFluids.SALES_GAS.source.get(), 8000), IFluidHandler.FluidAction.EXECUTE);
        return "running";
    }

    /** Шахтный метан в бак ГТУ и пуск (CombinedCycleGameTests). */
    private static String turbine(ServerLevel level, BlockPos pos) {
        GasTurbineBlockEntity g = be(level, pos, GasTurbineBlockEntity.class);
        int filled = g.fluids(null).fill(new FluidStack(GasBuffer.fluid("mine_methane"), 16000), IFluidHandler.FluidAction.EXECUTE);
        g.turbine().setRun(true);
        return "fuel " + filled + " mB, run";
    }

    /** Вода в котёл-утилизатор пара ПГУ: давление 0,2 МПа, уровень 67 %. */
    private static String boilerWater(ServerLevel level, BlockPos pos) {
        FireTubeBoilerBlockEntity b = be(level, pos, FireTubeBoilerBlockEntity.class);
        SimLevelBinding bind = SimBindings.get(level);
        if (bind == null || b.simId().isNone()) return "boiler twin not attached yet";
        bind.world().subsystem(SteamPlant.class).presetWater(b.simId(), 2e5, 0.67);
        return "water preset";
    }

    /** Пласт газа с АВПД прямо под ротором, долото у кровли: через минуты бурения — газопроявление (DrillingGameTests). */
    private static String kick(ServerLevel level, BlockPos console) {
        DrillersConsoleBlockEntity c = be(level, console, DrillersConsoleBlockEntity.class);
        if (!c.rigOk() || c.well() == null) return "rig not ready, missing " + c.missing();
        BlockPos rotary = console.offset(5, 0, 5);
        Trap gas = new Trap(rotary.getX(), rotary.getZ(), 80, 800, 60, 860, 15, 0.2, 50, FormationFluid.GAS, 11_800);
        openBop(c);
        c.spud(new Prospect(gas, 800), 799.6);  // bit 0.4 m above the gas top: the kick comes within the shot
        c.setDrilling(120e3, 100);
        return "drilling into the gas cap";
    }

    /** A BOP left shut by an earlier kick holds the string: the bit makes no hole until every element is open again. */
    private static void openBop(DrillersConsoleBlockEntity c) {
        for (BopStack.Component kind : BopStack.Component.values()) c.openComponent(kind, null);
    }

    /**
     * Steady drilling for the plant world: the same rig and prospect as {@link #kick}, but the bit spuds at 610 m (just
     * below the casing shoe), 190 m above the gas top, on a light bit, so the rotary turns and the hole deepens for a
     * long while without a kick turning into a blowout; rerun near the top, it spuds again.
     */
    private static String drill(ServerLevel level, BlockPos console) {
        DrillersConsoleBlockEntity c = be(level, console, DrillersConsoleBlockEntity.class);
        if (!c.rigOk() || c.well() == null) return "rig not ready, missing " + c.missing();
        BlockPos rotary = console.offset(5, 0, 5);
        Trap gas = new Trap(rotary.getX(), rotary.getZ(), 80, 800, 60, 860, 15, 0.2, 50, FormationFluid.GAS, 11_800);
        // rerunnable: spud once (below the 600 m casing shoe, Well requires it), later runs only report the depth
        if (c.well().holeDepthM() < 605 || c.well().holeDepthM() > 780) {
            openBop(c);
            c.spud(new Prospect(gas, 800), 610);
        }
        c.setDrilling(40e3, 60);
        return String.format(java.util.Locale.ROOT, "drilling, hole %.1f m, gas top at 800 m", c.well().holeDepthM());
    }

    /** Цилиндры природного UF₆ и пуск каскада на 80 % оборотов (EnrichmentGameTests). */
    private static String cascade(ServerLevel level, BlockPos pos) {
        CascadeBlockEntity c = be(level, pos, CascadeBlockEntity.class);
        ItemStack feed = Uf6CylinderItem.fill(new ItemStack(PcItems.UF6_CYLINDER_48Y.get()), 2000, Uf6Charge.NATURAL_U235,
                Uf6Charge.NATURAL_U234);
        c.cylinders().setStackInSlot(EnrichmentPanels.SLOT_FEED, feed);
        c.cylinders().setStackInSlot(EnrichmentPanels.SLOT_PRODUCT, new ItemStack(PcItems.UF6_CYLINDER_30B.get()));
        c.cylinders().setStackInSlot(EnrichmentPanels.SLOT_TAILS, new ItemStack(PcItems.UF6_CYLINDER_48Y.get()));
        c.hall().setDesign(CentrifugeHall.Design.CARBON);
        c.hall().setSpeedFraction(0.8);
        c.hall().setRunning(true);
        return "running, " + c.machines() + " machines";
    }

    /** Генератор 6,3 кВ на 64 об/мин передачи, поле включено (GridGameTests.configure). */
    private static String generator(ServerLevel level, BlockPos pos) {
        GeneratorUnit u = be(level, pos, ShaftGeneratorBlockEntity.class).unit();
        if (u == null) return "generator twin not attached yet";
        u.setVoltageClass(VoltageClass.MV_6_3);
        for (int i = 0; i < 5 && u.gearRpm() != 64; i++) u.cycleGear();
        u.setFieldOn(true);
        return "field on";
    }

    /** Wind turbine on automatic, as WindGameTests: it starts itself once the collector is live and the wind is up. */
    private static String wind(ServerLevel level, BlockPos pos) {
        var u = be(level, pos, dev.denfry.promptcritical.mod.powerhouse.WindTurbineBlockEntity.class).unit();
        if (u == null) return "wind turbine twin not attached yet";
        u.setEnabled(true);
        String fault = u.turbine().fault().name();
        boolean reset = u.resetFault();
        return String.format(java.util.Locale.ROOT, "enabled, fault %s%s, %s, wind %.1f m/s, rotor %.1f rpm, export %.0f kW, contactor %s",
                fault, reset ? " (reset)" : "", u.turbine().state(), u.windMs(), u.rotorRpm(), u.exportW() / 1e3, u.contactorClosed());
    }

    /** Wind turbine off automatic: it feathers and parks; a tripped one is reset once its rotor has stopped. */
    private static String windPark(ServerLevel level, BlockPos pos) {
        var u = be(level, pos, dev.denfry.promptcritical.mod.powerhouse.WindTurbineBlockEntity.class).unit();
        if (u == null) return "wind turbine twin not attached yet";
        u.setEnabled(false);
        return "parked, " + u.turbine().state() + (u.resetFault() ? ", fault reset" : "");
    }

    /**
     * Feed one refinery unit as its GameTest does (ConversionGameTests, RefineryUpgradingGameTests): catalyst once,
     * then the feeds; rerun by {@code /film feed} to keep it going, since the tests top the feeds up every tick.
     */
    private static String unit(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        // products leave first, as to the tank farm: a unit whose run-down tanks are full stops taking feed. Outlets
        // are the drainable handlers (feed inlets drain nothing or their own outlet), so every face is drained.
        int out = 0;
        if (be instanceof FoundryDevice d) for (Direction side : Direction.values()) {
            IFluidHandler h = d.fluids(side);
            if (h != null) out += h.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE).getAmount();
        }
        if (be instanceof dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity u)
            for (int i = 0; i < u.output().getSlots(); i++) out += u.output().extractItem(i, 64, false).getCount();
        String shipped = out > 0 ? " (shipped " + out + ")" : "";
        return unitFeed(be, pos) + shipped;
    }

    private static String unitFeed(net.minecraft.world.level.block.entity.BlockEntity be, BlockPos pos) {
        if (be instanceof dev.denfry.promptcritical.mod.refinery.DesalterBlockEntity d) {
            d.setBenchPower(true);
            return "desalter: crude " + fillMb(d.fluids(Direction.NORTH), PcFluids.CRUDE_OIL.source.get(), 8000)
                    + ", water " + fillMb(d.fluids(Direction.UP), PcFluids.RAW_WATER.source.get(), 8000);
        }
        if (be instanceof dev.denfry.promptcritical.mod.refinery.VacuumColumnBlockEntity v) {
            IFluidHandler in = v.fluids(Direction.NORTH);
            return "vacuum column: mazut " + fillMb(in, PcFluids.FUEL_OIL.source.get(), 8000) + ", gas "
                    + fillMb(in, PcFluids.SALES_GAS.source.get(), 8000) + ", feedwater "
                    + fillMb(in, PcFluids.BOILER_FEEDWATER.source.get(), 8000) + ", ejectors " + v.ejectors()
                    + ", " + v.pressureKpa() + " kPa";
        }
        if (be instanceof dev.denfry.promptcritical.mod.refinery.DelayedCokerBlockEntity c) {
            IFluidHandler in = c.fluids(Direction.NORTH);
            return "coker: tar " + fillMb(in, PcFluids.VACUUM_RESIDUE.source.get(), 4000) + ", mazut "
                    + fillMb(in, PcFluids.FUEL_OIL.source.get(), 2000);
        }
        if (be instanceof dev.denfry.promptcritical.mod.refinery.HydrotreaterBlockEntity t) {
            catalyst(t.input(), PcItems.CATALYST_HYDROTREATING.get());
            return "hydrotreater: diesel " + fillMb(t.fluids(Direction.NORTH), PcFluids.DIESEL_FUEL.source.get(), 4000)
                    + ", hydrogen " + fillMb(t.fluids(Direction.UP), PcFluids.HYDROGEN.source.get(), 8000);
        }
        if (be instanceof dev.denfry.promptcritical.mod.refinery.CatalyticReformerBlockEntity r) {
            catalyst(r.input(), PcItems.CATALYST_REFORMING.get());
            return "reformer: naphtha " + fillMb(r.fluids(Direction.NORTH), PcFluids.GASOLINE.source.get(), 4000)
                    + ", fuel gas " + fillMb(r.fluids(Direction.EAST), PcFluids.SALES_GAS.source.get(), 4000);
        }
        if (be instanceof dev.denfry.promptcritical.mod.uranium.AcidPlantBlockEntity a) {
            int sulfur = insert(a.input(), new ItemStack(PcItems.SULFUR.get(), 4));
            return "acid plant: sulfur " + sulfur + ", water " + fillMb(a.fluids(Direction.NORTH),
                    net.minecraft.world.level.material.Fluids.WATER, 1000);
        }
        return "no refinery unit at " + pos.toShortString() + (be == null ? "" : " (" + be.getClass().getSimpleName() + ")");
    }

    private static int fillMb(IFluidHandler h, net.minecraft.world.level.material.Fluid f, int mb) {
        return h == null ? -1 : h.fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
    }

    /** A catalyst charge goes in once: the unit keeps it until it is spent. */
    private static void catalyst(IItemHandler in, net.minecraft.world.item.Item cat) {
        for (int i = 0; i < in.getSlots(); i++) if (in.getStackInSlot(i).is(cat)) return;
        insert(in, new ItemStack(cat));
    }

    /** Voltage at each terminal of a grid device: where a network is dead. */
    private static String volts(ServerLevel level, BlockPos pos) {
        var be = be(level, pos, dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity.class);
        var u = be.unit();
        if (u == null) return "no twin";
        var g = dev.denfry.promptcritical.mod.binding.SimBindings.get(level).world()
                .subsystem(dev.denfry.promptcritical.sim.grid.GridSubsystem.class);
        StringBuilder out = new StringBuilder(u.getClass().getSimpleName());
        for (int i = 0; i < be.terminals().size(); i++)
            out.append(String.format(java.util.Locale.ROOT, ", t%d node %d %.0f V", i, u.node(i), g.voltageV(u.node(i))));
        return out.toString();
    }

    /**
     * Station load for the wind farm: 1000 kW (a {@code ConsumerUnit.POWER_KW} step; other values are ignored), carried
     * by three 400 kW sets until the 8 turbines (100 kW each at most) take part of it over. A load below the farm's
     * export drives power back into generators whose Create drive cannot brake, and the island overspeeds.
     */
    private static String load(ServerLevel level, BlockPos pos) {
        var u = be(level, pos, dev.denfry.promptcritical.mod.grid.ConsumerBlockEntity.class).unit();
        if (u == null) return "consumer twin not attached yet";
        u.setDemandKw(1000);
        u.setSwitchedOn(true);
        return (int) (u.demandW() / 1e3) + " kW on";
    }

    private static int insert(IItemHandler inv, ItemStack stack) {
        ItemStack rest = stack;
        for (int i = 0; i < inv.getSlots() && !rest.isEmpty(); i++) rest = inv.insertItem(i, rest, false);
        return stack.getCount() - rest.getCount();
    }

    /**
     * Коксовая батарея (IronworksGameTests.buildBattery): простенки разогреты до 1300 °C, как в cookUntilReady, все
     * печи загружены мытым углём через люки — печи коксуются в кадре. pos — нижний угол батареи (origin теста).
     */
    private static String coke(ServerLevel level, BlockPos origin) {
        CokeOvenBlockEntity m = be(level, origin, CokeOvenBlockEntity.class).master();
        if (m == null || m.shape() == null) return "battery not formed";
        SimLevelBinding bind = SimBindings.get(level);
        CokeBatteryUnit u = bind == null ? null : Ironworks.unit(bind, m.simId(), CokeBatteryUnit.class);
        if (u == null || u.battery() == null) return "battery twin not attached yet";
        CokeBattery bat = u.battery();
        IronmakingConfig cfg = IronmakingConfig.defaults();
        double dt = 600, allowed = (CokeBattery.WALL_HOT_K - 293.15) / (cfg.heatupDaysPhys() * 86_400);
        for (int k = 0; k < 20_000 && bat.wallTempK() < 1563.15; k++) {
            bat.setExtraHeat(Math.max(0, bat.wallHeatCapacityJK() * 0.9 * allowed + bat.coolingW()));
            bat.stepSlow(dt, null);
        }
        bat.setExtraHeat(0);
        int took = 0;
        for (int i = 0; i < m.shape().ovens(); i++) {
            BlockPos hatch = origin.offset(2, 2, 2 * i);
            if (level.getBlockEntity(hatch) instanceof FoundryDevice d && d.items(Direction.UP) != null)
                took += insert(d.items(Direction.UP), new ItemStack(PcItems.WASHED_COAL.get(), 64));
        }
        return String.format("walls %.0f °C, charged %d coal", bat.wallTempK() - 273.15, took);
    }

    /** Колошник доменной печи (IronworksGameTests): кокс с паспортом и агломерат в бункер. pos — контроллер. */
    /**
     * Crude unit as in ProductionGameTests: the column's fired heater gets crude and associated gas (its fuel), the
     * column runs at reflux 1.5 and a 360 °C furnace outlet.
     */
    private static String refinery(ServerLevel level, BlockPos column) {
        var c = be(level, column, dev.denfry.promptcritical.mod.production.CrudeColumnBlockEntity.class);
        var furnaces = Ironworks.blockEntities(level, new net.minecraft.world.phys.AABB(column).inflate(8),
                dev.denfry.promptcritical.mod.production.TubeFurnaceBlockEntity.class);
        if (furnaces.isEmpty()) return "no tube furnace within 8 blocks";
        int shipped = 0;
        for (var pr : dev.denfry.promptcritical.sim.refining.AtmosphericUnit.Product.values())
            shipped += c.tank(pr).drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE).getAmount();
        IFluidHandler in = furnaces.getFirst().fluids(Direction.UP);
        int crude = in.fill(new FluidStack(PcFluids.CRUDE_OIL.source.get(), 16_000), IFluidHandler.FluidAction.EXECUTE);
        int gas = in.fill(new FluidStack(PcFluids.ASSOCIATED_GAS.source.get(), 4_000), IFluidHandler.FluidAction.EXECUTE);
        c.setOperation(1.5, 360);
        return "crude " + crude + " L, gas " + gas + " L, products shipped " + shipped + " L, sections " + c.sections()
                + ", ready " + c.ready();
    }

    private static String blastFurnace(ServerLevel level, BlockPos controller) {
        BlastFurnaceControllerBlockEntity c = be(level, controller, BlastFurnaceControllerBlockEntity.class);
        if (c.shape() == null || c.unit() == null) return "furnace not formed";
        BlockPos top = controller.offset(3, 9, 0);
        if (!(level.getBlockEntity(top) instanceof FoundryDevice d) || d.items(Direction.UP) == null) return "no furnace top at " + top.toShortString();
        var u = c.unit();
        var f = u.furnace();
        // blow-in as BlastFurnaceTest.blowInSequence: the hearth must hold coke only, so a cold furnace whose bottom
        // layer already carries ore is emptied and charged with blank (coke-only) charges
        if (f.state() == dev.denfry.promptcritical.sim.metallurgy.BlastFurnaceState.COLD) {
            f.relineHearth();
            u.setBurden(u.oreLoad(), u.limestonePerOre(), true);
        }
        ItemStack coke = new ItemStack(PcItems.COKE.get(), 64);
        coke.set(IronworksComponents.COKE_ASSAY.get(), IronworksComponents.CokeAssay.DEFAULT);
        int k = insert(d.items(Direction.UP), coke);
        return "charged coke " + k + ", blanks " + u.blankCharges() + ", layers " + f.layerCount() + ", blast "
                + String.format(java.util.Locale.ROOT, "%.2f", u.blastNm3S()) + " nm3/s, state " + f.state();
    }
}
