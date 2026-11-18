package dev.denfry.promptcritical.mod.uranium;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.uranium.AduPrecipitation;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Осадитель диураната аммония ({@link AduPrecipitation}) с фильтр-прессом и сушилкой. Элюат и жидкий аммиак (1 мБ =
 * 1 л, 0,682 кг) — трубами к любой грани; жёлтый кек — мешками по {@value UraniumUnits#U_PER_ITEM_KG} кг урана в склад
 * продукта (воронкой снизу).
 */
public final class PrecipitationBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 8_000;
    /** Элюат на шаг, л/с [BALANCE]. */
    public static final double ELUATE_LS = 20;

    private final FeedTank eluate = new FeedTank(PcFluids.URANIUM_ELUATE.source.get(), MB);
    private final FeedTank ammonia = new FeedTank(PcFluids.AMMONIA.source.get(), MB / 8);
    private final IFluidHandler inlet = new MultiInlet(eluate, ammonia);
    private final AduPrecipitation adu = new AduPrecipitation();
    private double cakeKgU, ammoniaShare = 1;

    public PrecipitationBlockEntity(BlockPos pos, BlockState state) {
        super(UraniumBlockEntities.PRECIPITATION.get(), pos, state, 0, 4, s -> false);
    }

    public AduPrecipitation adu() {
        return adu;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        if (on && hasRoom(PcItems.YELLOWCAKE.get())) {
            double litres = Math.min(eluate.amount(), ELUATE_LS * dt);
            double u = litres * UraniumUnits.ELUATE_KG_U_L;
            double needL = u * AduPrecipitation.NH3_KG_PER_KG_U / UraniumUnits.NH3_KG_L;
            double nh3L = Math.min(needL, ammonia.amount());
            ammoniaShare = needL > 0 ? nh3L / needL : ammoniaShare;
            eluate.take(litres);
            ammonia.take(nh3L);
            cakeKgU += adu.step(u, nh3L * UraniumUnits.NH3_KG_L);
        }
        while (cakeKgU >= UraniumUnits.U_PER_ITEM_KG && emit(PcItems.YELLOWCAKE.get())) cakeKgU -= UraniumUnits.U_PER_ITEM_KG;
    }

    @Override
    protected boolean running() {
        return on && eluate.amount() > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return inlet;
    }

    @Override
    public PanelLayout panelLayout() {
        return UraniumPanels.ADU;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[UraniumPanels.A] = (int) Math.round(AduPrecipitation.recovery(ammoniaShare) * 1000);
        v[UraniumPanels.B] = (int) Math.round(ammoniaShare * 100);
        v[UraniumPanels.C] = (int) Math.round(cakeKgU * 100);
        v[UraniumPanels.TANK] = (int) Math.round(eluate.fill() * 1000);
        v[UraniumPanels.TANK2] = (int) Math.round(ammonia.fill() * 1000);
        UraniumPanels.ledger(v, adu.ledger());
        v[UraniumPanels.FLAGS] = (on ? 1 << UraniumPanels.F_ON : 0) | (running() ? 1 << UraniumPanels.F_RUN : 0)
                | (running() && ammoniaShare < 0.99 ? 1 << UraniumPanels.F_WARN : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == UraniumPanels.B_RUN) use(player);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        eluate.save(tag, "eluate");
        ammonia.save(tag, "nh3");
        tag.putDouble("cake", cakeKgU);
        MutableStateTree t = new MutableStateTree();
        adu.save(new TreeStateWriter(t));
        tag.put("adu", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        eluate.load(tag, "eluate");
        ammonia.load(tag, "nh3");
        cakeKgU = tag.getDouble("cake");
        if (tag.contains("adu")) adu.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("adu"))));
    }
}
