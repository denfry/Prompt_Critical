package dev.denfry.promptcritical.mod.steelworks;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
 * Рольганг {@code roller_table} (08 §8.7.2): вал Create проходит насквозь вдоль рольганга (ряд рольгангов — одна
 * линия вала), ролики катят раскат вдоль оси со скоростью ω·r ({@value #ROLL_RADIUS_M} м: 64 об/мин — 1 блок/с);
 * знак скорости вала задаёт направление, реверс вала — реверс раската. У края раскат переходит к соседнему
 * приёмнику (рольганг, клеть, печь, прошивной стан) или ждёт. Холодильник ({@code kind=cooling_bed}) держит
 * раскат, пока тот не остынет ниже {@value #BED_RELEASE_K} К, и выдаёт его холодным. Shift + ПКМ — сменить вид.
 */
public final class RollerTableBlockEntity extends KineticBlockEntity implements FoundryDevice, PieceReceiver {
    public static final double ROLL_RADIUS_M = 0.15, RATED_W = 4_000, BED_RELEASE_K = 473.15;

    private ItemStack piece = ItemStack.EMPTY;
    /** Положение раската вдоль оси: 0 — край в отрицательную сторону, 1 — в положительную. */
    private double x = 0.5;
    private final IItemHandler handler = new Handler();

    public RollerTableBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.ROLLER_TABLE.get(), pos, state);
    }

    public ItemStack piece() {
        return piece;
    }

    public double position() {
        return x;
    }

    public Direction.Axis axis() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock b ? b.facing(s).getAxis() : Direction.Axis.Z;
    }

    public boolean coolingBed() {
        BlockState s = getBlockState();
        return s.hasProperty(PcProperties.ROLLER_KIND) && s.getValue(PcProperties.ROLLER_KIND) == PcProperties.RollerKind.COOLING_BED;
    }

    /** Скорость раската, блоков за тик (со знаком по оси). */
    public double blocksPerTick() {
        return getSpeed() * 2 * Math.PI / 60 * ROLL_RADIUS_M * 0.05;
    }

    @Override
    public void tickDevice() {
        tick();
    }

    @Override
    public void tick() {
        super.tick();
        if (piece.isEmpty()) return;
        if (level.isClientSide) {
            if (!held()) x = Math.max(0, Math.min(1, x + blocksPerTick()));
            return;
        }
        long now = level.getGameTime();
        if (coolingBed()) {
            SteelworksComponents.SteelStock st = Steelworks.stock(piece);
            if (st != null && st.hot()) {
                double t = st.tempK(now);
                if (t > BED_RELEASE_K) return;
                piece.set(SteelworksComponents.STEEL_STOCK.get(), st.withTemp(SteelworksComponents.AMBIENT_K, now));
                sendData();
            }
        }
        double v = blocksPerTick();
        if (v == 0 || isOverStressed()) return;
        x += v;
        if (x > 0 && x < 1) return;
        Direction dir = Direction.get(v > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, axis());
        x = v > 0 ? 1 : 0;
        if (Steelworks.handOff(level, worldPosition.relative(dir), piece, dir)) {
            piece = ItemStack.EMPTY;
            x = 0.5;
            setChanged();
            sendData();
        }
    }

    private boolean held() {
        if (!coolingBed()) return false;
        SteelworksComponents.SteelStock st = Steelworks.stock(piece);
        return st != null && st.hot();
    }

    @Override
    public boolean acceptPiece(ItemStack in, Direction travel) {
        if (!piece.isEmpty() || in.isEmpty() || travel.getAxis() != axis()) return false;
        piece = in.copy();
        x = travel.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : 1;
        setChanged();
        sendData();
        return true;
    }

    private void place(ItemStack in) {
        piece = in;
        x = 0.5;
        setChanged();
        sendData();
    }

    @Override
    public float calculateStressApplied() {
        float impact = (float) (RATED_W / (ServerConfig.W_PER_SU.get() * 64));
        lastStressApplied = impact;
        return impact;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return handler;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return piece.isEmpty() && (Steelworks.isStock(stack) || Steelworks.isPipe(stack));
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!piece.isEmpty()) return ItemInteractionResult.FAIL;
        place(stack.split(Steelworks.isPipe(stack) ? stack.getCount() : 1));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (player.isShiftKeyDown()) {
            BlockState s = getBlockState();
            PcProperties.RollerKind next = coolingBed() ? PcProperties.RollerKind.TABLE : PcProperties.RollerKind.COOLING_BED;
            level.setBlock(worldPosition, s.setValue(PcProperties.ROLLER_KIND, next), Block.UPDATE_ALL);
            return InteractionResult.SUCCESS;
        }
        if (piece.isEmpty()) {
            player.displayClientMessage(SteelworksText.msg(coolingBed() ? "bed_empty" : "table_empty"), true);
            return InteractionResult.SUCCESS;
        }
        player.displayClientMessage(SteelworksText.msg("table", piece.getHoverName(),
                SteelworksText.num(Steelworks.tempK(piece, level.getGameTime()) - 273.15)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!piece.isEmpty()) tag.put("piece", piece.save(registries));
        tag.putDouble("x", x);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        piece = tag.contains("piece") ? ItemStack.parseOptional(registries, tag.getCompound("piece")) : ItemStack.EMPTY;
        x = tag.contains("x") ? tag.getDouble("x") : 0.5;
    }

    /** Склад на один раскат: класть только на пустой рольганг, забирать можно всегда. */
    private final class Handler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return piece;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!accepts(stack)) return stack;
            int n = Steelworks.isPipe(stack) ? stack.getCount() : 1;
            if (!simulate) place(stack.copyWithCount(n));
            return stack.copyWithCount(stack.getCount() - n);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (piece.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            ItemStack out = piece.copyWithCount(Math.min(amount, piece.getCount()));
            if (!simulate) {
                piece.shrink(out.getCount());
                if (piece.isEmpty()) piece = ItemStack.EMPTY;
                setChanged();
                sendData();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return accepts(stack);
        }
    }
}
