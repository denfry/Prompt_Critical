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
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationSubsystem;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.sim.consequence.DamageRequest;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.npp.SodiumFastReactor;
import dev.denfry.promptcritical.sim.radiation.FissionSource;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Натриевый быстрый реактор T4 ({@link SodiumFastReactor}) 3×6×3: бассейн с зоной и экраном внизу, поворотная пробка
 * с приводами стержней вверху. Зона собирается из одной ТВС МОКС (доля Pu 15–35 %), положенной в пустой заглушенный
 * реактор. В {@value #LINK_RADIUS} блоках — два насоса ({@link PrimaryPumpBlockEntity}: первый — первого контура,
 * второй — промежуточного) и парогенераторы натрий–вода ({@link SteamGeneratorBlockEntity}), которым отдаётся тепло
 * второго контура. Без ПГ отбора нет, и реактор глушит температура на выходе.
 *
 * <p>Течи (упрощение): перегрев выхода зоны выше {@value #CREEP_OUTLET_C} °C даёт течь первого контура (ползучесть
 * трубопроводов), перегрев второго контура выше {@value #SG_STRESS_C} °C — течь трубок ПГ. Натриевый пожар идёт в
 * {@code DamageBudget} как {@link DamageKind#FIRE_SPREAD} и даёт дым Na₂O; реакция натрий–вода при разрыве мембраны —
 * как {@link DamageKind#OVERPRESSURE_BREACH}.
 */
public final class SfrReactorBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost, WiredDevice {
    public static final int LINK_RADIUS = 12;
    public static final int CREEP_OUTLET_C = 700, SG_STRESS_C = 650;
    static final double MIN_PU = 0.15, MAX_PU = 0.35, PRIMARY_LEAK_KG_S = 2, SG_LEAK_KG_S = 0.5;
    /** Ход регулирующих стержней на один слышимый шаг привода, доля полного хода. */
    static final double ROD_STEP = 0.01;

    @Nullable
    private SodiumFastReactor reactor;
    private final MechanismWiring wiring = new MechanismWiring();
    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == SfrPanels.SLOT_FRESH && stack.is(PcItems.FUEL_ASSEMBLY.get()) && mox(FuelLot.of(stack));
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final List<BlockPos> pumps = new ArrayList<>(), generators = new ArrayList<>();
    private SodiumFastReactor.Trip lastTrip = SodiumFastReactor.Trip.MANUAL;
    private boolean discWas;
    private double lastRods = Double.NaN;
    private int ticks;
    private static long requestSeq;

    public SfrReactorBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.SFR_REACTOR.get(), pos, state);
    }

    static boolean mox(FuelLot l) {
        double hm = l.heavyMetalKg();
        return hm > 0 && l.burnup() <= 0 && l.pu() / hm >= MIN_PU && l.pu() / hm <= MAX_PU;
    }

    @Nullable
    public SodiumFastReactor reactor() {
        return reactor;
    }

    private void link(ServerLevel sl) {
        pumps.clear();
        generators.clear();
        AABB box = new AABB(worldPosition).inflate(LINK_RADIUS);
        for (PrimaryPumpBlockEntity p : Ironworks.blockEntities(sl, box, PrimaryPumpBlockEntity.class))
            if (pumps.size() < 2) pumps.add(p.getBlockPos());
        for (SteamGeneratorBlockEntity g : Ironworks.blockEntities(sl, box, SteamGeneratorBlockEntity.class)) generators.add(g.getBlockPos());
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        SimLevelBinding b = SimBindings.get(sl);
        if (b == null) return;
        if (ticks++ % 20 == 0) link(sl);
        if (reactor == null) {
            loadCore();
            return;
        }
        SodiumFastReactor r = reactor;
        double[] drive = new double[2];
        for (int i = 0; i < 2; i++)
            if (i < pumps.size() && level.getBlockEntity(pumps.get(i)) instanceof PrimaryPumpBlockEntity p) drive[i] = p.drive();
        r.setPumpDrive(drive[0], drive[1]);
        List<SteamGeneratorBlockEntity> out = new ArrayList<>();
        for (BlockPos p : generators)
            if (level.getBlockEntity(p) instanceof SteamGeneratorBlockEntity sg && !Double.isNaN(sg.secondaryK())) out.add(sg);
        r.setSteamDemand(out.isEmpty() ? 0 : 1.1 * SodiumFastReactor.NOMINAL_W);
        AutomationUnit a = MechanismWiring.unit(level, simId(), AutomationKind.REACTOR_IO);
        r.setExternalTrip(a != null && a.loadEnergized(1));
        r.setPrimaryLeak(r.outletK() - 273.15 > CREEP_OUTLET_C ? PRIMARY_LEAK_KG_S : 0);
        r.setSteamGeneratorLeak(r.intermediateHotK() - 273.15 > SG_STRESS_C ? SG_LEAK_KG_S : 0);
        r.step();
        for (SteamGeneratorBlockEntity sg : out) sg.deliver(r.steamGeneratorW() / out.size());
        if (a != null) {
            a.setInput(AutomationUnit.IN_MAIN, r.trip() != SodiumFastReactor.Trip.NONE ? 1 : 0);
            a.setInput(AutomationUnit.IN_AUX, r.power());
        }
        if (ticks % 20 == 0) {
            RadiationService.source(sl, worldPosition.getCenter().add(0, 1, 0),
                    FissionSource.reactor(r.power() * SodiumFastReactor.NOMINAL_W, Math.max(0, r.thermalW() - r.power() * SodiumFastReactor.NOMINAL_W)));
            accidents(sl, b, r);
        }
        events(sl, r);
        if (ticks % 100 == 0) setChanged();
    }

    /** Натриевый пожар и реакция натрий–вода: дым, огонь в {@code DamageBudget} и хлопок мембраны. */
    private void accidents(ServerLevel sl, SimLevelBinding b, SodiumFastReactor r) {
        GridPos o = new GridPos(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        if (r.sodiumFireW() > 0) {
            // секунда горения: энергия за интервал проверки (20 тиков ≈ 1 с)
            b.damage().submit(new DamageRequest(requestSeq++, simId(), null, DamageKind.FIRE_SPREAD, o, r.sodiumFireW(), 3, 2));
            double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
            int smoke = (int) Math.min(40, 5 + r.aerosolKgS() * 10);
            sl.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + 1, z, smoke, 1.5, 0.5, 1.5, 0.02);
            sl.sendParticles(ParticleTypes.FLAME, x, y, z, 10, 1.2, 0.2, 1.2, 0.01);
            sl.playSound(null, worldPosition, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.5f, 0.7f);
        }
        if (r.ruptureDiscBurst() && !discWas) {
            b.damage().submit(new DamageRequest(requestSeq++, simId(), null, DamageKind.OVERPRESSURE_BREACH, o,
                    r.sodiumWaterW() * 10, 3, 2));
            sl.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 2, worldPosition.getZ() + 0.5,
                    40, 1.5, 1, 1.5, 0.1);
            sl.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0f, 1.4f);
        }
        discWas = r.ruptureDiscBurst();
    }

    private void events(ServerLevel sl, SodiumFastReactor r) {
        SodiumFastReactor.Trip t = r.trip();
        driveSounds(sl, r, t != SodiumFastReactor.Trip.NONE && lastTrip == SodiumFastReactor.Trip.NONE);
        if (t != SodiumFastReactor.Trip.NONE && lastTrip == SodiumFastReactor.Trip.NONE) {
            sl.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 0.6f);
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 48 * 48))
                p.displayClientMessage(NppText.msg("scram", SfrPanels.reason(t)).withStyle(ChatFormatting.RED), false);
        }
        lastTrip = t;
    }

    /**
     * Приводы СУЗ над малой поворотной пробкой (art/blockbench/core/sfr_reactor.author.js, верх приводов в точке
     * (10, 15.6, 4) сегмента пробки): шаговый привод щёлкает на каждом шаге стержня — здесь щелчок на каждый процент
     * хода регулирующих стержней; при АЗ обесточенные захваты отпускают стержни аварийной защиты, и слышен удар
     * отключения.
     */
    private void driveSounds(ServerLevel sl, SodiumFastReactor r, boolean scram) {
        if (!(getBlockState().getBlock() instanceof PcBlock b)) return;
        double rods = r.controlPosition();
        if (Double.isNaN(lastRods)) lastRods = rods;
        boolean step = Math.abs(rods - lastRods) >= ROD_STEP && ticks % 3 == 0;
        if (!step && !scram) return;
        var p = dev.denfry.promptcritical.mod.boiler.BoilerGeometry.world(worldPosition.above(3), b.facing(getBlockState()), 10, 15.6, 4);
        if (scram) sl.playSound(null, p.x, p.y, p.z, dev.denfry.promptcritical.mod.sound.PcSfx.BREAKER_OPERATE.get(), SoundSource.BLOCKS, 1.2f, 0.7f);
        if (step) {
            sl.playSound(null, p.x, p.y, p.z, dev.denfry.promptcritical.mod.sound.PcSfx.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.6f,
                    0.8f + sl.getRandom().nextFloat() * 0.2f);
            lastRods = rods;
        }
    }

    /** Свежая ТВС МОКС становится зоной; горячий пуск — с ручной АЗ, взвод и стержни — игроку. */
    private void loadCore() {
        ItemStack s = fuel.getStackInSlot(SfrPanels.SLOT_FRESH);
        if (s.isEmpty()) return;
        FuelLot l = FuelLot.of(s);
        if (!mox(l)) return;
        reactor = new SodiumFastReactor(l.heavyMetalKg(), l.pu() / l.heavyMetalKg());
        dev.denfry.promptcritical.mod.content.SimAchievements.once(this, dev.denfry.promptcritical.mod.content.SimAchievements.MOX_LOADED, "pu_kg", l.pu());
        s.shrink(1);
        fuel.setStackInSlot(SfrPanels.SLOT_FRESH, s);
        lastTrip = reactor.trip();
        setChanged();
    }

    // --- руки и щит ---

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
        return fuel.isItemValid(SfrPanels.SLOT_FRESH, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack left = fuel.insertItem(SfrPanels.SLOT_FRESH, stack.copy(), false);
        int moved = stack.getCount() - left.getCount();
        if (moved == 0) return ItemInteractionResult.FAIL;
        if (!player.isCreative()) stack.shrink(moved);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (reactor != null) reactor.scramButton();
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return SfrPanels.REACTOR;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        SodiumFastReactor r = reactor;
        if (r == null) {
            v[SfrPanels.FLAGS] = 1 << SfrPanels.F_NO_CORE;
            return;
        }
        v[SfrPanels.POWER] = (int) Math.round(Math.min(r.power(), 9.9) * 1000);
        v[SfrPanels.RHO_PCM] = (int) Math.round(r.evaluateReactivity() * 1e5);
        v[SfrPanels.SCRAM_ROD] = (int) Math.round((1 - r.scramPosition()) * 1000);
        v[SfrPanels.CONTROL_ROD] = (int) Math.round((1 - r.controlPosition()) * 1000);
        v[SfrPanels.INLET_C] = (int) Math.round(r.inletK() - 273.15);
        v[SfrPanels.OUTLET_C] = (int) Math.round(r.outletK() - 273.15);
        v[SfrPanels.INT_HOT_C] = (int) Math.round(r.intermediateHotK() - 273.15);
        v[SfrPanels.FLOW] = (int) Math.round(r.primaryFlow() * 100);
        v[SfrPanels.STEAM] = (int) Math.round(r.steamKgS() * 10);
        v[SfrPanels.FUEL_C] = (int) Math.round(r.fuelK() - 273.15);
        v[SfrPanels.CORE_PU] = (int) Math.round(r.corePuKg() * 10);
        v[SfrPanels.BLANKET_PU_G] = (int) Math.round(r.blanketPuKg() * 1000);
        v[SfrPanels.BR] = (int) Math.round(r.breedingRatio() * 100);
        v[SfrPanels.TRIP] = r.trip().ordinal();
        v[SfrPanels.FLAGS] = (r.trip() != SodiumFastReactor.Trip.NONE ? 1 << SfrPanels.F_TRIPPED : 0)
                | (generators.isEmpty() ? 1 << SfrPanels.F_NO_STEAM : 0)
                | (r.sodiumFireW() > 0 ? 1 << SfrPanels.F_FIRE : 0)
                | (r.sodiumWaterW() > 0 ? 1 << SfrPanels.F_NA_WATER : 0)
                | (r.ruptureDiscBurst() ? 1 << SfrPanels.F_DISC : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        SodiumFastReactor r = reactor;
        if (r == null) return;
        switch (id) {
            case SfrPanels.B_SCRAM -> r.scramButton();
            case SfrPanels.B_RESET -> player.displayClientMessage(NppText.msg(r.resetTrip() ? "reset" : "reset_refused"), true);
            case SfrPanels.B_UP -> r.driveControl(-1);
            case SfrPanels.B_STOP -> r.driveControl(0);
            case SfrPanels.B_DOWN -> r.driveControl(1);
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
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock bl ? bl.facing(s) : Direction.NORTH;
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fuel", fuel.serializeNBT(registries));
        if (reactor != null) {
            tag.putDouble("hm", reactor.heavyMetalKg());
            tag.putDouble("pu0", reactor.initialPuFraction());
            MutableStateTree tree = new MutableStateTree();
            reactor.save(new TreeStateWriter(tree));
            tag.put("reactor", NbtStateTrees.toNbt(tree));
        }
        wiring.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("reactor") && tag.getDouble("hm") > 0 && tag.getDouble("pu0") > 0 && tag.getDouble("pu0") < 1) {
            reactor = new SodiumFastReactor(tag.getDouble("hm"), tag.getDouble("pu0"));
            reactor.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("reactor"))));
            lastTrip = reactor.trip();
        }
        wiring.load(tag);
    }
}
