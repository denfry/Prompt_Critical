package dev.denfry.promptcritical.mod.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.combustion.DieselEngine;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * Условный рецепт топлива дизель-генератора Immersive Engineering (16 §16.7.3): IE знает только биодизель, а наш
 * {@code diesel_fuel} несёт тег {@code c:diesel}. Рецепт {@code immersiveengineering:generator_fuel} грузится только
 * при {@code neoforge:mod_loaded}; без IE его тип не нужен.
 * <p>
 * Время горения — по удельному расходу нашего дизеля ({@link DieselEngine#B_MIN_KG_PER_KWH}) и курсу FE: ведро (1000 л
 * при 1 мБ = 1 л) даёт {@code ρ·V / b} кВт·ч электроэнергии, генератор IE отдаёт {@value #IE_OUTPUT_FE_PER_TICK} FE за
 * тик (конфиг IE {@code machines.dieselGen_output}). Честное значение — около 1,4 млн тиков на ведро, но IE сливает
 * {@code 10·1000/burnTime} мБ раз в 10 тиков целым числом: при burnTime больше {@value #IE_MAX_BURN_TICKS} слив
 * становится нулём и генератор не работает. Поэтому время горения ограничено этим пределом.
 */
public final class ImmersiveFuels implements DataProvider {
    /** Выход дизель-генератора IE по умолчанию, FE/тик ({@code IEServerConfig.MACHINES.dieselGen_output}). */
    public static final int IE_OUTPUT_FE_PER_TICK = 4096;
    /** Предел burnTime: слив IE {@code 10·BUCKET_VOLUME/burnTime} не должен округлиться к нулю. */
    public static final int IE_MAX_BURN_TICKS = 10_000;
    /** Плотность дизельного топлива, кг/л (EN 590: 0,820–0,845). */
    static final double DIESEL_KG_PER_L = 0.84;
    /** Курс FE по умолчанию (00 §0.9, {@code energy.j_per_fe}); рецепт генерируется раз, конфиг сервера в нём не учесть. */
    static final double DEFAULT_J_PER_FE = 2.5;

    private final PackOutput output;

    public ImmersiveFuels(PackOutput output) {
        this.output = output;
    }

    /** Тиков работы генератора IE на ведре нашего дизеля, без предела IE. */
    public static double honestBurnTicks(double jPerFe, int outputFePerTick) {
        double kwh = 1000 * DIESEL_KG_PER_L / DieselEngine.B_MIN_KG_PER_KWH;
        return kwh * 3.6e6 / jPerFe / outputFePerTick;
    }

    /** burnTime рецепта: честное значение, ограниченное пределом IE. */
    public static int burnTime(double jPerFe, int outputFePerTick) {
        return (int) Math.max(1, Math.min(IE_MAX_BURN_TICKS, Math.round(honestBurnTicks(jPerFe, outputFePerTick))));
    }

    static JsonObject dieselRecipe() {
        JsonObject cond = new JsonObject();
        cond.addProperty("type", "neoforge:mod_loaded");
        cond.addProperty("modid", "immersiveengineering");
        JsonArray conds = new JsonArray();
        conds.add(cond);
        JsonObject r = new JsonObject();
        r.add("neoforge:conditions", conds);
        r.addProperty("type", "immersiveengineering:generator_fuel");
        r.addProperty("burnTime", burnTime(DEFAULT_J_PER_FE, IE_OUTPUT_FE_PER_TICK));
        r.addProperty("fluidTag", "c:diesel");
        return r;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, dieselRecipe(), output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(PromptCritical.MOD_ID).resolve("recipe/compat/immersiveengineering/generator_fuel/diesel_fuel.json"));
    }

    @Override
    public String getName() {
        return "Recipes: Immersive Engineering fuels";
    }
}
