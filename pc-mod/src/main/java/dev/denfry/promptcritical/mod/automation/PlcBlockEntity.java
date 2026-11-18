package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.automation.plc.PlcCpu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ПЛК (A3): блок с клеммами модулей ввода-вывода и встроенным программатором. Пустая рука — окно программатора
 * (текст программы на IL, ошибки, ключ RUN/STOP, онлайн-индикация входов и выходов). Лампа RUN на модели —
 * свойство {@code running} блока.
 */
public class PlcBlockEntity extends AutomationBlockEntity {
    public PlcBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, AutomationKind.PLC);
    }

    /** Языки МЭК, на которых этот ПЛК уже работал в RUN (достижение {@code all_languages}, 11 §11.2.2). */
    private final java.util.EnumSet<dev.denfry.promptcritical.sim.automation.plc.PlcLanguage> languages =
            java.util.EnumSet.noneOf(dev.denfry.promptcritical.sim.automation.plc.PlcLanguage.class);

    @Override
    protected void serverTick(AutomationUnit u) {
        super.serverTick(u);
        boolean run = u.plc() != null && u.plc().mode() == PlcCpu.Mode.RUN;
        setFlag(PcProperties.RUNNING, run);
        if (!run) return;
        dev.denfry.promptcritical.mod.content.SimAchievements.once(this, dev.denfry.promptcritical.mod.content.SimAchievements.PLC_RUN, "", 1);
        if (languages.add(dev.denfry.promptcritical.sim.automation.plc.PlcLanguage.of(u.plc().program().source()))) {
            setChanged();
            var four = java.util.EnumSet.of(dev.denfry.promptcritical.sim.automation.plc.PlcLanguage.LD,
                    dev.denfry.promptcritical.sim.automation.plc.PlcLanguage.FBD, dev.denfry.promptcritical.sim.automation.plc.PlcLanguage.SFC,
                    dev.denfry.promptcritical.sim.automation.plc.PlcLanguage.ST);
            four.retainAll(languages);
            dev.denfry.promptcritical.mod.content.SimAchievements.once(this, dev.denfry.promptcritical.mod.content.SimAchievements.PLC_LANGUAGES, "languages_" + four.size(), four.size());
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        StringBuilder b = new StringBuilder();
        for (var l : languages) b.append(b.isEmpty() ? "" : ",").append(l.name());
        tag.putString("languages", b.toString());
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        languages.clear();
        for (String s : tag.getString("languages").split(",")) {
            try {
                if (!s.isEmpty()) languages.add(dev.denfry.promptcritical.sim.automation.plc.PlcLanguage.valueOf(s));
            } catch (IllegalArgumentException ignored) {
                // язык из будущей версии — пропустить
            }
        }
    }

    @Override
    public InteractionResult use(Player player) {
        AutomationUnit u = unit();
        if (u == null || u.plc() == null) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) PlcNet.open(sp, worldPosition, u);
        return InteractionResult.SUCCESS;
    }
}
