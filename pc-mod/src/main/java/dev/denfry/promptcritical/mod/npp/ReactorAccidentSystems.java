package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.fx.WorldFx;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.accident.CoreMeltProgression;
import dev.denfry.promptcritical.sim.accident.HydrogenCombustion;
import dev.denfry.promptcritical.sim.accident.PlantAccident;
import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.sim.consequence.DamageRequest;
import dev.denfry.promptcritical.sim.radiation.FissionSource;
import dev.denfry.promptcritical.sim.consequence.ConsequenceRouter;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * World side of {@link PlantAccident} for a light-water reactor block: finds the ECCS trains, the accumulators and the
 * containment around the reactor, turns a torn-out primary-loop block or the drill button into a break, decides
 * whether the safety buses have AC, steps the accident layer and routes its events — core uncovery, cladding failure,
 * a hydrogen burn, a containment breach — into messages, the damage budget and the radiation field.
 *
 * <p>AC on the safety buses is a turning shaft: an ECCS pump or a coolant pump whose shaft is driven by a grid motor
 * or an emergency diesel. When every one of them stops, the plant is in station blackout.
 */
public final class ReactorAccidentSystems {
    public static final int LINK_RADIUS = 12;
    /** I-131 released per failed assembly with the coolant, Bq (1 % of the gap inventory, as for the PWR's DNB failures). */
    static final double I131_PER_FAILED_BQ = PwrReactorBlockEntity.I131_PER_FAILED_BQ;
    /** A surge line (DN300 on a VVER-1000) has about 6 % of the area of the double-ended main pipe break. */
    static final double SURGE_LINE_SHARE = 0.06;
    private static long requestSeq;

    /** Kind of primary-loop block: its loss tears a pipe of the given share of the design large break. */
    public enum LoopPart {
        PUMP(1.0), PRESSURIZER(SURGE_LINE_SHARE);

        final double share;

        LoopPart(double share) {
            this.share = share;
        }

        /** The block is still standing at {@code p}, whether or not the reactor picked it this time. */
        boolean standsAt(ServerLevel sl, BlockPos p) {
            BlockEntity be = sl.getBlockEntity(p);
            return this == PUMP ? be instanceof PrimaryPumpBlockEntity : be instanceof PressurizerBlockEntity;
        }
    }

    private final PlantAccident accident;
    private final List<BlockPos> eccsPumps = new ArrayList<>();
    private final Map<BlockPos, LoopPart> loop = new HashMap<>();
    private final Map<BlockPos, LoopPart> broken = new HashMap<>();
    private int drill;
    /** A rod-housing nozzle torn open by an ejected cluster (04c §4c.5.1): a small break until the drill is reset. */
    private boolean housingBreak;
    /** Water in the reactor cavity below the vessel (scan), and the lowest block the corium has reached (null — in the vessel). */
    private boolean cavityFlooded;
    @org.jetbrains.annotations.Nullable
    private BlockPos coriumFront;
    private int accumulators;
    private ContainmentScan containment = ContainmentScan.OPEN;
    /** A scan has given a definite answer since the block loaded; until then the saved containment is left alone. */
    private boolean containmentKnown;
    private boolean sboAnnounced, locaAnnounced;
    private int releaseTicks;
    private double releaseBqS;
    /** I-131 already let out of the open building by the gap and in-vessel release, Bq. */
    private double releasedIodineBq, releasedCesiumBq;
    /** 04c §4c.8.4 (а): gap release of halogens and alkali metals at cladding failure (NUREG-1465). */
    static final double GAP_FRACTION = 0.05;
    /** 04c §4c.8.4 (б): gap + early in-vessel release limits, halogens 0.40, alkali metals 0.30 (NUREG-1465 PWR). */
    static final double HALOGEN_MAX = 0.40, ALKALI_MAX = 0.30;
    /** Share of what the core has given up that leaves the building each second (a ~10 s holdup in the hall). */
    static final double OPEN_RELEASE_PER_S = 0.1;
    private int ticks;
    /** Тики, пока расплав из прорванного днища ещё льётся струёй на дно шахты (видимый поток, не физика). */
    private int pourTicks;
    /** Где струя расплава бьёт в пол шахты: лужа растекается отсюда, пока фронт MCCI уходит вниз. */
    @org.jetbrains.annotations.Nullable private BlockPos pourAt;

    public ReactorAccidentSystems(PlantAccident.Design design) {
        this.accident = new PlantAccident(design);
    }

    public PlantAccident accident() {
        return accident;
    }

    public ContainmentScan containment() {
        return containment;
    }

