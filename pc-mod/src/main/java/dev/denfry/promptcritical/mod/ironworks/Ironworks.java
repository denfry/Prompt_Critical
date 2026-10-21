package dev.denfry.promptcritical.mod.ironworks;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.sim.consequence.DamageRequest;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.metallurgy.CyclicMachine;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import dev.denfry.promptcritical.sim.metallurgy.IronmakingSubsystem;
import dev.denfry.promptcritical.sim.metallurgy.IronmakingUnit;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/** Общее для коксохима и домны в мире: время тика, двойники, склады контрапций Create, разлив и взрывы. */
public final class Ironworks {
    /** Теплота сгорания твёрдого топлива топок разогрева, Дж/кг, и КПД топки [BALANCE]. */
    public static final double SOLID_FUEL_J_KG = 25e6, FIREBOX_EFFICIENCY = 0.6;
    private static long requestSeq = 1L << 40;

    private Ironworks() {
    }

    /** Физических секунд в одном тике мира (0,05 с × k_slow): газ и материалы агрегатов класса 1 §8.0.3. */
    public static double tickPhysS(SimLevelBinding b) {
        return 0.05 * b.world().clock().kSlow();
    }

    @Nullable
    public static SimLevelBinding binding(@Nullable Level level) {
        return level instanceof ServerLevel s ? SimBindings.get(s) : null;
    }

    @Nullable
    public static <U extends IronmakingUnit> U unit(SimLevelBinding b, SimId id, Class<U> cls) {
        if (b == null || id.isNone()) return null;
        IronmakingUnit u = b.world().subsystem(IronmakingSubsystem.class).unit(b.world().topology(), id);
        return cls.isInstance(u) ? cls.cast(u) : null;
    }

    @Nullable
    public static CyclicMachine shaftMachine(SimLevelBinding b, SimId id) {
        if (b == null || id.isNone()) return null;
        return b.world().subsystem(FoundrySubsystem.class).unit(b.world().topology(), id) instanceof CyclicMachine m ? m : null;
    }

    public static double bulkKg() {
        return ServerConfig.METALLURGY_BULK_ITEM_MASS_KG.get();
    }

    /** Склады всех контрапций Create, задевающих объём. */
    public static List<IItemHandlerModifiable> contraptionStores(Level level, AABB box) {
        List<IItemHandlerModifiable> out = new ArrayList<>();
        for (AbstractContraptionEntity e : level.getEntitiesOfClass(AbstractContraptionEntity.class, box)) {
            if (e.getContraption() == null) continue;
            IItemHandlerModifiable items = e.getContraption().getStorage().getMountedItems();
            if (items != null && items.getSlots() > 0) out.add(items);
        }
        return out;
    }

    /** Положить по складам; вернуть не поместившееся. */
    public static ItemStack insert(List<? extends IItemHandler> stores, ItemStack stack) {
        ItemStack rest = stack;
        for (IItemHandler h : stores) {
            if (rest.isEmpty()) break;
            rest = ItemHandlerHelper.insertItemStacked(h, rest, false);
        }
        return rest;
    }

    /** Выбросить в мир у позиции. */
    public static void spill(Level level, BlockPos pos, ItemStack stack) {
        while (!stack.isEmpty()) {
            ItemStack part = stack.split(stack.getMaxStackSize());
            ItemEntity e = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, part, 0, 0.05, 0);
            e.setDefaultPickUpDelay();
            level.addFreshEntity(e);
        }
    }

    /** Взрыв через бюджет разрушений (05 §5.5): энергия, Дж; радиус, м; приоритет 0–9. */
    public static void blast(SimLevelBinding b, BlockPos at, double energyJ, double radiusM, int priority) {
        b.damage().submit(new DamageRequest(requestSeq++, SimId.NONE, null, DamageKind.BLAST,
                new GridPos(at.getX(), at.getY(), at.getZ()), energyJ, radiusM, priority));
    }

    /** BE класса {@code cls} в объёме (только загруженные чанки). */
    public static <T extends BlockEntity> List<T> blockEntities(Level level, AABB box, Class<T> cls) {
        List<T> out = new ArrayList<>();
        int x0 = SectionPos.blockToSectionCoord(Mth.floor(box.minX)), x1 = SectionPos.blockToSectionCoord(Mth.floor(box.maxX));
        int z0 = SectionPos.blockToSectionCoord(Mth.floor(box.minZ)), z1 = SectionPos.blockToSectionCoord(Mth.floor(box.maxZ));
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                if (!level.hasChunk(x, z)) continue;
                for (BlockEntity be : level.getChunk(x, z).getBlockEntities().values())
                    if (cls.isInstance(be) && !be.isRemoved() && box.contains(Vec3.atCenterOf(be.getBlockPos()))) out.add(cls.cast(be));
            }
        out.sort((a, b) -> a.getBlockPos().compareTo(b.getBlockPos()));
        return out;
    }

    /** Контроллер конструкции для клетки: многоклеточные блоки и части — их контроллер, иначе сама клетка. */
    public static BlockPos controllerOf(Level level, BlockPos p) {
        BlockPos c = CokeBatteryShape.controller(level, p, level.getBlockState(p));
        return c == null ? p : c;
    }

    /** Предметов массой {@code kg} (целых) и остаток, кг. */
    public static int items(double kg) {
        return (int) Math.floor(kg / bulkKg() + 1e-9);
    }
}
