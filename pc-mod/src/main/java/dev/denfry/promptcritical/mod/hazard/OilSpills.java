package dev.denfry.promptcritical.mod.hazard;

import dev.denfry.promptcritical.mod.environment.AirQualityService;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.environment.EmissionLedger;
import dev.denfry.promptcritical.sim.hazard.OilSpill;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Разливы нефти в мире (09 §9.11.9, 14 §14.5). {@link #spill} — общий вход для перелива резервуара, разрыва
 * нефтепровода и фонтана:
 * <ol>
 *   <li>на суше — до {@value OilSpill#MAX_FLUID_BLOCKS} блоков {@code crude_oil} (1 м³ каждый) растекаются по рельефу
 *   и горят при поджоге (пожар пролива §9.11.4 — {@link HazardService}); остальное — в учёт;</li>
 *   <li>на воде — пятно ({@link OilSpill}: Фэй, Фингас, обводнение) живёт во времени SLOW (1 с игры = 72 с физики, 00
 *   §0.7): видимая часть — до {@value OilSpill#MAX_SLICK_BLOCKS} блоков {@code oil_slick} по связной поверхности воды,
 *   через боны не переходит; доходя до песка и гравия, мажет берег ({@code oiled_sand}/{@code oiled_gravel}, до
 *   {@value #MAX_SHORE_BLOCKS} блоков на пятно — {@code env.max_block_effects_per_chunk_per_day}); испарение — VOC в
 *   {@link EmissionLedger} (категория SPILL);</li>
 *   <li>каждый разлив записывается в {@link EmissionLedger#recordSpill}.</li>
 * </ol>
 * Режим {@code creative_safe} (04d §4d.1): мир не пачкается, только учёт. Берег пачкается напрямую, а не через очередь
 * {@code DamageBudget}: у очереди нет вида изменения «заменить блок», только разрушение.
 */
public final class OilSpills {
    public static final int MAX_SHORE_BLOCKS = 64;
    /** Блоков плёнки за секунду: раскладка не грузит сервер. */
    static final int PLACE_PER_S = 32;
    /** K_slow: 1 с игры — 72 с физики (00 §0.7). */
    public static final double K_SLOW = 72;
    /** Доля, выкипающая до 180 °C, по умолчанию — пример 14 §14.5.3 (нефть средняя) [моё]. */
    public static final double DEFAULT_D180_PCT = 30;
    static final String NAME = "promptcritical_oil_spills";

    /** Пятно на воде или пролив на суше. */
    public static final class Spill {
        final int id;
        final BlockPos origin;
        final String fluidId;
        final boolean onWater;
        final OilSpill model;
        final List<BlockPos> blocks = new ArrayList<>();
        int shoreBlocks;
        double vocLoggedKg;
        /** Пролив на суше: литры, ещё не ставшие блоком (меньше кубометра). */
        double pendingL;
        boolean enclosed;
        int reachable;

        Spill(int id, BlockPos origin, String fluidId, boolean onWater, OilSpill model) {
            this.id = id;
            this.origin = origin;
            this.fluidId = fluidId;
            this.onWater = onWater;
            this.model = model;
        }

        public OilSpill model() {
            return model;
        }

        public boolean onWater() {
            return onWater;
        }

        public List<BlockPos> blocks() {
            return blocks;
        }

        /** Площадь пятна с учётом бонов: если боны замкнули плёнку, она не шире огороженной воды, м². */
        public double effectiveAreaM2() {
            double a = model.areaM2();
            return enclosed ? Math.min(a, Math.max(1, reachable)) : a;
        }

        /** Толщина плёнки, м. */
        public double thicknessM() {
            return model.thicknessM(effectiveAreaM2());
        }

        String ledgerId() {
            return "spill#" + id;
        }
    }

    static final class Data extends SavedData {
        final List<Spill> spills = new ArrayList<>();
        int nextId = 1;

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            d.nextId = tag.getInt("next");
            for (Tag t : tag.getList("spills", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                OilSpill m = OilSpill.restore(c.getDouble("kg0"), c.getDouble("rho"), c.getDouble("d180"), c.getBoolean("sea"), c.getDouble("age"),
                        c.getDouble("evap"), c.getDouble("rec"), c.getDouble("burn"));
                Spill s = new Spill(c.getInt("id"), BlockPos.of(c.getLong("origin")), c.getString("fluid"), c.getBoolean("water"), m);
                for (long l : c.getLongArray("blocks")) s.blocks.add(BlockPos.of(l));
                s.shoreBlocks = c.getInt("shore");
                s.vocLoggedKg = c.getDouble("voc");
                s.pendingL = c.getDouble("pending");
                d.spills.add(s);
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("next", nextId);
            ListTag list = new ListTag();
            for (Spill s : spills) {
                CompoundTag c = new CompoundTag();
                OilSpill m = s.model;
                c.putInt("id", s.id);
                c.putLong("origin", s.origin.asLong());
                c.putString("fluid", s.fluidId);
                c.putBoolean("water", s.onWater);
                c.putDouble("kg0", m.initialKg());
                c.putDouble("rho", m.oilDensity());
                c.putDouble("d180", m.d180Pct());
                c.putBoolean("sea", m.sea());
                c.putDouble("age", m.ageS());
                c.putDouble("evap", m.evaporatedKg());
                c.putDouble("rec", m.recoveredKg());
                c.putDouble("burn", m.burnedKg());
                c.putLongArray("blocks", s.blocks.stream().mapToLong(BlockPos::asLong).toArray());
                c.putInt("shore", s.shoreBlocks);
                c.putDouble("voc", s.vocLoggedKg);
                c.putDouble("pending", s.pendingL);
                list.add(c);
            }
            tag.put("spills", list);
            return tag;
        }
    }

    private OilSpills() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(OilSpills::onLevelTick);
    }

    static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
    }

    public static List<Spill> spills(ServerLevel level) {
        return data(level).spills;
    }

    /**
     * Разлив {@code litres} литров жидкости {@code fluidId} плотностью {@code densityKgM3} в точке {@code at}.
     *
     * @return запись пятна или пролива; {@code null}, если мир не пачкается (creative_safe) или пролито ноль
     */
    public static Spill spill(ServerLevel level, BlockPos at, String fluidId, double densityKgM3, double litres) {
        if (!(litres > 0)) return null;
        boolean water = level.getFluidState(at).is(FluidTags.WATER) || level.getFluidState(at.below()).is(FluidTags.WATER);
        double m3 = litres / 1000;
        EmissionLedger ledger = AirQualityService.ledger(level);
        ledger.recordSpill(at.asLong(), fluidId, m3, water);
        if (!HazardService.harmful()) return null;
        Data d = data(level);
        BlockPos origin = surface(level, at);
        d.setDirty();
        if (!water) {
            // непрерывный разлив в одном месте (перелив, течь) копится в одну лужу: блок на каждый полный кубометр
            Spill s = null;
            for (Spill o : d.spills) if (!o.onWater && o.origin.equals(origin)) s = o;
            boolean fresh = s == null;
            if (fresh) {
                s = new Spill(d.nextId++, origin, fluidId, false, new OilSpill(m3 * densityKgM3, densityKgM3, DEFAULT_D180_PCT, false));
                d.spills.add(s);
            }
            s.pendingL += litres;
            int want = (int) Math.min(OilSpill.MAX_FLUID_BLOCKS, s.blocks.size() + Math.floor(s.pendingL / 1000 + 1e-9));
            for (BlockPos p : floodLand(level, s.origin, want)) {
                if (s.blocks.size() >= want) break;
                if (s.blocks.contains(p)) continue;
                level.setBlock(p, PcBlocks.CRUDE_OIL.get().defaultBlockState(), Block.UPDATE_ALL);
                s.blocks.add(p);
                s.pendingL -= 1000;
            }
            if (fresh) HazardService.tell(level, at, 48, HazardText.msg("spill.land", Math.round(m3 * 10) / 10.0));
            return s;
        }
        Spill s = new Spill(d.nextId++, origin, fluidId, true, new OilSpill(m3 * densityKgM3, densityKgM3, DEFAULT_D180_PCT, false));
        d.spills.add(s);
        HazardService.tell(level, at, 64, HazardText.msg("spill.water", Math.round(m3 * 10) / 10.0));
        return s;
    }

    /** Клетка над водой (у воды) или место на суше. */
    private static BlockPos surface(ServerLevel level, BlockPos at) {
        BlockPos p = at;
        for (int i = 0; i < 8 && level.getFluidState(p).is(FluidTags.WATER); i++) p = p.above();
        return p;
    }

    /** Клетки для нефти на суше: свободные, с опорой снизу, связные, начиная с самых низких рядом с точкой. */
    private static List<BlockPos> floodLand(ServerLevel level, BlockPos start, int n) {
        List<BlockPos> out = new ArrayList<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        BlockPos s = start;
        for (int i = 0; i < 6 && free(level, s.below()); i++) s = s.below();
        q.add(s);
        seen.add(s);
        while (!q.isEmpty() && out.size() < n && seen.size() < 4096) {
            BlockPos p = q.poll();
            if (!free(level, p)) continue;
            if (free(level, p.below())) continue; // нет опоры: туда стечёт жидкость, но источник не ставим
            out.add(p);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos nb = p.relative(d);
                BlockPos down = nb.below();
                BlockPos next = free(level, nb) && free(level, down) ? down : nb;
                if (seen.add(next)) q.add(next);
            }
        }
        return out;
    }

    private static boolean free(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p)) return false;
        BlockState st = level.getBlockState(p);
        return st.isAir() || st.canBeReplaced() && st.getFluidState().isEmpty();
    }

    /** Горящие клетки проливов на суше: огонь над или рядом с блоком нефти. */
    public static List<BlockPos> burningCells(ServerLevel level) {
        List<BlockPos> out = new ArrayList<>();
        SavedData sd = level.getDataStorage().get(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        if (!(sd instanceof Data d)) return out;
        for (Spill s : d.spills) {
            if (s.onWater) continue;
            for (BlockPos p : s.blocks) {
                if (!level.isLoaded(p)) continue;
                for (Direction dir : new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST})
                    if (level.getBlockState(p.relative(dir)).getBlock() instanceof BaseFireBlock) {
                        out.add(p);
                        break;
                    }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ тик

    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 10) return;
        SavedData sd = level.getDataStorage().get(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        if (!(sd instanceof Data d) || d.spills.isEmpty()) return;
        boolean dirty = false;
        for (var it = d.spills.iterator(); it.hasNext(); ) {
            Spill s = it.next();
            if (!level.isLoaded(s.origin)) continue;
            dirty = true;
            if (!s.onWater) {
                // проливы на суше: блоки, которые выгорели или убраны, — из записи вон
                s.blocks.removeIf(p -> level.isLoaded(p) && !level.getBlockState(p).is(PcBlocks.CRUDE_OIL.get()));
                if (s.blocks.isEmpty() && s.pendingL < 1000 && s.model.ageS() > 600) it.remove();
                s.model.advance(1, -1000); // только возраст: испарение на суше не считаем
                continue;
            }
            tickWater(level, s);
            if (s.model.remainingKg() <= 1) {
                for (BlockPos p : s.blocks) if (level.getBlockState(p).is(PcBlocks.OIL_SLICK.get())) level.removeBlock(p, false);
                it.remove();
            }
        }
        if (dirty) d.setDirty();
    }

    private static void tickWater(ServerLevel level, Spill s) {
        double tC = EnvironmentService.waterTempC(level, s.origin);
        s.model.advance(K_SLOW, tC);
        double voc = s.model.evaporatedKg() - s.vocLoggedKg;
        if (voc > 0) {
            AirQualityService.ledger(level).record(s.ledgerId(), EmissionLedger.Category.SPILL, EmissionLedger.Pollutant.VOC, voc, 1);
            s.vocLoggedKg = s.model.evaporatedKg();
        }
        OilSpill m = s.model;
        double gone = m.recoveredKg() + m.burnedKg(), left = m.remainingKg();
        int target = (int) Math.ceil(m.slickBlocks(m.areaM2()) * (left / Math.max(1e-9, left + gone)));
        // связная поверхность воды от места разлива, через боны не переходит
        List<BlockPos> surface = waterSurface(level, s.origin, Math.max(target, s.blocks.size()) + 1);
        s.reachable = surface.size();
        s.enclosed = surface.size() <= target;
        int age = (int) Math.min(3, m.ageS() / 21_600); // стадия «мусса» через каждые 6 ч физики
        int placed = 0;
        Set<BlockPos> have = new HashSet<>(s.blocks);
        for (BlockPos p : surface) {
            if (have.size() >= target || placed >= PLACE_PER_S) break;
            if (have.contains(p) || !level.getBlockState(p).isAir()) continue;
            level.setBlock(p, PcBlocks.OIL_SLICK.get().defaultBlockState().setValue(SpillBlocks.OilSlickBlock.AGE, age), Block.UPDATE_CLIENTS);
            s.blocks.add(p);
            have.add(p);
            placed++;
            if (s.shoreBlocks < MAX_SHORE_BLOCKS) oilShore(level, s, p);
        }
        while (s.blocks.size() > target) {
            BlockPos p = s.blocks.removeLast();
            if (level.getBlockState(p).is(PcBlocks.OIL_SLICK.get())) level.removeBlock(p, false);
        }
    }

    /** Песок и гравий у кромки воды рядом с плёнкой мажутся нефтью. */
    private static void oilShore(ServerLevel level, Spill s, BlockPos slick) {
        for (Direction d : Direction.Plane.HORIZONTAL)
            for (int dy = -1; dy <= 0; dy++) {
                BlockPos p = slick.relative(d).above(dy);
                BlockState st = level.getBlockState(p);
                Block to = st.is(Blocks.SAND) ? PcBlocks.OILED_SAND.get() : st.is(Blocks.GRAVEL) ? PcBlocks.OILED_GRAVEL.get() : null;
                if (to == null || s.shoreBlocks >= MAX_SHORE_BLOCKS) continue;
                level.setBlock(p, to.defaultBlockState(), Block.UPDATE_ALL);
                s.shoreBlocks++;
            }
    }

    /** Клетки над водой, связные с {@code start} (BFS), не дальше {@code limit} штук; боны — стенка. */
    static List<BlockPos> waterSurface(ServerLevel level, BlockPos start, int limit) {
        List<BlockPos> out = new ArrayList<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && out.size() < limit) {
            BlockPos p = q.poll();
            if (!level.isLoaded(p)) continue;
            BlockState st = level.getBlockState(p);
            if (st.is(PcBlocks.OIL_BOOM.get())) continue;
            if (!(st.isAir() || st.is(PcBlocks.OIL_SLICK.get())) || !level.getFluidState(p.below()).is(FluidTags.WATER)) continue;
            out.add(p);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (seen.add(n)) q.add(n);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ сбор

    /** Пятно на воде, плёнка которого лежит в {@code pos}. */
    public static Spill spillAt(ServerLevel level, BlockPos pos) {
        for (Spill s : spills(level)) if (s.onWater && s.blocks.contains(pos)) return s;
        return null;
    }

    /** Ближайшее пятно на воде с плёнкой в радиусе {@code r} от {@code pos}. */
    public static Spill spillNear(ServerLevel level, BlockPos pos, int r) {
        Spill best = null;
        double bestD = Double.MAX_VALUE;
        for (Spill s : spills(level)) {
            if (!s.onWater) continue;
            for (BlockPos p : s.blocks) {
                double d = p.distSqr(pos);
                if (d <= (double) r * r && d < bestD) {
                    bestD = d;
                    best = s;
                }
            }
        }
        return best;
    }

    /** Собрать с пятна до {@code kg} нефти; возвращает собранное. */
    public static double recover(ServerLevel level, Spill s, double kg) {
        double got = s.model.recover(kg);
        data(level).setDirty();
        return got;
    }

    /** Стенд (GameTest): прогнать пятно на {@code seconds} игровых секунд сразу. */
    public static void age(ServerLevel level, Spill s, int seconds) {
        for (int i = 0; i < seconds; i++) tickWater(level, s);
    }
}
