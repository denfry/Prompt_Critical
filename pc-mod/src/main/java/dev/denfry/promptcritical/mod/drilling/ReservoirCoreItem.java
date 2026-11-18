package dev.denfry.promptcritical.mod.drilling;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Керн (09 §9.2.6) с компонентом {@code core_data}: интервал, литология, истинные пористость, проницаемость и
 * водонасыщенность залежи в точке, свечение нефти под УФ; после станции анализа — измеренные значения.
 */
public final class ReservoirCoreItem extends Item {
    /**
     * @param topM       кровля интервала керна, м
     * @param sand       песчаник коллектора (иначе глина покрышки)
     * @param porosity   истинная пористость
     * @param permMd     истинная проницаемость, мД
     * @param sw         истинная водонасыщенность
     * @param fluorescent нефть светится под УФ
     * @param analysed   измерен на станции
     * @param mPorosity  измеренная пористость
     * @param mPermMd    измеренная проницаемость, мД
     * @param mSw        измеренная водонасыщенность
     */
    public record Data(float topM, boolean sand, float porosity, float permMd, float sw, boolean fluorescent, boolean analysed,
            float mPorosity, float mPermMd, float mSw) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("top_m").forGetter(Data::topM),
                Codec.BOOL.fieldOf("sand").forGetter(Data::sand),
                Codec.FLOAT.fieldOf("porosity").forGetter(Data::porosity),
                Codec.FLOAT.fieldOf("perm_md").forGetter(Data::permMd),
                Codec.FLOAT.fieldOf("sw").forGetter(Data::sw),
                Codec.BOOL.fieldOf("fluorescent").forGetter(Data::fluorescent),
                Codec.BOOL.fieldOf("analysed").forGetter(Data::analysed),
                Codec.FLOAT.fieldOf("m_porosity").forGetter(Data::mPorosity),
                Codec.FLOAT.fieldOf("m_perm_md").forGetter(Data::mPermMd),
                Codec.FLOAT.fieldOf("m_sw").forGetter(Data::mSw)).apply(i, Data::new));
        public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }

    public ReservoirCoreItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Data d = stack.get(PcDataComponents.CORE_DATA.get());
        if (d == null) return;
        tooltip.add(DrillingText.msg("core_tip", DrillingText.num(d.topM(), 0), DrillingText.num(d.topM() + CoreBarrel.LENGTH_M, 0),
                DrillingText.msg(d.sand() ? "core_sand" : "core_shale")).withStyle(ChatFormatting.GRAY));
        if (d.fluorescent()) tooltip.add(DrillingText.msg("core_uv").withStyle(ChatFormatting.YELLOW));
        if (d.analysed()) tooltip.add(DrillingText.msg("core_measured", DrillingText.num(d.mPorosity(), 3),
                DrillingText.num(d.mPermMd(), 0), DrillingText.num(d.mSw(), 2)).withStyle(ChatFormatting.AQUA));
    }
}
