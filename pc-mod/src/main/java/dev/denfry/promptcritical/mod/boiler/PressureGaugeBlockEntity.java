package dev.denfry.promptcritical.mod.boiler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Манометр: сервер читает избыточное давление объекта, на котором висит прибор ({@link PressureSource}), с
 * инерцией трубки Бурдона (τ ≈ 0,5 с) и отправляет клиенту при изменении больше 0,25 % шкалы; клиент плавно
 * доводит стрелку.
 */
public class PressureGaugeBlockEntity extends BlockEntity {
    /** Шкала, Па (избыточное). */
    public static final double FULL_SCALE_PA = 1.6e6;
    /** Угол шкалы, рад: ноль внизу слева, полная шкала внизу справа. */
    public static final float SWEEP = (float) Math.toRadians(270);
    private static final double TAU_S = 0.5;

    private double readingPa;
    private float sentPa = -1;
    /** Клиент: показываемое значение, Па. */
    private float shownPa, prevShownPa;

    public PressureGaugeBlockEntity(BlockPos pos, BlockState state) {
        super(BoilerBlockEntities.PRESSURE_GAUGE.get(), pos, state);
    }

    public double readingPa() {
        return readingPa;
    }

    /** Доля шкалы для рендера с поправкой на долю тика. */
    public float needleFraction(float partialTick) {
        double p = prevShownPa + (shownPa - prevShownPa) * partialTick;
        return (float) Math.max(0, Math.min(1.04, p / FULL_SCALE_PA));
    }

    public void tick() {
        if (level.isClientSide) {
            prevShownPa = shownPa;
            shownPa += (float) ((readingPa - shownPa) * Math.min(1, 0.05 / 0.15));
            return;
        }
        BlockPos c = BoilerGeometry.controllerOf(level, PressureGaugeBlock.mountPos(getBlockState(), worldPosition));
        double target = c != null && level.getBlockEntity(c) instanceof PressureSource src ? src.gaugePressurePa() : 0;
        if (!Double.isFinite(target)) target = 0;
        if (!Double.isFinite(readingPa)) readingPa = 0;
        readingPa += (target - readingPa) * Math.min(1, 0.05 / TAU_S);
        if (Math.abs(readingPa - sentPa) > 0.0025 * FULL_SCALE_PA) {
            sentPa = (float) readingPa;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("reading_pa", readingPa);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readingPa = tag.getDouble("reading_pa");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("reading_pa", readingPa);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
