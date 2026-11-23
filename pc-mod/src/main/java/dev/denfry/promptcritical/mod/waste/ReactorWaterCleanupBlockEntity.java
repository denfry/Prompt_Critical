package dev.denfry.promptcritical.mod.waste;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.npp.BwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.PowerReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.PwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.item.ExtractOnlyItemHandler;
import dev.denfry.promptcritical.sim.npp.BoilingWaterReactor;
import dev.denfry.promptcritical.sim.npp.CanduReactor;
import dev.denfry.promptcritical.sim.npp.PressurizedWaterReactor;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.reactor.ChannelReactor;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Система очистки теплоносителя первого контура (СВО-1/СВО-2 ВВЭР, RWCU кипящего реактора, система очистки
 * воды канального реактора): механический фильтр и ионообменный фильтр смешанного действия на байпасе контура. Ставится в
 * {@value #LINK_RADIUS} блоках от реактора (канальный, ВВЭР, кипящий, CANDU; у натриевого реактора вместо этого —
 * холодные ловушки) и берёт с него тепловую мощность.
 *
 * <p>Нормы — эксплуатационные порядки АЭС с водо-водяными реакторами (IAEA TRS-408, 2002, «Application of Ion Exchange
 * Processes for the Treatment of Radioactive Waste and Management of Spent Ion Exchangers»):
 * <ul>
 *   <li>на ионит и фильтры за год садится ≈ {@value #CAPTURE_BQ_Y_MW} Бк на МВт тепловой мощности — продукты коррозии
 *   (Co-60, половина), цезий и иод; 90 % — на ионите, 10 % — на фильтре (взвесь);</li>
 *   <li>каждая разгерметизированная ТВС прибавляет к цезию и иоду ещё по столько же (выход ПД через дефект);</li>
 *   <li>ионита расходуется ≈ {@value #RESIN_L_MW_Y} л на МВт·год — мешок 100 л служит {@value #RESIN_MW_Y} МВт·лет;
 *   фильтрующий патрон — {@value #FILTER_MW_Y} МВт·лет (меняют по перепаду давления);</li>
 *   <li>трапных вод и регенерационных растворов — ≈ {@value #LRW_M3_MW_Y} м³ на МВт·год: они уходят трубой в
 *   {@code liquid_radioactive_waste}.</li>
 * </ul>
 * Свежий ионит — мешки анионита (в слот 0), свежие патроны — в слот 1; отработанные (мешок
 * {@code spent_ion_exchange_resin} и патрон с активностью) выходят в склад снизу сами по исчерпании или по кнопке.
 */
public final class ReactorWaterCleanupBlockEntity extends DeviceBlockEntity implements dev.denfry.promptcritical.mod.environment.SelfEmittingSource, FoundryDevice, PanelHost {
    /** Насосы и подогреватели установки очистки; при недостатке поток через фильтры меньше, Вт (розетка или силовой щит рядом). */
    public static final double POWER_W = 75e3;
    public static final int LINK_RADIUS = 8, RESIN_SLOT = 0, FILTER_SLOT = 1, OUT_SLOTS = 4, LRW_MB = 8000;
    public static final double CAPTURE_BQ_Y_MW = 1e10, RESIN_SHARE = 0.9, RESIN_L_MW_Y = 3, RESIN_MW_Y = 100 / RESIN_L_MW_Y,
            FILTER_MW_Y = 25, LRW_M3_MW_Y = 1.5, RESIN_BAG_KG = 80, FILTER_KG = 10, YEAR_S = 365.25 * 86_400;
    /** Доли захвата по нуклидам (сумма 1). */
    static final Map<Emitter, Double> SPLIT = Map.of(Emitter.CO60, 0.5, Emitter.CS137, 0.3, Emitter.CS134, 0.15, Emitter.I131, 0.05);

    private final ItemStackHandler fresh = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == RESIN_SLOT ? stack.is(PcItems.ION_EXCHANGE_RESIN_SBA.get())
                    : stack.is(PcItems.FILTER_CARTRIDGE.get()) && !stack.has(ActivityData.ACTIVITY.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ItemStackHandler spent = new ItemStackHandler(OUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler spentView = new ExtractOnlyItemHandler(spent);
    private final IItemHandlerModifiable panelItems = new CombinedInvWrapper(fresh, spent);
    private final ProductTank lrw = new ProductTank(PcFluids.LIQUID_RADIOACTIVE_WASTE.source.get(), LRW_MB);
    private ActivityInventory resinBed = new ActivityInventory(Map.of(), 0, 1), filterBed = new ActivityInventory(Map.of(), 0, 1);
    private boolean resinIn, filterIn;
    private double resinUse, filterUse, thermalW;
    private int failed;
    @Nullable
    private BlockPos reactorPos;

    public ReactorWaterCleanupBlockEntity(BlockPos pos, BlockState state) {
        super(WasteBlockEntities.REACTOR_WATER_CLEANUP.get(), pos, state);
    }

    public ActivityInventory resinBed() {
        return resinBed;
    }

    public ActivityInventory filterBed() {
        return filterBed;
    }

    public ItemStackHandler fresh() {
        return fresh;
    }

    public ItemStackHandler spent() {
        return spent;
    }

    public ProductTank lrw() {
        return lrw;
    }

    public double thermalW() {
        return thermalW;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        long now = level.getGameTime();
        if (now % 200 == 0 || reactorPos != null && now % 20 == 0 && level.getBlockEntity(reactorPos) == null) reactorPos = findReactor(s);
        if (now % 20 != 0) return;
        double dtS = 20 * ActivityData.GAME_S_PER_TICK;
        thermalW = 0;
        failed = 0;
        if (reactorPos != null) read(level.getBlockEntity(reactorPos));
        load();
        capture(thermalW, failed, dtS * dev.denfry.promptcritical.mod.grid.MachinePower.supply(s, worldPosition, reactorPos != null ? POWER_W : 0));
        BlockState st = getBlockState();
        boolean run = thermalW > 0;
        if (st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING) != run)
            level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, run), Block.UPDATE_CLIENTS);
        if (resinBed.totalBq() + filterBed.totalBq() > 0) RadiationService.source(s, worldPosition.getCenter(), resinBed.source().plus(filterBed.source()));
        if (now % 100 == 0) setChanged();
    }

    @Nullable
    private BlockPos findReactor(ServerLevel s) {
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-LINK_RADIUS, -LINK_RADIUS, -LINK_RADIUS),
                worldPosition.offset(LINK_RADIUS, LINK_RADIUS, LINK_RADIUS))) {
            BlockEntity be = s.getBlockEntity(p);
            if (be instanceof PowerReactorBlockEntity || be instanceof PwrReactorBlockEntity || be instanceof BwrReactorBlockEntity
                    || be instanceof CanduReactorBlockEntity) return p.immutable();
        }
        return null;
    }

    private void read(@Nullable BlockEntity be) {
        if (be instanceof PowerReactorBlockEntity r) thermalW = r.reactor().power() * ChannelReactor.NOMINAL_W;
        else if (be instanceof PwrReactorBlockEntity r) {
            thermalW = r.reactor().power() * PressurizedWaterReactor.NOMINAL_W;
            failed = r.reactor().failedAssemblies();
        } else if (be instanceof BwrReactorBlockEntity r) thermalW = r.reactor().power() * BoilingWaterReactor.NOMINAL_W;
        else if (be instanceof CanduReactorBlockEntity r) thermalW = r.reactor().power() * CanduReactor.NOMINAL_W;
        thermalW = Math.max(0, thermalW);
    }

    /** Заправить ионит и патрон со склада, если гнёзда пусты. */
    private void load() {
        if (!resinIn && !fresh.extractItem(RESIN_SLOT, 1, false).isEmpty()) {
            resinIn = true;
            resinUse = 0;
        }
        if (!filterIn && !fresh.extractItem(FILTER_SLOT, 1, false).isEmpty()) {
            filterIn = true;
            filterUse = 0;
        }
    }

    /**
     * Захват активности за {@code dtS} секунд при тепловой мощности {@code w}, Вт, и {@code failedAssemblies}
     * негерметичных ТВС; исчерпанные ионит и патрон выгружаются.
     */
    public void capture(double w, int failedAssemblies, double dtS) {
        resinBed = resinBed.decayed(dtS);
        filterBed = filterBed.decayed(dtS);
        double mwY = w / 1e6 * dtS / YEAR_S;
        if (mwY <= 0) return;
        double total = CAPTURE_BQ_Y_MW * mwY;
        EnumMap<Emitter, Double> got = new EnumMap<>(Emitter.class);
        SPLIT.forEach((n, f) -> got.put(n, total * f * (n == Emitter.CO60 ? 1 : 1 + failedAssemblies)));
        if (resinIn) resinBed = add(resinBed, got, RESIN_SHARE, RESIN_BAG_KG);
        if (filterIn) filterBed = add(filterBed, got, 1 - RESIN_SHARE, FILTER_KG);
        lrw.add(LRW_M3_MW_Y * mwY * 1000);
        if (resinIn) resinUse += mwY / RESIN_MW_Y;
        if (filterIn) filterUse += mwY / FILTER_MW_Y;
        if (resinUse >= 1) changeResin();
        if (filterUse >= 1) changeFilter();
    }

    private static ActivityInventory add(ActivityInventory bed, Map<Emitter, Double> got, double share, double massKg) {
        EnumMap<Emitter, Double> m = new EnumMap<>(bed.activities());
        got.forEach((n, v) -> m.merge(n, v * share, Double::sum));
        return new ActivityInventory(m, massKg, 1.1);
    }

    /** Выгрузить ионит в мешок с активностью; false — некуда. */
    public boolean changeResin() {
        if (!resinIn || !out(PcItems.SPENT_ION_EXCHANGE_RESIN.get(), new ActivityInventory(resinBed.activities(), RESIN_BAG_KG, 1.1))) return false;
        resinIn = false;
        resinBed = new ActivityInventory(Map.of(), 0, 1);
        resinUse = 0;
        load();
        setChanged();
        return true;
    }

    /** Выгрузить фильтрующий патрон с активностью; false — некуда. */
    public boolean changeFilter() {
        if (!filterIn || !out(PcItems.FILTER_CARTRIDGE.get(), new ActivityInventory(filterBed.activities(), FILTER_KG, 0.6))) return false;
        filterIn = false;
        filterBed = new ActivityInventory(Map.of(), 0, 1);
        filterUse = 0;
        load();
        setChanged();
        return true;
    }

    private boolean out(Item item, ActivityInventory inv) {
        ItemStack s = new ItemStack(item);
        s.set(ActivityData.ACTIVITY.get(), ActivityData.of(inv, level != null ? level.getGameTime() : 0));
        for (int i = 0; i < OUT_SLOTS; i++) {
            if (spent.getStackInSlot(i).isEmpty()) {
                spent.setStackInSlot(i, s);
                return true;
            }
        }
        return false;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return side == Direction.DOWN ? spentView : fresh;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return lrw;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return panelItems;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return fresh.isItemValid(RESIN_SLOT, stack) || fresh.isItemValid(FILTER_SLOT, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int slot = fresh.isItemValid(RESIN_SLOT, stack) ? RESIN_SLOT : FILTER_SLOT;
        ItemStack left = fresh.insertItem(slot, stack.copy(), false);
        int moved = stack.getCount() - left.getCount();
        if (moved == 0) return ItemInteractionResult.FAIL;
        if (!player.isCreative()) stack.shrink(moved);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public PanelLayout panelLayout() {
        return WastePanels.CLEANUP;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[WastePanels.W_MW] = (int) Math.round(thermalW / 1e5);
        v[WastePanels.W_RESIN_GBQ] = (int) Math.min(2e9, Math.round(resinBed.totalBq() / 1e9 * 100));
        v[WastePanels.W_FILTER_GBQ] = (int) Math.min(2e9, Math.round(filterBed.totalBq() / 1e9 * 100));
        v[WastePanels.W_RESIN_USE] = (int) Math.round(resinUse * 1000);
        v[WastePanels.W_FILTER_USE] = (int) Math.round(filterUse * 1000);
        v[WastePanels.W_LRW] = (int) Math.round(lrw.fill() * 1000);
        v[WastePanels.W_FLAGS] = (reactorPos != null ? 1 << WastePanels.WF_LINK : 0) | (resinIn ? 1 << WastePanels.WF_RESIN : 0)
                | (filterIn ? 1 << WastePanels.WF_FILTER : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == WastePanels.B_RESIN) changeResin();
        else if (id == WastePanels.B_FILTER) changeFilter();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fresh", fresh.serializeNBT(registries));
        tag.put("spent", spent.serializeNBT(registries));
        lrw.save(tag, "lrw");
        tag.put("resin_bed", WasteNbt.activities(resinBed));
        tag.put("filter_bed", WasteNbt.activities(filterBed));
        tag.putBoolean("resin_in", resinIn);
        tag.putBoolean("filter_in", filterIn);
        tag.putDouble("resin_use", resinUse);
        tag.putDouble("filter_use", filterUse);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fresh")) fresh.deserializeNBT(registries, tag.getCompound("fresh"));
        if (tag.contains("spent")) spent.deserializeNBT(registries, tag.getCompound("spent"));
        lrw.load(tag, "lrw");
        resinBed = WasteNbt.activities(tag.getCompound("resin_bed"));
        filterBed = WasteNbt.activities(tag.getCompound("filter_bed"));
        resinIn = tag.getBoolean("resin_in");
        filterIn = tag.getBoolean("filter_in");
        resinUse = tag.getDouble("resin_use");
        filterUse = tag.getDouble("filter_use");
    }
}
