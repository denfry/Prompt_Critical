package dev.denfry.promptcritical.mod.production;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.AirQualityService;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.hazard.HazardService;
import dev.denfry.promptcritical.mod.hazard.OilSpills;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.environment.EmissionLedger;
import dev.denfry.promptcritical.sim.hazard.Flammable;
import dev.denfry.promptcritical.sim.pipeline.StorageTank;
import dev.denfry.promptcritical.sim.production.ProducingReservoir;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Вертикальный стальной резервуар РВС-1000 реального масштаба (09 §9.8.4, ГОСТ 31385; art/blockbench/storage/
 * oil_tank_real.author.js): 1000 м³ нефти, приём — патрубок на западе, раздача — на востоке. Два исполнения: со
 * стационарной крышей ({@code oil_tank_real}) и с плавающей ({@code oil_tank_floating_real}).
 * <ul>
 *   <li>Потери от испарения по {@link StorageTank}, как у резервуара Create с уровнемером: большие дыхания при наливе
 *   и малые от суточного цикла (SLOW, ×{@value OilSpills#K_SLOW}); масса уходит из резервуара, ЛОС — в учёт выбросов.
 *   Плавающая крыша снижает их в {@code 1 / (1 − 0,94)} раз. При наливе в резервуар со стационарной крышей у
 *   дыхательного клапана стоит облако паров — горючая зона.</li>
 *   <li>Температура продукта — смешение: налив приходит с температурой источника выше по линии ({@link CrudeLine}:
 *   подогреватель, насос, другой резервуар) или воздуха, остывает к воздуху с постоянной времени
 *   {@value #COOL_DAYS} сут (SLOW) [моё].</li>
 *   <li>Уровень и плавающая крыша видны на модели: BE шлёт клиенту заполнение.</li>
 * </ul>
 */
public final class OilTankBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost, CrudeLine.Source,
        dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity {
    /** Номинальный объём, мБ (1000 м³). */
    public static final int CAPACITY_MB = 1_000_000;
    public static final double DENSITY = ProducingReservoir.STOCK_OIL_KGM3;
    /** Постоянная времени остывания продукта к воздуху, сут [моё: 1000 м³ под открытым небом]. */
    public static final double COOL_DAYS = 2;
    /** Порог облака у дыхательного клапана, кг/с [моё], как у уровнемера. */
    public static final double VENT_CLOUD_KG_S = 0.01;
    /** Высота взлива, м (обечайка 11,92 м). */
    public static final double SHELL_M = 11.92;
    private static final BlockPos RECEIVE_CELL = new BlockPos(-6, 0, 0), BREATHER_CELL = new BlockPos(2, 12, 0);

    private final ProductTank oil = new ProductTank(PcFluids.CRUDE_OIL.source.get(), CAPACITY_MB);
    /** Резервуар и принимает, и отдаёт: {@link ProductTank} трубам только сливает, налив — через {@link ProductTank#add}. */
    private final IFluidHandler store = new Store();
    private double lastMb = -1, inflowLs, lossKgS, remainderL, productC = Double.NaN, sourceC = Double.NaN;
    private boolean venting;
    private int ticks;
    /** Заполнение, доля, как его видит клиент (плавающая крыша). */
    private float viewFill;

    public OilTankBlockEntity(BlockPos pos, BlockState state) {
        super(ProductionBlockEntities.OIL_TANK.get(), pos, state);
    }

    public ProductTank oil() {
        return oil;
    }

    public boolean floatingRoof() {
        return getBlockState().is(PcBlocks.OIL_TANK_FLOATING_REAL.get());
    }

    public double lossKgS() {
        return lossKgS;
    }

    public boolean venting() {
        return venting;
    }

    @Override
    public double crudeC() {
        return productC;
    }

    public float viewFill() {
        return viewFill;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || ticks++ % 20 != 0) return;
        Direction f = wholeModelFacing();
        double airC = EnvironmentService.climateK(sl, worldPosition) - 273.15;
        if (ticks % 100 == 1 || Double.isNaN(sourceC)) {
            CrudeLine.Source src = CrudeLine.upstream(sl, CrudeLine.portCell(worldPosition, f, RECEIVE_CELL),
                    CrudeLine.worldFace(Direction.WEST, f), this);
            double t = src == null ? Double.NaN : src.crudeC();
            sourceC = Double.isFinite(t) ? t : airC;
        }
        double amount = oil.amount();
        inflowLs = lastMb < 0 ? 0 : Math.max(0, amount - lastMb);
        if (Double.isNaN(productC)) productC = airC;
        // смешение налитого с тем, что было, и остывание к воздуху
        if (amount > 0 && inflowLs > 0) productC += (sourceC - productC) * Math.min(1, inflowLs / amount);
        double tau = COOL_DAYS * StorageTank.SECONDS_PER_DAY / OilSpills.K_SLOW;
        productC += (airC - productC) * Math.min(1, 1 / tau);
        // потери от испарения
        boolean floating = floatingRoof();
        double tK = productC + 273.15;
        double working = amount > 0 ? StorageTank.workingLossKgS(StorageTank.Product.CRUDE, inflowLs / 1000, tK, floating) : 0;
        double standing = amount > 0
                ? StorageTank.standingLossKgS(StorageTank.Product.CRUDE, (CAPACITY_MB - amount) / 1000, tK, floating) * OilSpills.K_SLOW : 0;
        lossKgS = working + standing;
        double litres = lossKgS / DENSITY * 1000 + remainderL;
        int mb = (int) litres;
        remainderL = litres - mb;
        if (mb > 0) oil.drain(mb, IFluidHandler.FluidAction.EXECUTE);
        String id = "rvs@" + worldPosition.toShortString();
        if (lossKgS > 0) AirQualityService.ledger(sl).record(id, EmissionLedger.Category.FUGITIVE, EmissionLedger.Pollutant.VOC, lossKgS, 1);
        venting = !floating && working >= VENT_CLOUD_KG_S;
        if (venting) HazardService.vent(sl, id, CrudeLine.portCell(worldPosition, f, BREATHER_CELL).above(), Flammable.BUTANE, working, 0, false);
        lastMb = oil.amount();
        if (ticks % 200 == 1) setChanged();
        if (Math.abs(oil.fill() - viewFill) > 0.005) {
            viewFill = (float) oil.fill();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("view_fill", (float) oil.fill());
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /** Приём и раздача — один и тот же объём; к каким граням можно подключиться, решают порты модели. */
    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return store;
    }

    private final class Store implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) {
            return oil.getFluidInTank(0);
        }

        @Override
        public int getTankCapacity(int tank) {
            return CAPACITY_MB;
        }

        @Override
        public boolean isFluidValid(int tank, net.neoforged.neoforge.fluids.FluidStack stack) {
            return stack.is(PcFluids.CRUDE_OIL.source.get());
        }

        @Override
        public int fill(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !isFluidValid(0, resource)) return 0;
            int n = (int) Math.min(resource.getAmount(), Math.floor(oil.room()));
            if (n > 0 && action.execute()) oil.add(n);
            return Math.max(0, n);
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
            return oil.drain(resource, action);
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain, FluidAction action) {
            return oil.drain(maxDrain, action);
        }
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
        return worldPosition.relative(wholeModelFacing(), 7);
    }

    @Override
    public PanelLayout panelLayout() {
        return floatingRoof() ? TransportPanels.OIL_TANK_FLOATING : TransportPanels.OIL_TANK;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        double fill = oil.fill();
        out[TransportPanels.T_LEVEL_PERMIL] = (int) Math.round(fill * 1000);
        out[TransportPanels.T_LEVEL_CM] = (int) Math.round(fill * SHELL_M * 100);
        out[TransportPanels.T_VOLUME_M3] = (int) Math.round(oil.amount() / 1000);
        out[TransportPanels.T_LOSS_KGH10] = (int) Math.round(lossKgS * 3600 * 10);
        out[TransportPanels.T_TEMP_C10] = Double.isFinite(productC) ? (int) Math.round(productC * 10) : 0;
        int fl = 0;
        if (venting) fl |= 1 << TransportPanels.TF_VENT;
        if (StorageTank.high(fill)) fl |= 1 << TransportPanels.TF_HIGH;
        if (StorageTank.highHigh(fill)) fl |= 1 << TransportPanels.TF_HH;
        out[TransportPanels.T_FLAGS] = fl;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        oil.save(tag, "oil");
        if (Double.isFinite(productC)) tag.putDouble("product_c", productC);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("view_fill")) {
            // пакет клиенту: только заполнение для модели
            viewFill = tag.getFloat("view_fill");
            return;
        }
        super.loadAdditional(tag, registries);
        oil.load(tag, "oil");
        productC = tag.contains("product_c") ? tag.getDouble("product_c") : Double.NaN;
    }
}
