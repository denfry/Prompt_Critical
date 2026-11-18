package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.content.ContentText;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Сейсмический разрез: результат съёмки сейсмостанцией ({@link SeismicRecorderBlockEntity}). Как геологическая карта
 * I0, показывает расстояние и румб до свода структуры, а в подсказке — время, глубину, амплитуду, яркое пятно и
 * прогноз пластового давления. Бурить надо на свод: там коллектор выше всего, а залежь — под ним.
 */
public class SeismicSectionItem extends Item {
    private static final String[] BEARINGS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    public SeismicSectionItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        SeismicSectionData d = stack.get(PcDataComponents.SEISMIC_SECTION.get());
        player.displayClientMessage(d == null ? DrillingText.msg("section_blank") : describe(d, player.blockPosition()), true);
        return InteractionResultHolder.consume(stack);
    }

    static Component describe(SeismicSectionData d, BlockPos from) {
        double dx = d.crest().getX() + 0.5 - from.getX(), dz = d.crest().getZ() + 0.5 - from.getZ();
        double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
        String b = BEARINGS[(int) Math.round(deg / 45) % 8];
        return DrillingText.msg("section", Math.round(Math.hypot(dx, dz)), Component.translatable(ContentText.BEARING + b), d.depthM());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        SeismicSectionData d = stack.get(PcDataComponents.SEISMIC_SECTION.get());
        if (d == null) {
            lines.add(DrillingText.msg("section_blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        lines.add(DrillingText.msg("section_tip", d.crest().getX(), d.crest().getZ(), d.twtMs(), d.depthM(), d.closureM())
                .withStyle(ChatFormatting.GRAY));
        lines.add(DrillingText.msg("section_pore", DrillingText.num(d.poreKgM3() / 1000.0, 2)).withStyle(ChatFormatting.GRAY));
        if (d.bright()) lines.add(DrillingText.msg("section_bright").withStyle(ChatFormatting.GOLD));
    }
}
