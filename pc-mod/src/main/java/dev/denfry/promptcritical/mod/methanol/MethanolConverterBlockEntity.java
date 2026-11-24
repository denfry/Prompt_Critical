package dev.denfry.promptcritical.mod.methanol;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.nitrogen.SteamReformerBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.methanol.MethanolLoop;
import dev.denfry.promptcritical.sim.methanol.MethanolReformer;
import dev.denfry.promptcritical.sim.methanol.MethanolSynthesis;
import dev.denfry.promptcritical.sim.nitrogen.ReformerTrain;
import dev.denfry.promptcritical.sim.nitrogen.Species;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Метанольный агрегат 1×2×1: своя трубчатая печь паровой конверсии ({@link MethanolReformer}), компрессор
 * синтез-газа и циркуляционное колесо на валу Create (вал сзади) и трубчатая колонна синтеза в контуре
 * ({@link MethanolLoop}). Товарный газ (сырьё и топливо печи) и питательная вода (технологический пар и вода
 * межтрубья колонны) — трубой к любой грани. Продувочный газ горит в печи. Сырой метанол уходит в колонну
 * ректификации ({@link MethanolColumnBlockEntity}), которая касается агрегата, вместе с теплом для её кипятильников:
 * паром межтрубья и половиной тепла охлаждения конвертированного газа [BALANCE]. Без колонны сырец копится в
 * сепараторе, пока тот не наполнится. Катализаторы — с руки: никелевый в печь, медный в колонну синтеза.
 */
public final class MethanolConverterBlockEntity extends KineticMachineBlockEntity<ShaftInput> implements PanelHost {
    public static final double NOMINAL_RPM = 64, RATED_W = 400e3, FEEDWATER_KG_L = 0.955;
    /** Доля тепла охлаждения конвертированного газа, годная кипятильникам ректификации [BALANCE]. */
    public static final double REFORMED_GAS_HEAT_SHARE = 0.5;
    /** Сепаратор сырца, моль метанола. */
    public static final double CRUDE_HOLDUP_MOL = 4000;
    public static final int MB = 8_000, STEP_TICKS = 5;

    private final ShaftInput shaft = new ShaftInput();
    private final FeedTank gas = new FeedTank(PcFluids.SALES_GAS.source.get(), MB);
    private final FeedTank water = new FeedTank(PcFluids.BOILER_FEEDWATER.source.get(), MB);
    private final IFluidHandler inlet = new PyrolysisFurnaceBlockEntity.MultiInlet(gas, water);
    private final MethanolReformer reformer = new MethanolReformer();
    private final MethanolLoop loop = new MethanolLoop();
    private long tick;
    private double loadW, ch4MolS, fuelW, crudeMeoh, crudeWater, totalKg;
    private boolean noFeed, noColumn, dry;

    public MethanolConverterBlockEntity(BlockPos pos, BlockState state) {
        super(MethanolBlockEntities.METHANOL_CONVERTER.get(), pos, state);
    }

    public MethanolReformer reformer() {
        return reformer;
    }

    public MethanolLoop loop() {
        return loop;
    }

    public FeedTank gas() {
        return gas;
    }

    public FeedTank water() {
        return water;
    }

    public double ch4MolS() {
        return ch4MolS;
    }

    /** Метанол, сделанный агрегатом за всё время (в сырце), кг. */
    public double totalKg() {
        return totalKg;
    }

    public double crudeMethanolMol() {
        return crudeMeoh;
    }

    public boolean noColumn() {
        return noColumn;
    }

    private double availableW() {
        return RATED_W * Math.min(1, shaft.rpm(tick) / NOMINAL_RPM);
    }

    @Override
    protected ShaftInput unit(SimLevelBinding binding) {
        return shaft;
    }

    @Override
    protected ShaftInput shaft(ShaftInput u) {
        return u;
    }

    @Override
    protected double powerW(ShaftInput u) {
        return u.rpm(tick) > 0 ? loadW : 0;
    }

