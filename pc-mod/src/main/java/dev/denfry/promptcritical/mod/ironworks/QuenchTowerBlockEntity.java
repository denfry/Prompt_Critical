package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.CokeQuenching;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
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
 * Тушильная башня {@code quench_tower} 3×3×3 (08 §8.4.3, мокрое тушение): горячий кокс в складе контрапции под
 * башней (тушильный вагон въехал под неё) или в собственном входном слоте поливается водой — 0,45 кг на кг кокса
 * испаряется ({@link CokeQuenching#WET_WATER_KG_PER_KG}), кокс выходит с влагой 4 %. Вода — трубой.
 */
public final class QuenchTowerBlockEntity extends IronworksBlockEntity {
    private static final int PERIOD = 10;
    private final GasBuffer water = new GasBuffer(16_000,
            s -> s.is(FluidTags.WATER) || s.is(PcFluids.RAW_WATER.source.get()), this::setChanged);
    private final MachineInventory inv = new MachineInventory(
            new MachineInventory.Role[] {MachineInventory.Role.INPUT, MachineInventory.Role.OUTPUT},
            (slot, s) -> isHot(s), this::setChanged);
    private int steamTicks;

    public QuenchTowerBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.QUENCH_TOWER.get(), pos, state);
    }

    static boolean isHot(ItemStack s) {
        IronworksComponents.CokeAssay a = s.get(IronworksComponents.COKE_ASSAY.get());
        return s.is(PcItems.COKE.get()) && a != null && a.hot();
    }

    /** Под башней: три блока ниже её основания. */
    AABB zone() {
        return new AABB(worldPosition.getX() - 1, worldPosition.getY() - 5, worldPosition.getZ() - 1,
                worldPosition.getX() + 2, worldPosition.getY() - 1, worldPosition.getZ() + 2);
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        if (steamTicks > 0 && --steamTicks == 0) setStateFlag(PcProperties.RUNNING, false);
        if (level.getGameTime() % PERIOD != 0) return;
        boolean any = false;
        List<IItemHandlerModifiable> stores = Ironworks.contraptionStores(level, zone());
        for (IItemHandlerModifiable st : stores) any |= quench(st);
        ItemStack in = inv.getStackInSlot(0);
        if (!in.isEmpty() && waterFor(in.getCount()) <= water.amount() && inv.outputFits(quenched(in))) {
            water.draw(waterFor(in.getCount()));
            inv.output(quenched(in));
            inv.setStackInSlot(0, ItemStack.EMPTY);
            any = true;
        }
        if (any) {
            steamTicks = 60;
            setStateFlag(PcProperties.RUNNING, true);
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.5f);
        }
    }

    private boolean quench(IItemHandlerModifiable st) {
        boolean any = false;
        for (int slot = 0; slot < st.getSlots(); slot++) {
            ItemStack s = st.getStackInSlot(slot);
            if (!isHot(s) || waterFor(s.getCount()) > water.amount()) continue;
            water.draw(waterFor(s.getCount()));
            st.setStackInSlot(slot, quenched(s));
            any = true;
        }
        return any;
    }

    private static double waterFor(int items) {
        return items * Ironworks.bulkKg() * CokeQuenching.WET_WATER_KG_PER_KG;
    }

    private static ItemStack quenched(ItemStack hot) {
        ItemStack out = hot.copy();
        IronworksComponents.CokeAssay a = hot.get(IronworksComponents.COKE_ASSAY.get());
        if (a != null) out.set(IronworksComponents.COKE_ASSAY.get(), a.quenched());
        return out;
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.RUNNING)) return;
        MachineFx fx = fx();
        for (int i = 0; i < 3; i++) fx.smoke(fx.at(8, 40, 8), false, 1.2);
        if (fx.chance(1)) fx.splash(fx.at(8, -20, 8), 1.0, 3);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return water.input();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return isHot(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (inv.giveOutputs(player)) return InteractionResult.SUCCESS;
        player.displayClientMessage(IronworksText.msg("quench_tower", water.amount()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("water", water.save(registries));
        tag.put("inv", inv.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("water")) water.load(registries, tag.getCompound("water"));
        if (tag.contains("inv")) inv.load(registries, tag.getCompound("inv"));
    }
}
