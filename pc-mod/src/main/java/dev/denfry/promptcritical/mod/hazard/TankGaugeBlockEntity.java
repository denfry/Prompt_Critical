package dev.denfry.promptcritical.mod.hazard;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.AirQualityService;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ops.FireService;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.environment.EmissionLedger;
import dev.denfry.promptcritical.sim.hazard.Flammable;
import dev.denfry.promptcritical.sim.pipeline.StorageTank;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Уровнемер {@code tank_gauge} на крыше резервуара Create (09 §9.8.4): резервуар Create с нефтью или нефтепродуктом
 * становится резервуаром парка — мод его не подменяет, а следит за ним сверху.
 * <ul>
 *   <li>Уровень, верхний ({@value StorageTank#HIGH}) и верхний аварийный ({@value StorageTank#HIGH_HIGH}) уровни;
 *   при верхнем аварийном уровнемер выдаёт сигнал редстоуна ({@code TANK_HIGH_HIGH_LEVEL}).</li>
 *   <li>Перелив: если резервуар налит до краёв, а налив не остановили, каждую секунду продукт в объёме последнего
 *   налива уходит через дыхательную арматуру и стекает по стенке — разлив ({@link OilSpills}) у подножия.</li>
 *   <li>Потери от испарения ({@link StorageTank}): малые дыхания газового пространства и большие при наливе — масса
 *   уходит из резервуара, ЛОС — в учёт выбросов (категория FUGITIVE). При наливе в резервуар со стационарной крышей у
 *   дыхательных клапанов стоит облако паров — горючая зона, искра там поджигает (паров бензина в моде нет: облако
 *   считается как бутан [моё]).</li>
 *   <li>Плавающая крыша: ПКМ уровнемером стопкой из {@value #ROOF_PLATES} стальных листов — потери ×0,06.</li>
 * </ul>
 * Время — FAST (1 с игры = 1 с), кроме малых дыханий: суточный цикл — SLOW, ×72 (00 §0.7).
 */
public class TankGaugeBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    /** Уровнемер резервуара, Вт: прибор питается от розетки, щита или ИБП рядом, без питания не даёт сигнала. */
    public static final double POWER_W = 50;
    public static final int ROOF_PLATES = 8;
    /** Порог облака у дыхательного клапана, кг/с [моё]. */
    static final double VENT_CLOUD_KG_S = 0.01;

    private boolean floatingRoof, overflowing;
    private double lastAmountL = -1, inflowLs, fillRateLs, lossKgS, remainderL, overflowLs, spilledL;
    private int ticks;

    public TankGaugeBlockEntity(BlockPos pos, BlockState state) {
        super(HazardBlockEntities.TANK_GAUGE.get(), pos, state);
    }

    @Nullable
    public FluidTankBlockEntity tank() {
        if (level == null || !(level.getBlockEntity(worldPosition.below()) instanceof FluidTankBlockEntity t)) return null;
        return t.getControllerBE();
    }

    /** Продукт для потерь от испарения; {@code null} — не нефтепродукт. */
    @Nullable
    static StorageTank.Product product(Fluid f) {
        if (f.isSame(PcFluids.GASOLINE.source.get()) || f.isSame(PcFluids.NATURAL_GASOLINE.source.get())
                || f.isSame(PcFluids.PYROLYSIS_GASOLINE.source.get())) return StorageTank.Product.GASOLINE;
        if (f.isSame(PcFluids.KEROSENE.source.get())) return StorageTank.Product.KEROSENE;
        if (f.isSame(PcFluids.DIESEL_FUEL.source.get())) return StorageTank.Product.DIESEL;
        if (f.isSame(PcFluids.FUEL_OIL.source.get())) return StorageTank.Product.FUEL_OIL;
        return FireService.hydrocarbon(f) ? StorageTank.Product.CRUDE : null;
    }

    public double fill() {
        FluidTankBlockEntity t = tank();
        if (t == null) return 0;
        var inv = t.getTankInventory();
        return inv.getCapacity() <= 0 ? 0 : (double) inv.getFluidAmount() / inv.getCapacity();
    }

    public boolean floatingRoof() {
        return floatingRoof;
    }

    public void setFloatingRoof(boolean v) {
        floatingRoof = v;
        setChanged();
    }

    public double lossKgS() {
        return lossKgS;
    }

    public double spilledL() {
        return spilledL;
    }

    public boolean overflowing() {
        return overflowing;
    }

    String id() {
        return "tank@" + worldPosition.toShortString();
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || !dev.denfry.promptcritical.mod.grid.MachinePower.powered(level, worldPosition, POWER_W) || ++ticks % 20 != 0) return;
        FluidTankBlockEntity t = tank();
        if (t == null) {
            lastAmountL = -1;
            setSignal(false);
            return;
        }
        var inv = t.getTankInventory();
        double amount = inv.getFluidAmount(), cap = inv.getCapacity();
        inflowLs = lastAmountL < 0 ? 0 : Math.max(0, amount - lastAmountL);
        Fluid fluid = inv.getFluid().getFluid();
        StorageTank.Product p = inv.isEmpty() ? null : product(fluid);
        // перелив: налит до краёв, а насос всё ещё качает (последний налив не нулевой)
        boolean full = cap > 0 && amount >= cap - 1;
        // сигнализатор аварийного уровня видит уровень до перелива
        boolean hh = cap > 0 && StorageTank.highHigh(amount / cap);
        // у полного резервуара налив не виден (принять больше некуда): переливается то, что качали до краёв
        if (full && inflowLs > 0) {
            overflowLs = Math.max(inflowLs, fillRateLs);
            overflowing = true;
        } else {
            overflowing = false;
            overflowLs = 0;
            if (!full) fillRateLs = inflowLs;
        }
        if (overflowing && p != null) {
            int mb = (int) Math.min(amount, Math.max(1, overflowLs));
            inv.drain(mb, IFluidHandler.FluidAction.EXECUTE);
            spilledL += mb;
            double density = Math.max(500, fluid.getFluidType().getDensity());
            OilSpills.spill(sl, foot(t), BuiltInRegistries.FLUID.getKey(fluid).toString(), density, mb);
            amount -= mb;
        }
        // потери от испарения
        lossKgS = 0;
        if (p != null) {
            double tK = EnvironmentService.climateK(sl, worldPosition);
            double working = StorageTank.workingLossKgS(p, inflowLs / 1000, tK, floatingRoof);
            double standing = StorageTank.standingLossKgS(p, (cap - amount) / 1000, tK, floatingRoof) * OilSpills.K_SLOW;
            lossKgS = working + standing;
            double density = Math.max(500, fluid.getFluidType().getDensity());
            double litres = lossKgS / density * 1000 + remainderL;
            int mb = (int) litres;
            remainderL = litres - mb;
            if (mb > 0) inv.drain(mb, IFluidHandler.FluidAction.EXECUTE);
            AirQualityService.ledger(sl).record(id(), EmissionLedger.Category.FUGITIVE, EmissionLedger.Pollutant.VOC, lossKgS, 1);
            if (working >= VENT_CLOUD_KG_S) HazardService.vent(sl, id(), worldPosition.above(), Flammable.BUTANE, working, 0, false);
        }
        lastAmountL = inv.getFluidAmount();
        setSignal(hh);
        if (ticks % 200 == 0) setChanged();
    }

    /** Место у подножия резервуара за его стенкой: туда стекает перелив. */
    private BlockPos foot(FluidTankBlockEntity t) {
        BlockPos c = t.getBlockPos();
        return c.offset(t.getWidth(), 0, 0);
    }

    private void setSignal(boolean on) {
        BlockState s = getBlockState();
        if (s.hasProperty(BlockStateProperties.POWERED) && s.getValue(BlockStateProperties.POWERED) != on) {
            level.setBlock(worldPosition, s.setValue(BlockStateProperties.POWERED, on), Block.UPDATE_ALL);
            level.updateNeighborsAt(worldPosition, s.getBlock());
        }
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return HazardPanels.TANK_GAUGE;
    }

    @Override
    public void readPanel(int[] v) {
        double f = fill();
        v[HazardPanels.T_LEVEL] = (int) Math.round(f * 1000);
        v[HazardPanels.T_INFLOW] = (int) Math.round(inflowLs * 10);
        v[HazardPanels.T_LOSS] = (int) Math.round(lossKgS * 3600 * 10);
        v[HazardPanels.T_SPILLED] = (int) Math.round(spilledL);
        v[HazardPanels.T_FLAGS] = (StorageTank.high(f) ? 1 << HazardPanels.TF_HIGH : 0) | (StorageTank.highHigh(f) ? 1 << HazardPanels.TF_HH : 0)
                | (overflowing ? 1 << HazardPanels.TF_OVERFLOW : 0) | (floatingRoof ? 1 << HazardPanels.TF_ROOF : 0);
    }

    // ------------------------------------------------------------------ руки

    @Override
    public boolean accepts(ItemStack stack) {
        return !floatingRoof && stack.is(PcItems.STEEL_PLATE.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.getCount() < ROOF_PLATES && !player.getAbilities().instabuild) {
            player.displayClientMessage(HazardText.msg("roof_plates", ROOF_PLATES), true);
            return ItemInteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) stack.shrink(ROOF_PLATES);
        setFloatingRoof(true);
        player.displayClientMessage(HazardText.msg("roof_fitted").withStyle(ChatFormatting.GREEN), true);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(HazardText.msg("gauge", Math.round(fill() * 1000) / 10.0, Math.round(lossKgS * 3600 * 10) / 10.0,
                floatingRoof ? HazardText.msg("gauge.floating").getString() : HazardText.msg("gauge.fixed").getString()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public IFluidHandler fluids(@Nullable Direction side) {
        return null;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("floatingRoof", floatingRoof);
        tag.putDouble("spilled", spilledL);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        floatingRoof = tag.getBoolean("floatingRoof");
        spilledL = tag.getDouble("spilled");
    }
}
