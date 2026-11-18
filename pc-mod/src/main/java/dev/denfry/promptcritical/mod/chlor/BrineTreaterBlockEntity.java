package dev.denfry.promptcritical.mod.chlor;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.chlor.BrineTreatment;
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
import dev.denfry.promptcritical.mod.fx.MachineFx;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Рассольное отделение ({@link BrineTreatment}) 3×2×3: растворитель соли, реакторы осаждения и осветлитель. Соль —
 * мешками по {@value ChlorUnits#SALT_BAG_KG} кг воронкой или рукой; вода, электролитическая щёлочь с ванн и дымовые
 * газы котельной (CO₂ на соду) — трубами к боковым граням. Очищенный рассол — снизу; если щёлочи или дымовых газов
 * меньше нормы, Ca и Mg остаются в растворе и выходит сырой рассол — он тоже течёт снизу, но забивает диафрагмы.
 */
public final class BrineTreaterBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int MB = 16_000;
    /** Производительность по рассолу, л/с [BALANCE: под серию 20 ванн на 30 кА — 2 л/с]. */
    public static final double CAPACITY_LS = 4;
    /** CO₂ в дымовых газах, объёмная доля; плотность CO₂, кг/нм³. */
    static final double FLUE_CO2 = 0.12, CO2_KG_NM3 = 1.977;
    /** Доля нормы реагентов, при которой рассол считается очищенным (Ca + Mg ≤ 5 мг/л). */
    static final double TREATED_AT = 0.99;

    private final ItemStackHandler salt = new ItemStackHandler(4) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(PcItems.SALT.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final FeedTank water = new FeedTank(Fluids.WATER, MB);
    private final FeedTank naoh = new FeedTank(PcFluids.SODIUM_HYDROXIDE_SOLUTION.source.get(), MB / 4);
    private final FeedTank flue = new FeedTank(PcFluids.FLUE_GAS.source.get(), MB / 4);
    private final IFluidHandler inlet = new MultiInlet(water, naoh, flue);
    private final ProductTank brine = new ProductTank(PcFluids.BRINE.source.get(), MB);
    private final ProductTank raw = new ProductTank(PcFluids.RAW_BRINE.source.get(), MB);
    private double saltKg, lastLs, lastHardness = BrineTreatment.RAW_HARDNESS_MG_L, lastReagent;

    public BrineTreaterBlockEntity(BlockPos pos, BlockState state) {
        super(ChlorBlockEntities.BRINE_TREATER.get(), pos, state);
    }

    public ItemStackHandler salt() {
        return salt;
    }

    public FeedTank water() {
        return water;
    }

    public FeedTank naoh() {
        return naoh;
    }

    public FeedTank flue() {
        return flue;
    }

    public ProductTank brine() {
        return brine;
    }

    public ProductTank rawBrine() {
        return raw;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) {
            clientTick();
            return;
        }
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        double dt = 0.05 * b.world().clock().kSlow();
        double litres = CAPACITY_LS * dt;
        litres = Math.min(litres, water.amount() / BrineTreatment.WATER_KG_PER_L);
        while (saltKg < litres * BrineTreatment.NACL_KG_PER_L && takeBag()) saltKg += ChlorUnits.SALT_BAG_KG;
        litres = Math.min(litres, saltKg / BrineTreatment.NACL_KG_PER_L);
        // реагенты: щёлочь на соду и на магний, CO₂ из дымовых газов на соду
        double naohNeedL = ChlorUnits.liquorLPerNaohKg(BrineTreatment.NAOH_KG_PER_L * litres);
        double flueNeed = BrineTreatment.CO2_KG_PER_L * litres / CO2_KG_NM3 / FLUE_CO2;
        double reagent = litres <= 0 ? 0 : Math.min(1, Math.min(naoh.amount() / naohNeedL, flue.amount() / flueNeed));
        boolean treated = reagent >= TREATED_AT;
        ProductTank out = treated ? brine : raw;
        litres = Math.min(litres, out.room());
        if (litres > 1e-9) {
            water.take(litres * BrineTreatment.WATER_KG_PER_L);
            saltKg -= litres * BrineTreatment.NACL_KG_PER_L;
            naoh.take(Math.min(naoh.amount(), ChlorUnits.liquorLPerNaohKg(BrineTreatment.NAOH_KG_PER_L * litres) * reagent));
            flue.take(Math.min(flue.amount(), BrineTreatment.CO2_KG_PER_L * litres / CO2_KG_NM3 / FLUE_CO2 * reagent));
            out.add(litres);
        } else litres = 0;
        lastLs = litres / dt;
        lastReagent = reagent;
        lastHardness = BrineTreatment.hardnessMgL(reagent);
        if (level.getGameTime() % 10 == 0) {
            BlockState st = getBlockState();
            boolean run = lastLs > 0;
            if (st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING) != run)
                level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, run), Block.UPDATE_CLIENTS);
        }
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    /**
     * Осветлитель работает: над барботёром дымовых газов лопаются пузыри, в сборный лоток переливается рассол,
     * со сливной трубы капает. Мост с граблями вращает рендер (ChlorClient). Точки —
     * art/blockbench/chlor_alkali/brine_treater.author.js.
     */
    private void clientTick() {
        BlockState st = getBlockState();
        if (!st.hasProperty(PcProperties.RUNNING) || !st.getValue(PcProperties.RUNNING)) return;
        MachineFx fx = MachineFx.of(level, worldPosition, st.getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (fx == null) return;
        if (fx.chance(6)) fx.bubbles(fx.at(0, 16.7, 19), 0.25, 2);
        if (fx.chance(2)) {
            double a = fx.random().nextDouble() * Math.PI * 2;
            fx.splash(fx.at(8 + 14.4 * Math.cos(a), 16.7, 8 + 14.4 * Math.sin(a)), 0.05, 1);
        }
        if (fx.chance(1)) fx.leak(fx.at(26, 1.2, 8));
    }

    private boolean takeBag() {
        for (int i = 0; i < salt.getSlots(); i++)
            if (!salt.extractItem(i, 1, false).isEmpty()) return true;
        return false;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == Direction.DOWN) return raw.amount() > 0 && brine.amount() <= 0 ? raw : brine;
        return inlet;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return salt;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.SALT.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!stack.is(PcItems.SALT.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack left = stack.copy();
        for (int i = 0; i < salt.getSlots() && !left.isEmpty(); i++) left = salt.insertItem(i, left, false);
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
        return ChlorPanels.BRINE;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return salt;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[ChlorPanels.B_LS_X100] = (int) Math.round(lastLs * 100);
        out[ChlorPanels.B_HARD] = (int) Math.round(lastHardness);
        out[ChlorPanels.B_REAGENT_PCT] = (int) Math.round(lastReagent * 100);
        out[ChlorPanels.B_TANK] = (int) Math.round(brine.fill() * 1000);
        out[ChlorPanels.B_RAW_TANK] = (int) Math.round(raw.fill() * 1000);
        out[ChlorPanels.B_WATER] = (int) Math.round(water.fill() * 1000);
        out[ChlorPanels.B_FLAGS] = (lastLs > 0 ? 1 << ChlorPanels.BF_RUN : 0) | (lastLs > 0 && lastReagent < TREATED_AT ? 1 << ChlorPanels.BF_RAW : 0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("salt", salt.serializeNBT(registries));
        water.save(tag, "water");
        naoh.save(tag, "naoh");
        flue.save(tag, "flue");
        brine.save(tag, "brine");
        raw.save(tag, "raw");
        tag.putDouble("salt_kg", saltKg);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("salt")) salt.deserializeNBT(registries, tag.getCompound("salt"));
        water.load(tag, "water");
        naoh.load(tag, "naoh");
        flue.load(tag, "flue");
        brine.load(tag, "brine");
        raw.load(tag, "raw");
        saltKg = tag.getDouble("salt_kg");
    }
}
