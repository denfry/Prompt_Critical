package dev.denfry.promptcritical.mod.hydrogen;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.safety.PortableVentilatorBlockEntity;
import dev.denfry.promptcritical.sim.hydrogen.HydrogenGas;
import dev.denfry.promptcritical.sim.ops.ConfinedSpaceAtmosphere;
import dev.denfry.promptcritical.sim.storage.BatteryRoom;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Водородное облако у места утечки (09 §9.11.7 — водород детонационноспособен; 04c §4c.7): водород перемешивается в
 * воздухе помещения ({@link BatteryRoom}: объём — воздух, связанный с местом утечки, до {@value #ROOM_MAX_M3} м³;
 * воздухообмен — инфильтрация закрытого помещения или проём к небу, плюс переносные вентиляторы). С 25 % НКПР звенит
 * газоанализатор. Смесь от 4 % (НКПР) вспыхивает от открытого огня, лавы, факела, горящего костра или искры работающей
 * машины в этом помещении. Сила вспышки — как у аккумуляторной: 1 + 6 на кг сгоревшего водорода, не больше 8.
 */
public final class H2Hazard {
    public static final int ROOM_MAX_M3 = 4096;
    static final float BLAST_PER_KG = 6, BLAST_MAX = 8;
    private final BatteryRoom room = new BatteryRoom(ROOM_MAX_M3, BatteryRoom.ACH_OPEN);
    @Nullable
    private BlockPos at;
    private boolean ignition;
    private int sinceMeasure = Integer.MAX_VALUE / 2;
    private int explosions;

    public BatteryRoom room() {
        return room;
    }

    public int explosions() {
        return explosions;
    }

    public boolean alarm() {
        return room.alarm();
    }

    /** Под открытым небом над клеткой: сброс свечи рассеивается вверху, облака у земли нет. */
    public static boolean underSky(Level level, BlockPos p) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) <= p.getY();
    }

    /**
     * Шаг облака: {@code mol} водорода вышло в клетке {@code leak} за {@code dtS} с ({@code leak} — null, если утечки
     * нет: облако только рассеивается).
     */
    public void step(ServerLevel sl, @Nullable BlockPos leak, double mol, double dtS) {
        if (leak != null && (at == null || !leak.equals(at) || sinceMeasure > 100)) {
            at = leak.immutable();
            measure(sl, at);
            sinceMeasure = 0;
        }
        sinceMeasure += (int) Math.round(dtS * 20);
        if (at == null) return;
        room.step(dtS, Math.max(0, mol) * HydrogenGas.M);
        if (room.explosive() && ignition) {
            double kg = room.burn();
            explosions++;
            float power = (float) Math.min(BLAST_MAX, 1 + BLAST_PER_KG * kg);
            sl.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, power, true, Level.ExplosionInteraction.BLOCK);
            return;
        }
        if (room.alarm()) sl.playSound(null, at, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1f, 1.6f);
        if (room.h2M3() < 1e-3 && leak == null) at = null;
    }

    /** Помещение у клетки {@code origin}: объём, воздухообмен и есть ли в нём источник зажигания. */
    private void measure(ServerLevel level, BlockPos origin) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        boolean open = false;
        boolean fire = false;
        int fans = 0;
        while (!queue.isEmpty() && seen.size() < ROOM_MAX_M3) {
            BlockPos p = queue.poll();
            if (seen.contains(p) || !level.isLoaded(p)) continue;
            BlockState st = level.getBlockState(p);
            if (!st.isAir()) {
                fire |= ignites(st);
                if (level.getBlockEntity(p) instanceof PortableVentilatorBlockEntity v && v.running()) fans++;
                continue;
            }
            seen.add(p);
            if (underSky(level, p)) open = true;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.contains(n) && n.distManhattan(origin) <= 64) queue.add(n);
            }
        }
        if (seen.size() >= ROOM_MAX_M3) open = true;
        double volume = Math.max(1, seen.size());
        double ach = (open ? BatteryRoom.ACH_OPEN : BatteryRoom.ACH_SEALED) + fans * ConfinedSpaceAtmosphere.VENTILATOR_M3S * 3600 / volume;
        room.resize(volume, ach);
        ignition = fire;
    }

    /** Источник зажигания: огонь, лава, факел, горящий костёр, работающая машина (искра контактов и щёток). */
    static boolean ignites(BlockState st) {
        if (st.is(BlockTags.FIRE) || st.is(Blocks.LAVA) || st.is(Blocks.TORCH) || st.is(Blocks.WALL_TORCH)
                || st.is(Blocks.SOUL_TORCH) || st.is(Blocks.SOUL_WALL_TORCH)) return true;
        if (st.getBlock() instanceof CampfireBlock && st.getValue(CampfireBlock.LIT)) return true;
        return st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING);
    }
}
