package dev.denfry.promptcritical.mod.mine;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.gantry.GantryContraption;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlockEntity;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimConfigs;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.foundry.StressFilter;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.worldgen.VeinFields;
import dev.denfry.promptcritical.sim.mining.Longwall;
import dev.denfry.promptcritical.sim.mining.LongwallConfig;
import dev.denfry.promptcritical.sim.mining.MethaneField;
import dev.denfry.promptcritical.sim.mining.ShearerCut;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Резание очистным комбайном на контрапции Create {@code gantry} (08 §8.2.3). Шнеки на концах корпуса режут
 * полосу забоя толщиной 1 блок со стороны фасада: ведущий (по ходу) — верхние ряды {@code longwall_cut_height_blocks},
 * отстающий — почвенный ряд. Пока столбец под шнеком не вынут, контрапция стоит. Энергия столбца — H_w по крепости
 * ({@link Longwall}), мощность — привод от скорости вала gantry, не больше свободной мощности сети; её же комбайн
 * объявляет стрессом на шестерню вала gantry под кареткой. Уголь падает за корпус — на конвейер; пласт отдаёт газ и
 * пыль (орошение из баков контрапции — пыли в 5 раз меньше).
 */
public final class ShearerMovementBehaviour implements MovementBehaviour {
    private static final int LEAD = 0, TRAIL = 1;
    private static final double DT = 0.05;
    /** Наибольшая высота полосы — номера трещин {@code destroyBlockProgress} на шнек. */
    private static final int MAX_ROWS = 4;
    /** Период пересчёта свободной мощности сети, тиков (как у клеток-входов). */
    private static final int SPARE_PERIOD_TICKS = 10;

    /** Состояние резания на время движения (не сохраняется: после загрузки столбец начинается заново). */
    private static final class Work {
        final ShearerCut[] cuts = {new ShearerCut(), new ShearerCut()};
        final List<List<BlockPos>> blocks = List.of(new ArrayList<>(), new ArrayList<>());
        final double[] coalTonnes = new double[2];
        final long[] column = {Long.MIN_VALUE, Long.MIN_VALUE};
        final int[] shownProgress = {-1, -1};
        final int breakerBase = -BlockBreakingKineticBlockEntity.NEXT_BREAKER_ID.getAndAdd(2 * MAX_ROWS) - 1;
        StressFilter stress = new StressFilter();
        @Nullable GantryShaftBlockEntity shaft;
        float declared;
        double spareW;
        long spareAt = Long.MIN_VALUE / 2;
        double powerW;

        int breakerId(int drum, int row) {
            return breakerBase - drum * MAX_ROWS - row;
        }
    }

    @Override
    public void tick(MovementContext ctx) {
        if (!(ctx.world instanceof ServerLevel level)) return;
        Work w = work(ctx);
        LongwallConfig cfg = SimConfigs.longwall();
        Direction facing = ctx.state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Direction.Axis axis = LongwallShearerBlock.travelAxis(facing);
        GantryShaftBlockEntity shaft = pinionShaft(ctx, level);
        bindShaft(w, shaft);
        double m = axis.choose(ctx.motion.x, ctx.motion.y, ctx.motion.z);
        if (shaft == null || shaft.getSpeed() == 0 || Math.abs(m) < 1e-9) {
            idle(level, w);
            ctx.stall = false;
            return;
        }
        int s = m > 0 ? 1 : -1;
        double c = axis.choose(ctx.position.x, ctx.position.y, ctx.position.z);
        BlockPos ctrl = BlockPos.containing(ctx.position);
        for (int d = LEAD; d <= TRAIL; d++) {
            // Столбец, в который шнек вот-вот войдёт: центр шнека ± 1 вдоль хода, следующая граница блока.
            int col = Mth.floor(c + (d == LEAD ? s : -s) + s * 0.501);
            long key = ((long) col << 1) | (s > 0 ? 1 : 0);
            if (w.column[d] == key) continue;
            clearDrum(level, w, d);
            w.column[d] = key;
            plan(level, w, d, cfg, ctrl, facing, axis, col);
        }

        long now = level.getGameTime();
        double rpm = shaft.getSpeed();
        if (now - w.spareAt >= SPARE_PERIOD_TICKS) {
            w.spareW = sparePowerW(shaft, w.declared);
            w.spareAt = now;
        }
        double p = Longwall.cuttingPowerW(cfg, rpm, w.spareW);
        double remSum = w.cuts[LEAD].remainingJ() + w.cuts[TRAIL].remainingJ();
        double used = 0;
        boolean stall = false;
        for (int d = LEAD; d <= TRAIL; d++) {
            ShearerCut cut = w.cuts[d];
            if (!cut.active()) continue;
            double share = remSum > 0 ? p * cut.remainingJ() / remSum : p;
            double alloc = Math.min(cut.demandW(p), share);
            used += alloc;
            if (cut.advance(alloc, DT)) {
                breakColumn(ctx, level, w, d, cfg, ctrl, facing);
                clearDrum(level, w, d);
            } else {
                stall = true;
                showProgress(level, w, d, now);
            }
        }
        w.powerW = used;
        ctx.stall = stall;
        if (w.stress.update(used, rpm, cfg.shearerRatedW(), cfg.nominalRpm(), now)) declare(w, w.stress.impact());
        else declare(w, w.declared);
    }

