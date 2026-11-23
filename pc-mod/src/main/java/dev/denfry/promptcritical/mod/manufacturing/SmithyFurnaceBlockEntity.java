package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.foundry.FoundryTags;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.manufacturing.Drawing;
import dev.denfry.promptcritical.sim.manufacturing.ElectricalSheet;
import dev.denfry.promptcritical.sim.manufacturing.Lamination;
import dev.denfry.promptcritical.sim.manufacturing.SheetCoating;
import dev.denfry.promptcritical.sim.manufacturing.WireInsulation;
import dev.denfry.promptcritical.sim.manufacturing.ForgeEvent;
import dev.denfry.promptcritical.sim.manufacturing.ForgeHeating;
import dev.denfry.promptcritical.sim.manufacturing.Forging;
import dev.denfry.promptcritical.sim.manufacturing.Furnace;
import dev.denfry.promptcritical.sim.manufacturing.Quenching;
import dev.denfry.promptcritical.sim.manufacturing.Tempering;
import dev.denfry.promptcritical.sim.manufacturing.Wire;
import dev.denfry.promptcritical.sim.manufacturing.Workpiece;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Камерная печь механизированной ступени (18 §18.6.2, §18.6.4, §18.7): {@code heating_furnace} греет заготовки к
 * уставке перед молотом и отжигает проволоку, {@code hardening_furnace} ещё и закаливает в своём баке (вода или масло)
 * и отпускает при низкой уставке. Модель камеры — {@link Furnace}; топливо — уголь и кокс, горелка отдаёт камере
 * {@link #BURNER_EFFICIENCY} теплоты сгорания, не больше {@link #MAX_POWER_W}. Газ и электричество (сеть 07) — в
 * проекте энергетики.
 */
public final class SmithyFurnaceBlockEntity extends SmithyDevice {
    /** Наибольшая мощность горелки в камеру, Вт [BALANCE]. */
    public static final double MAX_POWER_W = 50_000;
    /** Теплота сгорания угля на предмет (≈ 1 кг), Дж [сверить: каменный уголь 25–29 МДж/кг]. */
    public static final double FUEL_J_PER_ITEM = 28e6;
    /** Доля теплоты, дошедшая до камеры [сверить: камерные печи 30–50 %]. */
    public static final double BURNER_EFFICIENCY = 0.4;
    /** Запас теплоты в топке: не больше двух предметов вперёд, Дж. */
    public static final double FUEL_BUFFER_J = 2 * FUEL_J_PER_ITEM * BURNER_EFFICIENCY;
    /** Шаг уставки на щите, К. */
    public static final int SETPOINT_STEP_K = 25;
    /** Выше этой температуры камера отпуск не ведёт: это уже нагрев под закалку, К (650 °C) [моё]. */
    public static final double TEMPER_MAX_K = 923.15;
    /** Бак закалочной печи, мБ, и сколько в нём должно быть для закалки. */
    public static final int TANK_MB = 8000, QUENCH_MIN_MB = 1000;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.INPUT,
        MachineInventory.Role.INPUT};
    /** Слот покрывного материала: бумага или фенольная смола (18 §18.6.8–18.6.9). */
    public static final int COATING_SLOT = 2;

    private final boolean hardening;
    private Furnace furnace = Furnace.cold(WorkpieceData.AMBIENT_K);
    private double fuelJ;
    private double powerW;
    private int lastEvent = -1;
    /** Выдержка садки выше температуры отжига и в окне запекания лака, с. */
    private double soakS, bakeS;
    private final FluidTank tank = new FluidTank(TANK_MB, f -> f.getFluid().is(FluidTags.WATER) || f.getFluid().is(Smithy.QUENCH_OIL)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    public SmithyFurnaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean hardening) {
        super(type, pos, state, ROLES, (slot, s) -> slot == 0 ? isFuel(s)
                : slot == COATING_SLOT ? Electrical.isCoatingMaterial(s) : isCharge(s));
        this.hardening = hardening;
    }

    public static SmithyFurnaceBlockEntity heating(BlockPos pos, BlockState state) {
        return new SmithyFurnaceBlockEntity(SmithyBlockEntities.HEATING_FURNACE.get(), pos, state, false);
    }

    public static SmithyFurnaceBlockEntity hardening(BlockPos pos, BlockState state) {
        return new SmithyFurnaceBlockEntity(SmithyBlockEntities.HARDENING_FURNACE.get(), pos, state, true);
    }

    public static boolean isFuel(ItemStack s) {
        return s.is(FoundryTags.FURNACE_COAL);
    }

    public static boolean isCharge(ItemStack s) {
        return Smithy.forgeable(s) || s.has(ManufacturingComponents.WIRE.get()) || Electrical.isElectricalCharge(s);
    }

    public Furnace furnace() {
        return furnace;
    }

    public FluidTank tank() {
        return tank;
    }

    public void setSetpointK(double k) {
        furnace = furnace.withSetpoint(k);
        setChanged();
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel)) {
            clientTick();
            return;
        }
        if (level.getGameTime() % 20 == 0) step(1);
    }

    /** Секунда печи. */
    public void step(double seconds) {
        long now = level.getGameTime();
        ItemStack fuel = inv.getStackInSlot(0);
        while (!fuel.isEmpty() && fuelJ + FUEL_J_PER_ITEM * BURNER_EFFICIENCY <= FUEL_BUFFER_J) {
            fuelJ += FUEL_J_PER_ITEM * BURNER_EFFICIENCY;
            fuel.shrink(1);
        }
        double want = furnace.demandW(WorkpieceData.AMBIENT_K, MAX_POWER_W);
        powerW = Math.min(want, fuelJ / seconds);
        fuelJ = Math.max(0, fuelJ - powerW * seconds);
        furnace = furnace.step(powerW, WorkpieceData.AMBIENT_K, seconds);
        heatCharge(seconds, now);
        boolean lit = powerW > 0;
        BlockState st = getBlockState();
        if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT) != lit)
            level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_ALL);
        setChanged();
    }

    private void heatCharge(double seconds, long now) {
        ItemStack s = inv.getStackInSlot(1);
        if (s.isEmpty()) {
            soakS = 0;
            bakeS = 0;
            return;
        }
        if (Electrical.isElectricalCharge(s)) {
            electricalCharge(s, seconds);
            return;
        }
        if (s.getCount() != 1) return;
        WireData wire = s.get(ManufacturingComponents.WIRE.get());
        if (wire != null) {
            wireCharge(wire, seconds);
            return;
        }
        Workpiece w = Smithy.read(s, now);
        if (w == null) return;
        w = ForgeHeating.heat(w, furnace.tempK(), seconds);
        if (hardening && w.quenchHrc() > 0 && furnace.tempK() <= TEMPER_MAX_K) w = Tempering.temper(w, w.tempK(), seconds);
        inv.setStackInSlot(1, Smithy.stack(w, now));
    }

    /**
     * Лист или пачка пластин (18 §18.6.8): выше {@link ElectricalSheet#COATING_BURN_K} покрытие выгорает, выдержка
     * {@link ElectricalSheet#ANNEAL_S} выше {@link ElectricalSheet#ANNEAL_K} — отжиг (у пластин и наклёпа кромок),
     * выдержка {@link ElectricalSheet#BAKE_S} в окне запекания с материалом на каждый предмет садки — покрытие.
     */
    private void electricalCharge(ItemStack s, double seconds) {
        double t = furnace.tempK();
        ElectricalSheet sheet = Electrical.sheet(s);
        Lamination lam = Electrical.lamination(s);
        int n = s.getCount();
        if (t > ElectricalSheet.COATING_BURN_K) {
            if (sheet != null && sheet.coating() != SheetCoating.NONE) inv.setStackInSlot(1, Electrical.sheetStack(sheet.coat(SheetCoating.NONE), n));
            if (lam != null && lam.sheet().coating() != SheetCoating.NONE) inv.setStackInSlot(1, Electrical.bundleStack(lam.coat(SheetCoating.NONE), n));
        }
        soakS = t >= ElectricalSheet.ANNEAL_K ? soakS + seconds : 0;
        if (soakS >= ElectricalSheet.ANNEAL_S) {
            if (sheet != null && sheet.strain() > 0) inv.setStackInSlot(1, Electrical.sheetStack(sheet.anneal(), n));
            if (lam != null && (!lam.edgesRelieved() || lam.sheet().strain() > 0)) inv.setStackInSlot(1, Electrical.bundleStack(lam.anneal(), n));
        }
        ItemStack mat = inv.getStackInSlot(COATING_SLOT);
        SheetCoating c = Electrical.sheetCoating(mat);
        boolean window = t >= ElectricalSheet.BAKE_MIN_K && t <= ElectricalSheet.BAKE_MAX_K;
        bakeS = window && c != null && mat.getCount() >= n ? bakeS + seconds : 0;
        if (bakeS >= ElectricalSheet.BAKE_S) {
            bakeS = 0;
            if (sheet != null && sheet.coating() != c) {
                inv.setStackInSlot(1, Electrical.sheetStack(sheet.coat(c), n));
                mat.shrink(n);
            } else if (lam != null && !(lam.coatedAfterPunch() && lam.sheet().coating() == c)) {
                inv.setStackInSlot(1, Electrical.bundleStack(lam.coat(c), n));
                mat.shrink(n);
            }
        }
    }

    /** Проволока (18 §18.6.7, §18.6.9): отжиг наклёпа, выгорание изоляции, эмалирование или обмотка бумагой. */
    private void wireCharge(WireData wire, double seconds) {
        double t = furnace.tempK();
        Wire w = wire.wire();
        WireInsulation ins = wire.wireInsulation();
        if (t > ElectricalSheet.COATING_BURN_K && ins != WireInsulation.NONE) ins = WireInsulation.NONE;
        Wire annealed = Drawing.anneal(w, t, seconds);
        ItemStack mat = inv.getStackInSlot(COATING_SLOT);
        WireInsulation coat = Electrical.wireInsulation(mat);
        boolean window = t >= ElectricalSheet.BAKE_MIN_K && t <= ElectricalSheet.BAKE_MAX_K;
        bakeS = window && coat != null && ins == WireInsulation.NONE ? bakeS + seconds : 0;
        if (bakeS >= ElectricalSheet.BAKE_S) {
            bakeS = 0;
            ins = coat;
            mat.shrink(1);
        }
        if (annealed.strain() != w.strain() || ins != wire.wireInsulation())
            inv.setStackInSlot(1, Electrical.magnetWireStack(annealed, ins));
    }

    /** Закалить заготовку из камеры в баке печи: вода или масло по жидкости в баке. */
    public List<ForgeEvent> quench() {
        if (!hardening || level == null) return List.of();
        Workpiece w = Smithy.read(inv.getStackInSlot(1), level.getGameTime());
        if (w == null || inv.getStackInSlot(1).getCount() != 1 || tank.getFluidAmount() < QUENCH_MIN_MB) return List.of();
        Quenching.Medium m = tank.getFluid().getFluid().is(FluidTags.WATER) ? Quenching.Medium.WATER : Quenching.Medium.OIL;
        Forging.Result r = Quenching.quench(w, m);
        inv.setStackInSlot(1, Smithy.stack(r.piece(), level.getGameTime()));
        lastEvent = r.events().get(r.events().size() - 1).ordinal();
        if (level instanceof ServerLevel sl) {
            sl.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.9f);
            sl.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                    worldPosition.getZ() + 0.5, 10, 0.25, 0.1, 0.25, 0.02);
        }
        setChanged();
        return r.events();
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case SmithyPanels.F_BUTTON_DOWN -> setSetpointK(furnace.setpointK() - SETPOINT_STEP_K);
            case SmithyPanels.F_BUTTON_UP -> setSetpointK(Math.max(furnace.setpointK(), WorkpieceData.AMBIENT_K) + SETPOINT_STEP_K);
            case SmithyPanels.F_BUTTON_OFF -> setSetpointK(WorkpieceData.AMBIENT_K);
            case SmithyPanels.F_BUTTON_QUENCH -> quench();
            default -> { }
        }
    }

    private void clientTick() {
        MachineFx fx = MachineFx.of(level, worldPosition, getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (fx == null || !getBlockState().getValue(BlockStateProperties.LIT)) return;
        if (fx.chance(4)) fx.flame(fx.at(8, 4, 1), 0.05);
        if (fx.chance(3)) fx.smoke(fx.at(8, 16, 12), false, 0.08);
    }

    @Override
    @Nullable
    public IFluidHandler fluids(@Nullable Direction side) {
        return hardening ? tank : null;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (hardening && FluidUtil.getFluidHandler(stack).isPresent()) {
            return FluidUtil.interactWithFluidHandler(player, hand, tank) ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItem(player, hand, stack);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return isFuel(stack) || isCharge(stack) || Electrical.isCoatingMaterial(stack) || hardening && FluidUtil.getFluidHandler(stack).isPresent();
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public PanelLayout panelLayout() {
        return hardening ? SmithyPanels.HARDENING_FURNACE : SmithyPanels.HEATING_FURNACE;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[SmithyPanels.F_CHAMBER_C] = (int) Math.round(furnace.tempK() - 273.15);
        out[SmithyPanels.F_SETPOINT_C] = (int) Math.round(furnace.setpointK() - 273.15);
        out[SmithyPanels.F_POWER_KW10] = (int) Math.round(powerW / 100);
        out[SmithyPanels.F_FUEL_MJ10] = (int) Math.round(fuelJ / 1e5);
        out[SmithyPanels.F_TANK_MB] = tank.getFluidAmount();
        out[SmithyPanels.F_EVENT] = lastEvent;
        int f = powerW > 0 ? 1 << SmithyPanels.FF_LIT : 0;
        if (!tank.isEmpty() && tank.getFluid().getFluid().is(FluidTags.WATER)) f |= 1 << SmithyPanels.FF_WATER;
        if (level != null) {
            Workpiece w = Smithy.read(inv.getStackInSlot(1), level.getGameTime());
            if (w != null) {
                f |= 1 << SmithyPanels.FF_PIECE;
                out[SmithyPanels.F_PIECE_C] = (int) Math.round(w.tempK() - 273.15);
                out[SmithyPanels.F_HRC] = (int) Math.round(w.hardnessHrc());
            }
        }
        out[SmithyPanels.FLAGS] = f;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("chamber", furnace.tempK());
        tag.putDouble("setpoint", furnace.setpointK());
        tag.putDouble("fuel", fuelJ);
        tag.putInt("last", lastEvent);
        tag.putDouble("soak", soakS);
        tag.putDouble("bake", bakeS);
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        double t = tag.contains("chamber") ? tag.getDouble("chamber") : WorkpieceData.AMBIENT_K;
        furnace = new Furnace(Double.isFinite(t) && t > 0 ? t : WorkpieceData.AMBIENT_K,
                tag.contains("setpoint") ? tag.getDouble("setpoint") : WorkpieceData.AMBIENT_K);
        fuelJ = Math.max(0, Math.min(FUEL_BUFFER_J, tag.getDouble("fuel")));
        lastEvent = tag.contains("last") ? tag.getInt("last") : -1;
        soakS = Math.max(0, tag.getDouble("soak"));
        bakeS = Math.max(0, tag.getDouble("bake"));
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
    }
}
