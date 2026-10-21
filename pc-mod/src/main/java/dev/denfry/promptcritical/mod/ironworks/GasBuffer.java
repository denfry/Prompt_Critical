package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.metallurgy.ProcessGas;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Бак газа или жидкости агрегата с дробным расходом: 1 мБ = 1 нм³ газа (= 1 л жидкости), за тик уходят доли
 * миллибакета — копятся в остатке. Снаружи — только наполнение ({@link #input()}) или только слив ({@link #output()}).
 */
public final class GasBuffer {
    private final FluidTank tank;
    private final Runnable onChange;
    private double carry;

    public GasBuffer(int capacityMb, Predicate<FluidStack> accepts, Runnable onChange) {
        this.onChange = onChange;
        this.tank = new FluidTank(capacityMb, accepts) {
            @Override
            protected void onContentsChanged() {
                GasBuffer.this.onChange.run();
            }
        };
    }

    /** Горючий газ завода в баке; {@code null} — пусто или не газ. */
    @Nullable
    public ProcessGas gas() {
        return processGas(tank.getFluid().getFluid());
    }

    public static ProcessGas processGas(Fluid f) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(f);
        return id.getNamespace().equals(PromptCritical.MOD_ID) ? ProcessGas.byFluid(id.getPath()) : null;
    }

    public static boolean isProcessGas(FluidStack s) {
        return processGas(s.getFluid()) != null;
    }

    /** Жидкость мода по id ({@code coke_oven_gas}, {@code coal_tar}, …). */
    public static Fluid fluid(String id) {
        for (PcFluids.Entry e : PcFluids.all()) if (e.name.equals(id)) return e.source.get();
        throw new IllegalArgumentException(id);
    }

    public int amount() {
        return tank.getFluidAmount();
    }

    public int capacity() {
        return tank.getCapacity();
    }

    public int room() {
        return tank.getCapacity() - tank.getFluidAmount();
    }

    public FluidStack fluidStack() {
        return tank.getFluid();
    }

    /** Взять до {@code amount} (дробно), вернуть взятое. */
    public double draw(double amount) {
        if (!(amount > 0)) return 0;
        double have = tank.getFluidAmount() + carry;
        double take = Math.min(amount, have);
        carry -= take;
        int whole = (int) Math.ceil(-carry - 1e-9);
        if (whole > 0) {
            tank.drain(whole, IFluidHandler.FluidAction.EXECUTE);
            carry += whole;
        }
        return take;
    }

    /** Положить {@code amount} среды {@code f} (дробно); вернуть, сколько поместилось. */
    public double put(Fluid f, double amount) {
        if (!(amount > 0)) return 0;
        if (!tank.isEmpty() && !tank.getFluid().is(f)) return 0;
        double room = tank.getCapacity() - tank.getFluidAmount() - Math.max(0, carry);
        double add = Math.min(amount, room);
        if (add <= 0) return 0;
        carry += add;
        int whole = (int) Math.floor(carry);
        if (whole > 0) {
            tank.fill(new FluidStack(f, whole), IFluidHandler.FluidAction.EXECUTE);
            carry -= whole;
        }
        return add;
    }

    public IFluidHandler tank() {
        return tank;
    }

    /** Снаружи только наливают. */
    public IFluidHandler input() {
        return new Restricted(tank, true);
    }

    /** Снаружи только сливают. */
    public IFluidHandler output() {
        return new Restricted(tank, false);
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag t = tank.writeToNBT(registries, new CompoundTag());
        t.putDouble("carry", carry);
        return t;
    }

    public void load(HolderLookup.Provider registries, CompoundTag t) {
        tank.readFromNBT(registries, t);
        carry = t.getDouble("carry");
    }

    private record Restricted(FluidTank tank, boolean fill) implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int i) {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int i) {
            return tank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int i, FluidStack stack) {
            return fill && tank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return fill ? tank.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return fill ? FluidStack.EMPTY : tank.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return fill ? FluidStack.EMPTY : tank.drain(maxDrain, action);
        }
    }
}
