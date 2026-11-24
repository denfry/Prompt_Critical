package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.sim.radiation.DoseRate;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Активные предметы в контейнерах (R55; 16 §16.8.3): сундуки и любые блоки с обработчиком предметов, сундучные
 * вагонетки и лодки, вьючные животные, рамки и стойки. Блочные сущности в загруженных чанках в {@value #RANGE_M} м от
 * игроков обходятся по кругу, не больше {@value #SCAN_PER_TICK} за тик на уровень; найденные с активностью ложатся в
 * индекс по {@link BlockPos} и перепроверяются раз в {@value #RECHECK_TICKS} тиков. Запись уходит, когда чанк выгружен,
 * блочной сущности нет или содержимое пусто. Сущности-контейнеры собираются раз в {@value #ENTITY_TICKS} тиков.
 */
public final class ContainerActivityScan {
    /** Дальность сканирования от игрока, м. */
    public static final double RANGE_M = 48;
    /** Блочных сущностей за тик на уровень (16 §16.8.3). */
    public static final int SCAN_PER_TICK = 8;
    static final int RECHECK_TICKS = 40, ENTITY_TICKS = 20;

    /** Источник в контейнере: точка, суммарный источник и собственный экран контейнера (у источника). */
    public record ContainerSource(Vec3 at, PointSource source, List<DoseRate.Layer> extraShield) {
    }

    private record Hot(ContainerSource src, long nextCheck) {
    }

    private static final class State {
        final Map<BlockPos, Hot> hot = new HashMap<>();
        List<BlockPos> queue = new ArrayList<>();
        int cursor;
        List<ContainerSource> entities = List.of();
        long nextEntityScan;
        long nextRebuild;
    }

    /** Не чаще раза в секунду пересобирать очередь [моё]. */
    static final int REBUILD_TICKS = 20;

    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();
    /** Только для GameTest: подмена карты данных (блока-контейнера с готовым экраном в моде нет). */
    private static final Map<Block, ContainerShielding> TEST_SHIELDING = new HashMap<>();

    private ContainerActivityScan() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload e) -> {
            if (!(e.getLevel() instanceof ServerLevel level)) return;
            State st = STATES.get(level);
            if (st == null) return;
            ChunkPos c = e.getChunk().getPos();
            st.hot.keySet().removeIf(p -> p.getX() >> 4 == c.x && p.getZ() >> 4 == c.z);
        });
    }

    /** Все источники в контейнерах (блоки и сущности), для {@link RadiationService#rateSvH}. */
    public static List<ContainerSource> sources(ServerLevel level) {
        State st = STATES.get(level);
        if (st == null) return List.of();
        List<ContainerSource> out = new ArrayList<>(st.hot.size() + st.entities.size());
        for (Hot h : st.hot.values()) out.add(h.src);
        out.addAll(st.entities);
        return out;
    }

    /** Размер индекса блочных контейнеров с активностью. */
    public static int indexSize(ServerLevel level) {
        State st = STATES.get(level);
        return st == null ? 0 : st.hot.size();
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        List<ServerPlayer> players = level.players();
        State st = STATES.get(level);
        if (st == null) {
            if (players.isEmpty()) return;
            st = new State();
            STATES.put(level, st);
        }
        recheckHot(level, st, now, false);
        if (players.isEmpty()) {
            st.queue = new ArrayList<>();
            st.cursor = 0;
            st.entities = List.of();
            return;
        }
        List<Vec3> centers = new ArrayList<>(players.size());
        for (ServerPlayer p : players) centers.add(p.position());
        if (st.cursor >= st.queue.size() && now >= st.nextRebuild) {
            // обход чанков не чаще раза в секунду: при малом числе блок-сущностей очередь иначе пересобиралась бы каждый тик
            st.queue = candidates(level, centers);
            st.cursor = 0;
            st.nextRebuild = now + REBUILD_TICKS;
        }
        for (int i = 0; i < SCAN_PER_TICK && st.cursor < st.queue.size(); i++) inspect(level, st, st.queue.get(st.cursor++), now);
        if (now >= st.nextEntityScan) {
            st.entities = scanEntities(level, centers, now);
            st.nextEntityScan = now + ENTITY_TICKS;
        }
    }

    /** Только для GameTest: полный синхронный проход вокруг {@code near} (как будто там игрок) и перепроверка индекса. */
    public static void scanAllNow(ServerLevel level, Vec3 near) {
        State st = STATES.computeIfAbsent(level, l -> new State());
        long now = level.getGameTime();
        recheckHot(level, st, now, true);
        for (BlockPos p : candidates(level, List.of(near))) inspect(level, st, p, now, true);
        st.entities = scanEntities(level, List.of(near), now);
    }

    /** Только для GameTest: подменить (или снять, {@code null}) экран блока. */
    public static void overrideShielding(Block block, @Nullable ContainerShielding s) {
        if (s == null) TEST_SHIELDING.remove(block);
        else TEST_SHIELDING.put(block, s);
    }

    private static void recheckHot(ServerLevel level, State st, long now, boolean force) {
        for (Iterator<Map.Entry<BlockPos, Hot>> it = st.hot.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Hot> e = it.next();
            if (!force && e.getValue().nextCheck > now) continue;
            BlockPos pos = e.getKey();
            if (!level.isLoaded(pos)) {
                it.remove();
                continue;
            }
            ContainerSource s = read(level, pos, now);
            if (s == null) it.remove();
            else e.setValue(new Hot(s, now + RECHECK_TICKS));
        }
    }

    private static void inspect(ServerLevel level, State st, BlockPos pos, long now) {
        inspect(level, st, pos, now, false);
    }

    private static void inspect(ServerLevel level, State st, BlockPos pos, long now, boolean force) {
        BlockPos key = pos.immutable();
        if (!level.isLoaded(key)) { // чанк выгрузили, пока позиция стояла в очереди: getBlockEntity загрузил бы его
            st.hot.remove(key);
            return;
        }
        Hot known = st.hot.get(key);
        if (!force && known != null && known.nextCheck > now) return; // индекс уже перепроверяет сам
        ContainerSource s = read(level, key, now);
        if (s == null) st.hot.remove(key);
        else st.hot.put(key, new Hot(s, now + RECHECK_TICKS));
    }

    /** Блочные сущности загруженных чанков в радиусе от любого из центров. */
    private static List<BlockPos> candidates(ServerLevel level, List<Vec3> centers) {
        Set<BlockPos> seen = new HashSet<>();
        Set<Long> chunks = new HashSet<>();
        int reach = (int) Math.ceil(RANGE_M / 16);
        for (Vec3 c : centers) {
            int cx = (int) Math.floor(c.x) >> 4, cz = (int) Math.floor(c.z) >> 4;
            for (int dx = -reach; dx <= reach; dx++)
                for (int dz = -reach; dz <= reach; dz++) {
                    if (!chunks.add(ChunkPos.asLong(cx + dx, cz + dz))) continue;
                    LevelChunk ch = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                    if (ch == null) continue;
                    for (BlockPos p : ch.getBlockEntities().keySet())
                        for (Vec3 c2 : centers)
                            if (c2.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= RANGE_M * RANGE_M) {
                                seen.add(p);
                                break;
                            }
                }
        }
        return new ArrayList<>(seen);
    }

    /** Источник блочного контейнера; {@code null} — блочной сущности нет, она без инвентаря или в нём нет активности. */
    @Nullable
    private static ContainerSource read(ServerLevel level, BlockPos pos, long now) {
        if (!level.isLoaded(pos)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be.isRemoved() || be instanceof SelfEmittingSource) return null;
        PointSource sum = PointSource.NONE;
        if (be instanceof ChestBlockEntity chest) {
            // половинка двойного сундука отдаёт через обработчик весь сундук: считаем по своим 27 ячейкам, без двойного счёта
            sum = sumContainer(chest, now);
        } else {
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (h != null) {
                for (int i = 0; i < h.getSlots(); i++) sum = plus(sum, RadiationService.itemSource(h.getStackInSlot(i), now));
            } else if (be instanceof Container c) sum = sumContainer(c, now);
        }
        if (sum.empty()) return null;
        sightAll(level, be, pos);
        List<DoseRate.Layer> extra = new ArrayList<>(1);
        Block block = be.getBlockState().getBlock();
        ContainerShielding cs = TEST_SHIELDING.get(block);
        if (cs == null) cs = block.builtInRegistryHolder().getData(ContainerShielding.TYPE);
        DoseRate.Layer l = cs == null ? null : cs.layer();
        if (l != null) extra.add(l);
        return new ContainerSource(Vec3.atCenterOf(pos), sum, List.copyOf(extra));
    }

    private static List<ContainerSource> scanEntities(ServerLevel level, List<Vec3> centers, long now) {
        List<ContainerSource> out = new ArrayList<>();
        Set<Entity> seen = new HashSet<>();
        for (Vec3 c : centers) {
            AABB box = new AABB(c, c).inflate(RANGE_M);
            for (Entity e : level.getEntities((Entity) null, box, ContainerActivityScan::isContainerEntity)) {
                if (!seen.add(e) || e.position().distanceToSqr(c) > RANGE_M * RANGE_M) continue;
                PointSource sum = PointSource.NONE;
                if (e instanceof ContainerEntity ce) {
                    for (ItemStack s : ce.getItemStacks()) sum = plus(sum, RadiationService.itemSource(s, now));
                } else if (e instanceof AbstractChestedHorse horse) {
                    sum = sumContainer(horse.getInventory(), now);
                } else if (e instanceof ItemFrame f) {
                    sum = plus(sum, RadiationService.itemSource(f.getItem(), now));
                } else if (e instanceof ArmorStand a) {
                    for (ItemStack s : a.getAllSlots()) sum = plus(sum, RadiationService.itemSource(s, now));
                }
                if (sum.empty()) continue;
                out.add(new ContainerSource(e.position().add(0, 0.5, 0), sum, List.of()));
                Vec3 at = e.position().add(0, 0.5, 0);
                if (e instanceof ContainerEntity ce) ce.getItemStacks().forEach(s -> SourceLedger.sight(level, s, at, null, false));
                else if (e instanceof AbstractChestedHorse horse) sightContainer(level, horse.getInventory(), at);
                else if (e instanceof ItemFrame f) SourceLedger.sight(level, f.getItem(), at, null, false);
                else if (e instanceof ArmorStand a) a.getAllSlots().forEach(s -> SourceLedger.sight(level, s, at, null, false));
            }
        }
        return out;
    }

    /** Учтённые источники в блочном контейнере (16 §16.8.4): только уже помеченные, чужой обработчик не меняем. */
    private static void sightAll(ServerLevel level, BlockEntity be, BlockPos pos) {
        Vec3 at = Vec3.atCenterOf(pos);
        if (be instanceof ChestBlockEntity chest) {
            sightContainer(level, chest, at);
            return;
        }
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (h != null) for (int i = 0; i < h.getSlots(); i++) SourceLedger.sight(level, h.getStackInSlot(i), at, null, false);
        else if (be instanceof Container c) sightContainer(level, c, at);
    }

    private static void sightContainer(ServerLevel level, Container c, Vec3 at) {
        for (int i = 0; i < c.getContainerSize(); i++) SourceLedger.sight(level, c.getItem(i), at, null, false);
    }

    private static boolean isContainerEntity(Entity e) {
        return e instanceof ContainerEntity || e instanceof AbstractChestedHorse || e instanceof ItemFrame || e instanceof ArmorStand;
    }

    private static PointSource sumContainer(Container c, long now) {
        PointSource sum = PointSource.NONE;
        for (int i = 0; i < c.getContainerSize(); i++) sum = plus(sum, RadiationService.itemSource(c.getItem(i), now));
        return sum;
    }

    private static PointSource plus(PointSource a, @Nullable PointSource b) {
        return b == null ? a : a.plus(b);
    }
}
