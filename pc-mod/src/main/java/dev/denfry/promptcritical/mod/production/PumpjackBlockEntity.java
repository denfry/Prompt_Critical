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
public final class PumpjackBlockEntity extends DeviceBlockEntity implements ShaftConsumer, FoundryDevice, PanelHost {
    public static final double NOMINAL_RPM = 64, GEAR = 8, RATED_W = 30e3, RADIUS = 4;

    private final ShaftInput shaft = new ShaftInput();
    private double loadW;

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
    private WellheadBlockEntity wellhead() {
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
        return ProductionPanels.WELLHEAD;
    }

    @Override
    public void readPanel(int[] out) {
        WellheadBlockEntity w = wellhead();
        if (w != null) w.readPanel(out);
        else Arrays.fill(out, 0);
    }
}
