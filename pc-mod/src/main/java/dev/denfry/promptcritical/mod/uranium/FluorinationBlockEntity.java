package dev.denfry.promptcritical.mod.uranium;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.environment.ToxicReleaseService;
import dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.uranium.DryConversion;
import dev.denfry.promptcritical.sim.uranium.UraniumLedger;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Пламенный реактор фторирования ({@link DryConversion.Step#FLUORINATE}) с холодильными ловушками и наполнением
 * баллонов. Тетрафторид — мешками по {@value UraniumUnits#U_PER_ITEM_KG} кг урана и пустые баллоны UF₆ (48Y или 5A)
 * — воронкой или рукой; фтор (нм³) — трубой к любой грани. UF₆ вымораживается в ловушке (до {@value #TRAP_KG} кг) и
 * сливается в баллон в загрузке; полный баллон (или по кнопке «Выдать») уходит в склад продукта. Состав — природный
 * уран. Если баллона нет, а ловушка полна, UF₆ проходит ловушки и уходит со сдувкой — облако HF от гидролиза.
 */
public final class FluorinationBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 4_000;
    public static final double TRAP_KG = 50;
    static final double UF6_PER_U = UraniumLedger.M_UF6 / UraniumLedger.M_U;

    private final FeedTank fluorine = new FeedTank(PcFluids.FLUORINE.source.get(), MB);
    private final DryConversion stage = new DryConversion(DryConversion.Step.FLUORINATE);
    private double feedKgU, trapKgUf6, ventKgS;
    private boolean ship;

    public FluorinationBlockEntity(BlockPos pos, BlockState state) {
        super(UraniumBlockEntities.FLUORINATION.get(), pos, state, 4, 2, FluorinationBlockEntity::loadable);
    }

    static boolean loadable(ItemStack s) {
        return s.is(PcItems.UF4_POWDER.get()) || s.is(PcItems.UF6_CYLINDER_48Y.get()) || s.is(PcItems.UF6_CYLINDER_5A.get());
    }

    public DryConversion stage() {
        return stage;
    }

    public double trapKgUf6() {
        return trapKgUf6;
    }

    public double ventKgS() {
        return ventKgS;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        if (on) {
            while (feedKgU < stage.kind().capacityKgS * dt && !takeOne(s -> s.is(PcItems.UF4_POWDER.get())).isEmpty())
                feedKgU += UraniumUnits.U_PER_ITEM_KG;
            double kg = Math.min(feedKgU, stage.capacityKg(dt, fluorine.amount() * UraniumUnits.F2_KG_NM3, 0));
            feedKgU -= kg;
            fluorine.take(kg * stage.kind().reagentWithExcess() / UraniumUnits.F2_KG_NM3);
            trapKgUf6 += stage.process(kg, dt) * UF6_PER_U;
        } else stage.process(0, dt);
        // ловушка → баллон
        int slot = cylinderSlot();
        if (slot >= 0) {
            ItemStack cyl = input.getStackInSlot(slot);
            double move = Math.min(trapKgUf6, Uf6CylinderItem.roomKg(cyl));
            if (move > 0) {
                ItemStack filled = Uf6CylinderItem.fill(cyl.copy(), move, UraniumUnits.NATURAL_U235, UraniumUnits.NATURAL_U234);
                input.setStackInSlot(slot, filled);
                trapKgUf6 -= move;
                dev.denfry.promptcritical.mod.content.SimAchievements.once(this,
                        dev.denfry.promptcritical.mod.content.SimAchievements.UF6_FILLED, "kg", move);
            }
            ItemStack now = input.getStackInSlot(slot);
            if ((Uf6CylinderItem.roomKg(now) < 1e-3 || ship) && Uf6CylinderItem.charge(now).uf6Kg() > 0 && emit(now.copy())) {
                input.setStackInSlot(slot, ItemStack.EMPTY);
                ship = false;
            }
        }
        // ловушка переполнена — UF₆ уходит со сдувкой
        double over = Math.max(0, trapKgUf6 - TRAP_KG);
        trapKgUf6 -= over;
        ventKgS = over / dt;
        if (over > 0) {
            stage.ledger().loss(over / UF6_PER_U);
            ToxicReleaseService.uf6Vent(level, worldPosition.above(), ventKgS);
            if (level.getGameTime() % 200 == 0)
                for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 48 * 48))
                    p.displayClientMessage(UraniumText.msg("uf6.vent").withStyle(ChatFormatting.RED), false);
        }
    }

    /** Слот загрузки с баллоном, в который можно лить; −1 — нет. */
    private int cylinderSlot() {
        for (int i = 0; i < input.getSlots(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (s.getItem() instanceof Uf6CylinderItem && Uf6CylinderItem.accepts(s, UraniumUnits.NATURAL_U235)
                    && (Uf6CylinderItem.roomKg(s) > 1e-3 || Uf6CylinderItem.charge(s).uf6Kg() > 0)) return i;
        }
        return -1;
    }

    /** Электролизёры фтора и вентиляторы, Вт. */
    @Override
    protected double ratedPowerW() {
        return 110e3;
    }

    @Override
    protected boolean running() {
        return stage.lastKgS() > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return fluorine;
    }

    @Override
    public PanelLayout panelLayout() {
        return UraniumPanels.FLUORINATION;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[UraniumPanels.T_C] = (int) Math.round(stage.tempK() - 273.15);
        v[UraniumPanels.A] = (int) Math.round(DryConversion.efficiency(stage.kind(), stage.tempK()) * 1000);
        v[UraniumPanels.B] = (int) Math.round(stage.lastKgS() * UF6_PER_U * 3600);
        v[UraniumPanels.C] = (int) Math.round(trapKgUf6 * 10);
        int slot = cylinderSlot();
        v[UraniumPanels.D] = slot < 0 ? 0 : (int) Math.round(Uf6CylinderItem.charge(input.getStackInSlot(slot)).uf6Kg() * 10);
        v[UraniumPanels.TANK] = (int) Math.round(fluorine.fill() * 1000);
        UraniumPanels.ledger(v, stage.ledger());
        v[UraniumPanels.FLAGS] = (on ? 1 << UraniumPanels.F_ON : 0) | (running() ? 1 << UraniumPanels.F_RUN : 0)
                | (ventKgS > 0 ? 1 << UraniumPanels.F_LEAK : 0) | (slot < 0 ? 1 << UraniumPanels.F_WARN : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == UraniumPanels.B_RUN) use(player);
        else if (id == UraniumPanels.B_T_DOWN) stage.setTempK(stage.tempK() - 10);
        else if (id == UraniumPanels.B_T_UP) stage.setTempK(stage.tempK() + 10);
        else if (id == UraniumPanels.B_AUX) ship = true;
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        fluorine.save(tag, "f2");
        tag.putDouble("feed", feedKgU);
        tag.putDouble("trap", trapKgUf6);
        MutableStateTree t = new MutableStateTree();
        stage.save(new TreeStateWriter(t));
        tag.put("stage", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        fluorine.load(tag, "f2");
        feedKgU = tag.getDouble("feed");
        trapKgUf6 = tag.getDouble("trap");
        if (tag.contains("stage")) stage.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("stage"))));
    }
}
