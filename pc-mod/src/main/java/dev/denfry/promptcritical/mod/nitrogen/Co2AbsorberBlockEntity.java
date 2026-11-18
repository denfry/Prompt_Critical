package dev.denfry.promptcritical.mod.nitrogen;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.nitrogen.Purification;
import dev.denfry.promptcritical.sim.nitrogen.Species;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Колонна тонкой очистки ({@link Purification}) 1×3×1 вплотную к печи риформинга: абсорбер МЭА с отпарной колонной и
 * метанатор. Тепло регенерации — технологическое тепло печи. Газ в норме (H₂:N₂ 2,7–3,3, CO + CO₂ до 10 ppm) идёт в
 * бак азотоводородной смеси, его забирают трубой с любой грани (1 мБ = 1 нм³); газ не в норме сбрасывается на
 * свечу — как на пуске агрегата. CO₂ уходит в атмосферу.
 */
public final class Co2AbsorberBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int MB = 16_000;

    private final ProductTank syngas = new ProductTank(PcFluids.SYNGAS.source.get(), MB);
    private final Purification purification = new Purification();
    @Nullable
    private Purification.Result last;
    private double heatW, flowMolS, ventedNm3;
    private long lastFeedTick = -1000;

    public Co2AbsorberBlockEntity(BlockPos pos, BlockState state) {
        super(NitrogenBlockEntities.CO2_ABSORBER.get(), pos, state);
    }

    /** Колонна по любой клетке конструкции. */
    @Nullable
    public static Co2AbsorberBlockEntity at(Level level, BlockPos cell) {
        BlockPos c = BoilerGeometry.controllerOf(level, cell);
        return c != null && level.getBlockEntity(c) instanceof Co2AbsorberBlockEntity u ? u : null;
    }

    public Purification purification() {
        return purification;
    }

    public ProductTank syngas() {
        return syngas;
    }

    @Nullable
    public Purification.Result lastResult() {
        return last;
    }

    public double ventedNm3() {
        return ventedNm3;
    }

    /** Газ после конверсии CO от печи (моль/с) и её технологическое тепло (Вт) на шаг {@code dt}. */
    void process(double[] gasMolS, double processHeatW, double dt) {
        Purification.Result r = purification.step(gasMolS, processHeatW);
        last = r;
        heatW = processHeatW;
        lastFeedTick = level.getGameTime();
        double nm3 = Species.total(r.syngas()) * dt / Species.MOL_PER_NM3;
        if (r.onSpec() && syngas.room() >= nm3) {
            syngas.add(nm3);
            flowMolS = Species.total(r.syngas());
        } else {
            ventedNm3 += nm3;
            flowMolS = 0;
        }
        setLit(r.onSpec());
    }

    private boolean idle() {
        return level == null || level.getGameTime() - lastFeedTick > 40;
    }

    @Override
    public void tickDevice() {
        if (level == null || level.isClientSide) return;
        if (idle() && level.getGameTime() % 20 == 0) {
            flowMolS = 0;
            setLit(false);
        }
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    private void setLit(boolean lit) {
        BlockState st = getBlockState();
        if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT) != lit)
            level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
    }

    // --- руки ---

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return syngas;
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

    /** Shift + ПКМ: сброс блокировки метанатора. */
    @Override
    public InteractionResult use(Player player) {
        resetTrip(player);
        return InteractionResult.SUCCESS;
    }

    private void resetTrip(Player player) {
        if (!purification.tripped()) return;
        purification.resetTrip();
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 1.0f);
        player.displayClientMessage(NitrogenText.msg("absorber.reset"), true);
        setChanged();
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return NitrogenPanels.ABSORBER;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        boolean idle = idle() || last == null;
        if (!idle) {
            out[NitrogenPanels.A_CO2_PPM] = (int) Math.round(Math.min(last.co2SlipDry(), 0.5) * 1e6);
            out[NitrogenPanels.A_OXY_PPM10] = (int) Math.round(Math.min(last.oxygenates(), 0.5) * 1e7);
            out[NitrogenPanels.A_METH_C] = (int) Math.round(last.methanatorOutK() - 273.15);
            out[NitrogenPanels.A_RATIO] = (int) Math.round(Math.min(last.ratio(), 99) * 100);
            out[NitrogenPanels.A_REGEN_KW] = (int) Math.round(last.regenW() / 1e3);
            out[NitrogenPanels.A_HEAT_KW] = (int) Math.round(heatW / 1e3);
        }
        out[NitrogenPanels.A_CIRC] = (int) Math.round(purification.circulation() * 100);
        out[NitrogenPanels.A_TANK] = (int) Math.round(syngas.fill() * 1000);
        out[NitrogenPanels.A_FLOW] = (int) Math.round(flowMolS / Species.MOL_PER_NM3 * 3600);
        boolean on = !idle && last.onSpec();
        out[NitrogenPanels.A_FLAGS] = (on ? 1 << NitrogenPanels.AF_ON_SPEC : 0) | (!idle && !on ? 1 << NitrogenPanels.AF_VENT : 0)
                | (purification.tripped() ? 1 << NitrogenPanels.AF_TRIP : 0) | (idle ? 1 << NitrogenPanels.AF_IDLE : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case NitrogenPanels.AB_CIRC_DOWN -> purification.setCirculation(purification.circulation() - 0.1);
            case NitrogenPanels.AB_CIRC_UP -> purification.setCirculation(purification.circulation() + 0.1);
            case NitrogenPanels.AB_RESET -> resetTrip(player);
            default -> {
            }
        }
        setChanged();
    }

    // --- сохранение ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        syngas.save(tag, "syngas");
        MutableStateTree tree = new MutableStateTree();
        purification.save(new TreeStateWriter(tree));
        tag.put("purification", NbtStateTrees.toNbt(tree));
        tag.putDouble("vented", ventedNm3);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        syngas.load(tag, "syngas");
        if (tag.contains("purification"))
            purification.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("purification"))));
        ventedNm3 = tag.getDouble("vented");
    }
}
