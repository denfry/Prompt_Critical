package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.metallurgy.CastingSpec;
import dev.denfry.promptcritical.sim.metallurgy.CyclicMachine;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
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
 * Выбивная решётка {@code shakeout_grate} (08 §8.9): формы ставятся на решётку, остывшие выбиваются за цикл
 * (вал Create) — отливка с {@code casting_spec} и 90 % формовочной смеси на склад решётки.
 */
public final class ShakeoutGrateBlockEntity extends KineticMachineBlockEntity<CyclicMachine> implements PanelHost {
    /** Возврат формовочной смеси после выбивки. */
    public static final double SAND_RECOVERY = 0.9;
    private static final MachineInventory.Role[] ROLES = new MachineInventory.Role[9];

    static {
        Arrays.fill(ROLES, MachineInventory.Role.OUTPUT);
    }

    private final MachineInventory inv = new MachineInventory(ROLES, (slot, s) -> false, this::setChanged);
    private int readyCount;

    public ShakeoutGrateBlockEntity(BlockPos pos, BlockState state) {
        super(FoundryBlockEntities.SHAKEOUT_GRATE.get(), pos, state);
    }

    @Override
    @Nullable
    protected CyclicMachine unit(SimLevelBinding b) {
        return b.world().subsystem(FoundrySubsystem.class).unit(b.world().topology(), simId()) instanceof CyclicMachine u ? u : null;
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
        return 5_500;
    }

    @Override
    protected double nominalRpm() {
        return CyclicMachine.NOMINAL_RPM;
    }

    /** Первая остывшая форма над решёткой; {@code null} — нет. */
    @Nullable
    private SandMoldBlockEntity readyMold() {
        int ready = 0;
        SandMoldBlockEntity first = null;
        for (BlockPos top : tops()) {
            if (level.getBlockEntity(top) instanceof SandMoldBlockEntity m && m.readyCasting() != null) {
                if (first == null) first = m;
                ready++;
            }
        }
        readyCount = ready;
        return first;
    }

    private List<BlockPos> tops() {
        PcBlock block = (PcBlock) getBlockState().getBlock();
        List<BlockPos> out = new ArrayList<>(4);
        for (Footprint.Cell c : block.spec().footprint().cells(worldPosition, block.facing(getBlockState()))) out.add(c.pos().above());
        return out;
    }

    @Override
    protected void serverTick(SimLevelBinding b, CyclicMachine u) {
        SandMoldBlockEntity mold = readyMold();
        boolean loaded = mold != null && inv.outputFits(new ItemStack(PcItems.CASTING.get()));
        u.setLoaded(loaded);
        if (loaded && u.takeCycle()) {
            CastingSpec c = mold.readyCasting();
            int sand = (int) Math.round(mold.sandItems() * SAND_RECOVERY);
            mold.markKnockedOut();
            level.removeBlock(mold.getBlockPos(), false);
            dropOverflow(inv.output(SandMoldBlockEntity.casting(c)));
            if (sand > 0) dropOverflow(inv.output(new ItemStack(PcItems.FOUNDRY_SAND.get(), sand)));
        }
        setStateFlag(PcProperties.RUNNING, u.running());
    }

    private void dropOverflow(ItemStack rest) {
        if (!rest.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5,
                worldPosition.getY() + 1, worldPosition.getZ() + 0.5, rest);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
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
        inv.giveOutputs(player);
        readyMold();
        player.displayClientMessage(FoundryText.msg("shakeout", readyCount), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return FoundryPanels.SHAKEOUT;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[FoundryPanels.SHAFT_RPM10] = shaftRpm10();
        readyMold();
        out[FoundryPanels.K_READY] = readyCount;
        CyclicMachine u = unit();
        if (u == null) return;
        out[FoundryPanels.FLAGS] = u.running() ? 1 << FoundryPanels.F_RUNNING : 0;
        out[FoundryPanels.K_PROGRESS_PERMIL] = (int) Math.round(u.progress() * 1000);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) tag.put("inventory", inv.save(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket && tag.contains("inventory")) inv.load(registries, tag.getCompound("inventory"));
    }
}
