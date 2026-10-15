package dev.denfry.promptcritical.mod.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Документ на пюпитре. */
public class DocumentLecternBlockEntity extends BlockEntity {
    private ItemStack document = ItemStack.EMPTY;

    public DocumentLecternBlockEntity(BlockPos pos, BlockState state) {
        super(ContentRegistries.DOCUMENT_LECTERN.get(), pos, state);
    }

    public ItemStack document() {
        return document;
    }

    void setDocument(ItemStack stack) {
        document = stack.copyWithCount(1);
        setChanged();
    }

    ItemStack takeDocument() {
        ItemStack s = document;
        document = ItemStack.EMPTY;
        setChanged();
        return s;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!document.isEmpty()) tag.put("document", document.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        document = tag.contains("document") ? ItemStack.parseOptional(registries, tag.getCompound("document")) : ItemStack.EMPTY;
    }
}
