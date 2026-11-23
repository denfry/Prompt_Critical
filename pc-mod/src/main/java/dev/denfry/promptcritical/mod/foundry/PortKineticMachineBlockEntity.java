package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Машина на валу Create, которая стоит и в старой серии (вал — в контроллер), и в реальном масштабе (цельная модель,
 * вал — в клетку-порт {@link PortKineticMachineBlock}). Один BE на оба блока: у реального обороты и исправность сети
 * приходят с порта ({@link ShaftConsumer}), нагрузку на сеть объявляет порт.
 */
public abstract class PortKineticMachineBlockEntity<U> extends KineticMachineBlockEntity<U> implements ShaftConsumer, WholeModelHost {
    private double portRpm;
    private boolean portHealthy = true;

    protected PortKineticMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Машина реального масштаба: цельная модель и вал через порт. */
    public boolean real() {
        return wholeModelId() != null;
    }

    /**
     * Склад реальной машины открыт только с фасада контроллера (место станочника), старой — с любой грани;
     * {@code null} — свой доступ (щит, рука).
     */
    protected boolean itemSideOpen(@Nullable Direction side) {
        return side == null || !real() || side == wholeModelFacing();
    }

    /** В симуляции реальный станок — тот же станок, что и старый: id блока без {@code _real}. */
    @Override
    public String deviceType() {
        String t = super.deviceType();
        return real() && t.endsWith("_real") ? t.substring(0, t.length() - "_real".length()) : t;
    }

    @Override
    protected double shaftSpeed() {
        return real() ? portRpm : super.shaftSpeed();
    }

    @Override
    protected boolean shaftHealthy() {
        return real() ? portHealthy : super.shaftHealthy();
    }

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        portRpm = rpm;
        portHealthy = healthy;
    }

    @Override
    public double shaftPowerW() {
        U u = unit();
        return u == null ? 0 : powerW(u);
    }

    @Override
    public double ratedShaftW() {
        return ratedW();
    }

    @Override
    public double nominalShaftRpm() {
        return nominalRpm();
    }

    @Override
    @Nullable
    public String wholeModelId() {
        return WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        BlockState s = getBlockState();
        return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    /** Свет — с клетки перед станком (место станочника). */
    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing());
    }
}
