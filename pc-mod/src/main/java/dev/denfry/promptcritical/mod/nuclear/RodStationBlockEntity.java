package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Снаряжение твэлов: поддон таблеток засыпают в оболочечную трубу, ставят пружину и концевую заглушку, вакуумируют,
 * заполняют гелием до давления конструкции (ВВЭР-1000 — 2,0 МПа, PWR — 2,4 МПа) и заваривают; затем — контроль
 * герметичности гелиевым течеискателем (Olander, гл. 1; ТУ ТВЭЛ). Негерметичные твэлы (0,3 %) вскрывают: оболочку —
 * в лом, таблетки возвращаются на поддон. Нет гелия — твэлы заполняют аргоном: они герметичны, но теплопроводность
 * зазора у аргона в 8 раз хуже, и это пометка на твэле. Газа на твэл — около 0,5 л н. у. (свободный объём ~25 см³).
 */
public final class RodStationBlockEntity extends FuelShopBlockEntity {
    public static final int MB = 4_000;
    /** Время на твэл, игровые секунды; доля негерметичных; газ на твэл, нм³. */
    public static final double ROD_SECONDS = 30, LEAK = 0.003, GAS_NM3 = 0.0005;

    private final FeedTank helium = new FeedTank(PcFluids.HELIUM.source.get(), MB);
    private final FeedTank argon = new FeedTank(PcFluids.ARGON.source.get(), MB);
    private final IFluidHandler inlet = new PyrolysisFurnaceBlockEntity.MultiInlet(helium, argon);
    private final Random rng = new Random();
    private double progress;
    private int rods, defects;
    private boolean starved, lastArgon;

    /** Пост сварки заглушек реального масштаба (fuelcycle §4a): треск TIG и голубые искры в окне камеры. */
    @Override
    protected void clientTick() {
        if (!real() || !lit()) return;
        dev.denfry.promptcritical.mod.fx.MachineFx fx = dev.denfry.promptcritical.mod.fx.MachineFx.of(this);
        if (fx == null) return;
        if (fx.chance(6)) {
            net.minecraft.world.phys.Vec3 at = fx.at(4, 15, 8);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
                    (fx.random().nextDouble() - 0.5) * 0.1, 0.05, (fx.random().nextDouble() - 0.5) * 0.1);
        }
        if (fx.chance(1)) fx.sound(fx.at(4, 15, 8), net.minecraft.sounds.SoundEvents.BEE_LOOP, 0.25f, 2.0f);
    }

    public RodStationBlockEntity(BlockPos pos, BlockState state) {
        super(NuclearBlockEntities.ROD_LOADING_STATION.get(), pos, state, 3);
    }

    public int rods() {
        return rods;
    }

    public int defects() {
        return defects;
    }

    @Override
    protected boolean valid(int slot, ItemStack stack) {
        return slot == FuelPanels.R_SLOT_TRAYS && stack.is(PcItems.FUEL_PELLET_TRAY.get())
                || slot == FuelPanels.R_SLOT_TUBES && stack.is(PcItems.CLADDING_TUBE.get());
    }

    @Override
    protected String messageKey() {
        return "rods";
    }

    @Override
    protected void step(ServerLevel level, double dt) {
        ItemStack tray = items.getStackInSlot(FuelPanels.R_SLOT_TRAYS), tube = items.getStackInSlot(FuelPanels.R_SLOT_TUBES);
        boolean gas = helium.amount() >= GAS_NM3 || argon.amount() >= GAS_NM3;
        starved = !gas;
        if (tray.isEmpty() || tube.isEmpty() || !gas) {
            progress = 0;
            if (level.getGameTime() % 10 == 0) setLit(false);
            return;
        }
        progress += dt;
        if (level.getGameTime() % 10 == 0) setLit(true);
        if (progress < ROD_SECONDS) return;
        progress = 0;
        FuelLot l = FuelLot.of(tray);
        boolean he = helium.amount() >= GAS_NM3;
        ItemStack rod = MoxShare.of(l).apply(new FuelLot(l.uKg(), l.u235(), l.u234(), l.density(), l.count(), l.design(), he ? "he" : "ar"))
                .on(new ItemStack(PcItems.FUEL_ROD.get()));
        if (!room(FuelPanels.R_SLOT_RODS, rod)) return;
        tube.shrink(1);
        (he ? helium : argon).take(GAS_NM3);
        if (rng.nextDouble() < LEAK) {
            // течь сварного шва: оболочку в лом, таблетки — обратно на поддон
            defects++;
            level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3f, 1.6f);
            return;
        }
        balance.in(l.uKg(), l.u235());
        tray.shrink(1);
        output(FuelPanels.R_SLOT_RODS, rod);
        balance.out(l.uKg(), l.u235());
        lastArgon = !he;
        rods++;
        level.playSound(null, worldPosition, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.15f, 1.8f);
    }

    // --- руки ---

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return inlet;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return FuelPanels.ROD_STATION;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        ledger(v, FuelPanels.L_IN);
        v[FuelPanels.R_RODS] = rods;
        v[FuelPanels.R_DEFECTS] = defects;
        v[FuelPanels.R_HE] = (int) Math.round(helium.fill() * 1000);
        v[FuelPanels.R_AR] = (int) Math.round(argon.fill() * 1000);
        v[FuelPanels.FLAGS] = (running && progress > 0 ? 1 << FuelPanels.F_RUN : 0) | (defects > 0 ? 1 << FuelPanels.F_REJECT : 0)
                | (running && starved ? 1 << FuelPanels.F_STARVED : 0) | (lastArgon ? 1 << FuelPanels.F_ARGON : 0);
    }

    // --- сохранение ---

    @Override
    protected void saveState(StateWriter out) {
        out.putDouble("progress", progress);
        out.putInt("rods", rods);
        out.putInt("defects", defects);
        out.putBoolean("argon", lastArgon);
    }

    @Override
    protected void loadState(StateReader in) {
        progress = in.optDouble("progress", 0);
        rods = in.optInt("rods", 0);
        defects = in.optInt("defects", 0);
        lastArgon = in.has("argon") && in.getBoolean("argon");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        helium.save(tag, "he");
        argon.save(tag, "ar");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        helium.load(tag, "he");
        argon.load(tag, "ar");
    }
}
