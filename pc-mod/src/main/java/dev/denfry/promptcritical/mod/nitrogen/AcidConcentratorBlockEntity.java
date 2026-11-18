package dev.denfry.promptcritical.mod.nitrogen;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.gas.PortHandler;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.nitrogen.AcidConcentrator;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import net.minecraft.world.phys.Vec3;

/**
 * Колонна концентрирования азотной кислоты с плавом нитрата магния ({@link AcidConcentrator}) 1×3×1: слабая кислота
 * 56 % и товарный газ (топливо выпарного аппарата и кипятильника) — трубой к любой грани; кислота 98 % (1 мБ = 1 л,
 * 1,50 кг) — из той же грани. Если верхний продукт слабее 97 %, колонна работает «на себя» и кислоту не отдаёт.
 */
public final class AcidConcentratorBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int MB = 8_000;
    /** Номинальное питание слабой кислотой, кг/с (≈ 1,5 т/ч) [BALANCE: под один агрегат азотной кислоты]. */
    public static final double RATED_FEED_KG_S = 0.42, WEAK_KG_L = 1.345, STRONG_KG_L = 1.50, FURNACE_EFFICIENCY = 0.85;

    private final FeedTank weak = new FeedTank(PcFluids.NITRIC_ACID.source.get(), MB);
    private final FeedTank fuel = new FeedTank(PcFluids.SALES_GAS.source.get(), MB / 2);
    private final ProductTank strong = new ProductTank(PcFluids.CONCENTRATED_NITRIC_ACID.source.get(), MB);
    private final IFluidHandler port = new PortHandler(strong, weak, fuel);
    private final AcidConcentrator column = new AcidConcentrator();
    private boolean running, noFuel;
    @Nullable
    private AcidConcentrator.Result last;
    private double feedKgS, productKgS;

    public AcidConcentratorBlockEntity(BlockPos pos, BlockState state) {
        super(NitrogenBlockEntities.ACID_CONCENTRATOR.get(), pos, state);
    }

    public AcidConcentrator column() {
        return column;
    }

    public FeedTank weak() {
        return weak;
    }

    public FeedTank fuel() {
        return fuel;
    }

    public ProductTank strong() {
        return strong;
    }

    public boolean running() {
        return running;
    }

    public void setRunning(boolean r) {
        running = r;
        setChanged();
    }

    /** Работающий концентратор (арт art/blockbench/reagents/acid_concentrator.author.js): парит конденсатоотводчик парового подогревателя куба (контроллер — нижняя клетка, на 16 px ниже якоря модели). */
    private void clientFx() {
        MachineFx fx = MachineFx.of(level, worldPosition, getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (fx == null || !getBlockState().getValue(BlockStateProperties.LIT)) return;
        if (fx.chance(1.5)) fx.puff(fx.at(16.2, 2.1, 10.5), new Vec3(0.02, 0.02, 0), 1);
    }

    @Override
    public void tickDevice() {
        if (level != null && level.isClientSide) {
            clientFx();
            return;
        }
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        double dt = 0.05 * b.world().clock().kSlow();
        feedKgS = productKgS = 0;
        noFuel = false;
        if (running) {
            double kg = Math.min(RATED_FEED_KG_S * dt, weak.amount() * WEAK_KG_L);
            if (kg > 0) {
                AcidConcentrator.Result r = column.run(kg / dt);
                last = r;
                double fuelNm3 = r.heatW() * dt / (SteamReformerBlockEntity.SALES_GAS_J_PER_NM3 * FURNACE_EFFICIENCY);
                // топлива мало — питание сколько хватит тепла
                double share = fuelNm3 > 0 ? Math.min(1, fuel.amount() / fuelNm3) : 1;
                noFuel = share < 0.05;
                double productL = r.hno3KgS() * dt * share / (r.topStrength() * STRONG_KG_L);
                if (r.onSpec() && share > 0 && strong.room() >= productL) {
                    weak.take(kg * share / WEAK_KG_L);
                    fuel.take(fuelNm3 * share);
                    strong.add(productL);
                    feedKgS = kg * share / dt;
                    productKgS = productL * STRONG_KG_L / dt;
                }
            }
        }
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

    /** Shift + ПКМ: пуск или останов. */
    @Override
    public InteractionResult use(Player player) {
        toggle(player);
        return InteractionResult.SUCCESS;
    }

    private void toggle(Player player) {
        running = !running;
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5f, 1.0f);
        player.displayClientMessage(NitrogenText.msg(running ? "concentrator.start" : "concentrator.stop"), true);
        setChanged();
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return NitrogenPanels.CONCENTRATOR;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        AcidConcentrator.Result r = last != null ? last : column.run(RATED_FEED_KG_S);
        out[NitrogenPanels.C_TOP] = (int) Math.round(r.topStrength() * 1e4);
        out[NitrogenPanels.C_SALT] = (int) Math.round(column.saltRatio() * 10);
        out[NitrogenPanels.C_REFLUX] = (int) Math.round(column.reflux() * 10);
        out[NitrogenPanels.C_FEED_KGH] = (int) Math.round(feedKgS * 3600);
        out[NitrogenPanels.C_PRODUCT_KGH] = (int) Math.round(productKgS * 3600);
        out[NitrogenPanels.C_HEAT_KW] = running ? (int) Math.round(r.heatW() / 1e3) : 0;
        out[NitrogenPanels.C_WEAK_TANK] = (int) Math.round(weak.fill() * 1000);
        out[NitrogenPanels.C_PRODUCT_TANK] = (int) Math.round(strong.fill() * 1000);
        out[NitrogenPanels.C_FLAGS] = (productKgS > 0 ? 1 << NitrogenPanels.CF_RUN : 0)
                | (running && !r.onSpec() ? 1 << NitrogenPanels.CF_OFF_SPEC : 0) | (noFuel ? 1 << NitrogenPanels.CF_NO_FUEL : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case NitrogenPanels.CB_RUN -> toggle(player);
            case NitrogenPanels.CB_SALT_DOWN -> column.setSaltRatio(column.saltRatio() - 0.5);
            case NitrogenPanels.CB_SALT_UP -> column.setSaltRatio(column.saltRatio() + 0.5);
            case NitrogenPanels.CB_REFLUX_DOWN -> column.setReflux(column.reflux() - 0.1);
            case NitrogenPanels.CB_REFLUX_UP -> column.setReflux(column.reflux() + 0.1);
            default -> {
            }
        }
        setChanged();
    }

    // --- сохранение ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        weak.save(tag, "weak");
        fuel.save(tag, "fuel");
        strong.save(tag, "strong");
        MutableStateTree tree = new MutableStateTree();
        column.save(new TreeStateWriter(tree));
        tag.put("column", NbtStateTrees.toNbt(tree));
        tag.putBoolean("running", running);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        weak.load(tag, "weak");
        fuel.load(tag, "fuel");
        strong.load(tag, "strong");
        if (tag.contains("column")) column.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("column"))));
        running = tag.getBoolean("running");
    }
}
