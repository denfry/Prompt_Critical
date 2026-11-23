package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.sim.radiation.DoseRate;
import dev.denfry.promptcritical.sim.radiation.PortalMonitor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Портальный γ-монитор на выходе из зоны (04b §4b.7.2, санпропускник §4b.12.2): следит за проходом шириной в блок
 * перед колонной. Пока проход пуст, монитор копит фон места; вошедшего игрока он меряет по всему, что тот несёт
 * ({@link RadiationService#carried}), на расстоянии детектора {@value PortalMonitor#DETECTOR_M} м, и сравнивает счёт
 * с порогом фон + 5σ ({@link PortalMonitor}). Две колонны по сторонам одного прохода — пара, как у настоящих рам.
 * Тревога — звонок, сообщение и сигнал редстоуна, пока нарушитель в проходе и ещё {@value #HOLD_TICKS} тиков.
 */
public final class PortalMonitorBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    static final int PERIOD_TICKS = 10, HOLD_TICKS = 40;

    /** Фон места по последнему замеру при пустом проходе, мкЗв/ч. */
    private double backgroundUSvH = RadiationService.BACKGROUND_USV_H;
    private double lastRatio;
    private long alarmUntil = Long.MIN_VALUE;
    /** Кто уже получил вердикт за текущий проход: сообщение одно на проход, а не каждые полсекунды. */
    private final Set<UUID> judged = new HashSet<>();

    public PortalMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(RadiationBlockEntities.PORTAL_MONITOR.get(), pos, state);
    }

    /** Чистый счёт от несомого игроком в долях порога при фоне {@code backgroundUSvH}: больше 1 — тревога. */
    public static double ratio(Player p, double backgroundUSvH, long tick) {
        double uSvH = DoseRate.totalSvH(RadiationService.carried(p, tick), PortalMonitor.DETECTOR_M, List.of()) * 1e6;
        return PortalMonitor.STANDARD.ratio(backgroundUSvH, uSvH);
    }

    /** Ячейка прохода перед лицевой стороной колонны, в рост человека. */
    public AABB gate() {
        Direction f = getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        BlockPos cell = worldPosition.relative(f);
        return new AABB(cell).expandTowards(0, 1, 0);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (now % PERIOD_TICKS != 0) return;
        List<Player> inside = sl.getEntitiesOfClass(Player.class, gate(), p -> !p.isSpectator());
        if (inside.isEmpty()) {
            judged.clear();
            Vec3 c = gate().getCenter();
            backgroundUSvH = RadiationService.BACKGROUND_USV_H + RadiationService.rateSvH(sl, c) * 1e6;
        } else {
            judged.retainAll(inside.stream().map(Player::getUUID).toList());
            for (Player p : inside) pass(p);
        }
        boolean on = now < alarmUntil;
        if (on) sl.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1f, 0.5f);
        setSignal(on);
    }

    /** Замер игрока в проходе: вердикт один раз на проход, тревога держится, пока он внутри. */
    public void pass(Player p) {
        long now = level.getGameTime();
        double r = ratio(p, backgroundUSvH, now);
        lastRatio = r;
        if (r > 1) alarmUntil = now + HOLD_TICKS;
        if (!judged.add(p.getUUID())) return;
        if (r > 1) {
            p.displayClientMessage(ContaminationText.msg("portal.alarm", ContaminationText.num(r)).withStyle(ChatFormatting.RED), false);
        } else {
            p.displayClientMessage(ContaminationText.msg("portal.clean").withStyle(ChatFormatting.GREEN), true);
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.4f, 1.5f);
        }
        setSignal(now < alarmUntil);
    }

    public double backgroundUSvH() {
        return backgroundUSvH;
    }

    public double lastRatio() {
        return lastRatio;
    }

    public boolean alarming() {
        return level != null && level.getGameTime() < alarmUntil;
    }

    private void setSignal(boolean on) {
        BlockState s = getBlockState();
        if (s.hasProperty(BlockStateProperties.POWERED) && s.getValue(BlockStateProperties.POWERED) != on) {
            level.setBlock(worldPosition, s.setValue(BlockStateProperties.POWERED, on), Block.UPDATE_ALL);
            level.updateNeighborsAt(worldPosition, s.getBlock());
        }
    }

    @Override
    public InteractionResult use(Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        double thr = PortalMonitor.STANDARD.thresholdUSvH(backgroundUSvH);
        player.displayClientMessage(ContaminationText.msg("portal.status", ContaminationText.num(backgroundUSvH),
                ContaminationText.num(thr)), false);
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