    @Override
    protected double ratedW() {
        return RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return NOMINAL_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding binding, ShaftInput u) {
        tick = binding.world().clock().tick();
        if (level.getGameTime() % STEP_TICKS != 0) return;
        double dt = 0.05 * binding.world().clock().kSlow() * STEP_TICKS;
        MethanolColumnBlockEntity column = column();
        noColumn = column == null;
        ch4MolS = 0;
        fuelW = 0;
        double[] makeup = Species.flow();
        boolean on = loop.running() && reformer.activity() > 0 && loop.activity() > 0;
        if (on) {
            // печь идёт за контуром: сколько свежего газа он принял на прошлом шаге
            MethanolLoop.Result prev = loop.last();
            double want = MethanolReformer.RATED_CH4_MOL_S * reformer.rateFraction()
                    * (prev.circulationMolS() > 0 ? Math.max(0.1, prev.accepted()) : 1);
            double perMolFuel = reformer.fuelPerMolW();
            double jPerMolGas = SteamReformerBlockEntity.SALES_GAS_J_PER_NM3 / Species.MOL_PER_NM3 / SteamReformerBlockEntity.CH4_SHARE;
            double byGas = gas.amount() * Species.MOL_PER_NM3 * SteamReformerBlockEntity.CH4_SHARE / (1 + perMolFuel / jPerMolGas) / dt;
            double byWater = water.amount() / (reformer.steamToCarbon() * SteamReformerBlockEntity.WATER_L_PER_MOL) / dt;
            ch4MolS = Math.max(0, Math.min(want, Math.min(byGas, byWater)));
            noFeed = ch4MolS < 0.05 * want;
            if (noFeed) ch4MolS = 0;
            else makeup = reformer.step(dt, ch4MolS);
        } else {
            noFeed = false;
        }
        // межтрубье: вода на пар по расходу прошлого шага
        double shellL = loop.last().steamKgS() * dt / FEEDWATER_KG_L;
        double processL = ch4MolS * dt * reformer.steamToCarbon() * SteamReformerBlockEntity.WATER_L_PER_MOL;
        dry = loop.running() && water.amount() < processL + Math.max(shellL, 0.5);
        boolean full = crudeMeoh >= CRUDE_HOLDUP_MOL;
        double shaftW = full ? 0 : availableW();
        MethanolLoop.Result r = loop.step(dt, makeup, shaftW, !dry);
        // печь: топливо — товарный газ сверх продувочного газа контура
        if (ch4MolS > 0) {
            fuelW = reformer.fuelPerMolW() * ch4MolS;
            double fuelJ = Math.max(0, fuelW - r.purgeFuelW()) * dt;
            gas.take(ch4MolS * dt / SteamReformerBlockEntity.CH4_SHARE / Species.MOL_PER_NM3 + fuelJ / SteamReformerBlockEntity.SALES_GAS_J_PER_NM3);
        }
        water.take(processL + (dry ? 0 : r.steamKgS() * dt / FEEDWATER_KG_L));
        crudeMeoh += r.methanolMolS() * dt;
        crudeWater += r.waterMolS() * dt;
        totalKg += r.methanolMolS() * dt * Species.CH3OH.molarMass();
        loadW = r.shaftW();
        if (column != null && crudeMeoh > 0) {
            double heatW = r.dutyW() + REFORMED_GAS_HEAT_SHARE * reformer.solution().wasteHeatW() * ch4MolS;
            double taken = column.acceptCrude(crudeMeoh, crudeWater, heatW);
            double share = crudeMeoh > 0 ? taken / crudeMeoh : 0;
            crudeWater -= crudeWater * share;
            crudeMeoh -= taken;
        }
        setStateFlag(PcProperties.RUNNING, r.methanolMolS() > 0);
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    /** Колонна ректификации, касающаяся агрегата (любой его клетки). */
    @Nullable
    private MethanolColumnBlockEntity column() {
        // реактор реального масштаба: колонна — у патрубка метанола-сырца (восток, внизу), вплотную или по трубе
        if (wholeModelId() != null)
            return dev.denfry.promptcritical.mod.multiblock.WholeLinks.across(level, worldPosition, wholeModelFacing(), new BlockPos(2, 0, 0), Direction.EAST, MethanolColumnBlockEntity::at);
        for (BlockPos cell : new BlockPos[] {worldPosition, worldPosition.above()})
            for (Direction d : Direction.values()) {
                MethanolColumnBlockEntity c = MethanolColumnBlockEntity.at(level, cell.relative(d));
                if (c != null) return c;
            }
        return null;
    }

    // --- руки ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return inlet;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.CATALYST_STEAM_REFORMING.get()) || stack.is(PcItems.CATALYST_METHANOL.get());
    }

