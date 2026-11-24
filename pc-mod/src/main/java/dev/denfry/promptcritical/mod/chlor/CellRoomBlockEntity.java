package dev.denfry.promptcritical.mod.chlor;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.environment.ToxicReleaseService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.grid.DcLoad;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.chlor.BrineTreatment;
import dev.denfry.promptcritical.sim.chlor.DiaphragmCellRoom;
import dev.denfry.promptcritical.sim.consequence.BlastScaling;
import dev.denfry.promptcritical.sim.consequence.DamageBudgetConfig;
import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.sim.consequence.DamageRequest;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.consequence.ToxicGas;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Серия диафрагменных ванн ({@link DiaphragmCellRoom}): {@value DiaphragmCellRoom#CELLS} электролизёров на одном
 * блоке. Постоянный ток даёт выпрямительный агрегат ({@link RectifierBlockEntity}) в {@value RectifierBlockEntity#REACH}
 * блоках. Рассол — трубой к любой боковой грани: очищенный или сырой (сырой забивает диафрагму). Хлор — сверху
 * (1 мБ = 1 нм³), водород — снизу (нм³), электролитическая щёлочь — с боков (л). Если хлору некуда уходить, давление в
 * коллекторе растёт и хлор выходит через гидрозатворы наружу — облако. Если рассола не хватает и водород в хлоре
 * переходит 4 %, защита отключает серию, но смесь в газовых пространствах уже взрывается и выбрасывает хлор.
 */
public final class CellRoomBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost, DcLoad, dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity {
    public static final int BRINE_MB = 16_000, GAS_MB = 4_000, LIQUOR_MB = 16_000;
    /** Хлор в анодных пространствах серии, кг: столько уходит облаком при взрыве. */
    public static final double CELL_HOLDUP_CL2_KG = 40;
    /** Энергия хлоро-водородного взрыва в газовых пространствах, Дж (≈ 2 м³ смеси по 0,9 МДж/м³) [допущение]. */
    public static final double BLAST_J = 2e6;

    private final FeedTank brine = new FeedTank(PcFluids.BRINE.source.get(), BRINE_MB);
    private final FeedTank rawBrine = new FeedTank(PcFluids.RAW_BRINE.source.get(), BRINE_MB);
    private final IFluidHandler brineIn = new MultiInlet(brine, rawBrine);
    private final ProductTank chlorine = new ProductTank(PcFluids.CHLORINE.source.get(), GAS_MB);
    private final ProductTank hydrogen = new ProductTank(PcFluids.HYDROGEN.source.get(), GAS_MB);
    private final ProductTank liquor = new ProductTank(PcFluids.SODIUM_HYDROXIDE_SOLUTION.source.get(), LIQUOR_MB);
    private final DiaphragmCellRoom cells = new DiaphragmCellRoom();
    /** Ток, который выпрямитель даёт на этот тик, А; без обновления два тика — ноль. */
    private double suppliedAmps;
    private long suppliedTick = -10;
    private double ventKgS;
    private boolean wasTripped;
    private static long requestSeq;

    public CellRoomBlockEntity(BlockPos pos, BlockState state) {
        super(ChlorBlockEntities.CELL_ROOM.get(), pos, state);
    }

    public DiaphragmCellRoom cells() {
        return cells;
    }

    public FeedTank brine() {
        return brine;
    }

    public FeedTank rawBrine() {
        return rawBrine;
    }

    public ProductTank chlorine() {
        return chlorine;
    }

    public ProductTank hydrogen() {
        return hydrogen;
    }

    public ProductTank liquor() {
        return liquor;
    }

    public double ventKgS() {
        return ventKgS;
    }

    /** Ток от выпрямителя на этот тик, А. */
    @Override
    public void supply(double amps) {
        suppliedAmps = Math.max(0, amps);
        suppliedTick = level.getGameTime();
    }

    /** Мощность постоянного тока, которую серия возьмёт при заданном токе, Вт. */
    @Override
    public double dcSetpointA() {
        return cells.currentKa() * 1000;
    }

    @Override
    public double dcDemandW() {
        return cells.on() && !cells.tripped() ? cells.dcDemandW(cells.currentKa() * 1000) : 0;
    }

    /** Серия по клетке или блоку в радиусе, ближайшая. */
    @Nullable
    static CellRoomBlockEntity near(Level level, BlockPos pos, int reach) {
        CellRoomBlockEntity best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-reach, -reach, -reach), pos.offset(reach, reach, reach)))
            if (level.getBlockEntity(p) instanceof CellRoomBlockEntity c && p.distSqr(pos) < bd) {
                best = c;
                bd = p.distSqr(pos);
            }
        return best;
    }

    /**
     * Эффекты на клиенте: из перколятора на фасаде (art/blockbench/chlor_alkali/chlor_alkali_cell.author.js) в жёлоб
     * каплет электролитическая щёлочь, пока ячейка под током.
     */
    private void clientTick() {
        net.minecraft.world.level.block.state.BlockState st = getBlockState();
        if (!st.hasProperty(PcProperties.RUNNING) || !st.getValue(PcProperties.RUNNING)) return;
        dev.denfry.promptcritical.mod.fx.MachineFx fx = dev.denfry.promptcritical.mod.fx.MachineFx.of(level, worldPosition,
                st.getBlock() instanceof dev.denfry.promptcritical.mod.block.PcBlock pb ? pb.facing(st) : net.minecraft.core.Direction.NORTH);
        if (fx != null && fx.chance(3)) fx.leak(real() ? fx.at(8, 9, 32) : fx.at(11.5, 3.3, 1));
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) {
            clientTick();
            return;
        }
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        double dt = 0.05 * b.world().clock().kSlow();
        double amps = level.getGameTime() - suppliedTick <= 2 ? suppliedAmps : 0;
        // рассол: сначала очищенный, затем сырой; жёсткость — средняя по смеси
        double need = DiaphragmCellRoom.brineDemandLs(Math.min(cells.currentKa(), amps / 1000)) * dt;
        double fromSoft = Math.min(need, brine.amount()), fromRaw = Math.min(need - fromSoft, rawBrine.amount());
        double avail = fromSoft + fromRaw;
        double hardness = avail > 0 ? (fromSoft * BrineTreatment.TREATED_HARDNESS_MG_L + fromRaw * BrineTreatment.RAW_HARDNESS_MG_L) / avail
                : BrineTreatment.TREATED_HARDNESS_MG_L;
        double used = cells.step(dt, amps, avail, hardness);
        double soft = Math.min(used, fromSoft);
        brine.take(soft);
        rawBrine.take(used - soft);
        // продукты; хлор, которому некуда уходить, — наружу
        double cl2Nm3 = cells.cl2KgS() * dt / ChlorUnits.CL2_KG_NM3;
        double room = chlorine.room();
        chlorine.add(Math.min(cl2Nm3, room));
        ventKgS = Math.max(0, cl2Nm3 - room) * ChlorUnits.CL2_KG_NM3 / dt;
        if (ventKgS > 1e-6) ToxicReleaseService.vent(s, worldPosition.above(), ToxicGas.CHLORINE, ventKgS);
        hydrogen.add(Math.min(hydrogen.room(), cells.h2KgS() * dt / ChlorUnits.H2_KG_NM3)); // избыток водорода — на свечу
        liquor.add(Math.min(liquor.room(), cells.liquorKgS() * dt / ChlorUnits.LIQUOR_KG_L));
        if (cells.tripped() && !wasTripped && cells.h2InCl2() > DiaphragmCellRoom.H2_IN_CL2_LIMIT) explode(s, b);
        wasTripped = cells.tripped();
        if (level.getGameTime() % 10 == 0) {
            setFlag(PcProperties.RUNNING, cells.cl2KgS() > 0);
            if (cells.cl2KgS() > 0)
                s.sendParticles(ParticleTypes.BUBBLE_POP, worldPosition.getX() + 0.5, worldPosition.getY() + 0.9, worldPosition.getZ() + 0.5,
                        3, 0.3, 0.05, 0.3, 0);
        }
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    /** Водород в хлоре выше предела: смесь в газовых пространствах ванн взрывается, хлор выбрасывает облаком. */
    private void explode(ServerLevel s, SimLevelBinding b) {
        GridPos o = new GridPos(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        b.damage().submit(new DamageRequest(requestSeq++, SimId.NONE, null, DamageKind.BLAST, o, BLAST_J, 2, 2));
        DamageBudgetConfig dc = b.damage().config();
        double power = Math.max(1, Math.min(3, BlastScaling.power(BLAST_J, dc.tntReferenceKg(), dc.maxExplosionPower())));
        s.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, (float) power, false,
                Level.ExplosionInteraction.NONE);
        ToxicReleaseService.puff(s, worldPosition, ToxicGas.CHLORINE, CELL_HOLDUP_CL2_KG);
        for (ServerPlayer p : s.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 96 * 96))
            p.displayClientMessage(ChlorText.msg("cells.blast").withStyle(ChatFormatting.RED), false);
    }

    private void setFlag(net.minecraft.world.level.block.state.properties.BooleanProperty prop, boolean v) {
        BlockState st = getBlockState();
        if (st.hasProperty(prop) && st.getValue(prop) != v) level.setBlock(worldPosition, st.setValue(prop, v), Block.UPDATE_CLIENTS);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == Direction.UP) return chlorine;
        if (side == Direction.DOWN) return hydrogen;
        return side == null ? brineIn : new SideHandler();
    }

    /**
     * Электролизёр реального масштаба: у каждого патрубка свой поток — рассол с юга, щёлочь (x 0) и вода (x 2) с
     * севера, хлор (x −3) и водород (x 4) сверху.
     */
    @Override
    public IFluidHandler fluidsAt(int mx, int my, int mz, Direction modelFace, Direction side) {
        if (!real()) return fluids(side);
        if (modelFace == Direction.UP) return mx < 0 ? chlorine : hydrogen;
        if (modelFace == Direction.SOUTH) return brineIn;
        return mx == 0 ? liquor : brineIn;
    }

    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing(), real() ? 2 : 1);
    }

    /** Боковая грань: рассол внутрь, щёлочь наружу. */
    private final class SideHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 3;
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) {
            return tank < 2 ? brineIn.getFluidInTank(tank) : liquor.getFluidInTank(0);
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank < 2 ? brineIn.getTankCapacity(tank) : liquor.getTankCapacity(0);
        }

        @Override
        public boolean isFluidValid(int tank, net.neoforged.neoforge.fluids.FluidStack stack) {
            return tank < 2 && brineIn.isFluidValid(tank, stack);
        }

        @Override
        public int fill(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
            return brineIn.fill(resource, action);
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
            return liquor.drain(resource, action);
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain, FluidAction action) {
            return liquor.drain(maxDrain, action);
        }
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Shift + ПКМ: пуск или останов серии. */
    @Override
    public InteractionResult use(Player player) {
        cells.setOn(!cells.on());
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        player.displayClientMessage(ChlorText.msg(cells.on() ? "cells.on" : "cells.off"), true);
        setChanged();
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return ChlorPanels.CELLS;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[ChlorPanels.C_VOLT_X100] = (int) Math.round(cells.voltageV() * 100);
        out[ChlorPanels.C_KA] = (int) Math.round(level.getGameTime() - suppliedTick <= 2 ? suppliedAmps / 1000 : 0);
        out[ChlorPanels.C_SET_KA] = (int) Math.round(cells.currentKa());
        out[ChlorPanels.C_TEMP_C] = (int) Math.round(cells.tempK() - 273.15);
        out[ChlorPanels.C_CE_PERMIL] = (int) Math.round(cells.efficiency() * 1000);
        out[ChlorPanels.C_NAOH_PERMIL] = (int) Math.round(cells.naohFraction() * 1000);
        out[ChlorPanels.C_H2_PERMIL] = (int) Math.round(cells.h2InCl2() * 1000);
        out[ChlorPanels.C_CL2_KGH] = (int) Math.round(cells.cl2KgS() * 3600);
        double kwhPerT = cells.cl2KgS() > 0 ? cells.dcW() / cells.cl2KgS() / 3.6e6 * 1000 : 0;
        out[ChlorPanels.C_KWH_T] = (int) Math.round(kwhPerT);
        out[ChlorPanels.C_FOUL_PCT] = (int) Math.round(cells.fouling() * 100);
        out[ChlorPanels.C_AGE_PCT] = (int) Math.round(cells.ageFraction() * 100);
        out[ChlorPanels.C_BRINE] = (int) Math.round(Math.min(1, (brine.amount() + rawBrine.amount()) / BRINE_MB) * 1000);
        out[ChlorPanels.C_FLAGS] = (cells.cl2KgS() > 0 ? 1 << ChlorPanels.CF_RUN : 0) | (cells.tripped() ? 1 << ChlorPanels.CF_TRIP : 0)
                | (ventKgS > 0 ? 1 << ChlorPanels.CF_VENT : 0) | (rawBrine.amount() > 0 ? 1 << ChlorPanels.CF_RAW : 0)
                | (cells.brineFraction() < 0.9 && cells.cl2KgS() > 0 ? 1 << ChlorPanels.CF_LOW_BRINE : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ChlorPanels.CB_RUN -> use(player);
            case ChlorPanels.CB_DOWN -> cells.setCurrentKa(cells.currentKa() - 2.5);
            case ChlorPanels.CB_UP -> cells.setCurrentKa(cells.currentKa() + 2.5);
            case ChlorPanels.CB_REBUILD -> {
                if (cells.on()) return; // диафрагмы меняют на отключённой серии
                cells.rebuildDiaphragms();
                player.displayClientMessage(ChlorText.msg("cells.rebuilt"), true);
            }
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        brine.save(tag, "brine");
        rawBrine.save(tag, "raw");
        chlorine.save(tag, "cl2");
        hydrogen.save(tag, "h2");
        liquor.save(tag, "liquor");
        MutableStateTree tree = new MutableStateTree();
        cells.save(new TreeStateWriter(tree));
        tag.put("cells", NbtStateTrees.toNbt(tree));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        brine.load(tag, "brine");
        rawBrine.load(tag, "raw");
        chlorine.load(tag, "cl2");
        hydrogen.load(tag, "h2");
        liquor.load(tag, "liquor");
        if (tag.contains("cells")) cells.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("cells"))));
        wasTripped = cells.tripped();
    }
}
