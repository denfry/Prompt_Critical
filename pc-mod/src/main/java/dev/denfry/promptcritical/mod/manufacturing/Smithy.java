package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.steelworks.SteelworksComponents;
import dev.denfry.promptcritical.sim.manufacturing.Workpiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/** Общее у горна и наковальни: поковка в предмете, прокат цеха как заготовка, вода рядом. */
public final class Smithy {
    /** Кусок проката, который кузнец берёт в работу: полметра от прутка или листа [моё]. */
    public static final double STOCK_PIECE_M = 0.5;

    private Smithy() {
    }

    /** Предмет — поковка или стальной прокат цеха. */
    public static boolean forgeable(ItemStack s) {
        return s.has(ManufacturingComponents.WORKPIECE.get()) || s.has(SteelworksComponents.STEEL_STOCK.get());
    }

    /** Заготовка из предмета на тике {@code now}; прокат — кусок {@link #STOCK_PIECE_M} его сечения. */
    @Nullable
    public static Workpiece read(ItemStack s, long now) {
        WorkpieceData d = s.get(ManufacturingComponents.WORKPIECE.get());
        if (d != null) return d.workpiece(now);
        SteelworksComponents.SteelStock st = s.get(SteelworksComponents.STEEL_STOCK.get());
        if (st == null) return null;
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        Workpiece.Section sec = id.endsWith("_rod") ? Workpiece.Section.ROUND
                : id.endsWith("_plate") || id.endsWith("_sheet") ? Workpiece.Section.FLAT : Workpiece.Section.SQUARE;
        double a = st.thicknessM(), b = sec == Workpiece.Section.FLAT ? st.widthM() : a;
        return Workpiece.bar(st.grade(), sec, a, b, STOCK_PIECE_M, st.tempK(now));
    }

    /** Поковка как предмет. */
    public static ItemStack stack(Workpiece w, long now) {
        ItemStack s = new ItemStack(ManufacturingItems.WORKPIECE.get());
        s.set(ManufacturingComponents.WORKPIECE.get(), WorkpieceData.of(w, now));
        return s;
    }

    /** Вода рядом: источник воды или котёл с водой в кубе 3×3×3 вокруг. */
    public static boolean waterNear(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p).isSource()) return true;
            if (level.getBlockState(p).is(Blocks.WATER_CAULDRON)) return true;
        }
        return false;
    }
}
