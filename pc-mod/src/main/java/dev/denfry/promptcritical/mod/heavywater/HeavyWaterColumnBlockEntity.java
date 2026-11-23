package dev.denfry.promptcritical.mod.heavywater;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.heavywater.HeavyWater;
import dev.denfry.promptcritical.sim.heavywater.HeavyWaterGrade;
import dev.denfry.promptcritical.sim.heavywater.HeavyWaterInventory;
import dev.denfry.promptcritical.sim.heavywater.IsotopeColumn;
import dev.denfry.promptcritical.sim.heavywater.WaterDistillationColumn;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Вакуумная колонна доводки тяжёлой воды ({@link WaterDistillationColumn}) 1×3×1. Вода 15 % D с сероводородного
 * завода и разбавленная D₂O со станции заливаются в куб, где смешиваются по атомам ({@link HeavyWaterInventory});
 * колонна перерабатывает куб со скоростью {@value WaterDistillationColumn#FEED_KG_S} кг/с и копит реакторную D₂O в
 * баке продукта. Обеднённый верхний продукт сбрасывается в канализацию. Слишком бедное питание (природная вода) колонна
 * не берёт: на 400 тарелках при α ≈ 1,05 из него реакторную воду не получить.
 */
public final class HeavyWaterColumnBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    /** Электрический кипятильник и вакуум-насос колонны, Вт (розетка или силовой щит рядом). */
    public static final double POWER_W = 100e3;
    public static final int STILL_MB = 16_000, MB = 16_000;

    private final WaterDistillationColumn column = new WaterDistillationColumn();
    private final HeavyWaterInventory still = new HeavyWaterInventory();
    private final ProductTank product = new ProductTank(PcFluids.HEAVY_WATER.source.get(), MB);
    private final GradedWaterPort port = new GradedWaterPort(new GradedWaterPort.Sink() {
        @Override
        public double roomKg() {
            return HeavyWater.kgFromLitres(STILL_MB, HeavyWaterGrade.REACTOR.x) - still.kg();
        }

        @Override
        public void add(double kg, double x) {
            still.add(kg, x);
            setChanged();
        }

        @Override
        public int shownMb() {
            return STILL_MB;
        }
    }, EnumSet.of(HeavyWaterGrade.ENRICHED, HeavyWaterGrade.DOWNGRADED), List.of(product));
    private boolean running = true, tooLean;
    private int ticks;

    public HeavyWaterColumnBlockEntity(BlockPos pos, BlockState state) {
        super(HeavyWaterBlockEntities.HEAVY_WATER_COLUMN.get(), pos, state);
    }

    public HeavyWaterInventory still() {
        return still;
    }

    public ProductTank product() {
        return product;
    }

    public WaterDistillationColumn column() {
        return column;
    }

    public void setRunning(boolean on) {
        running = on;
        setChanged();
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        ticks++;
        SimLevelBinding b = SimBindings.get(sl);
        double dt = 0.05 * (b == null ? 1 : b.world().clock().kSlow());
        boolean boiling = false;
        tooLean = still.kg() > 0 && !column.split(still.purity()).works();
        if (running && still.kg() > 0 && !tooLean) {
            double x = still.purity();
            IsotopeColumn.Split split = column.split(x);
            double feed = Math.min(still.kg(), WaterDistillationColumn.FEED_KG_S * dt * dev.denfry.promptcritical.mod.grid.MachinePower.supply(sl, worldPosition, POWER_W));
            double outKg = HeavyWater.kg(HeavyWater.moles(feed, x) * split.productPerFeed(), HeavyWaterGrade.REACTOR.x);
            double litres = HeavyWater.litres(outKg, HeavyWaterGrade.REACTOR.x);
            if (product.room() >= litres) {
                still.remove(feed);
                product.add(HeavyWater.litres(column.process(feed, x), HeavyWaterGrade.REACTOR.x));
                boiling = true;
            }
        }
        BlockState s = getBlockState();
        if (s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING) != boiling)
            level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, boiling), Block.UPDATE_ALL);
        if (ticks % 100 == 0) setChanged();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return port;
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
        return HeavyWaterPanels.COLUMN;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        double stillL = HeavyWater.litres(still.kg(), still.purity());
        out[HeavyWaterPanels.C_STILL] = (int) Math.round(Math.min(1, stillL / STILL_MB) * 1000);
        out[HeavyWaterPanels.C_STILL_L] = (int) Math.round(stillL);
        out[HeavyWaterPanels.C_PURITY_X100] = (int) Math.round(still.purity() * 10000);
        out[HeavyWaterPanels.C_RECOVERY_X100] = still.kg() > 0 ? (int) Math.round(column.split(still.purity()).recovery() * 10000) : 0;
        out[HeavyWaterPanels.C_TANK] = (int) Math.round(product.fill() * 1000);
        out[HeavyWaterPanels.C_MADE_L] = (int) Math.min(Integer.MAX_VALUE,
                Math.round(HeavyWater.litres(column.totalProductKg(), HeavyWaterGrade.REACTOR.x)));
        out[HeavyWaterPanels.C_FLAGS] = (running ? 1 << HeavyWaterPanels.CF_RUN : 0) | (tooLean ? 1 << HeavyWaterPanels.CF_TOO_LEAN : 0)
                | (product.room() < 1 ? 1 << HeavyWaterPanels.CF_FULL : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == HeavyWaterPanels.CB_RUN) {
            running = !running;
            player.displayClientMessage(HeavyWaterText.msg(running ? "column.start" : "column.stop"), true);
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        product.save(tag, "product");
        MutableStateTree tree = new MutableStateTree();
        still.save(new TreeStateWriter(tree), "still");
        tag.put("still", NbtStateTrees.toNbt(tree));
        tag.putBoolean("running", running);
        tag.putDouble("made_kg", column.totalProductKg());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        product.load(tag, "product");
        if (tag.contains("still")) still.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("still"))), "still");
        running = !tag.contains("running") || tag.getBoolean("running");
        column.restoreTotal(tag.getDouble("made_kg"));
    }
}
