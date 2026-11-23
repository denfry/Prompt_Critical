package dev.denfry.promptcritical.mod.compat.create;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import dev.denfry.promptcritical.mod.compat.CompatText;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Источник Display Link «показание прибора»: одна строка с выбранной шкалы щита и единицей (16 §16.2.1). */
public final class PanelReadingDisplaySource extends SingleLineDisplaySource {
    /** Ключ настройки источника: номер прибора в раскладке щита. */
    static final String GAUGE = "Gauge";

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        return PanelReadings.reading(context.getSourceBlockEntity(), context.sourceConfig().getInt(GAUGE));
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
        super.initConfigurationWidgets(context, builder, isFirstLine);
        if (isFirstLine) return;
        List<Component> names = PanelReadings.names(context.getSourceBlockEntity());
        if (names.isEmpty()) return;
        builder.addSelectionScrollInput(0, 137, (input, label) -> input.forOptions(names)
                .titled(Component.translatable(CompatText.key("display.gauge"))), GAUGE);
    }
}
