package dev.denfry.promptcritical.mod.methanol;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.methanol.MethanolColumn;
import dev.denfry.promptcritical.sim.methanol.MethanolReformer;
import dev.denfry.promptcritical.sim.nitrogen.Species;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
 * Колонны ректификации метанола 1×3×1 ({@link MethanolColumn}): предварительная и основная. Сырой метанол и пар для
 * кипятильников приходят от метанольного агрегата, которого колонна касается. Метанол марки AA (1 мБ = 1 л, 0,791 кг)
 * — трубой из любой грани; это тот же {@code methanol}, что газопереработка берёт ингибитором гидратов. Кубовая вода
 * уходит на очистку. Если продукт не в марке (мало флегмы или пара), колонна работает «на себя» и метанол не отдаёт.
 */
public final class MethanolColumnBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int MB = 8_000;
    public static final double METHANOL_KG_L = 0.791, CRUDE_HOLDUP_MOL = 6000;
    /** Номинальная производительность по метанолу, моль/с — с запасом над агрегатом. */
    public static final double RATED_MEOH_MOL_S = 1.5 * MethanolReformer.RATED_CH4_MOL_S;

    private final ProductTank methanol = new ProductTank(PcFluids.METHANOL.source.get(), MB);
    private final MethanolColumn column = new MethanolColumn();
    private double crudeMeoh, crudeWater, heatW, feedKgS, productKgS;
    private boolean running = true, heatLimited;
    @Nullable
    private MethanolColumn.Result last;

    public MethanolColumnBlockEntity(BlockPos pos, BlockState state) {
        super(MethanolBlockEntities.METHANOL_COLUMN.get(), pos, state);
    }

    /** Колонна, которой принадлежит клетка {@code pos} (контроллер или часть). */
    @Nullable
    public static MethanolColumnBlockEntity at(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        BlockPos anchor = s.getBlock() instanceof MultiblockPartBlock ? MultiblockPartBlock.anchor(s, pos) : pos;
        BlockPos c = PcBlock.structureController(level, anchor);
        if (c == null) c = anchor;
        return level.getBlockEntity(c) instanceof MethanolColumnBlockEntity m ? m : null;
    }

    public MethanolColumn column() {
        return column;
    }

    public ProductTank methanol() {
        return methanol;
    }

    public double crudeMethanolMol() {
        return crudeMeoh;
    }

    @Nullable
    public MethanolColumn.Result lastResult() {
        return last;
    }

    public boolean running() {
        return running;
    }

    public void setRunning(boolean r) {
        running = r;
        setChanged();
    }

    /**
     * Сырец от агрегата: метанол и вода, моль; тепло кипятильникам, Вт (действует до следующей передачи). Возвращает,
     * сколько метанола (моль) колонна приняла.
     */
    double acceptCrude(double meohMol, double waterMol, double heatAvailableW) {
        heatW = heatAvailableW;
        double take = Math.max(0, Math.min(meohMol, CRUDE_HOLDUP_MOL - crudeMeoh));
        if (take <= 0) return 0;
        crudeMeoh += take;
        crudeWater += waterMol * take / meohMol;
        return take;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        double dt = 0.05 * b.world().clock().kSlow();
        feedKgS = productKgS = 0;
        heatLimited = false;
        if (running && crudeMeoh > 1e-6) {
            // пар решает, сколько сырца колонна переработает при заданной флегме
            double byHeat = heatW / ((1 + column.reflux()) * MethanolColumn.LATENT_J_MOL * (1 - MethanolColumn.TOPPING_LOSS)
                    * MethanolColumn.RECOVERY);
            double rate = Math.min(RATED_MEOH_MOL_S, byHeat);
            heatLimited = byHeat < RATED_MEOH_MOL_S && crudeMeoh > rate * dt;
            double m = Math.min(crudeMeoh, rate * dt);
            double w = crudeWater * m / crudeMeoh;
            MethanolColumn.Result r = m > 0 ? column.run(m / dt, w / dt, heatW) : MethanolColumn.Result.IDLE;
            last = r;
            double productL = r.productKgS() * dt / METHANOL_KG_L;
            if (m > 0 && r.gradeAa() && methanol.room() >= productL) {
                crudeMeoh -= m;
                crudeWater -= w;
                methanol.add(productL);
                feedKgS = (m * Species.CH3OH.molarMass() + w * Species.H2O.molarMass()) / dt;
                productKgS = r.productKgS();
            }
        }
        heatW *= 0.98; // пар агрегата приходит раз в несколько тиков; без него кипятильники остывают
        if (level.getGameTime() % 10 == 0) setLit(productKgS > 0);
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
        return methanol;
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

    /** Shift + ПКМ: пуск или останов. */
    @Override
    public InteractionResult use(Player player) {
        toggle(player);
        return InteractionResult.SUCCESS;
    }

    private void toggle(Player player) {
        running = !running;
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, 1.0f);
        player.displayClientMessage(MethanolText.msg(running ? "column.start" : "column.stop"), true);
        setChanged();
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return MethanolPanels.COLUMN;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        MethanolColumn.Result r = last != null ? last : MethanolColumn.Result.IDLE;
        out[MethanolPanels.C_WATER_PPM] = (int) Math.round(Math.min(r.waterWt(), 0.5) * 1e6);
        out[MethanolPanels.C_REFLUX] = (int) Math.round(column.reflux() * 10);
        out[MethanolPanels.C_REFLUX_ACT] = (int) Math.round(r.refluxActual() * 10);
        out[MethanolPanels.C_FEED_KGH] = (int) Math.round(feedKgS * 3600);
        out[MethanolPanels.C_PRODUCT_KGH] = (int) Math.round(productKgS * 3600);
        out[MethanolPanels.C_HEAT_KW] = (int) Math.round(Math.min(heatW, r.reboilerW()) / 1e3);
        out[MethanolPanels.C_NEED_KW] = (int) Math.round((1 + column.reflux()) * r.methanolMolS() * MethanolColumn.LATENT_J_MOL / 1e3);
        out[MethanolPanels.C_CRUDE] = (int) Math.round(crudeMeoh / CRUDE_HOLDUP_MOL * 1000);
        out[MethanolPanels.C_TANK] = (int) Math.round(methanol.fill() * 1000);
        out[MethanolPanels.C_FLAGS] = (productKgS > 0 ? 1 << MethanolPanels.CF_RUN : 0)
                | (running && last != null && !r.gradeAa() ? 1 << MethanolPanels.CF_OFF_SPEC : 0)
                | (heatLimited ? 1 << MethanolPanels.CF_NO_HEAT : 0)
                | (crudeMeoh <= 1e-6 ? 1 << MethanolPanels.CF_NO_CRUDE : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case MethanolPanels.CB_RUN -> toggle(player);
            case MethanolPanels.CB_REFLUX_DOWN -> column.setReflux(column.reflux() - 0.1);
            case MethanolPanels.CB_REFLUX_UP -> column.setReflux(column.reflux() + 0.1);
            default -> {
            }
        }
        setChanged();
    }

    // --- сохранение ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        methanol.save(tag, "methanol");
        MutableStateTree tree = new MutableStateTree();
        column.save(new TreeStateWriter(tree));
        tag.put("column", NbtStateTrees.toNbt(tree));
        tag.putDouble("crude_meoh", crudeMeoh);
        tag.putDouble("crude_water", crudeWater);
        tag.putBoolean("running", running);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        methanol.load(tag, "methanol");
        if (tag.contains("column")) column.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("column"))));
        crudeMeoh = tag.getDouble("crude_meoh");
        crudeWater = tag.getDouble("crude_water");
        running = !tag.contains("running") || tag.getBoolean("running");
    }
}
