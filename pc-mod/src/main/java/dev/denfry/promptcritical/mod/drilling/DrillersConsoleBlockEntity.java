package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.drilling.DrillersMethod;
import dev.denfry.promptcritical.sim.drilling.FormationFluid;
import dev.denfry.promptcritical.sim.drilling.KillSheet;
import dev.denfry.promptcritical.sim.drilling.NaturalGas;
import dev.denfry.promptcritical.sim.drilling.Prospect;
import dev.denfry.promptcritical.sim.drilling.Trap;
import dev.denfry.promptcritical.sim.drilling.Well;
import dev.denfry.promptcritical.sim.drilling.WellGeometry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Пульт бурильщика: собирает буровую из блоков вокруг (ротор под вышкой, лебёдка, буровые насосы, кольцевой
 * превентор, манифольд дросселирования, ёмкости раствора — в {@link #RIG_RADIUS} блоках) и ведёт скважину
 * ({@link Well}) под ротором в медленном времени симуляции (k_slow: сутки за игровые сутки). Пласт под ротором —
 * из {@link PetroleumFields}. Скважину начинают из-под кондуктора, спущенного и зацементированного до
 * {@link WellGeometry#shoeDepthM()} [допущение: кондуктор и цементаж не моделируются], на растворе 1,10 г/см³.
 *
 * <p>Щит: нагрузка на долото и ходы насосов — кнопками, превентор, глушение методом бурильщика
 * ({@link DrillersMethod}), обнуление счётчика прироста. Открытый фонтан газа воспламеняется на буровой и идёт
 * в бюджет разрушений как взрыв ({@link Ironworks#blast}).
 */
public final class DrillersConsoleBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int RIG_RADIUS = 8, SCAN_PERIOD = 40, MIN_TANKS = 4;
    /** Объём ёмкости-блока, м³: кубометр стали на кубометр раствора. */
    public static final double TANK_M3 = 1.0;
    public static final double SPUD_MUD_KGM3 = 1100;
    public static final double WOB_STEP_N = 20e3, SPM_STEP = 10;
    /** Доля теплоты сгорания облака газа, уходящая во взрыв (дефлаграция в загромождённой буровой, TNO multi-energy). */
    public static final double BLAST_FRACTION = 0.03, GAS_HEAT_J_PER_STD_M3 = 36e6, CLOUD_SECONDS = 60;

    private Well well;
    private final DrillersMethod kill = new DrillersMethod(MudPumpBlockEntity.M3_PER_STROKE);
    private double wobSetN, spmSet;
    private boolean rigOk, blowoutHandled;
    private final List<String> missing = new ArrayList<>();
    @Nullable
    private BlockPos rotaryPos;
    private final List<BlockPos> pumps = new ArrayList<>(), bops = new ArrayList<>(), tanks = new ArrayList<>();
    private double[] pendingWell, pendingProspect;
    private int ticks;

    public DrillersConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(DrillingBlockEntities.DRILLERS_CONSOLE.get(), pos, state);
    }

    @Nullable
    public Well well() {
        return well;
    }

    public DrillersMethod killProcedure() {
        return kill;
    }

    public boolean rigOk() {
        return rigOk;
    }

    public List<String> missing() {
        return List.copyOf(missing);
    }

    /** Ближайший пульт к позиции, в пределах буровой. */
    @Nullable
    public static DrillersConsoleBlockEntity near(Level level, BlockPos pos) {
        List<DrillersConsoleBlockEntity> all = Ironworks.blockEntities(level, new AABB(pos).inflate(RIG_RADIUS + 2),
                DrillersConsoleBlockEntity.class);
        DrillersConsoleBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (DrillersConsoleBlockEntity c : all) {
            double d = c.getBlockPos().distSqr(pos);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    // --- сборка буровой ---

    /** Осмотреть блоки вокруг пульта; возвращает {@code true}, если буровая собрана. */
    public boolean scan() {
        rotaryPos = null;
        pumps.clear();
        bops.clear();
        tanks.clear();
        boolean drawworks = false, choke = false;
        List<BlockPos> derricks = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-RIG_RADIUS, -RIG_RADIUS, -RIG_RADIUS),
                worldPosition.offset(RIG_RADIUS, RIG_RADIUS, RIG_RADIUS))) {
            BlockState s = level.getBlockState(p);
            if (s.is(PcBlocks.ROTARY_TABLE.get())) {
                if (rotaryPos == null || p.distSqr(worldPosition) < rotaryPos.distSqr(worldPosition)) rotaryPos = p.immutable();
            } else if (s.is(PcBlocks.MUD_PUMP.get()) && level.getBlockEntity(p) instanceof MudPumpBlockEntity) {
                pumps.add(p.immutable());
            } else if (s.is(PcBlocks.BOP_ANNULAR.get())) {
                bops.add(p.immutable());
            } else if (s.is(PcBlocks.MUD_TANK.get())) {
                tanks.add(p.immutable());
            } else if (s.is(PcBlocks.DRAWWORKS.get())) {
                drawworks = true;
            } else if (s.is(PcBlocks.CHOKE_MANIFOLD.get())) {
                choke = true;
            } else if (s.is(PcBlocks.DERRICK_SEGMENT.get())) {
                derricks.add(p.immutable());
            }
        }
        boolean derrick = false;
        if (rotaryPos != null) for (BlockPos d : derricks)
            derrick |= d.getY() > rotaryPos.getY() && Math.abs(d.getX() - rotaryPos.getX()) <= 2 && Math.abs(d.getZ() - rotaryPos.getZ()) <= 2;
        missing.clear();
        if (rotaryPos == null) missing.add("rotary_table");
        if (!derrick) missing.add("derrick_segment");
        if (!drawworks) missing.add("drawworks");
        if (pumps.isEmpty()) missing.add("mud_pump");
        if (bops.isEmpty()) missing.add("bop_annular");
        if (!choke) missing.add("choke_manifold");
        if (tanks.size() < MIN_TANKS) missing.add("mud_tank");
        rigOk = missing.isEmpty();
        if (well != null) well.setPitCapacityM3(Math.max(tanks.size(), 1) * TANK_M3);
        return rigOk;
    }

    private Component missingText() {
        Component out = Component.empty();
        for (int i = 0; i < missing.size(); i++) {
            String k = missing.get(i);
            if (i > 0) out = out.copy().append(", ");
            out = out.copy().append(k.equals("mud_tank") ? DrillingText.msg("part." + k, MIN_TANKS) : DrillingText.msg("part." + k));
        }
        return out;
    }

    /** Начать скважину под ротором: пласт — из мира, если не задан. Первый запуск буровой и испытания. */
    public void spud(@Nullable Prospect prospect, double depthM) {
        if (rotaryPos == null) scan();
        well = new Well(WellGeometry.STANDARD, prospect, depthM, SPUD_MUD_KGM3, Math.max(tanks.size(), 1) * TANK_M3);
        kill.abort(well);
        blowoutHandled = false;
        setChanged();
    }

    @Nullable
    private Prospect worldProspect() {
        if (!(level instanceof ServerLevel s) || rotaryPos == null) return null;
        return PetroleumFields.get(s.getSeed()).prospect(rotaryPos.getX(), rotaryPos.getZ());
    }

    // --- тик ---

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        ticks++;
        if (ticks == 1 || ticks % SCAN_PERIOD == 0) {
            scan();
            if (pendingWell != null) restoreWell();
            if (well == null && rigOk) {
                spud(worldProspect(), WellGeometry.STANDARD.shoeDepthM() + 10);
                Player p = s.getNearestPlayer(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 16, false);
                if (p != null) p.displayClientMessage(DrillingText.msg("rig_spud", DrillingText.num(well.holeDepthM(), 0),
                        DrillingText.num(SPUD_MUD_KGM3 / 1000, 2), DrillingText.num(well.pitCapacityM3(), 0)), true);
            }
        }
        if (well == null) return;
        double dt = 0.05 * b.world().clock().kSlow();
        List<MudPumpBlockEntity> pumpBes = new ArrayList<>();
        for (BlockPos p : pumps) if (level.getBlockEntity(p) instanceof MudPumpBlockEntity m) pumpBes.add(m);
        RotaryTableBlockEntity rotary = rotaryPos != null && level.getBlockEntity(rotaryPos) instanceof RotaryTableBlockEntity r ? r : null;
        double required = kill.requiredPumpM3s();
        double q = 0;
        for (int i = 0; i < pumpBes.size(); i++) {
            double set = !rigOk ? 0 : Double.isNaN(required) ? spmSet : i == 0 ? required * 60 / MudPumpBlockEntity.M3_PER_STROKE : 0;
            q += pumpBes.get(i).drive(set) / 60 * MudPumpBlockEntity.M3_PER_STROKE;
        }
        double rpm = rigOk && rotary != null ? rotary.tableRpm() : 0;
        if (bops.isEmpty() && well.bopClosed()) well.setBopClosed(false);
        boolean wasBlowout = well.blowout();
        well.step(dt, q, rpm, rigOk ? wobSetN : 0);
        kill.track(well, dt);
        double share = q > 0 ? well.pumpPowerW() / q : 0;
        for (MudPumpBlockEntity m : pumpBes) m.load(well.drillPipePressurePa(), share * m.flowM3s());
        if (rotary != null) rotary.load(well.rotaryPowerW());
        if (well.blowout() && !wasBlowout) onBlowout(s, b);
        if (well.blowout() && ticks % 5 == 0) blowoutFx(s);
        if (ticks % 10 == 0) updateStates();
        if (ticks % 100 == 0) setChanged();
    }

    private void updateStates() {
        setState(worldPosition, getBlockState().setValue(BlockStateProperties.LIT, well.pumpRateM3s() > 0)
                .setValue(PcProperties.ALARM, well.kickAlarm() || well.blowout()));
        double fill = well.pitVolumeM3() / well.pitCapacityM3();
        int lvl = fill < 0.3 ? 0 : fill < 0.85 ? 1 : 2;
        for (BlockPos p : tanks) {
            BlockState s = level.getBlockState(p);
            if (s.is(PcBlocks.MUD_TANK.get())) setState(p, s.setValue(PcProperties.MUD_LEVEL, lvl));
        }
        for (BlockPos p : bops) {
            BlockState s = level.getBlockState(p);
            if (s.is(PcBlocks.BOP_ANNULAR.get())) setState(p, s.setValue(PcProperties.SHUT, well.bopClosed()));
        }
    }

    private void setState(BlockPos p, BlockState s) {
        if (level.getBlockState(p) != s) level.setBlock(p, s, Block.UPDATE_ALL);
    }

    /** Фонтан: газ воспламеняется на буровой — взрыв облака через бюджет разрушений; всем рядом — тревога. */
    private void onBlowout(ServerLevel s, SimLevelBinding b) {
        if (blowoutHandled) return;
        blowoutHandled = true;
        BlockPos at = rotaryPos != null ? rotaryPos : worldPosition;
        for (Player p : s.players())
            if (p.distanceToSqr(at.getCenter()) < 64 * 64)
                p.displayClientMessage(DrillingText.msg("blowout").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        s.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0f, 0.6f);
        Prospect pr = well.prospect();
        if (pr == null || pr.trap().fluidAt(pr.topM()) != FormationFluid.GAS) return;
        // облако за первую минуту фонтана в нормальных кубометрах: расход в пластовых условиях пересчитан по давлению
        double mid = pr.topM() + well.exposedPayM() / 2, t = Trap.temperatureK(mid);
        double stdM3 = NaturalGas.standardVolumeM3(NaturalGas.moles(well.blowoutRateM3s() * CLOUD_SECONDS,
                well.bottomholePressurePa() + Well.P_ATM, t));
        Ironworks.blast(b, at, stdM3 * GAS_HEAT_J_PER_STD_M3 * BLAST_FRACTION, 12, 1);
    }

    private void blowoutFx(ServerLevel s) {
        BlockPos at = rotaryPos != null ? rotaryPos : worldPosition;
        boolean gas = well.prospect() != null && well.prospect().trap().fluidAt(well.prospect().topM()) == FormationFluid.GAS;
        s.sendParticles(gas ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SQUID_INK, at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5,
                12, 0.2, 2.0, 0.2, 0.3);
        if (gas) s.sendParticles(ParticleTypes.FLAME, at.getX() + 0.5, at.getY() + 3, at.getZ() + 0.5, 8, 0.4, 1.5, 0.4, 0.05);
    }

    // --- игрок ---

    public void addBarite(double kg, @Nullable Player player) {
        if (well == null) return;
        well.addBarite(kg);
        setChanged();
        if (player != null) {
            KillSheet k = kill.sheet();
            player.displayClientMessage(DrillingText.msg("barite", DrillingText.num(kg, 0), DrillingText.num(well.pitDensityKgM3() / 1000, 3),
                    k == null ? "—" : DrillingText.num(k.killMudKgM3() / 1000, 3)), true);
        }
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (well == null) {
            player.displayClientMessage(DrillingText.msg("rig_missing", missingText()), true);
            return;
        }
        switch (id) {
            case DrillingPanels.B_WOB_DOWN -> wobSetN = Math.max(0, wobSetN - WOB_STEP_N);
            case DrillingPanels.B_WOB_UP -> wobSetN = Math.min(well.maxWobN(), wobSetN + WOB_STEP_N);
            case DrillingPanels.B_SPM_DOWN -> spmSet = Math.max(0, spmSet - SPM_STEP);
            case DrillingPanels.B_SPM_UP -> spmSet = Math.min(MudPumpBlockEntity.MAX_SPM, spmSet + SPM_STEP);
            case DrillingPanels.B_BOP -> toggleBop(player);
            case DrillingPanels.B_KILL -> advanceKill(player);
            case DrillingPanels.B_ZERO -> well.zeroPitTotalizer();
            default -> {
            }
        }
        setChanged();
    }

    /** Закрыть или открыть превентор; без превентора под буровой — нечем. */
    public void toggleBop(@Nullable Player player) {
        if (well == null) return;
        if (bops.isEmpty()) {
            if (player != null) player.displayClientMessage(DrillingText.msg("bop_needs"), true);
            return;
        }
        boolean close = !well.bopClosed();
        well.setBopClosed(close);
        if (close) {
            spmSet = 0;
            wobSetN = 0;
        }
        level.playSound(null, bops.getFirst(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0f, 0.5f);
        updateStates();
    }

    /** Следующий шаг метода бурильщика. */
    public boolean advanceKill(@Nullable Player player) {
        if (well == null) return false;
        if (kill.stage() == DrillersMethod.Stage.IDLE || kill.stage() == DrillersMethod.Stage.DONE) spmSet = 0;
        boolean ok = kill.advance(well);
        if (player != null) {
            KillSheet k = kill.sheet();
            player.displayClientMessage(ok ? DrillingText.msg("kill_stage", DrillingText.msg("stage." + kill.stage().name().toLowerCase(java.util.Locale.ROOT)),
                    DrillingText.num(k.sidppPa() / 1e6, 2), DrillingText.num(k.sicpPa() / 1e6, 2), DrillingText.num(k.killMudKgM3() / 1000, 3),
                    DrillingText.num(k.icpPa() / 1e6, 2), DrillingText.num(k.fcpPa() / 1e6, 2))
                    : DrillingText.msg("kill_refused", k == null ? "—" : DrillingText.num(k.killMudKgM3() / 1000, 3)), true);
        }
        return ok;
    }

    /** Нагрузка на долото и ходы насосов, Н и ход/мин (щит и испытания). */
    public void setDrilling(double wobN, double spm) {
        wobSetN = Math.max(0, well == null ? wobN : Math.min(wobN, well.maxWobN()));
        spmSet = Math.clamp(spm, 0, MudPumpBlockEntity.MAX_SPM);
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Shift и правый клик — сводка. */
    @Override
    public InteractionResult use(Player player) {
        if (well == null) {
            scan();
            player.displayClientMessage(DrillingText.msg("rig_missing", missingText()), true);
            return InteractionResult.SUCCESS;
        }
        player.displayClientMessage(DrillingText.msg("rig_status", DrillingText.num(well.holeDepthM(), 1), DrillingText.num(well.ropMs() * 3600, 1),
                DrillingText.num(well.wobN() / 9.80665e3, 1), DrillingText.num(well.drillPipePressurePa() / 1e6, 2),
                DrillingText.num(well.casingPressurePa() / 1e6, 2), DrillingText.num(well.pitGainM3(), 2)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return DrillingPanels.CONSOLE;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        int flags = rigOk ? 1 << DrillingPanels.F_RIG : 0;
        out[DrillingPanels.C_WOB_SET_T10] = (int) Math.round(wobSetN / 9.80665e3 * 10);
        out[DrillingPanels.C_SPM_SET] = (int) Math.round(spmSet);
        if (well != null) {
            out[DrillingPanels.C_HOOK_T10] = (int) Math.round(well.hookLoadN() / 9.80665e3 * 10);
            out[DrillingPanels.C_WOB_T10] = (int) Math.round(well.wobN() / 9.80665e3 * 10);
            out[DrillingPanels.C_SPP_X100] = (int) Math.round(well.drillPipePressurePa() / 1e4);
            out[DrillingPanels.C_CSG_X100] = (int) Math.round(well.casingPressurePa() / 1e4);
            out[DrillingPanels.C_PIT_PERMIL] = (int) Math.round(1000 * well.pitVolumeM3() / well.pitCapacityM3());
            out[DrillingPanels.C_GAIN_L] = (int) Math.round(well.pitGainM3() * 1000);
            out[DrillingPanels.C_DEPTH_X10] = (int) Math.round(well.holeDepthM() * 10);
            out[DrillingPanels.C_ROP_X10] = (int) Math.round(well.ropMs() * 3600 * 10);
            out[DrillingPanels.C_SPM] = (int) Math.round(well.pumpRateM3s() * 60 / MudPumpBlockEntity.M3_PER_STROKE);
            out[DrillingPanels.C_RPM] = rotaryPos != null && level.getBlockEntity(rotaryPos) instanceof RotaryTableBlockEntity r
                    ? (int) Math.round(r.tableRpm()) : 0;
            out[DrillingPanels.C_ECD] = (int) Math.round(well.ecdKgM3());
            out[DrillingPanels.C_PIT_RHO] = (int) Math.round(well.pitDensityKgM3());
            out[DrillingPanels.C_STAGE] = DrillingPanels.stage(kill.stage());
            out[DrillingPanels.C_KMW] = kill.sheet() == null ? 0 : (int) Math.round(kill.sheet().killMudKgM3());
            out[DrillingPanels.C_MAASP_X100] = (int) Math.round(well.maaspPa() / 1e4);
            out[DrillingPanels.C_CHOKE_PERMIL] = (int) Math.round(well.chokeOpening() * 1000);
            out[DrillingPanels.C_FLOW_LS10] = (int) Math.round(well.flowOutM3s() * 1e4);
            if (well.kickAlarm()) flags |= 1 << DrillingPanels.F_KICK;
            if (well.bopClosed()) flags |= 1 << DrillingPanels.F_BOP;
            if (well.gasOutMolS() > 0) flags |= 1 << DrillingPanels.F_GAS;
            if (well.blowout()) flags |= 1 << DrillingPanels.F_BLOWOUT;
            if (kill.stage() != DrillersMethod.Stage.IDLE) flags |= 1 << DrillingPanels.F_KILL;
            if (well.shoeFractured()) flags |= 1 << DrillingPanels.F_SHOE;
        }
        out[DrillingPanels.C_FLAGS] = flags;
    }

    // --- сохранение ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("wob", wobSetN);
        tag.putDouble("spm", spmSet);
        tag.putBoolean("blowout_handled", blowoutHandled);
        if (well != null) {
            tag.putLongArray("well", bits(well.exportState()));
            Prospect p = well.prospect();
            if (p != null) {
                Trap t = p.trap();
                tag.putLongArray("prospect", bits(new double[] {t.crestX(), t.crestZ(), t.radiusM(), t.crestDepthM(), t.closureM(),
                        t.contactDepthM(), t.thicknessM(), t.porosity(), t.permeabilityMd(), t.fluid().ordinal(), t.gradientPaM(), p.topM()}));
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        wobSetN = tag.getDouble("wob");
        spmSet = tag.getDouble("spm");
        blowoutHandled = tag.getBoolean("blowout_handled");
        pendingWell = tag.contains("well") ? doubles(tag.getLongArray("well")) : null;
        pendingProspect = tag.contains("prospect") ? doubles(tag.getLongArray("prospect")) : null;
    }

    /** Скважина восстанавливается на первом осмотре буровой, когда известны ёмкости. */
    private void restoreWell() {
        Prospect p = null;
        double[] a = pendingProspect;
        if (a != null && a.length == 12) {
            try {
                FormationFluid f = FormationFluid.values()[(int) a[9]];
                p = new Prospect(new Trap((int) a[0], (int) a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], f, a[10]), a[11]);
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                p = null;
            }
        }
        well = Well.restore(WellGeometry.STANDARD, p, Math.max(tanks.size(), 1) * TANK_M3, pendingWell);
        pendingWell = pendingProspect = null;
    }

    private static long[] bits(double[] v) {
        long[] out = new long[v.length];
        for (int i = 0; i < v.length; i++) out[i] = Double.doubleToRawLongBits(v[i]);
        return out;
    }

    private static double[] doubles(long[] v) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = Double.longBitsToDouble(v[i]);
        return out;
    }
}
