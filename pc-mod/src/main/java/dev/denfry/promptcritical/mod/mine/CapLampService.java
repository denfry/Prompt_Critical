package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.item.CapLampData;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.sim.battery.CapLamp;
import dev.denfry.promptcritical.sim.mining.IgnitionSource;
import dev.denfry.promptcritical.sim.mining.MineNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Головной светильник в мире: батарея по игровым часам, свет у головы игрока, разбитое стекло.
 *
 * <p>Время батареи — игровое: сутки Minecraft — 24 ч (как у погоды слоя environment), один тик — 3,6 с, и
 * десятичасовая смена проходит за 10 000 тиков (≈ 8 минут). Надетая лампа пересчитывается раз в секунду, лежащая —
 * при следующем касании за всё прошедшее время (саморазряд).
 *
 * <p>Свет: блок {@link CapLampLightBlock} у глаз игрока (или у ног, если у глаз нет воздуха) с уровнем по потоку
 * фары. Яркость воспринимается логарифмически: свежая лампа — 13 (факел 14), вдвое меньше потока — на 4 ниже, пятая
 * часть — около 4 [BALANCE]. Сервис помнит один блок на игрока и снимает его при уходе, смене измерения, смерти и
 * остановке сервера; бесхозный блок исчезает сам.
 *
 * <p>Стекло фары бьётся при падении с уроном от {@link #FALL_DAMAGE_BREAKS} (≈ 7 блоков), при взрыве и под
 * обрушенной породой [BALANCE]. Горевшая лампа в этот миг — источник воспламенения {@link IgnitionSource#LAMP} в
 * узле выработки у головы; взорвётся ли смесь, решает методика метанового поля шахты.
 */
public final class CapLampService {
    /** Игровых секунд в тике: 86 400 с / 24 000 тиков. */
    public static final double SECONDS_PER_TICK = 86_400.0 / 24_000;
    static final int UPDATE_TICKS = 20;
    /** Наибольший шаг модели батареи, игровых секунд. */
    static final double MAX_STEP_S = 72;
    /** Температура воздуха в выработках и ламповой, °C [умеренная шахта]. */
    static final double AMBIENT_C = 15;
    static final float FALL_DAMAGE_BREAKS = 4;
    static final int FRESH_LEVEL = 13;

    private record Light(ResourceKey<Level> dim, BlockPos pos, int level) {
    }

    private static final Map<UUID, Light> LIGHTS = new HashMap<>();

    private CapLampService() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CapLampService::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(CapLampService::onDamage);
        NeoForge.EVENT_BUS.addListener(CapLampService::onDeath);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> clear(e.getEntity().getUUID(), e.getEntity().level()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> clear(e.getEntity().getUUID(), e.getEntity().level()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            for (Map.Entry<UUID, Light> l : Map.copyOf(LIGHTS).entrySet()) {
                ServerLevel level = e.getServer().getLevel(l.getValue().dim());
                if (level != null) clear(l.getKey(), level);
            }
            LIGHTS.clear();
        });
    }

    // ---------------------------------------------------------------- батарея

    /**
     * Пересчитать светильник за время с прошлого касания: горит, если надет.
     *
     * @return новое состояние
     */
    public static CapLampData update(ItemStack stack, ServerLevel level, boolean lit) {
        CapLampData d = CapLampItem.data(stack);
        long now = level.getGameTime();
        if (d.lastTick() < 0 || now <= d.lastTick()) {
            if (d.lastTick() >= 0) return d;
            CapLampData stamped = new CapLampData(d.removedAh(), d.cycles(), d.tempC(), d.flags(), now, lit ? 1000 : 0);
            stack.set(PcDataComponents.CAP_LAMP.get(), stamped);
            return stamped;
        }
        CapLamp lamp = d.toLamp();
        double left = (now - d.lastTick()) * SECONDS_PER_TICK;
        if (!lit) {
            lamp.operate(left, false, AMBIENT_C);
        } else {
            while (left > 0) {
                double dt = Math.min(MAX_STEP_S, left);
                lamp.operate(dt, true, AMBIENT_C);
                left -= dt;
            }
        }
        CapLampData next = CapLampData.of(lamp, now);
        stack.set(PcDataComponents.CAP_LAMP.get(), next);
        return next;
    }

    /** Уровень света блока у головы по потоку фары (‰ номинального); 0 — темно. */
    public static int lightLevel(int fluxPermil) {
        if (fluxPermil <= 0) return 0;
        double l = FRESH_LEVEL + 4 * Math.log(fluxPermil / 1000.0) / Math.log(2);
        return (int) Math.max(0, Math.min(15, Math.round(l)));
    }

    // ---------------------------------------------------------------- свет у головы

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) tickLight(p);
    }

    /** Поставить, передвинуть или снять свет фары игрока (каждый тик игрока). */
    public static void tickLight(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level)) return;
        ItemStack head = p.isSpectator() ? ItemStack.EMPTY : p.getItemBySlot(EquipmentSlot.HEAD);
        updateLight(level, p.getUUID(), BlockPos.containing(p.getEyePosition()), p.blockPosition(), head);
    }

    /**
     * Свет фары носителя {@code owner}: блок у глаз, иначе у ног, с уровнем по потоку надетого светильника; без
     * светильника или без света — снять.
     */
    public static void updateLight(ServerLevel level, UUID owner, BlockPos eye, BlockPos feet, ItemStack head) {
        int lvl = 0;
        if (head.getItem() instanceof CapLampItem) {
            CapLampData d = CapLampItem.data(head);
            if (d.lastTick() < 0) d = update(head, level, true);
            lvl = lightLevel(d.fluxPermil());
        }
        Light old = LIGHTS.get(owner);
        if (lvl <= 0) {
            if (old != null) clear(owner, level);
            return;
        }
        BlockPos target = placeable(level, eye);
        if (target == null) target = placeable(level, feet);
        if (old != null && old.dim() == level.dimension() && old.pos().equals(target) && old.level() == lvl) return;
        if (old != null) clear(owner, level);
        if (target == null) return;
        LIGHTS.put(owner, new Light(level.dimension(), target, lvl));
        level.setBlock(target, PcBlocks.CAP_LAMP_LIGHT.get().defaultBlockState().setValue(CapLampLightBlock.LEVEL, lvl), 3);
    }

    @Nullable
    private static BlockPos placeable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        BlockState s = level.getBlockState(pos);
        return s.isAir() || s.is(PcBlocks.CAP_LAMP_LIGHT.get()) ? pos.immutable() : null;
    }

    /** Блок света в этой точке — чей-то действующий. */
    static boolean owns(ServerLevel level, BlockPos pos) {
        for (Light l : LIGHTS.values()) if (l.dim() == level.dimension() && l.pos().equals(pos)) return true;
        return false;
    }

    /** Свет носителя (для испытаний): позиция блока или {@code null}. */
    @Nullable
    public static BlockPos lightOf(UUID owner) {
        Light l = LIGHTS.get(owner);
        return l == null ? null : l.pos();
    }

    private static void clear(UUID player, Level level) {
        Light l = LIGHTS.remove(player);
        if (l == null || !(level instanceof ServerLevel sl)) return;
        ServerLevel at = sl.dimension() == l.dim() ? sl : sl.getServer().getLevel(l.dim());
        if (at != null && at.isLoaded(l.pos()) && at.getBlockState(l.pos()).is(PcBlocks.CAP_LAMP_LIGHT.get()) && !owns(at, l.pos()))
            at.removeBlock(l.pos(), false);
    }

    private static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) clear(p.getUUID(), p.level());
    }

    // ---------------------------------------------------------------- удар

    private static void onDamage(LivingDamageEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) return;
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof CapLampItem) || !breaks(e.getSource(), e.getNewDamage())) return;
        breakGlass(level, p, head);
    }

    /**
     * Разбить стекло надетой лампы; горевшая нить — источник воспламенения.
     *
     * @return {@code true} — смесь у головы взорвалась
     */
    public static boolean breakGlass(ServerLevel level, ServerPlayer p, ItemStack head) {
        CapLampData d = update(head, level, true);
        if (d.glassBroken()) return false;
        CapLamp lamp = d.toLamp();
        boolean exposed = lamp.breakGlass(d.fluxPermil() > 0);
        head.set(PcDataComponents.CAP_LAMP.get(), CapLampData.of(lamp, level.getGameTime()));
        level.playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
        p.displayClientMessage(Component.translatable("promptcritical.mine.cap_lamp.broken").withStyle(ChatFormatting.RED), true);
        return exposed && ignite(level, p);
    }

    static boolean breaks(DamageSource source, float damage) {
        if (source.is(DamageTypeTags.IS_FALL)) return damage >= FALL_DAMAGE_BREAKS;
        return source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_ANVIL)
                || source.is(DamageTypes.FALLING_STALACTITE);
    }

    /** Раскалённая нить в атмосфере выработки у головы игрока. */
    static boolean ignite(ServerLevel level, ServerPlayer p) {
        SimLevelBinding b = SimBindings.get(level);
        if (b == null) return false;
        BlockPos eye = BlockPos.containing(p.getEyePosition());
        MineNetwork n = b.mines().mineAt(eye);
        int node = n == null ? -1 : n.nodeOf(eye.asLong());
        return node > 0 && n.ignite(node, IgnitionSource.LAMP);
    }
}
