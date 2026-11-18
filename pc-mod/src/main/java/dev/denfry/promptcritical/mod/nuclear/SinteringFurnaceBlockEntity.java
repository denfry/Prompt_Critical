package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.enrichment.Criticality;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.fuel.PelletProcess;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Проходная печь спекания таблеток в водороде с бесцентровым шлифованием на выходе ({@link PelletProcess}). Сырые
 * таблетки — от пресса, который стоит вплотную; водород — трубой (1 мБ = 1 нм³). Годные таблетки после шлифования
 * укладываются на поддоны — по поддону на твэл выбранной конструкции; шлам шлифования и брак (плотность или O/U вне
 * допуска) — мешками по 1 кг U обратно в пресс. U-235 в печи не больше безопасной массы.
 */
public final class SinteringFurnaceBlockEntity extends FuelShopBlockEntity {
    public static final int MB = 8_000;
    /** Загрузка печи (лодочки в зоне), кг U [BALANCE]. */
    public static final double LOAD_KG = 40;

    private final FeedTank hydrogen = new FeedTank(PcFluids.HYDROGEN.source.get(), MB);
    private double tK = 1973.15, hours = 4;
    // сырые таблетки в печи и годные на поддон: кг U, партия
    private double greenKg, greenDensity = 0.52, x, x234, pelletKg, sludgeKg, sludgeX, sludgeX234;
    @Nullable
    private FuelDesign design;
    @Nullable
    private PelletProcess.Sintered last;
    private boolean reject, starved, limited;
    private int trays;

