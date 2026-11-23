package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.steelworks.SteelworksComponents;
import dev.denfry.promptcritical.sim.manufacturing.Wire;
import dev.denfry.promptcritical.sim.manufacturing.Workpiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
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

    /** Масло для закалки (18 §18.6.4): нефть и минеральные фракции нефтепереработки. */
    public static final TagKey<Fluid> QUENCH_OIL =
            TagKey.create(Registries.FLUID, PromptCritical.id("quench_oil"));
    /** Медный слиток идёт в волоку прутком Ø6 мм длиной в метр (≈ 250 г) [BALANCE]. */
    public static final double COPPER_ROD_M = 0.006, COPPER_ROD_LENGTH_M = 1.0;
    /** Пруток и поковку берут голой рукой и тянут в волоку только остывшими, К (60 °C) [моё]. */
    public static final double HANDLE_MAX_K = 333.15;

    /** Ведро масла в баке рядом — достаточно для закалки куска, мБ [моё]. */
    public static final int OIL_BATH_MB = 1000;

    /**
     * Масло рядом в кубе 3×3×3: источник жидкости тега {@link #QUENCH_OIL} (лужа нефти) или бак с ведром такой
     * жидкости — Create, цистерна завода, любой блок с жидкостным складом.
     */
    public static boolean oilNear(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (level.getFluidState(p).is(QUENCH_OIL) && level.getFluidState(p).isSource()) return true;
            if (p.equals(pos)) continue;
            var h = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, p.immutable(), null);
            if (h == null) continue;
            for (int t = 0; t < h.getTanks(); t++) {
                var f = h.getFluidInTank(t);
                if (f.getAmount() >= OIL_BATH_MB && f.getFluid().is(QUENCH_OIL)) return true;
            }
        }
        return false;
    }

    /** Предмет тянут в волоку: проволока, медный слиток или холодная круглая поковка до Ø12 мм из волочимой стали. */
    public static boolean drawable(ItemStack s) {
        return s.has(ManufacturingComponents.WIRE.get()) || s.is(Items.COPPER_INGOT)
                || s.has(ManufacturingComponents.WORKPIECE.get());
    }

    /** Проволока из предмета; поковка — только холодная и подходящая под {@link Wire#fromRod}. */
    @Nullable
    public static Wire readWire(ItemStack s, long now) {
        WireData d = s.get(ManufacturingComponents.WIRE.get());
        if (d != null) return d.wire();
        if (s.is(Items.COPPER_INGOT))
            return new Wire(Wire.Metal.COPPER, COPPER_ROD_M, COPPER_ROD_LENGTH_M, 0);
        Workpiece w = read(s, now);
        if (w == null || w.tempK() > HANDLE_MAX_K) return null;
        return Wire.fromRod(w);
    }

    public static ItemStack wireStack(Wire w) {
        ItemStack s = new ItemStack(ManufacturingItems.WIRE.get());
        s.set(ManufacturingComponents.WIRE.get(), WireData.of(w));
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
