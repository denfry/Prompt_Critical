package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.metallurgy.CyclicMachine;
import dev.denfry.promptcritical.sim.metallurgy.OreDressing;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Магнитный сепаратор {@code magnetic_separator} (08 §8.5.1) на валу Create: за цикл — {@value #FEED_ITEMS} куска
 * магнетитовой руды ({@code raw_magnetite}); 85 % железа магнетита уходит в {@code iron_ore_concentrate} (66 % Fe),
 * остальное — хвосты ({@code stone_dust}). Гематит не магнитен — его сепаратор не берёт. Дробные килограммы копятся.
 */
public final class MagneticSeparatorBlockEntity extends KineticMachineBlockEntity<CyclicMachine> implements PanelHost {
    public static final int FEED_ITEMS = 4;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.OUTPUT,
            MachineInventory.Role.OUTPUT};

    private final MachineInventory inv = new MachineInventory(ROLES, (slot, s) -> OreChem.magnetic(s), this::setChanged);
    private double concentrateKg, tailingsKg, lastFe;

    public MagneticSeparatorBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.MAGNETIC_SEPARATOR.get(), pos, state);
    }

    @Override
    @Nullable
    protected CyclicMachine unit(SimLevelBinding b) {
        return Ironworks.shaftMachine(b, simId());
    }

    @Override
    protected ShaftInput shaft(CyclicMachine u) {
        return u.shaft;
    }

    @Override
    protected double powerW(CyclicMachine u) {
        return u.powerW();
    }

    @Override
    protected double ratedW() {
        return 11_000;
    }

    @Override
    protected double nominalRpm() {
        return CyclicMachine.NOMINAL_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding b, CyclicMachine u) {
        flush();
        ItemStack feed = inv.getStackInSlot(0);
        boolean ready = feed.getCount() >= FEED_ITEMS && concentrateKg < Ironworks.bulkKg() && tailingsKg < Ironworks.bulkKg();
        u.setLoaded(ready);
        if (ready && u.takeCycle()) {
            OreChem chem = OreChem.of(feed);
            double kg = FEED_ITEMS * Ironworks.bulkKg();
            double fe = chem == null ? 0 : Math.min(chem.fe(), OreDressing.MAGNETITE_FE);
            OreDressing.Split s = OreDressing.magneticSeparation(kg, fe, 1);
            feed.shrink(FEED_ITEMS);
            concentrateKg += s.concentrateKg();
            tailingsKg += s.tailingsKg();
            lastFe = s.concentrateFe();
            flush();
        }
        setStateFlag(PcProperties.RUNNING, u.running());
    }

    /** Целые предметы продуктов — в выходы. */
    private void flush() {
        double item = Ironworks.bulkKg();
        while (concentrateKg >= item && inv.outputFits(new ItemStack(PcItems.IRON_ORE_CONCENTRATE.get()))) {
            inv.output(new ItemStack(PcItems.IRON_ORE_CONCENTRATE.get()));
            concentrateKg -= item;
        }
        while (tailingsKg >= item && inv.outputFits(new ItemStack(PcItems.STONE_DUST.get()))) {
            inv.output(new ItemStack(PcItems.STONE_DUST.get()));
            tailingsKg -= item;
        }
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return OreChem.magnetic(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (inv.giveOutputs(player)) return InteractionResult.SUCCESS;
        player.displayClientMessage(IronworksText.msg("separator", IronworksText.num(lastFe * 100),
                IronworksText.num(concentrateKg), IronworksText.num(tailingsKg)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return IronworksPanels.SEPARATOR;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[IronworksPanels.SHAFT_RPM10] = shaftRpm10();
        out[IronworksPanels.S_FE_PCT10] = (int) Math.round(lastFe * 1000);
        out[IronworksPanels.S_CONC_KG] = (int) Math.round(concentrateKg);
        out[IronworksPanels.S_TAIL_KG] = (int) Math.round(tailingsKg);
        CyclicMachine u = unit();
        if (u != null) out[IronworksPanels.S_PROGRESS_PERMIL] = (int) Math.round(u.progress() * 1000);
        if (u != null && u.running()) out[IronworksPanels.FLAGS] = 1 << IronworksPanels.F_RUNNING;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) return;
        tag.put("inventory", inv.save(registries));
        tag.putDouble("concentrate_kg", concentrateKg);
        tag.putDouble("tailings_kg", tailingsKg);
        tag.putDouble("last_fe", lastFe);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        if (tag.contains("inventory")) inv.load(registries, tag.getCompound("inventory"));
        concentrateKg = tag.getDouble("concentrate_kg");
        tailingsKg = tag.getDouble("tailings_kg");
        lastFe = tag.getDouble("last_fe");
    }
}
