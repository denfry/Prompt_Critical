package dev.denfry.promptcritical.mod.isotopes;

import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.isotopes.TargetMaterial;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Изотопные операции на верстаке (12 §12.1.2, §12.3.1, §12.5.2), все — по компонентам предметов:
 *
 * <ul>
 *   <li>пустая капсула + кобальт / иридий / NpO₂ → снаряжённая капсула: {@value #COBALT_KG} кг таблеток кобальта,
 *   {@value #IRIDIUM_KG} кг дисков иридия, {@value #NPO2_KG} кг таблеток NpO₂ — так, чтобы мишень не съедала запас
 *   реактивности АМ-1;</li>
 *   <li>облучённая герметичная капсула Co или Ir + железный слиток (вторая стальная оболочка особой формы) →
 *   закрытый источник с активностью капсулы;</li>
 *   <li>облучённая капсула NpO₂ + слиток иридия (оболочка, как у GPHS) → источник тепла Pu-238: весь Np-238 к
 *   выдержке распадается в Pu-238, недооблучённый Np-237 уходит на повторное облучение (здесь не возвращается);</li>
 *   <li>стеклоблок ВАО + железный слиток → источник тепла Sr-90 (SrTiO₃ в стальной капсуле): стронциевая фракция
 *   отбирается из блока, блок с остальной активностью возвращается.</li>
 * </ul>
 */
public final class IsotopeCraftRecipe extends CustomRecipe {
    public static final double COBALT_KG = 0.1, IRIDIUM_KG = 0.01, NPO2_KG = 0.05;
    /** PuO₂: 88,2 % Pu по массе, изотопная чистота по Pu-238 после облучения — 85 % (12 §12.2.4). */
    public static final double PU_IN_OXIDE = 0.882, PU238_PURITY = 0.85, PUO2_DENSITY = 11.5;
    /** SrTiO₃: 47,7 % Sr по массе, Sr-90 — половина выделенного стронция; плотность 5,1 г/см³ (12 §12.5.3). */
    public static final double SR_IN_TITANATE = 0.477, SR90_IN_SR = 0.5, SRTIO3_DENSITY = 5.1;
    /** AmO₂: 88,3 % Am по массе, плотность 11,7 г/см³. */
    public static final double AM_IN_OXIDE = 0.883, AMO2_DENSITY = 11.7;

    public IsotopeCraftRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** Ровно два непустых предмета. */
    @Nullable
    private static List<ItemStack> two(CraftingInput input) {
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (!s.isEmpty()) l.add(s);
        }
        return l.size() == 2 ? l : null;
    }

    @Nullable
    private static ItemStack pick(List<ItemStack> l, java.util.function.Predicate<ItemStack> p) {
        for (ItemStack s : l) if (p.test(s)) return s;
        return null;
    }

    private static boolean emptyCapsule(ItemStack s) {
        return s.is(PcItems.IRRADIATION_CAPSULE.get()) && !s.has(IsotopeComponents.IRRADIATION_TARGET.get());
    }

    @Nullable
    private static IsotopeComponents.IrradiationTarget target(ItemStack s, TargetMaterial m) {
        if (!s.is(PcItems.IRRADIATION_CAPSULE.get())) return null;
        IsotopeComponents.IrradiationTarget t = s.get(IsotopeComponents.IRRADIATION_TARGET.get());
        return t != null && t.sealed() && t.irradiated() && m.name().equals(t.material()) && t.atoms().size() == m.chain.size() ? t : null;
    }

    /** Результат или пусто. */
    static ItemStack result(CraftingInput input) {
        List<ItemStack> l = two(input);
        if (l == null) return ItemStack.EMPTY;
        // снаряжение капсулы
        ItemStack cap = pick(l, IsotopeCraftRecipe::emptyCapsule);
        if (cap != null) {
            ItemStack other = l.get(0) == cap ? l.get(1) : l.get(0);
            TargetMaterial m = other.is(PcItems.COBALT_INGOT.get()) ? TargetMaterial.COBALT_METAL
                    : other.is(PcItems.IRIDIUM_INGOT.get()) ? TargetMaterial.IRIDIUM_METAL
                    : other.is(PcItems.NPO2_POWDER.get()) ? TargetMaterial.NEPTUNIUM_OXIDE : null;
            if (m == null) return ItemStack.EMPTY;
            double kg = m == TargetMaterial.COBALT_METAL ? COBALT_KG : m == TargetMaterial.IRIDIUM_METAL ? IRIDIUM_KG : NPO2_KG;
            ItemStack out = new ItemStack(PcItems.IRRADIATION_CAPSULE.get());
            out.set(IsotopeComponents.IRRADIATION_TARGET.get(), IsotopeComponents.IrradiationTarget.fresh(m, kg, 0));
            return out;
        }
        ItemStack iron = pick(l, s -> s.is(Items.IRON_INGOT));
        ItemStack iridium = pick(l, s -> s.is(PcItems.IRIDIUM_INGOT.get()));
        // закрытый источник Co-60 / Ir-192
        if (iron != null) {
            ItemStack other = l.get(0) == iron ? l.get(1) : l.get(0);
            for (TargetMaterial m : new TargetMaterial[] {TargetMaterial.COBALT_METAL, TargetMaterial.IRIDIUM_METAL}) {
                IsotopeComponents.IrradiationTarget t = target(other, m);
                ActivityData a = other.get(ActivityData.ACTIVITY.get());
                if (t == null || a == null) continue;
                ItemStack out = new ItemStack(PcItems.SEALED_SOURCE.get());
                out.set(ActivityData.ACTIVITY.get(), a);
                return out;
            }
            ActivityData sr = strontium(other);
            if (sr != null) {
                ItemStack out = new ItemStack(PcItems.SR90_HEAT_SOURCE.get());
                out.set(ActivityData.ACTIVITY.get(), sr);
                return out;
            }
        }
        // выделение Am-241 из выдержанного PuO₂ ионным обменом (12 §12.2.5)
        ItemStack resin = pick(l, s -> s.is(PcItems.ION_EXCHANGE_RESIN_SBA.get()));
        if (resin != null) {
            ItemStack other = l.get(0) == resin ? l.get(1) : l.get(0);
            ActivityData am = americium(other);
            if (am != null) {
                ItemStack out = new ItemStack(PcItems.AM241_OXIDE.get());
                out.set(ActivityData.ACTIVITY.get(), am);
                return out;
            }
        }
        // закрытый источник Am-241 (γ 59,5 кэВ): оксид в двойной стальной капсуле
        if (iron != null) {
            ItemStack other = l.get(0) == iron ? l.get(1) : l.get(0);
            if (other.is(PcItems.AM241_OXIDE.get()) && other.has(ActivityData.ACTIVITY.get())) {
                ItemStack out = new ItemStack(PcItems.SEALED_SOURCE.get());
                out.set(ActivityData.ACTIVITY.get(), other.get(ActivityData.ACTIVITY.get()));
                return out;
            }
        }
        // источник тепла Pu-238
        if (iridium != null) {
            ItemStack other = l.get(0) == iridium ? l.get(1) : l.get(0);
            IsotopeComponents.IrradiationTarget t = target(other, TargetMaterial.NEPTUNIUM_OXIDE);
            if (t == null) return ItemStack.EMPTY;
            double[] atoms = t.atomsArray();
            double pu = atoms[2] + atoms[1];
            if (pu <= 0) return ItemStack.EMPTY;
            double bq = pu * TargetMaterial.NEPTUNIUM_OXIDE.chain.get(2).lambda();
            double oxideKg = pu / TargetMaterial.AVOGADRO * 238.05 / 1000 / PU_IN_OXIDE / PU238_PURITY;
            ItemStack out = new ItemStack(PcItems.PU238_HEAT_SOURCE.get());
            out.set(ActivityData.ACTIVITY.get(), new ActivityData(t.refTick(), oxideKg, PUO2_DENSITY, Map.of(Emitter.PU238.name(), bq)));
            return out;
        }
        return ItemStack.EMPTY;
    }

    /** Игровое время сервера для распада в рецепте (верстак своего времени не знает). */
    static long now() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? 0 : server.overworld().getGameTime();
    }

    /** Am-241, вросший в партию PuO₂ к нынешнему игровому времени, или null. */
    @Nullable
    static ActivityData americium(ItemStack s) {
        if (!s.is(PcItems.PUO2_POWDER.get())) return null;
        ActivityData a = s.get(ActivityData.ACTIVITY.get());
        if (a == null) return null;
        long t = now();
        double am = a.inventory(t).bq(Emitter.AM241);
        if (am <= 0) return null;
        double kg = am / Emitter.AM241.lambda() / TargetMaterial.AVOGADRO * 241.06 / 1000;
        return new ActivityData(t, kg / AM_IN_OXIDE, AMO2_DENSITY, Map.of(Emitter.AM241.name(), am));
    }

    /** Стронциевая фракция стеклоблока ВАО или null. */
    @Nullable
    static ActivityData strontium(ItemStack s) {
        if (!s.is(PcItems.HLW_GLASS_CANISTER.get())) return null;
        ActivityData a = s.get(ActivityData.ACTIVITY.get());
        if (a == null) return null;
        double sr = a.bq().getOrDefault(Emitter.SR90.name(), 0.0);
        if (sr <= 0) return null;
        double y = a.bq().getOrDefault(Emitter.Y90.name(), 0.0);
        double sr90Kg = sr / Emitter.SR90.lambda() / TargetMaterial.AVOGADRO * 89.908 / 1000;
        return new ActivityData(a.refTick(), sr90Kg / (SR_IN_TITANATE * SR90_IN_SR), SRTIO3_DENSITY,
                Map.of(Emitter.SR90.name(), sr, Emitter.Y90.name(), y));
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !result(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return result(input);
    }

    /** Стеклоблок возвращается без стронция. */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> out = super.getRemainingItems(input);
        if (result(input).is(PcItems.SR90_HEAT_SOURCE.get())) {
            for (int i = 0; i < input.size(); i++) {
                ItemStack s = input.getItem(i);
                if (!s.is(PcItems.HLW_GLASS_CANISTER.get())) continue;
                ActivityData a = s.get(ActivityData.ACTIVITY.get());
                ItemStack back = s.copyWithCount(1);
                if (a != null) {
                    Map<String, Double> rest = new HashMap<>(a.bq());
                    rest.remove(Emitter.SR90.name());
                    rest.remove(Emitter.Y90.name());
                    back.set(ActivityData.ACTIVITY.get(), new ActivityData(a.refTick(), a.massKg(), a.densityGcc(), rest));
                }
                out.set(i, back);
            }
        }
        // PuO₂ возвращается без америция (распад досчитан до сейчас), ионит — отработанным
        if (result(input).is(PcItems.AM241_OXIDE.get())) {
            long t = now();
            for (int i = 0; i < input.size(); i++) {
                ItemStack s = input.getItem(i);
                if (s.is(PcItems.ION_EXCHANGE_RESIN_SBA.get())) {
                    out.set(i, new ItemStack(PcItems.SPENT_ION_EXCHANGE_RESIN.get()));
                } else if (s.is(PcItems.PUO2_POWDER.get())) {
                    ActivityData a = s.get(ActivityData.ACTIVITY.get());
                    ItemStack back = s.copyWithCount(1);
                    if (a != null) {
                        ActivityData now = ActivityData.of(a.inventory(t), t);
                        Map<String, Double> rest = new HashMap<>(now.bq());
                        rest.remove(Emitter.AM241.name());
                        back.set(ActivityData.ACTIVITY.get(), new ActivityData(t, a.massKg(), a.densityGcc(), rest));
                    }
                    out.set(i, back);
                }
            }
        }
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return IsotopeBlockEntities.CRAFT.get();
    }
}
