package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.sim.environment.GasCleaning;
import dev.denfry.promptcritical.sim.grid.ConsumerUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Электрофильтр {@code electrostatic_precipitator} (14 §14.2.2): поле с осадительными электродами
 * {@value #AREA_M2} м², Дойч–Андерсон по расходу газа топки; уголь условно сернистый (f_УЭС = 1). Потребитель сети:
 * 720 Дж на м³ газа. Без напряжения не очищает, а котёл работает дальше — отказ экологический.
 */
public final class ElectrostaticPrecipitatorBlockEntity extends GridDeviceBlockEntity<ConsumerUnit> implements FlueGasCleaner {
    public static final double AREA_M2 = 5000, MIN_DEMAND_KW = 5;
    private double lastEta, lastFlow;

    public ElectrostaticPrecipitatorBlockEntity(BlockPos pos, BlockState state) {
        super(EnvironmentBlockEntities.ELECTROSTATIC_PRECIPITATOR.get(), pos, state);
    }

    @Override
    protected ConsumerUnit unitOf(GridSubsystem grid) {
        return grid.consumer(simId());
    }

    @Override
    public List<List<GridDevice.Face>> terminals() {
        return List.of(GridDevice.allFaces(worldPosition));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) FlueGasCleaner.add(sl, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel sl) FlueGasCleaner.remove(sl, worldPosition);
    }

    @Override
    protected void serverTick(ConsumerUnit u) {
        if (ticks % 20 == 0) {
            u.setDemandKw((int) Math.ceil(Math.max(MIN_DEMAND_KW, GasCleaning.espPowerW(lastFlow) / 1e3)));
            setFlag(BlockStateProperties.POWERED, u.energized());
        }
    }

    @Override
    public void clean(double[] q, double flowM3S, double dtS) {
        ConsumerUnit u = unit();
        lastFlow = flowM3S;
        lastEta = u != null && u.energized() ? GasCleaning.esp(AREA_M2, flowM3S, 1.5, 1) : 0;
        q[2] *= 1 - lastEta;
    }

    /** Shift + ПКМ: степень очистки. */
    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(Component.literal(String.format(Locale.ROOT, "η = %.4f · Q = %.1f m³/s", lastEta, lastFlow)), true);
        return InteractionResult.SUCCESS;
    }
}
