package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.IslandView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Пульт диспетчера энергосистемы {@code grid_dispatch_center} (07 §7.6.6): видит все острова, шины и ветви сети
 * мира, их частоту, баланс и перетоки, и прогноз нагрузки. Режим он не планирует (P3): план генерации, АРЧМ, резервы
 * строит игрок — на щите и через CC-периферию {@code pc_grid} (§7.17.4). Тревога — остров вне 49,5–50,5 Гц или
 * обесточенный остров с шинами (блэкаут).
 */
public class GridDispatchCenterBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    /** Ошибка прогноза нагрузки, доля (§7.17.4: 3 %). */
    public static final double FORECAST_SIGMA = 0.03;
    /** Полоса нормальной частоты для тревоги пульта, Гц. */
    static final double FREQ_BAND_HZ = 0.5;
    private int ticks;

    public GridDispatchCenterBlockEntity(BlockPos pos, BlockState state) {
        super(GridBlockEntities.GRID_DISPATCH_CENTER.get(), pos, state);
    }

    @Nullable
    private GridSubsystem grid() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        return b == null ? null : b.world().subsystem(GridSubsystem.class);
    }

    /** Остров с наибольшей генерацией — «основная сеть» на щите. */
    @Nullable
    public IslandView mainIsland() {
        GridSubsystem g = grid();
        if (g == null) return null;
        IslandView best = null;
        for (IslandView v : g.islandViews()) if (v.energized() && (best == null || v.generationW() > best.generationW())) best = v;
        return best;
    }

    public boolean alarm() {
        GridSubsystem g = grid();
        if (g == null) return false;
        for (IslandView v : g.islandViews()) {
            if (!v.energized() && v.busCount() > 0) return true;
            if (v.energized() && Math.abs(v.frequencyHz() - g.config().nominalFrequencyHz()) > FREQ_BAND_HZ) return true;
        }
        return false;
    }

    @Override
    public void tickDevice() {
        if (level == null || level.isClientSide || ++ticks % 20 != 0) return;
        BlockState s = getBlockState();
        boolean a = alarm();
        if (s.hasProperty(PcProperties.ALARM) && s.getValue(PcProperties.ALARM) != a)
            level.setBlock(worldPosition, s.setValue(PcProperties.ALARM, a), Block.UPDATE_CLIENTS);
    }

    // ------------------------------------------------------------------ pc_grid (§7.17.4)

    public List<Map<String, Object>> listIslands() {
        List<Map<String, Object>> out = new ArrayList<>();
        GridSubsystem g = grid();
        if (g == null) return out;
        for (IslandView v : g.islandViews()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.id());
            m.put("freq", v.frequencyHz());
            m.put("pGen", v.generationW());
            m.put("pLoad", v.loadW());
            m.put("pLoss", v.lossW());
            m.put("hSys", v.inertiaWs());
            m.put("buses", v.busCount());
            m.put("energized", v.energized());
            out.add(m);
        }
        return out;
    }

    @Nullable
    public Map<String, Object> getIsland(int id) {
        GridSubsystem g = grid();
        if (g == null) return null;
        for (IslandView v : g.islandViews()) {
            if (v.id() != id) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("freq", v.frequencyHz());
            m.put("rocof", v.rocofHzPerS());
            m.put("ace", v.generationW() - v.loadW() - v.lossW());
            m.put("machines", v.machineCount());
            m.put("energized", v.energized());
            return m;
        }
        return null;
    }

    public List<Map<String, Object>> listBuses() {
        List<Map<String, Object>> out = new ArrayList<>();
        GridSubsystem g = grid();
        if (g == null) return out;
        for (GridSubsystem.BusInfo b : g.busInfos()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.id());
            m.put("label", b.label());
            m.put("v", b.voltageV());
            m.put("angle", b.angleRad());
            m.put("freq", b.frequencyHz());
            m.put("island", b.island());
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> listBranches() {
        List<Map<String, Object>> out = new ArrayList<>();
        GridSubsystem g = grid();
        if (g == null) return out;
        for (GridSubsystem.BranchInfo b : g.branchInfos()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.id());
            m.put("from", b.from());
            m.put("to", b.to());
            m.put("p", b.powerW());
            m.put("i", b.currentA());
            m.put("closed", b.closed());
            out.add(m);
        }
        return out;
    }

    /** Прогноз нагрузки на час {@code h}: заявленная мощность потребителей и ошибка 3 % (суточного графика пока нет). */
    public Map<String, Object> getLoadForecast(int h) {
        GridSubsystem g = grid();
        double p = g == null ? 0 : g.contractedLoadW();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hour", Math.floorMod(h, 24));
        m.put("p", p);
        m.put("sigma", p * FORECAST_SIGMA);
        return m;
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return GridDispatchPanels.DISPATCH;
    }

    @Override
    public Component panelTitle() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public boolean panelValid(Player player) {
        return PanelHost.near(this, player);
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        GridSubsystem g = grid();
        if (g == null) return;
        int energized = 0, dead = 0;
        for (IslandView v : g.islandViews()) {
            if (v.energized()) energized++;
            else if (v.busCount() > 0) dead++;
        }
        out[GridDispatchPanels.ISLANDS] = energized;
        out[GridDispatchPanels.DEAD] = dead;
        IslandView m = mainIsland();
        if (m != null) {
            out[GridDispatchPanels.HZ100] = (int) Math.round(m.frequencyHz() * 100);
            out[GridDispatchPanels.GEN_KW] = (int) Math.round(m.generationW() / 1e3);
            out[GridDispatchPanels.LOAD_KW] = (int) Math.round(m.loadW() / 1e3);
            out[GridDispatchPanels.LOSS_KW] = (int) Math.round(m.lossW() / 1e3);
            out[GridDispatchPanels.ROCOF1000] = (int) Math.round(m.rocofHzPerS() * 1000);
        }
        out[GridDispatchPanels.FORECAST_KW] = (int) Math.round(g.contractedLoadW() / 1e3);
        out[GridDispatchPanels.FLAGS] = alarm() ? 1 << GridDispatchPanels.F_ALARM : 0;
    }

    @Override
    public void onPanelButton(net.minecraft.server.level.ServerPlayer player, int id) {
    }

    // ------------------------------------------------------------------ руки

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
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
}
