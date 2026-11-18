package dev.denfry.promptcritical.mod.nitrogen;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.nitrogen.ReformerTrain;
import dev.denfry.promptcritical.sim.nitrogen.Species;
import java.util.Arrays;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Трубчатая печь паровой конверсии метана с шахтным реактором вторичного риформинга, котлом-утилизатором и
 * конверторами CO ({@link ReformerTrain}) 3×3×3. Природный газ — товарный газ газопереработки трубой к любой грани:
 * и сырьё, и топливо горелок; питательная вода — на технологический пар. Продувочный газ цикла синтеза аммиака
 * возвращается сюда топливом. Газ после конверсии CO уходит в колонну очистки ({@link Co2AbsorberBlockEntity}),
 * которая должна касаться печи: состав газа по трубам не передать. Катализаторы загружают с руки: никелевый — в
 * трубы печи, железохромовый — в конвертор СТК, медноцинковый — в конвертор НТК.
 */
public final class SteamReformerBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int GAS_MB = 8_000, WATER_MB = 8_000;
    /** Низшая теплота сгорания товарного газа, Дж/нм³; доля метана в нём. */
    public static final double SALES_GAS_J_PER_NM3 = 35.8e6, CH4_SHARE = 0.99, WATER_L_PER_MOL = 0.018;

    private final FeedTank gas = new FeedTank(PcFluids.SALES_GAS.source.get(), GAS_MB);
    private final FeedTank water = new FeedTank(PcFluids.BOILER_FEEDWATER.source.get(), WATER_MB);
    private final FeedTank[] inputs = {gas, water};
    private final IFluidHandler inlet = new PyrolysisFurnaceBlockEntity.MultiInlet(inputs);
    private final ReformerTrain train = new ReformerTrain();
    private boolean running, noAbsorber, noFeed;
    private double purgeJ, ch4MolS, fuelW;

    public SteamReformerBlockEntity(BlockPos pos, BlockState state) {
        super(NitrogenBlockEntities.STEAM_REFORMER.get(), pos, state);
    }

    public ReformerTrain train() {
        return train;
    }

    public boolean running() {
        return running;
    }

    public void setRunning(boolean r) {
        running = r;
        setChanged();
    }

    public boolean noAbsorber() {
        return noAbsorber;
    }

    public double ch4MolS() {
        return ch4MolS;
    }

    public FeedTank gas() {
        return gas;
    }

    public FeedTank water() {
        return water;
    }

    /** Продувочный газ цикла синтеза — топливом горелок, Дж. */
    void acceptPurgeFuel(double j) {
        purgeJ = Math.min(purgeJ + j, 5e9);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        double dt = 0.05 * b.world().clock().kSlow();
        Co2AbsorberBlockEntity absorber = absorber();
        noAbsorber = absorber == null;
        ch4MolS = 0;
        fuelW = 0;
        if (running && !noAbsorber && train.primaryActivity() > 0) {
            double want = ReformerTrain.RATED_CH4_MOL_S * train.rateFraction();
            double fuelPerMol = train.fuelPerMolW();
            // газ: сырьё и топливо сверх продувочного газа; вода — на пар
            double gasMolAvailable = gas.amount() * Species.MOL_PER_NM3 * CH4_SHARE;
            double perMolGas = 1 + fuelPerMol / (SALES_GAS_J_PER_NM3 / Species.MOL_PER_NM3 / CH4_SHARE);
            double byGas = (gasMolAvailable + purgeJ / (SALES_GAS_J_PER_NM3 / Species.MOL_PER_NM3 / CH4_SHARE)) / perMolGas / dt;
            double byWater = water.amount() / (train.steamToCarbon() * WATER_L_PER_MOL) / dt;
            ch4MolS = Math.max(0, Math.min(want, Math.min(byGas, byWater)));
            noFeed = ch4MolS < 0.05 * want;
            if (!noFeed) {
                double[] shifted = train.step(dt, ch4MolS);
                fuelW = fuelPerMol * ch4MolS;
                double fuelJ = fuelW * dt, fromPurge = Math.min(purgeJ, fuelJ);
                purgeJ -= fromPurge;
                gas.take((ch4MolS * dt / CH4_SHARE) / Species.MOL_PER_NM3 + (fuelJ - fromPurge) / SALES_GAS_J_PER_NM3);
                water.take(ch4MolS * dt * train.steamToCarbon() * WATER_L_PER_MOL);
                absorber.process(shifted, train.solution().processHeatW() * ch4MolS, dt);
            } else {
                ch4MolS = 0;
            }
        }
        // излишек продувочного газа — на факел
        if (purgeJ > 1e9) purgeJ = 1e9;
        if (level.getGameTime() % 10 == 0) setLit(ch4MolS > 0);
        if (ch4MolS > 0 && level.getGameTime() % 20 == 0)
            s.sendParticles(ParticleTypes.SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 2.5, worldPosition.getZ() + 0.5,
                    2, 0.2, 0.1, 0.2, 0.01);
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    private void setLit(boolean lit) {
        BlockState st = getBlockState();
        if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT) != lit)
            level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
    }

    /** Колонна очистки, касающаяся печи (3×3×3 вокруг контроллера). */
    @Nullable
    private Co2AbsorberBlockEntity absorber() {
        for (int x = -2; x <= 2; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) < 2 && Math.abs(z) < 2) continue;
                    Co2AbsorberBlockEntity c = Co2AbsorberBlockEntity.at(level, worldPosition.offset(x, y, z));
                    if (c != null) return c;
                }
        return null;
    }

    /** Ближайшая печь риформинга в радиусе {@code r} — ей отдают продувочный газ. */
    @Nullable
    static SteamReformerBlockEntity near(Level level, BlockPos pos, int r) {
        SteamReformerBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (SteamReformerBlockEntity f : dev.denfry.promptcritical.mod.ironworks.Ironworks.blockEntities(level,
                new net.minecraft.world.phys.AABB(pos).inflate(r), SteamReformerBlockEntity.class)) {
            double d = f.getBlockPos().distSqr(pos);
            if (d < bestD) {
                bestD = d;
                best = f;
            }
        }
        return best;
    }

    // --- руки ---

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return inlet;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.CATALYST_STEAM_REFORMING.get()) || stack.is(PcItems.CATALYST_SHIFT_HT.get())
                || stack.is(PcItems.CATALYST_SHIFT_LT.get());
    }

    /** Катализатор с руки: никель — когда активность ниже 90 %, конверсия CO — когда реактор пуст. */
    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        String key = null;
        if (stack.is(PcItems.CATALYST_STEAM_REFORMING.get())) {
            if (train.primaryActivity() < 0.9) {
                train.loadPrimaryCatalyst();
                key = "catalyst.ni";
            }
        } else if (stack.is(PcItems.CATALYST_SHIFT_HT.get())) {
            if (!train.hts()) {
                train.loadHts();
                key = "catalyst.hts";
            }
        } else if (stack.is(PcItems.CATALYST_SHIFT_LT.get())) {
            if (!train.lts()) {
                train.loadLts();
                key = "catalyst.lts";
            }
        } else {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (key == null) {
            player.displayClientMessage(NitrogenText.msg("catalyst.full"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.8f, 0.8f);
        player.displayClientMessage(NitrogenText.msg(key), true);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    /** Shift + ПКМ: пуск или останов. */
    @Override
    public InteractionResult use(Player player) {
        toggle(player);
        return InteractionResult.SUCCESS;
    }

    private void toggle(Player player) {
        if (!running && train.primaryActivity() <= 0) {
            player.displayClientMessage(NitrogenText.msg("reformer.no_catalyst"), true);
            return;
        }
        running = !running;
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6f, 0.8f);
        player.displayClientMessage(NitrogenText.msg(running ? "reformer.start" : "reformer.stop"), true);
        setChanged();
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return NitrogenPanels.REFORMER;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        ReformerTrain.Solution s = train.solution();
        boolean on = ch4MolS > 0;
        out[NitrogenPanels.R_OUT_C] = on ? (int) Math.round(train.primaryOutK() - 273.15) : 0;
        out[NitrogenPanels.R_SEC_C] = on ? (int) Math.round(s.secondaryK() - 273.15) : 0;
        out[NitrogenPanels.R_CH4_P] = (int) Math.round(s.primaryCh4Dry() * 1e4);
        out[NitrogenPanels.R_CH4_S] = (int) Math.round(s.secondaryCh4Dry() * 1e4);
        out[NitrogenPanels.R_CO_HTS] = (int) Math.round(s.htsCoDry() * 1e4);
        out[NitrogenPanels.R_CO_LTS] = (int) Math.round(s.ltsCoDry() * 1e4);
        out[NitrogenPanels.R_SC] = (int) Math.round(train.steamToCarbon() * 100);
        out[NitrogenPanels.R_RATIO] = (int) Math.round(Math.min(s.ratioOut(), 99) * 100);
        out[NitrogenPanels.R_AIR] = (int) Math.round(s.airMolS() * ch4MolS / Species.MOL_PER_NM3 * 3600);
        out[NitrogenPanels.R_FEED] = (int) Math.round(ch4MolS / CH4_SHARE / Species.MOL_PER_NM3 * 3600);
        out[NitrogenPanels.R_FUEL_KW] = (int) Math.round(fuelW / 1e3);
        out[NitrogenPanels.R_NI] = (int) Math.round(train.primaryActivity() * 1000);
        out[NitrogenPanels.R_FLAGS] = (on ? 1 << NitrogenPanels.RF_RUN : 0) | (noAbsorber ? 1 << NitrogenPanels.RF_NO_ABS : 0)
                | (train.primaryActivity() <= 0 ? 1 << NitrogenPanels.RF_NO_NI : 0) | (!train.hts() ? 1 << NitrogenPanels.RF_NO_HTS : 0)
                | (!train.lts() ? 1 << NitrogenPanels.RF_NO_LTS : 0)
                | (train.steamToCarbon() < ReformerTrain.MIN_STEAM_TO_CARBON ? 1 << NitrogenPanels.RF_CARBON : 0)
                | (running && noFeed ? 1 << NitrogenPanels.RF_NO_FEED : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case NitrogenPanels.RB_RUN -> toggle(player);
            case NitrogenPanels.RB_SC_DOWN -> train.setSteamToCarbon(train.steamToCarbon() - 0.1);
            case NitrogenPanels.RB_SC_UP -> train.setSteamToCarbon(train.steamToCarbon() + 0.1);
            case NitrogenPanels.RB_T_DOWN -> train.setPrimaryOutK(train.primaryOutK() - 5);
            case NitrogenPanels.RB_T_UP -> train.setPrimaryOutK(train.primaryOutK() + 5);
            case NitrogenPanels.RB_RATIO_DOWN -> train.setRatio(train.ratio() - 0.02);
            case NitrogenPanels.RB_RATIO_UP -> train.setRatio(train.ratio() + 0.02);
            case NitrogenPanels.RB_RATE_DOWN -> train.setRateFraction(train.rateFraction() - 0.1);
            case NitrogenPanels.RB_RATE_UP -> train.setRateFraction(train.rateFraction() + 0.1);
            default -> {
            }
        }
        setChanged();
    }

    // --- сохранение ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < inputs.length; i++) inputs[i].save(tag, "in" + i);
        MutableStateTree tree = new MutableStateTree();
        train.save(new TreeStateWriter(tree));
        tag.put("train", NbtStateTrees.toNbt(tree));
        tag.putBoolean("running", running);
        tag.putDouble("purge", purgeJ);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < inputs.length; i++) inputs[i].load(tag, "in" + i);
        if (tag.contains("train")) train.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("train"))));
        running = tag.getBoolean("running");
        purgeJ = tag.getDouble("purge");
    }
}
