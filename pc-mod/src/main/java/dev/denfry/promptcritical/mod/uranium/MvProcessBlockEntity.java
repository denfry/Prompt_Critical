package dev.denfry.promptcritical.mod.uranium;

import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.grid.GridLoad;
import dev.denfry.promptcritical.mod.grid.GridText;
import dev.denfry.promptcritical.mod.grid.MachinePower;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.GridUnit;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Установка мощнее 200 кВт со своим вводом 6,3 кВ (энергопроект 2, решение владельца 2026-11-23): кабель 6 кВ к любой
 * грани контроллера, потребитель сети заявляет номинал {@link #mvRatedW()}, пока установка включена. Скорость идёт за
 * частотой; ниже 0,85 о.е. дольше 0,5 с выключатель отпадает, установка останавливается и ждёт пуска игроком
 * (Shift + ПКМ), сама не запускается ({@link MachinePower.Mv}).
 */
public abstract class MvProcessBlockEntity extends UraniumProcessBlockEntity implements GridDevice {
    private final MachinePower.Mv mv = new MachinePower.Mv();

    protected MvProcessBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int inSlots, int outSlots,
            java.util.function.Predicate<net.minecraft.world.item.ItemStack> accepts) {
        super(type, pos, state, inSlots, outSlots, accepts);
    }

    /** Номинальная мощность установки от сети 6,3 кВ, Вт. */
    protected abstract double mvRatedW();

    /** Щит 0,4 кВ не нужен: вся мощность — со своего ввода. */
    @Override
    protected final double ratedPowerW() {
        return 0;
    }

    @Override
    protected double powerFactor(ServerLevel s) {
        double f = mv.supply(s, worldPosition, simId(), on ? mvRatedW() : 0);
        if (on && mv.tripped()) {
            on = false;
            setChanged();
        }
        return f;
    }

    /** Пуск снимает отпадание выключателя; останов отключает его. */
    @Override
    protected void onSwitched(boolean nowOn) {
        if (nowOn) mv.start();
        else mv.stop();
    }

    /** Выключатель ввода отпал по нулевой защите. */
    public boolean tripped() {
        return mv.tripped();
    }

    @Override
    @Nullable
    protected Component switchNote() {
        return mv.tripped() ? Component.translatable(GridText.POWER_TRIPPED) : null;
    }

    @Override
    public List<List<Face>> terminals() {
        return List.of(GridDevice.allFaces(worldPosition));
    }

    @Override
    @Nullable
    public GridUnit gridUnit(GridSubsystem grid) {
        return simId().isNone() ? null : grid.consumer(simId());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        GridLoad.register(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        GridLoad.unregister(level, worldPosition);
    }

    @Override
    protected void savePower(CompoundTag tag) {
        mv.save(tag);
    }

    @Override
    protected void loadPower(CompoundTag tag) {
        mv.load(tag);
    }
}
