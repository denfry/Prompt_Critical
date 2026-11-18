package dev.denfry.promptcritical.mod.heavywater;

import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.sim.heavywater.HeavyWater;
import dev.denfry.promptcritical.sim.heavywater.HeavyWaterGrade;
import java.util.List;
import java.util.Set;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Патрубок воды с учётом изотопного состава: заливаемая вода принятых сортов переводится в килограммы с номинальной
 * долей D и уходит в {@link Sink} (каландр, куб колонны); слив — из баков продукта. Литры — при 20 °C.
 */
public final class GradedWaterPort implements IFluidHandler {
    /** Приёмник воды. */
    public interface Sink {
        /** Свободно, кг. */
        double roomKg();

        void add(double kg, double x);

        /** Сколько сейчас внутри, мБ (для показа трубам). */
        default int shownMb() {
            return 0;
        }
    }

    private final Sink sink;
    private final Set<HeavyWaterGrade> accepted;
    private final List<ProductTank> outlets;

    public GradedWaterPort(Sink sink, Set<HeavyWaterGrade> accepted, List<ProductTank> outlets) {
        this.sink = sink;
        this.accepted = accepted;
        this.outlets = outlets;
    }

    @Override
    public int getTanks() {
        return 1 + outlets.size();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank == 0 ? FluidStack.EMPTY : outlets.get(tank - 1).getFluidInTank(0);
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? Math.max(0, sink.shownMb()) : outlets.get(tank - 1).capacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        HeavyWaterGrade g = HeavyWaterFluids.gradeOf(stack.getFluid());
        return tank == 0 && g != null && accepted.contains(g);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        HeavyWaterGrade g = HeavyWaterFluids.gradeOf(resource.getFluid());
        if (g == null || !accepted.contains(g)) return 0;
        int mb = (int) Math.min(resource.getAmount(), Math.floor(HeavyWater.litres(Math.max(0, sink.roomKg()), g.x)));
        if (mb <= 0) return 0;
        if (action.execute()) sink.add(HeavyWater.kgFromLitres(mb, g.x), g.x);
        return mb;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        for (ProductTank t : outlets) {
            FluidStack s = t.drain(resource, action);
            if (!s.isEmpty()) return s;
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        for (ProductTank t : outlets) {
            FluidStack s = t.drain(maxDrain, action);
            if (!s.isEmpty()) return s;
        }
        return FluidStack.EMPTY;
    }
}
