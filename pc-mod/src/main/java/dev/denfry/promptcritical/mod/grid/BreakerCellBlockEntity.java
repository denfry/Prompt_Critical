package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.grid.BreakerResponse;
import dev.denfry.promptcritical.sim.grid.BreakerState;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.RelaySetting;
import dev.denfry.promptcritical.sim.grid.SwitchUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Ячейка КРУ 6 кВ ({@code switchgear_cell}) или щит 0,4 кВ ({@code lv_switchboard}) — 1×2×1 (07 §7.4.6). У ячейки
 * шина — боковые грани (ряд ячеек = шина), присоединение — задняя грань и верх; у щита ввод — сзади, отходящие —
 * по бокам и сверху. Модули РЗА — в соседнем шкафу {@code relay_cabinet}.
 */
public class BreakerCellBlockEntity extends GridDeviceBlockEntity<SwitchUnit> implements PanelHost {
    private final SwitchUnit.Kind kind;

    public BreakerCellBlockEntity(BlockPos pos, BlockState state, SwitchUnit.Kind kind) {
        super(kind == SwitchUnit.Kind.MV_BREAKER ? GridBlockEntities.SWITCHGEAR_CELL.get() : GridBlockEntities.LV_SWITCHBOARD.get(),
                pos, state);
        this.kind = kind;
    }

    public static BreakerCellBlockEntity mv(BlockPos pos, BlockState state) {
        return new BreakerCellBlockEntity(pos, state, SwitchUnit.Kind.MV_BREAKER);
    }

    public static BreakerCellBlockEntity lv(BlockPos pos, BlockState state) {
        return new BreakerCellBlockEntity(pos, state, SwitchUnit.Kind.LV_BREAKER);
    }

    public SwitchUnit.Kind kind() {
        return kind;
    }

    @Override
    protected SwitchUnit unitOf(GridSubsystem grid) {
        return grid.switchUnit(simId(), kind);
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> bus = new ArrayList<>(), feeder = new ArrayList<>();
        Direction f = facing();
        boolean mv = kind == SwitchUnit.Kind.MV_BREAKER;
        for (int y = 0; y <= 1; y++) {
            GridDevice.faces(mv ? bus : feeder, worldPosition, f, 0, y, 0, Direction.WEST, Direction.EAST);
            GridDevice.faces(mv ? feeder : bus, worldPosition, f, 0, y, 0, Direction.SOUTH);
        }
        GridDevice.faces(feeder, worldPosition, f, 0, 1, 0, Direction.UP);
        return List.of(bus, feeder);
    }

    /** Модули РЗА из шкафов, примыкающих к ячейке. */
    private List<RelaySetting> cabinetRelays() {
        List<RelaySetting> out = new ArrayList<>();
        List<BlockPos> seen = new ArrayList<>();
        for (int y = 0; y <= 1; y++) {
            BlockPos cell = worldPosition.above(y);
            for (Direction d : Direction.values()) {
                RelayCabinetBlockEntity c = RelayCabinetBlockEntity.at(level, cell.relative(d));
                if (c == null || seen.contains(c.getBlockPos())) continue;
                seen.add(c.getBlockPos());
                out.addAll(c.settings());
            }
        }
        return out;
    }

    @Override
    protected void serverTick(SwitchUnit u) {
        if (ticks % 20 == 3) u.setRelays(cabinetRelays());
        if (ticks % 5 == 0) {
            BreakerState s = u.breakerState();
            setFlag(BlockStateProperties.OPEN, !u.closed());
            setFlag(PcProperties.ALARM, s == BreakerState.FAULTED || s == BreakerState.FAILED_TO_INTERRUPT);
        }
    }

    /** Включить или отключить; ответ привода — игроку. */
    public BreakerResponse operate(@Nullable Player player, boolean close) {
        SwitchUnit u = unit();
        if (u == null) return BreakerResponse.UNAVAILABLE;
        BreakerResponse r = u.operate(close);
        if (player != null) player.displayClientMessage(GridText.msg(GridText.BREAKER_RESPONSE, response(r)), true);
        if (r == BreakerResponse.ACCEPTED)
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, close ? 0.6f : 0.8f);
        return r;
    }

    static net.minecraft.network.chat.Component response(BreakerResponse r) {
        return GridText.msg(switch (r) {
            case ACCEPTED -> GridText.ACCEPTED;
            case INTERLOCK -> GridText.INTERLOCK;
            case NO_CONTROL_POWER -> GridText.NO_CONTROL_POWER;
            case BUSY -> GridText.BUSY;
            case UNAVAILABLE -> GridText.UNAVAILABLE;
        });
    }

    @Override
    public InteractionResult use(Player player) {
        SwitchUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        operate(player, !u.closed());
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return kind == SwitchUnit.Kind.MV_BREAKER ? GridPanels.CELL : GridPanels.SWITCHBOARD;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        SwitchUnit u = unit();
        if (u == null) return;
        GridPanels.readSwitch(u, out);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        SwitchUnit u = unit();
        if (u == null) return;
        switch (id) {
            case GridPanels.BUTTON_CLOSE -> operate(player, true);
            case GridPanels.BUTTON_OPEN -> operate(player, false);
            case GridPanels.BUTTON_RATING -> {
                if (kind == SwitchUnit.Kind.LV_BREAKER) u.cycleRating();
            }
            default -> {
            }
        }
    }
}
