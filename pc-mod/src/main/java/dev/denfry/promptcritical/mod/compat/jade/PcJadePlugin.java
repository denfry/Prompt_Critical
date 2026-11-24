package dev.denfry.promptcritical.mod.compat.jade;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.HideThingsExtensionProvider;
import snownee.jade.api.view.ViewGroup;

/**
 * Плагин Jade. Подсказка при наведении не должна заменять щиты: уровни и давления в баках машин мода игрок читает
 * по приборам, поэтому полоски жидкостей Jade для наших блок-сущностей скрыты; содержимое слотов, название
 * и мод остаются. Часть мультиблока показывается как его машина (по предмету выбора — якорю).
 */
@WailaPlugin(PromptCritical.MOD_ID)
public final class PcJadePlugin implements IWailaPlugin {
    private static final HideOwnFluids HIDE_FLUIDS = new HideOwnFluids();

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerFluidStorage(HIDE_FLUIDS, BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerFluidStorageClient(HIDE_FLUIDS);
        registration.usePickedResult(PcBlocks.MULTIBLOCK_PART.get());
        registration.usePickedResult(PcBlocks.PORT_CELL.get());
    }

    /** Пустая группа для блок-сущностей мода (Jade прячет вид), {@code null} для чужих — их обслужит следующий. */
    static final class HideOwnFluids extends HideThingsExtensionProvider<CompoundTag, FluidView> {
        private static final ResourceLocation UID = PromptCritical.id("hide_fluids");

        @Override
        public @Nullable List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
            return accessor instanceof BlockAccessor block && isOwn(block.getBlockEntity()) ? List.of() : null;
        }

        static boolean isOwn(@Nullable BlockEntity be) {
            if (be == null) return false;
            ResourceLocation type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            return type != null && PromptCritical.MOD_ID.equals(type.getNamespace());
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public int getDefaultPriority() {
            return -10_000;
        }
    }
}
