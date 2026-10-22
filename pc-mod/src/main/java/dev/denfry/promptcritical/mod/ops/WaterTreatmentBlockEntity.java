package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.binding.DeviceHost;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.DeviceMode;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.utilities.UtilitiesSubsystem;
import dev.denfry.promptcritical.sim.utilities.WaterGrade;
import dev.denfry.promptcritical.sim.utilities.WaterTreatmentUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Блок водоподготовки (08 §8.12): осветлитель {@code water_clarifier}, песчаный фильтр {@code sand_filter} или
 * Na-катионитная колонна {@code ion_exchange_column}; двойник — {@link WaterTreatmentUnit} в
 * {@link UtilitiesSubsystem}. Вода входит сверху и сзади, выходит снизу и спереди сортом
 * {@link WaterTreatmentUnit#outlet}; через блок — {@value #FLOW_MB} мБ за тик. Осветлителю нужна известь
 * {@code burnt_lime} ({@value #KG_PER_ITEM} кг в предмете), колонне на регенерацию — соль {@code salt}; фильтр
 * промывают Shift + ПКМ пустой рукой.
 */
public final class WaterTreatmentBlockEntity extends IronworksBlockEntity implements DeviceHost {
    public static final int TANK_MB = 8_000, FLOW_MB = 25;
    /** Извести или соли в предмете, кг [BALANCE]. */
    public static final double KG_PER_ITEM = 10;
    /** Извести в бункере не больше, кг. */
    public static final double LIME_BIN_KG = 640;

    private final GasBuffer in = new GasBuffer(TANK_MB, s -> accepts(s.getFluid()), this::setChanged);
    private final GasBuffer out = new GasBuffer(TANK_MB, s -> grade(s.getFluid()) != null, this::setChanged);
    private SimId simId = SimId.NONE;
    private boolean chunkUnloading;
    private double limeKg;

    public WaterTreatmentBlockEntity(BlockPos pos, BlockState state) {
        super(OpsBlockEntities.WATER_TREATMENT.get(), pos, state);
    }

    @Nullable
    public static WaterGrade grade(Fluid f) {
        if (f.isSame(Fluids.WATER)) return WaterGrade.RAW;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(f);
        return WaterGrade.byFluid(id.getPath());
    }

    /** Жидкость сорта: сырая — ванильная вода. */
    public static Fluid fluid(WaterGrade g) {
        return g == WaterGrade.RAW ? Fluids.WATER : GasBuffer.fluid(g.fluidId());
    }

    private boolean accepts(Fluid f) {
        WaterGrade g = grade(f);
        WaterTreatmentUnit u = unit();
        return g != null && (u == null ? g != WaterGrade.SOFTENED : u.accepts(g));
    }

    @Nullable
    public WaterTreatmentUnit unit() {
        return unit(Ironworks.binding(level));
    }

    @Nullable
    private WaterTreatmentUnit unit(@Nullable SimLevelBinding b) {
        if (b == null || simId.isNone()) return null;
        return b.world().subsystem(UtilitiesSubsystem.class).unit(b.world().topology(), simId) instanceof WaterTreatmentUnit u ? u : null;
    }

    public GasBuffer in() {
        return in;
    }

    public GasBuffer out() {
        return out;
    }

    public double limeKg() {
        return limeKg;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        WaterTreatmentUnit u = unit(b);
        if (u == null) return;
        if (u.stage() == WaterTreatmentUnit.Stage.CLARIFIER) {
            double pay = Math.min(limeKg, u.limeDueKg());
            if (pay > 0) {
                u.payLime(pay);
                limeKg -= pay;
                setChanged();
            }
            u.setLime(limeKg > 0);
        }
        boolean running = false;
        WaterGrade g = in.amount() > 0 ? grade(in.fluidStack().getFluid()) : null;
        if (g != null && u.accepts(g)) {
            Fluid o = fluid(u.outlet(g));
            int mb = Math.min(FLOW_MB, Math.min(in.amount(), out.room()));
            if (mb > 0 && (out.amount() == 0 || out.fluidStack().getFluid().isSame(o))) {
                in.draw(mb);
                out.put(o, mb);
                u.pass(g, mb / 1e6);
                running = true;
            }
        }
        setStateFlag(PcProperties.RUNNING, running);
        setStateFlag(PcProperties.ALARM, alarm(u));
    }

    /** Фильтр забит или смола исчерпана. */
    private static boolean alarm(WaterTreatmentUnit u) {
        return switch (u.stage()) {
            case CLARIFIER -> false;
            case SAND_FILTER -> u.train().filterLoadingFrac() >= 1;
            case ION_EXCHANGER -> u.train().resin(0).exhausted();
        };
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.RUNNING)) return;
        MachineFx fx = fx();
        if (fx.chance(10)) fx.drip(fx.at(8, 2, 8));
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == Direction.UP || side == facing().getOpposite()) return in.input();
        return out.output();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        WaterTreatmentUnit.Stage s = stage();
        return s == WaterTreatmentUnit.Stage.CLARIFIER && stack.is(PcItems.BURNT_LIME.get())
                || s == WaterTreatmentUnit.Stage.ION_EXCHANGER && stack.is(PcItems.SALT.get());
    }

    @Nullable
    private WaterTreatmentUnit.Stage stage() {
        String type = deviceType();
        return switch (type) {
            case UtilitiesSubsystem.WATER_CLARIFIER -> WaterTreatmentUnit.Stage.CLARIFIER;
            case UtilitiesSubsystem.SAND_FILTER -> WaterTreatmentUnit.Stage.SAND_FILTER;
            case UtilitiesSubsystem.ION_EXCHANGE_COLUMN -> WaterTreatmentUnit.Stage.ION_EXCHANGER;
            default -> null;
        };
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        WaterTreatmentUnit u = unit();
        if (u == null) return ItemInteractionResult.FAIL;
        if (u.stage() == WaterTreatmentUnit.Stage.CLARIFIER && stack.is(PcItems.BURNT_LIME.get())) {
            int n = (int) Math.min(stack.getCount(), Math.floor((LIME_BIN_KG - limeKg) / KG_PER_ITEM));
            if (n <= 0) return ItemInteractionResult.FAIL;
            stack.consume(n, player);
            limeKg += n * KG_PER_ITEM;
            level.playSound(null, worldPosition, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
            player.displayClientMessage(OpsText.msg("clarifier_lime", OpsText.num(limeKg, 0)), true);
            setChanged();
            return ItemInteractionResult.SUCCESS;
        }
        if (u.stage() == WaterTreatmentUnit.Stage.ION_EXCHANGER && stack.is(PcItems.SALT.get())) {
            double kg = u.train().regenerantKg(0);
            int need = (int) Math.ceil(kg / KG_PER_ITEM - 1e-9);
            if (need <= 0) return ItemInteractionResult.FAIL;
            if (stack.getCount() < need && !player.getAbilities().instabuild) {
                player.displayClientMessage(OpsText.msg("regen_need", OpsText.num(kg, 0)), true);
                return ItemInteractionResult.FAIL;
            }
            stack.consume(need, player);
            u.train().regenerate(0);
            level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8f, 0.8f);
            player.displayClientMessage(OpsText.msg("regenerated", OpsText.num(kg, 0)), true);
            setChanged();
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(Player player) {
        WaterTreatmentUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        if (u.stage() == WaterTreatmentUnit.Stage.SAND_FILTER && player.isShiftKeyDown()) {
            u.train().backwashFilter();
            level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8f, 0.7f);
            player.displayClientMessage(OpsText.msg("filter_backwash"), true);
            setChanged();
            return InteractionResult.SUCCESS;
        }
        String state = switch (u.stage()) {
            case CLARIFIER -> OpsText.msg("clarifier_lime", OpsText.num(limeKg, 0)).getString();
            case SAND_FILTER -> OpsText.msg("filter_load", OpsText.num(u.train().filterLoadingFrac() * 100, 0)).getString();
            case ION_EXCHANGER -> OpsText.msg("resin", OpsText.num(u.train().resin(0).loadingFrac() * 100, 0),
                    OpsText.num(u.train().regenerantKg(0), 0)).getString();
        };
        player.displayClientMessage(OpsText.msg("water", getBlockState().getBlock().getName(), in.amount(), out.amount(), state), true);
        return InteractionResult.SUCCESS;
    }

    // --- двойник ---

    @Override
    public SimId simId() {
        return simId;
    }

    @Override
    public void bindSimId(SimId id) {
        if (!id.equals(simId)) {
            simId = id;
            setChanged();
        }
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel) level;
    }

    @Override
    public BlockPos pos() {
        return worldPosition;
    }

    @Override
    public String deviceType() {
        return BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).toString();
    }

    @Override
    public DeviceMode mode() {
        SimLevelBinding b = Ironworks.binding(level);
        if (b == null || simId.isNone() || !b.world().topology().exists(simId)) return DeviceMode.LOCAL;
        return b.world().topology().deviceMode(simId);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        chunkUnloading = false;
        SimLevelBinding b = Ironworks.binding(level);
        if (b != null) b.attach(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        chunkUnloading = true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        SimLevelBinding b = Ironworks.binding(level);
        if (b != null) b.detach(this, !chunkUnloading);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("sim_id", simId.raw());
        tag.put("in", in.save(registries));
        tag.put("out", out.save(registries));
        tag.putDouble("lime_kg", limeKg);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        simId = new SimId(tag.getLong("sim_id"));
        in.load(registries, tag.getCompound("in"));
        out.load(registries, tag.getCompound("out"));
        limeKg = tag.getDouble("lime_kg");
    }

    /** Наполнить вход (GameTest и ведро). */
    public int fill(FluidStack s) {
        return in.tank().fill(s, IFluidHandler.FluidAction.EXECUTE);
    }
}
