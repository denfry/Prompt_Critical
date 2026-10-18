package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.CastingGeometry;
import dev.denfry.promptcritical.sim.metallurgy.CastingSpec;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import dev.denfry.promptcritical.sim.metallurgy.FoundryUnit;
import dev.denfry.promptcritical.sim.metallurgy.SandMoldUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Песчаная форма {@code sand_mold} (08 §8.9). Заформованная (с формовочной машины) несёт компонент
 * {@code sand_mold} — модель и влажность; в мире её двойник {@link SandMoldUnit}. Стадия блока следует за
 * двойником: {@code empty} → {@code poured} (жидкий металл) → {@code cooling}. Под дождём форма мокнет.
 * Разбитая вручную остывшая форма отдаёт отливку; выбивная решётка — ещё и формовочную смесь.
 */
public final class SandMoldBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    private static final int SYNC_PERIOD_TICKS = 10;

    @Nullable
    private FoundryComponents.Mold pending;
    private boolean knockedOut;

    public SandMoldBlockEntity(BlockPos pos, BlockState state) {
        super(FoundryBlockEntities.SAND_MOLD.get(), pos, state);
    }

    @Nullable
    SandMoldUnit unit() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null || simId().isNone()) return null;
        if (!(b.world().subsystem(FoundrySubsystem.class).unit(b.world().topology(), simId()) instanceof SandMoldUnit u)) return null;
        if (pending != null && u.stage() == SandMoldUnit.Stage.EMPTY) {
            u.form(pending.kind(), pending.riser(), pending.moisture());
            pending = null;
        }
        return u;
    }

    /** Заформовать (как форма с машины). */
    public void setMold(FoundryComponents.Mold mold) {
        pending = mold;
        unit();
    }

    /** Залить форму в {@code pos} из печи; {@code true} — залита. */
    static boolean pourInto(Level level, BlockPos pos, FoundrySubsystem shop, FoundryUnit.MeltSource from) {
        if (!(level.getBlockEntity(pos) instanceof SandMoldBlockEntity m)) return false;
        SandMoldUnit u = m.unit();
        if (u == null || !u.acceptsPour() || !shop.pour(from, u)) return false;
        m.syncStage(u);
        return true;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel)) {
            clientTick();
            return;
        }
        SandMoldUnit u = unit();
        if (u == null) return;
        if (u.stage() == SandMoldUnit.Stage.EMPTY && level.isRainingAt(worldPosition.above())) u.wet(0.05);
        if (level.getGameTime() % SYNC_PERIOD_TICKS == 0) syncStage(u);
    }

    /** Залитая форма дымит и вспыхивает над литником, остывающая — парит. */
    private void clientTick() {
        MachineFx fx = MachineFx.of(level, worldPosition, Direction.NORTH);
        if (fx == null) return;
        switch (getBlockState().getValue(PcProperties.MOLD_STAGE)) {
            case POURED -> {
                if (fx.chance(4)) fx.wisp(fx.at(10.5, 13, 10.5), 0.2);
                if (fx.chance(1.5)) fx.flame(fx.at(4, 12.8, 11), 0.1);
            }
            case COOLING -> {
                if (fx.chance(1)) fx.wisp(fx.at(8, 13, 8), 0.5);
            }
            default -> {
            }
        }
    }

    void syncStage(SandMoldUnit u) {
        PcProperties.MoldStage target = switch (u.stage()) {
            case EMPTY -> PcProperties.MoldStage.EMPTY;
            case LIQUID -> PcProperties.MoldStage.POURED;
            case SOLID -> PcProperties.MoldStage.COOLING;
        };
        BlockState s = getBlockState();
        if (s.getValue(PcProperties.MOLD_STAGE) != target)
            level.setBlock(worldPosition, s.setValue(PcProperties.MOLD_STAGE, target), Block.UPDATE_ALL);
    }

    /** Остывшая отливка для выбивки; {@code null} — не готова. */
    @Nullable
    CastingSpec readyCasting() {
        SandMoldUnit u = unit();
        return u != null && u.readyForShakeout() ? u.casting() : null;
    }

    /** Формовочной смеси в форме, предметов. */
    int sandItems() {
        SandMoldUnit u = unit();
        return u == null || u.kind() == null ? 1 : CastingGeometry.of(u.kind()).sandItems();
    }

    /** Форму разобрала решётка: отливку отдаёт она. */
    void markKnockedOut() {
        knockedOut = true;
    }

    /** Блок разбит: остывшая или горячая твёрдая отливка выпадает, жидкий металл пропадает. */
    void dropCastingOnBreak() {
        if (knockedOut) return;
        SandMoldUnit u = unit();
        CastingSpec c = u == null ? null : u.casting();
        if (c != null) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), casting(c));
    }

    static ItemStack casting(CastingSpec c) {
        ItemStack s = new ItemStack(PcItems.CASTING.get());
        s.set(FoundryComponents.CASTING_SPEC.get(), c);
        return s;
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        FoundryComponents.Mold m = input.get(FoundryComponents.SAND_MOLD.get());
        if (m != null) pending = m;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        SandMoldUnit u = unit();
        if (u != null && u.acceptsPour()) builder.set(FoundryComponents.SAND_MOLD.get(),
                new FoundryComponents.Mold(u.kind(), u.riser(), (float) u.moisture()));
        else if (pending != null) builder.set(FoundryComponents.SAND_MOLD.get(), pending);
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
        SandMoldUnit u = unit();
        if (u == null) return InteractionResult.SUCCESS;
        if (u.kind() == null) {
            player.displayClientMessage(FoundryText.msg("mold_unformed"), true);
            return InteractionResult.SUCCESS;
        }
        var kind = FoundryText.kind(u.kind());
        var riser = FoundryText.riser(u.riser());
        player.displayClientMessage(switch (u.stage()) {
            case EMPTY -> FoundryText.msg("mold_empty", kind, riser, FoundryText.num(u.moisture() * 100));
            case LIQUID -> FoundryText.msg("mold_liquid", kind, riser,
                    FoundryText.num(Math.max(0, u.solidificationS() - u.elapsedS())));
            case SOLID -> FoundryText.msg(u.readyForShakeout() ? "mold_ready" : "mold_cooling", kind, riser);
        }, true);
        return InteractionResult.SUCCESS;
    }
}
