package dev.denfry.promptcritical.mod.production;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.pipeline.CentrifugalPump;
import dev.denfry.promptcritical.sim.pipeline.CrudeViscosity;
import dev.denfry.promptcritical.sim.pipeline.Pipeline;
import dev.denfry.promptcritical.sim.production.ProducingReservoir;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Магистральный насос НМ 1250-260 на валу Create (09 §9.8): напор — по характеристике «напор — подача»
 * ({@link CentrifugalPump}), частота вращения — доля номинальной ({@value #NOMINAL_RPM} об/мин вала Create ↔ 3000
 * об/мин двигателя [BALANCE]). Насос работает на участок нефтепровода DN 350 эквивалентной длины
 * {@value #LINE_EQ_M} м [моё]: рабочая точка — пересечение кривой насоса с характеристикой участка, где трение зависит
 * от вязкости нефти (Беггс — Робинсон, {@link CrudeViscosity}) при её температуре на всасе. Холодная нефть вязче —
 * подача меньше; нагретая путевым подогревателем ({@link LineHeaterBlockEntity}) выше по линии идёт легче.
 * <p>
 * Трубы Create подают нефть в приёмный бак у всасывающего патрубка и забирают из бака нагнетания; насос перекачивает
 * за секунду не больше рабочей точки, не больше, чем есть на всасе, и не больше, чем помещается в нагнетание (закрытая
 * линия — подача падает, напор растёт к напору закрытой задвижки). Пустой всас при работе — срыв подачи (кавитация).
 * Мощность на валу ({@link CentrifugalPump#shaftPowerW}) нагружает сеть Create.
 */
public final class MainlinePumpBlockEntity extends DeviceBlockEntity implements ShaftConsumer, FoundryDevice, PanelHost, CrudeLine.Source,
        dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity {
    public static final CentrifugalPump PUMP = CentrifugalPump.NM_1250_260;
    public static final double NOMINAL_RPM = 128;
    /** Участок, на который работает насос: внутренний диаметр трубы 377 × 9 мм и эквивалентная длина, м [моё]. */
    public static final double LINE_ID_M = 0.359, LINE_EQ_M = 6000;
    /** Подпор на всасе от подпорного насоса или резервуарного парка при полном приёмном баке, м [моё]. */
    public static final double FEED_HEAD_M = 30;
    public static final int SUCTION_MB = 20_000, DISCHARGE_MB = 20_000;
    public static final double DENSITY = ProducingReservoir.STOCK_OIL_KGM3;
    public static final double RATED_W = PUMP.shaftPowerW(PUMP.nominalFlowM3s(), 1, DENSITY);
    public static final int B_START = 0, B_STOP = 1;
    private static final Direction SUCTION_FACE = Direction.NORTH;
    private static final BlockPos SUCTION_CELL = new BlockPos(-1, 0, -1);

    private final ShaftInput shaft = new ShaftInput();
    private final FeedTank suction = new FeedTank(PcFluids.CRUDE_OIL.source.get(), SUCTION_MB);
    private final ProductTank discharge = new ProductTank(PcFluids.CRUDE_OIL.source.get(), DISCHARGE_MB);
    private boolean on, cavitating;
    private double speed, flowM3s, operatingM3s, headM, suctionPa, powerW, oilC = Double.NaN;
    private int ticks;

    public MainlinePumpBlockEntity(BlockPos pos, BlockState state) {
        super(ProductionBlockEntities.MAINLINE_PUMP.get(), pos, state);
    }

    private long tick() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        return b == null ? 0 : b.world().clock().tick();
    }

    public FeedTank suction() {
        return suction;
    }

    public ProductTank discharge() {
        return discharge;
    }

    public boolean on() {
        return on;
    }

    public void setOn(boolean v) {
        on = v;
        setChanged();
    }

    public double flowM3s() {
        return flowM3s;
    }

    public double operatingFlowM3s() {
        return operatingM3s;
    }

    public double headM() {
        return headM;
    }

    public double dischargePa() {
        return suctionPa + CentrifugalPump.pressurePa(headM, DENSITY);
    }

    public boolean cavitating() {
        return cavitating;
    }

    /** Температура нефти на всасе, °C (до первого расчёта — NaN). */
    @Override
    public double crudeC() {
        return oilC;
    }

    /** Напор участка, м, при подаче {@code q} и вязкости {@code muPaS}: трение по Дарси — Вейсбаху. */
    public static double lineHeadM(double q, double muPaS) {
        if (q <= 0) return 0;
        double dp = Pipeline.pressureDropPa(q * DENSITY, DENSITY, muPaS, LINE_ID_M, LINE_EQ_M);
        return dp / (DENSITY * 9.80665);
    }

    /** Рабочая точка: подача, при которой напор насоса на доле частоты {@code r} равен напору участка, м³/с. */
    public static double operatingFlow(double r, double muPaS) {
        if (r <= 0) return 0;
        double lo = 0, hi = PUMP.maxFlowM3s(r);
        for (int i = 0; i < 50; i++) {
            double mid = (lo + hi) / 2;
            if (PUMP.headM(mid, r) > lineHeadM(mid, muPaS)) lo = mid;
            else hi = mid;
        }
        return (lo + hi) / 2;
    }

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        shaft.sample(rpm, healthy, tick);
    }

    @Override
    public double shaftPowerW() {
        return powerW;
    }

    @Override
    public double ratedShaftW() {
        return RATED_W;
    }

    @Override
    public double nominalShaftRpm() {
        return NOMINAL_RPM;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) {
            clientTick();
            return;
        }
        if (ticks++ % 20 != 0) return;
        Direction f = wholeModelFacing();
        if (ticks % 100 == 1 || Double.isNaN(oilC)) {
            CrudeLine.Source src = CrudeLine.upstream(sl, CrudeLine.portCell(worldPosition, f, SUCTION_CELL),
                    CrudeLine.worldFace(SUCTION_FACE, f), this);
            double t = src == null ? Double.NaN : src.crudeC();
            oilC = Double.isFinite(t) ? t : CrudeLine.ambientC(sl, worldPosition);
        }
        speed = on ? Math.min(1, shaft.rpm(tick()) / NOMINAL_RPM) : 0;
        double mu = CrudeViscosity.deadOilCp(DENSITY, oilC) * 1e-3;
        operatingM3s = operatingFlow(speed, mu);
        double avail = suction.amount() / 1000, room = discharge.room() / 1000;
        flowM3s = Math.max(0, Math.min(operatingM3s, Math.min(avail, room)));
        if (flowM3s > 0) discharge.add(suction.take(flowM3s * 1000));
        // срыв подачи: насос крутится, а на всасе нефти меньше половины того, что он берёт, и упирается он не в нагнетание
        cavitating = speed > 0 && avail < operatingM3s * 0.5 && room > avail;
        double h = PUMP.headM(flowM3s, speed);
        headM = cavitating ? h * avail / Math.max(1e-9, operatingM3s) : h;
        suctionPa = CentrifugalPump.pressurePa(FEED_HEAD_M * suction.fill(), DENSITY);
        powerW = speed > 0 ? PUMP.shaftPowerW(flowM3s, speed, DENSITY) : 0;
        BlockState st = getBlockState();
        boolean running = speed > 0;
        if (st.getValue(PcProperties.RUNNING) != running) level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, running), Block.UPDATE_ALL);
        if (ticks % 200 == 1) setChanged();
    }

    private void clientTick() {
        BlockState st = getBlockState();
        if (!st.getValue(PcProperties.RUNNING)) return;
        MachineFx fx = MachineFx.of(level, worldPosition, st.getValue(BlockStateProperties.HORIZONTAL_FACING));
        // торцевые уплотнения насоса подтекают каплями (вал на y 14,4, корпус x −16…0)
        if (fx != null && fx.chance(0.5)) fx.leak(fx.at(level.random.nextBoolean() ? -15 : -1, 12, 8));
    }

    /** Всас (северный патрубок модели) — приёмный бак, нагнетание (южный) — бак нагнетания. */
    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == null) return discharge;
        Direction model = side.getAxis().isVertical() ? side : dev.denfry.promptcritical.mod.multiblock.WholePorts.modelFace(side, wholeModelFacing());
        return model == SUCTION_FACE ? suction : discharge;
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
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing(), 2);
    }

    @Override
    public PanelLayout panelLayout() {
        return TransportPanels.MAINLINE_PUMP;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == B_START) setOn(true);
        else if (id == B_STOP) setOn(false);
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[TransportPanels.P_SUCTION_KPA] = (int) Math.round(suctionPa / 1000);
        out[TransportPanels.P_DISCHARGE_KPA] = (int) Math.round(dischargePa() / 1000);
        out[TransportPanels.P_FLOW_M3H] = (int) Math.round(flowM3s * 3600);
        out[TransportPanels.P_HEAD_M] = (int) Math.round(headM);
        out[TransportPanels.P_POWER_KW] = (int) Math.round(powerW / 1000);
        out[TransportPanels.P_EFF_PERMIL] = (int) Math.round(PUMP.efficiency(flowM3s, speed) * 1000);
        out[TransportPanels.P_SPEED_PERMIL] = (int) Math.round(speed * 1000);
        out[TransportPanels.P_OIL_C10] = Double.isFinite(oilC) ? (int) Math.round(oilC * 10) : 0;
        out[TransportPanels.P_VISC_CP10] = Double.isFinite(oilC) ? (int) Math.round(CrudeViscosity.deadOilCp(DENSITY, oilC) * 10) : 0;
        int fl = 0;
        if (speed > 0) fl |= 1 << TransportPanels.PF_RUN;
        if (cavitating) fl |= 1 << TransportPanels.PF_CAVITATION;
        out[TransportPanels.P_FLAGS] = fl;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("on", on);
        suction.save(tag, "suction");
        discharge.save(tag, "discharge");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        on = tag.getBoolean("on");
        suction.load(tag, "suction");
        discharge.load(tag, "discharge");
    }
}