    /** Катализатор с руки: загрузка меняется, когда активность ниже 90 %. */
    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        String key = null;
        if (stack.is(PcItems.CATALYST_STEAM_REFORMING.get())) {
            if (reformer.activity() < 0.9) {
                reformer.loadCatalyst();
                key = "catalyst.ni";
            }
        } else if (stack.is(PcItems.CATALYST_METHANOL.get())) {
            if (loop.activity() < 0.9) {
                loop.loadCatalyst();
                key = "catalyst.cu";
            }
        } else {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (key == null) {
            player.displayClientMessage(MethanolText.msg("catalyst.full"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.8f, 0.8f);
        player.displayClientMessage(MethanolText.msg(key), true);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    /** Shift + ПКМ: пуск или останов. */
    @Override
    public InteractionResult use(Player player) {
        toggle(player);
        return InteractionResult.SUCCESS;
    }

    private void toggle(Player player) {
        if (!loop.running() && (reformer.activity() <= 0 || loop.activity() <= 0)) {
            player.displayClientMessage(MethanolText.msg("converter.no_catalyst"), true);
            return;
        }
        loop.setRunning(!loop.running());
        level.playSound(null, worldPosition, loop.running() ? SoundEvents.FIRECHARGE_USE : SoundEvents.PISTON_CONTRACT,
                SoundSource.BLOCKS, 0.6f, 0.7f);
        player.displayClientMessage(MethanolText.msg(loop.running() ? "converter.start" : "converter.stop"), true);
        setChanged();
    }

    // --- щит ---

    /**
     * Работает (арт — art/blockbench/petrochem/process_units.author.js): кипящая вода межтрубного пространства
     * отдаёт тепло синтеза паром — из продувки паросборника над конвертером курится пар, тем гуще, чем быстрее
     * циркуляционный компрессор гонит газ.
     */
    @Override
    protected void clientTick() {
        var fx = fx();
        if (fx == null || !runningState()) return;
        double n = Math.min(1.5, Math.abs(getSpeed()) / 64.0);
        boolean real = wholeModelId() != null;
        // у реального — предохранительный клапан парового барабана над корпусом
        net.minecraft.world.phys.Vec3 valve = real ? fx.at(-8, 210, -16) : fx.at(8, 31.6, 5);
        if (fx.chance(1 + 3 * n)) fx.wisp(valve, 0.1);
        if (fx.chance(0.3 * n)) fx.puff(valve, new net.minecraft.world.phys.Vec3(0, 0.08, 0), 2);
    }

    @Override
    public PanelLayout panelLayout() {
        return MethanolPanels.CONVERTER;
    }

    @Override
    public Component panelTitle() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public boolean panelValid(Player player) {
        return PanelHost.near(this, player);
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        MethanolLoop.Result r = loop.last();
        double toNm3h = 3600 / Species.MOL_PER_NM3;
        boolean on = r.circulationMolS() > 0;
        v[MethanolPanels.M_P_BAR] = (int) Math.round(loop.pressureBar());
        v[MethanolPanels.M_BED_C] = (int) Math.round(loop.bedK() - 273.15);
        v[MethanolPanels.M_OUT_MEOH] = (int) Math.round(r.outletMeoh() * 1e4);
        v[MethanolPanels.M_EQ_MEOH] = (int) Math.round(r.equilibriumMeoh() * 1e4);
        v[MethanolPanels.M_SN] = on ? (int) Math.round(Math.min(r.loopSn(), 99) * 100) : 0;
        v[MethanolPanels.M_MAKEUP_SN] = r.makeupMolS() > 0 ? (int) Math.round(Math.min(r.makeupSn(), 99) * 100)
                : (int) Math.round(Math.min(reformer.solution().sn(), 99) * 100);
        v[MethanolPanels.M_INERTS] = (int) Math.round(r.inerts() * 1e4);
        v[MethanolPanels.M_MEOH_KGH] = (int) Math.round(r.methanolMolS() * Species.CH3OH.molarMass() * 3600);
        v[MethanolPanels.M_CRUDE] = (int) Math.round(r.crudeStrength() * 1000);
        v[MethanolPanels.M_PURGE] = (int) Math.round(loop.purgeMolS() * toNm3h);
        v[MethanolPanels.M_CIRC] = (int) Math.round(loop.circulation() * 100);
        v[MethanolPanels.M_STEAM_BAR] = (int) Math.round(loop.steamBar() * 10);
        v[MethanolPanels.M_STEAM_KGH] = (int) Math.round(r.steamKgS() * 3600);
        v[MethanolPanels.M_SHAFT_KW] = (int) Math.round(loadW / 1e3);
        v[MethanolPanels.M_CU] = (int) Math.round(loop.activity() * 1000);
        v[MethanolPanels.M_NI] = (int) Math.round(reformer.activity() * 1000);
        v[MethanolPanels.M_REF_C] = (int) Math.round(reformer.outK() - 273.15);
        v[MethanolPanels.M_SC] = (int) Math.round(reformer.steamToCarbon() * 100);
        v[MethanolPanels.M_FEED] = (int) Math.round(ch4MolS / SteamReformerBlockEntity.CH4_SHARE * toNm3h);
        v[MethanolPanels.M_FLAGS] = (r.methanolMolS() > 0 ? 1 << MethanolPanels.MF_RUN : 0)
                | (loop.bedK() > MethanolSynthesis.SINTER_K ? 1 << MethanolPanels.MF_HOT : 0)
                | (loop.pressureBar() >= 0.98 * MethanolLoop.MAX_BAR ? 1 << MethanolPanels.MF_PMAX : 0)
                | (dry ? 1 << MethanolPanels.MF_DRY : 0) | (noColumn ? 1 << MethanolPanels.MF_NO_COLUMN : 0)
                | (loop.running() && noFeed ? 1 << MethanolPanels.MF_NO_FEED : 0)
                | (loop.activity() <= 0 ? 1 << MethanolPanels.MF_NO_CU : 0) | (reformer.activity() <= 0 ? 1 << MethanolPanels.MF_NO_NI : 0)
                | (reformer.steamToCarbon() < ReformerTrain.MIN_STEAM_TO_CARBON ? 1 << MethanolPanels.MF_CARBON : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        double purgeStep = 40 * Species.MOL_PER_NM3 / 3600;
        switch (id) {
            case MethanolPanels.MB_RUN -> toggle(player);
            case MethanolPanels.MB_PURGE_DOWN -> loop.setPurgeMolS(loop.purgeMolS() - purgeStep);
            case MethanolPanels.MB_PURGE_UP -> loop.setPurgeMolS(loop.purgeMolS() + purgeStep);
            case MethanolPanels.MB_STEAM_DOWN -> loop.setSteamBar(loop.steamBar() - 2);
            case MethanolPanels.MB_STEAM_UP -> loop.setSteamBar(loop.steamBar() + 2);
            case MethanolPanels.MB_CIRC_DOWN -> loop.setCirculation(loop.circulation() - 0.1);
            case MethanolPanels.MB_CIRC_UP -> loop.setCirculation(loop.circulation() + 0.1);
            case MethanolPanels.MB_SC_DOWN -> reformer.setSteamToCarbon(reformer.steamToCarbon() - 0.1);
            case MethanolPanels.MB_SC_UP -> reformer.setSteamToCarbon(reformer.steamToCarbon() + 0.1);
            case MethanolPanels.MB_T_DOWN -> reformer.setOutK(reformer.outK() - 5);
            case MethanolPanels.MB_T_UP -> reformer.setOutK(reformer.outK() + 5);
            case MethanolPanels.MB_RATE_DOWN -> reformer.setRateFraction(reformer.rateFraction() - 0.1);
            case MethanolPanels.MB_RATE_UP -> reformer.setRateFraction(reformer.rateFraction() + 0.1);
            default -> {
            }
        }
        setChanged();
    }

    // --- сохранение ---

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) return;
        gas.save(tag, "gas");
        water.save(tag, "water");
        MutableStateTree ref = new MutableStateTree();
        reformer.save(new TreeStateWriter(ref));
        tag.put("reformer", NbtStateTrees.toNbt(ref));
        MutableStateTree lp = new MutableStateTree();
        loop.save(new TreeStateWriter(lp));
        tag.put("loop", NbtStateTrees.toNbt(lp));
        tag.putDouble("crude_meoh", crudeMeoh);
        tag.putDouble("crude_water", crudeWater);
        tag.putDouble("total", totalKg);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        gas.load(tag, "gas");
        water.load(tag, "water");
        if (tag.contains("reformer")) reformer.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("reformer"))));
        if (tag.contains("loop")) loop.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("loop"))));
        crudeMeoh = tag.getDouble("crude_meoh");
        crudeWater = tag.getDouble("crude_water");
        totalKg = tag.getDouble("total");
    }
}
