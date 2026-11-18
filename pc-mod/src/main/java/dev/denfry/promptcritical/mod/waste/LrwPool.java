package dev.denfry.promptcritical.mod.waste;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.waste.LrwEvaporator;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Бак-сборник ЖРО у выпарной установки: принимает жидкие радиоактивные отходы и дезактивационные растворы и сразу
 * усредняет их состав (соли, активность на литр), как сборник трапных вод перед выпаркой. Состав по сорту жидкости —
 * номинальный: в трубах активность не переносится.
 *
 * <ul>
 *   <li>{@code liquid_radioactive_waste} — трапные воды АЭС: 5 г/л солей, Cs-137 2,2·10⁴, Cs-134 0,6·10⁴, Co-60
 *   0,9·10⁴ Бк/л (≈ 10⁻⁶ Ки/л; у трапных вод 10⁻⁷–10⁻⁵ Ки/л, IAEA TRS-370);</li>
 *   <li>{@code contaminated_decon_solution} — отработанный дезактивирующий раствор (щелочной перманганат, щавелевая
 *   кислота): 20 г/л реагентов, снятые с поверхностей отложения богаче Co-60 — 2·10⁵ Co-60 и 10⁵ Cs-137 Бк/л.</li>
 * </ul>
 */
public final class LrwPool implements IFluidHandler {
    static final ResourceLocation DECON = PromptCritical.id("contaminated_decon_solution");

    private final int capacityMb;
    private double litres, saltKg;
    private final EnumMap<Emitter, Double> bq = new EnumMap<>(Emitter.class);

    public LrwPool(int capacityMb) {
        this.capacityMb = capacityMb;
    }

    /** Номинальный состав сорта жидкости или null, если это не ЖРО. */
    static LrwEvaporator.Feed feedOf(Fluid f) {
        if (f == PcFluids.LIQUID_RADIOACTIVE_WASTE.source.get())
            return new LrwEvaporator.Feed(5, Map.of(Emitter.CS137, 2.2e4, Emitter.CS134, 0.6e4, Emitter.CO60, 0.9e4), 303.15);
        if (DECON.equals(BuiltInRegistries.FLUID.getKey(f)))
            return new LrwEvaporator.Feed(20, Map.of(Emitter.CO60, 2e5, Emitter.CS137, 1e5), 303.15);
        return null;
    }

    public double litres() {
        return litres;
    }

    public double fill() {
        return litres / capacityMb;
    }

    /** Усреднённый состав в баке (null — пусто). */
    public LrwEvaporator.Feed feed() {
        if (litres <= 1e-9) return null;
        EnumMap<Emitter, Double> perL = new EnumMap<>(Emitter.class);
        bq.forEach((k, v) -> perL.put(k, v / litres));
        return new LrwEvaporator.Feed(saltKg * 1000 / litres, perL, 303.15);
    }

    /** Убрать {@code l} литров (взяты на выпарку). */
    public void take(double l) {
        if (litres <= 0) return;
        double k = Math.max(0, 1 - Math.min(l, litres) / litres);
        litres *= k;
        saltKg *= k;
        bq.replaceAll((n, v) -> v * k);
        if (litres < 1e-9) {
            litres = 0;
            saltKg = 0;
            bq.clear();
        }
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return litres >= 1 ? new FluidStack(PcFluids.LIQUID_RADIOACTIVE_WASTE.source.get(), (int) litres) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return capacityMb;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return feedOf(stack.getFluid()) != null;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        LrwEvaporator.Feed f = feedOf(resource.getFluid());
        if (f == null) return 0;
        int n = (int) Math.min(resource.getAmount(), Math.floor(capacityMb - litres));
        if (n <= 0) return 0;
        if (action.execute()) {
            litres += n;
            saltKg += n * f.saltGL() / 1000;
            f.bqPerL().forEach((k, v) -> bq.merge(k, v * n, Double::sum));
        }
        return n;
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
        CompoundTag t = new CompoundTag();
        t.putDouble("l", litres);
        t.putDouble("salt", saltKg);
        CompoundTag a = new CompoundTag();
        bq.forEach((k, v) -> a.putDouble(k.name(), v));
        t.put("bq", a);
        tag.put(key, t);
    }

    public void load(CompoundTag tag, String key) {
        CompoundTag t = tag.getCompound(key);
        litres = t.getDouble("l");
        saltKg = t.getDouble("salt");
        bq.clear();
        CompoundTag a = t.getCompound("bq");
        for (String k : a.getAllKeys()) {
            try {
                bq.put(Emitter.valueOf(k), a.getDouble(k));
            } catch (IllegalArgumentException ignored) {
                // нуклид из другой версии
            }
        }
    }
}
