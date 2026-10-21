package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.sim.metallurgy.ProcessGas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Пылеуловитель {@code dust_catcher} 2×3×2 (08 §8.5.3): колошниковый газ домны в пределах
 * {@value BlastFurnaceControllerBlockEntity#LINK_RANGE} блоков приходит сюда очищенным — {@code blast_furnace_gas}
 * в бак, откуда его забирают трубы Create (кауперы, батарея). Чего бак не принял — горит на свечах колошника.
 */
public final class DustCatcherBlockEntity extends IronworksBlockEntity {
    private final GasBuffer outlet = new GasBuffer(32_000,
            s -> s.is(GasBuffer.fluid(ProcessGas.BLAST_FURNACE.fluidId)), this::setChanged);

    public DustCatcherBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.DUST_CATCHER.get(), pos, state);
    }

    public GasBuffer outlet() {
        return outlet;
    }

    /** Принять очищенный газ, нм³; вернуть принятое. */
    double accept(double nm3) {
        return outlet.put(GasBuffer.fluid(ProcessGas.BLAST_FURNACE.fluidId), nm3);
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return outlet.output();
    }

    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(IronworksText.msg("dust_catcher", outlet.amount()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("outlet", outlet.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("outlet")) outlet.load(registries, tag.getCompound("outlet"));
    }
}
