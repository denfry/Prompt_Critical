package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.DeviceHost;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.DeviceMode;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.metallurgy.CyclicMachine;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import dev.denfry.promptcritical.sim.metallurgy.OreDressing;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Агломашина {@code sinter_strand} 3×2×3 (08 §8.5.1) на валу Create: за цикл — {@value #ORE_ITEMS} предметов
 * концентрата или руды (одной стопкой), коксовая мелочь {@value #BREEZE_KG_T} кг/т и известняк на основность со
 * щита; шихту поджигает горн — {@value #IGNITION_NM3} нм³ горючего газа на спекание. Мелочь вне окна 45–70 кг/т даёт
 * слабый агломерат: возврат {@value #WEAK_RETURN} массы теряется пылью [BALANCE].
 */
public final class SinterStrandBlockEntity extends IronworksBlockEntity implements DeviceHost, ShaftConsumer, PanelHost {
    public static final int ORE_ITEMS = 10;
    public static final double BREEZE_KG_T = 55, IGNITION_NM3 = 3, WEAK_RETURN = 0.4;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.INPUT,
            MachineInventory.Role.INPUT, MachineInventory.Role.OUTPUT, MachineInventory.Role.OUTPUT};

    private final MachineInventory inv = new MachineInventory(ROLES, SinterStrandBlockEntity::valid, this::setChanged);
    private final GasBuffer gas = new GasBuffer(4_000, GasBuffer::isProcessGas, this::setChanged);
    private double breezeKg, limeKg, sinterKg;
    private IronworksComponents.SinterAssay lastAssay = IronworksComponents.SinterAssay.DEFAULT;
    private int basicity10 = 15, litTicks;
    private boolean weak, chunkUnloading;
    private SimId simId = SimId.NONE;
    private double shaftRpm;

    public SinterStrandBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.SINTER_STRAND.get(), pos, state);
    }

    private static boolean valid(int slot, ItemStack s) {
        return switch (slot) {
            case 0 -> s.is(IronworksTags.SINTER_ORE);
            case 1 -> s.is(PcItems.COKE_BREEZE.get());
            case 2 -> s.is(IronworksTags.FLUX);
            default -> false;
        };
    }

    @Nullable
    private CyclicMachine unit() {
        return Ironworks.shaftMachine(Ironworks.binding(level), simId);
    }

    // --- вал Create через порт ---

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        shaftRpm = rpm;
        CyclicMachine u = unit();
        if (u != null) u.shaft.sample(rpm, healthy, tick);
    }

    @Override
    public double shaftPowerW() {
        CyclicMachine u = unit();
        return u == null ? 0 : u.powerW();
    }

    @Override
    public double ratedShaftW() {
        return 30_000;
    }

    @Override
    public double nominalShaftRpm() {
        return CyclicMachine.NOMINAL_RPM;
    }

    private double oreKg() {
        return ORE_ITEMS * Ironworks.bulkKg();
    }

    private double breezeNeedKg() {
        return BREEZE_KG_T * oreKg() / 1000;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        CyclicMachine u = Ironworks.shaftMachine(b, simId);
        if (u == null) return;
        flush();
        double item = Ironworks.bulkKg();
        if (breezeKg < breezeNeedKg() && !inv.getStackInSlot(1).isEmpty()) {
            inv.getStackInSlot(1).shrink(1);
            breezeKg += item;
        }
        ItemStack ore = inv.getStackInSlot(0);
        OreChem chem = OreChem.of(ore);
        double limeNeed = chem == null ? 0 : OreDressing.limestoneFor(oreKg(), chem.sio2(), chem.cao(), basicity10 / 10.0);
        if (limeKg < limeNeed && !inv.getStackInSlot(2).isEmpty()) {
            inv.getStackInSlot(2).shrink(1);
            limeKg += item;
        }
        boolean ready = chem != null && ore.getCount() >= ORE_ITEMS && breezeKg >= breezeNeedKg() && limeKg >= limeNeed
                && gas.amount() >= IGNITION_NM3 && sinterKg < item;
        u.setLoaded(ready);
        if (ready && u.takeCycle()) {
            gas.draw(IGNITION_NM3);
            double breeze = breezeNeedKg();
            OreDressing.Sinter s = OreDressing.sinter(oreKg(), chem.fe(), chem.sio2(), chem.cao(), breeze, limeNeed);
            ore.shrink(ORE_ITEMS);
            breezeKg -= breeze;
            limeKg -= limeNeed;
            weak = s.weak();
            sinterKg += s.sinterKg() * (weak ? 1 - WEAK_RETURN : 1);
            lastAssay = IronworksComponents.SinterAssay.of(s.feFrac(), s.sio2Frac(), s.caoFrac());
            litTicks = 200;
            flush();
        }
        if (litTicks > 0) litTicks--;
        setStateFlag(PcProperties.RUNNING, u.running());
        setStateFlag(BlockStateProperties.LIT, litTicks > 0);
    }

    private void flush() {
        double item = Ironworks.bulkKg();
        while (sinterKg >= item) {
            ItemStack out = new ItemStack(PcItems.SINTER.get());
            out.set(IronworksComponents.SINTER_ASSAY.get(), lastAssay);
            if (!inv.outputFits(out)) return;
            inv.output(out);
            sinterKg -= item;
        }
    }

    @Override
    protected void clientTick() {
        BlockState s = getBlockState();
        if (!s.hasProperty(BlockStateProperties.LIT) || !s.getValue(BlockStateProperties.LIT)) return;
        MachineFx fx = fx();
        if (fx.chance(2)) fx.smoke(fx.at(0, 30, 8), false, 1.0);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return gas.input();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return valid(0, stack) || valid(1, stack) || valid(2, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (inv.giveOutputs(player)) return InteractionResult.SUCCESS;
        player.displayClientMessage(IronworksText.msg("sinter", IronworksText.num(basicity10 / 10.0), IronworksText.num(breezeKg),
                IronworksText.num(limeKg), gas.amount()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return IronworksPanels.SINTER;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[IronworksPanels.SHAFT_RPM10] = (int) Math.round(Math.abs(shaftRpm) * 10);
        out[IronworksPanels.G_BASICITY10] = basicity10;
        out[IronworksPanels.G_BREEZE_KG] = (int) Math.round(breezeKg);
        out[IronworksPanels.G_LIME_KG] = (int) Math.round(limeKg);
        out[IronworksPanels.G_GAS_MB] = gas.amount();
        out[IronworksPanels.G_FE_PCT] = lastAssay.fePct();
        CyclicMachine u = unit();
        if (u != null) out[IronworksPanels.G_PROGRESS_PERMIL] = (int) Math.round(u.progress() * 1000);
        int flags = 0;
        if (u != null && u.running()) flags |= 1 << IronworksPanels.F_RUNNING;
        if (weak) flags |= 1 << IronworksPanels.F_ALARM;
        out[IronworksPanels.FLAGS] = flags;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == IronworksPanels.BTN_DOWN) basicity10 = Math.max(8, basicity10 - 1);
        else if (id == IronworksPanels.BTN_UP) basicity10 = Math.min(25, basicity10 + 1);
        setChanged();
    }

    public void setBasicity(double b) {
        basicity10 = (int) Math.max(8, Math.min(25, Math.round(b * 10)));
    }

    // --- DeviceHost ---

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
        return FoundrySubsystem.SINTER_STRAND;
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
        tag.put("inventory", inv.save(registries));
        tag.put("gas", gas.save(registries));
        tag.putDouble("breeze_kg", breezeKg);
        tag.putDouble("lime_kg", limeKg);
        tag.putDouble("sinter_kg", sinterKg);
        tag.putInt("basicity10", basicity10);
        tag.putIntArray("assay", new int[] {lastAssay.fePct(), lastAssay.sio2Pct(), lastAssay.caoPct()});
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        simId = new SimId(tag.getLong("sim_id"));
        if (tag.contains("inventory")) inv.load(registries, tag.getCompound("inventory"));
        if (tag.contains("gas")) gas.load(registries, tag.getCompound("gas"));
        breezeKg = tag.getDouble("breeze_kg");
        limeKg = tag.getDouble("lime_kg");
        sinterKg = tag.getDouble("sinter_kg");
        if (tag.contains("basicity10")) basicity10 = tag.getInt("basicity10");
        int[] a = tag.getIntArray("assay");
        if (a.length == 3) lastAssay = new IronworksComponents.SinterAssay(a[0], a[1], a[2]);
    }
}
