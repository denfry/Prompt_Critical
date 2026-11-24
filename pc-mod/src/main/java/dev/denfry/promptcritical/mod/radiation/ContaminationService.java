package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.radiation.ContaminationExposure;
import dev.denfry.promptcritical.sim.radiation.ContaminationProbe;
import dev.denfry.promptcritical.sim.radiation.ContaminationTransfer;
import dev.denfry.promptcritical.sim.radiation.DeconMethod;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.radiation.SurfaceContamination;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Поверхностное радиоактивное загрязнение в мире ({@link SurfaceContamination}): на верхних гранях блоков
 * ({@link ContaminationMap}), на предметах ({@link ContaminationData}) и на игроке — руки и обувь отдельно, в данных
 * игрока. Раз в секунду для каждого игрока:
 * <ul>
 *   <li>шаги по полу меняются рыхлым загрязнением с обувью ({@link ContaminationTransfer#walk}) — грязь разносится
 *       из зоны по чистым коридорам;</li>
 *   <li>предмет в руке меняется загрязнением с руками ({@link ContaminationTransfer#GRIP_K});</li>
 *   <li>рыхлое с пола поднимается в воздух и попадает в рот, с рук — в рот: ожидаемая доза
 *       ({@link ContaminationExposure}) идёт в учёт доз {@link RadiationService#addCommitted};</li>
 *   <li>β-загрязнение пола (Cs-137) светит γ: сумма по секции 16³ — точечный источник в её центре масс.</li>
 * </ul>
 * Источники загрязнения: {@link #spill} — проливы и выбросы аварий (оголённый стеллаж бассейна выдержки, СЦР в каньоне
 * PUREX, разрыв первого контура); разбор загрязнённого блока переносит его загрязнение на выпавшие предметы.
 * Дезактивация — душ санпропускника, ультразвуковая ванна, снимаемое покрытие.
 */
public final class ContaminationService {
    static final String TAG = PromptCritical.MOD_ID + ":contamination";
    /** Доля снимаемого в свежем проливе: капли и пыль на сухой поверхности почти целиком рыхлые. */
    public static final double SPILL_LOOSE = 0.9;
    public static final ContaminationProbe PROBE = ContaminationProbe.STANDARD;
    /** Ниже этой β-активности на секцию (Бк) γ пола не считаем: 10 МБк Cs-137 на метре — сотые доли мкЗв/ч. */
    static final double GAMMA_MIN_BQ = 1e7;
    /** Высыхание снимаемого покрытия: 4 ч игрового времени (типовые латексные составы — 4–24 ч), тик — 3,6 с. */
    public static final long CURE_TICKS = (long) (4 * 3600 / 3.6);
    /** Площадь касания ладонями при хвате предмета или разборе блока, см². */
    static final double GRIP_CM2 = 50;
    /** Где сейчас стоит γ-источник каждой секции уровня (ключ — {@link SectionPos#asLong}). */
    private static final Map<ServerLevel, Map<Long, Vec3>> GAMMA_AT = new WeakHashMap<>();

    private ContaminationService() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel s && s.getGameTime() % 20 == 0) tick(s);
        });
        NeoForge.EVENT_BUS.addListener(ContaminationService::onDrops);
        NeoForge.EVENT_BUS.addListener(ContaminationService::onRightClick);
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent e) -> {
            SurfaceContamination c = ContaminationData.get(e.getItemStack());
            if (c.negligible()) return;
            e.getToolTip().add(ContaminationText.msg("tooltip", ContaminationText.num(c.alpha()), ContaminationText.num(c.beta()))
                    .withStyle(ChatFormatting.GOLD));
        });
    }

    // --- игрок ---

    private static CompoundTag data(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(TAG)) root.put(TAG, new CompoundTag());
        return root.getCompound(TAG);
    }

    private static SurfaceContamination read(CompoundTag d, String k) {
        if (!d.contains(k)) return SurfaceContamination.NONE;
        CompoundTag t = d.getCompound(k);
        return new SurfaceContamination(Math.max(0, t.getDouble("a")), Math.max(0, t.getDouble("af")), Math.max(0, t.getDouble("b")),
                Math.max(0, t.getDouble("bf")));
    }

    private static void write(CompoundTag d, String k, SurfaceContamination c) {
        if (c.negligible()) {
            d.remove(k);
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putDouble("a", c.alphaLoose());
        t.putDouble("af", c.alphaFixed());
        t.putDouble("b", c.betaLoose());
        t.putDouble("bf", c.betaFixed());
        d.put(k, t);
    }

    public static SurfaceContamination hands(ServerPlayer p) {
        return read(data(p), "hands");
    }

    public static void setHands(ServerPlayer p, SurfaceContamination c) {
        write(data(p), "hands", c);
    }

    public static SurfaceContamination shoes(ServerPlayer p) {
        return read(data(p), "shoes");
    }

    public static void setShoes(ServerPlayer p, SurfaceContamination c) {
        write(data(p), "shoes", c);
    }

    // --- мир ---

    public static SurfaceContamination surface(ServerLevel level, BlockPos pos) {
        return ContaminationMap.of(level).get(pos);
    }

    public static void setSurface(ServerLevel level, BlockPos pos, SurfaceContamination c) {
        ContaminationMap.of(level).set(pos, c);
    }

    public static void deposit(ServerLevel level, BlockPos pos, SurfaceContamination c) {
        ContaminationMap.of(level).add(pos, c);
    }

    /**
     * Пролив или осаждение активности {@code alphaBq}, {@code betaBq} поровну на верхние грани блоков в круге радиуса
     * {@code radius} вокруг {@code center}: в каждом столбце — верхняя твёрдая грань под открытым над ней местом в пределах
     * ±4 блоков по высоте. Нет ни одной — всё на сам блок {@code center}.
     *
     * @return сколько блоков загрязнено
     */
    public static int spill(ServerLevel level, BlockPos center, int radius, double alphaBq, double betaBq) {
        if (!(alphaBq > 0) && !(betaBq > 0)) return 0;
        java.util.List<BlockPos> floors = new java.util.ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                BlockPos f = floorIn(level, center.offset(dx, 0, dz));
                if (f != null) floors.add(f);
            }
        if (floors.isEmpty()) floors.add(center);
        SurfaceContamination each = SurfaceContamination.spill(Math.max(0, alphaBq), Math.max(0, betaBq),
                floors.size() * ContaminationTransfer.BLOCK_FACE_CM2, SPILL_LOOSE);
        ContaminationMap m = ContaminationMap.of(level);
        for (BlockPos f : floors) m.add(f, each);
        return floors.size();
    }

    /** Верхняя твёрдая грань в столбце около {@code at}, или {@code null}. */
    private static BlockPos floorIn(ServerLevel level, BlockPos at) {
        for (int dy = 4; dy >= -4; dy--) {
            BlockPos p = at.above(dy);
            if (!level.isLoaded(p)) return null;
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) return p;
        }
        return null;
    }

    /** Прошёл {@code metres} по полу {@code floor}: обмен рыхлым загрязнением обуви и пола. */
    public static void walk(ServerLevel level, ServerPlayer p, BlockPos floor, double metres) {
        ContaminationMap m = ContaminationMap.of(level);
        // бахилы или боты пневмокостюма берут пол на себя и пропускают на обувь только свою долю (04b §4b.7.3)
        ItemStack cover = RadGear.footCover(p);
        SurfaceContamination shoes = cover != null ? ContaminationData.get(cover) : shoes(p), f = m.get(floor);
        if (shoes.negligible() && f.negligible()) return;
        ContaminationTransfer.Pair r = ContaminationTransfer.walk(shoes, f, ContaminationTransfer.BLOCK_FACE_CM2, metres);
        if (cover != null) {
            SurfaceContamination leak = r.a().minus(shoes).scaled(1 - RadGear.footRetained(cover));
            ContaminationData.set(cover, r.a().minus(leak));
            setShoes(p, shoes(p).plus(leak));
        } else setShoes(p, r.a());
        m.set(floor, r.b());
    }

    /** Секунда с предметом в руке: обмен рыхлым загрязнением рук и предмета (стопка — площадь всех предметов). */
    public static void handle(ServerPlayer p, ItemStack stack) {
        if (stack.isEmpty()) return;
        // в пневмокостюме предмет держат перчатки куртки: загрязнение ложится на неё
        ItemStack gloves = RadGear.handCover(p);
        SurfaceContamination h = gloves != null ? ContaminationData.get(gloves) : hands(p), it = ContaminationData.get(stack);
        if (h.loose().negligible() && it.loose().negligible()) return;
        ContaminationTransfer.Pair r = ContaminationTransfer.contact(h, ContaminationTransfer.HANDS_CM2, it,
                ContaminationTransfer.ITEM_CM2 * stack.getCount(), GRIP_CM2, ContaminationTransfer.GRIP_K);
        if (gloves != null) {
            SurfaceContamination leak = r.a().minus(h).scaled(1 - dev.denfry.promptcritical.sim.radiation.RadRespirator.PNEUMOSUIT.skinRetained());
            ContaminationData.set(gloves, r.a().minus(leak));
            setHands(p, hands(p).plus(leak));
        } else setHands(p, r.a());
        ContaminationData.set(stack, r.b());
    }

    /**
     * Ожидаемая доза за {@code seconds} от загрязнения пола {@code floor} (вдох, заглатывание) и рук игрока
     * (заглатывание); записывается в учёт доз. Творческий режим — без дозы.
     *
     * @return доза, Зв
     */
    public static double expose(ServerPlayer p, SurfaceContamination floor, double seconds) {
        // пыль с пола вдыхается мимо маски только в доле, которую пропускает фильтр P или аппарат (13 §13.7)
        double inhaled = ContaminationExposure.inhalationSvH(floor) * (dev.denfry.promptcritical.mod.safety.PpeService.aerosolFraction(p) - 1);
        double sv = (ContaminationExposure.committedSvH(floor, hands(p)) + inhaled) * seconds / 3600;
        if (!p.isCreative() && !p.isSpectator()) RadiationService.addCommitted(p, sv);
        return sv;
    }

    /** Секунда жизни игрока в загрязнённом мире: шаги, предмет в руке, доза. */
    public static void tickPlayer(ServerLevel level, ServerPlayer p) {
        if (p.isSpectator()) return;
        CompoundTag d = data(p);
        Vec3 pos = p.position();
        BlockPos floor = p.getOnPos();
        if (d.contains("lx") && p.onGround()) {
            double dx = pos.x - d.getDouble("lx"), dz = pos.z - d.getDouble("lz");
            double metres = Math.sqrt(dx * dx + dz * dz);
            if (metres > 0.05) walk(level, p, floor, metres);
        }
        d.putDouble("lx", pos.x);
        d.putDouble("lz", pos.z);
        handle(p, p.getMainHandItem());
        expose(p, p.onGround() ? surface(level, floor) : SurfaceContamination.NONE, 1);
    }

    /** Помывка в душе на долю {@code fraction} полной помывки: руки и обувь. Возвращает смытую активность, Бк. */
    public static double shower(ServerPlayer p, double fraction) {
        SurfaceContamination h = hands(p), s = shoes(p);
        SurfaceContamination h2 = h.after(DeconMethod.SOAP_SHOWER, fraction), s2 = s.after(DeconMethod.WATER_RINSE, fraction);
        setHands(p, h2);
        setShoes(p, s2);
        return (h.total() - h2.total()) * ContaminationTransfer.HANDS_CM2 + (s.total() - s2.total()) * 2 * ContaminationTransfer.SOLE_CM2;
    }

    private static void tick(ServerLevel level) {
        for (ServerPlayer p : level.players()) tickPlayer(level, p);
        gamma(level);
    }

    /**
     * γ от β-загрязнения пола: по секциям 16³ в источники {@link RadiationService}. Центр масс секции сдвигается, когда
     * загрязнение разносят или смывают, поэтому источник с прошлого места снимается сразу — иначе до истечения
     * удержания секция светила бы дважды.
     */
    private static void gamma(ServerLevel level) {
        ContaminationMap m = ContaminationMap.of(level);
        Map<Long, Vec3> before = GAMMA_AT.getOrDefault(level, Map.of()), now = new HashMap<>();
        Map<Long, double[]> sections = new HashMap<>();
        for (Map.Entry<Long, SurfaceContamination> e : m.all().entrySet()) {
            double bq = e.getValue().beta() * ContaminationTransfer.BLOCK_FACE_CM2;
            BlockPos p = BlockPos.of(e.getKey());
            double[] s = sections.computeIfAbsent(SectionPos.asLong(p), k -> new double[4]);
            s[0] += bq;
            s[1] += bq * (p.getX() + 0.5);
            s[2] += bq * (p.getY() + 1.05);
            s[3] += bq * (p.getZ() + 0.5);
        }
        for (Map.Entry<Long, double[]> e : sections.entrySet()) {
            double[] s = e.getValue();
            if (s[0] < GAMMA_MIN_BQ) continue;
            Vec3 at = new Vec3(s[1] / s[0], s[2] / s[0], s[3] / s[0]);
            RadiationService.source(level, at, Emitter.CS137.source(s[0]));
            now.put(e.getKey(), at);
        }
        before.forEach((section, at) -> {
            if (!at.equals(now.get(section))) RadiationService.removeSource(level, at);
        });
        if (now.isEmpty()) GAMMA_AT.remove(level);
        else GAMMA_AT.put(level, now);
    }

    /** Разбор загрязнённого блока: руки берут своё, остальное — на выпавшие предметы; покрытие уходит с блоком. */
    private static void onDrops(BlockDropsEvent e) {
        ServerLevel level = e.getLevel();
        BlockPos pos = e.getPos();
        ContaminationMap m = ContaminationMap.of(level);
        SurfaceContamination c = m.get(pos);
        m.removeCoating(pos);
        m.setOxidised(pos, false);
        if (c.negligible()) return;
        if (e.getBreaker() instanceof ServerPlayer p) {
            ContaminationTransfer.Pair r = ContaminationTransfer.contact(hands(p), ContaminationTransfer.HANDS_CM2, c,
                    ContaminationTransfer.BLOCK_FACE_CM2, GRIP_CM2, ContaminationTransfer.GRIP_K);
            setHands(p, r.a());
            c = r.b();
        }
        m.set(pos, SurfaceContamination.NONE);
        int items = 0;
        for (ItemEntity it : e.getDrops()) items += it.getItem().getCount();
        if (items == 0) {
            // ничего не выпало (стекло, творческий режим): пыль осыпается на открывшуюся грань блока снизу
            m.add(pos.below(), c);
            return;
        }
        // активность грани сохраняется: делится на поверхность всех выпавших предметов
        double perItemScale = ContaminationTransfer.BLOCK_FACE_CM2 / (items * ContaminationTransfer.ITEM_CM2);
        for (ItemEntity it : e.getDrops()) {
            ItemStack st = it.getItem();
            ContaminationData.set(st, ContaminationData.get(st).plus(c.scaled(perItemScale)));
        }
    }

    /** Пустой рукой с Shift по высохшему покрытию — содрать плёнку вместе с рыхлым загрязнением. */
    private static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != InteractionHand.MAIN_HAND || !e.getEntity().isShiftKeyDown() || !e.getItemStack().isEmpty()) return;
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof ServerPlayer p)) return;
        if (peel(level, e.getPos(), p)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * Содрать покрытие с блока {@code pos}: рыхлое загрязнение под ним уменьшается в КД покрытия раз, снятое уходит в
     * плёнку — твёрдые отходы с той же активностью.
     *
     * @return было ли покрытие на блоке (сухое или нет)
     */
    public static boolean peel(ServerLevel level, BlockPos pos, ServerPlayer p) {
        ContaminationMap m = ContaminationMap.of(level);
        long ready = m.coating(pos);
        if (ready < 0) return false;
        long left = ready - level.getGameTime();
        if (left > 0) {
            p.displayClientMessage(ContaminationText.msg("coat.curing", String.format(Locale.ROOT, "%.1f", left * 3.6 / 3600)), true);
            return true;
        }
        SurfaceContamination before = m.get(pos), after = before.after(DeconMethod.STRIPPABLE_COATING);
        SurfaceContamination removed = before.minus(after);
        m.set(pos, after);
        m.removeCoating(pos);
        ItemStack film = new ItemStack(PcItems.STRIPPED_COATING.get());
        ContaminationData.set(film, removed.scaled(ContaminationTransfer.BLOCK_FACE_CM2 / ContaminationTransfer.ITEM_CM2));
        if (!p.getInventory().add(film)) p.drop(film, false);
        double pct = before.total() > 0 ? 100 * removed.total() / before.total() : 0;
        p.displayClientMessage(ContaminationText.msg("coat.peeled", String.format(Locale.ROOT, "%.0f", pct)), true);
        level.playSound(null, pos, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.BLOCKS, 0.6f, 1.2f);
        return true;
    }
}
