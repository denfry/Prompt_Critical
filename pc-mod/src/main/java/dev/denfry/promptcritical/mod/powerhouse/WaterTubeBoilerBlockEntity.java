package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.binding.DeviceHost;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.ops.WaterTreatmentBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.boiler.BoilerFuels;
import dev.denfry.promptcritical.sim.combustion.BmsResponse;
import dev.denfry.promptcritical.sim.combustion.BmsState;
import dev.denfry.promptcritical.sim.combustion.BoilerOutputs;
import dev.denfry.promptcritical.sim.combustion.BurnerKind;
import dev.denfry.promptcritical.sim.core.CommandSource;
import dev.denfry.promptcritical.sim.core.DeviceMode;
import dev.denfry.promptcritical.sim.core.SimCommand;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.metallurgy.ProcessGas;
import dev.denfry.promptcritical.sim.plant.PlantFuels;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import dev.denfry.promptcritical.sim.plant.WaterTubeUnit;
import dev.denfry.promptcritical.sim.utilities.Deaerator;
import dev.denfry.promptcritical.sim.utilities.WaterGrade;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Щит водотрубного котла I1 {@code water_tube_boiler} (07 §7.9.5–7.9.6): касается топки снаружи, находит конструкцию
 * ({@link WaterTubeBoilerShape}) и держит её двойник {@link WaterTubeUnit} подсистемы {@link SteamPlant}. Обвязка — по
 * близости ({@value #LINK_RANGE} блоков): дутьевые вентиляторы и дымососы ({@link DraftFanBlockEntity}), деаэратор.
 *
 * <p>Топливо — горючий газ завода по трубам Create (1 мБ = 1 нм³) или угольная пыль воронкой и в слот щита; трубы и
 * воронки подключаются к щиту, горелкам и барабану. Питательная вода — {@code #minecraft:water} и вода
 * водоподготовки; {@code boiler_feedwater} после деаэратора горячая и без кислорода. Тип горелок — по топливу; смена
 * топлива другого типа — только на остановленном котле.
 */
public final class WaterTubeBoilerBlockEntity extends IronworksBlockEntity implements DeviceHost, PanelHost {
    public static final int LINK_RANGE = 12, FEED_TANK_MB = 8000, GAS_TANK_MB = 4000;
    public static final double AMBIENT_K = 288.15;
    /** Шаг ручного задания и уставки давления кнопками щита. */
    public static final double MANUAL_STEP = 0.05, SETPOINT_STEP_PA = 0.05e6;
    private static final int VALIDATE_PERIOD = 40, LOOK_PERIOD = 4;

    @Nullable
    private WaterTubeBoilerShape shape;
    private String problem = "";
    private SimId simId = SimId.NONE;
    private boolean chunkUnloading;
    private final GasBuffer gas = new GasBuffer(GAS_TANK_MB, GasBuffer::isProcessGas, this::setChanged);
    private final FluidTank feed = new FluidTank(FEED_TANK_MB,
            fs -> fs.is(FluidTags.WATER) || WaterTreatmentBlockEntity.grade(fs.getFluid()) != null) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final ItemStackHandler dust = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(PcItems.COAL_DUST.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IFluidHandler inlet = new Inlet();
    private List<BlockPos> fans = List.of();
    private double feedDebtKg, dustDebtKg, deaeratorDemandKgS, deaeratorSteamKg;
    private boolean wasRelief;

    public WaterTubeBoilerBlockEntity(BlockPos pos, BlockState state) {
        super(PowerhouseBlockEntities.WATER_TUBE_BOILER.get(), pos, state);
    }

    @Nullable
    public WaterTubeBoilerShape shape() {
        return shape;
    }

    public GasBuffer gasBuffer() {
        return gas;
    }

    public FluidTank feedTank() {
        return feed;
    }

    public ItemStackHandler dustSlot() {
        return dust;
    }

    /** Двойник в домене (сервер), если котёл собран хотя бы раз. */
    @Nullable
    public WaterTubeUnit unit() {
        SimLevelBinding b = Ironworks.binding(level);
        if (b == null || simId.isNone()) return null;
        SteamPlant plant = b.world().subsystem(SteamPlant.class);
        return plant == null ? null : plant.waterTube(simId);
    }

    /** Котёл, которому принадлежит клетка {@code part} (стена, горелка, барабан или сам щит). */
    @Nullable
    public static WaterTubeBoilerBlockEntity of(Level level, BlockPos part) {
        if (level.getBlockEntity(part) instanceof WaterTubeBoilerBlockEntity c) return c;
        AABB box = new AABB(part).inflate(WaterTubeBoilerShape.MAX_SIDE + 1, WaterTubeBoilerShape.MAX_HEIGHT + 2,
                WaterTubeBoilerShape.MAX_SIDE + 1);
        for (WaterTubeBoilerBlockEntity c : Ironworks.blockEntities(level, box, WaterTubeBoilerBlockEntity.class))
            if (c.shape != null && c.shape.contains(part)) return c;
        return null;
    }

    /** Ближайший собранный котёл в {@value #LINK_RANGE} блоках от {@code pos}. */
    @Nullable
    public static WaterTubeBoilerBlockEntity near(Level level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(LINK_RANGE + WaterTubeBoilerShape.MAX_SIDE);
        WaterTubeBoilerBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (WaterTubeBoilerBlockEntity c : Ironworks.blockEntities(level, box, WaterTubeBoilerBlockEntity.class)) {
            if (c.shape == null || !c.shape.bounds().inflate(LINK_RANGE).contains(pos.getCenter())) continue;
            double d = c.shape.center().distSqr(pos);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    // --- деаэратор ---

    /**
     * Заявка деаэратора на греющий пар, кг/с; возвращает пар, отданный барабаном с прошлого вызова, кг. Заявки
     * нескольких деаэраторов складываются на тик.
     */
    public double drawDeaeratorSteam(double demandKgS) {
        deaeratorDemandKgS += Math.max(0, demandKgS);
        double kg = deaeratorSteamKg;
        deaeratorSteamKg = 0;
        return kg;
    }

    public double drumPressurePa() {
        WaterTubeUnit u = unit();
        return u == null ? Double.NaN : u.drumPressurePa();
    }

    /** Энтальпия пара из барабана для теплового баланса деаэратора, Дж/кг (насыщенный при давлении барабана). */
    public double drumSteamEnthalpy() {
        double p = drumPressurePa();
        return Double.isFinite(p) ? dev.denfry.promptcritical.sim.thermo.WaterProperties.tables().saturationP(p).hG() : 0;
    }

    // --- тик ---

    @Override
    protected void serverTick(SimLevelBinding b) {
        long now = level.getGameTime();
        if (now % VALIDATE_PERIOD == Math.floorMod(worldPosition.asLong(), VALIDATE_PERIOD)) validate(b);
        WaterTubeUnit u = unit();
        if (u == null) return;
        supplyFuel(u);
        supplyFeed(u);
        double fd = 0, id = 0;
        for (BlockPos p : fans) {
            if (level.getBlockEntity(p) instanceof DraftFanBlockEntity f) {
                f.link(now);
                if (f.forcedDraft()) fd += f.flowKgS();
                else id += f.flowKgS();
            }
        }
        u.setFans(fd, id);
        u.setDeaeratorDemand(deaeratorDemandKgS);
        deaeratorDemandKgS = 0;
        deaeratorSteamKg += u.takeDeaeratorSteam();
        if (now % LOOK_PERIOD == 0) looks(u);
    }

    private void validate(SimLevelBinding b) {
        WaterTubeBoilerShape.Scan scan = WaterTubeBoilerShape.scan(level, worldPosition);
        WaterTubeUnit u = unit();
        if (scan.shape() == null) {
            problem = scan.problem();
            if (shape != null && u != null) u.boiler().bmsControl().requestShutdown();
            shape = null;
            fans = List.of();
            setStateFlag(BlockStateProperties.LIT, false);
            return;
        }
        shape = scan.shape();
        problem = "";
        if (simId.isNone() || !b.world().topology().exists(simId)) return;
        String fuel = fuelKey(u);
        BurnerKind kind = WaterTubeUnit.burnerKind(fuel);
        b.world().subsystem(SteamPlant.class).addWaterTubeBoiler(simId, kind, shape.burners().size(), shape.volumeM3(),
                shape.wallAreaM2(), shape.superheaters(), shape.economizers(), shape.airPreheaters(), fuel, AMBIENT_K);
        List<BlockPos> found = new ArrayList<>();
        for (DraftFanBlockEntity f : Ironworks.blockEntities(level, shape.bounds().inflate(LINK_RANGE), DraftFanBlockEntity.class))
            found.add(f.getBlockPos());
        fans = List.copyOf(found);
    }

    /** Топливо котла: газ в коллекторе, иначе угольная пыль, иначе прежнее (по умолчанию шахтный метан). */
    private String fuelKey(@Nullable WaterTubeUnit u) {
        ProcessGas g = gas.gas();
        if (g != null) return g.fluidId;
        if (!dust.getStackInSlot(0).isEmpty()) return PlantFuels.COAL_DUST;
        return u != null ? u.fuelKey() : ProcessGas.MINE_METHANE.fluidId;
    }

    private void supplyFuel(WaterTubeUnit u) {
        double used = u.takeFuelUsed();
        ProcessGas g = ProcessGas.byFluid(u.fuelKey());
        if (g != null) {
            if (used > 0) gas.draw(used / g.densityKgNm3);
            u.setFuelSupply(g == gas.gas() ? gas.amount() * g.densityKgNm3 : 0);
            return;
        }
        double kgPerItem = BoilerFuels.itemMassKg();
        dustDebtKg += used;
        while (dustDebtKg >= kgPerItem && !dust.getStackInSlot(0).isEmpty()) {
            dust.extractItem(0, 1, false);
            dustDebtKg -= kgPerItem;
        }
        if (dust.getStackInSlot(0).isEmpty()) dustDebtKg = 0;
        u.setFuelSupply(Math.max(0, dust.getStackInSlot(0).getCount() * kgPerItem - dustDebtKg));
    }

    private void supplyFeed(WaterTubeUnit u) {
        feedDebtKg += u.takeFeedUsed();
        int mb = (int) Math.min(feedDebtKg, feed.getFluidAmount());
        if (mb > 0) {
            feed.drain(mb, IFluidHandler.FluidAction.EXECUTE);
            feedDebtKg -= mb;
        }
        if (feed.isEmpty()) feedDebtKg = 0;
        WaterGrade grade = feed.isEmpty() ? null : WaterTreatmentBlockEntity.grade(feed.getFluid().getFluid());
        if (grade == null) grade = WaterGrade.RAW;
        double temp = grade == WaterGrade.DEAERATED ? Deaerator.outletTempK() : AMBIENT_K;
        u.setFeedSupply(Math.max(0, feed.getFluidAmount() - feedDebtKg), temp);
        u.setFeedQuality(grade.quality().hardnessMolEqM3(), grade.quality().o2KgKg(), grade.quality().chlorideKgKg());
    }

    /** Состояние блоков: факелы горелок, лампы щита, свист ПК, дым. */
    private void looks(WaterTubeUnit u) {
        boolean exploded = u.exploded();
        BmsState s = u.bms();
        setStateFlag(BlockStateProperties.LIT, u.outputs().flame && !exploded);
        setStateFlag(PcProperties.ALARM, exploded || s == BmsState.MFT || s == BmsState.POST_PURGE);
        if (shape == null) return;
        boolean gasFired = u.spec().burnerKind() != BurnerKind.PULVERIZED_COAL;
        List<BlockPos> burners = shape.burners();
        for (int i = 0; i < burners.size(); i++) {
            BlockPos p = burners.get(i);
            BlockState st = level.getBlockState(p);
            if (!st.hasProperty(PcProperties.GAS_FIRED)) continue;
            boolean lit = !exploded && i < u.spec().burners() && (u.boiler().mainFlame(i) || u.boiler().igniterFlame(i));
            BlockState t = st.setValue(BlockStateProperties.LIT, lit).setValue(PcProperties.GAS_FIRED, gasFired);
            if (t != st) level.setBlock(p, t, Block.UPDATE_CLIENTS);
        }
        if (u.reliefOpen() && !shape.drums().isEmpty() && level instanceof ServerLevel sl) {
            BlockPos d = shape.drums().get(0);
            sl.sendParticles(ParticleTypes.CLOUD, d.getX() + 0.5, d.getY() + 1.1, d.getZ() + 0.5, 3, 0.1, 0.1, 0.1, 0.2);
            if (!wasRelief) level.playSound(null, d, dev.denfry.promptcritical.mod.boiler.BoilerSounds.whistle(), SoundSource.BLOCKS, 2.0f, 0.8f);
        }
        wasRelief = u.reliefOpen();
        if (u.outputs().flame && level instanceof ServerLevel sl && level.random.nextInt(4) == 0) {
            for (BlockPos p : fans) {
                if (level.getBlockEntity(p) instanceof DraftFanBlockEntity f && !f.forcedDraft() && f.flowKgS() > 0) {
                    sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 1, 0.1, 0, 0.1, 0.02);
                }
            }
        }
    }

    // --- команды щита ---

    /** Команда BMS от игрока или автоматики; ответ BMS — в {@link SteamPlant#lastBmsResponse}. */
    public void bmsCommand(String action, UUID who) {
        SimLevelBinding b = Ironworks.binding(level);
        if (b == null || unit() == null) return;
        b.submit(new SimCommand.Operate(simId, action), new CommandSource.Panel(who, worldPosition.asLong()));
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        WaterTubeUnit u = unit();
        if (u == null) return;
        switch (id) {
            case PowerhousePanels.BUTTON_PURGE -> bmsCommand(SteamPlant.BMS_PURGE, player.getUUID());
            case PowerhousePanels.BUTTON_IGNITER -> bmsCommand(SteamPlant.BMS_IGNITER, player.getUUID());
            case PowerhousePanels.BUTTON_MAIN_FUEL -> bmsCommand(SteamPlant.BMS_MAIN_FUEL, player.getUUID());
            case PowerhousePanels.BUTTON_SHUTDOWN -> bmsCommand(SteamPlant.BMS_SHUTDOWN, player.getUUID());
            case PowerhousePanels.BUTTON_BYPASS -> {
                boolean on = !u.boiler().bmsControl().flameDetectorBypass(0);
                bmsCommand(SteamPlant.BMS_TOGGLE_BYPASS, player.getUUID());
                player.displayClientMessage(PowerhouseText.msg(on ? "bypass_on" : "bypass_off"), true);
                level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.6f);
            }
            case PowerhousePanels.BUTTON_COMB_MODE -> {
                if (u.combustionAuto()) u.setManual(u.fuelPosition(), u.airPosition(), u.draftPosition(), u.manualFeed());
                u.setCombustionAuto(!u.combustionAuto());
            }
            case PowerhousePanels.BUTTON_FEED_MODE -> {
                if (u.feedAuto()) u.setManual(u.manualFuel(), u.manualAir(), u.manualDraft(),
                        u.feedRateKgS() / (1.5 * u.ratedSteamKgS()));
                u.setFeedAuto(!u.feedAuto());
            }
            case PowerhousePanels.BUTTON_FUEL_DOWN, PowerhousePanels.BUTTON_FUEL_UP -> {
                int sign = id == PowerhousePanels.BUTTON_FUEL_UP ? 1 : -1;
                if (u.combustionAuto()) u.setPressureSetpoint(u.pressureSetpointPa() + sign * SETPOINT_STEP_PA);
                else u.setManual(u.manualFuel() + sign * MANUAL_STEP, u.manualAir(), u.manualDraft(), u.manualFeed());
            }
            case PowerhousePanels.BUTTON_AIR_DOWN, PowerhousePanels.BUTTON_AIR_UP -> u.setManual(u.manualFuel(),
                    u.manualAir() + (id == PowerhousePanels.BUTTON_AIR_UP ? 1 : -1) * MANUAL_STEP, u.manualDraft(), u.manualFeed());
            case PowerhousePanels.BUTTON_DRAFT_DOWN, PowerhousePanels.BUTTON_DRAFT_UP -> u.setManual(u.manualFuel(), u.manualAir(),
                    u.manualDraft() + (id == PowerhousePanels.BUTTON_DRAFT_UP ? 1 : -1) * MANUAL_STEP, u.manualFeed());
            case PowerhousePanels.BUTTON_FEED_DOWN, PowerhousePanels.BUTTON_FEED_UP -> u.setManual(u.manualFuel(), u.manualAir(),
                    u.manualDraft(), u.manualFeed() + (id == PowerhousePanels.BUTTON_FEED_UP ? 1 : -1) * MANUAL_STEP);
            default -> {
                return;
            }
        }
        level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, 1.2f);
    }

    @Override
    public PanelLayout panelLayout() {
        return PowerhousePanels.BOILER;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return dust;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[PowerhousePanels.FEED_L] = feed.getFluidAmount();
        WaterTubeUnit u = unit();
        int flags = shape == null ? 1 << PowerhousePanels.F_UNFORMED : 0;
        if (shape == null) out[PowerhousePanels.PROBLEM] = PowerhousePanels.PROBLEMS.indexOf(problem) + 1;
        if (u == null) {
            out[PowerhousePanels.FLAGS] = flags;
            return;
        }
        BoilerOutputs o = u.outputs();
        BmsState s = u.bms();
        double p = u.drumPressurePa();
        out[PowerhousePanels.P_KPA] = finite((p - SteamPlant.ATMOSPHERE_PA) / 1000);
        out[PowerhousePanels.LEVEL_PERMIL] = (int) Math.round(Math.max(0, Math.min(1,
                (u.drumLevelM() + PowerhousePanels.GLASS_SPAN_M / 2) / PowerhousePanels.GLASS_SPAN_M)) * 1000);
        out[PowerhousePanels.STEAM_C10] = finite((u.steamTempK() - 273.15) * 10);
        out[PowerhousePanels.DRAFT_PA10] = finite(Math.max(0, -o.furnacePressurePa) * 10);
        out[PowerhousePanels.FLUE_C10] = finite((o.fluegasExitK - 273.15) * 10);
        out[PowerhousePanels.FUEL_PERMIL] = permil(u.fuelPosition());
        out[PowerhousePanels.AIR_PERMIL] = permil(u.airPosition());
        out[PowerhousePanels.IDF_PERMIL] = permil(u.draftPosition());
        out[PowerhousePanels.STEAM_KGH] = finite(u.steamFlowKgS() * 3600);
        out[PowerhousePanels.FEED_KGH] = finite(u.feedRateKgS() * 3600);
        out[PowerhousePanels.EFF_PERMIL] = Double.isNaN(o.efficiency) ? -1 : finite(o.efficiency * 1000);
        out[PowerhousePanels.BMS] = s.ordinal();
        out[PowerhousePanels.TRIP] = u.lastTrip() == null ? 0 : u.lastTrip().ordinal() + 1;
        SimLevelBinding b = Ironworks.binding(level);
        BmsResponse r = b == null ? null : b.world().subsystem(SteamPlant.class).lastBmsResponse(simId);
        out[PowerhousePanels.RESPONSE] = r == null ? 0 : r.ordinal() + 1;
        out[PowerhousePanels.PURGE_S] = (int) u.boiler().bmsControl().purgeElapsedS();
        out[PowerhousePanels.FUEL_STOCK] = finite(u.fuelKey().equals(PlantFuels.COAL_DUST)
                ? dust.getStackInSlot(0).getCount() * BoilerFuels.itemMassKg() : gas.amount());
        out[PowerhousePanels.SET_KPA] = finite((u.pressureSetpointPa() - SteamPlant.ATMOSPHERE_PA) / 1000);
        out[PowerhousePanels.MAN_FUEL] = permil(u.manualFuel());
        out[PowerhousePanels.MAN_AIR] = permil(u.manualAir());
        out[PowerhousePanels.MAN_DRAFT] = permil(u.manualDraft());
        out[PowerhousePanels.MAN_FEED] = permil(u.manualFeed());
        out[PowerhousePanels.ALPHA100] = o.flame && Double.isFinite(o.combustion.excessAir) ? finite(o.combustion.excessAir * 100) : 0;
        var bms = u.boiler().bmsControl();
        flags |= (o.flame ? 1 << PowerhousePanels.F_FLAME : 0)
                | (u.reliefOpen() ? 1 << PowerhousePanels.F_RELIEF : 0)
                | (bms.flameDetectorBypass(0) ? 1 << PowerhousePanels.F_BYPASS : 0)
                | (u.combustionAuto() ? 1 << PowerhousePanels.F_COMB_AUTO : 0)
                | (u.feedAuto() ? 1 << PowerhousePanels.F_FEED_AUTO : 0)
                | (o.airKgS > 0.05 * u.spec().ratedAirKgS() ? 1 << PowerhousePanels.F_FANS : 0)
                | (u.exploded() ? 1 << PowerhousePanels.F_EXPLODED : 0)
                | (s == BmsState.MFT || s == BmsState.POST_PURGE ? 1 << PowerhousePanels.F_TRIP : 0)
                | (Math.abs(u.drumLevelM()) > 0.1 ? 1 << PowerhousePanels.F_LOW_LEVEL : 0)
                | (s == BmsState.PURGE_COMPLETE ? 1 << PowerhousePanels.F_PURGED : 0)
                | (u.spec().burnerKind() == BurnerKind.PULVERIZED_COAL ? 1 << PowerhousePanels.F_COAL : 0);
        out[PowerhousePanels.FLAGS] = flags;
    }

    private static int permil(double v) {
        return finite(v * 1000);
    }

    private static int finite(double v) {
        return Double.isFinite(v) ? (int) Math.round(v) : 0;
    }

    // --- игрок ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return dust;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return inlet;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.COAL_DUST.get()) || FluidUtil.getFluidHandler(stack).isPresent();
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (FluidUtil.interactWithFluidHandler(player, hand, inlet)) return ItemInteractionResult.SUCCESS;
        if (stack.is(PcItems.COAL_DUST.get())) {
            WaterTubeUnit u = unit();
            if (u != null && WaterTubeUnit.burnerKind(u.fuelKey()) != BurnerKind.PULVERIZED_COAL && u.bms() != BmsState.OFF) {
                player.displayClientMessage(PowerhouseText.msg("wrong_fuel"), true);
                return ItemInteractionResult.CONSUME;
            }
            ItemStack rest = dust.insertItem(0, stack.copy(), false);
            stack.setCount(rest.getCount());
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(Player player) {
        if (shape == null) {
            player.displayClientMessage(PowerhouseText.msg("unformed", PowerhouseText.problem(problem.isEmpty() ? "no_wall" : problem)), true);
            return InteractionResult.SUCCESS;
        }
        WaterTubeUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        String fuel = u.fuelKey().equals(PlantFuels.COAL_DUST)
                ? dust.getStackInSlot(0).getCount() + " × " + Component.translatable(PcItems.COAL_DUST.get().getDescriptionId()).getString()
                : gas.amount() + " mB";
        player.displayClientMessage(PowerhouseText.msg("status", PowerhouseText.bmsState(u.bms()),
                PowerhouseText.num(u.drumPressurePa() / 1e6, 2), fuel, feed.getFluidAmount()), true);
        return InteractionResult.SUCCESS;
    }

    /** Наливная горловина котла: вода — в питательный бак, горючий газ — в коллектор горелок. Сливать нельзя. */
    private final class Inlet implements IFluidHandler {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int t) {
            return t == 0 ? feed.getFluid() : gas.fluidStack();
        }

        @Override
        public int getTankCapacity(int t) {
            return t == 0 ? feed.getCapacity() : gas.capacity();
        }

        @Override
        public boolean isFluidValid(int t, FluidStack stack) {
            return t == 0 ? feed.isFluidValid(stack) : GasBuffer.isProcessGas(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (feed.isFluidValid(resource)) return feed.fill(resource, action);
            if (!GasBuffer.isProcessGas(resource)) return 0;
            WaterTubeUnit u = unit();
            ProcessGas g = GasBuffer.processGas(resource.getFluid());
            // другой тип горелки — только на остановленном котле
            if (u != null && u.bms() != BmsState.OFF && WaterTubeUnit.burnerKind(g.fluidId) != u.spec().burnerKind()) return 0;
            return gas.input().fill(resource, action);
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

    // --- устройство ---

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
        tag.put("gas", gas.save(registries));
        tag.put("feed", feed.writeToNBT(registries, new CompoundTag()));
        tag.put("dust", dust.serializeNBT(registries));
        tag.putDouble("feed_debt", feedDebtKg);
        tag.putDouble("dust_debt", dustDebtKg);
        tag.putDouble("dea_steam", deaeratorSteamKg);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        simId = new SimId(tag.getLong("sim_id"));
        if (tag.contains("gas")) gas.load(registries, tag.getCompound("gas"));
        if (tag.contains("feed")) feed.readFromNBT(registries, tag.getCompound("feed"));
        if (tag.contains("dust")) dust.deserializeNBT(registries, tag.getCompound("dust"));
        feedDebtKg = tag.getDouble("feed_debt");
        dustDebtKg = tag.getDouble("dust_debt");
        deaeratorSteamKg = tag.getDouble("dea_steam");
    }
}
