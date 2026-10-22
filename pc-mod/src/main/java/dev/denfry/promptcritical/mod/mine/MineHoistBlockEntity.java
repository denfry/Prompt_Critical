package dev.denfry.promptcritical.mod.mine;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.item.RopeStateData;
import dev.denfry.promptcritical.mod.item.WireRopeItem;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.ops.OpsActions;
import dev.denfry.promptcritical.mod.ops.OpsMachine;
import dev.denfry.promptcritical.mod.ops.OpsWorld;
import dev.denfry.promptcritical.sim.mining.Hoist;
import dev.denfry.promptcritical.sim.mining.HoistDuty;
import dev.denfry.promptcritical.sim.mining.HoistSubsystem;
import dev.denfry.promptcritical.sim.mining.HoistUnit;
import dev.denfry.promptcritical.sim.mining.RopeState;
import dev.denfry.promptcritical.sim.ops.Baseline;
import dev.denfry.promptcritical.sim.ops.CheckInput;
import dev.denfry.promptcritical.sim.ops.FaultSource;
import dev.denfry.promptcritical.sim.ops.MaintenanceFaultKind;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.UnitKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Подъёмная машина (08 §8.2.8) в мире: связывает доменный {@link HoistUnit} с копром, стволом и клетью. Клеть на
 * посадке — блоки; при отправке она собирается в контрапцию Create {@link CageContraption}, которую эта машина
 * ведёт по глубине двойника (контроллер {@link IControlContraption}), а на посадке разбирается обратно. Груз —
 * люди (80 кг), тележки и предметы в клети на момент отправки; назначение (людской, грузо-людской, грузовой) — по
 * тому, кто в клети. Клиент получает глубину и скорость и сам ведёт клеть между пакетами.
 *
 * <p>Эксплуатация ({@link OpsMachine}, 13 §13.6.5–13.6.7): тормоз со сбитым зазором держит только долю расчётного
 * момента до испытания ключом под грузом; пробный пуск — один рейс на всю глубину ствола; износ каната — строкой
 * щита обслуживания; заплавленный подшипник клинит вал.
 */
public final class MineHoistBlockEntity extends DeviceBlockEntity implements ShaftConsumer, IControlContraption, OpsMachine {
    /** Масса человека, тележки, предмета в клети, кг [BALANCE]. */
    public static final double PERSON_KG = 80, CART_KG = 300, ITEM_KG = 1;
    private static final int SCAN_PERIOD_TICKS = 100, SYNC_PERIOD_TICKS = 2;
    /** Груз, от которого считается доля для испытания тормоза, кг [BALANCE]. */
    public static final double RATED_PAYLOAD_KG = 1000;
    /** Пробный пуск — рейс не короче этой доли глубины ствола. */
    public static final double TRIAL_TRIP_FRACTION = 0.9;

    @Nullable private HoistGeometry.Headframe headframe;
    @Nullable private HoistGeometry.Shaft shaft;
    @Nullable private ControlledContraptionEntity cage;
    private HoistText.Problem problem = HoistText.Problem.NO_HEADFRAME;
    private boolean chunkUnloading, dirtySync, seized;
    /** Глубина начала рейса; NaN — клеть стоит. */
    private double tripFromM = Double.NaN;
    private long nextScan;

    // синхронизируется клиенту
    @Nullable private BlockPos sheave;
    private double depth, speed;

    // клиент
    private double clientDepth;
    private float drumAngle, drumAnglePrev;

    public MineHoistBlockEntity(BlockPos pos, BlockState state) {
        super(MineBlockEntities.MINE_HOIST.get(), pos, state);
    }

    public Direction facing() {
        return MineHoistBlock.class.cast(getBlockState().getBlock()).facing(getBlockState());
    }

