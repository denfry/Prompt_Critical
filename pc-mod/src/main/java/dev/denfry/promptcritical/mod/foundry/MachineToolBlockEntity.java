package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.metallurgy.CastingDefect;
import dev.denfry.promptcritical.sim.metallurgy.CastingSpec;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import dev.denfry.promptcritical.sim.metallurgy.MachineToolUnit;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Станок {@code machine_lathe} или {@code drill_press} (08 §8.10): отливка на входе, резец {@code cutting_tool} в
 * служебном слоте (его износ — прочность предмета), обработанная отливка на выходе. Резец и заготовка в работе живут в
 * домене ({@link MachineToolUnit}); предмет резца в слоте зеркалит износ.
 */
public final class MachineToolBlockEntity extends KineticMachineBlockEntity<MachineToolUnit> implements PanelHost {
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.SERVICE,
            MachineInventory.Role.OUTPUT};

    private final MachineToolUnit.Kind kind;
    private final MachineInventory inv = new MachineInventory(ROLES, this::valid, this::setChanged);

    private MachineToolBlockEntity(BlockEntityType<?> type, MachineToolUnit.Kind kind, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.kind = kind;
    }

    public static MachineToolBlockEntity lathe(BlockPos pos, BlockState state) {
        return new MachineToolBlockEntity(FoundryBlockEntities.MACHINE_LATHE.get(), MachineToolUnit.Kind.LATHE, pos, state);
    }

    public static MachineToolBlockEntity drillPress(BlockPos pos, BlockState state) {
        return new MachineToolBlockEntity(FoundryBlockEntities.DRILL_PRESS.get(), MachineToolUnit.Kind.DRILL_PRESS, pos, state);
    }

    private boolean valid(int slot, ItemStack s) {
        if (slot == 1) return s.is(PcItems.CUTTING_TOOL.get());
        CastingSpec c = s.get(FoundryComponents.CASTING_SPEC.get());
        return c != null && (c.machinedMask() & kind.operation) == 0 && c.defect() != CastingDefect.CHILL;
    }

    @Override
    @Nullable
    protected MachineToolUnit unit(SimLevelBinding b) {
        return b.world().subsystem(FoundrySubsystem.class).unit(b.world().topology(), simId()) instanceof MachineToolUnit u ? u : null;
    }

    @Override
    protected ShaftInput shaft(MachineToolUnit u) {
        return u.shaft;
    }

    @Override
    protected double powerW(MachineToolUnit u) {
        return u.powerW();
    }

    @Override
    protected double ratedW() {
        return kind.ratedW;
    }

    @Override
    protected double nominalRpm() {
        return 64;
    }

    @Override
    protected void serverTick(SimLevelBinding b, MachineToolUnit u) {
        syncTool(u);
        ItemStack in = inv.getStackInSlot(0);
        CastingSpec c = in.get(FoundryComponents.CASTING_SPEC.get());
        if (u.work() == null && c != null && u.load(c)) in.shrink(1);
        if (u.done()) {
            ItemStack out = SandMoldBlockEntity.casting(u.work());
            if (inv.outputFits(out)) inv.output(SandMoldBlockEntity.casting(u.unload()));
        }
        setStateFlag(PcProperties.RUNNING, u.running());
    }

    /** Предмет резца ↔ резец домена: вставили — ставится с износом предмета, вынули — снимается, иначе износ в предмет. */
    private void syncTool(MachineToolUnit u) {
        ItemStack tool = inv.getStackInSlot(1);
        boolean inUnit = !Double.isNaN(u.toolWear());
        if (!tool.isEmpty() && !inUnit) {
            u.insertTool(tool.getMaxDamage() > 0 ? (double) tool.getDamageValue() / tool.getMaxDamage() : 0);
        } else if (tool.isEmpty() && inUnit) {
            u.removeTool();
        } else if (inUnit && tool.getMaxDamage() > 0) {
            int damage = (int) Math.round(u.toolWear() * tool.getMaxDamage());
            if (damage != tool.getDamageValue()) {
                tool.setDamageValue(Math.min(damage, tool.getMaxDamage() - 1));
                setChanged();
            }
        }
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.CUTTING_TOOL.get()) || stack.is(PcItems.CASTING.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        CastingSpec c = stack.get(FoundryComponents.CASTING_SPEC.get());
        if (c != null && !valid(0, stack)) {
            player.displayClientMessage(FoundryText.msg(c.defect() == CastingDefect.CHILL ? "chill_rejected" : "not_needed"), true);
            return ItemInteractionResult.CONSUME;
        }
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        inv.giveOutputs(player);
        MachineToolUnit u = unit();
        if (u == null) return InteractionResult.SUCCESS;
        Object wear = Double.isNaN(u.toolWear()) ? FoundryText.msg("no_tool") : FoundryText.num(u.toolWear() * 100);
        player.displayClientMessage(u.work() == null
                ? FoundryText.msg("tool_idle", FoundryText.num(u.spindleRpm()), wear)
                : FoundryText.msg("tool", FoundryText.kind(u.work().kind()), FoundryText.num(u.progress() * 100),
                        FoundryText.num(u.spindleRpm()), wear), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return kind == MachineToolUnit.Kind.LATHE ? FoundryPanels.LATHE : FoundryPanels.DRILL_PRESS;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[FoundryPanels.SHAFT_RPM10] = shaftRpm10();
        out[FoundryPanels.L_KIND] = -1;
        out[FoundryPanels.L_WEAR_PERMIL] = -1;
        MachineToolUnit u = unit();
        if (u == null) return;
        out[FoundryPanels.FLAGS] = u.running() ? 1 << FoundryPanels.F_RUNNING : 0;
        out[FoundryPanels.L_SPINDLE_RPM] = (int) Math.round(u.spindleRpm());
        out[FoundryPanels.L_PROGRESS_PERMIL] = (int) Math.round(u.progress() * 1000);
        if (!Double.isNaN(u.toolWear())) out[FoundryPanels.L_WEAR_PERMIL] = (int) Math.round(u.toolWear() * 1000);
        if (u.work() != null) out[FoundryPanels.L_KIND] = u.work().kind().ordinal();
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
