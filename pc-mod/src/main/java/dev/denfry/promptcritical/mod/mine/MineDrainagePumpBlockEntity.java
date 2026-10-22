package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ops.OpsActions;
import dev.denfry.promptcritical.mod.ops.OpsMachine;
import dev.denfry.promptcritical.mod.ops.OpsText;
import dev.denfry.promptcritical.mod.ops.OpsWorld;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.ops.Baseline;
import dev.denfry.promptcritical.sim.ops.CheckInput;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.UnitKind;
import dev.denfry.promptcritical.sim.mining.MineSump;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
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
 *
 * <p>Эксплуатация ({@link OpsMachine}, 13 §13.6.5–13.6.7): новый насос не залит — без заливки ведром воды на
 * остановленном насосе не качает и греет подшипник всухую; обратное вращение и износ колеса снижают напор
 * ({@link dev.denfry.promptcritical.sim.ops.ImpellerWear#headFactor}); кислая вода ест колесо втрое быстрее;
 * заплавленный подшипник клинит вал.
 */
public final class MineDrainagePumpBlockEntity extends KineticMachineBlockEntity<ShaftInput> implements PanelHost, OpsMachine {
    public static final double NOMINAL_RPM = 64;
    public static final MineSump.PumpSpec PUMP = new MineSump.PumpSpec(100.0 / 3600, 150, 0.7, NOMINAL_RPM);
    public static final double RATED_W = MineSump.pumpPowerW(PUMP.ratedFlowM3s(), PUMP.ratedHeadM(), PUMP.efficiency());
    private static final double IDLE_SHARE = 0.02;
    /** pH откачиваемой воды: кислая шахтная и обычная [BALANCE]. */
    public static final double ACID_PH = 3.5, NEUTRAL_PH = 7.5;

    private final ShaftInput shaft = new ShaftInput();
    private long tick;
    private double headM, pumpedM3s, pendingMb;
    private boolean acidic, seized, dry;

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
        double f = headFactor(simTick);
        return f > 0 ? PUMP.flowAt(shaft.rpm(simTick), headM / f) : 0;
    }

    /** Множитель характеристики H(Q): не залит или заклинил — 0, обратное вращение и износ колеса — меньше 1. */
    private double headFactor(long simTick) {
        OpsSubsystem ops = OpsWorld.ops(level);
        OpsSubsystem.Unit u = ops == null ? null : ops.unit(simId());
        if (u == null) return 1;
        if (u.seized() || !ops.commissioning().primed(simId())) return 0;
        return u.wear().impeller().headFactor(shaft.signedRpm(simTick) < 0);
    }

    /** Скорость вала со знаком направления Create, об/мин. */
    public double shaftSignedRpm() {
        return shaft.signedRpm(tick);
    }

    /** Откачка на последнем шаге водоотлива, м³/с. */
    public double pumpedM3s() {
        return pumpedM3s;
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
        if (seized) return 100 * RATED_W;
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
        operations(u);
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

    private void operations(ShaftInput u) {
        OpsSubsystem ops = OpsWorld.ops(level);
        OpsSubsystem.Unit ou = OpsWorld.ensure(level, simId(), UnitKind.MINE_DRAINAGE_PUMP, 0, false);
        if (ops == null || ou == null) return;
        boolean running = u.rpm(tick) > 0;
        dry = !ops.commissioning().primed(simId());
        ou.setRunning(running, running && dry, acidic ? ACID_PH : NEUTRAL_PH);
        seized = ou.seized();
        ops.observeTrial(simId(), 0.05, running, pumpedM3s / PUMP.ratedFlowM3s(), false, opsCurrent());
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    /** Ведро воды — заливка насоса (13 §13.6.6, {@code PRIMING}). */
    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(Items.WATER_BUCKET);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!stack.is(Items.WATER_BUCKET)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!opsStopped()) {
            player.displayClientMessage(OpsText.msg("prime_stop"), true);
            return ItemInteractionResult.CONSUME;
        }
        OpsActions.perform(player, this, new CheckInput.Priming(true));
        if (!player.getAbilities().instabuild) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        player.displayClientMessage(OpsText.msg("primed"), true);
        level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8f, 1.0f);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected void clientTick() {
        MachineFx fx = fx();
        if (fx == null || !runningState()) return;
        if (fx.chance(1.5)) fx.leak(fx.at(4.5, 5, 8));
        if (fx.chance(1.5)) fx.leak(fx.at(27, 5, 8));
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
        out[MinePanels.FLAGS] = (pumpedM3s > 0 ? 1 << MinePanels.F_RUNNING : 0) | (acidic ? 1 << MinePanels.F_ACID : 0)
                | (dry ? 1 << MinePanels.F_DRY : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == MinePanels.BUTTON_BUMP) MainMineFanBlockEntity.bump(player, this, shaft.signedRpm(tick));
    }

    // --- эксплуатация ---

    @Override
    public UnitKind opsKind() {
        return UnitKind.MINE_DRAINAGE_PUMP;
    }

    @Override
    public boolean opsStopped() {
        return shaft.rpm(tick) <= 0;
    }

    @Override
    public Vec3 opsBearingPos() {
        return Vec3.atCenterOf(worldPosition);
    }

    @Override
    public Baseline opsCurrent() {
        double f = headFactor(tick);
        return new Baseline(Double.NaN, Double.NaN, f > 0 ? PUMP.ratedHeadM() * f : Double.NaN, Double.NaN,
                shaft.rpm(tick) * 2 * Math.PI / 60, Double.NaN);
    }

    @Override
    public InteractionResult use(Player player) {
        if (level == null || level.isClientSide) return InteractionResult.SUCCESS;
        player.displayClientMessage(HoistText.msg("pump", HoistText.num(pumpedM3s * 3600), HoistText.num(headM()),
                HoistText.num(powerW(shaft) / 1000)), true);
        return InteractionResult.CONSUME;
    }
}