    public int eccsTrains() {
        return eccsPumps.size();
    }

    public int accumulators() {
        return accumulators;
    }

    /** Positions of torn-out loop blocks still open. */
    public Map<BlockPos, LoopPart> brokenLoop() {
        return broken;
    }

    public int drillStage() {
        return drill;
    }

    /** The accident layer has taken over (break, blackout boil-off): the reactor must stay scrammed. */
    public boolean demandsTrip() {
        return accident.active() || accident.breakArea() > 0;
    }

    // ------------------------------------------------------------------ linking

    /**
     * Finds the ECCS pumps and accumulators near the reactor and checks the primary loop.
     *
     * @param loopNow     the coolant pumps and pressurizer the reactor found this time
     * @param pressurized the primary holds fuel and pressure, so losing a loop block is a break, not dismantling
     */
    public void link(ServerLevel sl, BlockPos center, Map<BlockPos, LoopPart> loopNow, boolean pressurized) {
        eccsPumps.clear();
        AABB box = new AABB(center).inflate(LINK_RADIUS);
        for (EccsPumpBlockEntity p : Ironworks.blockEntities(sl, box, EccsPumpBlockEntity.class)) eccsPumps.add(p.getBlockPos());
        // a block of the loop that was there and is gone from a loaded chunk is a torn pipe; one still standing but
        // not picked this time (the reactor takes only as many pumps as it has loops) is not
        for (Map.Entry<BlockPos, LoopPart> e : loop.entrySet()) {
            BlockPos p = e.getKey();
            if (loopNow.containsKey(p) || !sl.isLoaded(p) || e.getValue().standsAt(sl, p)) continue;
            if (pressurized) broken.put(p.immutable(), e.getValue());
        }
        // a loop block back in place closes its break
        broken.entrySet().removeIf(e -> loopNow.containsKey(e.getKey()) || sl.isLoaded(e.getKey()) && e.getValue().standsAt(sl, e.getKey()));
        loop.clear();
        loop.putAll(loopNow);
    }

