package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.sim.metallurgy.BatchFurnaceUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Методическая печь реального масштаба {@code reheating_furnace_real} (08 §8.7.2,
 * docs/research/reheating_furnace-dimensions.md): толкательная печь сортового стана на 12 заготовок, 3 × 2 × 3 блока,
 * тот же {@link BatchFurnaceUnit} и та же выдача, что у старой печи. Садка — с юга, через загрузочное окно (клетка
 * (0, 0, 1)) или с рольганга позади; газ — к горелке на восточной стене (1, 0, 0); горячая заготовка выходит через
 * окно фасада на рольганг перед печью. Заслонки окон поднимаются на время выдачи и посадки (только картинка).
 */
public final class ReheatingFurnaceRealBlockEntity extends ReheatingFurnaceBlockEntity implements WholeModelHost {
    /** Сколько тиков заслонка стоит поднятой после выдачи или посадки. */
    public static final int DOOR_OPEN_TICKS = 40;
    /** Окно выдачи на фасаде, px модели: середина проёма над подом. */
    private static final double[] MOUTH = {8, 14, -1};

    private long dischargedAt = Long.MIN_VALUE / 2, chargedAt = Long.MIN_VALUE / 2;

    public ReheatingFurnaceRealBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.REHEATING_FURNACE_REAL.get(), pos, state);
    }

    @Override
    protected void discharge(BatchFurnaceUnit u, long now) {
        int before = occupied();
        super.discharge(u, now);
        if (occupied() < before) {
            dischargedAt = now;
            level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 0.7f);
            syncClient();
        }
    }

    @Override
    protected boolean load(BatchFurnaceUnit u, ItemStack piece) {
        if (!super.load(u, piece)) return false;
        chargedAt = level.getGameTime();
        syncClient();
        return true;
    }

    private int occupied() {
        int n = 0;
        for (int i = 0; i < places.getSlots(); i++) if (!places.getStackInSlot(i).isEmpty()) n++;
        return n;
    }

    /** Подъём заслонки 0…1: быстро вверх, постоять, вниз. */
    private float lift(long at, float pt) {
        if (level == null) return 0;
        double t = level.getGameTime() + pt - at;
        if (t < 0 || t > DOOR_OPEN_TICKS) return 0;
        return (float) Math.min(1, Math.min(t, DOOR_OPEN_TICKS - t) / 8);
    }

    /** Подъём заслонки окна выдачи для рендера (клиент). */
    public float dischargeDoorLift(float pt) {
        return lift(dischargedAt, pt);
    }

    /** Подъём заслонки загрузочного окна для рендера (клиент). */
    public float chargeDoorLift(float pt) {
        return lift(chargedAt, pt);
    }

    /** Языки пламени из окна выдачи, пока заслонка поднята, и дым над сводом. */
    @Override
    protected void clientTick() {
        super.clientTick();
        if (!stateFlag(BlockStateProperties.LIT) || dischargeDoorLift(0) <= 0) return;
        var fx = fx();
        if (fx.chance(1)) fx.flame(fx.at(MOUTH[0], MOUTH[1], MOUTH[2]), 0.2);
    }

    @Override
    protected void writeClient(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeClient(tag, registries);
        tag.putLong("dischargedAt", dischargedAt);
        tag.putLong("chargedAt", chargedAt);
    }

    @Override
    protected void readClient(CompoundTag tag, HolderLookup.Provider registries) {
        super.readClient(tag, registries);
        if (tag.contains("dischargedAt")) dischargedAt = tag.getLong("dischargedAt");
        if (tag.contains("chargedAt")) chargedAt = tag.getLong("chargedAt");
    }

    /** В симуляции — та же печь, что и старый блок: иначе подсистема не создаст BatchFurnaceUnit. */
    @Override
    public String deviceType() {
        return dev.denfry.promptcritical.sim.metallurgy.IronmakingSubsystem.REHEATING_FURNACE;
    }

    @Override
    @Nullable
    public String wholeModelId() {
        return WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    /** Свет — с клетки перед окном выдачи: контроллер внутри кладки. */
    @Override
    public BlockPos wholeModelLightPos() {
        return Steelworks.world(worldPosition, wholeModelFacing(), 0, 0, -2);
    }
}
