package dev.denfry.promptcritical.mod.registry;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Жидкости и газы M2 (07 §7.22, 08 §8.20): существуют в трубах и баках (Create, гидросети); вёдер нет — газ из
 * ведра в мир не выливается; блок есть только у шахтной воды (затопление). Плотность газов отрицательная (всплывают), как принято в NeoForge.
 */
public final class PcFluids {
    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, PromptCritical.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, PromptCritical.MOD_ID);

    private static final List<Entry> ALL = new ArrayList<>();

    public static final Entry FLUE_GAS = gas("flue_gas", 450, -1);
    public static final Entry MINE_METHANE = gas("mine_methane", 293, -1);
    public static final Entry RAW_WATER = liquid("raw_water", 1000, 1000, 288);
    /** Шахтная вода: единственная с блоком — ею затапливаются выработки (08 §8.2.9); в источник не превращается. */
    public static final Entry MINE_WATER = liquid("mine_water", 1005, 1050, 293);
    // коксохим и домна (08 §8.4–8.5, этап 10 M3): газы в трубах Create — 1 мБ = 1 нм³
    public static final Entry COKE_OVEN_GAS = gas("coke_oven_gas", 303, -1);
    public static final Entry BLAST_FURNACE_GAS = gas("blast_furnace_gas", 313, -1);
    public static final Entry COAL_TAR = liquid("coal_tar", 1170, 6000, 333);
    public static final Entry CRUDE_BENZOL = liquid("crude_benzol", 880, 700, 293);

    static {
        MINE_WATER.props.block(PcBlocks.MINE_WATER);
    }

    /** Тип, источник и текущая форма одной жидкости. */
    public static final class Entry {
        public final String name;
        public final DeferredHolder<FluidType, FluidType> type;
        public final DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
        public final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
        private final BaseFlowingFluid.Properties props;

        private Entry(String name, Supplier<FluidType> typeFactory) {
            this.name = name;
            this.type = TYPES.register(name, typeFactory);
            BaseFlowingFluid.Properties[] props = new BaseFlowingFluid.Properties[1];
            this.source = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(props[0]));
            this.flowing = FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(props[0]));
            props[0] = new BaseFlowingFluid.Properties(type, source, flowing);
            this.props = props[0];
        }
    }

    private PcFluids() {
    }

    private static Entry gas(String name, int temperatureK, int density) {
        Entry e = new Entry(name, () -> new FluidType(FluidType.Properties.create()
                .density(density).viscosity(20).temperature(temperatureK).canSwim(false).canDrown(false)
                .canPushEntity(false).canExtinguish(false).supportsBoating(false)));
        ALL.add(e);
        return e;
    }

    private static Entry liquid(String name, int density, int viscosity, int temperatureK) {
        Entry e = new Entry(name, () -> new FluidType(FluidType.Properties.create()
                .density(density).viscosity(viscosity).temperature(temperatureK).canExtinguish(true)));
        ALL.add(e);
        return e;
    }

    public static List<Entry> all() {
        return Collections.unmodifiableList(ALL);
    }
}
