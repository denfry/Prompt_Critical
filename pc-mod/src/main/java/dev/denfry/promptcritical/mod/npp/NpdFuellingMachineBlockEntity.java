package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.grid.MachinePower;
import dev.denfry.promptcritical.mod.heavywater.HeavyWaterFluids;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.sim.heavywater.HeavyWaterGrade;
import dev.denfry.promptcritical.sim.npp.NpdFuellingMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Перегрузочная машина NPD у торца реактора (7×7×2, art/blockbench/core/candu_npd/candu_npd.author.js). Автомат цикла —
 * у реактора ({@link NpdReactorBlockEntity}, {@link NpdFuellingMachine}): он работает, только если по машине стоит у
 * каждого торца. Сама машина даёт ему питание приводов (щит или розетка, {@value #DRIVE_W} Вт в работе) и D₂O своей
 * головки: бак на {@value #HEAD_MB} мБ с западного патрубка, в работе подпитка уплотнений берёт литр в минуту. Лоток с
 * севера — тот же склад пучков реактора. Правая кнопка открывает щит реактора.
 */
public final class NpdFuellingMachineBlockEntity extends DeviceBlockEntity implements FoundryDevice,
        dev.denfry.promptcritical.mod.client.whole.WholeModelHost {
    public static final int HEAD_MB = 4000;
    public static final double DRIVE_W = 60e3, IDLE_W = 3e3;

    private final FluidTank head = new FluidTank(HEAD_MB, NpdFuellingMachineBlockEntity::heavyWater) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    @Nullable
    private BlockPos reactor;
    private double lastSupply;
    private int ticks;
    /** Для клиента — копия положения машины от реактора. */
    private float viewX = (float) NpdFuellingMachine.HOME_X, viewY = (float) NpdFuellingMachine.HOME_Y, viewSnout;
    private int viewPhase, viewSign = 1, viewDy, viewPushed;

    public NpdFuellingMachineBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.NPD_FUELLING_MACHINE.get(), pos, state);
    }

    private static boolean heavyWater(FluidStack s) {
        for (HeavyWaterGrade g : HeavyWaterGrade.values())
            if (g != HeavyWaterGrade.LIGHT && s.getFluid() == HeavyWaterFluids.fluid(g)) return true;
        return false;
    }

    /** Реактор, которому служит машина (ставит сам реактор при обходе). */
    void bind(BlockPos pos) {
        if (!pos.equals(reactor)) {
            reactor = pos;
            setChanged();
        }
    }

    /** Доля питания приводов; {@code busy} — машины в цикле. */
    double supply(boolean busy) {
        lastSupply = MachinePower.supply(level, worldPosition, busy ? DRIVE_W : IDLE_W);
        if (busy && ticks % 1200 == 0 && head.getFluidAmount() > 0) head.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        return lastSupply;
    }

    /** Заполнение бака D₂O головки, 0…1. */
    double headD2oFraction() {
        return (double) head.getFluidAmount() / HEAD_MB;
    }

    /** Копия положения машины от реактора для клиента. */
    void mirror(NpdReactorBlockEntity r) {
        viewX = r.viewX();
        viewY = r.viewY();
        viewSnout = r.viewSnout();
        viewPhase = r.viewPhase();
        viewPushed = r.viewPushed();
        // машина у южного торца смотрит навстречу реактору: поперёк каландра её ось x обратная
        viewSign = facing() == r.wholeModelFacing() ? 1 : -1;
        viewDy = worldPosition.getY() - r.getBlockPos().getY();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    public float viewX() {
        return viewX;
    }

    public float viewY() {
        return viewY;
    }

    public float viewSnout() {
        return viewSnout;
    }

    /** +1 — машина смотрит туда же, куда реактор (северный торец), −1 — у южного торца. */
    public int viewSign() {
        return viewSign;
    }

    /** Пол машины относительно пола реактора, блоки (машину ставят в приямок, чтобы хобот доставал нижние ряды). */
    public int viewDy() {
        return viewDy;
    }

    public int viewPushed() {
        return viewPushed;
    }

    public int viewPhase() {
        return viewPhase;
    }

    public boolean running() {
        return viewPhase != NpdFuellingMachine.Phase.IDLE.ordinal();
    }

    @Nullable
    private NpdReactorBlockEntity reactorBe() {
        return reactor != null && level != null && level.getBlockEntity(reactor) instanceof NpdReactorBlockEntity r ? r : null;
    }

    @Override
    public void tickDevice() {
        ticks++;
        if (level == null || level.isClientSide || ticks % 20 != 0) return;
        BlockState s = getBlockState();
        boolean run = running();
        if (s.hasProperty(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING)
                && s.getValue(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING) != run)
            level.setBlock(worldPosition, s.setValue(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING, run), 3);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return head;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        NpdReactorBlockEntity r = reactorBe();
        return r != null ? r.fuelHandler() : null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        NpdReactorBlockEntity r = reactorBe();
        return r != null && r.accepts(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        NpdReactorBlockEntity r = reactorBe();
        return r != null ? r.useItem(player, hand, stack) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(Player player) {
        NpdReactorBlockEntity r = reactorBe();
        if (r == null) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) PanelHost.open(sp, r);
        return InteractionResult.SUCCESS;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putFloat("view_x", viewX);
        tag.putFloat("view_y", viewY);
        tag.putFloat("view_snout", viewSnout);
        tag.putInt("view_phase", viewPhase);
        tag.putInt("view_sign", viewSign);
        tag.putInt("view_dy", viewDy);
        tag.putInt("view_pushed", viewPushed);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("head", head.writeToNBT(registries, new CompoundTag()));
        if (reactor != null) tag.putLong("reactor", reactor.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        head.readFromNBT(registries, tag.getCompound("head"));
        reactor = tag.contains("reactor") ? BlockPos.of(tag.getLong("reactor")) : null;
        if (tag.contains("view_x")) {
            viewX = tag.getFloat("view_x");
            viewY = tag.getFloat("view_y");
            viewSnout = tag.getFloat("view_snout");
            viewPhase = tag.getInt("view_phase");
            viewSign = tag.contains("view_sign") ? tag.getInt("view_sign") : 1;
            viewDy = tag.getInt("view_dy");
            viewPushed = tag.getInt("view_pushed");
        }
    }

    private Direction facing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock b ? b.facing(s) : Direction.NORTH;
    }

    @Override
    public String wholeModelId() {
        return dev.denfry.promptcritical.mod.client.whole.WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        return facing();
    }

    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(facing()).above(2);
    }
}
