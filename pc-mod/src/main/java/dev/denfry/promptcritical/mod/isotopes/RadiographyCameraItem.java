package dev.denfry.promptcritical.mod.isotopes;

import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.steelworks.SteelworksComponents;
import dev.denfry.promptcritical.sim.isotopes.Radiography;
import dev.denfry.promptcritical.sim.metallurgy.DefectKind;
import dev.denfry.promptcritical.sim.metallurgy.PipeSpec;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Гамма-дефектоскоп {@code radiography_camera} (12 §12.2.3): контейнер из обеднённого урана с источником Ir-192 (или
 * Co-60 для толстых стенок). Shift + ПКМ с закрытым источником во второй руке — зарядить, с пустой второй рукой —
 * разрядить. ПКМ и удержание с трубой во второй руке и плёнкой в инвентаре — рукоятка выдвигает источник к шву: идёт
 * экспозиция, и вместе с плёнкой облучается дефектоскопист (источник без защиты в полуметре). Отпустить —
 * источник возвращается в контейнер, плёнка проявляется: недодержка и передержка бесполезны, годная плёнка показывает
 * дефекты, видимые радиографией (поры, непровар, трещины, раковины), а труба получает отметку контроля RT.
 */
public final class RadiographyCameraItem extends Item {
    /** Расстояние источник — плёнка (через одну стенку), м. */
    public static final double SOURCE_TO_FILM_M = 0.5;
    /** Источник у шва в стольких метрах перед глазами дефектоскописта. */
    public static final double SOURCE_FROM_EYE_M = 0.6;

    public RadiographyCameraItem(Properties props) {
        super(props);
    }

