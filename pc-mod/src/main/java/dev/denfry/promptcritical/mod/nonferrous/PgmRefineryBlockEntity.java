package dev.denfry.promptcritical.mod.nonferrous;

import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.nonferrous.PgmRefinery;
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
 * Аффинажный участок платиноидов ({@link PgmRefinery}, 08 §8.8.4) 2×2×1. Две стадии идут параллельно: анодный шлам
 * с серной кислотой → {@code pgm_concentrate} в склад; концентрат в царской водке (азотная кислота и хлороводород) →
 * {@code platinum_ingot} (100 г) и {@code iridium_ingot} (10 г). Шлам и концентрат — воронкой или рукой, кислоты и
 * HCl — трубами к любой грани, продукты — воронкой снизу.
 */
public final class PgmRefineryBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 4_000;

    private final FeedTank acid = new FeedTank(PcFluids.SULFURIC_ACID.source.get(), MB);
    private final FeedTank nitric = new FeedTank(PcFluids.NITRIC_ACID.source.get(), MB);
    private final FeedTank hcl = new FeedTank(PcFluids.HYDROGEN_CHLORIDE.source.get(), MB);
    private final IFluidHandler inlet = new MultiInlet(acid, nitric, hcl);
    /** Время стадий в работе, с физ.; платина и иридий, ещё не выданные, кг; всего платиноидов в продукт, кг. */
    private double leachS, dissolveS, platinumKg, iridiumKg, totalPgmKg;
    private boolean leaching, dissolving;

    public PgmRefineryBlockEntity(BlockPos pos, BlockState state) {
        super(NonferrousBlockEntities.PGM_REFINERY.get(), pos, state, 4, 6,
                s -> s.is(PcItems.ANODE_SLIME.get()) || s.is(PcItems.PGM_CONCENTRATE.get()));
    }

    public FeedTank acid() {
        return acid;
    }

    public FeedTank nitric() {
        return nitric;
    }

    public FeedTank hcl() {
        return hcl;
    }

    public double totalPgmKg() {
        return totalPgmKg;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        leaching = on && count(s -> s.is(PcItems.ANODE_SLIME.get())) > 0 && acid.amount() >= PgmRefinery.ACID_L_PER_LOT
                && hasRoom(PcItems.PGM_CONCENTRATE.get());
        if (leaching && (leachS += dt) >= PgmRefinery.STAGE_S) {
            leachS = 0;
            takeOne(s -> s.is(PcItems.ANODE_SLIME.get()));
            acid.take(PgmRefinery.ACID_L_PER_LOT);
            emit(PcItems.PGM_CONCENTRATE.get());
        }
        dissolving = on && count(s -> s.is(PcItems.PGM_CONCENTRATE.get())) > 0 && nitric.amount() >= PgmRefinery.NITRIC_L_PER_LOT
                && hcl.amount() >= PgmRefinery.HCL_NM3_PER_LOT && hasRoom(PcItems.PLATINUM_INGOT.get()) && hasRoom(PcItems.IRIDIUM_INGOT.get());
        if (dissolving && (dissolveS += dt) >= PgmRefinery.STAGE_S) {
            dissolveS = 0;
            takeOne(s -> s.is(PcItems.PGM_CONCENTRATE.get()));
            nitric.take(PgmRefinery.NITRIC_L_PER_LOT);
            hcl.take(PgmRefinery.HCL_NM3_PER_LOT);
            platinumKg += PgmRefinery.platinumPerLot();
            iridiumKg += PgmRefinery.iridiumPerLot();
            totalPgmKg += PgmRefinery.PGM_PER_LOT_KG * PgmRefinery.RECOVERY;
        }
        // слитки по массе: допуск на округление в доли грамма
        while (platinumKg >= NonferrousUnits.PLATINUM_INGOT_KG - 1e-9 && emit(PcItems.PLATINUM_INGOT.get()))
            platinumKg -= NonferrousUnits.PLATINUM_INGOT_KG;
        while (iridiumKg >= NonferrousUnits.IRIDIUM_INGOT_KG - 1e-9 && emit(PcItems.IRIDIUM_INGOT.get()))
            iridiumKg -= NonferrousUnits.IRIDIUM_INGOT_KG;
    }

    @Override
    protected boolean running() {
        return leaching || dissolving;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return inlet;
    }

    @Override
    public PanelLayout panelLayout() {
        return NonferrousPanels.PGM;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[NonferrousPanels.A] = (int) Math.round(leachS / PgmRefinery.STAGE_S * 1000);
        v[NonferrousPanels.B] = (int) Math.round(dissolveS / PgmRefinery.STAGE_S * 1000);
        v[NonferrousPanels.C] = (int) Math.round(platinumKg * 1000);
        v[NonferrousPanels.D] = (int) Math.round(iridiumKg * 1e4);
        v[NonferrousPanels.E] = (int) Math.round(totalPgmKg * 1000);
        v[NonferrousPanels.TANK] = (int) Math.round(acid.fill() * 1000);
        v[NonferrousPanels.TANK2] = (int) Math.round(nitric.fill() * 1000);
        v[NonferrousPanels.TANK3] = (int) Math.round(hcl.fill() * 1000);
        v[NonferrousPanels.FLAGS] = (on ? 1 << NonferrousPanels.F_ON : 0) | (running() ? 1 << NonferrousPanels.F_RUN : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == NonferrousPanels.B_RUN) use(player);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        acid.save(tag, "acid");
        nitric.save(tag, "nitric");
        hcl.save(tag, "hcl");
        tag.putDouble("leach", leachS);
        tag.putDouble("dissolve", dissolveS);
        tag.putDouble("pt", platinumKg);
        tag.putDouble("ir", iridiumKg);
        tag.putDouble("total", totalPgmKg);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        acid.load(tag, "acid");
        nitric.load(tag, "nitric");
        hcl.load(tag, "hcl");
        leachS = tag.getDouble("leach");
        dissolveS = tag.getDouble("dissolve");
        platinumKg = tag.getDouble("pt");
        iridiumKg = tag.getDouble("ir");
        totalPgmKg = tag.getDouble("total");
    }
}
