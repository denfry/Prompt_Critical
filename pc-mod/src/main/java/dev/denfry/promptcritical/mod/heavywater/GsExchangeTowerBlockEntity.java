package dev.denfry.promptcritical.mod.heavywater;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.heavywater.GirdlerSulfidePlant;
import dev.denfry.promptcritical.sim.heavywater.HeavyWater;
import dev.denfry.promptcritical.sim.heavywater.HeavyWaterGrade;
import dev.denfry.promptcritical.sim.heavywater.IsotopeColumn;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Сероводородный завод тяжёлой воды ({@link GirdlerSulfidePlant}) 3×6×3. Питание — природная вода озера: блоки воды-
 * источника в квадрате 9×9 на три слоя вниз от основания; {@value #LAKE_BLOCKS} и больше дают полное питание 1 т/с,
 * меньше — пропорционально (трубы Create столько не прокачают, поэтому завод ставят на берегу — как Брюс на Гуроне).
 * Обеднённая вода уходит обратно в озеро. Продукт — вода 15 % D — в трубы с любой стороны. Время — по
 * {@code time.k_slow}, как у всех медленных процессов.
 */
public final class GsExchangeTowerBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int LAKE_BLOCKS = 32, LAKE_RADIUS = 4, MB = 16_000;

    private final GirdlerSulfidePlant plant = new GirdlerSulfidePlant();
    private final ProductTank product = new ProductTank(PcFluids.DEUTERIUM_ENRICHED_WATER.source.get(), MB);
    private boolean running;
    private int lake = -1, ticks;
    private double lastIntake;

    public GsExchangeTowerBlockEntity(BlockPos pos, BlockState state) {
        super(HeavyWaterBlockEntities.GS_EXCHANGE_TOWER.get(), pos, state);
    }

    public GirdlerSulfidePlant plant() {
        return plant;
    }

    public ProductTank product() {
        return product;
    }

    public boolean running() {
        return running;
    }

    public void setRunning(boolean on) {
        running = on;
        setChanged();
    }

    /** Блоков воды-источника вокруг основания (пересчёт раз в 5 с); −1 — ещё не считали. */
    public int lakeBlocks() {
        return lake;
    }

    /** Доля полного питания по озеру. */
    public double intake() {
        return lake <= 0 ? 0 : Math.min(1, lake / (double) LAKE_BLOCKS);
    }

    private int countLake(ServerLevel sl) {
        int n = 0;
        BlockPos base = worldPosition.below();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 0; dy++)
            for (int dx = -LAKE_RADIUS; dx <= LAKE_RADIUS; dx++)
                for (int dz = -LAKE_RADIUS; dz <= LAKE_RADIUS; dz++) {
                    p.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (sl.isLoaded(p) && sl.getFluidState(p).getType() == Fluids.WATER) n++;
                }
        return n;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        if (ticks++ % 100 == 0 || lake < 0) lake = countLake(sl);
        SimLevelBinding b = SimBindings.get(sl);
        double dt = 0.05 * (b == null ? 1 : b.world().clock().kSlow());
        lastIntake = 0;
        if (running && intake() > 0) {
            double feedKg = GirdlerSulfidePlant.FEED_KG_S * intake() * dt;
            IsotopeColumn.Split split = plant.split(HeavyWater.NATURAL_X);
            double x = HeavyWaterGrade.ENRICHED.x;
            double litres = split.works()
                    ? HeavyWater.litres(HeavyWater.kg(HeavyWater.moles(feedKg, HeavyWater.NATURAL_X) * split.productPerFeed(), x), x) : 0;
            // бак полон — завод на себя: питание не берётся
            if (litres > 0 && product.room() >= litres) {
                product.add(HeavyWater.litres(plant.process(feedKg, HeavyWater.NATURAL_X), HeavyWaterGrade.ENRICHED.x));
                lastIntake = intake();
            }
        }
        boolean shown = running && lastIntake > 0;
        BlockState s = getBlockState();
        if (s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING) != shown)
            level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, shown), Block.UPDATE_ALL);
        if (ticks % 100 == 0) setChanged();
    }

    /** D₂O в выработанной воде, кг. */
    public double d2oMadeKg() {
        double kg = plant.totalProductKg(), x = HeavyWaterGrade.ENRICHED.x;
        return HeavyWater.moles(kg, x) * x * HeavyWater.molarMassKg(1);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return product;
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
        return HeavyWaterPanels.GS;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[HeavyWaterPanels.G_INTAKE_PERMIL] = (int) Math.round(lastIntake * 1000);
        out[HeavyWaterPanels.G_HOT_C] = (int) Math.round(plant.hotK() - 273.15);
        out[HeavyWaterPanels.G_RECOVERY_X100] = (int) Math.round(plant.split(HeavyWater.NATURAL_X).recovery() * 10000);
        out[HeavyWaterPanels.G_TANK] = (int) Math.round(product.fill() * 1000);
        out[HeavyWaterPanels.G_D2O_KG] = (int) Math.min(Integer.MAX_VALUE, Math.round(d2oMadeKg()));
        out[HeavyWaterPanels.G_LAKE] = Math.max(0, lake);
        out[HeavyWaterPanels.G_FLAGS] = (running ? 1 << HeavyWaterPanels.GF_RUN : 0)
                | (lake >= 0 && lake < LAKE_BLOCKS ? 1 << HeavyWaterPanels.GF_NO_LAKE : 0)
                | (product.room() < 1 ? 1 << HeavyWaterPanels.GF_FULL : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case HeavyWaterPanels.GB_RUN -> {
                running = !running;
                player.displayClientMessage(HeavyWaterText.msg(running ? "gs.start" : "gs.stop"), true);
            }
            case HeavyWaterPanels.GB_HOT_DOWN -> plant.setHotK(plant.hotK() - 5);
            case HeavyWaterPanels.GB_HOT_UP -> plant.setHotK(plant.hotK() + 5);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        product.save(tag, "product");
        tag.putBoolean("running", running);
        tag.putDouble("hot_k", plant.hotK());
        tag.putDouble("feed_kg", plant.totalFeedKg());
        tag.putDouble("product_kg", plant.totalProductKg());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        product.load(tag, "product");
        running = tag.getBoolean("running");
        plant.restoreTotals(tag.getDouble("feed_kg"), tag.getDouble("product_kg"),
                tag.contains("hot_k") ? tag.getDouble("hot_k") : GirdlerSulfidePlant.HOT_K);
    }
}
