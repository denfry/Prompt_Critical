package dev.denfry.promptcritical.mod.nonferrous;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.grid.DcLoad;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.nonferrous.Electrorefining;
import dev.denfry.promptcritical.sim.nonferrous.Electrorefining.Mode;
import dev.denfry.promptcritical.sim.nonferrous.Flotation;
import dev.denfry.promptcritical.sim.nonferrous.PeirceSmithConverter;
import dev.denfry.promptcritical.sim.nonferrous.PgmRefinery;
import dev.denfry.promptcritical.sim.nonferrous.SulfideSmelting.Metal;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Секция ванн электрорафинирования ({@link Electrorefining}, 08 §8.8.3–8.8.4) 2×1×1 на постоянном токе от
 * выпрямительного агрегата. Режим Cu: анодная медь → катоды 99,99 %; режим Ni: штейновые аноды медно-никелевой линии →
 * никель, кобальт из раствора и шлам. Аноды — воронкой или рукой; катоды, кобальт и анодный шлам (порция платиноидов
 * {@value PgmRefinery#PGM_PER_LOT_KG} кг) — в склад (воронкой снизу). Режим меняют на пустой секции кнопкой щита.
 * Электролиз — реальное время (08 §8.0.3).
 */
public final class ElectrorefiningCellBlockEntity extends UraniumProcessBlockEntity implements DcLoad, dev.denfry.promptcritical.mod.client.whole.WholeModelHost {
    private static final double TICK_S = 0.05;

    private final Electrorefining cells = new Electrorefining();
    /** Металл в анодах, катодный металл, платиноиды и кобальт, ещё не выданные, кг. */
    private double anodeMetalKg, cathodeKg, pgmKg, cobaltKg;
    private double suppliedAmps;
    private long suppliedTick = -10;

    public ElectrorefiningCellBlockEntity(BlockPos pos, BlockState state) {
        super(NonferrousBlockEntities.ELECTROREFINING_CELL.get(), pos, state, 4, 6, ElectrorefiningCellBlockEntity::anode);
    }

    static boolean anode(ItemStack s) {
        return s.is(PcItems.COPPER_ANODE.get()) || (s.is(PcItems.SULFIDE_MATTE.get()) && NonferrousComponents.nickel(s));
    }

    private boolean modeAnode(ItemStack s) {
        return cells.mode() == Mode.COPPER ? s.is(PcItems.COPPER_ANODE.get())
                : s.is(PcItems.SULFIDE_MATTE.get()) && NonferrousComponents.nickel(s);
    }

    public Electrorefining cells() {
        return cells;
    }

    public double anodeMetalKg() {
        return anodeMetalKg;
    }

    /** Платиноиды и кобальт, ещё не выданные шламом и слитками, кг. */
    public double pgmKg() {
        return pgmKg;
    }

    public double cobaltKg() {
        return cobaltKg;
    }

    /** Сменить режим: только на пустой секции. */
    public boolean setMode(Mode m) {
        if (m == cells.mode()) return true;
        if (anodeMetalKg > 1e-6) return false;
        cells.setMode(m);
        cathodeKg = 0;
        BlockState st = getBlockState();
        if (level != null && st.hasProperty(PcProperties.NICKEL))
            level.setBlock(worldPosition, st.setValue(PcProperties.NICKEL, m == Mode.NICKEL), Block.UPDATE_ALL);
        setChanged();
        return true;
    }

    @Override
    public double dcSetpointA() {
        return cells.currentSetA();
    }

    @Override
    public double dcDemandW() {
        return on ? cells.dcDemandW(cells.currentSetA()) : 0;
    }

    @Override
    public void supply(double amps) {
        suppliedAmps = Math.max(0, amps);
        suppliedTick = level.getGameTime();
    }

    @Override
    protected void process(ServerLevel level, double dtPhys) {
        double ingot = NonferrousUnits.ingotKg();
        double perAnode = cells.mode() == Mode.COPPER ? ingot * PeirceSmithConverter.ANODE_CU : ingot * Metal.NICKEL.matteGrade;
        if (anodeMetalKg < perAnode / 4 && !takeOne(this::modeAnode).isEmpty()) anodeMetalKg += perAnode;
        double amps = on && level.getGameTime() - suppliedTick <= 2 ? suppliedAmps : 0;
        double kg = cells.step(TICK_S, amps, anodeMetalKg);
        double used = cells.anodeUse(kg);
        anodeMetalKg = Math.max(0, anodeMetalKg - used);
        cathodeKg += kg;
        if (cells.mode() == Mode.COPPER) pgmKg += kg * Electrorefining.PGM_PER_CU;
        else {
            pgmKg += used * Flotation.PGM_PER_NI;
            cobaltKg += used * Flotation.CO_PER_NI * Electrorefining.CO_RECOVERY;
        }
        var cathode = cells.mode() == Mode.COPPER ? PcItems.COPPER_CATHODE.get() : PcItems.NICKEL_INGOT.get();
        while (cathodeKg >= ingot && emit(cathode)) cathodeKg -= ingot;
        while (pgmKg >= PgmRefinery.PGM_PER_LOT_KG && emit(PcItems.ANODE_SLIME.get())) pgmKg -= PgmRefinery.PGM_PER_LOT_KG;
        while (cobaltKg >= NonferrousUnits.COBALT_INGOT_KG && emit(PcItems.COBALT_INGOT.get())) cobaltKg -= NonferrousUnits.COBALT_INGOT_KG;
    }

    @Override
    protected boolean running() {
        return cells.lastMetalKgS() > 0;
    }

    @Override
    @Nullable
    public IFluidHandler fluids(@Nullable Direction side) {
        return null;
    }

    @Override
    public PanelLayout panelLayout() {
        return NonferrousPanels.REFINING;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[NonferrousPanels.A] = (int) Math.round(cells.lastVoltage() * 100);
        v[NonferrousPanels.B] = (int) Math.round(cells.lastAmps() / 100);
        v[NonferrousPanels.C] = (int) Math.round(cells.lastMetalKgS() * 3600);
        v[NonferrousPanels.D] = Double.isFinite(cells.specificKwhPerKg()) ? (int) Math.round(cells.specificKwhPerKg() * 1000) : 0;
        v[NonferrousPanels.E] = cells.mode().ordinal();
        v[NonferrousPanels.TANK] = (int) Math.round(Math.min(1, anodeMetalKg / NonferrousUnits.ingotKg()) * 1000);
        v[NonferrousPanels.TANK3] = (int) Math.round(cells.currentSetA() / 100);
        v[NonferrousPanels.FLAGS] = (on ? 1 << NonferrousPanels.F_ON : 0) | (running() ? 1 << NonferrousPanels.F_RUN : 0)
                | (on && anodeMetalKg <= 0 ? 1 << NonferrousPanels.F_WARN : 0)
                | (cells.mode() == Mode.NICKEL ? 1 << NonferrousPanels.F_AUX : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case NonferrousPanels.B_RUN -> use(player);
            case NonferrousPanels.B_DOWN -> cells.setCurrentSetA(cells.currentSetA() - 2e3);
            case NonferrousPanels.B_UP -> cells.setCurrentSetA(cells.currentSetA() + 2e3);
            case NonferrousPanels.B_AUX -> {
                Mode next = cells.mode() == Mode.COPPER ? Mode.NICKEL : Mode.COPPER;
                player.displayClientMessage(NonferrousText.msg(setMode(next) ? "refining.mode_" + next.name().toLowerCase(java.util.Locale.ROOT)
                        : "refining.mode_busy"), true);
            }
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("anode", anodeMetalKg);
        tag.putDouble("cathode", cathodeKg);
        tag.putDouble("pgm", pgmKg);
        tag.putDouble("co", cobaltKg);
        MutableStateTree t = new MutableStateTree();
        cells.save(new TreeStateWriter(t));
        tag.put("cells", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        anodeMetalKg = tag.getDouble("anode");
        cathodeKg = tag.getDouble("cathode");
        pgmKg = tag.getDouble("pgm");
        cobaltKg = tag.getDouble("co");
        if (tag.contains("cells")) cells.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("cells"))));
    }

    // --- блок реального масштаба с цельной моделью (никелевая серия — модель _ni) ---

    @Override
    @org.jetbrains.annotations.Nullable
    public String wholeModelId() {
        return NonferrousReal.model(getBlockState(), getBlockState().getValue(dev.denfry.promptcritical.mod.block.PcProperties.NICKEL) ? "ni" : "");
    }

    @Override
    public net.minecraft.core.Direction wholeModelFacing() {
        return NonferrousReal.facing(getBlockState());
    }

    @Override
    public net.minecraft.core.BlockPos wholeModelLightPos() {
        return NonferrousReal.lightPos(worldPosition, getBlockState(), 1);
    }
}
