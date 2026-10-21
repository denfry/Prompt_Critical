package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.StockShape;
import dev.denfry.promptcritical.sim.metallurgy.StockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Общее для стали, проката и труб в мире: формы проката ↔ предметы, передача заготовок, координаты моделей. */
public final class Steelworks {
    private Steelworks() {
    }

    /** Форма проката предмета; {@code null} — не прокат (трубы, лом). */
    @Nullable
    public static StockShape shape(ItemStack s) {
        if (s.is(PcItems.STEEL_INGOT.get())) return StockShape.INGOT;
        if (s.is(PcItems.STEEL_BILLET.get())) return StockShape.BILLET;
        if (s.is(PcItems.STEEL_PLATE.get())) return StockShape.PLATE;
        if (s.is(PcItems.STEEL_ROD.get())) return StockShape.ROD;
        if (s.is(PcItems.STEEL_BEAM.get())) return StockShape.BEAM;
        return null;
    }

    public static Item item(StockShape shape) {
        return switch (shape) {
            case INGOT -> PcItems.STEEL_INGOT.get();
            case BILLET -> PcItems.STEEL_BILLET.get();
            case PLATE -> PcItems.STEEL_PLATE.get();
            case ROD -> PcItems.STEEL_ROD.get();
            case BEAM -> PcItems.STEEL_BEAM.get();
        };
    }

    public static boolean isPipe(ItemStack s) {
        return s.is(PcItems.SEAMLESS_PIPE.get()) || s.is(PcItems.WELDED_PIPE.get());
    }

    @Nullable
    public static SteelworksComponents.SteelStock stock(ItemStack s) {
        return s.get(SteelworksComponents.STEEL_STOCK.get());
    }

    /** Прокат или слиток с маркой — заготовка для печи, клети, прошивки. */
    public static boolean isStock(ItemStack s) {
        return shape(s) != null && stock(s) != null;
    }

    /** Заготовка предмета (масса — {@code metallurgy.ingot_mass_kg}); {@code null} — не прокат. */
    @Nullable
    public static StockState state(ItemStack s, long now) {
        StockShape shape = shape(s);
        SteelworksComponents.SteelStock st = stock(s);
        return shape == null || st == null ? null : st.stock(shape, Ironworks.bulkKg(), now);
    }

    /** Предмет проката из заготовки. */
    public static ItemStack piece(StockState s, long heatNo, long now) {
        ItemStack out = new ItemStack(item(s.shape()));
        out.set(SteelworksComponents.STEEL_STOCK.get(), SteelworksComponents.SteelStock.of(s, heatNo, now));
        return out;
    }

    /** Температура предмета стали сейчас, К (холодный и не сталь — комнатная). */
    public static double tempK(ItemStack s, long now) {
        SteelworksComponents.SteelStock st = stock(s);
        return st == null ? SteelworksComponents.AMBIENT_K : st.tempK(now);
    }

    /** Мировое смещение по модельному (фасад на север). */
    public static BlockPos world(BlockPos controller, Direction facing, int x, int y, int z) {
        return controller.offset(new BlockPos(x, y, z).rotate(Footprint.rotation(facing)));
    }

    /** Приёмник заготовки в клетке {@code pos} (клетка многоклеточного агрегата — его контроллер). */
    @Nullable
    public static PieceReceiver receiver(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        return level.getBlockEntity(Ironworks.controllerOf(level, pos)) instanceof PieceReceiver r ? r : null;
    }

    /** Отдать заготовку в клетку {@code pos}, идущую в направлении {@code travel}. */
    public static boolean handOff(Level level, BlockPos pos, ItemStack piece, Direction travel) {
        PieceReceiver r = receiver(level, pos);
        return r != null && r.acceptPiece(piece, travel);
    }
}
