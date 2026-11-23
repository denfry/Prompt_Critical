package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.sim.environment.GasCleaning;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Мокрая известняковая сероочистка {@code fgd_absorber} (14 §14.2.3): суспензия CaCO₃ орошает газ при L/G =
 * {@value #LG} л/м³ → 1 − e^{−0,25·L/G}. Известняк — кальцит, {@value #CALCITE_KG} кг CaCO₃ на блок; на кг
 * уловленного SO₂ уходит 1,562 × 1,05 кг. Без известняка аппарат не очищает.
 */
public final class FgdAbsorberBlockEntity extends IronworksBlockEntity implements FlueGasCleaner {
    /** Насосы орошения суспензией; при недостатке орошения улавливается меньше SO₂, Вт (розетка или силовой щит рядом). */
    public static final double PUMP_W = 200e3;
    private double power = 1;
    public static final double LG = 15, CALCITE_KG = 10;
    private final ItemStackHandler limestone = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(Items.CALCITE);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** Известняк в суспензии, кг. */
    private double slurryKg;
    private double lastEta;

    public FgdAbsorberBlockEntity(BlockPos pos, BlockState state) {
        super(EnvironmentBlockEntities.FGD_ABSORBER.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) FlueGasCleaner.add(sl, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel sl) FlueGasCleaner.remove(sl, worldPosition);
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        power = dev.denfry.promptcritical.mod.grid.MachinePower.supply(level, worldPosition, slurryKg > 0 ? PUMP_W : 0);
        if (slurryKg < CALCITE_KG && !limestone.getStackInSlot(0).isEmpty()) {
            limestone.getStackInSlot(0).shrink(1);
            slurryKg += CALCITE_KG;
            setChanged();
        }
    }

    @Override
    public void clean(double[] q, double flowM3S, double dtS) {
        if (slurryKg <= 0) {
            lastEta = 0;
            return;
        }
        double eta = GasCleaning.fgd(LG) * power;
        double caught = q[0] * eta * dtS;
        double need = caught * GasCleaning.LIMESTONE_PER_SO2 * GasCleaning.CA_S;
        double share = need > slurryKg ? slurryKg / need : 1;
        slurryKg = Math.max(0, slurryKg - need * share);
        lastEta = eta * share;
        q[0] *= 1 - lastEta;
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return limestone;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(Items.CALCITE);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, limestone.insertItem(0, stack, false));
        return ItemInteractionResult.SUCCESS;
    }

    /** Shift + ПКМ: степень очистки и запас известняка. */
    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(Component.literal(String.format(Locale.ROOT, "η(SO₂) = %.3f · CaCO₃ %.1f kg + %d",
                lastEta, slurryKg, limestone.getStackInSlot(0).getCount())), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("limestone", limestone.serializeNBT(registries));
        tag.putDouble("slurry", slurryKg);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("limestone")) limestone.deserializeNBT(registries, tag.getCompound("limestone"));
        slurryKg = tag.getDouble("slurry");
    }
}
