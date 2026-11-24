package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Буровой насос — трёхпоршневой (триплекс) одностороннего действия, втулки 6½″, ход 12″: подача за ход
 * 3·π/4·d²·L·η_v = 0,0190 м³ при объёмном КПД 0,97 (Bourgoyne et al., §4.2). Номинал 120 ходов в минуту при 64
 * об/мин вала Create; ходы задаёт пульт бурильщика, не больше, чем позволяет вал. Мощность на валу — гидравлическая
 * мощность p·q, деленная на механический КПД 0,85, её считает скважина и раздаёт насосам пульт.
 * <p>
 * Тот же BE служит насосу реального масштаба {@code mud_pump_real} (дуплекс У8-6М по арту): насос в симуляции один и
 * тот же, а щит реального — на фоне oilfield_panels.author.js, с уровнем в приёмных ёмкостях от пульта бурильщика.
 */
public final class MudPumpBlockEntity extends DeviceBlockEntity implements ShaftConsumer, FoundryDevice, PanelHost,
        dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity {
    public static final double NOMINAL_RPM = 64, MAX_SPM = 120;
    public static final double M3_PER_STROKE = 3 * Math.PI / 4 * Math.pow(6.5 * 0.0254, 2) * 12 * 0.0254 * 0.97;
    /** Номинальная мощность: 120 ходов при 25 МПа, КПД 0,85. */
    public static final double RATED_W = MAX_SPM / 60 * M3_PER_STROKE * 25e6 / 0.85;
    /** Без нагрузки вал крутит привод и коленчатый вал: 2 % номинала. */
    private static final double IDLE_SHARE = 0.02;

    private final ShaftInput shaft = new ShaftInput();
    private double demandSpm, spm, powerW, dischargePa;

    public MudPumpBlockEntity(BlockPos pos, BlockState state) {
        super(DrillingBlockEntities.MUD_PUMP.get(), pos, state);
    }

    private long tick() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        return b == null ? 0 : b.world().clock().tick();
    }

    /** Наибольшие ходы при текущей скорости вала. */
    public double availableSpm() {
        return Math.min(MAX_SPM, shaft.rpm(tick()) * MAX_SPM / NOMINAL_RPM);
    }

    /** Пульт задаёт ходы; возвращает фактические, ход/мин. */
    public double drive(double setSpm) {
        demandSpm = Math.max(0, setSpm);
        spm = Math.min(demandSpm, availableSpm());
        return spm;
    }

    /** Пульт сообщает давление нагнетания и долю гидравлической мощности насоса. */
    public void load(double pressurePa, double hydraulicW) {
        dischargePa = Math.max(0, pressurePa);
        powerW = Math.max(0, hydraulicW);
    }

    public double spm() {
        return spm;
    }

    public double flowM3s() {
        return spm / 60 * M3_PER_STROKE;
    }

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        shaft.sample(rpm, healthy, tick);
    }

    @Override
    public double shaftPowerW() {
        return shaft.rpm(tick()) > 0 ? IDLE_SHARE * RATED_W + powerW : 0;
    }

    @Override
    public double ratedShaftW() {
        return RATED_W;
    }

    @Override
    public double nominalShaftRpm() {
        return NOMINAL_RPM;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel)) {
            clientTick();
            return;
        }
        if (availableSpm() < spm) spm = availableSpm();
        if (spm <= 0) powerW = 0;
        BlockState st = getBlockState();
        boolean running = spm > 0;
        if (st.getValue(PcProperties.RUNNING) != running && level.getGameTime() % 10 == 0)
            level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, running), Block.UPDATE_ALL);
    }

    private void clientTick() {
        BlockState st = getBlockState();
        if (!st.getValue(PcProperties.RUNNING)) return;
        MachineFx fx = MachineFx.of(level, worldPosition, st.getValue(BlockStateProperties.HORIZONTAL_FACING));
        // реальный насос подтекает у сальников штоков (по арту: штоки на y 18, z 2 и 14, сальники у x 10)
        if (fx != null && fx.chance(2)) fx.leak(real() ? fx.at(10, 18, level.random.nextBoolean() ? 2 : 14) : fx.at(20, 14, 12));
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public PanelLayout panelLayout() {
        return real() ? DrillingPanels.PUMP_REAL : DrillingPanels.PUMP;
    }

    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing(), real() ? 2 : 1);
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[DrillingPanels.P_SHAFT_RPM10] = (int) Math.round(shaft.rpm(tick()) * 10);
        out[DrillingPanels.P_SPM] = (int) Math.round(spm);
        out[DrillingPanels.P_SPP_X100] = (int) Math.round(dischargePa / 1e4);
        out[DrillingPanels.P_KW] = (int) Math.round(shaftPowerW() / 1000);
        out[DrillingPanels.P_FLAGS] = spm > 0 ? 1 << DrillingPanels.PF_RUNNING : 0;
        if (out.length > DrillingPanels.P_PIT_PERMIL) {
            DrillersConsoleBlockEntity c = DrillersConsoleBlockEntity.near(level, worldPosition);
            dev.denfry.promptcritical.sim.drilling.Well w = c == null ? null : c.well();
            if (w != null && w.pitCapacityM3() > 0) out[DrillingPanels.P_PIT_PERMIL] = (int) Math.round(1000 * w.pitVolumeM3() / w.pitCapacityM3());
        }
    }
}
