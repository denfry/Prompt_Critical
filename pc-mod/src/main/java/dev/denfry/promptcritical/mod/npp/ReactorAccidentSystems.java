package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.environment.RadiationService;
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
    private int ticks;

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
        return center.below(1 - minY);
    }

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
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1, 0, 0, 0, 0);
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
        if (loca && accident.breakFlowKgS() > 0 && ticks % 4 == 0)
            sl.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, (int) Math.min(30, 2 + accident.breakFlowKgS() / 5), 1.5, 0.5, 1.5, 0.08);
        GridPos o = new GridPos(pos.getX(), pos.getY(), pos.getZ());
        if (accident.burnEvent()) {
            // R6: the burn goes through the consequence router; inside a closed containment the activity stays in
            // a detonation hits with the Chapman–Jouguet pressure, twice the deflagration's (04c §4c.7.3)
            boolean det = accident.burnRegime() == HydrogenCombustion.Regime.DETONATION;
            ConsequenceRouter.Routed r = ConsequenceRouter.route(det ? ConsequenceRouter.Event.HYDROGEN_DETONATION
                    : ConsequenceRouter.Event.HYDROGEN_BURN, requestSeq++, source, null, o, accident.burnEnergyJ(), true, failed > 0);
            if (r.damage() != null) b.damage().submit(r.damage());
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
        }
        for (int i = 0; i < m.ablatedBlocks() && coriumFront != null; i++) {
            BlockPos next = coriumFront.below();
            if (!sl.isLoaded(next) || sl.getBlockState(next).getDestroySpeed(sl, next) < 0) break; // bedrock stops it
            coriumFront = next.immutable();
            meltThrough(b, source, coriumFront);
            broadcast(sl, c, 96, NppText.msg("melt_mcci", String.format(Locale.ROOT, "%.0f", m.ablatedM3())).withStyle(ChatFormatting.DARK_RED));
        }
        if (coriumFront != null && ticks % 20 == 0) {
            double share = Math.min(1, m.exVesselKg() / Math.max(1, m.exVesselKg() + m.lowerPlenumKg() + m.meltKg()));
            RadiationService.source(sl, coriumFront.getCenter(), FissionSource.reactor(0, coreW * share));
            sl.sendParticles(ParticleTypes.LAVA, coriumFront.getX() + 0.5, coriumFront.getY() + 1.0, coriumFront.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 0);
        }
    }

    /** One block of floor turned to corium through the budget ({@link DamageKind#MELT_THROUGH}). */
    private static void meltThrough(SimLevelBinding b, SimId source, BlockPos at) {
        GridPos o = new GridPos(at.getX(), at.getY(), at.getZ());
        b.damage().submit(new DamageRequest(requestSeq++, source, null, DamageKind.MELT_THROUGH, o, CoreMeltProgression.BLOCK_J, 0, 1));
    }

    private static void broadcast(ServerLevel sl, Vec3 c, int radius, Component msg) {
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(c) < (double) radius * radius)) p.displayClientMessage(msg, false);
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
        if (failed > 0 && accident.breakFlowKgS() > 0 && !accident.hasContainment()) {
            double bqS = failed * I131_PER_FAILED_BQ / 600.0;
            RadiationService.airRelease(sl, top, Emitter.I131, bqS);
            RadiationService.airRelease(sl, top, Emitter.CS137, bqS * PwrReactorBlockEntity.CS137_PER_I131);
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
