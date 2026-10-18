package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.mining.MineSump;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Насос водоотлива ЦНС {@code mine_drainage_pump} (08 §8.2.9) на валу Create (вал слева). Рабочая точка — на
 * статическом напоре от насоса до поверхности над ним: H(Q, n) = 1,25H_ном(n/n_ном)² − kQ² ({@link MineSump}); 100 м³/ч
 * на 150 м при 64 об/мин, N = ρgQH/η = 58 кВт. Подачу насосов шахты складывает {@link MineWorld}, водосборник —
 * домен; откачанная вода ({@code mine_water} у сернистых пластов, иначе вода) уходит в трубу на верхней грани, без
 * трубы — по ставу на поверхность [допущение].
 */
public final class MineDrainagePumpBlockEntity extends KineticMachineBlockEntity<ShaftInput> implements PanelHost {
    public static final double NOMINAL_RPM = 64;
    public static final MineSump.PumpSpec PUMP = new MineSump.PumpSpec(100.0 / 3600, 150, 0.7, NOMINAL_RPM);
    public static final double RATED_W = MineSump.pumpPowerW(PUMP.ratedFlowM3s(), PUMP.ratedHeadM(), PUMP.efficiency());
    private static final double IDLE_SHARE = 0.02;

    private final ShaftInput shaft = new ShaftInput();
    private long tick;
    private double headM, pumpedM3s, pendingMb;
    private boolean acidic;

    public MineDrainagePumpBlockEntity(BlockPos pos, BlockState state) {
        super(MineBlockEntities.MINE_DRAINAGE_PUMP.get(), pos, state);
    }

    /** Напор: от насоса до поверхности над ним, м. */
    public double headM() {
        if (level == null) return 0;
        return Math.max(1, level.getHeight(Heightmap.Types.WORLD_SURFACE, worldPosition.getX(), worldPosition.getZ()) - worldPosition.getY());
    }

    /** Подача на своём напоре при скорости вала на тике мира {@code simTick}, м³/с. */
    public double capacityM3s(long simTick) {
        headM = headM();
        return PUMP.flowAt(shaft.rpm(simTick), headM);
    }

    /** Сколько насос качал на последнем шаге водоотлива и какую воду. */
    public void setPumped(double m3s, boolean acidic) {
        this.pumpedM3s = Math.max(0, m3s);
        this.acidic = acidic;
    }

    @Override
    protected ShaftInput unit(SimLevelBinding binding) {
        return shaft;
    }

    @Override
    protected ShaftInput shaft(ShaftInput u) {
        return u;
    }

    @Override
    protected double powerW(ShaftInput u) {
        double idle = u.rpm(tick) > 0 ? IDLE_SHARE * RATED_W : 0;
        return idle + (pumpedM3s > 0 ? MineSump.pumpPowerW(pumpedM3s, headM, PUMP.efficiency()) : 0);
    }

    @Override
    protected double ratedW() {
        return RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return NOMINAL_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding binding, ShaftInput u) {
        tick = binding.world().clock().tick();
        if (u.rpm(tick) <= 0) pumpedM3s = 0;
        setStateFlag(PcProperties.RUNNING, pumpedM3s > 0);
        pendingMb += pumpedM3s * 1000 / 20;                 // м³/с → мВ за тик (ведро 1000 мВ = 1 м³)
        int mb = (int) pendingMb;
        if (mb <= 0) return;
        pendingMb -= mb;
        IFluidHandler out = level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition.above(), Direction.DOWN);
        if (out == null) return;
        FluidStack water = new FluidStack(acidic ? PcFluids.MINE_WATER.source.get() : Fluids.WATER, mb);
        out.fill(water, IFluidHandler.FluidAction.EXECUTE);
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

    @Override
    public PanelLayout panelLayout() {
        return MinePanels.DRAINAGE;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        double rpm = shaft.rpm(tick);
        out[MinePanels.SHAFT_RPM10] = (int) Math.round(rpm * 10);
        out[MinePanels.PUMP_FLOW_M3H10] = (int) Math.round(pumpedM3s * 3600 * 10);
        out[MinePanels.PUMP_HEAD_M] = (int) Math.round(headM());
        out[MinePanels.PUMP_KW10] = (int) Math.round(powerW(shaft) / 100);
        out[MinePanels.FLAGS] = (pumpedM3s > 0 ? 1 << MinePanels.F_RUNNING : 0) | (acidic ? 1 << MinePanels.F_ACID : 0);
    }

    @Override
    public InteractionResult use(Player player) {
        if (level == null || level.isClientSide) return InteractionResult.SUCCESS;
        player.displayClientMessage(HoistText.msg("pump", HoistText.num(pumpedM3s * 3600), HoistText.num(headM()),
                HoistText.num(powerW(shaft) / 1000)), true);
        return InteractionResult.CONSUME;
    }
}
