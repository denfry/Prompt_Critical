package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.metallurgy.BatchFurnaceUnit;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatment;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Термическая печь {@code heat_treatment_furnace} 2×2×2 (08 §8.7.5): садка проката и труб, нагрев до уставки, выдача
 * всей садки кнопкой «Выгрузить» (дверь поднимается). Режим «Закалка» — в закалочный бак рядом
 * ({@link QuenchTankBlockEntity}, до {@value #TANK_REACH} блоков): {@link HeatTreatment#quench}, металл выходит
 * холодным; режим «Воздух» — {@link HeatTreatment#airCool} (отпуск закалённой, нормализация, снятие напряжений,
 * старение), металл выходит горячим. Трубам марка меняется и в {@code pipe_spec}.
 */
public final class HeatTreatFurnaceBlockEntity extends BatchFurnaceBlockEntity {
    public static final int TANK_REACH = 2, DOOR_TICKS = 40;
    private boolean quench;
    private int doorTicks;

    public HeatTreatFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.HEAT_TREATMENT_FURNACE.get(), pos, state, BatchFurnaceUnit.Spec.HEAT_TREATMENT.capacity());
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return Steelworks.isStock(stack) || Steelworks.isPipe(stack) && Steelworks.stock(stack) != null;
    }

    @Override
    protected double massKg(ItemStack s) {
        SteelworksComponents.PipeData p = s.get(SteelworksComponents.PIPE_SPEC.get());
        return p != null ? p.kgPerM() : Ironworks.bulkKg();
    }

    @Override
    protected double thicknessM(ItemStack s) {
        SteelworksComponents.SteelStock st = Steelworks.stock(s);
        return st == null ? 0.01 : Math.min(st.thicknessM(), st.widthM());
    }

    public boolean quenchMode() {
        return quench;
    }

    public void setQuenchMode(boolean q) {
        quench = q;
        setChanged();
    }

    @Override
    protected void discharge(BatchFurnaceUnit u, long now) {
        if (doorTicks > 0 && --doorTicks == 0) setStateFlag(BlockStateProperties.OPEN, false);
    }

    @Nullable
    private QuenchTankBlockEntity tank() {
        AABB box = new AABB(worldPosition).inflate(TANK_REACH + 1, 1, TANK_REACH + 1);
        List<QuenchTankBlockEntity> found = Ironworks.blockEntities(level, box, QuenchTankBlockEntity.class);
        return found.isEmpty() ? null : found.getFirst();
    }

    /** Выгрузить всю садку в выходы: закалка в бак или на воздух. */
    public void unload() {
        BatchFurnaceUnit u = unit();
        if (u == null) return;
        long now = level.getGameTime();
        QuenchTankBlockEntity tank = quench ? tank() : null;
        boolean any = false;
        for (int i = 0; i < places.getSlots(); i++) {
            if (places.getStackInSlot(i).isEmpty()) continue;
            double t = u.furnace().pieceTempK(i);
            double kg = massKg(places.getStackInSlot(i));
            ItemStack s = take(u, i, now);
            SteelworksComponents.SteelStock st = Steelworks.stock(s);
            any = true;
            if (st == null || Double.isNaN(t)) {
                toOutput(s);
                continue;
            }
            SteelGrade g = st.grade(), ng;
            double outK;
            if (quench) {
                boolean effective = tank != null && tank.absorb(kg, t);
                ng = effective ? HeatTreatment.quench(g, t) : g;
                outK = effective ? tank.tempK() : t;
            } else {
                ng = HeatTreatment.airCool(g, t);
                outK = t;
            }
            s.set(SteelworksComponents.STEEL_STOCK.get(), st.withGrade(ng).withTemp(outK, now));
            SteelworksComponents.PipeData p = s.get(SteelworksComponents.PIPE_SPEC.get());
            if (p != null) s.set(SteelworksComponents.PIPE_SPEC.get(), SteelworksComponents.PipeData.of(p.spec().withGrade(ng)));
            toOutput(s);
        }
        if (any) {
            doorTicks = DOOR_TICKS;
            setStateFlag(BlockStateProperties.OPEN, true);
            level.playSound(null, worldPosition, quench && tank != null ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.BLOCKS, 1.0f, 0.8f);
        }
    }

    @Override
    protected int panelFlags() {
        return quench ? 1 << SteelworksPanels.RF_QUENCH : 0;
    }

    @Override
    protected void button(BatchFurnaceUnit u, int id) {
        if (id == SteelworksPanels.BTN_UNLOAD) unload();
        else if (id == SteelworksPanels.BTN_MODE) setQuenchMode(!quench);
    }

    @Override
    public PanelLayout panelLayout() {
        return SteelworksPanels.HEAT_TREATMENT;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("quench", quench);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        quench = tag.getBoolean("quench");
    }
}
