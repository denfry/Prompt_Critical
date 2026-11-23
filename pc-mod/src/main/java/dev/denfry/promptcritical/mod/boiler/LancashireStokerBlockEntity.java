package dev.denfry.promptcritical.mod.boiler;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Механические забрасыватели ланкаширского котла (docs/research/lancashire_stoker-dimensions.md): два толкателя с
 * бункерами на общей раме перед лобовой стенкой, вал Create через клетку {@code kinetic_port}. Подача та же, что у
 * цепной решётки ({@link StokerFeed}, номинал котла 07 §7.9.4); котёл — клетка за спиной контроллера.
 */
public final class LancashireStokerBlockEntity extends DeviceBlockEntity implements FoundryDevice, ShaftConsumer, PanelHost, WholeModelHost {
    /** Привод толкателей, Вт, и номинальные обороты линии вала. */
    public static final double RATED_W = 1_500, NOMINAL_RPM = 32;

    private final ShaftInput shaft = new ShaftInput();
    private final StokerFeed feed = new StokerFeed(GrateStokerBlockEntity.NOMINAL_KG_S, NOMINAL_RPM, "lancashire_stoker", this::setChanged);
    private double rpmNow;

    public LancashireStokerBlockEntity(BlockPos pos, BlockState state) {
        super(BoilerBlockEntities.LANCASHIRE_STOKER.get(), pos, state);
    }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock b ? b.facing(s) : Direction.NORTH;
    }

    /** Котёл, к лобовой стенке которого примыкает рама. */
    @Nullable
    public FireTubeBoilerBlockEntity boiler() {
        return level == null ? null : FireTubeBoilerBlockEntity.at(level, worldPosition.relative(facing().getOpposite()));
    }

    /** Обороты линии вала (клиент — по состоянию {@code running}). */
    public double rpm() {
        return rpmNow;
    }

    @Override
    public void tickDevice() {
        if (level == null) return;
        if (!(level instanceof ServerLevel s)) {
            clientTick();
            return;
        }
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        rpmNow = shaft.rpm(b.world().clock().tick());
        feed.tick(b, rpmNow, boiler());
        boolean running = feed.feedKgS() > 0;
        BlockState st = getBlockState();
        if (st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING) != running)
            level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, running), Block.UPDATE_ALL);
    }

    private void clientTick() {
        BlockState s = getBlockState();
        if (!s.hasProperty(PcProperties.RUNNING) || !s.getValue(PcProperties.RUNNING)) return;
        MachineFx fx = MachineFx.of(level, worldPosition, facing());
        var r = fx.random();
        // угольная пыль над бункерами и вспышки в смотровых окнах коробок
        if (fx.chance(8)) fx.wisp(fx.at((r.nextBoolean() ? -0.6 : 16.6) + r.nextDouble() * 6 - 3, 40.5, 6 + r.nextDouble() * 6), 0.2);
        if (fx.chance(10)) fx.flame(fx.at((r.nextBoolean() ? -0.6 : 16.6) + r.nextDouble() * 4 - 2, 26, 2.2), 0.02);
    }

    // --- вал Create ---

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        shaft.sample(rpm, healthy, tick);
    }

    @Override
    public double shaftPowerW() {
        return RATED_W * rpmNow / NOMINAL_RPM;
    }

    @Override
    public double ratedShaftW() {
        return RATED_W;
    }

    @Override
    public double nominalShaftRpm() {
        return NOMINAL_RPM;
    }

    // --- руки, воронки, щит ---

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return feed.hopper();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return BoilerFuels.key(stack) != null;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, feed.insert(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        feed.tell(player, boiler() != null);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return BoilerPanels.STOKER;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return feed.hopper();
    }

    @Override
    public void readPanel(int[] out) {
        feed.readPanel(out, rpmNow, boiler());
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

    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(facing()).above();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        feed.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        feed.load(tag, registries);
    }
}
