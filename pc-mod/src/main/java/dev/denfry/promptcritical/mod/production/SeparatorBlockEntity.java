package dev.denfry.promptcritical.mod.production;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.production.ProducingReservoir;
import dev.denfry.promptcritical.sim.production.ThreePhaseSeparator;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
 * Трёхфазный сепаратор первой ступени ({@link ThreePhaseSeparator}): принимает продукцию скважин и делит её на
 * нефть (трубы — к боковым граням), пластовую воду (к нижней) и попутный газ (к верхней). Газ, который некуда
 * девать, уходит на факел ({@code flare_tip} в {@link #FLARE_RADIUS} блоках); без факела переполненный газовый объём
 * останавливает приём — скважины закрываются.
 * <p>
 * Тот же BE служит сепаратору реального масштаба {@code three_phase_separator_real} (НГС-1,6-2000, 11 × 3 × 3): трубы
 * цепляются к его патрубкам (газ сверху, нефть с востока, вода снизу — {@code WholePorts}), а поплавки уровнемеров и
 * штоки регулирующих клапанов на модели идут по уровням, которые BE шлёт клиенту.
 */
public final class SeparatorBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost,
        dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity {
    public static final int OIL_MB = 16_000, WATER_MB = 16_000, GAS_MB = 4_000, FLARE_RADIUS = 16;

    private final ProductTank oil = new ProductTank(PcFluids.CRUDE_OIL.source.get(), OIL_MB);
    private final ProductTank water = new ProductTank(PcFluids.PRODUCED_WATER.source.get(), WATER_MB);
    private final ProductTank gas = new ProductTank(PcFluids.ASSOCIATED_GAS.source.get(), GAS_MB);
    private double inOil, inWater, inGas, inDt;
    private double flaredStdM3s, bsw, retentionS, carryover;
    @Nullable
    private BlockPos flare;
    private int ticks;
    /** Уровни нефти и воды, доли, как их видит клиент (поплавки и клапаны модели реального масштаба). */
    private float viewOil, viewWater;

    public SeparatorBlockEntity(BlockPos pos, BlockState state) {
        super(ProductionBlockEntities.SEPARATOR.get(), pos, state);
    }

    public ProductTank oil() {
        return oil;
    }

    public ProductTank water() {
        return water;
    }

    public ProductTank gas() {
        return gas;
    }

    /** Есть куда принять продукцию: жидкость помещается, газ — в объём или на факел. */
    public boolean hasRoom() {
        return oil.room() > 1000 && water.room() > 1000 && (flare != null || gas.room() > 100);
    }

    /** Поток скважины за шаг {@code dt}: нефть и вода, м³; газ, нм³. */
    public void accept(double oilM3, double waterM3, double gasStdM3, double dt) {
        inOil += oilM3;
        inWater += waterM3;
        inGas += gasStdM3;
        inDt += dt;
    }

    @Override
    public void tickDevice() {
        if (level == null || level.isClientSide) return;
        if (ticks++ % 40 == 0) flare = findFlare();
        if (inDt > 0) {
            ThreePhaseSeparator.Result r = ThreePhaseSeparator.STANDARD.separate(inOil / inDt, inWater / inDt, inGas / inDt,
                    ProducingReservoir.STOCK_OIL_KGM3);
            bsw = r.waterInOil();
            retentionS = r.oilRetentionS();
            carryover = r.liquidCarryover();
            oil.add(r.oilM3s() * inDt * 1000);
            water.add(r.waterM3s() * inDt * 1000);
            double g = r.gasStdM3s() * inDt;
            double left = g - gas.add(g);
            flaredStdM3s = flare != null ? left / inDt : 0;
        } else {
            flaredStdM3s = 0;
        }
        inOil = inWater = inGas = inDt = 0;
        if (ticks % 20 == 0 && flare != null && dev.denfry.promptcritical.mod.safety.Flares.managed(level, flare)) {
            // у оголовка есть щит розжига (13 §13.4.3): горение, выбросы и свет считает он
            dev.denfry.promptcritical.mod.safety.Flares.offer(level, flare, flaredStdM3s);
        } else if (ticks % 20 == 0 && flare != null) {
            BlockState tip = level.getBlockState(flare);
            boolean lit = flaredStdM3s > 0;
            // факел: попутный газ ≈ 38 МДж/м³, полнота сгорания 0,98 — остальное метаном (14 §14.1.1); ствол 30 м, 1000 К
            if (lit && level instanceof net.minecraft.server.level.ServerLevel sl0)
                dev.denfry.promptcritical.mod.environment.AirQualityService.emitGasFiring(sl0, flare, 30, 1000, flaredStdM3s * 38e6,
                        flaredStdM3s * 20, 0.03, 0.02, "flare@" + flare.toShortString(),
                        dev.denfry.promptcritical.sim.environment.EmissionLedger.Category.FLARE);
            if (tip.is(PcBlocks.FLARE_TIP.get()) && tip.getValue(BlockStateProperties.LIT) != lit)
                level.setBlock(flare, tip.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_ALL);
        }
        if (ticks % 100 == 0) setChanged();
        if (ticks % 20 == 0 && real() && (Math.abs(oil.fill() - viewOil) > 0.01 || Math.abs(water.fill() - viewWater) > 0.01)) {
            viewOil = (float) oil.fill();
            viewWater = (float) water.fill();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Уровень нефти, доля (на клиенте — последний присланный). */
    public float viewOil() {
        return viewOil;
    }

    /** Уровень воды, доля (на клиенте — последний присланный). */
    public float viewWater() {
        return viewWater;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("view_oil", (float) oil.fill());
        tag.putFloat("view_water", (float) water.fill());
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing(), real() ? 2 : 1);
    }

    @Nullable
    private BlockPos findFlare() {
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-FLARE_RADIUS, -4, -FLARE_RADIUS),
                worldPosition.offset(FLARE_RADIUS, 24, FLARE_RADIUS)))
            if (level.getBlockState(p).is(PcBlocks.FLARE_TIP.get())) return p.immutable();
        return null;
    }

    /** Нефть — с боков, газ — сверху, вода — снизу. */
    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == Direction.UP) return gas;
        if (side == Direction.DOWN) return water;
        return oil;
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
    public PanelLayout panelLayout() {
        return real() ? ProductionPanels.SEPARATOR_REAL : ProductionPanels.SEPARATOR_PANEL;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[ProductionPanels.S_OIL_PERMIL] = (int) Math.round(oil.fill() * 1000);
        out[ProductionPanels.S_WATER_PERMIL] = (int) Math.round(water.fill() * 1000);
        out[ProductionPanels.S_GAS_PERMIL] = (int) Math.round(gas.amount() / GAS_MB * 1000);
        out[ProductionPanels.S_BSW_X100] = (int) Math.round(bsw * 1e4);
        out[ProductionPanels.S_RET_S] = Double.isFinite(retentionS) ? (int) Math.min(36_000, Math.round(retentionS)) : 0;
        out[ProductionPanels.S_FLARE_M3H] = (int) Math.round(flaredStdM3s * 3600);
        int f = 0;
        if (flaredStdM3s > 0) f |= 1 << ProductionPanels.SF_FLARING;
        if (carryover > 0) f |= 1 << ProductionPanels.SF_CARRYOVER;
        if (!hasRoom()) f |= 1 << ProductionPanels.SF_FULL;
        out[ProductionPanels.S_FLAGS] = f;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        oil.save(tag, "oil");
        water.save(tag, "water");
        gas.save(tag, "gas");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("view_oil")) {
            // пакет клиенту: только уровни для модели, сохранённое состояние не трогаем
            viewOil = tag.getFloat("view_oil");
            viewWater = tag.getFloat("view_water");
            return;
        }
        super.loadAdditional(tag, registries);
        oil.load(tag, "oil");
        water.load(tag, "water");
        gas.load(tag, "gas");
    }
}