    @Override
    public void stopMoving(MovementContext ctx) {
        if (!(ctx.world instanceof ServerLevel level)) return;
        Work w = work(ctx);
        idle(level, w);
        bindShaft(w, null);
    }

    @Override
    public void cancelStall(MovementContext ctx) {
        MovementBehaviour.super.cancelStall(ctx);
        if (ctx.world instanceof ServerLevel level) idle(level, work(ctx));
    }

    private static Work work(MovementContext ctx) {
        if (ctx.temporaryData instanceof Work w) return w;
        Work w = new Work();
        ctx.temporaryData = w;
        return w;
    }

    // --- столбцы ---------------------------------------------------------------------------------------------

    private static void plan(ServerLevel level, Work w, int d, LongwallConfig cfg, BlockPos ctrl, Direction facing,
            Direction.Axis axis, int col) {
        BlockPos base = (axis == Direction.Axis.X ? new BlockPos(col, ctrl.getY(), ctrl.getZ())
                : new BlockPos(ctrl.getX(), ctrl.getY(), col)).relative(facing);
        int h = Math.min(MAX_ROWS, cfg.cutHeightBlocks());
        int y0 = d == LEAD ? 1 : 0, y1 = d == LEAD ? h - 1 : Math.min(0, h - 1);
        List<BlockPos> list = w.blocks.get(d);
        double energy = 0, coal = 0;
        for (int dy = y0; dy <= y1; dy++) {
            BlockPos p = base.above(dy);
            BlockState st = level.getBlockState(p);
            if (!cuttable(level, p, st)) continue;
            list.add(p);
            if (st.is(PcBlocks.COAL_SEAM.get())) {
                double vdaf = VeinFields.get(level.getSeed()).volatileDafPct(p.getX(), p.getY(), p.getZ()) / 100;
                energy += Longwall.blockEnergyJ(Longwall.coalProtodyakonov(vdaf), MethaneField.COAL_DENSITY);
                coal += MethaneField.COAL_DENSITY / 1000;
            } else {
                energy += Longwall.blockEnergyJ(cfg.rockProtodyakonov(), Longwall.ROCK_DENSITY);
            }
        }
        w.coalTonnes[d] = coal;
        if (!list.isEmpty()) w.cuts[d].start(energy, Longwall.minColumnTimeS(cfg));
    }

    /** Режется всё, что ломает бур Create, кроме блоков с BE и устройств и конструкций мода. */
    static boolean cuttable(ServerLevel level, BlockPos p, BlockState st) {
        if (st.isAir() || st.getBlock() instanceof PcDeviceBlock || st.getBlock() instanceof MultiblockPartBlock) return false;
        if (st.getBlock() instanceof PcBlock b && !b.spec().footprint().isSingle()) return false;
        if (level.getBlockEntity(p) != null) return false;
        return BlockBreakingKineticBlockEntity.isBreakable(st, st.getDestroySpeed(level, p));
    }

