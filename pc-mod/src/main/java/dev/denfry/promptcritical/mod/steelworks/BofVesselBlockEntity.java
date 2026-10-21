package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksDeviceBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.LadleBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.Converter;
import dev.denfry.promptcritical.sim.metallurgy.ConverterPhase;
import dev.denfry.promptcritical.sim.metallurgy.ConverterUnit;
import dev.denfry.promptcritical.sim.metallurgy.MoltenWater;
import dev.denfry.promptcritical.sim.metallurgy.SteelMelt;
import dev.denfry.promptcritical.sim.metallurgy.SteelmakingConfig;
import dev.denfry.promptcritical.sim.metallurgy.SteelmakingLog;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Кислородный конвертер {@code bof_vessel} 3×2×3 (08 §8.6.1): корпус на цапфах, наклон — вал Create через порт
 * цапфы (передача {@value #GEAR}: 64 об/мин — ≈ 4 °/с). Вперёд (к фасаду, ≥ {@value #CHARGE_DEG}°) — завалка: из
 * ковша с чугуном перед горловиной и из слотов лома ({@code steel_scrap}, холодный чугун) и извести
 * ({@code burnt_lime}); назад (≤ −{@value #TAP_DEG}°) — выпуск в сталеразливочный ковш позади. Продувка — только в
 * вертикали с опущенной фурмой {@code oxygen_lance} над горловиной (2 блока над контроллером); пока фурма в
 * горловине, привод наклона заблокирован. Конвертерный газ — в бак газоотвода (трубы Create).
 */
public final class BofVesselBlockEntity extends IronworksDeviceBlockEntity<ConverterUnit> implements ShaftConsumer, PanelHost {
    /** Передача привода наклона: градусов корпуса на оборот вала. */
    public static final double GEAR = 3.6;
    public static final double CHARGE_DEG = 40, TAP_DEG = 80, MAX_TILT_DEG = 110;
    /** Чугун из ковша за тик, кг; наибольшая завалка чугуна, кг [BALANCE]. */
    public static final double POUR_KG_TICK = 200, MAX_HOT_METAL_KG = 12_000;
    /** Привод наклона, Вт [BALANCE]. */
    public static final double TILT_DRIVE_W = 75_000;
    /** Вода прогоревшей фурмы в металле, кг [BALANCE]. */
    public static final double LANCE_LEAK_KG = 30;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.INPUT};

    private final MachineInventory inv = new MachineInventory(ROLES, BofVesselBlockEntity::valid, this::setChanged);
    private final GasBuffer offgas = new GasBuffer(8_000, s -> s.getFluid().isSame(PcFluids.CONVERTER_GAS.source.get()), this::setChanged);
    /** Лом и известь, засыпанные до чугуна, кг: уйдут в завалку с первой порцией чугуна. */
    private double pendingScrapKg, pendingLimeKg;
    private double tiltDeg, clientTilt, rpm, powerW;
    private boolean healthy, interlocked;

    public BofVesselBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.BOF_VESSEL.get(), pos, state, ConverterUnit.class);
    }

    private static boolean valid(int slot, ItemStack s) {
        return slot == 0 ? s.is(PcItems.STEEL_SCRAP.get()) || s.is(PcItems.PIG_IRON.get()) : s.is(PcItems.BURNT_LIME.get());
    }

    // --- вал привода наклона через порт ---

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        this.rpm = rpm;
        this.healthy = healthy;
    }

    @Override
    public double shaftPowerW() {
        return powerW;
    }

    @Override
    public double ratedShaftW() {
        return TILT_DRIVE_W;
    }

    @Override
    public double nominalShaftRpm() {
        return 64;
    }

    public double tiltDeg() {
        return tiltDeg;
    }

    /** Наклон для рендера (клиент). */
    public double renderTiltDeg() {
        return clientTilt;
    }

    @Nullable
    public OxygenLanceBlockEntity lance() {
        return level.getBlockEntity(worldPosition.above(2)) instanceof OxygenLanceBlockEntity l ? l : null;
    }

    @Override
    protected void serverTick(SimLevelBinding b, ConverterUnit u) {
        double dtp = Ironworks.tickPhysS(b);
        OxygenLanceBlockEntity lance = lance();
        boolean lanceIn = lance != null && !lance.parked();
        // наклон
        double dDeg = healthy ? rpm * GEAR / 60 * 0.05 : 0;
        interlocked = lanceIn && dDeg != 0;
        if (interlocked) dDeg = 0;
        double before = tiltDeg;
        tiltDeg = Math.max(-MAX_TILT_DEG, Math.min(MAX_TILT_DEG, tiltDeg + dDeg));
        powerW = Math.abs(rpm) > 0 && !interlocked && tiltDeg != before ? TILT_DRIVE_W * Math.min(1, Math.abs(rpm) / 64) : 0;
        if (Math.abs(tiltDeg - clientTilt) > 1 || tiltDeg != before && Math.abs(tiltDeg) == MAX_TILT_DEG) {
            clientTilt = tiltDeg;
            syncClient();
        }
        u.setTilt(tiltDeg);
        u.setLance(lance == null ? ConverterUnit.MAX_LANCE_M : lance.heightM());
        // кислород из буфера фурмы
        if (lance != null) u.supplyOxygen(lance.oxygen().draw(u.oxygenDemandNm3S() * dtp));
        // газоотвод: что не влезло в бак — на свечу
        offgas.put(PcFluids.CONVERTER_GAS.source.get(), u.takeOffgasNm3(Double.MAX_VALUE));
        Converter c = u.converter();
        if (tiltDeg >= CHARGE_DEG) charge(c);
        if (tiltDeg <= -TAP_DEG && c.phase() == ConverterPhase.BLOWN) tap(c);
        events(b, u);
        setStateFlag(PcProperties.RUNNING, u.lastOxygenNm3S() > 0 && c.phase() == ConverterPhase.BLOWING);
    }

    private void charge(Converter c) {
        if (c.phase() != ConverterPhase.EMPTY && c.phase() != ConverterPhase.CHARGED) return;
        ItemStack scrap = inv.getStackInSlot(0), lime = inv.getStackInSlot(1);
        double item = Ironworks.bulkKg();
        if (!scrap.isEmpty()) {
            scrap.shrink(1);
            pendingScrapKg += item;
        }
        if (!lime.isEmpty()) {
            lime.shrink(1);
            pendingLimeKg += item;
        }
        double room = MAX_HOT_METAL_KG - c.metalKg();
        LadleBlockEntity ladle = room > 1 ? ladle(-3, -2) : null;
        SteelMelt hm = ladle == null || ladle.steel() || ladle.frozen() ? null : ladle.melt();
        double kg = 0;
        if (hm != null) kg = ladle.take(Math.min(POUR_KG_TICK, room));
        if (kg > 0 || c.metalKg() > 0 && (pendingScrapKg > 0 || pendingLimeKg > 0)) {
            c.charge(kg > 0 ? hm.withMass(kg) : new SteelMelt(0, 1600, Map.of()), pendingScrapKg, pendingLimeKg);
            pendingScrapKg = pendingLimeKg = 0;
            setChanged();
        }
    }

    private void tap(Converter c) {
        LadleBlockEntity ladle = ladle(2, 3);
        if (ladle != null && (ladle.metalKg() > 0 && !ladle.steel() || ladle.frozen())) ladle = null;
        SteelMelt steel = c.tap();
        if (steel == null) return;
        double rest = steel.massKg() - (ladle == null ? 0 : ladle.receive(steel, true));
        if (rest > 1) {
            // мимо ковша: сталь на площадку — лом
            int n = Ironworks.items(rest);
            BlockPos at = Steelworks.world(worldPosition, facing(), 0, 0, 2);
            if (n > 0) Ironworks.spill(level, at, new ItemStack(PcItems.STEEL_SCRAP.get(), n));
            level.playSound(null, at, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.7f);
        }
        level.playSound(null, worldPosition, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0f, 0.6f);
        setChanged();
    }

    /** Ковш в зоне модели по Z от {@code z0} до {@code z1} (впереди отрицательные). */
    @Nullable
    private LadleBlockEntity ladle(int z0, int z1) {
        Direction f = facing();
        BlockPos a = Steelworks.world(worldPosition, f, -2, -2, z0), c = Steelworks.world(worldPosition, f, 2, 2, z1);
        List<LadleBlockEntity> found = Ironworks.blockEntities(level, AABB.encapsulatingFullBlocks(a, c), LadleBlockEntity.class);
        return found.isEmpty() ? null : found.getFirst();
    }

    private void events(SimLevelBinding b, ConverterUnit u) {
        SteelmakingLog.Entry e;
        while ((e = u.log().poll()) != null) {
            switch (e.kind()) {
                case SLOPPING -> {
                    level.playSound(null, worldPosition, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.5f, 0.5f);
                    near(SteelworksText.msg("bof_slopping", SteelworksText.num(e.value())));
                }
                case OVERBLOWN -> near(SteelworksText.msg("bof_overblown"));
                case LANCE_DAMAGED -> {
                    double energy = MoltenWater.steelEnergyJ(LANCE_LEAK_KG, u.converter().tempK(), SteelmakingConfig.defaults());
                    Ironworks.blast(b, worldPosition.above(), energy, MoltenWater.radiusM(energy), MoltenWater.PRIORITY);
                    near(SteelworksText.msg("bof_lance_leak"));
                }
                case SCRAP_UNMELTED -> near(SteelworksText.msg("bof_scrap_unmelted", SteelworksText.num(e.value())));
                case PHASE -> {
                }
            }
        }
    }

    private void near(Component msg) {
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(16))) p.displayClientMessage(msg, false);
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.RUNNING)) return;
        MachineFx fx = fx();
        if (fx.chance(1)) fx.sparks(fx.at(8, 32, 8), 2);
        if (fx.chance(1)) fx.smoke(fx.at(8, 34, 8), true, 1.0);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return offgas.output();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return valid(0, stack) || valid(1, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        ConverterUnit u = unit();
        if (u != null) {
            Converter c = u.converter();
            player.displayClientMessage(SteelworksText.msg("bof", SteelworksText.phase(c.phase()), SteelworksText.num(tiltDeg),
                    SteelworksText.num(c.metalKg() / 1000), SteelworksText.num(c.tempK() - 273.15), SteelworksText.num(c.carbonFrac() * 100)), true);
        }
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return SteelworksPanels.CONVERTER;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        ConverterUnit u = unit();
        if (u == null) return;
        Converter c = u.converter();
        OxygenLanceBlockEntity lance = lance();
        out[SteelworksPanels.C_PHASE] = c.phase().ordinal();
        out[SteelworksPanels.C_TEMP_C] = c.metalKg() > 0 ? (int) Math.round(c.tempK() - 273.15) : 0;
        out[SteelworksPanels.C_O2_NM3H] = (int) Math.round(u.lastOxygenNm3S() * 3600);
        out[SteelworksPanels.C_O2_SET_NM3H] = (int) Math.round(u.oxygenSetpointNm3S() * 3600);
        out[SteelworksPanels.C_TILT] = (int) Math.round(tiltDeg);
        out[SteelworksPanels.C_C_PCT1000] = (int) Math.round(c.carbonFrac() * 100_000);
        out[SteelworksPanels.C_METAL_KG] = (int) Math.round(c.metalKg());
        out[SteelworksPanels.C_SCRAP_KG] = (int) Math.round(c.scrapKg() + pendingScrapKg);
        out[SteelworksPanels.C_SLAG_KG] = (int) Math.round(c.slagKg());
        out[SteelworksPanels.C_BASICITY10] = (int) Math.round(c.slagBasicity() * 10);
        out[SteelworksPanels.C_FEO_PCT] = (int) Math.round(c.slagFeOFrac() * 100);
        out[SteelworksPanels.C_O2_BLOWN] = (int) Math.round(c.o2BlownNm3());
        out[SteelworksPanels.C_LANCE_CM] = lance == null ? -1 : (int) Math.round(lance.heightM() * 100);
        out[SteelworksPanels.C_LANCE_SET_CM] = lance == null ? -1 : (int) Math.round(lance.setpointM() * 100);
        out[SteelworksPanels.C_O2_MB] = lance == null ? 0 : lance.oxygen().amount();
        out[SteelworksPanels.C_GAS_MB] = offgas.amount();
        int flags = 0;
        if (c.phase() == ConverterPhase.BLOWING && u.lastOxygenNm3S() > 0) flags |= 1 << SteelworksPanels.CF_BLOWING;
        if (interlocked) flags |= 1 << SteelworksPanels.CF_INTERLOCK;
        if (u.blowReady()) flags |= 1 << SteelworksPanels.CF_READY;
        if (c.carbonFrac() > 0 && c.carbonFrac() < Converter.OVERBLOW_C) flags |= 1 << SteelworksPanels.CF_OVERBLOWN;
        out[SteelworksPanels.C_FLAGS] = flags;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        ConverterUnit u = unit();
        OxygenLanceBlockEntity lance = lance();
        if (u == null) return;
        switch (id) {
            case SteelworksPanels.BTN_O2_DOWN -> u.setOxygenSetpoint(u.oxygenSetpointNm3S() - 0.05);
            case SteelworksPanels.BTN_O2_UP -> u.setOxygenSetpoint(u.oxygenSetpointNm3S() + 0.05);
            case SteelworksPanels.BTN_LANCE_DOWN -> {
                if (lance != null) lance.setSetpoint(lance.setpointM() - 0.1);
            }
            case SteelworksPanels.BTN_LANCE_UP -> {
                if (lance != null) lance.setSetpoint(lance.setpointM() + 0.1);
            }
            case SteelworksPanels.BTN_LANCE_PARK -> {
                if (lance != null) lance.setSetpoint(lance.parked() ? ConverterUnit.DEFAULT_LANCE_M : ConverterUnit.MAX_LANCE_M);
            }
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void writeClient(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("tilt", tiltDeg);
    }

    @Override
    protected void readClient(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("tilt")) clientTilt = tag.getDouble("tilt");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inv", inv.save(registries));
        tag.put("offgas", offgas.save(registries));
        tag.putDouble("tilt", tiltDeg);
        tag.putDouble("pending_scrap", pendingScrapKg);
        tag.putDouble("pending_lime", pendingLimeKg);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inv")) inv.load(registries, tag.getCompound("inv"));
        if (tag.contains("offgas")) offgas.load(registries, tag.getCompound("offgas"));
        tiltDeg = clientTilt = tag.getDouble("tilt");
        pendingScrapKg = tag.getDouble("pending_scrap");
        pendingLimeKg = tag.getDouble("pending_lime");
    }
}
