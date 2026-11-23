package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.PowerBoard;
import dev.denfry.promptcritical.sim.grid.PowerBoardUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Розетка ({@code power_socket} 16 А, {@code power_socket_3p} 32 А), силовой щит {@code power_board} или ИБП
 * {@code ups_cabinet} — питание машин от сети 0,4 кВ (см. {@link MachinePower}). Ввод — кабель к стене за розеткой
 * или щитом (сверху и снизу тоже можно), у ИБП — к любой грани, кроме фасада. Группа — машины в зоне
 * {@link MachinePower#range}, для которых этот щит ближайший.
 *
 * <p>ПКМ — «Пуск» или «Стоп» контактора. Shift + ПКМ — состояние группы; у щита ещё и следующий номинал вводного
 * автомата.
 */
public class PowerBoardBlockEntity extends GridDeviceBlockEntity<PowerBoardUnit> {
    /** Машина выпадает из группы, если не заявляла мощность столько тиков. */
    static final int STALE_TICKS = 60;
    private final PowerBoard.Kind kind;
    /** Заявки машин: мощность, Вт, и тик заявки. */
    private final Map<BlockPos, double[]> machines = new HashMap<>();
    private double factor;

    public PowerBoardBlockEntity(PowerBoard.Kind kind, BlockPos pos, BlockState state) {
        super(type(kind), pos, state);
        this.kind = kind;
    }

    private static net.minecraft.world.level.block.entity.BlockEntityType<?> type(PowerBoard.Kind kind) {
        return switch (kind) {
            case SOCKET -> GridBlockEntities.POWER_SOCKET.get();
            case SOCKET_3P -> GridBlockEntities.POWER_SOCKET_3P.get();
            case BOARD -> GridBlockEntities.POWER_BOARD.get();
            case UPS -> GridBlockEntities.UPS_CABINET.get();
        };
    }

    public static PowerBoardBlockEntity socket(BlockPos pos, BlockState state) {
        return new PowerBoardBlockEntity(PowerBoard.Kind.SOCKET, pos, state);
    }

    public static PowerBoardBlockEntity socket3p(BlockPos pos, BlockState state) {
        return new PowerBoardBlockEntity(PowerBoard.Kind.SOCKET_3P, pos, state);
    }

    public static PowerBoardBlockEntity board(BlockPos pos, BlockState state) {
        return new PowerBoardBlockEntity(PowerBoard.Kind.BOARD, pos, state);
    }

    public static PowerBoardBlockEntity ups(BlockPos pos, BlockState state) {
        return new PowerBoardBlockEntity(PowerBoard.Kind.UPS, pos, state);
    }

    public PowerBoard.Kind kind() {
        return kind;
    }

    @Override
    protected PowerBoardUnit unitOf(GridSubsystem grid) {
        PowerBoardUnit u = grid.powerBoard(simId());
        u.configure(kind);
        return u;
    }

    @Override
    public List<List<Face>> terminals() {
        Direction front = facing();
        List<Face> faces = new ArrayList<>();
        if (kind == PowerBoard.Kind.UPS) {
            // шкаф 1 × 2 × 1: выводы на всех гранях, кроме фасада; верх — у верхней клетки. Шкаф, поставленный до
            // 1 × 2 × 1, верхней части не имеет, и над ним мог лечь чужой кабель — тогда вывод сверху остаётся у контроллера
            boolean tall = ownPartAbove();
            for (Direction d : Direction.values()) if (d != front && !(tall && d == Direction.UP)) faces.add(new Face(worldPosition, d));
            if (tall) for (Direction d : Direction.values())
                if (d != front && d != Direction.DOWN) faces.add(new Face(worldPosition.above(), d));
        } else {
            faces.add(new Face(worldPosition, front.getOpposite()));
            faces.add(new Face(worldPosition, Direction.UP));
            faces.add(new Face(worldPosition, Direction.DOWN));
        }
        return List.of(faces);
    }

    /** Над контроллером — своя часть, а не чужой блок (шкаф ИБП, поставленный до 1 × 2 × 1, её не имеет). */
    private boolean ownPartAbove() {
        if (level == null) return false;
        BlockPos above = worldPosition.above();
        BlockState s = level.getBlockState(above);
        return s.getBlock() instanceof dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock
                && dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock.anchor(s, above).equals(worldPosition);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) MachinePower.add(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) MachinePower.remove(level, worldPosition);
    }

    /** Машина заявляет мощность; ответ — доля, которую получает группа. */
    double feed(BlockPos machine, double watts, long now) {
        machines.put(machine.immutable(), new double[] {watts, now});
        return unit() == null ? 0 : factor;
    }

    /** Сумма свежих заявок, Вт. */
    double demandW(long now) {
        double sum = 0;
        for (Iterator<double[]> it = machines.values().iterator(); it.hasNext(); ) {
            double[] m = it.next();
            if (now - (long) m[1] > STALE_TICKS) it.remove();
            else sum += m[0];
        }
        return sum;
    }

    /** Машин в группе. */
    public int machineCount() {
        return machines.size();
    }

    /** Доля мощности, которую получает группа, 0…1. */
    public double supplyFactor() {
        return unit() == null ? 0 : factor;
    }

    @Override
    public void tickDevice() {
        super.tickDevice();
        if (level != null && !level.isClientSide && unit() == null && factor > 0) {
            factor = 0; // устройство выпало из сети
            setFlag(BlockStateProperties.LIT, false);
        }
    }

    @Override
    protected void serverTick(PowerBoardUnit u) {
        PowerBoard b = u.board();
        if (b == null) return;
        factor = b.supplyFactor();
        if (ticks % 20 != 7) return;
        b.setDemandW(demandW(level.getGameTime()));
        setFlag(BlockStateProperties.LIT, b.on() && (factor > 0 || b.demandW() == 0 && u.inputPu() >= PowerBoard.UV_TRIP_PU));
        if (kind == PowerBoard.Kind.UPS) setFlag(PcProperties.ALARM, b.onBattery());
    }

    @Override
    public InteractionResult use(Player player) {
        PowerBoardUnit u = unit();
        PowerBoard b = u == null ? null : u.board();
        if (b == null) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (kind == PowerBoard.Kind.BOARD) {
                b.cycleRating();
                player.displayClientMessage(GridText.msg(GridText.POWER_RATING, Math.round(b.ratedW() / 1e3)), false);
            }
            String tail = b.tripped() ? net.minecraft.network.chat.Component.translatable(GridText.POWER_TRIPPED).getString() : "";
            if (kind == PowerBoard.Kind.UPS) {
                String on = b.onBattery() ? net.minecraft.network.chat.Component.translatable(GridText.POWER_ON_BATTERY).getString() : "";
                tail = net.minecraft.network.chat.Component.translatable(GridText.POWER_BATTERY, Math.round(b.batterySoc() * 100), on).getString();
            }
            player.displayClientMessage(GridText.msg(GridText.POWER_STATUS, machines.size(), String.format("%.1f", b.demandW() / 1e3),
                    String.format("%.1f", b.ratedW() / 1e3), Math.round(factor * 100), Math.round(u.inputPu() * 400), tail), true);
            return InteractionResult.SUCCESS;
        }
        boolean start = !b.on();
        if (start) b.start();
        else b.stop();
        player.displayClientMessage(GridText.msg(start ? GridText.POWER_START : GridText.POWER_STOP), true);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, start ? 1.3f : 1.0f);
        return InteractionResult.SUCCESS;
    }
}
