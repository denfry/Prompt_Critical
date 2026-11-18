package dev.denfry.promptcritical.mod.waste;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.waste.Conditioning;
import dev.denfry.promptcritical.sim.waste.LrwEvaporator;
import dev.denfry.promptcritical.sim.waste.ScalingFactors;
import dev.denfry.promptcritical.sim.waste.Vitrification;
import dev.denfry.promptcritical.sim.waste.WasteClassification;
import dev.denfry.promptcritical.sim.waste.WasteForm;
import dev.denfry.promptcritical.sim.waste.WasteInventory;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Узел переработки ЖРО ({@link LrwEvaporator}): выпарной аппарат с баком-сборником, а рядом (в {@value #REACH} блоках) —
 * аппараты отверждения кубового остатка, как на спецкорпусе АЭС:
 * <ul>
 *   <li>{@code cementation_mixer} — цементирование в бочке 200 л ({@link Conditioning#cement}); сюда же идут мешки
 *   отработанного ионита ({@link Conditioning#cementResin});</li>
 *   <li>{@code bitumenizer} — роторный битуматор ({@link Conditioning#bitumen}); вяжущее — мазут трубой: в аппарат
 *   встроено окисление воздухом в битум (так делают битум на НПЗ из гудрона), вода остатка выпаривается;</li>
 *   <li>{@code vitrification_melter} — если куб по удельной активности оказался ВАО (дезактивация после аварии),
 *   цемент и битум ему запрещены: только остекловывание в алюмофосфатное стекло (так ПО «Маяк» отверждает жидкие ВАО),
 *   стеклоблоки по {@value #CANISTER_KG} кг.</li>
 * </ul>
 * Загрузка (воронкой или рукой): цемент, пустые бочки, отработанная смола. ЖРО и мазут — трубой к боковым граням;
 * конденсат (почти чистая вода) — сверху; бочки и стеклоблоки — воронкой снизу. Shift + ПКМ — пуск/стоп.
 */
public final class LrwEvaporatorBlockEntity extends UraniumProcessBlockEntity {
    public static final int REACH = 4, IN_SLOTS = 6, OUT_SLOTS = 6, POOL_MB = 16_000;
    public static final double CEMENT_BAG_KG = 50, RESIN_BAG_L = 100, FUEL_OIL_KG_L = 0.95, DRUM_S = 1800, CANISTER_KG = 50;
    /** Масса оксидов на килограмм солей куба после прокалки (нитраты и бораты натрия теряют NOₓ и воду). */
    public static final double OXIDE_PER_SALT = 0.5;

    private final LrwPool pool = new LrwPool(POOL_MB);
    private final FeedTank binder = new FeedTank(PcFluids.FUEL_OIL.source.get(), 4000);
    private final ProductTank condensate = new ProductTank(Fluids.WATER, POOL_MB);
    private final IFluidHandler inlet = new Inlet();
    private final LrwEvaporator evaporator = new LrwEvaporator();
    private ActivityInventory resin = new ActivityInventory(Map.of(), 0, 1);
    private double resinL, busyS;
    private boolean cement, bitumen, melter;
    private int drums;
    private WasteClassification.Category category;

    public LrwEvaporatorBlockEntity(BlockPos pos, BlockState state) {
        super(WasteBlockEntities.LRW_EVAPORATOR.get(), pos, state, IN_SLOTS, OUT_SLOTS, LrwEvaporatorBlockEntity::loadable);
    }

    static boolean loadable(ItemStack s) {
        return s.is(PcItems.PORTLAND_CEMENT.get()) || s.is(PcItems.WASTE_DRUM.get()) && !s.has(ActivityData.ACTIVITY.get())
                || s.is(PcItems.SPENT_ION_EXCHANGE_RESIN.get());
    }

    public LrwEvaporator evaporator() {
        return evaporator;
    }

    public LrwPool pool() {
        return pool;
    }

    public ProductTank condensate() {
        return condensate;
    }

    public int drums() {
        return drums;
    }

    @Nullable
    public WasteClassification.Category category() {
        return category;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        if (level.getGameTime() % 40 == 0) scan(level);
        LrwEvaporator.Step st = evaporator.step(dt, pool.feed(), pool.litres(), condensate.room(), on);
        pool.take(st.feedL());
        condensate.add(st.condensateL());
        resin = resin.decayed(dt);
        if (level.getGameTime() % 20 == 0) category = classifyConcentrate();
        if (!on) return;
        busyS -= dt;
        if (busyS > 0) return;
        if (condition(level.getGameTime())) {
            busyS = DRUM_S;
            level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.4f, 0.7f);
        }
    }

    /** Категория куба сейчас (null — куб пуст); тепловыделяющий куб — ВАО при любой удельной активности. */
    @Nullable
    private WasteClassification.Category classifyConcentrate() {
        return evaporator.waterL() > 1 ? WasteClassification.classify(concentrate(), true).category() : null;
    }

    /** Состав куба для классификации: γ-излучатели плюс трудноизмеряемые эксплуатационных отходов. */
    WasteInventory concentrate() {
        double volM3 = (evaporator.waterL() + evaporator.saltKg() / Conditioning.SALT_KG_L) / 1000;
        return ScalingFactors.characterize(ScalingFactors.Stream.REACTOR_OPERATIONAL, evaporator.liquor(), volM3);
    }

    private void scan(ServerLevel level) {
        boolean c = false, b = false, m = false;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-REACH, -1, -REACH), worldPosition.offset(REACH, 1, REACH))) {
            Block blk = level.getBlockState(p).getBlock();
            if (blk == PcBlocks.CEMENTATION_MIXER.get()) c = true;
            else if (blk == PcBlocks.BITUMENIZER.get()) b = true;
            else if (blk == PcBlocks.VITRIFICATION_MELTER.get()) m = true;
        }
        cement = c;
        bitumen = b;
        melter = m;
    }

    /** Одна партия отверждения; true — что-то сделано. */
    private boolean condition(long now) {
        // мешки смолы — в буфер перед цементированием
        if (resinL < Conditioning.cementResin().feedL()) {
            ItemStack r = takeOne(s -> s.is(PcItems.SPENT_ION_EXCHANGE_RESIN.get()));
            if (!r.isEmpty()) {
                ActivityData a = r.get(ActivityData.ACTIVITY.get());
                if (a != null) resin = sum(resin, a.inventory(now));
                resinL += RESIN_BAG_L;
            }
        }
        // категорию — заново, а не из показания щита (оно раз в секунду): ВАО не должно успеть уйти в цемент
        category = classifyConcentrate();
        if (category == WasteClassification.Category.HLW) return melter && vitrify(now);
        if (cement && resinL >= Conditioning.cementResin().feedL() && cementResin(now)) return true;
        if (!evaporator.ready()) return false;
        if (cement) return cementConcentrate(now);
        if (bitumen) return bitumenConcentrate(now);
        return false;
    }

    private static ActivityInventory sum(ActivityInventory a, ActivityInventory b) {
        EnumMap<Emitter, Double> m = new EnumMap<>(a.activities());
        b.activities().forEach((k, v) -> m.merge(k, v, Double::sum));
        return new ActivityInventory(m, a.massKg() + b.massKg(), 1.1);
    }

    private boolean ready(Item product, int cementBags) {
        return hasRoom(product) && count(s -> s.is(PcItems.WASTE_DRUM.get()) && !s.has(ActivityData.ACTIVITY.get())) > 0
                && count(s -> s.is(PcItems.PORTLAND_CEMENT.get())) >= cementBags;
    }

    private void consume(int cementBags) {
        takeOne(s -> s.is(PcItems.WASTE_DRUM.get()) && !s.has(ActivityData.ACTIVITY.get()));
        for (int i = 0; i < cementBags; i++) takeOne(s -> s.is(PcItems.PORTLAND_CEMENT.get()));
    }

    private boolean cementResin(long now) {
        Conditioning.Recipe r = Conditioning.cementResin();
        int bags = (int) Math.ceil(r.binderKg() / CEMENT_BAG_KG);
        if (!ready(PcItems.CEMENTED_WASTE_DRUM.get(), bags)) return false;
        consume(bags);
        double share = r.feedL() / resinL;
        EnumMap<Emitter, Double> part = new EnumMap<>(Emitter.class), rest = new EnumMap<>(Emitter.class);
        resin.activities().forEach((k, v) -> {
            part.put(k, v * share);
            rest.put(k, v * (1 - share));
        });
        resin = new ActivityInventory(rest, resin.massKg() * (1 - share), 1.1);
        resinL -= r.feedL();
        drum(PcItems.CEMENTED_WASTE_DRUM.get(), new ActivityInventory(part, r.productKg(), r.productKg() / r.productL()), now, WasteForm.CEMENT,
                r.compliant(), r.productL());
        return true;
    }

    private boolean cementConcentrate(long now) {
        Conditioning.Recipe r = Conditioning.cement(evaporator.saltGL());
        int bags = (int) Math.ceil(r.binderKg() / CEMENT_BAG_KG);
        if (evaporator.waterL() < r.feedL() || !ready(PcItems.CEMENTED_WASTE_DRUM.get(), bags)) return false;
        consume(bags);
        WasteInventory part = evaporator.draw(r.feedL());
        drum(PcItems.CEMENTED_WASTE_DRUM.get(), new ActivityInventory(part.emitters().activities(), r.productKg(), r.productKg() / r.productL()),
                now, WasteForm.CEMENT, r.compliant(), r.productL());
        return true;
    }

    private boolean bitumenConcentrate(long now) {
        Conditioning.Recipe r = Conditioning.bitumen(evaporator.saltGL());
        double oilL = r.binderKg() / FUEL_OIL_KG_L;
        if (evaporator.waterL() < r.feedL() || binder.amount() < oilL || !ready(PcItems.BITUMEN_WASTE_DRUM.get(), 0)) return false;
        consume(0);
        binder.take(oilL);
        WasteInventory part = evaporator.draw(r.feedL());
        drum(PcItems.BITUMEN_WASTE_DRUM.get(), new ActivityInventory(part.emitters().activities(), r.productKg(), r.productKg() / r.productL()),
                now, WasteForm.BITUMEN, r.compliant(), r.productL());
        return true;
    }

    private boolean vitrify(long now) {
        Vitrification.Glass g = Vitrification.Glass.PHOSPHATE;
        double saltGL = evaporator.saltGL();
        if (saltGL <= 0 || !hasRoom(PcItems.HLW_GLASS_CANISTER.get())) return false;
        double litres = CANISTER_KG * g.maxLoading / OXIDE_PER_SALT / (saltGL / 1000);
        if (evaporator.waterL() < litres) return false;
        WasteInventory part = evaporator.draw(litres);
        ActivityInventory glass = new ActivityInventory(part.emitters().activities(), CANISTER_KG, g.form.densityKgL);
        boolean ok = Vitrification.centreK(g, glass.decayHeatW(), CANISTER_KG, 323.15) < g.limitK;
        ItemStack c = WastePackage.make(PcItems.HLW_GLASS_CANISTER.get(), glass, now, g.form, ScalingFactors.Stream.DECONTAMINATION, ok,
                CANISTER_KG / g.form.densityKgL);
        emit(c);
        drums++;
        return true;
    }

    private void drum(Item item, ActivityInventory gamma, long now, WasteForm form, boolean compliant, double volumeL) {
        emit(WastePackage.make(item, gamma, now, form, ScalingFactors.Stream.REACTOR_OPERATIONAL, compliant, volumeL));
        drums++;
    }

    @Override
    protected boolean running() {
        return on && (pool.litres() > 1 || busyS > 0);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return side == Direction.UP ? condensate : inlet;
    }

    @Override
    public PanelLayout panelLayout() {
        return WastePanels.EVAPORATOR;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[WastePanels.E_SUMP_L] = (int) Math.round(evaporator.waterL());
        v[WastePanels.E_SALT_GL] = (int) Math.round(evaporator.saltGL());
        v[WastePanels.E_POOL] = (int) Math.round(pool.fill() * 1000);
        v[WastePanels.E_COND] = (int) Math.round(condensate.fill() * 1000);
        v[WastePanels.E_COND_MBQ_L] = (int) Math.min(2e9, Math.round(evaporator.condensateBqPerL() * 1000));
        v[WastePanels.E_CAT] = category == null ? 0 : category.ordinal() + 1;
        v[WastePanels.E_DRUMS] = drums;
        v[WastePanels.E_FLAGS] = (on ? 1 << WastePanels.EF_ON : 0) | (running() ? 1 << WastePanels.EF_RUN : 0)
                | (evaporator.ready() ? 1 << WastePanels.EF_READY : 0) | (cement ? 1 << WastePanels.EF_CEMENT : 0)
                | (bitumen ? 1 << WastePanels.EF_BITUMEN : 0) | (melter ? 1 << WastePanels.EF_MELTER : 0)
                | (category == WasteClassification.Category.HLW && !melter ? 1 << WastePanels.EF_HLW : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == WastePanels.B_RUN) use(player);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        pool.save(tag, "pool");
        binder.save(tag, "binder");
        condensate.save(tag, "condensate");
        MutableStateTree t = new MutableStateTree();
        evaporator.save(new TreeStateWriter(t));
        tag.put("evaporator", NbtStateTrees.toNbt(t));
        tag.putDouble("resin_l", resinL);
        tag.putDouble("resin_kg", resin.massKg());
        CompoundTag r = new CompoundTag();
        resin.activities().forEach((k, val) -> r.putDouble(k.name(), val));
        tag.put("resin", r);
        tag.putDouble("busy", busyS);
        tag.putInt("drums", drums);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        pool.load(tag, "pool");
        binder.load(tag, "binder");
        condensate.load(tag, "condensate");
        if (tag.contains("evaporator")) evaporator.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("evaporator"))));
        resinL = tag.getDouble("resin_l");
        EnumMap<Emitter, Double> m = new EnumMap<>(Emitter.class);
        CompoundTag r = tag.getCompound("resin");
        for (String k : r.getAllKeys()) {
            try {
                m.put(Emitter.valueOf(k), r.getDouble(k));
            } catch (IllegalArgumentException ignored) {
                // нуклид из другой версии
            }
        }
        resin = new ActivityInventory(m, tag.getDouble("resin_kg"), 1.1);
        busyS = tag.getDouble("busy");
        drums = tag.getInt("drums");
    }

    /** Боковые патрубки: ЖРО — в бак-сборник, мазут — вяжущему битуматора. */
    private final class Inlet implements IFluidHandler {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? pool.getFluidInTank(0) : binder.getFluidInTank(0);
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? pool.getTankCapacity(0) : binder.getTankCapacity(0);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 ? pool.isFluidValid(0, stack) : binder.isFluidValid(0, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int n = pool.fill(resource, action);
            return n > 0 ? n : binder.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
