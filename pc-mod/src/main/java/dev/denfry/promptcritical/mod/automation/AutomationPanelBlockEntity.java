package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок автоматики со щитом настроек ({@link AutomationPanels}): реле, реле времени, искатель, командоаппарат, реле
 * процесса, блок питания, газоанализатор, блоки A2 и станция. Пустая рука — щит, Shift — состояние в строке.
 */
public class AutomationPanelBlockEntity extends AutomationBlockEntity implements PanelHost {
    /** Ручка станции «▼/▲» держится полсекунды на клик. */
    public static final int RAMP_TICKS = 10;

    private int cursor, rampTicks, rampDir, resetTicks;

    public AutomationPanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, AutomationKind kind) {
        super(type, pos, state, kind);
    }

    private AutomationPanels.Spec spec() {
        return AutomationPanels.of(kind);
    }

    @Override
    protected void serverTick(AutomationUnit u) {
        super.serverTick(u);
        if (kind == AutomationKind.SIS_LOGIC) {
            if (resetTicks > 0) resetTicks--;
            u.setInput(AutomationUnit.IN_MAIN, resetTicks > 0 ? 1 : 0);
            setFlag(PcProperties.ALARM, u.sis().tripped() || u.sis().bypass() || !u.sisPowered()
                    || u.sis().proofTestOverdue(u.sisProofIntervalH()));
            // 11 sis_matrix: шкаф — МПС из 10+ причин с голосованием 2oo3 (03d §3d.6.3)
            if (level.getGameTime() % 100 == 0 && level instanceof net.minecraft.server.level.ServerLevel sl) {
                dev.denfry.promptcritical.sim.automation.sis.SisCabinet cab = u.sisCabinet();
                if (cab.matrix()) dev.denfry.promptcritical.mod.content.SimAchievements.nearby(sl, worldPosition,
                        dev.denfry.promptcritical.mod.content.SimAchievements.SIS_MATRIX, "causes", cab.causes());
            }
        }
        if (kind == AutomationKind.MANUAL_STATION) {
            if (rampTicks > 0) rampTicks--;
            u.setInput(AutomationUnit.IN_MAIN, rampTicks > 0 ? rampDir : 0);
        }
    }

    @Override
    public PanelLayout panelLayout() {
        return spec().layout();
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        AutomationUnit u = unit();
        if (u == null) return;
        List<AutomationPanels.Row> rows = spec().rows();
        for (int i = 0; i < rows.size() && i < out.length; i++) out[i] = rows.get(i).read().read(u, cursor);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        AutomationUnit u = unit();
        if (u == null) return;
        List<AutomationPanels.Row> rows = spec().rows();
        if (id >= 0 && id < 2 * rows.size()) {
            AutomationPanels.Adjust a = rows.get(id / 2).adjust();
            if (a != null) a.adjust(u, new AutomationPanels.Cursor() {
                @Override
                public int get() {
                    return cursor;
                }

                @Override
                public void set(int v) {
                    cursor = v;
                }
            }, id % 2 == 0 ? -1 : 1, player.isShiftKeyDown());
        } else {
            switch (id) {
                case AutomationPanels.BUTTON_REPLACE -> {
                    u.replace();
                    player.displayClientMessage(AutomationText.msg(AutomationText.REPLACED), true);
                }
                case AutomationPanels.BUTTON_CALIBRATE -> player.displayClientMessage(AutomationText.msg(u.calibrate()
                        ? AutomationText.CALIBRATED : AutomationText.CALIBRATE_REFUSED), true);
                case AutomationPanels.BUTTON_AUTO -> u.setStationManual(false);
                case AutomationPanels.BUTTON_MAN -> u.setStationManual(true);
                case AutomationPanels.BUTTON_SIS_RESET -> resetTicks = 4;
                case AutomationPanels.BUTTON_SIS_BYPASS -> {
                    if (u.sis() != null) {
                        u.sis().setBypass(!u.sis().bypass());
                        player.displayClientMessage(AutomationText.msg(u.sis().bypass() ? AutomationText.SIS_BYPASS_ON : AutomationText.SIS_BYPASS_OFF,
                                player.getName()), false);
                    }
                }
                case AutomationPanels.BUTTON_SIS_TEST, AutomationPanels.BUTTON_SIS_TEST_CHANNEL, AutomationPanels.BUTTON_SIS_SIGN -> {
                    if (u.sis() != null) proofTest(player, u, id);
                }
                case AutomationPanels.BUTTON_DOWN, AutomationPanels.BUTTON_UP -> {
                    rampDir = id == AutomationPanels.BUTTON_UP ? 1 : -1;
                    rampTicks = RAMP_TICKS;
                }
                default -> {
                }
            }
        }
        sync();
    }

    /** Периодическая проверка функции ПАЗ по шагам 03d §3d.6.8. */
    private static void proofTest(ServerPlayer player, AutomationUnit u, int id) {
        dev.denfry.promptcritical.sim.automation.sis.SisLogic sis = u.sis();
        dev.denfry.promptcritical.sim.automation.sis.SisLogic.Voting v = u.sisVoting();
        dev.denfry.promptcritical.sim.automation.sis.SisLogic.ProofResult r;
        switch (id) {
            case AutomationPanels.BUTTON_SIS_TEST -> {
                if (sis.testing()) {
                    sis.abortProofTest();
                    player.displayClientMessage(AutomationText.msg(AutomationText.SIS_PROOF_ABORTED), true);
                    return;
                }
                r = sis.startProofTest();
                if (r == dev.denfry.promptcritical.sim.automation.sis.SisLogic.ProofResult.OK) {
                    player.displayClientMessage(AutomationText.msg(AutomationText.SIS_PROOF_STARTED), false);
                    return;
                }
            }
            case AutomationPanels.BUTTON_SIS_TEST_CHANNEL -> {
                int ch = 0;
                while (ch < v.n && (sis.testedMask() & 1 << ch) != 0) ch++;
                if (ch >= v.n) ch = 0;
                r = sis.testChannel(ch, v);
                if (r == dev.denfry.promptcritical.sim.automation.sis.SisLogic.ProofResult.OK) {
                    player.displayClientMessage(AutomationText.msg(AutomationText.SIS_PROOF_CHANNEL_OK, ch + 1), true);
                    return;
                }
                if (r == dev.denfry.promptcritical.sim.automation.sis.SisLogic.ProofResult.CHANNEL_FAILED) {
                    player.displayClientMessage(AutomationText.msg(AutomationText.SIS_PROOF_CHANNEL_FAILED, ch + 1)
                            .withStyle(net.minecraft.ChatFormatting.GOLD), false);
                    return;
                }
            }
            default -> {
                r = sis.signProofTest(v, u.sisProofIntervalH());
                if (r == dev.denfry.promptcritical.sim.automation.sis.SisLogic.ProofResult.PASSED_ON_TIME) {
                    player.displayClientMessage(AutomationText.msg(AutomationText.SIS_PROOF_ON_TIME, player.getName())
                            .withStyle(net.minecraft.ChatFormatting.GREEN), false);
                    dev.denfry.promptcritical.mod.content.SimAchievements.direct(player,
                            dev.denfry.promptcritical.mod.content.SimAchievements.PROOF_TEST, "", 1);
                    return;
                }
                if (r == dev.denfry.promptcritical.sim.automation.sis.SisLogic.ProofResult.PASSED_LATE) {
                    player.displayClientMessage(AutomationText.msg(AutomationText.SIS_PROOF_LATE, player.getName()), false);
                    return;
                }
            }
        }
        player.displayClientMessage(AutomationText.msg(switch (r) {
            case NEEDS_BYPASS -> AutomationText.SIS_PROOF_NEEDS_BYPASS;
            case BYPASS_STILL_ON -> AutomationText.SIS_PROOF_BYPASS_ON;
            case NOT_TESTING -> AutomationText.SIS_PROOF_NOT_TESTING;
            case CHANNELS_UNTESTED, LOGIC_NOT_SEEN -> AutomationText.SIS_PROOF_UNTESTED;
            default -> AutomationText.SIS_PROOF_FAILED;
        }).withStyle(net.minecraft.ChatFormatting.GOLD), true);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("cursor", cursor);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cursor = tag.getInt("cursor");
    }
}
