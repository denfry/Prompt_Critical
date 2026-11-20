package dev.denfry.promptcritical.mod.film;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.boiler.SteamEngineBlockEntity;
import dev.denfry.promptcritical.mod.drilling.DrillersConsoleBlockEntity;
import dev.denfry.promptcritical.mod.drilling.MudHopperBlockEntity;
import dev.denfry.promptcritical.mod.grid.ConnectorBlockEntity;
import dev.denfry.promptcritical.mod.grid.ElectricMotorBlockEntity;
import dev.denfry.promptcritical.mod.grid.LightingBoardBlockEntity;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.BlastFurnaceControllerBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.IronworksComponents;
import dev.denfry.promptcritical.mod.ironworks.OreChem;
import dev.denfry.promptcritical.mod.npp.PwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.drilling.DrillersMethod;
import dev.denfry.promptcritical.sim.drilling.FormationFluid;
import dev.denfry.promptcritical.sim.drilling.KillSheet;
import dev.denfry.promptcritical.sim.drilling.Prospect;
import dev.denfry.promptcritical.sim.drilling.Trap;
import dev.denfry.promptcritical.sim.drilling.Well;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.LightingBoardUnit;
import dev.denfry.promptcritical.sim.grid.MotorUnit;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnace;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnaceSpec;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnaceState;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnaceUnit;
import dev.denfry.promptcritical.sim.metallurgy.BurdenLayer;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import dev.denfry.promptcritical.sim.npp.PressurizedWaterReactor;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Server commands for the film set datapack ({@code art/film/filmset.py}), only in the gametest source set (film and
 * dev clients). They do what a player does by hand, through the same code: {@code wire} strings a span with the wire
 * coil item ({@link dev.denfry.promptcritical.mod.grid.WireCoilItem#useOn}), {@code gen} sets a shaft generator as its
 * panel would, {@code probe} writes a device's state to the log ({@code [filmset]}) to check the set without a screen.
 *
 * <pre>
 * /filmset wire &lt;a&gt; &lt;b&gt; [insulated]       a, b: wire connectors of one class (MV: overhead conductor)
 * /filmset gen &lt;pos&gt; lv|mv &lt;gearRpm&gt; &lt;trim‰&gt;   voltage class, gear, frequency trim, field on
 * /filmset motor &lt;pos&gt; &lt;gearRpm&gt;               electric motor: gear, contactor closed (start button)
 * /filmset boiler &lt;pos&gt; &lt;MPa&gt; &lt;level m&gt;     boiler on the saturation line, as {@code pc boiler preset} (no gaze)
 * /filmset kick &lt;console&gt; [depth m]        spud into the over-pressured gas trap of DrillingGameTests and drill
 * /filmset barite &lt;console&gt;                 weight the pits to kill mud through the mixing hopper (kill step 2)
 * /filmset furnace &lt;controller&gt; hot          preset a running, hot hearth with a full column (no test reaches it)
 * /filmset reactor &lt;pos&gt; load               19 VVER-440 assemblies, rods in (PwrReactorGameTests)
 * /filmset reactor &lt;pos&gt; power &lt;pct&gt;        reset, thermal state, boron on criticality, equilibrate at pct
 * /filmset probe &lt;pos&gt;
 * </pre>
 */
@EventBusSubscriber(modid = PromptCritical.MOD_ID)
public final class FilmSet {
    private static final Logger LOG = LogUtils.getLogger();

    private FilmSet() {
    }

    @SubscribeEvent
    static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("filmset").requires(s -> s.hasPermission(2))
                .then(Commands.literal("wire")
                        .then(Commands.argument("a", BlockPosArgument.blockPos())
                                .then(Commands.argument("b", BlockPosArgument.blockPos())
                                        .executes(ctx -> wire(ctx, false))
                                        .then(Commands.argument("insulated", BoolArgumentType.bool())
                                                .executes(ctx -> wire(ctx, BoolArgumentType.getBool(ctx, "insulated")))))))
                .then(Commands.literal("gen")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("class", StringArgumentType.word())
                                        .then(Commands.argument("gear", IntegerArgumentType.integer(16, 256))
                                                .then(Commands.argument("trim", IntegerArgumentType.integer(-100, 100))
                                                        .executes(FilmSet::gen))))))
                .then(Commands.literal("motor")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("gear", IntegerArgumentType.integer(1, 512)).executes(FilmSet::motor))))
                .then(Commands.literal("boiler")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("mpa", DoubleArgumentType.doubleArg(0.1, 6))
                                        .then(Commands.argument("level", DoubleArgumentType.doubleArg(0, 1.6)).executes(FilmSet::boiler)))))
                .then(Commands.literal("kick")
                        .then(Commands.argument("console", BlockPosArgument.blockPos())
                                .executes(ctx -> kick(ctx, 799.5))
                                .then(Commands.argument("depth", DoubleArgumentType.doubleArg(780, 799.9))
                                        .executes(ctx -> kick(ctx, DoubleArgumentType.getDouble(ctx, "depth"))))))
                .then(Commands.literal("barite")
                        .then(Commands.argument("console", BlockPosArgument.blockPos()).executes(FilmSet::barite)))
                .then(Commands.literal("furnace")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.literal("hot").executes(FilmSet::furnaceHot))))
                .then(Commands.literal("reactor")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.literal("load").executes(FilmSet::reactorLoad))
                                .then(Commands.literal("power")
                                        .then(Commands.argument("pct", DoubleArgumentType.doubleArg(1, 100)).executes(FilmSet::reactorPower)))))
                .then(Commands.literal("probe")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(FilmSet::probe))));
    }

    private static int wire(CommandContext<CommandSourceStack> ctx, boolean insulated) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos a = BlockPosArgument.getBlockPos(ctx, "a"), b = BlockPosArgument.getBlockPos(ctx, "b");
        if (!(level.getBlockEntity(a) instanceof ConnectorBlockEntity ca) || !(level.getBlockEntity(b) instanceof ConnectorBlockEntity))
            return fail(ctx, "wire: no connector at " + a.toShortString() + " or " + b.toShortString());
        ItemStack coil = new ItemStack(ca.mediumVoltage() ? PcItems.OVERHEAD_CONDUCTOR.get()
                : insulated ? PcItems.WIRE_COIL_LV.get() : PcItems.OVERHEAD_CONDUCTOR.get());
        InteractionResult first = use(level, coil, a), second = use(level, coil, b);
        boolean ok = first.consumesAction() && second.consumesAction() && ca.linkedTo(b);
        log(ctx, "wire " + a.toShortString() + " -> " + b.toShortString() + (ok ? " ok" : " FAILED " + first + "/" + second));
        return ok ? 1 : 0;
    }

    private static InteractionResult use(ServerLevel level, ItemStack coil, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return coil.getItem().useOn(new UseOnContext(level, null, InteractionHand.MAIN_HAND, coil, hit));
    }

    private static int gen(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        if (!(ctx.getSource().getLevel().getBlockEntity(pos) instanceof ShaftGeneratorBlockEntity be) || be.unit() == null)
            return fail(ctx, "gen: no generator twin at " + pos.toShortString());
        GeneratorUnit u = be.unit();
        String cls = StringArgumentType.getString(ctx, "class");
        u.setVoltageClass("mv".equals(cls) ? VoltageClass.MV_6_3 : VoltageClass.LV_0_4);
        int gear = IntegerArgumentType.getInteger(ctx, "gear");
        for (int i = 0; i < GeneratorUnit.GEARS.length && u.gearRpm() != gear; i++) u.cycleGear();
        if (u.gearRpm() != gear) return fail(ctx, "gen: no gear " + gear);
        u.setTrimPermil(IntegerArgumentType.getInteger(ctx, "trim"));
        u.setFieldOn(true);
        log(ctx, "gen " + pos.toShortString() + " " + u.voltageClass() + " gear " + u.gearRpm());
        return 1;
    }

    private static int motor(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        if (!(ctx.getSource().getLevel().getBlockEntity(pos) instanceof ElectricMotorBlockEntity be) || be.unit() == null)
            return fail(ctx, "motor: no motor twin at " + pos.toShortString());
        MotorUnit u = be.unit();
        int gear = IntegerArgumentType.getInteger(ctx, "gear");
        for (int i = 0; i < MotorUnit.GEARS.length && u.gearRpm() != gear; i++) u.cycleGear();
        if (u.gearRpm() != gear) return fail(ctx, "motor: no gear " + gear);
        u.setContactor(true);
        log(ctx, "motor " + pos.toShortString() + " gear " + u.gearRpm() + " contactor closed");
        return 1;
    }

    private static int boiler(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos c = BoilerGeometry.controllerOf(level, BlockPosArgument.getBlockPos(ctx, "pos"));
        SimLevelBinding b = SimBindings.get(level);
        if (c == null || b == null || !(level.getBlockEntity(c) instanceof FireTubeBoilerBlockEntity be) || be.simId().isNone())
            return fail(ctx, "boiler: no boiler twin at " + BlockPosArgument.getBlockPos(ctx, "pos").toShortString());
        double mpa = DoubleArgumentType.getDouble(ctx, "mpa");
        b.world().subsystem(SteamPlant.class).presetWater(be.simId(), mpa * 1e6, DoubleArgumentType.getDouble(ctx, "level"));
        log(ctx, "boiler " + c.toShortString() + " preset " + mpa + " MPa");
        return 1;
    }

    // --- drilling rig: DrillingGameTests.drillIntoGasTakeKickAndKillIt ---

    /**
     * The test's over-pressured gas trap (11.8 kPa/m against 1.10 mud, pay top 800 m) under the rotary table nearest to
     * the console (the console links the nearest one too), spudded at {@code depth} and drilled with 120 kN at 100 spm.
     */
    private static int kick(CommandContext<CommandSourceStack> ctx, double depth) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "console");
        if (!(level.getBlockEntity(pos) instanceof DrillersConsoleBlockEntity c))
            return fail(ctx, "kick: no driller's console at " + pos.toShortString());
        if (!c.rigOk()) return fail(ctx, "kick: rig not assembled, missing " + c.missing());
        BlockPos rotary = nearest(level, pos, 16, 4, p -> level.getBlockState(p).is(PcBlocks.ROTARY_TABLE.get()));
        if (rotary == null) return fail(ctx, "kick: no rotary table near " + pos.toShortString());
        Trap gas = new Trap(rotary.getX(), rotary.getZ(), 80, 800, 60, 860, 15, 0.2, 50, FormationFluid.GAS, 11_800);
        c.spud(new Prospect(gas, 800), depth);
        c.setDrilling(120e3, 100);
        log(ctx, String.format(Locale.ROOT, "kick %s: spudded at %.1f m over the gas trap at 800 m, 120 kN, 100 spm",
                pos.toShortString(), depth));
        return 1;
    }

    /** Barite sacks into the mixing hopper to kill mud + 3 kg/m³, as the test does between the two circulations. */
    private static int barite(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "console");
        if (!(level.getBlockEntity(pos) instanceof DrillersConsoleBlockEntity c) || c.well() == null)
            return fail(ctx, "barite: no well at console " + pos.toShortString());
        KillSheet k = c.killProcedure().sheet();
        if (k == null || c.killProcedure().stage() != DrillersMethod.Stage.WEIGHT_UP)
            return fail(ctx, "barite: kill stage is " + c.killProcedure().stage() + ", weight up comes after the first circulation");
        BlockPos hp = nearest(level, pos, 16, 4, p -> level.getBlockEntity(p) instanceof MudHopperBlockEntity);
        if (hp == null) return fail(ctx, "barite: no mud mixing hopper near " + pos.toShortString());
        Well w = c.well();
        int sacks = (int) Math.ceil(KillSheet.bariteKg(w.pitVolumeM3(), w.pitDensityKgM3(), k.killMudKgM3() + 3) / MudHopperBlockEntity.SACK_KG);
        ((MudHopperBlockEntity) level.getBlockEntity(hp)).useItem(FakePlayerFactory.getMinecraft(level), InteractionHand.MAIN_HAND,
                new ItemStack(PcItems.BARITE_POWDER.get(), sacks));
        boolean ok = w.pitDensityKgM3() >= k.killMudKgM3();
        log(ctx, String.format(Locale.ROOT, "barite %d sacks: pits %.0f kg/m3, kill mud %.0f%s", sacks, w.pitDensityKgM3(),
                k.killMudKgM3(), ok ? "" : " NOT REACHED"));
        return ok ? 1 : 0;
    }

    @Nullable
    private static BlockPos nearest(ServerLevel level, BlockPos from, int r, int ry, Predicate<BlockPos> what) {
        BlockPos best = null;
        for (BlockPos p : BlockPos.betweenClosed(from.offset(-r, -ry, -r), from.offset(r, ry, r)))
            if (what.test(p) && (best == null || p.distSqr(from) < best.distSqr(from))) best = p.immutable();
        return best;
    }

    // --- blast furnace ---

    /**
     * No GameTest takes a furnace past COLD (blow-in is 2 physical days), so this presets the sim state through the
     * furnace's own save/load: the shaft is filled with burden layers made as {@link BlastFurnaceUnit} charges them
     * (default coke assay, sinter at the unit's ore load), then the state is RUNNING after a finished blow-in with a
     * 1600 °C hearth holding iron 0.25 m above the taphole and 0.25 m of slag. Everything afterwards is the real model:
     * blast from the blowers, descent, the drill opening the taphole, flow down the runner into the ladle. Re-run to
     * reheat and refill the hearth.
     */
    private static int furnaceHot(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        if (!(ctx.getSource().getLevel().getBlockEntity(pos) instanceof BlastFurnaceControllerBlockEntity c) || c.shape() == null
                || c.unit() == null || c.unit().furnace() == null)
            return fail(ctx, "furnace: no formed blast furnace controller at " + pos.toShortString());
        BlastFurnaceUnit u = c.unit();
        BlastFurnace f = u.furnace();
        BlastFurnaceSpec spec = c.shape().spec();
        OreChem sinter = OreChem.of(new ItemStack(PcItems.SINTER.get()));
        CoalAssay coke = IronworksComponents.CokeAssay.DEFAULT.toAssay();
        double cokeKg = u.cokeLayerKg(), oreKg = cokeKg * u.oreLoad();
        int layers = 0;
        while (layers < BlastFurnace.MAX_LAYERS && f.charge(new BurdenLayer(oreKg, sinter.fe(), sinter.sio2(), sinter.cao(), cokeKg, coke,
                oreKg * u.limestonePerOre()))) layers++;
        TreeStateWriter w = new TreeStateWriter();
        f.save(w);
        MutableStateTree t = w.root();
        double area = spec.hearthAreaM2(), iron = (spec.tapholeHeightM() + 0.25) * area * BlastFurnace.RHO_IRON,
                slag = 0.25 * area * BlastFurnace.RHO_SLAG;
        double[] v = ((double[]) t.get("scalars")).clone();
        v[0] = 1873.15;                                   // hearth
        v[1] = iron;
        v[2] = 0.045 * iron;                              // C, Si, S of the hot metal
        v[3] = 0.006 * iron;
        v[4] = 0.0003 * iron;
        v[5] = slag;
        v[6] = 0.40 * slag;                               // CaO, SiO2, S of the slag (basicity 1.1)
        v[7] = 0.36 * slag;
        v[8] = 0.01 * slag;
        v[9] = BlastFurnace.BLOW_IN_MIN_S;                // blow-in done
        for (int i = 10; i <= 13; i++) v[i] = 0;          // low blast, no blast, no cooling, hanging timers
        t.put("scalars", v);
        t.put("state", BlastFurnaceState.RUNNING.ordinal());
        f.load(new TreeStateReader(t));
        log(ctx, String.format(Locale.ROOT, "furnace %s: %s, hearth %.0f C, iron %.1f t, slag %.1f t, +%d layers (%d in the shaft), blast %.2f nm3/s",
                pos.toShortString(), f.state(), f.hearthTempK() - 273.15, f.ironKg() / 1000, f.slagKg() / 1000, layers,
                f.layerCount(), u.blastNm3S()));
        return 1;
    }

    // --- VVER: PwrReactorGameTests.losingPumpDriveAtPowerTrips / SevereAccidentGameTests.critical ---

    private static int reactorLoad(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        if (!(ctx.getSource().getLevel().getBlockEntity(pos) instanceof PwrReactorBlockEntity be) || be.reactor() == null)
            return fail(ctx, "reactor: no VVER at " + pos.toShortString());
        PressurizedWaterReactor r = be.reactor();
        double kg = FuelDesign.VVER_440.uKgPerAssembly(0.96, 0.044);
        r.setRods(1, 1);
        int n = 0;
        for (int i = 0; i < PressurizedWaterReactor.POSITIONS; i++) if (r.load(i, FuelDesign.VVER_440, kg, 0.044, 0.0003)) n++;
        log(ctx, "reactor " + pos.toShortString() + ": loaded " + n + ", in core " + r.loadedAssemblies() + " of " + PressurizedWaterReactor.POSITIONS);
        return r.loadedAssemblies() == PressurizedWaterReactor.POSITIONS ? 1 : 0;
    }

    /**
     * The tests' hot start: trip reset (refused until both pumps are at speed and the reset hold has passed), thermal
     * state, scram bank out, control group at 30 %, boron bisected to criticality, core equilibrated at {@code pct}. At
     * 50 % the temperatures are the tests' 800/588/563 K; other powers scale the fuel and hot-leg rise linearly.
     */
    private static int reactorPower(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        if (!(ctx.getSource().getLevel().getBlockEntity(pos) instanceof PwrReactorBlockEntity be) || be.reactor() == null)
            return fail(ctx, "reactor: no VVER at " + pos.toShortString());
        PressurizedWaterReactor r = be.reactor();
        if (r.loadedAssemblies() < PressurizedWaterReactor.POSITIONS)
            return fail(ctx, "reactor: core has " + r.loadedAssemblies() + " assemblies, run /filmset reactor <pos> load first");
        if (r.pumpSpeed(0) < 0.99 || r.pumpSpeed(1) < 0.99)
            return fail(ctx, String.format(Locale.ROOT, "reactor: pumps not at speed (%.2f, %.2f)", r.pumpSpeed(0), r.pumpSpeed(1)));
        if (r.protection().isTripped() && !r.resetTrip())
            return fail(ctx, "reactor: reset refused " + r.protection().activeReasons() + " (wait ~12 s after the pumps and retry)");
        double n = DoubleArgumentType.getDouble(ctx, "pct") / 100;
        r.setThermalState(563 + 474 * n, 563 + 50 * n, 563);
        r.setRods(0, 0.3);
        double lo = 0, hi = 30;
        for (int i = 0; i < 40; i++) {
            double m = (lo + hi) / 2;
            r.setBoronGkg(m);
            if (r.evaluateReactivity() > 0) lo = m;
            else hi = m;
        }
        r.setBoronGkg((lo + hi) / 2);
        r.equilibrate(n);
        log(ctx, String.format(Locale.ROOT, "reactor %s: equilibrated at %.0f %%, boron %.2f g/kg, power %.3f, tripped %s",
                pos.toShortString(), n * 100, r.boronGkg(), r.power(), r.protection().isTripped()));
        return 1;
    }

    private static int probe(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        BlockEntity be = ctx.getSource().getLevel().getBlockEntity(pos);
        BlockState s = ctx.getSource().getLevel().getBlockState(pos);
        String what;
        if (be instanceof ShaftGeneratorBlockEntity g && g.unit() != null) {
            GeneratorUnit u = g.unit();
            what = String.format(Locale.ROOT, "generator %s gear %d speed %.3f pu, %.0f V, field %s", u.voltageClass(), u.gearRpm(),
                    u.speedPu(), u.terminalVoltageV(), u.fieldOn());
        } else if (be instanceof LightingBoardBlockEntity lb && lb.unit() != null) {
            LightingBoardUnit u = lb.unit();
            what = String.format(Locale.ROOT, "lighting board in %.3f pu, lit %s, demand %.0f W, draw %.0f W", u.inputPu(),
                    u.lampsLit(), u.demandW(), u.powerW());
        } else if (be instanceof SteamEngineBlockEntity e && e.unit() != null) {
            what = String.format(Locale.ROOT, "steam engine valve %s, boiler %s, %.2f MPa, omega %.2f, port %.1f rpm",
                    e.unit().stopValveOpen(), e.unit().boiler(), e.gaugePressurePa() / 1e6, e.unit().omega(), e.portRpm());
        } else if (be instanceof FireTubeBoilerBlockEntity b) {
            what = String.format(Locale.ROOT, "boiler %.2f MPa, glass %.2f, relief %s", b.gaugePressurePa() / 1e6,
                    b.glassFraction(), b.reliefOpen());
            if (b.unit() != null)
                what += String.format(Locale.ROOT, ", water %.1f C, lit %s, fuel %s, smoky %s, ash %.1f kg",
                        b.unit().waterTempK() - 273.15, b.unit().lit(), b.unit().fuelKey(), b.unit().smoky(), b.unit().ashKg());
        } else if (be instanceof ElectricMotorBlockEntity m && m.unit() != null) {
            MotorUnit u = m.unit();
            what = String.format(Locale.ROOT, "motor %s speed %.3f pu, %.1f A, %.0f W", u.state(), u.speedPu(), u.currentA(), u.powerW());
        } else if (be instanceof DrillersConsoleBlockEntity c && c.well() != null) {
            Well w = c.well();
            what = String.format(Locale.ROOT, "well %.2f m, rop %.1f m/h, in %.4f out %.4f m3/s, influx %.5f m3/s, gain %.2f m3, kick %s, "
                    + "bop %s, SIDPP %.2f SICP %.2f MPa, pits %.0f kg/m3, kill %s, blowout %s", w.holeDepthM(), w.ropMs() * 3600,
                    w.pumpRateM3s(), w.flowOutM3s(), w.influxM3s(), w.pitGainM3(), w.kickAlarm(), w.bopClosed() ? "closed" : "open",
                    w.drillPipePressurePa() / 1e6, w.casingPressurePa() / 1e6, w.pitDensityKgM3(), c.killProcedure().stage(), w.blowout());
        } else if (be instanceof BlastFurnaceControllerBlockEntity c && c.unit() != null && c.unit().furnace() != null) {
            BlastFurnace f = c.unit().furnace();
            what = String.format(Locale.ROOT, "blast furnace %s, hearth %.0f C, iron %.1f t, slag %.1f t, layers %d, blast %.2f nm3/s at %.0f C, "
                    + "taphole %s %.0f mm", f.state(), f.hearthTempK() - 273.15, f.ironKg() / 1000, f.slagKg() / 1000, f.layerCount(),
                    c.unit().blastNm3S(), c.unit().blastK() - 273.15, f.tapholeOpen() ? "open" : "shut", f.tapholeDiameterM() * 1000);
        } else if (be instanceof PwrReactorBlockEntity p && p.reactor() != null) {
            PressurizedWaterReactor r = p.reactor();
            what = String.format(Locale.ROOT, "VVER power %.3f, %d assemblies, rg %.2f, boron %.2f g/kg, cold leg %.1f C, %.2f MPa, "
                    + "pumps %.2f/%.2f, tripped %s first-out %s", r.power(), r.loadedAssemblies(), r.rg(), r.boronGkg(),
                    r.coldLegK() - 273.15, r.pressure() / 1e6, r.pumpSpeed(0), r.pumpSpeed(1), r.protection().isTripped(),
                    r.protection().firstOut());
        } else if (s.hasProperty(BlockStateProperties.LIT)) {
            what = s.getBlock().getDescriptionId() + " lit " + s.getValue(BlockStateProperties.LIT);
        } else {
            what = s.toString() + (be == null ? "" : " " + be.getClass().getSimpleName());
        }
        if (be instanceof KineticBlockEntity k)
            what += String.format(Locale.ROOT, " | create %.1f rpm%s, stress@32 %.0f su, capacity@32 %.0f su", k.getSpeed(),
                    k.isOverStressed() ? " OVERSTRESSED" : "", k.calculateStressApplied() * 32,
                    k.calculateAddedStressCapacity() * 32);
        log(ctx, "probe " + pos.toShortString() + ": " + what);
        return 1;
    }

    private static void log(CommandContext<CommandSourceStack> ctx, String msg) {
        LOG.info("[filmset] {}", msg);
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String msg) {
        LOG.warn("[filmset] {}", msg);
        ctx.getSource().sendFailure(Component.literal(msg));
        return 0;
    }
}
