package dev.denfry.promptcritical.mod.production;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Трубчатая печь атмосферной установки: сырьё (нефть) и топливный газ подводят трубами к любой клетке. Колонна
 * ({@link CrudeColumnBlockEntity}) берёт из печи нагретую нефть, печь сжигает на это попутный газ: расход
 * Q / (η·Q_н), Q_н = 42 МДж/нм³ (попутный газ с этаном и пропаном богаче метана), КПД печи 0,85 (Ахметов, гл. 6;
 * API Std 560).
 */
public final class TubeFurnaceBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    public static final double FUEL_J_PER_NM3 = 42e6, EFFICIENCY = 0.85;
    public static final int CRUDE_MB = 16_000, FUEL_MB = 4_000;
    /**
     * Соль в нефти, пришедшей трубой мимо ЭЛОУ, мг/л: продукция скважины без промыслового обессоливания — значение
     * примера 09 §9.7.3 (200 мг/л). Соль в баке печи — пассивный скаляр, смешивается по объёму.
     */
    public static final double RAW_SALT_MG_L = 200;

    private final FeedTank crude = new FeedTank(PcFluids.CRUDE_OIL.source.get(), CRUDE_MB);
    private final FeedTank fuel = new FeedTank(PcFluids.ASSOCIATED_GAS.source.get(), FUEL_MB);
    private final IFluidHandler inlet = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return (tank == 0 ? crude : fuel).getFluidInTank(0);
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? CRUDE_MB : FUEL_MB;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return (tank == 0 ? crude : fuel).isFluidValid(0, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!resource.is(crude.fluid())) return fuel.fill(resource, action);
            double before = crude.amount();
            int n = crude.fill(resource, action);
            if (n > 0 && action.execute()) mixSalt(before, n, RAW_SALT_MG_L);
            return n;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };
    private double fuelNm3s, saltMgL = RAW_SALT_MG_L;
    private int litTicks;

    public TubeFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ProductionBlockEntities.TUBE_FURNACE.get(), pos, state);
    }

    public FeedTank crude() {
        return crude;
    }

    public FeedTank fuel() {
        return fuel;
    }

    /** Соль в нефти бака печи, мг/л (её получает колонна вместе с нефтью). */
    public double crudeSaltMgL() {
        return crude.amount() > 0 ? saltMgL : RAW_SALT_MG_L;
    }

    /** Принять обессоленную нефть от касающегося ЭЛОУ; возвращает принятое, л. */
    public double acceptDesalted(double liters, double salt) {
        double before = crude.amount();
        double n = crude.add(liters);
        if (n > 0) mixSalt(before, n, salt);
        return n;
    }

    private void mixSalt(double before, double added, double salt) {
        double old = before > 0 ? saltMgL : salt;
        saltMgL = (old * before + salt * added) / (before + added);
        setChanged();
    }

    /** Расход топлива на последнем шаге, нм³/с. */
    public double fuelNm3s() {
        return fuelNm3s;
    }

    /**
     * Нагреть до {@code maxKg} нефти за шаг {@code dt}, тепло {@code dutyJPerKg} на кг: сколько хватает сырья и
     * топлива. Возвращает нагретую массу, кг.
     */
    public double fire(double maxKg, double dutyJPerKg, double dt) {
        double rho = PcFluids.CRUDE_OIL.type.get().getDensity() / 1000.0;
        double byCrude = crude.amount() * rho, byFuel = fuel.amount() * FUEL_J_PER_NM3 * EFFICIENCY / dutyJPerKg;
        double kg = Math.max(0, Math.min(maxKg, Math.min(byCrude, byFuel)));
        crude.take(kg / rho);
        double nm3 = kg * dutyJPerKg / (FUEL_J_PER_NM3 * EFFICIENCY);
        fuel.take(nm3);
        fuelNm3s = dt > 0 ? nm3 / dt : 0;
        if (kg > 0) litTicks = 40;
        return kg;
    }

    @Override
    public void tickDevice() {
        if (level == null) return;
        if (level.isClientSide) {
            if (getBlockState().getValue(BlockStateProperties.LIT)) {
                MachineFx fx = MachineFx.of(level, worldPosition, getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
                if (fx != null) clientFx(fx);
            }
            return;
        }
        if (litTicks > 0) litTicks--;
        else fuelNm3s = 0;
        boolean lit = litTicks > 0;
        BlockState s = getBlockState();
        if (s.getValue(BlockStateProperties.LIT) != lit && level.getGameTime() % 10 == 0)
            level.setBlock(worldPosition, s.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_ALL);
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    /**
     * Работающая печь (модель art/blockbench/refinery/tube_furnace.author.js): дым из трубы, языки пламени
     * в амбразурах подовых горелок под полом топки, гул факелов.
     */
    private static void clientFx(MachineFx fx) {
        if (fx.chance(3)) fx.smoke(fx.at(8, 32, 8), false, 0.2);
        if (fx.chance(4)) {
            int i = fx.random().nextInt(6);
            fx.flame(fx.at(-6 + 14 * (i % 3), -11, i < 3 ? -2 : 18), 0.1);
        }
        if (fx.chance(0.4)) fx.sound(fx.at(8, -10, 8), dev.denfry.promptcritical.mod.sound.PcSfx.BOILER_FIRE.get(), 0.7f, 0.8f);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return inlet;
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        crude.save(tag, "crude");
        fuel.save(tag, "fuel");
        tag.putDouble("salt", saltMgL);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        crude.load(tag, "crude");
        fuel.load(tag, "fuel");
        saltMgL = tag.contains("salt") ? tag.getDouble("salt") : RAW_SALT_MG_L;
    }
}
