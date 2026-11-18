package dev.denfry.promptcritical.mod.chem;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.petrochem.PyroComponent;
import dev.denfry.promptcritical.sim.petrochem.PyrolysisFeed;
import dev.denfry.promptcritical.sim.petrochem.PyrolysisFurnace;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Печь пиролиза ({@link PyrolysisFurnace}) 3×3×3: радиантная камера со змеевиками. Сырьё — пропан,
 * бутан, газовый бензин или прямогонный бензин (нафта) трубами к любой грани; этан и пропан с газоразделения
 * возвращаются в печь сами (рецикл); питательная вода — на пар разбавления; топливо — водородно-метановая фракция
 * газоразделения, недостающее — товарный газ трубой. Пирогаз уходит в установку газоразделения
 * ({@link CrackedGasUnitBlockEntity}), которая должна касаться печи: состав пирогаза зависит от сырья и режима, по
 * трубам его не перенести. Без газоразделения печь не работает. Коксование — по игровому времени (тик — 3,6 с).
 */
public final class PyrolysisFurnaceBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int FEED_MB = 8_000, WATER_MB = 8_000, FUEL_MB = 4_000;
    /** Сырьё по умолчанию, кг/с [BALANCE: опытная печь 1,1 т/ч; промышленные — 20–50 т/ч]. */
    public static final double RATED_FEED_KG_S = 0.3, SALES_GAS_J_PER_NM3 = 36e6, GAME_S_PER_TICK = 3.6;

    private final FeedTank propane = new FeedTank(PcFluids.PROPANE.source.get(), FEED_MB);
    private final FeedTank butane = new FeedTank(PcFluids.BUTANE.source.get(), FEED_MB);
    private final FeedTank naturalGasoline = new FeedTank(PcFluids.NATURAL_GASOLINE.source.get(), FEED_MB);
    private final FeedTank naphtha = new FeedTank(PcFluids.GASOLINE.source.get(), FEED_MB);
    private final FeedTank water = new FeedTank(PcFluids.BOILER_FEEDWATER.source.get(), WATER_MB);
    private final FeedTank fuel = new FeedTank(PcFluids.SALES_GAS.source.get(), FUEL_MB);
    private final FeedTank[] inputs = {propane, butane, naturalGasoline, naphtha, water, fuel};
    private final IFluidHandler inlet = new MultiInlet(inputs);
    private final PyrolysisFurnace furnace = new PyrolysisFurnace();
    /** Рецикл с газоразделения: этан и пропан, кг; хвостовой газ — тепло, Дж. */
    private double ethaneKg, recyclePropaneKg, tailGasJ;
    private double rateFraction = 1, flaredW;
    private boolean noSeparation;

    public PyrolysisFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ChemBlockEntities.PYROLYSIS_FURNACE.get(), pos, state);
    }

    public PyrolysisFurnace furnace() {
        return furnace;
    }

    public FeedTank tank(PyrolysisFeed f) {
        if (f == PyrolysisFeed.PROPANE) return propane;
        if (f == PyrolysisFeed.BUTANE) return butane;
        if (f == PyrolysisFeed.NATURAL_GASOLINE) return naturalGasoline;
        if (f == PyrolysisFeed.NAPHTHA) return naphtha;
        return null;
    }

    public FeedTank water() {
        return water;
    }

    public FeedTank fuel() {
        return fuel;
    }

    public boolean noSeparation() {
        return noSeparation;
    }

    /** Рецикл с газоразделения: этан и пропан, кг; хвостовой газ (H₂ + CH₄) — теплом, Дж. */
    void acceptRecycle(double ethane, double propaneKg, double tailJ) {
        ethaneKg = Math.min(ethaneKg + ethane, 2000);
        recyclePropaneKg = Math.min(recyclePropaneKg + propaneKg, 2000);
        tailGasJ = Math.min(tailGasJ + tailJ, 5e9);
    }

    /** Сырьё, кг, которое можно взять сейчас. */
    private double feedAvailableKg(PyrolysisFeed f) {
        if (f == PyrolysisFeed.ETHANE) return ethaneKg;
        double kg = tank(f).amount() / 1000 * f.densityKgM3();
        return f == PyrolysisFeed.PROPANE ? kg + recyclePropaneKg : kg;
    }

    private void takeFeed(PyrolysisFeed f, double kg) {
        if (f == PyrolysisFeed.ETHANE) {
            ethaneKg = Math.max(0, ethaneKg - kg);
            return;
        }
        if (f == PyrolysisFeed.PROPANE) {
            double r = Math.min(kg, recyclePropaneKg);
            recyclePropaneKg -= r;
            kg -= r;
        }
        tank(f).take(kg / f.densityKgM3() * 1000);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        double dt = 0.05 * b.world().clock().kSlow();
        CrackedGasUnitBlockEntity sep = separation();
        noSeparation = sep == null;
        PyrolysisFeed f = furnace.feed();
        double want = furnace.mode() == PyrolysisFurnace.Mode.RUN && !noSeparation ? RATED_FEED_KG_S * rateFraction * dt : 0;
        double kg = Math.min(want, feedAvailableKg(f));
        // пар разбавления — из питательной воды (1 л ≈ 1 кг)
        kg = Math.min(kg, water.amount() / 1000 / Math.max(furnace.steamRatio(), 1e-9));
        // топливо: сначала хвостовой газ, затем товарный
        if (kg > 0) {
            furnace.step(1, 0, 1); // удельные величины на 1 кг/с при нынешнем режиме
            double jPerKg = furnace.fuelW();
            double available = tailGasJ + fuel.amount() * SALES_GAS_J_PER_NM3;
            kg = Math.min(kg, available / Math.max(jPerKg, 1));
        }
        furnace.step(dt, GAME_S_PER_TICK, kg / dt);
        if (kg > 0 && furnace.feedKgS() > 0) {
            takeFeed(f, kg);
            water.take(kg * furnace.steamRatio() * 1000);
            double need = furnace.fuelW() * dt;
            double fromTail = Math.min(tailGasJ, need);
            tailGasJ -= fromTail;
            fuel.take((need - fromTail) / SALES_GAS_J_PER_NM3);
            double[] kgOut = furnace.productKgS();
            for (int i = 0; i < kgOut.length; i++) kgOut[i] *= dt;
            sep.acceptCrackedGas(kgOut);
        }
        // излишек хвостового газа — на факел
        flaredW = tailGasJ > 1e9 ? (tailGasJ - 1e9) / dt : 0;
        if (tailGasJ > 1e9) tailGasJ = 1e9;
        if (level.getGameTime() % 10 == 0) setLit(furnace.feedKgS() > 0 || furnace.mode() == PyrolysisFurnace.Mode.DECOKE);
        if (furnace.feedKgS() > 0 && level.getGameTime() % 20 == 0)
            s.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 2.5, worldPosition.getZ() + 0.5,
                    2, 0.2, 0.1, 0.2, 0.01);
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    private void setLit(boolean lit) {
        BlockState st = getBlockState();
        if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT) != lit)
            level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
    }

    /** Установка газоразделения, касающаяся радиантной камеры (3×3×3 вокруг контроллера). */
    @Nullable
    private CrackedGasUnitBlockEntity separation() {
        for (int x = -2; x <= 2; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) < 2 && Math.abs(z) < 2) continue;
                    CrackedGasUnitBlockEntity c = CrackedGasUnitBlockEntity.at(level, worldPosition.offset(x, y, z));
                    if (c != null) {
                        c.bindFurnace(worldPosition);
                        return c;
                    }
                }
        return null;
    }

    // --- руки ---

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

    /** Shift + ПКМ: пуск или останов. */
    @Override
    public InteractionResult use(Player player) {
        toggleRun(player);
        return InteractionResult.SUCCESS;
    }

    private void toggleRun(Player player) {
        furnace.setMode(furnace.mode() == PyrolysisFurnace.Mode.RUN ? PyrolysisFurnace.Mode.STOP : PyrolysisFurnace.Mode.RUN);
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6f, 0.8f);
        player.displayClientMessage(ChemText.msg("furnace." + furnace.mode().name().toLowerCase(java.util.Locale.ROOT)), true);
        setChanged();
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return ChemPanels.FURNACE;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[ChemPanels.COT_C] = (int) Math.round(furnace.cotK() - 273.15);
        out[ChemPanels.TMT_C] = furnace.tmtK() > 0 ? (int) Math.round(furnace.tmtK() - 273.15) : 0;
        out[ChemPanels.COKE_X100] = (int) Math.round(furnace.cokeMm() * 100);
        out[ChemPanels.FEED_KGH] = (int) Math.round(furnace.feedKgS() * 3600);
        out[ChemPanels.CONV_PERMIL] = (int) Math.round(furnace.conversion() * 1000);
        out[ChemPanels.STEAM_X100] = (int) Math.round(furnace.steamRatio() * 100);
        out[ChemPanels.TAU_MS] = (int) Math.round(furnace.residenceS() * 1000);
        out[ChemPanels.HP_STEAM_KGH] = (int) Math.round(furnace.hpSteamKgS() * 3600);
        out[ChemPanels.FUEL_KW] = (int) Math.round(furnace.fuelW() / 1e3);
        out[ChemPanels.FEED] = Arrays.asList(PyrolysisFeed.ALL).indexOf(furnace.feed());
        double[] y = furnace.yieldsNow();
        out[ChemPanels.ETHYLENE_PERMIL] = (int) Math.round(y[PyroComponent.C2H4.ordinal()] * 1000);
        out[ChemPanels.PROPYLENE_PERMIL] = (int) Math.round(y[PyroComponent.C3H6.ordinal()] * 1000);
        out[ChemPanels.MODE] = furnace.mode().ordinal();
        out[ChemPanels.FLAGS] = (furnace.feedKgS() > 0 ? 1 << ChemPanels.F_RUN : 0) | (furnace.tmtTrip() ? 1 << ChemPanels.F_TRIP : 0)
                | (furnace.mode() == PyrolysisFurnace.Mode.DECOKE ? 1 << ChemPanels.F_DECOKE : 0)
                | (noSeparation ? 1 << ChemPanels.F_NO_SEP : 0) | (flaredW > 0 ? 1 << ChemPanels.F_FLARE : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ChemPanels.B_RUN -> toggleRun(player);
            case ChemPanels.B_DECOKE -> furnace.setMode(furnace.mode() == PyrolysisFurnace.Mode.DECOKE ? PyrolysisFurnace.Mode.STOP
                    : PyrolysisFurnace.Mode.DECOKE);
            case ChemPanels.B_FEED -> {
                if (furnace.mode() == PyrolysisFurnace.Mode.RUN && furnace.feedKgS() > 0) return;
                int i = Arrays.asList(PyrolysisFeed.ALL).indexOf(furnace.feed());
                furnace.setFeed(PyrolysisFeed.ALL[(i + 1) % PyrolysisFeed.ALL.length]);
            }
            case ChemPanels.B_COT_DOWN -> furnace.setCotK(furnace.cotK() - 5);
            case ChemPanels.B_COT_UP -> furnace.setCotK(furnace.cotK() + 5);
            case ChemPanels.B_STEAM_DOWN -> furnace.setSteamRatio(furnace.steamRatio() - 0.05);
            case ChemPanels.B_STEAM_UP -> furnace.setSteamRatio(furnace.steamRatio() + 0.05);
            case ChemPanels.B_RATE_DOWN -> rateFraction = Math.max(0.2, rateFraction - 0.1);
            case ChemPanels.B_RATE_UP -> rateFraction = Math.min(1.2, rateFraction + 0.1);
            default -> {
            }
        }
        setChanged();
    }

    // --- сохранение ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < inputs.length; i++) inputs[i].save(tag, "in" + i);
        MutableStateTree tree = new MutableStateTree();
        furnace.save(new TreeStateWriter(tree));
        tag.put("furnace", dev.denfry.promptcritical.mod.binding.NbtStateTrees.toNbt(tree));
        tag.putDouble("ethane", ethaneKg);
        tag.putDouble("recycle_c3", recyclePropaneKg);
        tag.putDouble("tail", tailGasJ);
        tag.putDouble("rate", rateFraction);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < inputs.length; i++) inputs[i].load(tag, "in" + i);
        if (tag.contains("furnace"))
            furnace.load(new TreeStateReader(dev.denfry.promptcritical.mod.binding.NbtStateTrees.fromNbt(tag.getCompound("furnace"))));
        ethaneKg = tag.getDouble("ethane");
        recyclePropaneKg = tag.getDouble("recycle_c3");
        tailGasJ = tag.getDouble("tail");
        if (tag.contains("rate")) rateFraction = tag.getDouble("rate");
    }

    /** Вход нескольких жидкостей через одну грань: каждая — в свой бак. */
    static final class MultiInlet implements IFluidHandler {
        private final FeedTank[] tanks;

        MultiInlet(FeedTank... tanks) {
            this.tanks = tanks;
        }

        @Override
        public int getTanks() {
            return tanks.length;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tanks[tank].getFluidInTank(0);
        }

        @Override
        public int getTankCapacity(int tank) {
            return tanks[tank].getTankCapacity(0);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tanks[tank].isFluidValid(0, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            for (FeedTank t : tanks) if (t.isFluidValid(0, resource)) return t.fill(resource, action);
            return 0;
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
