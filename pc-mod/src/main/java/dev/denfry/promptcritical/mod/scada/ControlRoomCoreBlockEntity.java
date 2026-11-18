package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.scada.ControlRoomHabitability;
import dev.denfry.promptcritical.sim.scada.ControlRoomHabitability.Cause;
import dev.denfry.promptcritical.sim.scada.ControlRoomHabitability.Result;
import dev.denfry.promptcritical.sim.scada.ControlRoomHabitability.State;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Ядро помещения щита управления {@code control_room_core} (03c §3c.9.1): заливкой находит замкнутый объём воздуха
 * вокруг себя (не больше {@value #MAX_VOLUME} блоков, {@code scada.mcr_max_volume}: зал 16×16×16) и раз в секунду
 * оценивает обитаемость ({@link ControlRoomHabitability}): мощность дозы — среднее по 9 точкам объёма, дым — блоки огня в
 * объёме и на его границе. Температуры воздуха помещений в модели мира нет, питание приборов не отслеживается — эти два
 * параметра 03c не учитываются (не сделано).
 *
 * <p>На щите ядра — жёлтая лампа «ухудшено» и красная «необитаемо»: табло ({@link AnnunciatorBlockEntity}) в 16 блоках
 * само заводит по ним сигнализации приоритета 3 и 2. Объём пересчитывается раз в {@value #RESCAN_S} с: игрок
 * достраивает зал.
 */
public class ControlRoomCoreBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int MAX_VOLUME = 4096, SAMPLES = 9, RESCAN_S = 30;

    private long[] volume = new long[0];
    private boolean enclosed;
    private int ticks, fires;
    private double doseSvS;
    private Result result = new Result(State.NORMAL, Cause.NONE);

    public ControlRoomCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ScadaBlockEntities.CONTROL_ROOM_CORE.get(), pos, state);
    }

    public boolean enclosed() {
        return enclosed;
    }

    public int volume() {
        return volume.length;
    }

    public Result habitability() {
        return result;
    }

    public double doseSvPerS() {
        return doseSvS;
    }

    public int fires() {
        return fires;
    }

    /** Блок внутри объёма помещения. */
    public boolean contains(BlockPos p) {
        long k = p.asLong();
        for (long v : volume) if (v == k) return true;
        return false;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        if (ticks++ % (RESCAN_S * 20) == 0) flood();
        if (ticks % 20 != 0) return;
        if (!enclosed) {
            result = new Result(State.NORMAL, Cause.NONE);
            return;
        }
        double sum = 0;
        for (int k = 0; k < SAMPLES; k++) {
            BlockPos p = BlockPos.of(volume[(int) ((long) k * volume.length / SAMPLES)]);
            sum += RadiationService.rateSvH(sl, p.getCenter()) + RadiationService.cloudSvH(sl, p.getCenter());
        }
        doseSvS = sum / SAMPLES / 3600;
        LongOpenHashSet counted = new LongOpenHashSet();
        int f = 0;
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (long v : volume) {
            BlockPos p = BlockPos.of(v);
            if (level.getBlockState(p).is(BlockTags.FIRE) && counted.add(v)) f++;
            for (Direction d : Direction.values()) {
                n.setWithOffset(p, d);
                if (counted.contains(n.asLong())) continue;
                if (level.isLoaded(n) && level.getBlockState(n).is(BlockTags.FIRE)) {
                    counted.add(n.asLong());
                    f++;
                }
            }
        }
        fires = f;
        result = ControlRoomHabitability.assess(doseSvS, Double.NaN, fires, Double.NaN);
    }

    /** Заливка воздуха от граней ядра; больше {@link #MAX_VOLUME} — помещение не замкнуто. */
    void flood() {
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayList out = new LongArrayList();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction d : Direction.values()) {
            BlockPos s = worldPosition.relative(d);
            if (air(s) && seen.add(s.asLong())) queue.add(s);
        }
        boolean open = false;
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            out.add(p.asLong());
            if (out.size() > MAX_VOLUME) {
                open = true;
                break;
            }
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (!seen.contains(q.asLong()) && air(q)) {
                    seen.add(q.asLong());
                    queue.add(q);
                }
            }
        }
        enclosed = !open && !out.isEmpty();
        volume = enclosed ? out.toLongArray() : new long[0];
    }

    private boolean air(BlockPos p) {
        if (!level.isLoaded(p)) return false;
        BlockState s = level.getBlockState(p);
        return s.isAir() || s.is(BlockTags.FIRE);
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return ControlRoomPanels.CORE;
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
        out[ControlRoomPanels.C_VOLUME] = enclosed ? volume.length : -1;
        out[ControlRoomPanels.C_DOSE_NSV_H] = (int) Math.min(Integer.MAX_VALUE, Math.round(doseSvS * 3600 * 1e9));
        out[ControlRoomPanels.C_FIRES] = fires;
        out[ControlRoomPanels.C_STATE] = result.state().ordinal();
        out[ControlRoomPanels.C_CAUSE] = result.cause().ordinal();
        int flags = 0;
        if (result.state() == State.DEGRADED) flags |= 1 << ControlRoomPanels.CF_DEGRADED;
        if (result.state() == State.UNINHABITABLE) flags |= 1 << ControlRoomPanels.CF_UNINHABITABLE;
        out[ControlRoomPanels.C_FLAGS] = flags;
    }

    // ------------------------------------------------------------------ руки

    @Override
    public InteractionResult use(Player player) {
        flood();
        player.displayClientMessage(ControlRoomPanels.RESCANNED.get(enclosed ? volume.length : 0), true);
        return InteractionResult.SUCCESS;
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
