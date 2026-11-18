package dev.denfry.promptcritical.mod.production;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Приёмный бак машины: трубы Create только заливают в него одну жидкость, машина расходует дробно. */
public final class FeedTank implements IFluidHandler {
    private final Fluid fluid;
    private final int capacity;
    private double amount;

    public FeedTank(Fluid fluid, int capacityMb) {
        this.fluid = fluid;
        this.capacity = capacityMb;
    }

    public Fluid fluid() {
        return fluid;
    }

    public double amount() {
        return amount;
    }

    public double fill() {
        return amount / capacity;
    }

    /** Израсходовать до {@code mb}; возвращает взятое. */
    public double take(double mb) {
        double t = Math.max(0, Math.min(mb, amount));
        amount -= t;
        return t;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        int n = (int) Math.floor(amount);
        return n > 0 ? new FluidStack(fluid, n) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return capacity;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return stack.is(fluid);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !resource.is(fluid)) return 0;
        int n = (int) Math.min(resource.getAmount(), Math.floor(capacity - amount));
        if (n > 0 && action.execute()) amount += n;
        return Math.max(0, n);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }

    public void save(CompoundTag tag, String key) {
        tag.putDouble(key, amount);
    }

    public void load(CompoundTag tag, String key) {
        amount = Math.clamp(tag.getDouble(key), 0, capacity);
    }
}
