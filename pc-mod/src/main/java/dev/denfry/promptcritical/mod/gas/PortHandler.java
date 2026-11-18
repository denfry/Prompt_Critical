package dev.denfry.promptcritical.mod.gas;

import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Патрубок установки на одной грани: трубы Create заливают в приёмные баки (каждый принимает свою жидкость) и
 * забирают из одного бака продукта этой грани.
 */
public final class PortHandler implements IFluidHandler {
    private final FeedTank[] inputs;
    private final ProductTank output;

    public PortHandler(ProductTank output, FeedTank... inputs) {
        this.inputs = inputs;
        this.output = output;
    }

    @Override
    public int getTanks() {
        return inputs.length + 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank < inputs.length ? inputs[tank].getFluidInTank(0) : output.getFluidInTank(0);
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank < inputs.length ? inputs[tank].getTankCapacity(0) : output.getTankCapacity(0);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank < inputs.length && inputs[tank].isFluidValid(0, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        for (FeedTank t : inputs) if (t.isFluidValid(0, resource)) return t.fill(resource, action);
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return output.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return output.drain(maxDrain, action);
    }
}
