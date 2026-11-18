package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.item.ExtractOnlyItemHandler;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.radiation.ContaminationTransfer;
import dev.denfry.promptcritical.sim.radiation.DeconMethod;
import dev.denfry.promptcritical.sim.radiation.SurfaceContamination;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Ультразвуковая ванна дезактивации мелких деталей и инструмента: загрязнённые предметы — в загрузку (рукой или
 * воронкой), растворы — трубами в боковые грани: вода, лимонно-щавелевый и щелочно-перманганатный. Деталь
 * обрабатывается {@value #CYCLE_TICKS} тиков (10 мин игрового времени) самым сильным способом, на который хватает
 * растворов: перманганат и затем кислота ({@link DeconMethod#ULTRASONIC_PERMANGANATE}), одна кислота
 * ({@link DeconMethod#ULTRASONIC_CITRIC_OXALIC}), вода ({@link DeconMethod#ULTRASONIC_WATER}), — и уходит в выгрузку
 * (снизу воронкой). Отработанный раствор — {@link DeconMethod#BATH_L_PER_ITEM} л каждой ванны на деталь — жидкие
 * радиоактивные отходы, снизу трубой; снятая активность учитывается в баке отходов.
 */
public final class UltrasonicBathBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int IN = 4, OUT = 4, SOLUTION_MB = 8000, WASTE_MB = 16000, CYCLE_TICKS = 160;

    private final ItemStackHandler input = new ItemStackHandler(IN) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return accepts(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ItemStackHandler output = new ItemStackHandler(OUT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler outputView = new ExtractOnlyItemHandler(output);
    private final IItemHandlerModifiable panelItems = new CombinedInvWrapper(input, output);
    private final FeedTank water = new FeedTank(Fluids.WATER, SOLUTION_MB);
    private final FeedTank citric = new FeedTank(PcFluids.DECON_SOLUTION.source.get(), SOLUTION_MB);
    private final FeedTank permanganate = new FeedTank(PcFluids.DECON_PERMANGANATE.source.get(), SOLUTION_MB);
    private final IFluidHandler inlet = new MultiInlet(water, citric, permanganate);
    private final ProductTank waste = new ProductTank(PcFluids.CONTAMINATED_DECON_SOLUTION.source.get(), WASTE_MB);
    private int progress;
    private double wasteBq, lastDf;
    @Nullable
    private DeconMethod lastMethod;

    public UltrasonicBathBlockEntity(BlockPos pos, BlockState state) {
        super(RadiationBlockEntities.ULTRASONIC_BATH.get(), pos, state);
    }

    public ItemStackHandler input() {
        return input;
    }

    public ItemStackHandler output() {
        return output;
    }

    public ProductTank waste() {
        return waste;
    }

    public double wasteBq() {
        return wasteBq;
    }

    public double lastDf() {
        return lastDf;
    }

    @Nullable
    public DeconMethod lastMethod() {
        return lastMethod;
    }

    /**
     * Самая сильная обработка, на которую хватит растворов; {@code null} — нечем мыть. Окислительно-восстановительная —
     * две ванны подряд: перманганат, затем кислота, по {@link DeconMethod#BATH_L_PER_ITEM} л каждого.
     */
    @Nullable
    DeconMethod method() {
        double l = DeconMethod.BATH_L_PER_ITEM;
        if (permanganate.amount() >= l && citric.amount() >= l) return DeconMethod.ULTRASONIC_PERMANGANATE;
        if (citric.amount() >= l) return DeconMethod.ULTRASONIC_CITRIC_OXALIC;
        if (water.amount() >= l) return DeconMethod.ULTRASONIC_WATER;
        return null;
    }

    /** Литров раствора (и отходов) на деталь для способа. */
    private static double litres(DeconMethod m) {
        return m == DeconMethod.ULTRASONIC_PERMANGANATE ? 2 * DeconMethod.BATH_L_PER_ITEM : DeconMethod.BATH_L_PER_ITEM;
    }

    private void consume(DeconMethod m) {
        double l = DeconMethod.BATH_L_PER_ITEM;
        switch (m) {
            case ULTRASONIC_PERMANGANATE -> {
                permanganate.take(l);
                citric.take(l);
            }
            case ULTRASONIC_CITRIC_OXALIC -> citric.take(l);
            default -> water.take(l);
        }
    }

    private int loaded() {
        for (int i = 0; i < IN; i++) if (!input.getStackInSlot(i).isEmpty()) return i;
        return -1;
    }

    public boolean running() {
        DeconMethod m = method();
        return loaded() >= 0 && m != null && waste.room() >= litres(m);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        if (!running()) {
            progress = 0;
            return;
        }
        progress++;
        if (progress % 10 == 0) {
            var c = worldPosition.getCenter();
            s.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y + 0.4, c.z, 4, 0.3, 0.05, 0.3, 0.01);
            if (progress % 40 == 0) s.playSound(null, worldPosition, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.4f, 1.8f);
        }
        if (progress >= CYCLE_TICKS) {
            progress = 0;
            finishOne();
        }
    }

    /** Обработать одну деталь из загрузки; false — нечем, некуда или нечего. */
    boolean finishOne() {
        int slot = loaded();
        DeconMethod m = method();
        if (slot < 0 || m == null || waste.room() < litres(m)) return false;
        ItemStack one = input.getStackInSlot(slot).copyWithCount(1);
        SurfaceContamination before = ContaminationData.get(one), after = before.after(m);
        ContaminationData.set(one, after);
        ItemStack left = one;
        for (int i = 0; i < OUT && !left.isEmpty(); i++) left = output.insertItem(i, left, true);
        if (!left.isEmpty()) return false;
        for (int i = 0; i < OUT && !one.isEmpty(); i++) one = output.insertItem(i, one, false);
        input.extractItem(slot, 1, false);
        consume(m);
        waste.add(litres(m));
        wasteBq += (before.total() - after.total()) * ContaminationTransfer.ITEM_CM2;
        lastDf = after.total() > 0 ? before.total() / after.total() : 0;
        lastMethod = m;
        setChanged();
        return true;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return side == Direction.DOWN ? outputView : input;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return side == Direction.DOWN ? waste : inlet;
    }

    /** Деталь с загрязнением; чистые мыть незачем. */
    @Override
    public boolean accepts(ItemStack stack) {
        return stack.has(ContaminationData.TYPE.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack left = stack.copy();
        for (int i = 0; i < IN && !left.isEmpty(); i++) left = input.insertItem(i, left, false);
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
    public IItemHandlerModifiable panelItems() {
        return panelItems;
    }

    @Override
    public PanelLayout panelLayout() {
        return DeconPanels.BATH;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[DeconPanels.WATER] = (int) Math.round(water.fill() * 1000);
        v[DeconPanels.CITRIC] = (int) Math.round(citric.fill() * 1000);
        v[DeconPanels.KMNO4] = (int) Math.round(permanganate.fill() * 1000);
        v[DeconPanels.WASTE] = (int) Math.round(waste.fill() * 1000);
        v[DeconPanels.PROGRESS] = progress * 1000 / CYCLE_TICKS;
        v[DeconPanels.LAST_DF] = (int) Math.round(Math.min(1e6, lastDf) * 10);
        v[DeconPanels.WASTE_KBQ] = (int) Math.round(Math.min(2e9, wasteBq / 1e3));
        v[DeconPanels.METHOD] = lastMethod == null ? -1 : lastMethod.ordinal();
        v[DeconPanels.FLAGS] = (running() ? 1 << DeconPanels.F_RUN : 0) | (method() == null ? 1 << DeconPanels.F_DRY : 0)
                | (waste.room() < 2 * DeconMethod.BATH_L_PER_ITEM ? 1 << DeconPanels.F_FULL : 0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("in", input.serializeNBT(registries));
        tag.put("out", output.serializeNBT(registries));
        water.save(tag, "water");
        citric.save(tag, "citric");
        permanganate.save(tag, "kmno4");
        waste.save(tag, "waste");
        tag.putInt("progress", progress);
        tag.putDouble("waste_bq", wasteBq);
        tag.putDouble("df", lastDf);
        if (lastMethod != null) tag.putString("method", lastMethod.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("in")) input.deserializeNBT(registries, tag.getCompound("in"));
        if (tag.contains("out")) output.deserializeNBT(registries, tag.getCompound("out"));
        water.load(tag, "water");
        citric.load(tag, "citric");
        permanganate.load(tag, "kmno4");
        waste.load(tag, "waste");
        progress = tag.getInt("progress");
        wasteBq = tag.getDouble("waste_bq");
        lastDf = tag.getDouble("df");
        try {
            lastMethod = tag.contains("method") ? DeconMethod.valueOf(tag.getString("method")) : null;
        } catch (IllegalArgumentException e) {
            lastMethod = null;
        }
    }
}