    private static void breakColumn(MovementContext ctx, ServerLevel level, Work w, int d, LongwallConfig cfg, BlockPos ctrl,
            Direction facing) {
        double dust = spray(ctx, cfg, w.coalTonnes[d]) ? cfg.sprayDustFactor() : 1;
        SimLevelBinding b = SimBindings.get(level);
        BlockPos behind = ctrl.relative(facing.getOpposite());
        Vec3 drop = level.getBlockState(behind).getCollisionShape(level, behind).isEmpty()
                ? new Vec3(behind.getX() + 0.5, ctrl.getY() + 0.75, behind.getZ() + 0.5)
                : Vec3.atCenterOf(ctrl).add(0, 0.9, 0);
        for (BlockPos p : w.blocks.get(d)) {
            BlockState st = level.getBlockState(p);
            if (!cuttable(level, p, st)) continue;
            if (b != null) b.mines().onBlockBroken(p, st, dust);
            for (ItemStack stack : Block.getDrops(st, level, p, null)) {
                ItemEntity e = new ItemEntity(level, drop.x, drop.y, drop.z, stack, 0, 0, 0);
                e.setDefaultPickUpDelay();
                level.addFreshEntity(e);
            }
            level.levelEvent(2001, p, Block.getId(st));
            level.setBlock(p, st.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
        }
    }

    /** Орошение: вода с баков контрапции на уголь столбца; хватило — {@code true}. */
    private static boolean spray(MovementContext ctx, LongwallConfig cfg, double coalTonnes) {
        int mb = (int) Math.ceil(Longwall.sprayWaterM3(cfg, coalTonnes) * 1000);
        if (mb <= 0) return false;
        IFluidHandler tanks = ctx.contraption.getStorage().getFluids();
        FluidStack want = new FluidStack(Fluids.WATER, mb);
        if (tanks.drain(want, IFluidHandler.FluidAction.SIMULATE).getAmount() < mb) return false;
        tanks.drain(want, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    private static void showProgress(ServerLevel level, Work w, int d, long now) {
        int stage = Math.min(9, (int) (w.cuts[d].progress() * 10));
        if (stage == w.shownProgress[d]) return;
        w.shownProgress[d] = stage;
        List<BlockPos> list = w.blocks.get(d);
        for (int i = 0; i < list.size(); i++) level.destroyBlockProgress(w.breakerId(d, i), list.get(i), stage);
        if (!list.isEmpty()) {
            BlockPos p = list.get(0);
            BlockState st = level.getBlockState(p);
            level.playSound(null, p, st.getSoundType(level, p, null).getHitSound(), SoundSource.BLOCKS, 0.5f, 0.7f);
        }
    }

    private static void clearDrum(ServerLevel level, Work w, int d) {
        List<BlockPos> list = w.blocks.get(d);
        if (w.shownProgress[d] >= 0)
            for (int i = 0; i < list.size(); i++) level.destroyBlockProgress(w.breakerId(d, i), list.get(i), -1);
        list.clear();
        w.cuts[d].clear();
        w.coalTonnes[d] = 0;
        w.shownProgress[d] = -1;
    }

    private static void idle(ServerLevel level, Work w) {
        for (int d = LEAD; d <= TRAIL; d++) {
            clearDrum(level, w, d);
            w.column[d] = Long.MIN_VALUE;
        }
        w.powerW = 0;
        declare(w, 0);
    }

    // --- вал gantry -----------------------------------------------------------------------------------------

    /** Вал gantry под кареткой — как его ищет сама контрапция Create. */
    @Nullable
    private static GantryShaftBlockEntity pinionShaft(MovementContext ctx, ServerLevel level) {
        if (!(ctx.contraption instanceof GantryContraption g) || ctx.contraption.entity == null) return null;
        BlockPos p = BlockPos.containing(ctx.contraption.entity.getAnchorVec().add(0.5, 0.5, 0.5)).relative(g.getFacing().getOpposite());
        return level.getBlockEntity(p) instanceof GantryShaftBlockEntity s ? s : null;
    }

    private static void bindShaft(Work w, @Nullable GantryShaftBlockEntity shaft) {
        if (w.shaft == shaft) return;
        declare(w, 0);
        w.shaft = shaft;
        w.stress = new StressFilter();
        w.spareAt = Long.MIN_VALUE / 2;
    }

    /** Нагрузка шнеков сверх собственной нагрузки вала, SU/RPM — в сеть вала под кареткой. */
    private static void declare(Work w, float impact) {
        GantryShaftBlockEntity s = w.shaft;
        if (s == null || s.isRemoved() || !s.hasNetwork()) {
            w.declared = 0;
            return;
        }
        KineticNetwork net = s.getOrCreateNetwork();
        float desired = s.calculateStressApplied() + impact;
        Float current = net.members.get(s);
        if (current == null || Math.abs(current - desired) > 1e-4f) net.updateStressFor(s, desired);
        w.declared = impact;
    }

    /** (ёмкость − нагрузка) сети вала в SU × Вт/SU плюс своя объявленная нагрузка, Вт. */
    private static double sparePowerW(GantryShaftBlockEntity s, float declared) {
        if (!s.hasNetwork() || s.getSpeed() == 0) return 0;
        KineticNetwork net = s.getOrCreateNetwork();
        double own = declared * Math.abs(s.getSpeed());
        return Math.max(0, (net.calculateCapacity() - net.calculateStress() + own) * ServerConfig.W_PER_SU.get());
    }

    /** Мощность резания сейчас, Вт (для тестов и отладки). */
    public static double powerW(MovementContext ctx) {
        return ctx.temporaryData instanceof Work w ? w.powerW : 0;
    }
}
