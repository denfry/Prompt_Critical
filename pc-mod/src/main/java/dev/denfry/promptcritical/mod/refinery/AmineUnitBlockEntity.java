package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.mod.gas.GasPlantBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.gasprocessing.AmineTreating;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Аминовая очистка попутного газа ({@link AmineTreating}, 09 §9.10.3): абсорбер и десорбер с ребойлером. Установка
 * стоит в {@value #PLANT_RADIUS} блоках от установки подготовки газа ({@link GasPlantBlockEntity}) и очищает весь
 * газ, который та принимает, — по ГПЗ 09 §9.10.1 аминовый блок стоит перед осушкой и НТС. Попутный газ несёт
 * {@value #Y_H2S} мольн. H₂S и {@value #Y_CO2} CO₂ [допущение: состав примера §9.10.3; свой компонент
 * {@code gas_composition} у жидкости попутного газа пока не задан]. Сероводород десорбера копится в аппарате и уходит в
 * касающуюся установку Клауса; без неё аппарат переполняется и H₂S проскакивает в товарный газ.
 *
 * <p>Ребойлер — огневой, на товарном газе (трубой к любой грани), тепло — как у пара 110–140 кг/м³ раствора
 * [допущение: пара 07 у установки нет]. Раствор МДЭА заправлен в установку при постройке, как ТЭГ в регенератор
 * гликоля [допущение: потери амина не учитываются]. Циркуляцию и пар на ребойлер задают кнопками щита.
 */
public final class AmineUnitBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 4_000, PLANT_RADIUS = 6;
    public static final double Y_H2S = 0.02, Y_CO2 = 0.01, H2S_HOLDUP_KG = 50, FUEL_J_PER_NM3 = 36e6, EFFICIENCY = 0.85;
    public static final double Q_MIN = 0.25, Q_MAX = 6, Q_STEP = 0.25, STEAM_MIN = 60, STEAM_MAX = 160, STEAM_STEP = 10;

    private final FeedTank fuel = new FeedTank(PcFluids.SALES_GAS.source.get(), MB);
    private double amineM3h = 2, steamKgM3 = 120, h2sKg, gasNm3h, totalH2sKg;
    private boolean plant, fired;
    private AmineTreating.Result last = AmineTreating.treat(0, Y_H2S, Y_CO2, 2, 120);

    public AmineUnitBlockEntity(BlockPos pos, BlockState state) {
        super(RefineryBlockEntities.AMINE_UNIT.get(), pos, state, 0, 0, s -> false);
    }

    public FeedTank fuel() {
        return fuel;
    }

    public AmineTreating.Result last() {
        return last;
    }

    public double h2sKg() {
        return h2sKg;
    }

    public double totalH2sKg() {
        return totalH2sKg;
    }

    public double gasNm3h() {
        return gasNm3h;
    }

    public void setCirculation(double m3h) {
        amineM3h = Math.clamp(m3h, Q_MIN, Q_MAX);
        setChanged();
    }

    public void setSteam(double kgM3) {
        steamKgM3 = Math.clamp(kgM3, STEAM_MIN, STEAM_MAX);
        setChanged();
    }

    /** Отдать сероводород установке Клауса, кг. */
    public double drainH2s(double maxKg) {
        double kg = Math.min(maxKg, h2sKg);
        h2sKg -= kg;
        return kg;
    }

    @Nullable
    private GasPlantBlockEntity plant(ServerLevel level) {
        GasPlantBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (GasPlantBlockEntity g : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(PLANT_RADIUS), GasPlantBlockEntity.class)) {
            double d = g.getBlockPos().distSqr(worldPosition);
            if (d < bestD) {
                bestD = d;
                best = g;
            }
        }
        return best;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        GasPlantBlockEntity g = plant(level);
        plant = g != null;
        gasNm3h = g == null ? 0 : g.flowSm3s() * 3600;
        double q = on ? amineM3h : 0;
        // ребойлер: тепло пара на м³ раствора — из товарного газа
        double heatW = q * steamKgM3 * AmineTreating.STEAM_LATENT_J_KG / 3600;
        double nm3 = heatW * dt / (FUEL_J_PER_NM3 * EFFICIENCY);
        fired = q > 0 && fuel.amount() >= nm3;
        if (fired) fuel.take(nm3);
        boolean room = h2sKg < H2S_HOLDUP_KG;
        last = AmineTreating.treat(gasNm3h, Y_H2S, Y_CO2, room ? q : 0, fired ? steamKgM3 : 1);
        double kg = last.h2sKmolH() * AmineTreating.M_H2S / 3600 * dt;
        h2sKg += kg;
        totalH2sKg += kg;
    }

    @Override
    protected boolean running() {
        return last.h2sKmolH() > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return fuel;
    }

    @Override
    public PanelLayout panelLayout() {
        return ConversionPanels.AMINE;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[ConversionPanels.AM_SWEET_X10] = (int) Math.round(Math.min(99999, last.sweetH2sMgNm3() * 10));
        v[ConversionPanels.AM_RICH_PERMIL] = (int) Math.round(last.richLoading() * 1000);
        v[ConversionPanels.AM_Q_X100] = (int) Math.round(amineM3h * 100);
        v[ConversionPanels.AM_STEAM] = (int) Math.round(steamKgM3);
        v[ConversionPanels.AM_GAS_NM3H] = (int) Math.round(gasNm3h);
        v[ConversionPanels.AM_H2S_KGH10] = (int) Math.round(last.h2sKmolH() * AmineTreating.M_H2S * 10);
        v[ConversionPanels.AM_PURITY_PERMIL] = (int) Math.round(last.acidPurity() * 1000);
        v[ConversionPanels.AM_LEAN_X1000] = (int) Math.round(last.leanLoading() * 1e4);
        v[ConversionPanels.AM_HOLDUP] = (int) Math.round(Math.min(1, h2sKg / H2S_HOLDUP_KG) * 1000);
        boolean offSpec = gasNm3h > 0 && last.sweetH2sPpm() > AmineTreating.SPEC_PPM_GOST;
        v[ConversionPanels.AM_FLAGS] = (on ? 1 << ConversionPanels.F_ON : 0) | (running() ? 1 << ConversionPanels.F_RUN : 0)
                | (offSpec ? 1 << ConversionPanels.F_ALARM : 0) | (last.saturated() ? 1 << ConversionPanels.F_WARN : 0)
                | (!plant ? 1 << ConversionPanels.F_AUX : 0) | (h2sKg >= H2S_HOLDUP_KG ? 1 << ConversionPanels.F_LOW : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ConversionPanels.B_RUN -> use(player);
            case ConversionPanels.B_DOWN -> setCirculation(amineM3h - Q_STEP);
            case ConversionPanels.B_UP -> setCirculation(amineM3h + Q_STEP);
            case ConversionPanels.B_DOWN2 -> setSteam(steamKgM3 - STEAM_STEP);
            case ConversionPanels.B_UP2 -> setSteam(steamKgM3 + STEAM_STEP);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        fuel.save(tag, "fuel");
        tag.putDouble("q", amineM3h);
        tag.putDouble("steam", steamKgM3);
        tag.putDouble("h2s", h2sKg);
        tag.putDouble("total_h2s", totalH2sKg);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        fuel.load(tag, "fuel");
        if (tag.contains("q")) amineM3h = tag.getDouble("q");
        if (tag.contains("steam")) steamKgM3 = tag.getDouble("steam");
        h2sKg = tag.getDouble("h2s");
        totalH2sKg = tag.getDouble("total_h2s");
    }
}
