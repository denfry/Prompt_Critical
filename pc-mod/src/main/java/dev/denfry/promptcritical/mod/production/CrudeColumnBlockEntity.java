package dev.denfry.promptcritical.mod.production;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.refining.AtmosphericUnit;
import dev.denfry.promptcritical.sim.refining.AtmosphericUnit.Product;
import dev.denfry.promptcritical.sim.refining.CrudeAssay;
import java.util.Arrays;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Атмосферная колонна ({@link AtmosphericUnit}): куб (этот блок, 2 в высоту), над ним тарельчатые секции и верх. На
 * каждую двухметровую секцию — {@link #STAGES_PER_SECTION} теоретические тарелки (три тарелки через 0,6 м при КПД
 * около 0,7), все тарелки делятся поровну между тремя участками разделения. Нефть приходит из трубчатой печи в
 * {@link #FURNACE_RADIUS} блоках.
 *
 * <p>Продукты — из баков на высоте отбора, трубами Create: бензин — с верха колонны, керосин — с секций верхней
 * половины, дизельное топливо — с секций нижней половины, мазут — с куба. Флегмовое число и температуру на выходе печи
 * задают кнопками щита. Производительность модели — {@link #FEED_M3H} м³/ч нефти [BALANCE: колонна диаметром около
 * 1 м].
 */
public final class CrudeColumnBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int MIN_SECTIONS = 3, MAX_SECTIONS = 30, FURNACE_RADIUS = 6, PRODUCT_MB = 16_000;
    public static final double STAGES_PER_SECTION = 2, FEED_M3H = 10, CRUDE_KGM3 = 850;
    /** Бензин в спецификации (11 §11.2.2 {@code first_cut}): перекрытие с керосином ≤ 30 К — верх диапазона 10–30 К АВТ (09). */
    public static final double GASOLINE_OVERLAP_MAX_K = 30;
    public static final double REFLUX_MIN = 0.5, REFLUX_MAX = 5, REFLUX_STEP = 0.5, T_MIN_C = 320, T_MAX_C = 380, T_STEP_C = 5;

    private final ProductTank[] tanks = {new ProductTank(PcFluids.GASOLINE.source.get(), PRODUCT_MB),
            new ProductTank(PcFluids.KEROSENE.source.get(), PRODUCT_MB), new ProductTank(PcFluids.DIESEL_FUEL.source.get(), PRODUCT_MB),
            new ProductTank(PcFluids.FUEL_OIL.source.get(), PRODUCT_MB)};
    private double reflux = 1.5, outletC = 360;
    private int sections;
    @Nullable
    private BlockPos furnacePos;
    private AtmosphericUnit.Result result;
    private double resultKey = Double.NaN;
    private double feedKgS, fuelNm3s;
    private int ticks;

    public CrudeColumnBlockEntity(BlockPos pos, BlockState state) {
        super(ProductionBlockEntities.CRUDE_COLUMN.get(), pos, state);
    }

    public ProductTank tank(Product p) {
        return tanks[p.ordinal()];
    }

    public int sections() {
        return sections;
    }

    public double theoreticalStages() {
        return sections * STAGES_PER_SECTION;
    }

    public boolean ready() {
        return sections >= MIN_SECTIONS && furnacePos != null;
    }

    public void setOperation(double refluxRatio, double furnaceOutletC) {
        reflux = Math.clamp(refluxRatio, REFLUX_MIN, REFLUX_MAX);
        outletC = Math.clamp(furnaceOutletC, T_MIN_C, T_MAX_C);
        setChanged();
    }

    /** Секции над кубом до верха: 0, если верха нет. */
    private int countSections() {
        int n = 0;
        BlockPos p = worldPosition.above(2);
        while (n < MAX_SECTIONS && level.getBlockState(p).is(PcBlocks.COLUMN_TRAY_SECTION.get())) {
            n++;
            p = p.above(2);
        }
        return level.getBlockState(p).is(PcBlocks.COLUMN_TOP.get()) ? n : 0;
    }

    @Nullable
    private TubeFurnaceBlockEntity furnace() {
        if (furnacePos != null && level.getBlockEntity(furnacePos) instanceof TubeFurnaceBlockEntity f) return f;
        List<TubeFurnaceBlockEntity> all = Ironworks.blockEntities(level, new AABB(worldPosition).inflate(FURNACE_RADIUS),
                TubeFurnaceBlockEntity.class);
        furnacePos = all.isEmpty() ? null : all.getFirst().getBlockPos();
        return all.isEmpty() ? null : all.getFirst();
    }

    public AtmosphericUnit.Result result() {
        double key = sections * 1e6 + reflux * 1e3 + outletC;
        if (result == null || key != resultKey) {
            double stages = Math.max(1, theoreticalStages() / 3);
            result = new AtmosphericUnit(stages, reflux, outletC + 273.15).distill(CrudeAssay.WEST_SIBERIAN);
            resultKey = key;
        }
        return result;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        if (ticks++ % 40 == 0) {
            sections = countSections();
            furnacePos = null;
            furnace();
        }
        feedKgS = fuelNm3s = 0;
        TubeFurnaceBlockEntity f = furnacePos == null ? null : furnace();
        if (sections < MIN_SECTIONS || f == null) return;
        double dt = 0.05 * b.world().clock().kSlow();
        AtmosphericUnit.Result r = result();
        double maxKg = FEED_M3H * CRUDE_KGM3 / 3600 * dt;
        for (Product p : Product.values()) {
            double perKg = r.yield(p) / p.densityKgM3 * 1000;
            if (perKg > 0) maxKg = Math.min(maxKg, tanks[p.ordinal()].room() / perKg);
        }
        double kg = f.fire(maxKg, r.dutyJPerKg(), dt);
        for (Product p : Product.values()) tanks[p.ordinal()].add(kg * r.yield(p) / p.densityKgM3 * 1000);
        feedKgS = kg / dt;
        // бензин в спецификации: перекрытие D86 5–95 % с керосином не больше верхней границы реальных АВТ (09, σ₀)
        if (feedKgS > 0 && r.overlapK()[0] <= GASOLINE_OVERLAP_MAX_K) dev.denfry.promptcritical.mod.content.SimAchievements.once(this, dev.denfry.promptcritical.mod.content.SimAchievements.FIRST_CUT, "overlap_k", r.overlapK()[0]);
        if (feedKgS > 0 && level instanceof net.minecraft.server.level.ServerLevel sl)
            dev.denfry.promptcritical.mod.content.IndustrialComplex.running(sl, worldPosition, dev.denfry.promptcritical.mod.content.IndustrialComplex.Part.REFINERY);
        fuelNm3s = f.fuelNm3s();
        if (ticks % 100 == 0) setChanged();
    }

    /** Бак отбора на высоте {@code pos}: верх — бензин, верхняя половина секций — керосин, нижняя — дизель. */
    @Nullable
    public IFluidHandler drawAt(BlockPos pos) {
        int dy = pos.getY() - worldPosition.getY();
        if (dy < 2) return tanks[Product.RESIDUE.ordinal()];
        int section = (dy - 2) / 2;
        if (section >= sections) return tanks[Product.GASOLINE.ordinal()];
        return section >= sections / 2 ? tanks[Product.KEROSENE.ordinal()] : tanks[Product.DIESEL.ordinal()];
    }

    /** Колонна под секцией или верхом. */
    @Nullable
    public static CrudeColumnBlockEntity below(Level level, BlockPos pos) {
        BlockPos p = pos;
        for (int i = 0; i < 2 * MAX_SECTIONS + 4; i++) {
            p = p.below();
            if (level.getBlockEntity(p) instanceof CrudeColumnBlockEntity c) return c;
            BlockState s = level.getBlockState(p);
            if (!s.is(PcBlocks.COLUMN_TRAY_SECTION.get()) && !s.is(PcBlocks.MULTIBLOCK_PART.get()) && !s.is(PcBlocks.CRUDE_COLUMN.get())) return null;
        }
        return null;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return tanks[Product.RESIDUE.ordinal()];
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
        if (!ready()) {
            player.displayClientMessage(ProductionText.msg("column_missing", MIN_SECTIONS, sections), true);
        } else {
            player.displayClientMessage(ProductionText.msg("column_status", ProductionText.num(feedKgS / CRUDE_KGM3 * 3600, 2),
                    ProductionText.num(theoreticalStages(), 0), ProductionText.num(reflux, 1), ProductionText.num(outletC, 0),
                    ProductionText.num(fuelNm3s * 3600, 0), ProductionText.num(result().overlapK()[0], 0)), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ProductionPanels.CB_T_DOWN -> setOperation(reflux, outletC - T_STEP_C);
            case ProductionPanels.CB_T_UP -> setOperation(reflux, outletC + T_STEP_C);
            case ProductionPanels.CB_R_DOWN -> setOperation(reflux - REFLUX_STEP, outletC);
            case ProductionPanels.CB_R_UP -> setOperation(reflux + REFLUX_STEP, outletC);
            default -> {
            }
        }
    }

    @Override
    public PanelLayout panelLayout() {
        return ProductionPanels.COLUMN;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        AtmosphericUnit.Result r = result();
        out[ProductionPanels.C_T_OUT] = (int) Math.round(outletC);
        out[ProductionPanels.C_FEED_M3H10] = (int) Math.round(feedKgS / CRUDE_KGM3 * 3600 * 10);
        out[ProductionPanels.C_FUEL_M3H] = (int) Math.round(fuelNm3s * 3600);
        out[ProductionPanels.C_REFLUX_X10] = (int) Math.round(reflux * 10);
        out[ProductionPanels.C_TRAYS] = (int) Math.round(theoreticalStages());
        out[ProductionPanels.C_Y_GASOLINE] = (int) Math.round(r.yield(Product.GASOLINE) * 1000);
        out[ProductionPanels.C_Y_KEROSENE] = (int) Math.round(r.yield(Product.KEROSENE) * 1000);
        out[ProductionPanels.C_Y_DIESEL] = (int) Math.round(r.yield(Product.DIESEL) * 1000);
        out[ProductionPanels.C_Y_RESIDUE] = (int) Math.round(r.yield(Product.RESIDUE) * 1000);
        out[ProductionPanels.C_OVERLAP_K] = (int) Math.round(r.overlapK()[1]);
        out[ProductionPanels.C_FLAGS] = (feedKgS > 0 ? 1 << ProductionPanels.CF_LIT : 0) | (ready() ? 1 << ProductionPanels.CF_READY : 0);
        for (int i = 0; i < tanks.length; i++) out[ProductionPanels.C_TANK0 + i] = (int) Math.round(tanks[i].fill() * 1000);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("reflux", reflux);
        tag.putDouble("outlet", outletC);
        for (Product p : Product.values()) tanks[p.ordinal()].save(tag, p.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("reflux")) reflux = tag.getDouble("reflux");
        if (tag.contains("outlet")) outletC = tag.getDouble("outlet");
        for (Product p : Product.values()) tanks[p.ordinal()].load(tag, p.name().toLowerCase(java.util.Locale.ROOT));
    }
}
