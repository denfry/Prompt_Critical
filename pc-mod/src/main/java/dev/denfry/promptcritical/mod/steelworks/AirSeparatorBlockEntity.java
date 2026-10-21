package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.binding.DeviceHost;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ironworks.CokeOvenBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.DeviceMode;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.utilities.AirSeparationUnit;
import dev.denfry.promptcritical.sim.utilities.AsuState;
import dev.denfry.promptcritical.sim.utilities.AsuUnit;
import dev.denfry.promptcritical.sim.utilities.UtilitiesLog;
import dev.denfry.promptcritical.sim.utilities.UtilitiesSubsystem;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Блок разделения воздуха {@code air_separator} (08 §8.11): ректификационная колонна в кожухе, двойник —
 * {@link AsuUnit} в {@link UtilitiesSubsystem}. Установка собрана, если в {@value #REACH} блоках есть основной
 * компрессор {@code asu_main_compressor} (вал Create, вода), блок осушки {@code asu_molsieve} и турбодетандер
 * {@code asu_expander}. Продукт — по реальной мощности компрессора, в баки по сторонам: слева азот, справа кислород,
 * сзади аргон (трубы Create); жидкий — в криобак {@code cryogenic_tank} рядом. Воздух у коксохима (в
 * {@value #COKE_REACH} блоках) несёт ацетилен: без ПАЗ по углеводородам LOX копит его до взрыва
 * конденсатора-испарителя. Разрушенный блок чинят 8 стальными листами.
 */
public final class AirSeparatorBlockEntity extends IronworksBlockEntity implements DeviceHost, PanelHost {
    public static final int REACH = 8, COKE_REACH = 24, SCAN_TICKS = 20, REPAIR_PLATES = 8;
    /** Ацетилен в воздухе у коксохима и в чистом месте, ppm [BALANCE]. */
    public static final double COKE_C2H2_PPM = 0.3, CLEAN_C2H2_PPM = 0.01;
    /** Шаг доли жидкого продукта со щита. */
    public static final double LIQUID_STEP = 0.05;
    private static final double TICK_S = 0.05;

    private final GasBuffer oxygen = gas(PcFluids.OXYGEN, 16_000);
    private final GasBuffer nitrogen = gas(PcFluids.NITROGEN, 16_000);
    private final GasBuffer argon = gas(PcFluids.ARGON, 4_000);
    private final GasBuffer liquid = new GasBuffer(2_000, s -> s.getFluid().isSame(PcFluids.LIQUID_OXYGEN.source.get())
            || s.getFluid().isSame(PcFluids.LIQUID_NITROGEN.source.get()), this::setChanged);
    private SimId simId = SimId.NONE;
    private boolean chunkUnloading, formed, molsieve, expander, alarm;
    private double inletPpm = CLEAN_C2H2_PPM;
    private int scan;
    @Nullable
    private AsuCompressorBlockEntity compressor;
    private List<CryoTankBlockEntity> tanks = List.of();

    public AirSeparatorBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.AIR_SEPARATOR.get(), pos, state);
    }

    private GasBuffer gas(PcFluids.Entry e, int capacity) {
        return new GasBuffer(capacity, s -> s.getFluid().isSame(e.source.get()), this::setChanged);
    }

    @Nullable
    public AsuUnit unit() {
        return unit(Ironworks.binding(level));
    }

    @Nullable
    private AsuUnit unit(@Nullable SimLevelBinding b) {
        if (b == null || simId.isNone()) return null;
        return b.world().subsystem(UtilitiesSubsystem.class).unit(b.world().topology(), simId) instanceof AsuUnit u ? u : null;
    }

    public boolean formed() {
        return formed;
    }

    public GasBuffer oxygen() {
        return oxygen;
    }

    public GasBuffer nitrogen() {
        return nitrogen;
    }

    public GasBuffer argon() {
        return argon;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        AsuUnit u = unit(b);
        if (u == null) return;
        if (scan-- <= 0) {
            scan = SCAN_TICKS;
            survey();
        }
        if (compressor != null && compressor.isRemoved()) compressor = null;
        formed = compressor != null && molsieve && expander;
        u.setFormed(formed);
        AirSeparationUnit asu = u.asu();
        asu.setInletHydrocarbons(inletPpm);
        double w = compressor == null ? 0 : compressor.shaftPowerW();
        u.setCompressor(w, compressor == null ? 0 : compressor.coolingWater(AsuUnit.coolingWaterNeedKgS(w)));
        // продукт за реальный тик: газы 1 мБ = 1 нм³, жидкости — литры
        oxygen.put(PcFluids.OXYGEN.source.get(), u.productRate("oxygen") * TICK_S);
        nitrogen.put(PcFluids.NITROGEN.source.get(), u.productRate("nitrogen") * TICK_S);
        argon.put(PcFluids.ARGON.source.get(), u.productRate("argon") * TICK_S);
        liquid(PcFluids.LIQUID_OXYGEN.source.get(), u.productRate("liquid_oxygen") * TICK_S, CryoTankBlockEntity.RHO_LOX);
        liquid(PcFluids.LIQUID_NITROGEN.source.get(), u.productRate("liquid_nitrogen") * TICK_S, CryoTankBlockEntity.RHO_LIN);
        for (CryoTankBlockEntity t : tanks) {
            if (liquid.amount() <= 0) break;
            if (t.isRemoved()) continue;
            int moved = t.fillLiquid(liquid.fluidStack().copyWithAmount(liquid.amount()));
            if (moved > 0) liquid.draw(moved);
        }
        events(b, u);
        alarm = asu.loxHydrocarbonsPpm() > AirSeparationUnit.HYDROCARBON_SOLUBILITY_PPM * 0.5
                || asu.state() == AsuState.ICED || asu.state() == AsuState.TRIPPED;
        setStateFlag(PcProperties.RUNNING, asu.state() == AsuState.RUNNING || asu.state() == AsuState.COOLDOWN);
    }

    private void liquid(Fluid f, double kg, double rho) {
        if (kg > 0) liquid.put(f, kg / rho * 1000);
    }

    /** Состав установки вокруг и воздух у коксохима. */
    private void survey() {
        AABB near = new AABB(worldPosition).inflate(REACH);
        List<AsuCompressorBlockEntity> c = Ironworks.blockEntities(level, near, AsuCompressorBlockEntity.class);
        compressor = c.isEmpty() ? null : c.get(0);
        tanks = Ironworks.blockEntities(level, near, CryoTankBlockEntity.class);
        molsieve = expander = false;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-REACH, -REACH, -REACH), worldPosition.offset(REACH, REACH, REACH))) {
            BlockState s = level.getBlockState(p);
            if (s.is(PcBlocks.ASU_MOLSIEVE.get())) molsieve = true;
            else if (s.is(PcBlocks.ASU_EXPANDER.get())) expander = true;
            if (molsieve && expander) break;
        }
        boolean coke = !Ironworks.blockEntities(level, new AABB(worldPosition).inflate(COKE_REACH), CokeOvenBlockEntity.class).isEmpty();
        inletPpm = coke ? COKE_C2H2_PPM : CLEAN_C2H2_PPM;
    }

    private void events(SimLevelBinding b, AsuUnit u) {
        for (UtilitiesLog.Entry e; (e = u.log().poll()) != null; ) {
            switch (e.kind()) {
                case ASU_STATE -> level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6f,
                        e.index() == AsuState.RUNNING.ordinal() ? 1.4f : 0.7f);
                case HYDROCARBON_HIGH, MOLSIEVE_BREAKTHROUGH -> level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BIT.value(),
                        SoundSource.BLOCKS, 1, 0.5f);
                case ASU_EXPLODED -> {
                    double r = Math.max(3, Math.min(12, Math.cbrt(e.value() / 1e6) * 2));
                    Ironworks.blast(b, worldPosition, e.value(), r, 6);
                }
            }
        }
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.RUNNING)) return;
        MachineFx fx = fx();
        if (fx.chance(6)) fx.wisp(fx.at(8, 16, 8), 0.3);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == null) return oxygen.output();
        Direction f = facing();
        if (side == f.getCounterClockWise()) return nitrogen.output();
        if (side == f.getClockWise()) return oxygen.output();
        if (side == f.getOpposite()) return argon.output();
        if (side == Direction.DOWN) return liquid.output();
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.STEEL_PLATE.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        AsuUnit u = unit();
        if (u == null || u.asu().state() != AsuState.DESTROYED || !stack.is(PcItems.STEEL_PLATE.get())) return ItemInteractionResult.FAIL;
        if (stack.getCount() < REPAIR_PLATES && !player.getAbilities().instabuild) {
            player.displayClientMessage(SteelworksText.msg("asu_repair_need", REPAIR_PLATES), true);
            return ItemInteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) stack.shrink(REPAIR_PLATES);
        u.asu().rebuild();
        level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1, 1);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        AsuUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        AirSeparationUnit a = u.asu();
        if (!formed) {
            player.displayClientMessage(SteelworksText.msg("asu_incomplete", SteelworksText.yes(compressor != null),
                    SteelworksText.yes(molsieve), SteelworksText.yes(expander)), true);
            return InteractionResult.SUCCESS;
        }
        player.displayClientMessage(SteelworksText.msg("asu", SteelworksText.asuState(a.state()), SteelworksText.num(a.coldBoxK() - 273.15),
                SteelworksText.num(u.productRate("oxygen") * 3600), SteelworksText.num(a.oxygenPurity() * 100)), true);
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return SteelworksPanels.ASU;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        AsuUnit u = unit();
        if (u == null) return;
        AirSeparationUnit a = u.asu();
        out[SteelworksPanels.A_STATE] = a.state().ordinal();
        out[SteelworksPanels.A_COLD_C] = (int) Math.round(a.coldBoxK() - 273.15);
        out[SteelworksPanels.A_POWER_KW] = (int) Math.round(u.compressorW() / 1e3);
        out[SteelworksPanels.A_O2_NM3H] = (int) Math.round(u.productRate("oxygen") * 3600);
        out[SteelworksPanels.A_N2_NM3H] = (int) Math.round(u.productRate("nitrogen") * 3600);
        out[SteelworksPanels.A_PURITY_PERMIL] = (int) Math.round(a.oxygenPurity() * 1000);
        out[SteelworksPanels.A_LOX_PPM10] = (int) Math.round(a.loxHydrocarbonsPpm() * 10);
        out[SteelworksPanels.A_SIEVE_PCT] = (int) Math.round(a.molsieveLoading() * 100);
        out[SteelworksPanels.A_LIQUID_PCT] = (int) Math.round(a.liquidFraction() * 100);
        out[SteelworksPanels.A_O2_MB] = oxygen.amount();
        int flags = 0;
        if (a.state() == AsuState.RUNNING) flags |= 1 << SteelworksPanels.F_RUNNING;
        if (alarm || a.state() == AsuState.DESTROYED) flags |= 1 << SteelworksPanels.F_ALARM;
        if (formed) flags |= 1 << SteelworksPanels.AF_FORMED;
        if (a.regenerationHeater()) flags |= 1 << SteelworksPanels.AF_REGEN;
        if (a.hydrocarbonTrip()) flags |= 1 << SteelworksPanels.AF_TRIP;
        if (a.liquidOxygen()) flags |= 1 << SteelworksPanels.AF_LOX;
        if (a.state() == AsuState.ICED) flags |= 1 << SteelworksPanels.AF_ICED;
        out[SteelworksPanels.FLAGS] = flags;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        AsuUnit u = unit();
        if (u == null) return;
        AirSeparationUnit a = u.asu();
        switch (id) {
            case SteelworksPanels.BTN_REGEN -> a.setRegenerationHeater(!a.regenerationHeater());
            case SteelworksPanels.BTN_TRIP -> a.setHydrocarbonTrip(!a.hydrocarbonTrip());
            case SteelworksPanels.BTN_LIQ_DOWN -> a.setLiquidFraction(Math.max(0, a.liquidFraction() - LIQUID_STEP), a.liquidOxygen());
            case SteelworksPanels.BTN_LIQ_UP -> a.setLiquidFraction(Math.min(0.5, a.liquidFraction() + LIQUID_STEP), a.liquidOxygen());
            case SteelworksPanels.BTN_LIQ_KIND -> a.setLiquidFraction(a.liquidFraction(), !a.liquidOxygen());
            case SteelworksPanels.BTN_DRAIN -> {
                a.drainLox();
                level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.6f);
            }
            default -> {
            }
        }
        setChanged();
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
        tag.put("oxygen", oxygen.save(registries));
        tag.put("nitrogen", nitrogen.save(registries));
        tag.put("argon", argon.save(registries));
        tag.put("liquid", liquid.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        simId = new SimId(tag.getLong("sim_id"));
        if (tag.contains("oxygen")) oxygen.load(registries, tag.getCompound("oxygen"));
        if (tag.contains("nitrogen")) nitrogen.load(registries, tag.getCompound("nitrogen"));
        if (tag.contains("argon")) argon.load(registries, tag.getCompound("argon"));
        if (tag.contains("liquid")) liquid.load(registries, tag.getCompound("liquid"));
    }
}
