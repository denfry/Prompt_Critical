package dev.denfry.promptcritical.mod.production;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import java.util.Arrays;
import net.minecraft.world.phys.AABB;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Станок-качалка ({@link dev.denfry.promptcritical.sim.production.BeamPump}): редуктор 8:1 — 8 качаний в минуту при
 * 64 об/мин вала. Нагрузку на вал по динамограмме сообщает устье скважины в {@link #RADIUS} блоках; щит качалки —
 * тот же, что у устья: динамограмма, давления, дебит.
 */
public final class PumpjackBlockEntity extends DeviceBlockEntity implements ShaftConsumer, FoundryDevice, PanelHost,
        dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity {
    public static final double NOMINAL_RPM = 64, GEAR = 8, RATED_W = 30e3, RADIUS = 4;

    private final ShaftInput shaft = new ShaftInput();
    private double loadW;
    /** Физическое время работы с нагрузкой скважины, с (11 §11.2.2 {@code pumpjack}: сутки SLOW). */
    private double pumpingS;
    public static final double PUMPJACK_DAY_S = 86_400;

    public PumpjackBlockEntity(BlockPos pos, BlockState state) {
        super(ProductionBlockEntities.PUMPJACK.get(), pos, state);
    }

    private long tick() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        return b == null ? 0 : b.world().clock().tick();
    }

    /** Качаний в минуту. */
    public double spm() {
        return shaft.rpm(tick()) / GEAR;
    }

    public void load(double watts) {
        loadW = Math.max(0, watts);
    }

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        shaft.sample(rpm, healthy, tick);
    }

    @Override
    public double shaftPowerW() {
        return spm() > 0 ? loadW : 0;
    }

    @Override
    public double ratedShaftW() {
        return RATED_W;
    }

    @Override
    public double nominalShaftRpm() {
        return NOMINAL_RPM;
    }

    @Override
    public void tickDevice() {
        if (level == null || level.isClientSide || level.getGameTime() % 10 != 0) return;
        BlockState s = getBlockState();
        boolean running = spm() > 0;
        if (s.getValue(PcProperties.RUNNING) != running) level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, running), Block.UPDATE_ALL);
        SimLevelBinding b = level instanceof ServerLevel sl ? SimBindings.get(sl) : null;
        if (running && loadW > 0 && b != null) {
            boolean before = pumpingS >= PUMPJACK_DAY_S;
            pumpingS += 10 * 0.05 * b.world().clock().kSlow();
            if (!before && pumpingS >= PUMPJACK_DAY_S) dev.denfry.promptcritical.mod.content.SimAchievements.once(this, dev.denfry.promptcritical.mod.content.SimAchievements.PUMPJACK_DAY, "hours", pumpingS / 3600);
            if (level.getGameTime() % 200 == 0) setChanged();
        }
    }

    /** Время работы с нагрузкой, ч физ. */
    public double pumpingHours() {
        return pumpingS / 3600;
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("pumping_s", pumpingS);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pumpingS = tag.getDouble("pumping_s");
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

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    /** Устье, на которое работает качалка. */
    @Nullable
    public WellheadBlockEntity wellhead() {
        var all = Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), WellheadBlockEntity.class);
        WellheadBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (WellheadBlockEntity w : all) {
            double d = w.getBlockPos().distSqr(worldPosition);
            if (d < bestD) {
                bestD = d;
                best = w;
            }
        }
        return best;
    }

    @Override
    public PanelLayout panelLayout() {
        return real() ? ProductionPanels.PUMPJACK_REAL : ProductionPanels.WELLHEAD;
    }

    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing(), real() ? 2 : 1);
    }

    @Override
    public void onPanelButton(net.minecraft.server.level.ServerPlayer player, int id) {
        WellheadBlockEntity w = wellhead();
        if (w != null) w.onPanelButton(player, id);
    }

    @Override
    public void readPanel(int[] out) {
        WellheadBlockEntity w = wellhead();
        if (w != null) w.readPanel(out);
        else Arrays.fill(out, 0);
    }
}
