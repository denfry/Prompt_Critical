package dev.denfry.promptcritical.mod.gas;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.gasprocessing.GlycolDehydration;
import dev.denfry.promptcritical.sim.gasprocessing.Hydrates;
import dev.denfry.promptcritical.sim.gasprocessing.LowTemperatureSeparation;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Установка подготовки попутного газа с НТС ({@link LowTemperatureSeparation}): попутный газ и метанол подводят
 * трубами к любой грани; товарный газ забирают сверху, воду снизу, ШФЛУ с боков. Сжимает газ компрессор
 * ({@link GasCompressorBlockEntity}) в {@link #RADIUS} блоках, гликоль регенерирует {@code glycol_regenerator} там же —
 * куб регенератора топится товарным газом установки (36 МДж/нм³, КПД 0,85). Давление НТС, уставку пропанового
 * холодильника и впрыск метанола задают кнопками щита; метанол дозируется так, чтобы температура гидратообразования
 * была на 3 K ниже температуры в самой холодной точке. Без ингибитора при выпадении свободной воды ниже гидратной
 * температуры в штуцере растёт гидратная пробка и перекрывает установку.
 */
public final class GasPlantBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int RADIUS = 6, GAS_MB = 4_000, LIQUID_MB = 8_000;
    /** Наибольшая производительность установки, нм³/с [BALANCE: малый блок, 3600 нм³/ч]. */
    public static final double MAX_SM3S = 1.0, FUEL_J_PER_SM3 = 36e6, HEATER_EFFICIENCY = 0.85, METHANOL_KGM3 = 791;
    public static final double P_STEP = 0.5e6, CHILL_STEP = 5, INHIBITION_MARGIN_K = 3;

    private final FeedTank gasIn = new FeedTank(PcFluids.ASSOCIATED_GAS.source.get(), GAS_MB);
    private final FeedTank methanol = new FeedTank(PcFluids.METHANOL.source.get(), LIQUID_MB);
    private final ProductTank sales = new ProductTank(PcFluids.SALES_GAS.source.get(), GAS_MB);
    private final ProductTank ngl = new ProductTank(PcFluids.NGL.source.get(), LIQUID_MB);
    private final ProductTank water = new ProductTank(PcFluids.PRODUCED_WATER.source.get(), LIQUID_MB);
    private final IFluidHandler top = new PortHandler(sales, gasIn, methanol), bottom = new PortHandler(water, gasIn, methanol),
            side = new PortHandler(ngl, gasIn, methanol);
    private final LowTemperatureSeparation lts = new LowTemperatureSeparation(GlycolDehydration.STANDARD);
    private boolean inhibit = true, compressor, regenerator, warned;
    private LowTemperatureSeparation.Result last;
    private double freeWaterPerSm3, flowSm3s, methanolWt;
    private int ticks;
    @Nullable
    private BlockPos regenPos;

    public GasPlantBlockEntity(BlockPos pos, BlockState state) {
        super(GasBlockEntities.GAS_PLANT.get(), pos, state);
    }

    public LowTemperatureSeparation lts() {
        return lts;
    }

    public FeedTank gasIn() {
        return gasIn;
    }

    public FeedTank methanol() {
        return methanol;
    }

    public ProductTank sales() {
        return sales;
    }

    public ProductTank ngl() {
        return ngl;
    }

    public void setInhibit(boolean on) {
        inhibit = on;
    }

    @Nullable
    public LowTemperatureSeparation.Result last() {
        return last;
    }

    @Nullable
    private GasCompressorBlockEntity findCompressor() {
        List<GasCompressorBlockEntity> all = Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), GasCompressorBlockEntity.class);
        return all.isEmpty() ? null : all.getFirst();
    }

    @Nullable
    private BlockPos findRegenerator() {
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-RADIUS, -RADIUS, -RADIUS), worldPosition.offset(RADIUS, RADIUS, RADIUS)))
            if (level.getBlockState(p).is(PcBlocks.GLYCOL_REGENERATOR.get())) return p.immutable();
        return null;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        ticks++;
        double dt = 0.05 * b.world().clock().kSlow();
        GasCompressorBlockEntity comp = findCompressor();
        if (ticks % 40 == 1 || (regenPos != null && !level.getBlockState(regenPos).is(PcBlocks.GLYCOL_REGENERATOR.get())))
            regenPos = findRegenerator();
        BlockPos regen = regenPos;
        compressor = comp != null && comp.availableW() > 0;
        regenerator = regen != null;
        flowSm3s = 0;
        if (!compressor || !regenerator) {
            if (comp != null) comp.load(0);
            updateStates(regen);
            return;
        }
        // удельная мощность при текущих уставках (шаг нулевой длины пробку не меняет)
        double specificW = lts.process(1, 0, 0).compressorW();
        double q = Math.min(MAX_SM3S, Math.min(gasIn.amount() / dt, comp.availableW() / Math.max(1, specificW)));
        q = Math.min(q, Math.min(sales.room() / dt, Math.min(ngl.room() / (dt * 1.5), water.room() / dt)));
        q = Math.max(0, q);
        double dose = 0;
        if (inhibit && last != null && freeWaterPerSm3 > 0) {
            double need = Hydrates.methanolForDepression(last.hydrateK() - last.ltsK() + INHIBITION_MARGIN_K);
            dose = Math.min(freeWaterPerSm3 * q * need / Math.max(1, 100 - need), methanol.amount() / 1000 * METHANOL_KGM3 / dt);
        }
        LowTemperatureSeparation.Result r = lts.process(q, dose, dt);
        last = r;
        methanolWt = r.methanolWtPct();
        if (q > 0) freeWaterPerSm3 = r.freeWaterKgS() / q;
        double flowing = r.blocked() ? 0 : q;
        flowSm3s = flowing;
        gasIn.take(flowing * dt);
        methanol.take(dose * dt / METHANOL_KGM3 * 1000);
        double fuel = r.regenerationW() * dt / (HEATER_EFFICIENCY * FUEL_J_PER_SM3);
        sales.add(Math.max(0, r.salesSm3s() * dt - fuel));
        ngl.add(r.nglM3s() * dt * 1000);
        water.add(r.waterKgS() * dt);
        comp.load(r.compressorW());
        if (r.blocked() && !warned) {
            warned = true;
            for (Player p : s.players())
                if (p.distanceToSqr(worldPosition.getCenter()) < 48 * 48)
                    p.displayClientMessage(GasText.msg("plugged").withStyle(ChatFormatting.RED), false);
        } else if (!r.blocked()) {
            warned = false;
        }
        updateStates(regen);
        if (ticks % 100 == 0) setChanged();
    }

    private void updateStates(@Nullable BlockPos regen) {
        if (ticks % 10 != 0) return;
        boolean cold = flowSm3s > 0 && lts.chillK() < 273.15;
        BlockState st = getBlockState();
        if (st.getValue(BlockStateProperties.LIT) != cold) level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, cold), Block.UPDATE_ALL);
        if (regen != null) {
            BlockState rs = level.getBlockState(regen);
            boolean run = flowSm3s > 0;
            if (rs.getValue(PcProperties.RUNNING) != run) level.setBlock(regen, rs.setValue(PcProperties.RUNNING, run), Block.UPDATE_ALL);
        }
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction face) {
        if (face == Direction.UP) return top;
        if (face == Direction.DOWN) return bottom;
        return side;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction face) {
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
        if (!compressor || !regenerator || last == null) {
            player.displayClientMessage(GasText.msg("missing", RADIUS), true);
        } else {
            player.displayClientMessage(GasText.msg("status", GasText.num(flowSm3s * 3600, 0), GasText.num(last.nglM3s() * 3.6e6, 0),
                    GasText.num(last.ltsK() - 273.15, 1), GasText.num(lts.ltsPressurePa() / 1e6, 1),
                    GasText.num(last.ltsDewPointK() - 273.15, 1), GasText.num(last.inhibitedK() - 273.15, 1)), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case GasPanels.LB_P_DOWN -> lts.setLtsPressurePa(lts.ltsPressurePa() - P_STEP);
            case GasPanels.LB_P_UP -> lts.setLtsPressurePa(lts.ltsPressurePa() + P_STEP);
            case GasPanels.LB_CHILL_DOWN -> lts.setChillK(lts.chillK() - CHILL_STEP);
            case GasPanels.LB_CHILL_UP -> lts.setChillK(lts.chillK() + CHILL_STEP);
            case GasPanels.LB_METHANOL -> inhibit = !inhibit;
            default -> {
            }
        }
        setChanged();
    }

    @Override
    public PanelLayout panelLayout() {
        return GasPanels.PLANT;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[GasPanels.L_P_X100] = (int) Math.round(lts.ltsPressurePa() / 1e4);
        out[GasPanels.L_GAS_M3H] = (int) Math.round(flowSm3s * 3600);
        out[GasPanels.L_PLUG_PERMIL] = (int) Math.round(lts.plug() * 1000);
        out[GasPanels.L_NGL_PERMIL] = (int) Math.round(ngl.fill() * 1000);
        out[GasPanels.L_SALES_PERMIL] = (int) Math.round(sales.fill() * 1000);
        out[GasPanels.L_MEOH_PERMIL] = (int) Math.round(methanol.fill() * 1000);
        out[GasPanels.L_CHILL_X10] = (int) Math.round((lts.chillK() - 273.15) * 10);
        out[GasPanels.L_TEG_X10] = (int) Math.round(GlycolDehydration.STANDARD.leanTegMassFraction() * 1000);
        out[GasPanels.L_MEOH_WT_X10] = (int) Math.round(methanolWt * 10);
        int f = 0;
        if (compressor) f |= 1 << GasPanels.LF_COMPRESSOR;
        if (regenerator) f |= 1 << GasPanels.LF_REGENERATOR;
        if (inhibit) f |= 1 << GasPanels.LF_METHANOL;
        if (last != null) {
            out[GasPanels.L_POWER_KW] = (int) Math.round(last.compressorW() / 1000);
            out[GasPanels.L_TLTS_X10] = (int) Math.round((last.ltsK() - 273.15) * 10);
            out[GasPanels.L_DEW_X10] = (int) Math.round((last.ltsDewPointK() - 273.15) * 10);
            out[GasPanels.L_HYD_X10] = (int) Math.round((last.inhibitedK() - 273.15) * 10);
            out[GasPanels.L_NGL_LH] = (int) Math.round(last.nglM3s() * 3.6e6);
            if (last.freeWaterKgS() > 0 && last.ltsK() < last.inhibitedK()) f |= 1 << GasPanels.LF_RISK;
            if (last.blocked()) f |= 1 << GasPanels.LF_PLUG;
        }
        out[GasPanels.L_FLAGS] = f;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gasIn.save(tag, "gas_in");
        methanol.save(tag, "methanol");
        sales.save(tag, "sales");
        ngl.save(tag, "ngl");
        water.save(tag, "water");
        tag.putDouble("plug", lts.plug());
        tag.putDouble("lts_p", lts.ltsPressurePa());
        tag.putDouble("chill", lts.chillK());
        tag.putBoolean("inhibit", inhibit);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gasIn.load(tag, "gas_in");
        methanol.load(tag, "methanol");
        sales.load(tag, "sales");
        ngl.load(tag, "ngl");
        water.load(tag, "water");
        lts.setPlug(tag.getDouble("plug"));
        if (tag.contains("lts_p")) lts.setLtsPressurePa(tag.getDouble("lts_p"));
        if (tag.contains("chill")) lts.setChillK(tag.getDouble("chill"));
        inhibit = !tag.contains("inhibit") || tag.getBoolean("inhibit");
    }
}