    public SinteringFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(NuclearBlockEntities.SINTERING_FURNACE.get(), pos, state, 2);
    }

    /** Печь по любой клетке конструкции. */
    @Nullable
    public static SinteringFurnaceBlockEntity at(Level level, BlockPos cell) {
        BlockPos c = BoilerGeometry.controllerOf(level, cell);
        return c != null && level.getBlockEntity(c) instanceof SinteringFurnaceBlockEntity f ? f : null;
    }

    public double tK() {
        return tK;
    }

    public void setTK(double k) {
        tK = Math.max(1573.15, Math.min(2073.15, k));
    }

    public double hours() {
        return hours;
    }

    public void setHours(double h) {
        hours = Math.max(1, Math.min(10, h));
    }

    public FeedTank hydrogen() {
        return hydrogen;
    }

    public int trays() {
        return trays;
    }

    @Nullable
    public PelletProcess.Sintered last() {
        return last;
    }

    /**
     * Сырые таблетки от пресса: уран {@code kgU} партии {@code u235}/{@code u234}, сырая плотность и конструкция.
     * Возвращает принятое, кг U: другую партию или конструкцию печь не смешивает, U-235 держит ниже безопасной массы.
     */
    double acceptGreen(double kgU, double u235, double u234, double green, FuelDesign d) {
        boolean empty = greenKg <= 1e-9 && pelletKg <= 1e-9;
        if (!empty && (design != d || Math.abs(u235 - x) > 1e-9)) return 0;
        double room = Math.min(LOAD_KG - greenKg, (Criticality.safeU235Kg(u235) - (greenKg + pelletKg) * u235) / Math.max(u235, 1e-12));
        limited = kgU > room;
        double a = Math.max(0, Math.min(kgU, room));
        if (a <= 0) return 0;
        greenDensity = greenKg > 0 ? (greenDensity * greenKg + green * a) / (greenKg + a) : green;
        greenKg += a;
        x = u235;
        x234 = u234;
        design = d;
        balance.in(a, u235);
        setChanged();
        return a;
    }

    @Override
    protected boolean valid(int slot, ItemStack stack) {
        return false;
    }

    @Override
    protected String messageKey() {
        return "sintering";
    }

    @Override
    protected void step(ServerLevel level, double dt) {
        if (design == null || greenKg <= 0) {
            flushSludge();
            if (level.getGameTime() % 10 == 0) setLit(false);
            return;
        }
        double kg = Math.min(greenKg, LOAD_KG * dt / (hours * 3600));
        double uo2 = kg / FuelDesign.Uo2.uraniumShare(x);
        double need = uo2 * PelletProcess.H2_NM3_PER_KG;
        double share = need > 0 ? Math.min(1, hydrogen.amount() / need) : 1;
        starved = share < 1;
        hydrogen.take(need * share);
        PelletProcess.Sintered s = PelletProcess.sinter(greenDensity, tK, hours, share);
        last = s;
        greenKg -= kg;
        reject = !PelletProcess.accepted(design, s);
        if (reject) {
            addSludge(kg);
        } else {
            double ground = kg * PelletProcess.GRINDING_LOSS;
            addSludge(ground);
            pelletKg += kg - ground;
        }
        double perRod = design.uKgPerRod(s.density(), x);
        while (pelletKg >= perRod) {
            ItemStack tray = new FuelLot(perRod, x, x234, s.density(), design.pelletsPerRod(), design.name(), "")
                    .on(new ItemStack(PcItems.FUEL_PELLET_TRAY.get()));
            if (!output(FuelPanels.S_SLOT_TRAYS, tray)) break;
            pelletKg -= perRod;
            balance.out(perRod, x);
            trays++;
        }
        flushSludge();
        if (level.getGameTime() % 10 == 0) setLit(true);
        if (level.getGameTime() % 20 == 0)
            level.sendParticles(ParticleTypes.SMALL_FLAME, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5,
                    1, 0.2, 0.05, 0.2, 0.0);
    }

    private void addSludge(double kg) {
        sludgeX = sludgeKg + kg > 0 ? (sludgeX * sludgeKg + x * kg) / (sludgeKg + kg) : x;
        sludgeX234 = sludgeKg + kg > 0 ? (sludgeX234 * sludgeKg + x234 * kg) / (sludgeKg + kg) : x234;
        sludgeKg += kg;
    }

    /** Шлам и брак — мешками по 1 кг U: они уходят из печи и считаются выходом участка (вернутся через пресс). */
    private void flushSludge() {
        while (sludgeKg >= 1) {
            ItemStack bag = FuelLot.powder(1, sludgeX, sludgeX234).on(new ItemStack(PcItems.UO2_GRINDING_SLUDGE.get()));
            if (!output(FuelPanels.S_SLOT_SLUDGE, bag)) break;
            sludgeKg -= 1;
            balance.out(1, sludgeX);
        }
    }

    // --- руки ---

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return hydrogen;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return FuelPanels.FURNACE;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        ledger(v, FuelPanels.L_IN);
        PelletProcess.Sintered s = last != null ? last : PelletProcess.sinter(greenDensity, tK, hours, 1);
        v[FuelPanels.S_T_C] = (int) Math.round(tK - 273.15);
        v[FuelPanels.S_HOURS] = (int) Math.round(hours * 10);
        v[FuelPanels.S_DENSITY] = (int) Math.round(s.density() * 1000);
        v[FuelPanels.S_GRAIN] = (int) Math.round(s.grainUm() * 10);
        v[FuelPanels.S_OU] = (int) Math.round(s.oxygenToU() * 1000);
        v[FuelPanels.S_H2] = (int) Math.round(hydrogen.fill() * 1000);
        v[FuelPanels.S_TRAYS] = trays;
        v[FuelPanels.S_BUFFER] = (int) Math.round(greenKg * 1000);
        boolean on = running && greenKg > 0;
        v[FuelPanels.FLAGS] = (on ? 1 << FuelPanels.F_RUN : 0) | (on && reject ? 1 << FuelPanels.F_REJECT : 0)
                | (on && starved ? 1 << FuelPanels.F_STARVED : 0) | (limited ? 1 << FuelPanels.F_LIMIT : 0);
    }

    @Override
    protected void button(int id) {
        switch (id) {
            case FuelPanels.SB_T_DOWN -> setTK(tK - 25);
            case FuelPanels.SB_T_UP -> setTK(tK + 25);
            case FuelPanels.SB_H_DOWN -> setHours(hours - 0.5);
            case FuelPanels.SB_H_UP -> setHours(hours + 0.5);
            default -> {
            }
        }
    }

    // --- сохранение ---

    @Override
    protected void saveState(StateWriter out) {
        out.putDouble("t", tK);
        out.putDouble("hours", hours);
        out.putDouble("green", greenKg);
        out.putDouble("green_d", greenDensity);
        out.putDouble("x", x);
        out.putDouble("x234", x234);
        out.putDouble("pellets", pelletKg);
        out.putDouble("sludge", sludgeKg);
        out.putDouble("sludge_x", sludgeX);
        out.putDouble("sludge_x234", sludgeX234);
        out.putString("design", design == null ? "" : design.name());
        out.putInt("trays", trays);
    }

    @Override
    protected void loadState(StateReader in) {
        setTK(in.optDouble("t", 1973.15));
        setHours(in.optDouble("hours", 4));
        greenKg = in.optDouble("green", 0);
        greenDensity = in.optDouble("green_d", 0.52);
        x = in.optDouble("x", 0);
        x234 = in.optDouble("x234", 0);
        pelletKg = in.optDouble("pellets", 0);
        sludgeKg = in.optDouble("sludge", 0);
        sludgeX = in.optDouble("sludge_x", 0);
        sludgeX234 = in.optDouble("sludge_x234", 0);
        String d = in.optString("design", "");
        design = null;
        for (FuelDesign f : FuelDesign.values()) if (f.name().equals(d)) design = f;
        trays = in.optInt("trays", 0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        hydrogen.save(tag, "h2");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hydrogen.load(tag, "h2");
    }
}