    @Nullable
    private HoistUnit unit() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        return b == null || simId().isNone() ? null : b.world().subsystem(HoistSubsystem.class).unit(b.world().topology(), simId());
    }

    // --- тик ------------------------------------------------------------------------------------------------

    public void tick() {
        if (level == null) return;
        if (level.isClientSide) {
            clientTick();
            return;
        }
        HoistUnit u = unit();
        if (u == null) return;
        long now = level.getGameTime();
        boolean idle = u.target() < 0 && u.speedMs() == 0;
        if (now >= nextScan && idle) {
            rescan(u);
            nextScan = now + SCAN_PERIOD_TICKS;
        }
        double d = u.positionM(), v = u.speedMs();
        operations(u, d, v);
        if (d != depth || v != speed) dirtySync = true;
        depth = d;
        speed = v;
        if (cage != null && !cage.isAlive()) cage = null;
        if (cage != null && shaft != null) {
            placeCage(depth);
            int at = u.landingAtCage();
            if (u.target() < 0 && at >= 0 && v == 0) disassemble(shaft.landings()[at]);
        }
        BlockState s = getBlockState();
        boolean running = v != 0;
        if (s.getValue(PcProperties.RUNNING) != running) level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, running), Block.UPDATE_CLIENTS);
        if (dirtySync && (now % SYNC_PERIOD_TICKS == 0 || v == 0)) {
            dirtySync = false;
            level.sendBlockUpdated(worldPosition, s, s, Block.UPDATE_CLIENTS);
        }
    }

    /** Тормоз по пусконаладке, канат и подшипник на щите обслуживания, пробный рейс (13 §13.6.6). */
    private void operations(HoistUnit u, double d, double v) {
        OpsSubsystem ops = OpsWorld.ops(level);
        OpsSubsystem.Unit ou = OpsWorld.ensure(level, simId(), UnitKind.MINE_HOIST, 0, false);
        if (ops == null || ou == null) return;
        if (level.getGameTime() % 20 == 0) ops.attach(simId(), FaultSource.of(MaintenanceFaultKind.ROPE_WEAR,
                () -> u.rope() == null ? 0 : u.rope().damage(), () -> { }));
        if (u.hoist() != null) u.hoist().setBrakeFactor(ops.commissioning().brakeTorqueFactor(simId()));
        ou.setRunning(v != 0, false, Double.NaN);
        seized = ou.seized();
        if (v != 0 && Double.isNaN(tripFromM)) tripFromM = d;
        else if (v == 0 && !Double.isNaN(tripFromM)) {
            if (shaft != null && Math.abs(d - tripFromM) >= TRIAL_TRIP_FRACTION * shaft.depth()) {
                ops.perform(simId(), new CheckInput.TrialRun(ops.config().trialRunS(), 1, false, true, opsCurrent()), null);
            }
            tripFromM = Double.NaN;
        }
    }

    /** Груз в клети к доле {@link #RATED_PAYLOAD_KG}: клеть на посадке — по содержимому, в пути — по рейсу. */
    public double cageLoadFraction() {
        HoistUnit u = unit();
        if (u == null) return 0;
        double kg = u.hoist() == null ? 0 : u.hoist().payloadKg();
        if (cage == null && shaft != null) {
            BlockPos cagePos = shaft.cageAt((int) Math.round(u.positionM()));
            BlockState cs = level.getBlockState(cagePos);
            if (cs.getBlock() instanceof MineCageBlock mc && mc.isController(cs)) kg = load(cagePos, mc, cs).kg();
        }
        return kg / RATED_PAYLOAD_KG;
    }

    private void clientTick() {
        drumAnglePrev = drumAngle;
        drumAngle += (float) (speed / HoistUnit.DRUM_RADIUS_M * 0.05);
        clientDepth -= speed * 0.05;
        clientDepth += (depth - clientDepth) * 0.3;
        if (Math.abs(depth - clientDepth) > 2) clientDepth = depth;
        if (cage != null && shaft != null) placeCage(clientDepth);
    }

    private void placeCage(double d) {
        cage.setPos(shaft.cageX(), shaft.topY() - d, shaft.cageZ());
    }

    /** Найти копёр, клеть и ствол; передать двойнику посадки и фактическую глубину клети. */
    private void rescan(HoistUnit u) {
        HoistGeometry.Headframe hf = headframe != null ? HoistGeometry.stackAt(level, headframe.bottom()) : null;
        if (hf == null) hf = HoistGeometry.findHeadframe(level, worldPosition, facing());
        headframe = hf;
        BlockPos newSheave = hf == null ? null : hf.top();
        if (!java.util.Objects.equals(newSheave, sheave)) dirtySync = true;
        sheave = newSheave;
        if (hf == null || !hf.formed()) {
            problem = hf == null ? HoistText.Problem.NO_HEADFRAME : HoistText.Problem.NOT_FORMED;
            if (cage == null) setShaft(u, null);
            return;
        }
        BlockPos cagePos = null;
        int cx, cz;
        Direction cf;
        if (cage != null && shaft != null) {
            cx = shaft.cageX();
            cz = shaft.cageZ();
            cf = shaft.cageFacing();
        } else {
            cagePos = HoistGeometry.findCage(level, hf);
            if (cagePos == null) {
                problem = HoistText.Problem.NO_CAGE;
                setShaft(u, null);
                return;
            }
            cx = cagePos.getX();
            cz = cagePos.getZ();
            cf = MineCageBlock.class.cast(level.getBlockState(cagePos).getBlock()).facing(level.getBlockState(cagePos));
        }
        HoistGeometry.Shaft s = HoistGeometry.scanShaft(level, hf, cx, cz, cf);
        if (s == null) {
            problem = HoistText.Problem.NO_SHAFT;
            if (cage == null) setShaft(u, null);
            return;
        }
        setShaft(u, s);
        problem = u.rope() == null ? HoistText.Problem.NO_ROPE : HoistText.Problem.NONE;
        if (cagePos != null) u.seatCage(s.topY() - cagePos.getY());
    }

    private void setShaft(HoistUnit u, @Nullable HoistGeometry.Shaft s) {
        if (s == null ? shaft != null : shaft == null || !java.util.Arrays.equals(s.landings(), shaft.landings())
                || s.cageX() != shaft.cageX() || s.cageZ() != shaft.cageZ() || s.topY() != shaft.topY()) dirtySync = true;
        shaft = s;
        double[] d = new double[s == null ? 0 : s.landings().length];
        for (int i = 0; i < d.length; i++) d[i] = s.landings()[i];
        u.setShaft(d);
        setChanged();
    }

    // --- клеть ---------------------------------------------------------------------------------------------

    private boolean assemble(BlockPos cagePos) {
        CageContraption c = new CageContraption();
        if (!c.assemble(level, cagePos)) return false;
        c.removeBlocksFromWorld(level, BlockPos.ZERO);
        ControlledContraptionEntity e = ControlledContraptionEntity.create(level, this, c);
        e.setPos(cagePos.getX(), cagePos.getY(), cagePos.getZ());
        level.addFreshEntity(e);
        cage = e;
        return true;
    }

    private void disassemble(int landingDepth) {
        if (cage == null || shaft == null) return;
        placeCage(landingDepth);
        cage.disassemble();
        cage = null;
        nextScan = 0;
    }

    /** Груз клети на посадке: люди, тележки, предметы. */
    private record Load(double kg, HoistDuty duty) {
    }

    private Load load(BlockPos cagePos, MineCageBlock block, BlockState state) {
        AABB box = null;
        for (Footprint.Cell c : block.spec().footprint().cells(cagePos, block.facing(state))) {
            AABB b = new AABB(c.pos());
            box = box == null ? b : box.minmax(b);
        }
        double kg = 0;
        int people = 0, cargo = 0;
        for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof LivingEntity || e instanceof AbstractMinecart || e instanceof ItemEntity)) {
            if (e instanceof Player || e instanceof LivingEntity) {
                kg += PERSON_KG;
                people++;
            } else if (e instanceof AbstractMinecart) {
                kg += CART_KG;
                cargo++;
            } else if (e instanceof ItemEntity it) {
                kg += ITEM_KG * it.getItem().getCount();
                cargo++;
            }
        }
        HoistDuty duty = people == 0 ? HoistDuty.CARGO : cargo == 0 ? HoistDuty.PEOPLE : HoistDuty.MIXED;
        return new Load(kg, duty);
    }

    // --- панель ---------------------------------------------------------------------------------------------

    public void openPanel(ServerPlayer player) {
        int[] landings = shaft == null ? new int[0] : shaft.landings();
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new HoistMenu(id, inv, this, landings), HoistText.msg("hoist.title")), buf -> {
                    buf.writeBlockPos(worldPosition);
                    buf.writeVarIntArray(landings);
                });
    }

    /** Кнопка панели: посадка {@code 0…}, стоп, сброс, предел скорости. */
    public void onButton(Player player, int id) {
        HoistUnit u = unit();
        if (u == null) return;
        switch (id) {
            case HoistMenu.BUTTON_STOP -> u.stop();
            case HoistMenu.BUTTON_RESET -> u.reset();
            case HoistMenu.BUTTON_SLOWER -> u.setSpeedLimit(Math.round(u.speedLimitMs()) - 1);
            case HoistMenu.BUTTON_FASTER -> u.setSpeedLimit(Math.round(u.speedLimitMs()) + 1);
            default -> {
                if (id >= 0 && id < HoistGeometry.MAX_LANDINGS) depart(player, u, id);
            }
        }
        setChanged();
    }

    private void depart(Player player, HoistUnit u, int landing) {
        if (!OpsActions.startAllowed(player, level, this)) return;
        if (shaft == null || !u.ready()) {
            player.displayClientMessage(HoistText.msg("hoist.blocked", HoistText.problem(problem == HoistText.Problem.NONE
                    ? HoistText.Problem.NO_ROPE : problem)), true);
            return;
        }
        if (landing >= shaft.landings().length) return;
        if (u.landingAtCage() == landing && cage == null) {
            player.displayClientMessage(HoistText.msg("hoist.here"), true);
            return;
        }
        BlockPos cagePos = null;
        HoistDuty duty = u.hoist().duty();
        double kg = u.hoist().payloadKg();
        if (cage == null) {
            cagePos = shaft.cageAt((int) Math.round(u.positionM()));
            BlockState cs = level.getBlockState(cagePos);
            if (!(cs.getBlock() instanceof MineCageBlock mc) || !mc.isController(cs)) {
                player.displayClientMessage(HoistText.msg("hoist.blocked", HoistText.problem(HoistText.Problem.NO_CAGE)), true);
                nextScan = 0;
                return;
            }
            if (mc.open(cs)) {
                player.displayClientMessage(HoistText.msg("hoist.door_open"), true);
                return;
            }
            Load l = load(cagePos, mc, cs);
            duty = l.duty();
            kg = l.kg();
        }
        Hoist.Verdict v = u.depart(landing, duty, kg);
        if (v == null) {
            player.displayClientMessage(HoistText.msg("hoist.blocked", HoistText.state(u.hoist().state())), true);
            return;
        }
        if (v == Hoist.Verdict.FORBIDDEN) {
            double need = ServerConfigHoist.required(duty);
            player.displayClientMessage(HoistText.msg("hoist.forbidden", HoistText.num(u.hoist().safetyFactor(kg)),
                    HoistText.num(need), HoistText.duty(duty)), false);
            return;
        }
        if (v == Hoist.Verdict.UNSAFE_OPERATION) player.displayClientMessage(HoistText.msg("hoist.unsafe"), false);
        if (cagePos != null && !assemble(cagePos)) u.stop();
    }

    /** Повесить канат из стопки в руке: нужно ⌈(глубина + подвеска)/длина бухты⌉ бухт. */
    public ItemInteractionResult installRope(Player player, ItemStack stack) {
        HoistUnit u = unit();
        if (u == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (headframe == null || shaft == null) {
            player.displayClientMessage(HoistText.msg("hoist.blocked", HoistText.problem(problem)), true);
            return ItemInteractionResult.CONSUME;
        }
        if (u.target() >= 0 || u.speedMs() != 0) {
            player.displayClientMessage(HoistText.msg("hoist.rope_busy"), true);
            return ItemInteractionResult.CONSUME;
        }
        double length = HoistUnit.ropeLengthM(shaft.depth(), HoistGeometry.reachM(worldPosition, headframe));
        int coils = (int) Math.ceil(length / ServerConfig.MINING_ROPE_ITEM_LENGTH_M.get());
        if (!player.getAbilities().instabuild && stack.getCount() < coils) {
            player.displayClientMessage(HoistText.msg("hoist.rope_need", HoistText.num(length), coils), true);
            return ItemInteractionResult.CONSUME;
        }
        RopeStateData d = WireRopeItem.rope(stack);
        if (!player.getAbilities().instabuild) stack.shrink(coils);
        u.installRope(d.fresh());
        problem = HoistText.Problem.NONE;
        player.displayClientMessage(HoistText.msg("hoist.rope_installed", d.diameterMm(), HoistText.num(length)), true);
        setChanged();
        return ItemInteractionResult.CONSUME;
    }

    /** Показания для панели (короткие целые: пакет данных меню передаёт short). */
    final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            HoistUnit u = unit();
            Hoist h = u == null ? null : u.hoist();
            RopeState r = u == null ? null : u.rope();
            return switch (i) {
                case HoistMenu.D_STATE -> h == null ? -1 : h.state().ordinal();
                case HoistMenu.D_DEPTH_DM -> (int) Math.round(depth * 10);
                case HoistMenu.D_SPEED_CMS -> (int) Math.round(speed * 100);
                case HoistMenu.D_TARGET -> u == null ? -1 : u.target();
                case HoistMenu.D_AT -> u == null ? -1 : u.landingAtCage();
                case HoistMenu.D_LIMIT_DMS -> u == null ? 0 : (int) Math.round(u.speedLimitMs() * 10);
                case HoistMenu.D_PAYLOAD_KG -> h == null ? 0 : (int) Math.min(32_000, Math.round(h.payloadKg()));
                case HoistMenu.D_SF_X10 -> h == null ? 0 : (int) Math.min(3_000, Math.round(h.safetyFactor() * 10));
                case HoistMenu.D_SF_NEED_X10 -> h == null ? 0 : (int) Math.round(ServerConfigHoist.required(h.duty()) * 10);
                case HoistMenu.D_POWER_KW -> u == null ? 0 : (int) Math.round(u.powerW() / 1000);
                case HoistMenu.D_AVAILABLE_KW -> u == null ? 0 : (int) Math.round(u.availableW() / 1000);
                case HoistMenu.D_ROPE_MM -> r == null ? 0 : (int) Math.round(r.diameterMm());
                case HoistMenu.D_ROPE_WEAR -> r != null && r.worn() ? 1 : 0;
                case HoistMenu.D_PROBLEM -> problem.ordinal();
                case HoistMenu.D_DUTY -> h == null ? 0 : h.duty().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return HoistMenu.DATA_COUNT;
        }
    };

    boolean stillValid(Player player) {
        return !isRemoved() && player.distanceToSqr(worldPosition.getCenter()) <= 64;
    }

    // --- ShaftConsumer ---------------------------------------------------------------------------------------

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        HoistUnit u = unit();
        if (u != null) u.shaft.sample(rpm, healthy, tick);
    }

    @Override
    public void sampleSparePowerW(double watts) {
        HoistUnit u = unit();
        if (u != null) u.setSparePowerW(watts);
    }

    @Override
    public double shaftPowerW() {
        HoistUnit u = unit();
        if (seized) return 100 * HoistUnit.RATED_W;
        return u == null ? 0 : u.powerW();
    }

    // --- эксплуатация ---

    @Override
    public UnitKind opsKind() {
        return UnitKind.MINE_HOIST;
    }

    @Override
    public boolean opsStopped() {
        return speed == 0;
    }

    /** Подшипник барабана. */
    @Override
    public Vec3 opsBearingPos() {
        return Vec3.atCenterOf(worldPosition).add(0, 0.4, 0);
    }

    @Override
    public Baseline opsCurrent() {
        HoistUnit u = unit();
        return u == null || u.hoist() == null ? Baseline.NONE : new Baseline(Double.NaN, Double.NaN, Double.NaN, Double.NaN, u.hoist().drumOmega(), Double.NaN);
    }

    @Override
    public double ratedShaftW() {
        return HoistUnit.RATED_W;
    }

    @Override
    public double nominalShaftRpm() {
        return HoistUnit.NOMINAL_RPM;
    }

    // --- IControlContraption --------------------------------------------------------------------------------

    @Override
    public boolean isAttachedTo(AbstractContraptionEntity contraption) {
        return contraption == cage;
    }

    @Override
    public void attach(ControlledContraptionEntity contraption) {
        cage = contraption;
        clientDepth = depth;
    }

    @Override
    public void onStall() {
    }

    @Override
    public boolean isValid() {
        return !isRemoved();
    }

    @Override
    public BlockPos getBlockPosition() {
        return worldPosition;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        chunkUnloading = true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        chunkUnloading = false;
    }

    @Override
    public void setRemoved() {
        // машину сломали — клеть встаёт блоками там, где была (при выгрузке чанка контрапция сохраняется сама)
        if (level instanceof ServerLevel && !chunkUnloading && cage != null && cage.isAlive()) {
            cage.disassemble();
            cage = null;
        }
        super.setRemoved();
    }

    // --- клиент: рендер -----------------------------------------------------------------------------------

    /** Угол барабана, рад. */
    public float drumAngle(float partialTicks) {
        return drumAnglePrev + (drumAngle - drumAnglePrev) * partialTicks;
    }

    @Nullable
    public BlockPos sheave() {
        return sheave;
    }

    @Nullable
    public HoistGeometry.Shaft shaft() {
        return shaft;
    }

    /** Глубина клети на клиенте, м. */
    public double renderDepth(float partialTicks) {
        return clientDepth - speed * 0.05 * partialTicks;
    }

    public double speedMs() {
        return speed;
    }

    // --- сохранение и синхронизация -------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeShared(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readShared(tag);
    }

    private void writeShared(CompoundTag tag) {
        if (sheave != null) tag.putLong("sheave", sheave.asLong());
        if (shaft != null) {
            CompoundTag s = new CompoundTag();
            s.putInt("x", shaft.cageX());
            s.putInt("z", shaft.cageZ());
            s.putInt("top", shaft.topY());
            s.putInt("facing", shaft.cageFacing().get2DDataValue());
            s.putIntArray("landings", shaft.landings());
            tag.put("shaft", s);
        }
        if (headframe != null) tag.putLong("headframe", headframe.bottom().asLong());
    }

    private void readShared(CompoundTag tag) {
        sheave = tag.contains("sheave") ? BlockPos.of(tag.getLong("sheave")) : null;
        if (tag.contains("shaft")) {
            CompoundTag s = tag.getCompound("shaft");
            shaft = new HoistGeometry.Shaft(s.getInt("x"), s.getInt("z"), Direction.from2DDataValue(s.getInt("facing")),
                    s.getInt("top"), s.getIntArray("landings"));
        } else {
            shaft = null;
        }
        if (tag.contains("headframe") && level != null && !level.isClientSide) {
            headframe = HoistGeometry.stackAt(level, BlockPos.of(tag.getLong("headframe")));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeShared(tag);
        tag.putDouble("depth", depth);
        tag.putDouble("speed", speed);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readShared(tag);
        depth = tag.getDouble("depth");
        speed = tag.getDouble("speed");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Нормы запаса прочности по назначению — из конфига сервера. */
    private static final class ServerConfigHoist {
        static double required(HoistDuty duty) {
            return switch (duty) {
                case PEOPLE -> ServerConfig.MINING_HOIST_SF_PEOPLE.get();
                case MIXED -> ServerConfig.MINING_HOIST_SF_MIXED.get();
                case CARGO -> ServerConfig.MINING_HOIST_SF_CARGO.get();
            };
        }
    }
}
