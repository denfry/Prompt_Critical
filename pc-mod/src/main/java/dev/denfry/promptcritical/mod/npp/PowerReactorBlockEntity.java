package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.automation.MechanismWiring;
import dev.denfry.promptcritical.mod.automation.WiredDevice;
import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.nuclear.FuelLot;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.accident.PlantAccident;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationSubsystem;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.radiation.FissionSource;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import dev.denfry.promptcritical.sim.reactor.ChannelReactor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Энергетический реактор T1 ({@link ChannelReactor}) — графитовая кладка с каналами 3×6×3 и пульт СУЗ на контроллере.
 * ГЦН ({@link PrimaryPumpBlockEntity}) и парогенераторы ({@link SteamGeneratorBlockEntity}) — в
 * {@value #LINK_RADIUS} блоках. Свежее топливо — ТВС канала АМ-1 в слот щита или воронкой: на заглушенном реакторе
 * загружается в пустые каналы от центра; выгрузка — кнопкой, по одному самому выгоревшему каналу, в слот
 * отработанного топлива (партия с выгоранием, Pu-239 и продуктами деления).
 *
 * <p>Клеммы СУЗ ({@link AutomationKind#REACTOR_IO}) на фасаде: «SCRAM» — внешняя АЗ от ПАЗ/ПЛК, «13–14» — контакт
 * «АЗ сработала», «N» — мощность 4–20 мА. Реактор — источник излучения ({@link RadiationService}) по мощности
 * делений и остаточному теплу; биологическая защита — блоки вокруг. Разрушенный канал выносит I-131 в зал.
 */
public final class PowerReactorBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost, WiredDevice {
    public static final int LINK_RADIUS = 12;
    /** Размазанная плотность канальной сборки АМ-1 (U-Mo в стальных трубках, графитовая втулка), г/см³. */
    public static final double ASSEMBLY_DENSITY = 4;
    /** I-131, выходящий из одного разрушенного канала (1 % равновесного запаса канала), Бк [оценка]. */
    public static final double I131_PER_FAILED_BQ = 2e12;

    private final ChannelReactor reactor = new ChannelReactor();
    /** Непрерывная работа без автоматической АЗ (11 §11.2.2 {@code hundred_days}). */
    private final dev.denfry.promptcritical.sim.reactor.ContinuousRun run = new dev.denfry.promptcritical.sim.reactor.ContinuousRun();
    /** Следование суточному графику (11 §11.2.2 {@code load_follow}). */
    private final dev.denfry.promptcritical.sim.reactor.LoadFollowWatch follow = new dev.denfry.promptcritical.sim.reactor.LoadFollowWatch();
    private final MechanismWiring wiring = new MechanismWiring();
    private final ItemStackHandler fuel = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == NppPanels.SLOT_FRESH && stack.is(PcItems.FUEL_ASSEMBLY.get()) && FuelLot.of(stack).designOrNull() == FuelDesign.AM_1
                    && FuelLot.of(stack).freshThermal();
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final List<BlockPos> pumps = new ArrayList<>(), generators = new ArrayList<>();
    /** Экспериментальных каналов облучения у АМ-1 (12 §12.1.1). */
    public static final int IRRADIATION_CHANNELS = 4;
    /** Каналы облучения: поглощение мишени, см², и игровое время, до которого отметка действительна. */
    private final java.util.Map<BlockPos, double[]> irradiation = new java.util.LinkedHashMap<>();
    private ChannelReactor.Trip lastTrip = ChannelReactor.Trip.MANUAL;
    /** LOCA и обесточивание (04c, M10): слой аварии {@link PlantAccident#AM1_T1} и его связь с миром. */
    private final ReactorAccidentSystems accident = new ReactorAccidentSystems(PlantAccident.AM1_T1);
    private int ticks;

    public PowerReactorBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.POWER_REACTOR.get(), pos, state);
    }

    public ChannelReactor reactor() {
        return reactor;
    }

    public ReactorAccidentSystems accident() {
        return accident;
    }

    public ItemStackHandler fuelSlots() {
        return fuel;
    }

    /**
     * Канал облучения у корпуса занимает экспериментальный канал и сообщает поглощение мишени, см²; отметка живёт две
     * секунды. Каналов не больше {@value #IRRADIATION_CHANNELS}: лишний получает {@code false} и потока не видит.
     */
    public boolean irradiationSlot(BlockPos channel, double absorberCm2, long now) {
        irradiation.values().removeIf(v -> v[1] < now);
        if (!irradiation.containsKey(channel) && irradiation.size() >= IRRADIATION_CHANNELS) return false;
        irradiation.put(channel.immutable(), new double[] {Math.max(0, absorberCm2), now + 40});
        return true;
    }

    private double irradiationAbsorberCm2(long now) {
        double s = 0;
        for (double[] v : irradiation.values()) if (v[1] >= now) s += v[0];
        return s;
    }

    private Direction facing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock b ? b.facing(s) : Direction.NORTH;
    }

    private void link(ServerLevel sl) {
        pumps.clear();
        generators.clear();
        AABB box = new AABB(worldPosition).inflate(LINK_RADIUS);
        for (PrimaryPumpBlockEntity p : Ironworks.blockEntities(sl, box, PrimaryPumpBlockEntity.class)) pumps.add(p.getBlockPos());
        for (SteamGeneratorBlockEntity g : Ironworks.blockEntities(sl, box, SteamGeneratorBlockEntity.class)) generators.add(g.getBlockPos());
        // сорванный с контура ГЦН под давлением — разрыв главного трубопровода
        java.util.Map<BlockPos, ReactorAccidentSystems.LoopPart> loop = new java.util.LinkedHashMap<>();
        for (BlockPos p : pumps) loop.put(p, ReactorAccidentSystems.LoopPart.PUMP);
        accident.link(sl, worldPosition, loop, reactor.loadedChannels() > 0);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        SimLevelBinding b = SimBindings.get(sl);
        if (b == null) return;
        if (ticks++ % 20 == 0) link(sl);
        double gameDt = 0.05 * b.world().clock().kSlow();
        // расход — сумма насосов; парогенераторы — средняя температура котлов, тепло — поровну
        double flow = 0;
        boolean pumpsDriven = false;
        for (BlockPos p : pumps)
            if (level.getBlockEntity(p) instanceof PrimaryPumpBlockEntity pump) {
                flow += pump.flowShare();
                pumpsDriven |= pump.drive() > 0.5;
            }
        reactor.setFlowFraction(flow);
        List<SteamGeneratorBlockEntity> sgs = new ArrayList<>();
        double sum = 0;
        for (BlockPos p : generators)
            if (level.getBlockEntity(p) instanceof SteamGeneratorBlockEntity sg && !Double.isNaN(sg.secondaryK())) {
                sgs.add(sg);
                sum += sg.secondaryK();
            }
        double secondary = sgs.isEmpty() ? Double.NaN : sum / sgs.size();
        AutomationUnit a = MechanismWiring.unit(level, simId(), AutomationKind.REACTOR_IO);
        reactor.setExternalTrip(a != null && a.loadEnergized(1) || accident.demandsTrip());
        // потеря теплоносителя по слою аварии — осушение каналов (паровой эффект АМ-1 отрицательный)
        var acc = accident.accident();
        reactor.setCoolantLoss(acc.active() ? 1 - Math.min(1, acc.levelFraction()) : 0);
        reactor.setIrradiationAbsorber(irradiationAbsorberCm2(level.getGameTime()));
        double q = reactor.step(gameDt, secondary);
        if (run.update(gameDt, reactor.power(), reactor.trip() != ChannelReactor.Trip.NONE && reactor.trip() != ChannelReactor.Trip.MANUAL))
            dev.denfry.promptcritical.mod.content.SimAchievements.nearby(sl, worldPosition, dev.denfry.promptcritical.mod.content.SimAchievements.HUNDRED_DAYS, "days", run.days());
        if (reactor.power() >= dev.denfry.promptcritical.mod.content.IndustrialComplex.REACTOR_POWER) dev.denfry.promptcritical.mod.content.IndustrialComplex.running(sl, worldPosition, dev.denfry.promptcritical.mod.content.IndustrialComplex.Part.REACTOR);
        if (follow.update(gameDt, dev.denfry.promptcritical.mod.grid.LoadProfiles.shape(sl), reactor.power(), reactor.trip() != ChannelReactor.Trip.NONE))
            dev.denfry.promptcritical.mod.content.SimAchievements.nearby(sl, worldPosition, dev.denfry.promptcritical.mod.content.SimAchievements.LOAD_FOLLOW, "hours", follow.hours());
        for (SteamGeneratorBlockEntity sg : sgs) sg.deliver(q / sgs.size());
        if (ticks % 100 == 1) accident.scan(sl, worldPosition);
        // слой аварии: тепло зоны, сток — парогенераторы; разогрев оголённой зоны разрушает самые нагруженные каналы
        int breach = accident.step(sl, b, simId(), worldPosition, gameDt, reactor.fissionW() + reactor.decayHeatW(), q,
                10e6, pumpsDriven, reactor.loadedChannels(), reactor.failedChannels());
        if (breach > 0 && reactor.failHottest(breach) > 0)
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 64 * 64))
                p.displayClientMessage(NppText.msg("channel_failed", breach).withStyle(ChatFormatting.DARK_RED), false);
        accident.radiation(sl, worldPosition, reactor.failedChannels());
        if (a != null) {
            a.setInput(AutomationUnit.IN_MAIN, reactor.trip() != ChannelReactor.Trip.NONE ? 1 : 0);
            a.setInput(AutomationUnit.IN_AUX, reactor.power());
        }
        Vec3 center = worldPosition.getCenter().add(0, 1, 0);
        PointSource src = FissionSource.reactor(reactor.fissionW(), reactor.decayHeatW());
        int failed = reactor.failedChannels();
        if (failed > 0) src = src.plus(Emitter.I131.source(failed * I131_PER_FAILED_BQ));
        if (ticks % 20 == 0) RadiationService.source(sl, center, src);
        events(sl);
        if (reactor.shutDown()) loadFresh();
        if (ticks % 100 == 0) setChanged();
    }

    private void events(ServerLevel sl) {
        ChannelReactor.Trip t = reactor.trip();
        if (t != lastTrip && t != ChannelReactor.Trip.NONE && lastTrip == ChannelReactor.Trip.NONE) {
            sl.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 0.6f);
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 48 * 48))
                p.displayClientMessage(NppText.msg("scram", tripName(t)).withStyle(ChatFormatting.RED), false);
        }
        lastTrip = t;
        if (reactor.xenonPitEscaped())
            dev.denfry.promptcritical.mod.content.SimAchievements.nearby(sl, worldPosition,
                    dev.denfry.promptcritical.mod.content.SimAchievements.XENON_PIT, "orm_min", reactor.xenonPit().minMargin());
        if (reactor.xenonPit().spoiled())
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 48 * 48))
                p.displayClientMessage(NppText.msg("orm_violated", Math.round(ChannelReactor.ORM_MIN_RODS)).withStyle(ChatFormatting.GOLD), false);
        if (reactor.newlyFailed() > 0) {
            sl.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 3, worldPosition.getZ() + 0.5, 30, 0.8, 0.5, 0.8, 0.05);
            sl.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6f, 1.6f);
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 64 * 64))
                p.displayClientMessage(NppText.msg("channel_failed", reactor.newlyFailed()).withStyle(ChatFormatting.RED), false);
        }
    }

    private static net.minecraft.network.chat.Component tripName(ChannelReactor.Trip t) {
        return net.minecraft.network.chat.Component.translatable("panel.promptcritical.npp.trip." + t.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Свежее топливо из слота — в первый пустой канал от центра. */
    private void loadFresh() {
        ItemStack s = fuel.getStackInSlot(NppPanels.SLOT_FRESH);
        if (s.isEmpty()) return;
        FuelLot l = FuelLot.of(s);
        for (int ch = 0; ch < ChannelReactor.CHANNELS; ch++) {
            if (reactor.loaded(ch)) continue;
            if (reactor.load(ch, l.uKg(), l.u235(), l.u234(), l.plutonium())) {
                if (dev.denfry.promptcritical.mod.nuclear.MoxShare.of(l).isMox()) dev.denfry.promptcritical.mod.content.SimAchievements.once(this, dev.denfry.promptcritical.mod.content.SimAchievements.MOX_LOADED, "pu_kg", 1);
                s.shrink(1);
                fuel.setStackInSlot(NppPanels.SLOT_FRESH, s);
                setChanged();
            }
            return;
        }
    }

    /** Выгрузка самого выгоревшего канала в слот отработанного топлива. */
    private void unload(@Nullable ServerPlayer player) {
        if (!reactor.shutDown()) {
            if (player != null) player.displayClientMessage(NppText.msg("not_shut_down"), true);
            return;
        }
        if (!fuel.getStackInSlot(NppPanels.SLOT_SPENT).isEmpty()) return;
        int best = -1;
        for (int ch = 0; ch < ChannelReactor.CHANNELS; ch++)
            if (reactor.loaded(ch) && (best < 0 || reactor.burnup(ch) > reactor.burnup(best))) best = ch;
        if (best < 0) return;
        ChannelReactor.ChannelFuel f = reactor.unload(best);
        if (f == null) return;
        FuelLot lot = FuelLot.irradiated(FuelDesign.AM_1, 0.955, FuelDesign.AM_1.rods, f.uraniumKg(), f.u235Kg(), f.u234() * f.hm0Kg(),
                f.u236Kg(), f.pu239Kg(), f.pu240Kg(), f.pu241Kg(), f.pu242Kg(), f.burnupMwdKg());
        ItemStack spent = lot.on(new ItemStack(PcItems.FUEL_ASSEMBLY.get()));
        // активность при выгрузке (12 §12.0.2): удельная мощность — номинал на весь тяжёлый металл зоны, сборка с
        // трубками из нержавейки (размазанная плотность ≈ 4 г/см³) — с Co-60 конструкций
        double hmZone = reactor.heavyMetalKg() + f.hm0Kg();
        double wPerG = hmZone > 0 ? ChannelReactor.NOMINAL_W / (hmZone * 1000) : 1;
        if (f.burnupMwdKg() > 0) spent.set(dev.denfry.promptcritical.mod.environment.ActivityData.ACTIVITY.get(),
                dev.denfry.promptcritical.mod.environment.SpentFuel.discharge(f.heavyMetalKg(), f.burnupMwdKg(), wPerG, ASSEMBLY_DENSITY,
                        level.getGameTime()));
        fuel.setStackInSlot(NppPanels.SLOT_SPENT, spent);
        if (player != null)
            player.displayClientMessage(NppText.msg("unloaded", best + 1, String.format(java.util.Locale.ROOT, "%.2f", f.burnupMwdKg())), true);
        setChanged();
    }

    // --- руки ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return fuel;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return fuel;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return fuel.isItemValid(NppPanels.SLOT_FRESH, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack left = fuel.insertItem(NppPanels.SLOT_FRESH, stack.copy(), false);
        int moved = stack.getCount() - left.getCount();
        if (moved == 0) return ItemInteractionResult.FAIL;
        if (!player.isCreative()) stack.shrink(moved);
        return ItemInteractionResult.SUCCESS;
    }

    /** Shift + ПКМ: аварийная защита (кнопка АЗ). */
    @Override
    public InteractionResult use(Player player) {
        reactor.scram();
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return NppPanels.REACTOR;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        ChannelReactor r = reactor;
        v[NppPanels.POWER] = (int) Math.round(Math.min(r.power(), 9.9) * 1000);
        double t = r.periodS();
        v[NppPanels.PERIOD] = t > 0 && t < 1000 ? (int) Math.round(t * 10) : 0;
        v[NppPanels.RHO_PCM] = (int) Math.round(r.reactivity() * 1e5);
        v[NppPanels.AZ] = (int) Math.round((1 - r.az()) * 1000);
        v[NppPanels.RR] = (int) Math.round((1 - r.rr()) * 1000);
        v[NppPanels.AR] = (int) Math.round((1 - r.ar()) * 1000);
        v[NppPanels.FUEL_C] = (int) Math.round(r.fuelK() - 273.15);
        v[NppPanels.GRAPHITE_C] = (int) Math.round(r.graphiteK() - 273.15);
        v[NppPanels.IN_C] = (int) Math.round(r.inletK() - 273.15);
        v[NppPanels.OUT_C] = (int) Math.round(r.outletK() - 273.15);
        v[NppPanels.FLOW] = (int) Math.round(r.flowFraction() * 100);
        v[NppPanels.QUALITY] = (int) Math.round(r.hottestChannelQuality() * 1000);
        v[NppPanels.SG_KW] = (int) Math.round(r.steamGeneratorW() / 1e3);
        v[NppPanels.XE_PCM] = (int) Math.round(r.xenonReactivity() * 1e5);
        v[NppPanels.BURNUP] = (int) Math.round(r.maxBurnup() * 100);
        v[NppPanels.LOADED] = r.loadedChannels();
        v[NppPanels.FAILED] = r.failedChannels();
        v[NppPanels.SETPOINT] = (int) Math.round(r.setpoint() * 100);
        v[NppPanels.TRIP] = r.trip().ordinal();
        v[NppPanels.FLAGS] = (r.trip() != ChannelReactor.Trip.NONE ? 1 << NppPanels.F_TRIPPED : 0)
                | (r.autoRegulator() ? 1 << NppPanels.F_AUTO : 0) | (!r.protection() ? 1 << NppPanels.F_PROT_OFF : 0)
                | (r.hottestChannelQuality() > ChannelReactor.CRITICAL_QUALITY ? 1 << NppPanels.F_DRYOUT : 0)
                | (generators.isEmpty() || r.steamGeneratorW() <= 0 && r.fissionW() > 1e6 ? 1 << NppPanels.F_NO_SG : 0)
                | (r.failedChannels() > 0 ? 1 << NppPanels.F_FAILED : 0)
                | (r.rrDrive() < 0 ? 1 << NppPanels.F_RR_UP : 0) | (r.rrDrive() > 0 ? 1 << NppPanels.F_RR_DOWN : 0)
                | (r.marginLow() ? 1 << NppPanels.F_ORM_LOW : 0);
        v[NppPanels.ORM] = (int) Math.round(r.operationalMargin() * 10);
        accident.readPanel(v, NppPanels.ACCIDENT);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case NppPanels.B_SCRAM -> reactor.scram();
            case NppPanels.B_RESET -> player.displayClientMessage(NppText.msg(reactor.resetTrip() ? "reset" : "reset_refused"), true);
            case NppPanels.B_RR_UP -> reactor.driveRr(-1);
            case NppPanels.B_RR_STOP -> reactor.driveRr(0);
            case NppPanels.B_RR_DOWN -> reactor.driveRr(1);
            case NppPanels.B_AUTO -> reactor.setAutoRegulator(!reactor.autoRegulator());
            case NppPanels.B_SP_DOWN -> reactor.setSetpoint(reactor.setpoint() - 0.05);
            case NppPanels.B_SP_UP -> reactor.setSetpoint(reactor.setpoint() + 0.05);
            case NppPanels.B_PROTECTION -> {
                reactor.setProtection(!reactor.protection());
                player.displayClientMessage(NppText.msg(reactor.protection() ? "protection_on" : "protection_off")
                        .withStyle(reactor.protection() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
            }
            case NppPanels.B_UNLOAD -> unload(player);
            case NppPanels.B_LOCA -> accident.cycleDrill(player);
            default -> {
            }
        }
        setChanged();
    }

    // --- клеммы ---

    @Override
    public BlockPos wiredPos() {
        return worldPosition;
    }

    @Override
    public AutomationKind wiredKind() {
        return AutomationKind.REACTOR_IO;
    }

    @Override
    public Direction wiredFacing() {
        return facing();
    }

    @Override
    public List<WiredDevice.Wire> wires() {
        return wiring.wires();
    }

    @Override
    public void wiresChanged() {
        MechanismWiring.changed(this);
    }

    @Override
    @Nullable
    public AutomationUnit automationUnit(AutomationSubsystem automation) {
        return simId().isNone() ? null : automation.unit(simId(), AutomationKind.REACTOR_IO);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        wiring.onLoad(this, this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        wiring.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        wiring.setRemoved(this, this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        wiring.save(tag);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    // --- сохранение ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("run_s", run.seconds());
        tag.put("fuel", fuel.serializeNBT(registries));
        MutableStateTree tree = new MutableStateTree();
        reactor.save(new TreeStateWriter(tree));
        tag.put("reactor", NbtStateTrees.toNbt(tree));
        wiring.save(tag);
        accident.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        run.restore(tag.getDouble("run_s"));
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("reactor")) reactor.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("reactor"))));
        wiring.load(tag);
        lastTrip = reactor.trip();
        accident.load(tag);
    }
}
