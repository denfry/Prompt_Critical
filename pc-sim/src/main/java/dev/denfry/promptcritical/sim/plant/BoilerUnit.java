package dev.denfry.promptcritical.sim.plant;

import dev.denfry.promptcritical.sim.combustion.BoilerInputs;
import dev.denfry.promptcritical.sim.combustion.BoilerOutputs;
import dev.denfry.promptcritical.sim.combustion.BoilerRupture;
import dev.denfry.promptcritical.sim.combustion.FireTubeBoiler;
import dev.denfry.promptcritical.sim.combustion.FireTubeBoilerSpec;
import dev.denfry.promptcritical.sim.combustion.FuelSpec;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.hydraulics.FlowLink;
import dev.denfry.promptcritical.sim.hydraulics.FluidNode;

/**
 * Котёл котельной: газовая сторона {@link FireTubeBoiler} и водяной узел в гидросети {@link SteamPlant}.
 *
 * <p>Поля «вход биндинга» биндинг пишет между тиками (серверный поток), «выход» — читает после тика (05 §5.2.1:
 * величины, которые домен берёт из мира раз в тик). Питательная вода — запас в баке блока: домен берёт из
 * {@link #feedSupplyKg} и копит взятое в {@link #feedUsedKg}, биндинг списывает это с бака и обнуляет счётчик. Пока
 * чанк выгружен, котёл дорабатывает остаток запаса.
 */
public final class BoilerUnit {
    final SimId id;
    final FireTubeBoiler boiler;
    final BoilerInputs in = new BoilerInputs();
    final BoilerOutputs out = new BoilerOutputs();
    String fuelKey;
    FuelSpec fuel;
    FluidNode drum;
    FlowLink relief, plug;
    EngineUnit[] engines = new EngineUnit[0];
    double ashKg, feedSupplyKg, feedUsedKg, feedTempK = 288.15;
    boolean reliefOpen, exploded;
    long reliefLifts;
    /** Начальное состояние водяного узла для ближайшей пересборки сети (новый котёл или загрузка); NaN — нет. */
    double initPressure = Double.NaN, initEnthalpy = Double.NaN;

    BoilerUnit(SimId id, FireTubeBoilerSpec spec, String fuelKey, FuelSpec fuel, double initialTempK) {
        this.id = id;
        this.fuelKey = fuelKey;
        this.fuel = fuel;
        this.boiler = new FireTubeBoiler(spec, fuel, initialTempK);
    }

    public SimId id() {
        return id;
    }

    public FireTubeBoiler boiler() {
        return boiler;
    }

    public FireTubeBoilerSpec spec() {
        return boiler.spec();
    }

    // --- вход биндинга ---

    /** Высота трубы над решёткой, м. */
    public void setChimneyHeight(double m) {
        boiler.setChimneyHeight(m);
    }

    /** Температура наружного воздуха, К. */
    public void setAmbient(double kelvin) {
        in.ambientK = kelvin;
    }

    /** Запас питательной воды в баке блока, кг, и её температура, К. */
    public void setFeedSupply(double kg, double tempK) {
        feedSupplyKg = Math.max(0, kg);
        feedTempK = tempK;
    }

    /** Взятая доменом вода с прошлого вызова, кг; счётчик обнуляется. */
    public double takeFeedUsed() {
        double kg = feedUsedKg;
        feedUsedKg = 0;
        return kg;
    }

    /** Накопленная зола, кг; биндинг выгружает её шлаком. */
    public double ashKg() {
        return ashKg;
    }

    public void removeAsh(double kg) {
        ashKg = Math.max(0, ashKg - kg);
    }

    // --- выход ---

    /** Давление в котле (паровое пространство), Па; NaN до первой сборки сети. */
    public double pressurePa() {
        return drum != null ? drum.pressure() : Double.NaN;
    }

    public double waterTempK() {
        return drum != null ? drum.temperature() : Double.NaN;
    }

    /** Уровень по водоуказательному стеклу (коллапсированный: в стекле вода не кипит), м от низа узла. */
    public double gaugeGlassLevelM() {
        return drum != null ? drum.collapsedLevel() : 0;
    }

    public double waterMassKg() {
        return drum != null ? drum.mass() : 0;
    }

    public boolean lit() {
        return boiler.isLit();
    }

    public boolean plugMelted() {
        return boiler.plugMelted();
    }

    public BoilerRupture rupture() {
        return boiler.rupture();
    }

    /** ПК сейчас открыт (свист). */
    public boolean reliefOpen() {
        return reliefOpen;
    }

    public long reliefLifts() {
        return reliefLifts;
    }

    /** Расход пара через ПК и выплавленную пробку, кг/с. */
    public double ventFlowKgS() {
        return (relief != null ? Math.max(0, relief.massFlow()) : 0) + (plug != null ? Math.max(0, plug.massFlow()) : 0);
    }

    public BoilerOutputs outputs() {
        return out;
    }

    public String fuelKey() {
        return fuelKey;
    }
}