    public static boolean isRadiographySource(ItemStack s) {
        if (!s.is(PcItems.SEALED_SOURCE.get())) return false;
        ActivityData a = s.get(ActivityData.ACTIVITY.get());
        return a != null && (a.bq().containsKey(Emitter.IR192.name()) || a.bq().containsKey(Emitter.CO60.name()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack cam = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(cam);
        ItemStack off = player.getOffhandItem();
        ActivityData src = cam.get(IsotopeComponents.SHIELDED_SOURCE.get());
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) return InteractionResultHolder.success(cam);
            if (src == null && isRadiographySource(off)) {
                ActivityData a = off.get(ActivityData.ACTIVITY.get());
                cam.set(IsotopeComponents.SHIELDED_SOURCE.get(), a);
                off.shrink(1);
                player.displayClientMessage(IsotopeText.msg("camera_loaded", activityText(a, level.getGameTime())), true);
            } else if (src != null && off.isEmpty()) {
                ItemStack s = new ItemStack(PcItems.SEALED_SOURCE.get());
                s.set(ActivityData.ACTIVITY.get(), src);
                cam.remove(IsotopeComponents.SHIELDED_SOURCE.get());
                player.setItemInHand(InteractionHand.OFF_HAND, s);
                player.displayClientMessage(IsotopeText.msg("camera_unloaded"), true);
            }
            return InteractionResultHolder.success(cam);
        }
        if (src == null) {
            if (!level.isClientSide) player.displayClientMessage(IsotopeText.msg("camera_empty"), true);
            return InteractionResultHolder.fail(cam);
        }
        if (off.get(SteelworksComponents.PIPE_SPEC.get()) == null || findFilm(player) < 0) {
            if (!level.isClientSide) player.displayClientMessage(IsotopeText.msg("camera_needs"), true);
            return InteractionResultHolder.fail(cam);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(cam);
    }

    private static int findFilm(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(PcItems.RADIOGRAPHY_FILM.get())) return i;
        return -1;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    /** Нужная экспозиция при этой трубе и источнике, тиков; бесконечность — нельзя. */
    static double neededTicks(PointSource src, @Nullable PipeSpec pipe) {
        double wallCm = pipe == null ? 1 : pipe.wallM() * 100;
        return Radiography.exposureS(src, SOURCE_TO_FILM_M, wallCm, IsotopeComponents.filmExposureGy()) / ActivityData.GAME_S_PER_TICK;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack cam, int remaining) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof ServerPlayer p)) return;
        ActivityData a = cam.get(IsotopeComponents.SHIELDED_SOURCE.get());
        if (a == null) {
            p.stopUsingItem();
            return;
        }
        PointSource src = a.inventory(sl.getGameTime()).source();
        int used = getUseDuration(cam, entity) - remaining;
        // источник выдвинут: открытый, у шва перед дефектоскопистом. Точка привязана к центру блока: служба излучения
        // ведёт источники по координате, и дрожащий взгляд не должен плодить копии одного источника
        if (used % 10 == 0) {
            Vec3 at = Vec3.atCenterOf(net.minecraft.core.BlockPos.containing(p.getEyePosition().add(p.getLookAngle().scale(SOURCE_FROM_EYE_M))));
            RadiationService.source(sl, at, src);
        }
        if (used % 5 == 0) {
            var pipe = p.getOffhandItem().get(SteelworksComponents.PIPE_SPEC.get());
            double need = neededTicks(src, pipe == null ? null : pipe.spec());
            double pct = need > 0 && Double.isFinite(need) ? used / need * 100 : 0;
            p.displayClientMessage(IsotopeText.msg("camera_exposure", Math.round(pct),
                    String.format(Locale.ROOT, "%.0f", need * ActivityData.GAME_S_PER_TICK)), true);
        }
    }

    @Override
    public void releaseUsing(ItemStack cam, Level level, LivingEntity entity, int timeLeft) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof ServerPlayer p)) return;
        ActivityData a = cam.get(IsotopeComponents.SHIELDED_SOURCE.get());
        ItemStack pipeStack = p.getOffhandItem();
        var pipe = pipeStack.get(SteelworksComponents.PIPE_SPEC.get());
        int filmSlot = findFilm(p);
        if (a == null || pipe == null || filmSlot < 0) return;
        PipeSpec spec = pipe.spec();
        PointSource src = a.inventory(sl.getGameTime()).source();
        int used = getUseDuration(cam, entity) - timeLeft;
        double gotGy = Radiography.kermaRateGyS(src, SOURCE_TO_FILM_M, spec.wallM() * 100) * used * ActivityData.GAME_S_PER_TICK;
        Radiography.Film film = Radiography.film(gotGy, IsotopeComponents.filmExposureGy());
        p.getInventory().getItem(filmSlot).shrink(1);
        DefectKind defect = film == Radiography.Film.GOOD && spec.defect().present() && spec.defect().kind().radiography ? spec.defect().kind()
                : DefectKind.NONE;
        ItemStack exposed = new ItemStack(PcItems.RADIOGRAPHY_FILM_EXPOSED.get());
        exposed.set(IsotopeComponents.FILM_RESULT.get(), new IsotopeComponents.FilmResult(film.ordinal(), defect.ordinal(), spec.grade()));
        if (!p.addItem(exposed)) p.drop(exposed, false);
        if (film == Radiography.Film.GOOD) {
            pipeStack.set(SteelworksComponents.PIPE_SPEC.get(), SteelworksComponents.PipeData.of(spec.inspected(PipeSpec.RT)));
            p.displayClientMessage(IsotopeText.msg("film_good", filmText(defect)), false);
        } else {
            p.displayClientMessage(IsotopeText.msg(film == Radiography.Film.UNDEREXPOSED ? "film_under" : "film_over"), true);
        }
    }

    static Component filmText(DefectKind d) {
        return d == DefectKind.NONE ? IsotopeText.msg("film_clean").withStyle(ChatFormatting.GREEN)
                : IsotopeText.msg("film_defect", IsotopeText.defect(d.name())).withStyle(ChatFormatting.RED);
    }

    static Component activityText(ActivityData a, long tick) {
        var inv = a.inventory(tick);
        return Component.literal(String.format(Locale.ROOT, "%.2f TBq", inv.totalBq() / 1e12));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        if (!stack.has(IsotopeComponents.SHIELDED_SOURCE.get())) lines.add(IsotopeText.msg("camera_empty").withStyle(ChatFormatting.DARK_GRAY));
    }
}
