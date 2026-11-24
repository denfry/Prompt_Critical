package dev.denfry.promptcritical.mod.content;

import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.worldgen.VeinFields;
import dev.denfry.promptcritical.sim.mining.VeinBody;
import dev.denfry.promptcritical.sim.mining.VeinField;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Геологическая карта (11 §11.4.1): при первом развороте отмечает ближайшее рудное тело или угольный пласт
 * ({@code map_target}), дальше показывает расстояние, румб и отметку центра тела — как карта сокровищ.
 */
public class GeologicalMapItem extends Item {
    /** Поиск — по ячейкам жил в этом радиусе вокруг игрока. */
    public static final int SEARCH_CELLS = 6;
    private static final String[] BEARINGS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    public GeologicalMapItem(Properties props) {
        super(props);
    }

    /** 16 §16.5.1: прочитанная карта в руке ставит путевую точку JourneyMap на центр рудного тела. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (!level.isClientSide || !selected || level.getGameTime() % 20 != 0) return;
        MapTargetData t = stack.get(PcDataComponents.MAP_TARGET.get());
        if (t == null || !(entity instanceof Player p) || !p.isLocalPlayer()) return;
        if (dev.denfry.promptcritical.mod.compat.CompatSwitch.active(
                dev.denfry.promptcritical.mod.config.ServerConfig.COMPAT_JOURNEYMAP, "journeymap"))
            dev.denfry.promptcritical.mod.compat.journeymap.JourneyMapWaypoints.held(t, level.dimension());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(stack);
        MapTargetData t = stack.get(PcDataComponents.MAP_TARGET.get());
        if (t == null) {
            t = nearest(sl, player.blockPosition());
            if (t == null) {
                player.displayClientMessage(Component.translatable(ContentText.MAP_BLANK), true);
                return InteractionResultHolder.consume(stack);
            }
            stack.set(PcDataComponents.MAP_TARGET.get(), t);
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
            // 16 §16.5.1: Xaero получает точку строкой чата один раз — когда карта прочитана
            if (player instanceof net.minecraft.server.level.ServerPlayer sp)
                dev.denfry.promptcritical.mod.compat.XaeroWaypoints.offer(sp, Component.translatable(ContentText.vein(t.vein())).getString(),
                        t.pos(), dev.denfry.promptcritical.mod.compat.XaeroWaypoints.GOLD, level.dimension());
        }
        player.displayClientMessage(describe(t, player.blockPosition()), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        MapTargetData t = stack.get(PcDataComponents.MAP_TARGET.get());
        lines.add(t == null ? Component.translatable(ContentText.MAP_UNREAD).withStyle(ChatFormatting.GRAY)
                : Component.translatable(ContentText.MAP_TARGET, Component.translatable(ContentText.vein(t.vein())),
                        t.pos().getX(), t.pos().getZ()).withStyle(ChatFormatting.GRAY));
    }

    static Component describe(MapTargetData t, BlockPos from) {
        double dx = t.pos().getX() + 0.5 - from.getX(), dz = t.pos().getZ() + 0.5 - from.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        // румб: 0 — север (к −Z), по часовой стрелке
        double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
        String b = BEARINGS[(int) Math.round(deg / 45) % 8];
        return Component.translatable(ContentText.MAP_READING, Component.translatable(ContentText.vein(t.vein())),
                Math.round(dist), Component.translatable(ContentText.BEARING + b), t.pos().getY());
    }

    /** Ближайшее по горизонтали тело в {@link #SEARCH_CELLS} ячейках; {@code null} — жилы выключены или пусто. */
    @Nullable
    public static MapTargetData nearest(ServerLevel level, BlockPos from) {
        VeinField f = VeinFields.get(level.getSeed());
        VeinField.BiomeProbe probe = VeinFields.biomes(level);
        int cx = f.cellOf(from.getX()), cz = f.cellOf(from.getZ());
        VeinBody best = null;
        double bestD = Double.MAX_VALUE;
        for (int r = 0; r <= SEARCH_CELLS; r++) {
            // центр тела кольца r не ближе (r − 1) ячеек: если найденное ближе, дальше искать незачем
            if (best != null && Math.sqrt(bestD) < (r - 1) * (double) f.config().cellBlocks()) break;
            for (int i = -r; i <= r; i++) {
                for (int j = -r; j <= r; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != r) continue;
                    VeinBody b = f.body(cx + i, cz + j, probe);
                    if (b == null) continue;
                    double dx = b.centerX() - from.getX(), dz = b.centerZ() - from.getZ(), d = dx * dx + dz * dz;
                    if (d < bestD) {
                        bestD = d;
                        best = b;
                    }
                }
            }
        }
        return best == null ? null
                : new MapTargetData(BlockPos.containing(best.centerX(), best.centerY(), best.centerZ()), best.type().id());
    }
}
