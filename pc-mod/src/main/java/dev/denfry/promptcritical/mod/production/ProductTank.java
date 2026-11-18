package dev.denfry.promptcritical.mod.production;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Бак одного продукта машины: машина доливает в него сама, наружу (трубам Create) он только отдаёт. Объём копится
 * дробно — дебиты малы против тика, — в трубы уходит целыми миллибакетами.
 */
public final class ProductTank implements IFluidHandler {
    private final Fluid fluid;
    private final int capacity;
    private double amount;

    public ProductTank(Fluid fluid, int capacityMb) {
        this.fluid = fluid;
        this.capacity = capacityMb;
    }

    /** Долить; возвращает принятое, мБ. */
    public double add(double mb) {
        double take = Math.max(0, Math.min(mb, capacity - amount));
        amount += take;
        return take;
    }

    public double amount() {
        return amount;
    }

    public double room() {
        return capacity - amount;
    }

    public int capacity() {
        return capacity;
    }

    public double fill() {
        return amount / capacity;
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
        return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !resource.is(fluid)) return FluidStack.EMPTY;
        return drain(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        int n = (int) Math.min(maxDrain, Math.floor(amount));
        if (n <= 0) return FluidStack.EMPTY;
        if (action.execute()) amount -= n;
        return new FluidStack(fluid, n);
    }

    public void save(CompoundTag tag, String key) {
        tag.putDouble(key, amount);
    }

    public void load(CompoundTag tag, String key) {
        amount = Math.clamp(tag.getDouble(key), 0, capacity);
    }
}
