package dev.denfry.promptcritical.mod.scada;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Удостоверение оператора {@code operator_license} (11 §11.3.5) с компонентом {@code license_spec}: уровень допуска,
 * установка, на которой сдан экзамен, игровой день выдачи, владелец и подпись станции (координаты тренажёра). Выдаёт
 * место инструктора по журналу шкафа тренажёра.
 */
public class OperatorLicenseItem extends Item {
    public enum Level { TRAINEE, OPERATOR, SHIFT_SUPERVISOR }

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);

    public record LicenseSpec(int level, String plant, long day, String holder, String station) {
        public static final Codec<LicenseSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("level").forGetter(LicenseSpec::level),
                Codec.STRING.fieldOf("plant").forGetter(LicenseSpec::plant),
                Codec.LONG.fieldOf("day").forGetter(LicenseSpec::day),
                Codec.STRING.fieldOf("holder").forGetter(LicenseSpec::holder),
                Codec.STRING.fieldOf("station").forGetter(LicenseSpec::station)).apply(i, LicenseSpec::new));
        public static final StreamCodec<ByteBuf, LicenseSpec> STREAM = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LicenseSpec::level, ByteBufCodecs.STRING_UTF8, LicenseSpec::plant,
                ByteBufCodecs.VAR_LONG, LicenseSpec::day, ByteBufCodecs.STRING_UTF8, LicenseSpec::holder,
                ByteBufCodecs.STRING_UTF8, LicenseSpec::station, LicenseSpec::new);

        public Level levelEnum() {
            Level[] v = Level.values();
            return v[Math.max(0, Math.min(v.length - 1, level))];
        }
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LicenseSpec>> LICENSE_SPEC =
            COMPONENTS.registerComponentType("license_spec", b -> b.persistent(LicenseSpec.CODEC).networkSynchronized(LicenseSpec.STREAM));

    static final Tr[] LEVEL_TEXT = {
            PanelText.tr("license.trainee", "Trainee", "Стажёр"),
            PanelText.tr("license.operator", "Operator", "Оператор"),
            PanelText.tr("license.shift_supervisor", "Shift supervisor", "Начальник смены")};
    private static final Tr LINE = PanelText.tr("license.line", "%s · %s · day %s", "%s · %s · день %s"),
            HOLDER = PanelText.tr("license.holder", "Holder %s, station %s", "Владелец %s, станция %s"),
            ALL = PanelText.tr("license.all_plants", "all plants", "все установки");

    public OperatorLicenseItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        LicenseSpec spec = stack.get(LICENSE_SPEC.get());
        if (spec == null) return;
        int plant = dev.denfry.promptcritical.sim.automation.twin.TwinPlants.IDS.indexOf(spec.plant());
        Component plantName = plant >= 0 ? TwinPanels.PLANT_TEXT[plant].get() : ALL.get();
        tooltip.add(LINE.get(LEVEL_TEXT[spec.levelEnum().ordinal()].get(), plantName, spec.day()).withStyle(ChatFormatting.GRAY));
        tooltip.add(HOLDER.get(spec.holder(), spec.station()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
