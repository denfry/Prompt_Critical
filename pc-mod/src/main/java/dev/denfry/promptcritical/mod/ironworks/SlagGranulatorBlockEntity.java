package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Грануляция шлака {@code slag_granulator} 2×1×1 (08 §8.5.5): жидкий шлак из жёлоба домны (касается жёлоба или
 * стоит у лётки) гасится струёй воды — {@value #WATER_L_PER_KG} л на кг уходит паром [BALANCE], на выходе
 * {@code granulated_slag}. Без воды шлак не принимается — он остаётся на площадке.
 */
public final class SlagGranulatorBlockEntity extends IronworksBlockEntity {
    public static final double WATER_L_PER_KG = 1.0;
    private final GasBuffer water = new GasBuffer(16_000,
            s -> s.is(FluidTags.WATER) || s.is(PcFluids.RAW_WATER.source.get()), this::setChanged);
    private final MachineInventory out = new MachineInventory(
            new MachineInventory.Role[] {MachineInventory.Role.OUTPUT, MachineInventory.Role.OUTPUT}, (slot, s) -> false, this::setChanged);
    private double slagKg;
    private int steamTicks;

    public SlagGranulatorBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.SLAG_GRANULATOR.get(), pos, state);
    }

    /** Принять жидкий шлак, кг; вернуть принятое. */
    double accept(double kg) {
        if (!(kg > 0) || !out.outputFits(new ItemStack(PcItems.GRANULATED_SLAG.get()))) return 0;
        double take = Math.min(kg, water.amount() / WATER_L_PER_KG);
        if (!(take > 0)) return 0;
        water.draw(take * WATER_L_PER_KG);
        slagKg += take;
        double item = Ironworks.bulkKg();
        while (slagKg >= item && out.outputFits(new ItemStack(PcItems.GRANULATED_SLAG.get()))) {
            out.output(new ItemStack(PcItems.GRANULATED_SLAG.get()));
            slagKg -= item;
        }
        steamTicks = 40;
        setChanged();
        return take;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        if (steamTicks > 0) steamTicks--;
        setStateFlag(PcProperties.RUNNING, steamTicks > 0);
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.RUNNING)) return;
        MachineFx fx = fx();
        for (int i = 0; i < 2; i++) fx.smoke(fx.at(16, 14, 8), false, 0.8);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return out.external();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return water.input();
    }

    @Override
    public InteractionResult use(Player player) {
        if (out.giveOutputs(player)) return InteractionResult.SUCCESS;
        player.displayClientMessage(IronworksText.msg("granulator", water.amount(), IronworksText.num(slagKg)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("water", water.save(registries));
        tag.put("out", out.save(registries));
        tag.putDouble("slag_kg", slagKg);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("water")) water.load(registries, tag.getCompound("water"));
        if (tag.contains("out")) out.load(registries, tag.getCompound("out"));
        slagKg = tag.getDouble("slag_kg");
    }
}
