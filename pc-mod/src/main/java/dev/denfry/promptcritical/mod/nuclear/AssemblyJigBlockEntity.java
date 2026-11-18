package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Стапель сборки ТВС: каркас из направляющих каналов (оболочечные трубы) и дистанционирующих решёток, в него
 * протягивают твэлы — ВВЭР в шестигранную решётку, PWR в квадратную 17×17 ({@link FuelDesign}). Твэлы берутся только
 * своей конструкции; разное обогащение допустимо (профилирование ТВС), обогащение ТВС — среднее по урану. Готовая ТВС
 * несёт партию: уран, обогащение, конструкцию, число твэлов и газ (аргон, если хоть один твэл с аргоном).
 */
public final class AssemblyJigBlockEntity extends FuelShopBlockEntity {
    private FuelDesign design = FuelDesign.VVER_1000;
    private int loaded, assemblies;
    private double uKg, u235Kg, u234Kg, densityKg;
    /** Плутоний МОКС в собранных твэлах, на кг U. */
    private MoxShare share = MoxShare.NONE;
    private boolean argon;

    public AssemblyJigBlockEntity(BlockPos pos, BlockState state) {
        super(NuclearBlockEntities.ASSEMBLY_JIG.get(), pos, state, 4);
    }

    public FuelDesign design() {
        return design;
    }

    public void setDesign(FuelDesign d) {
        if (loaded == 0) design = d;
    }

    public int loaded() {
        return loaded;
    }

    public int assemblies() {
        return assemblies;
    }

    /** Направляющих труб на каркас: у ВВЭР-440 каналов нет, нужна хотя бы центральная труба. */
    public static int tubesFor(FuelDesign d) {
        return Math.max(1, d.guideTubes);
    }

    @Override
    protected boolean valid(int slot, ItemStack stack) {
        return slot == FuelPanels.J_SLOT_RODS && stack.is(PcItems.FUEL_ROD.get())
                || slot == FuelPanels.J_SLOT_GRIDS && stack.is(PcItems.SPACER_GRID.get())
                || slot == FuelPanels.J_SLOT_TUBES && stack.is(PcItems.CLADDING_TUBE.get());
    }

    @Override
    protected String messageKey() {
        return "jig";
    }

    @Override
    protected void step(ServerLevel level, double dt) {
        // твэлы своей конструкции — в каркас по одному за тик
        ItemStack rods = items.getStackInSlot(FuelPanels.J_SLOT_RODS);
        if (!rods.isEmpty() && loaded < design.rods) {
            FuelLot l = FuelLot.of(rods);
            if (l.designOrNull() == design) {
                loaded++;
                share = MoxShare.mix(share, uKg, MoxShare.of(l), l.uKg());
                uKg += l.uKg();
                u235Kg += l.u235Kg();
                u234Kg += l.uKg() * l.u234();
                densityKg += l.uKg() * l.density();
                argon |= "ar".equals(l.gas());
                balance.in(l.uKg(), l.u235());
                rods.shrink(1);
                setChanged();
            }
        }
        ItemStack grids = items.getStackInSlot(FuelPanels.J_SLOT_GRIDS), tubes = items.getStackInSlot(FuelPanels.J_SLOT_TUBES);
        boolean ready = loaded >= design.rods && grids.getCount() >= design.grids && tubes.getCount() >= tubesFor(design)
                && items.getStackInSlot(FuelPanels.J_SLOT_OUT).isEmpty();
        if (level.getGameTime() % 10 == 0) setLit(loaded > 0 && loaded < design.rods);
        if (!ready) return;
        double x = u235Kg / uKg;
        ItemStack fa = share.apply(new FuelLot(uKg, x, u234Kg / uKg, densityKg / uKg, loaded, design.name(), argon ? "ar" : "he"))
                .on(new ItemStack(PcItems.FUEL_ASSEMBLY.get()));
        grids.shrink(design.grids);
        tubes.shrink(tubesFor(design));
        output(FuelPanels.J_SLOT_OUT, fa);
        balance.out(uKg, x);
        assemblies++;
        loaded = 0;
        uKg = u235Kg = u234Kg = densityKg = 0;
        share = MoxShare.NONE;
        argon = false;
        level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4f, 1.4f);
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return FuelPanels.JIG;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        ledger(v, FuelPanels.L_IN);
        v[FuelPanels.J_LOADED] = loaded;
        v[FuelPanels.J_NEEDED] = design.rods;
        v[FuelPanels.J_GRIDS] = items.getStackInSlot(FuelPanels.J_SLOT_GRIDS).getCount();
        v[FuelPanels.J_TUBES] = items.getStackInSlot(FuelPanels.J_SLOT_TUBES).getCount();
        v[FuelPanels.J_U_KG] = (int) Math.round(uKg * 10);
        v[FuelPanels.J_X] = uKg > 0 ? (int) Math.round(u235Kg / uKg * 1e5) : 0;
        v[FuelPanels.J_DESIGN] = design.ordinal();
        v[FuelPanels.FLAGS] = (running && loaded > 0 ? 1 << FuelPanels.F_RUN : 0) | (argon ? 1 << FuelPanels.F_ARGON : 0);
    }

    @Override
    protected void button(int id) {
        if (id == FuelPanels.JB_DESIGN) setDesign(FuelDesign.values()[(design.ordinal() + 1) % FuelDesign.values().length]);
    }

    // --- сохранение ---

    @Override
    protected void saveState(StateWriter out) {
        out.putString("design", design.name());
        out.putInt("loaded", loaded);
        out.putInt("assemblies", assemblies);
        out.putDouble("jig_u", uKg); // ключи u, u235… заняты объектами MaterialBalance
        out.putDouble("jig_u235", u235Kg);
        out.putDouble("jig_u234", u234Kg);
        out.putDouble("jig_density", densityKg);
        share.save(out, "mox_");
        out.putBoolean("argon", argon);
    }

    @Override
    protected void loadState(StateReader in) {
        String d = in.optString("design", FuelDesign.VVER_1000.name());
        for (FuelDesign f : FuelDesign.values()) if (f.name().equals(d)) design = f;
        loaded = in.optInt("loaded", 0);
        assemblies = in.optInt("assemblies", 0);
        uKg = in.optDouble("jig_u", 0);
        u235Kg = in.optDouble("jig_u235", 0);
        u234Kg = in.optDouble("jig_u234", 0);
        densityKg = in.optDouble("jig_density", 0);
        share = MoxShare.load(in, "mox_");
        argon = in.has("argon") && in.getBoolean("argon");
    }
}