    /** Slow scans: containment and accumulators (every 5 s, like the PWR's containment scan). */
    public void scan(ServerLevel sl, BlockPos center) {
        ContainmentScan c = ContainmentScan.scan(sl, center);
        if (c != null) {
            containment = c;
            containmentKnown = true;
        }
        int n = 0;
        int r = LINK_RADIUS;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    m.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (sl.isLoaded(m) && sl.getBlockState(m).is(PcBlocks.ECCS_ACCUMULATOR.get())) n++;
                }
        accumulators = n;
        // the reactor cavity: the 3×3 under the vessel, flooded when most of it is water
        BlockPos floor = underVessel(sl, center);
        int water = 0;
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) {
                m.set(floor.getX() + x, floor.getY(), floor.getZ() + z);
                if (sl.isLoaded(m) && sl.getFluidState(m).is(net.minecraft.tags.FluidTags.WATER)) water++;
            }
        cavityFlooded = water >= 5;
    }

    /** The block under the vessel's footprint — the cavity floor where a failed head drops its corium. */
    static BlockPos underVessel(ServerLevel sl, BlockPos center) {
        int minY = sl.getBlockState(center).getBlock() instanceof dev.denfry.promptcritical.mod.block.PcBlock pb
                ? pb.spec().footprint().minY() : 0;
        BlockPos p = center.below(1 - minY);
        // a vessel hung over an open cavity: the melt falls through the air to lie on the cavity floor (or in its water)
        for (int i = 0; i < CAVITY_DEPTH && sl.isLoaded(p.below()) && sl.getBlockState(p).isAir() && sl.getBlockState(p.below()).isAir(); i++)
            p = p.below();
        return p;
    }

    /** Глубже этого под днищем дно шахты не ищется, блоков. */
    static final int CAVITY_DEPTH = 16;

    public boolean cavityFlooded() {
        return cavityFlooded;
    }

    /** Lowest block the corium has reached outside the vessel, or null. */
    @org.jetbrains.annotations.Nullable
    public BlockPos coriumFront() {
        return coriumFront;
    }

    // ------------------------------------------------------------------ drill

    /** Initiating-event drill: no break → small break → guillotine break → isolated. */
    public void cycleDrill(ServerPlayer player) {
        drill = (drill + 1) % 3;
        player.displayClientMessage(NppText.msg("accident_drill_" + drill).withStyle(drill > 0 ? ChatFormatting.RED : ChatFormatting.GREEN), true);
    }

    private double breakArea() {
        PlantAccident.Design d = accident.design();
        double a = drill == 1 ? d.smallBreakM2() : drill == 2 ? d.largeBreakM2() : 0;
        for (LoopPart part : broken.values()) a += part.share * d.largeBreakM2();
        if (housingBreak) a += dev.denfry.promptcritical.sim.npp.PressurizedWaterReactor.ROD_HOUSING_BREAK_M2;
        return Math.min(a, 2 * d.largeBreakM2());
    }

    /** Opens or seals the rod-housing break of an ejected cluster. */
    public void setRodHousingBreak(boolean open) {
        housingBreak = open;
    }

    public boolean rodHousingBreak() {
        return housingBreak;
    }

    /**
     * A steam explosion at the core or under the vessel (04c §4c.10.4): a confined blast through the consequence router,
     * the activity of failed fuel inside the containment when it holds, outside otherwise.
     */
    public void steamExplosion(ServerLevel sl, SimLevelBinding b, SimId source, BlockPos at, double energyJ, int failed) {
        if (!(energyJ > 0)) return;
        GridPos o = new GridPos(at.getX(), at.getY(), at.getZ());
        ConsequenceRouter.Routed r = ConsequenceRouter.route(ConsequenceRouter.Event.STEAM_EXPLOSION, requestSeq++, source, null, o,
                energyJ, accident.hasContainment(), failed > 0);
        if (r.damage() != null) b.damage().submit(r.damage());
        if (r.release() == ConsequenceRouter.ReleasePath.ENVIRONMENT) startRelease(failed);
        sl.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3f, 0.5f);
        WorldFx.blast(sl, at.getCenter(), energyJ, sl.random);
        broadcast(sl, at.getCenter(), 128, NppText.msg("fci", String.format(Locale.ROOT, "%.1f", energyJ / 1e6)).withStyle(ChatFormatting.DARK_RED));
    }

    // ------------------------------------------------------------------ step

    /**
     * Steps the accident layer and handles its events.
     *
     * @param coolantPumpsDriven a coolant pump's shaft still turns (its bus has AC)
     * @param loaded             loaded assemblies
     * @param failed             assemblies with failed cladding
     * @return how many more assemblies the core heat-up has breached (the caller fails them in its core)
     */
    public int step(ServerLevel sl, SimLevelBinding b, SimId source, BlockPos pos, double dt, double coreW, double sinkW,
            double reactorPa, boolean coolantPumpsDriven, int loaded, int failed) {
        ticks++;
        boolean ac = coolantPumpsDriven;
        List<EccsPumpBlockEntity> pumps = new ArrayList<>();
        for (BlockPos p : eccsPumps)
            if (sl.getBlockEntity(p) instanceof EccsPumpBlockEntity e) {
                pumps.add(e);
                ac |= e.driven();
            }
        int trains = 0;
        for (EccsPumpBlockEntity e : pumps) if (e.driven()) trains++;
        accident.setAcPower(ac);
        accident.setEccsTrains(trains);
        accident.setAccumulators(accumulators);
        if (containmentKnown) accident.setContainment(containment.intact() ? containment.volumeM3() : 0, containment.recombiners());
        accident.setBreakArea(breakArea());
        accident.setSeed(sl.getSeed() ^ pos.asLong());
        accident.setCavityFlooded(cavityFlooded);
        accident.step(dt, coreW, sinkW, reactorPa);
        boolean injecting = accident.eccsFlowKgS() > 0;
        for (EccsPumpBlockEntity e : pumps) e.reportInjecting(injecting && e.driven());
        events(sl, b, source, pos, failed);
        melt(sl, b, source, pos, coreW, failed);
        if (loaded <= 0) return 0;
        int target = (int) Math.ceil(accident.claddingFailedFraction() * loaded - 1e-9);
        return Math.max(0, target - failed);
    }

    private void events(ServerLevel sl, SimLevelBinding b, SimId source, BlockPos pos, int failed) {
        Vec3 c = pos.getCenter();
        boolean loca = accident.breakArea() > 0;
        if (loca && !locaAnnounced)
            broadcast(sl, c, 64, NppText.msg("accident_loca", String.format(Locale.ROOT, "%.0f", accident.breakArea() * 1e4))
                    .withStyle(ChatFormatting.RED));
        locaAnnounced = loca;
        boolean sbo = accident.stationBlackout() && accident.active();
        if (sbo && !sboAnnounced) {
            sl.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2f, 0.5f);
            broadcast(sl, c, 64, NppText.msg("accident_sbo").withStyle(ChatFormatting.RED));
        }
        sboAnnounced = sbo;
        if (accident.uncoveryEvent()) {
            sl.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.5f, 0.5f);
            broadcast(sl, c, 96, NppText.msg("accident_uncovered").withStyle(ChatFormatting.DARK_RED));
        }
        // after the blowdown the decay heat keeps boiling the water left in the vessel and the steam leaves through
        // the same break; once the core is dry only the hot gas of the oxidising cladding comes out of it
        if (loca && !headFailed()) {
            double steam = Math.max(accident.breakFlowKgS(), accident.boilOffKgS());
            if (steam > 0 || accident.coreUncovered()) breakJet(sl, pos, steam);
        }
        GridPos o = new GridPos(pos.getX(), pos.getY(), pos.getZ());
        if (accident.burnEvent()) {
            // R6: the burn goes through the consequence router; inside a closed containment the activity stays in
            // a detonation hits with the Chapman–Jouguet pressure, twice the deflagration's (04c §4c.7.3)
            boolean det = accident.burnRegime() == HydrogenCombustion.Regime.DETONATION;
            ConsequenceRouter.Routed r = ConsequenceRouter.route(det ? ConsequenceRouter.Event.HYDROGEN_DETONATION
                    : ConsequenceRouter.Event.HYDROGEN_BURN, requestSeq++, source, null, o, accident.burnEnergyJ(), true, failed > 0);
            if (r.damage() != null) b.damage().submit(r.damage());
            // the burn fills the free volume of the building: a fireball as big as the room, a blast only on detonation
            WorldFx.fireball(sl, c, roomRadius(), det ? 600 : 350, sl.random);
            if (det) WorldFx.blast(sl, c, accident.burnEnergyJ() * 0.1, sl.random);
            sl.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, det ? 4f : 3f, det ? 0.4f : 0.6f);
            broadcast(sl, c, 128, NppText.msg(det ? "accident_h2_detonation" : "accident_h2_burn",
                    String.format(Locale.ROOT, "%.0f", accident.burnEnergyJ() / 1e6)).withStyle(ChatFormatting.DARK_RED));
        }
        if (accident.breachEvent()) {
            double energy = Math.max(0, accident.design().containmentFailurePa() - PlantAccident.ATMOSPHERE_PA) * containment.volumeM3();
            ConsequenceRouter.Routed r = ConsequenceRouter.route(ConsequenceRouter.Event.CONTAINMENT_BREACH, requestSeq++, source, null,
                    o, energy, false, failed > 0);
            if (r.damage() != null) b.damage().submit(r.damage());
            if (r.release() == ConsequenceRouter.ReleasePath.ENVIRONMENT) startRelease(failed);
            WorldFx.blast(sl, c, energy, sl.random);
            sl.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3f, 0.4f);
            broadcast(sl, c, 128, NppText.msg("accident_breach").withStyle(ChatFormatting.DARK_RED));
        }
    }

    /**
     * Melt progression in the world (04c §4c.10): stage messages, steam explosions through the consequence router, the
     * corium poured under a failed vessel and sinking through the floor block by block, each block through the damage
     * budget as a melt-through (the server mode decides), and the corium's decay gamma at the front.
     */
    private void melt(ServerLevel sl, SimLevelBinding b, SimId source, BlockPos pos, double coreW, int failed) {
        CoreMeltProgression m = accident.melt();
        if (m == null || !m.started()) return;
        Vec3 c = pos.getCenter();
        if (m.stageEvent()) {
            String key = switch (m.stage()) {
                case CLAD_MELT_CANDLING -> "melt_candling";
                case CERAMIC_MELT_POOL -> "melt_pool";
                case LOWER_PLENUM_RELOCATION -> "melt_plenum";
                case LOWER_HEAD_FAILURE -> "melt_vessel";
                default -> null;
            };
            if (key != null) {
                sl.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 2f, 0.4f);
                broadcast(sl, c, 128, NppText.msg(key).withStyle(ChatFormatting.DARK_RED));
            }
        }
        if (m.steamExplosionJ() > 0) {
            if (m.vesselFailed()) steamExplosion(sl, b, source, underVessel(sl, pos), m.steamExplosionJ(), failed);
            else {
                // in the lower plenum the vessel takes the pressure pulse (the alpha-mode failure of the head is not
                // modelled): heard and announced, no blast in the world — and the reactor block keeps its accident
                sl.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2f, 0.3f);
                broadcast(sl, c, 128, NppText.msg("fci_in_vessel", String.format(Locale.ROOT, "%.1f", m.steamExplosionJ() / 1e6))
                        .withStyle(ChatFormatting.DARK_RED));
            }
        }
        if (m.failureEvent()) {
            if (m.directHeatingJ() > 0)
                broadcast(sl, c, 128, NppText.msg("melt_dch", String.format(Locale.ROOT, "%.0f", m.directHeatingJ() / 1e6))
                        .withStyle(ChatFormatting.DARK_RED));
            coriumFront = underVessel(sl, pos).immutable();
            meltThrough(b, source, coriumFront);
            pourTicks = POUR_TICKS;
            pourAt = coriumFront;
            spread(sl, pourAt, m.exVesselKg());
            sl.playSound(null, coriumFront, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 3f, 0.5f);
        }
        for (int i = 0; i < m.ablatedBlocks() && coriumFront != null; i++) {
            BlockPos next = coriumFront.below();
            if (!sl.isLoaded(next) || sl.getBlockState(next).getDestroySpeed(sl, next) < 0) break; // bedrock stops it
            coriumFront = next.immutable();
            meltThrough(b, source, coriumFront);
            broadcast(sl, c, 96, NppText.msg("melt_mcci", String.format(Locale.ROOT, "%.0f", m.ablatedM3())).withStyle(ChatFormatting.DARK_RED));
        }
        if (coriumFront != null && pourTicks > 0) {
            pourTicks--;
            pour(sl, pos, coriumFront);
            // the melt keeps coming out of the head: the pool grows with the mass ex-vessel
            if (pourAt != null && pourTicks % 20 == 0) spread(sl, pourAt, m.exVesselKg());
        }
        if (coriumFront != null && ticks % 20 == 0) {
            double share = Math.min(1, m.exVesselKg() / Math.max(1, m.exVesselKg() + m.lowerPlenumKg() + m.meltKg()));
            RadiationService.source(sl, coriumFront.getCenter(), FissionSource.reactor(0, coreW * share));
        }
        if (coriumFront != null && ticks % 2 == 0) mcci(sl, coriumFront);
    }

    /** One block of floor turned to corium through the budget ({@link DamageKind#MELT_THROUGH}). */
    private static void meltThrough(SimLevelBinding b, SimId source, BlockPos at) {
        GridPos o = new GridPos(at.getX(), at.getY(), at.getZ());
        b.damage().submit(new DamageRequest(requestSeq++, source, null, DamageKind.MELT_THROUGH, o, CoreMeltProgression.BLOCK_J, 0, 1));
    }

    /** Тиков видимой струи расплава после прорыва днища. */
    static final int POUR_TICKS = 240;

    /**
     * Струя течи из патрубка главного трубопровода (у цельных моделей — первый боковой патрубок петли, у старых блоков
     * — фасад): пароводяная смесь бьёт по направлению патрубка, часть воды проливается на пол, над местом течи
     * поднимается пар. Число частиц — от расхода течи; внутри закрытой оболочки пар ещё и заполняет помещение.
     */
    private void breakJet(ServerLevel sl, BlockPos pos, double flow) {
        var state = sl.getBlockState(pos);
        var hf = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
        Direction facing = state.hasProperty(hf) ? state.getValue(hf) : Direction.NORTH;
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        Vec3 from = null, dir = null;
        for (var port : dev.denfry.promptcritical.mod.multiblock.WholePorts.of(id)) {
            Direction face = dev.denfry.promptcritical.mod.multiblock.WholePorts.worldFace(facing, port);
            if (face.getAxis().isVertical()) continue;
            BlockPos cell = dev.denfry.promptcritical.mod.multiblock.WholePorts.cell(pos, facing, port);
            dir = Vec3.atLowerCornerOf(face.getNormal());
            from = cell.getCenter().add(dir.scale(0.6));
            break;
        }
        if (from == null) {
            dir = Vec3.atLowerCornerOf(facing.getNormal());
            from = pos.getCenter().add(dir.scale(0.6));
        }
        var r = sl.random;
        if (flow <= 0) {
            // a dry core: a thin dark plume of hot gas and aerosol out of the break
            if (ticks % 2 == 0) WorldFx.jet(sl, ParticleTypes.LARGE_SMOKE, from, dir.add(0, 0.3, 0), 0.15, 0.2, 2, r);
            return;
        }
        int n = (int) Math.min(48, 6 + Math.sqrt(flow) * 1.2);
        double speed = Math.min(1.6, 0.5 + Math.sqrt(flow) * 0.03);
        WorldFx.jet(sl, ParticleTypes.CLOUD, from, dir, speed, 0.18, n, r);
        if (ticks % 2 == 0) {
            // the water part of the flashing jet falls out of it onto the floor
            WorldFx.jet(sl, ParticleTypes.FALLING_WATER, from, dir.add(0, -0.3, 0), speed * 0.5, 0.25, Math.max(2, n / 3), r);
            WorldFx.jet(sl, ParticleTypes.SPLASH, from.add(dir.scale(2)).add(0, -2, 0), new Vec3(0, 1, 0), 0.2, 0.8, Math.max(2, n / 3), r);
            // steam rising over the break; once the core is bare the jet carries smoke of burning cladding with it
            Vec3 top = from.add(dir.scale(2.5));
            for (int i = 0, k = (int) Math.min(4, 1 + flow / 300); i < k; i++)
                WorldFx.fly(sl, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, top.add(r.nextGaussian() * 0.8, 0, r.nextGaussian() * 0.8),
                        new Vec3(dir.x * 0.05, 0.07 + 0.05 * r.nextDouble(), dir.z * 0.05));
            if (accident.coreUncovered()) WorldFx.jet(sl, ParticleTypes.LARGE_SMOKE, from, dir, speed * 0.6, 0.25, 2, r);
        }
        if (accident.hasContainment() && ticks % 3 == 0) {
            // the building fills with steam
            double rr = roomRadius();
            Vec3 c = pos.getCenter();
            for (int i = 0; i < 6; i++) {
                Vec3 p = c.add(r.nextGaussian() * rr * 0.5, r.nextDouble() * rr * 0.8, r.nextGaussian() * rr * 0.5);
                if (sl.getBlockState(BlockPos.containing(p)).isAir())
                    WorldFx.fly(sl, ParticleTypes.CLOUD, p, new Vec3(r.nextGaussian() * 0.02, 0.01, r.nextGaussian() * 0.02));
            }
        }
    }

    /** Радиус шара того же объёма, что свободный объём оболочки, блоков; без оболочки — 6. */
    private double roomRadius() {
        int v = containment.intact() ? containment.volumeM3() : 0;
        return v > 0 ? Math.min(20, Math.cbrt(3 * v / (4 * Math.PI))) : 6;
    }

    /** Расплав льётся из прорванного днища на дно шахты: светящаяся струя, брызги, пламя и дым над лужей. */
    private void pour(ServerLevel sl, BlockPos pos, BlockPos front) {
        int minY = sl.getBlockState(pos).getBlock() instanceof dev.denfry.promptcritical.mod.block.PcBlock pb ? pb.spec().footprint().minY() : 0;
        double top = pos.getY() + minY, bottom = front.getY() + 1;
        var r = sl.random;
        double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;
        for (int i = 0; i < 6; i++) {
            double y = bottom + r.nextDouble() * Math.max(0.5, top - bottom);
            WorldFx.fly(sl, ParticleTypes.FALLING_LAVA, new Vec3(x + r.nextGaussian() * 0.15, y, z + r.nextGaussian() * 0.15), new Vec3(0, -0.6, 0));
        }
        WorldFx.send(sl, ParticleTypes.LAVA, front.getX() + 0.5, bottom, front.getZ() + 0.5, 4, 0.6, 0.1, 0.6, 0);
        WorldFx.send(sl, ParticleTypes.FLAME, front.getX() + 0.5, bottom + 0.2, front.getZ() + 0.5, 6, 0.8, 0.2, 0.8, 0.03);
        if (pourTicks % 5 == 0) sl.playSound(null, front, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 2f, 0.6f);
    }

    /** Плотность оксидного кориума, кг/м³, и толщина слоя, которым он растекается по сухому полу, м (опыты VULCANO). */
    static final double CORIUM_KG_M3 = 8_000, SPREAD_DEPTH_M = 0.1;
    /** Больше этого клеток лужа не занимает. */
    static final int SPREAD_MAX_CELLS = 24;

    /**
     * Вышедший из корпуса расплав растекается по полу шахты вокруг места падения слоем {@link #SPREAD_DEPTH_M}: клетки по
     * спирали от центра, только свободные и на твёрдом полу; стены шахты его держат.
     */
    private static void spread(ServerLevel sl, BlockPos front, double exVesselKg) {
        var pool = PcBlocks.CORIUM_POOL.get().defaultBlockState();
        // cells the mass covers 10 cm deep, less the pour point itself and what has already spread
        int cells = (int) Math.min(SPREAD_MAX_CELLS, Math.round(exVesselKg / CORIUM_KG_M3 / SPREAD_DEPTH_M)) - 1;
        java.util.List<BlockPos> free = new java.util.ArrayList<>();
        for (int ring = 1; ring <= 3 && cells > 0; ring++) {
            free.clear();
            for (int dx = -ring; dx <= ring; dx++)
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    BlockPos p = front.offset(dx, 0, dz);
                    if (!sl.isLoaded(p)) continue;
                    if (sl.getBlockState(p).is(pool.getBlock())) cells--;
                    else if (sl.getBlockState(p).isAir() && pool.canSurvive(sl, p)) free.add(p);
                }
            // the melt runs out evenly round the pour, not row by row
            net.minecraft.Util.shuffle(free, sl.random);
            for (int i = 0; i < free.size() && cells > 0; i++, cells--)
                sl.setBlock(free.get(i), pool, net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    /** Кориум в бетоне: газы абляции (CO2, пар) пузырят расплав и поднимаются дымом, корка светится. */
    private static void mcci(ServerLevel sl, BlockPos front) {
        var r = sl.random;
        Vec3 c = front.getCenter().add(0, 0.6, 0);
        WorldFx.fly(sl, ParticleTypes.CAMPFIRE_COSY_SMOKE, c.add(r.nextGaussian() * 0.4, 0, r.nextGaussian() * 0.4),
                new Vec3(0, 0.05 + 0.04 * r.nextDouble(), 0));
        if (r.nextInt(3) == 0) WorldFx.send(sl, ParticleTypes.LAVA, c.x, c.y, c.z, 1, 0.3, 0, 0.3, 0);
    }

    private static void broadcast(ServerLevel sl, Vec3 c, int radius, Component msg) {
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(c) < (double) radius * radius)) p.displayClientMessage(msg, false);
    }

    /** The lower head of a vessel plant has failed (channel reactors have no head). */
    private boolean headFailed() {
        return accident.melt() != null && accident.melt().vesselFailed();
    }

    private void startRelease(int failed) {
        if (failed <= 0) return;
        releaseTicks = 60 * 20;
        releaseBqS = failed * I131_PER_FAILED_BQ / 60.0;
    }

    /**
     * Activity of failed fuel leaving the primary: through an open break with no closed containment it goes up as a
     * plume; a breach plume runs for a minute after the tear.
     */
    public void radiation(ServerLevel sl, BlockPos pos, int failed) {
        if (ticks % 20 != 0) return;
        Vec3 top = pos.getCenter().add(0, 6, 0);
        // an open building: what the failed fuel gives up leaves through the break while steam still carries it, or
        // through the failed head. The gap release (5 % of the iodine of a failed assembly) grows to the early in-vessel
        // limit (40 %) with the core's loss of geometry Γ — a stand-in for CORSOR-M's temperature-driven rate that keeps
        // its end points (04c §4c.8.4)
        boolean path = accident.breakFlowKgS() > 0 || accident.boilOffKgS() > 0 || accident.coreUncovered() || headFailed();
        if (failed > 0 && path && !accident.hasContainment()) {
            double inventory = failed * I131_PER_FAILED_BQ / GAP_FRACTION;
            double gamma = accident.melt() != null ? accident.melt().gamma() : 0;
            double iodine = Math.max(0, inventory * (GAP_FRACTION + (HALOGEN_MAX - GAP_FRACTION) * gamma) - releasedIodineBq)
                    * OPEN_RELEASE_PER_S;
            double cesium = Math.max(0, inventory * PwrReactorBlockEntity.CS137_PER_I131
                    * (GAP_FRACTION + (ALKALI_MAX - GAP_FRACTION) * gamma) - releasedCesiumBq) * OPEN_RELEASE_PER_S;
            releasedIodineBq += iodine;
            releasedCesiumBq += cesium;
            if (iodine > 0) RadiationService.airRelease(sl, top, Emitter.I131, iodine);
            if (cesium > 0) RadiationService.airRelease(sl, top, Emitter.CS137, cesium);
        }
        if (releaseTicks > 0) {
            releaseTicks -= 20;
            RadiationService.airRelease(sl, top, Emitter.I131, releaseBqS);
            RadiationService.airRelease(sl, top, Emitter.CS137, releaseBqS * PwrReactorBlockEntity.CS137_PER_I131);
        }
    }

    // ------------------------------------------------------------------ panel

    /** Fills the accident channels starting at {@code base} (see {@link AccidentPanels}). */
    public void readPanel(int[] v, int base) {
        PlantAccident a = accident;
        v[base + AccidentPanels.LEVEL] = (int) Math.round(Math.min(1, a.levelFraction()) * 1000);
        v[base + AccidentPanels.COVER] = (int) Math.round(a.coverage() * 1000);
        v[base + AccidentPanels.PCT_C] = (int) Math.round(a.peakCladK() - 273.15);
        v[base + AccidentPanels.CLAD_C] = (int) Math.round(a.cladK() - 273.15);
        v[base + AccidentPanels.H2_KG10] = (int) Math.round(a.hydrogenKg() * 10);
        v[base + AccidentPanels.CONT_KPA] = (int) Math.round(a.containmentPa() / 1e3);
        v[base + AccidentPanels.CONT_H2] = (int) Math.round(a.containmentHydrogenFraction() * 1000);
        v[base + AccidentPanels.CONT_STEAM] = (int) Math.round(a.containmentSteamFraction() * 1000);
        v[base + AccidentPanels.H2_REGIME] = a.containmentRegime().ordinal();
        v[base + AccidentPanels.ECCS_KGS10] = (int) Math.round((a.eccsFlowKgS() + a.accumulatorFlowKgS()) * 10);
        v[base + AccidentPanels.BREAK_KGS10] = (int) Math.round(a.breakFlowKgS() * 10);
        v[base + AccidentPanels.BREAK_CM2] = (int) Math.round(a.breakArea() * 1e4);
        v[base + AccidentPanels.TRAINS] = a.eccsTrains() | eccsPumps.size() << 8 | accumulators << 16;
        v[base + AccidentPanels.FLAGS] = (a.active() ? 1 << AccidentPanels.F_ACTIVE : 0)
                | (a.stationBlackout() ? 1 << AccidentPanels.F_SBO : 0)
                | (a.coreUncovered() ? 1 << AccidentPanels.F_UNCOVERED : 0)
                | (a.adsOpen() ? 1 << AccidentPanels.F_ADS : 0)
                | (a.hasContainment() ? 1 << AccidentPanels.F_CONTAINMENT : 0)
                | (a.containmentBreached() ? 1 << AccidentPanels.F_BREACHED : 0)
                | (a.pctLimitExceeded() ? 1 << AccidentPanels.F_PCT : 0);
        CoreMeltProgression m = a.melt();
        if (m != null) {
            v[base + AccidentPanels.MELT_STAGE] = m.stage().ordinal();
            v[base + AccidentPanels.MELT_GAMMA] = (int) Math.round(m.gamma() * 1000);
            v[base + AccidentPanels.HEAD_C] = (int) Math.round(m.lowerHeadK() - 273.15);
            v[base + AccidentPanels.CORIUM_KG] = (int) Math.round(m.exVesselKg());
            v[base + AccidentPanels.ABLATED_M3] = (int) Math.round(m.ablatedM3());
            v[base + AccidentPanels.MELT_FLAGS] = 1 << AccidentPanels.M_VESSEL | (cavityFlooded ? 1 << AccidentPanels.M_FLOODED : 0)
                    | (m.retainedInVessel() ? 1 << AccidentPanels.M_IVR : 0) | (m.vesselFailed() ? 1 << AccidentPanels.M_FAILED : 0);
        }
    }

    // ------------------------------------------------------------------ save

    public void save(CompoundTag tag) {
        CompoundTag t = new CompoundTag();
        MutableStateTree tree = new MutableStateTree();
        accident.save(new TreeStateWriter(tree));
        t.put("sim", NbtStateTrees.toNbt(tree));
        t.putInt("drill", drill);
        t.putBoolean("housing", housingBreak);
        t.putBoolean("cavity", cavityFlooded);
        if (coriumFront != null) t.putLong("corium", coriumFront.asLong());
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, LoopPart> e : broken.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", e.getKey().asLong());
            c.putString("part", e.getValue().name());
            list.add(c);
        }
        t.put("broken", list);
        tag.put("accident", t);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("accident")) return;
        CompoundTag t = tag.getCompound("accident");
        if (t.contains("sim")) accident.load(new TreeStateReader(NbtStateTrees.fromNbt(t.getCompound("sim"))));
        drill = Math.floorMod(t.getInt("drill"), 3);
        housingBreak = t.getBoolean("housing");
        cavityFlooded = t.getBoolean("cavity");
        coriumFront = t.contains("corium") ? BlockPos.of(t.getLong("corium")) : null;
        broken.clear();
        for (Tag e : t.getList("broken", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            try {
                broken.put(BlockPos.of(c.getLong("pos")), LoopPart.valueOf(c.getString("part")));
            } catch (IllegalArgumentException ignored) {
                // unknown part name from a newer save: the break is dropped rather than guessed
            }
        }
        locaAnnounced = accident.breakArea() > 0;
        sboAnnounced = accident.stationBlackout() && accident.active();
    }
}
