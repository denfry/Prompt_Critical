package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.metallurgy.CupolaUnit;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Вагранка реального масштаба {@code cupola_furnace_real} (08 §8.9, docs/research/cupola-dimensions.md): 24-дюймовая
 * вагранка Кирка, 3 × 8 × 4 блока. Тот же {@link CupolaUnit}, что у старой вагранки (1,2 т/ч): тип устройства в
 * симуляции — {@link FoundrySubsystem#CUPOLA}. Вал Create входит в клетку вентилятора (1, 1, 1) с востока
 * ({@link CupolaRealBlock}), колошу подают на площадку, чугун течёт по жёлобу на север в форму перед контроллером или
 * на блок ниже. Сигнал красного камня на контроллере держит лётку закрытой.
 */
public final class CupolaRealBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost, ShaftConsumer, WholeModelHost {
    /** Точки модели, px (фасад на север, от угла клетки контроллера): носок жёлоба, глазки фурм, колпак искрогасителя. */
    static final double[] SPOUT = {8, 14.8, -1}, SLAG = {-17, 18, 24}, CAP = {8, 126, 24}, CHARGING = {8, 62, 31};

    private final MachineInventory inv = CupolaLogic.inventory(this::setChanged);

    public CupolaRealBlockEntity(BlockPos pos, BlockState state) {
        super(FoundryBlockEntities.CUPOLA_REAL.get(), pos, state);
    }

    /** В симуляции — та же вагранка, что и старая серия. */
    @Override
    public String deviceType() {
        return FoundrySubsystem.CUPOLA;
    }

    /** Юнит симуляции вагранки; {@code null} — на клиенте или пока устройство не привязано. */
    @Nullable
    public CupolaUnit unit() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null || simId().isNone()) return null;
        return b.world().subsystem(FoundrySubsystem.class).unit(b.world().topology(), simId()) instanceof CupolaUnit u ? u : null;
    }

    private Direction facing() {
        return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    private boolean held() {
        return level.hasNeighborSignal(worldPosition);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) {
            clientTick();
            return;
        }
        SimLevelBinding b = SimBindings.get(s);
        CupolaUnit u = unit();
        if (b == null || u == null) return;
        CupolaLogic.charge(inv, u);
        if (level.getGameTime() % CupolaBlockEntity.POUR_PERIOD_TICKS == 0 && !held())
            CupolaLogic.pour(level, worldPosition.relative(facing()), b.world().subsystem(FoundrySubsystem.class), u);
        BlockState st = getBlockState();
        if (st.getValue(BlockStateProperties.LIT) != u.lit())
            level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, u.lit()), Block.UPDATE_ALL);
    }

    /** Искры и дым над искрогасителем, пламя из колошника, струя чугуна с жёлоба, шлак с летки. */
    private void clientTick() {
        MachineFx fx = MachineFx.of(level, worldPosition, facing());
        if (fx == null || !getBlockState().getValue(BlockStateProperties.LIT)) return;
        if (fx.chance(3)) fx.sparks(fx.at(CAP[0], CAP[1], CAP[2]), 1);
        if (fx.chance(4)) fx.smoke(fx.at(CAP[0], CAP[1], CAP[2]), false, 0.6);
        if (fx.chance(2)) fx.flame(fx.at(CHARGING[0], CHARGING[1], CHARGING[2]), 0.25);
        if (fx.chance(1.5)) fx.drip(fx.at(SPOUT[0], SPOUT[1], SPOUT[2]));
        if (fx.chance(0.4)) fx.drip(fx.at(SLAG[0], SLAG[1], SLAG[2]));
    }

    // --- вал воздуходувки через клетку kinetic_port ---

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        CupolaUnit u = unit();
        if (u != null) u.shaft.sample(rpm, healthy, tick);
    }

    @Override
    public double shaftPowerW() {
        CupolaUnit u = unit();
        return u == null ? 0 : u.powerW();
    }

    @Override
    public double ratedShaftW() {
        return CupolaUnit.BLOWER_NOMINAL_W;
    }

    @Override
    public double nominalShaftRpm() {
        return CupolaUnit.NOMINAL_RPM;
    }

    private int shaftRpm10() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        CupolaUnit u = unit();
        return b == null || u == null ? 0 : (int) Math.round(u.shaft.rpm(b.world().clock().tick()) * 10);
    }

    // --- склад, руки, щит ---

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return CupolaLogic.accepts(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        CupolaLogic.status(player, unit());
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return FoundryPanels.CUPOLA;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        CupolaLogic.readPanel(out, unit(), shaftRpm10(), held());
    }

    // --- цельная модель ---

    @Override
    @Nullable
    public String wholeModelId() {
        return WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        return facing();
    }

    /** Свет — с клетки перед жёлобом: сам контроллер внутри глухого объёма. */
    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(facing());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inv.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inventory")) inv.load(registries, tag.getCompound("inventory"));
    }
}
