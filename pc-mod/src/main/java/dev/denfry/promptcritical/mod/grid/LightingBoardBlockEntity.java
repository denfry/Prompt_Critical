package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.LightingBoardUnit;
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
 * Щиток освещения {@code lighting_board}: ввод 0,4 кВ — кабель к стене за щитком (сверху и снизу тоже можно), группа —
 * светильники в радиусе {@value LightingBoards#RANGE} блоков, для которых щиток ближайший. ПКМ — групповой автомат,
 * Shift + ПКМ — сколько светильников и сколько они берут.
 */
public class LightingBoardBlockEntity extends GridDeviceBlockEntity<LightingBoardUnit> {
    /** Светильник выпадает из группы, если не отзывался столько тиков. */
    static final int STALE_TICKS = 60;
    private final Map<BlockPos, long[]> lamps = new HashMap<>();
    private boolean lit;

    public LightingBoardBlockEntity(BlockPos pos, BlockState state) {
        super(GridBlockEntities.LIGHTING_BOARD.get(), pos, state);
    }

    @Override
    protected LightingBoardUnit unitOf(GridSubsystem grid) {
        return grid.lightingBoard(simId());
    }

    @Override
    public List<List<Face>> terminals() {
        Direction back = facing().getOpposite();
        return List.of(List.of(new Face(worldPosition, back), new Face(worldPosition, Direction.UP),
                new Face(worldPosition, Direction.DOWN)));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LightingBoards.add(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) LightingBoards.remove(level, worldPosition);
    }

    /** Светильник отзывается щитку: его мощность входит в группу; ответ — горят ли лампы группы. */
    boolean feed(BlockPos lamp, int watts, long now) {
        lamps.put(lamp.immutable(), new long[] {watts, now});
        return lit;
    }

    /** Установленная мощность отозвавшихся светильников, Вт. */
    long demandW(long now) {
        long sum = 0;
        for (Iterator<long[]> it = lamps.values().iterator(); it.hasNext(); ) {
            long[] l = it.next();
            if (now - l[1] > STALE_TICKS) it.remove();
            else sum += l[0];
        }
        return sum;
    }

    @Override
    public void tickDevice() {
        super.tickDevice();
        if (level != null && !level.isClientSide && unit() == null && lit) {
            lit = false; // устройство выпало из сети: группа гаснет
            setFlag(BlockStateProperties.LIT, false);
        }
    }

    @Override
    protected void serverTick(LightingBoardUnit u) {
        if (ticks % 20 != 3) return;
        u.setDemandW(demandW(level.getGameTime()));
        lit = u.lampsLit();
        setFlag(BlockStateProperties.LIT, lit);
    }

    @Override
    public InteractionResult use(Player player) {
        LightingBoardUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            player.displayClientMessage(GridText.msg(GridText.LIGHTING_STATUS, lamps.size(),
                    String.format("%.1f", u.demandW() / 1e3), Math.round(u.inputPu() * 400)), true);
            return InteractionResult.SUCCESS;
        }
        boolean on = !u.switchedOn();
        u.setSwitchedOn(on);
        player.displayClientMessage(GridText.msg(on ? GridText.LIGHTING_ON : GridText.LIGHTING_OFF), true);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, on ? 1.3f : 1.0f);
        return InteractionResult.SUCCESS;
    }
}
