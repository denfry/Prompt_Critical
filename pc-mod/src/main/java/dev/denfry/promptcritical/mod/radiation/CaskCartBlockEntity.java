package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.environment.SelfEmittingSource;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.radiation.SpentFuelCask;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Самоходная рельсовая тележка контейнера ОЯТ ({@code cask_transport_cart_real}, грузоподъёмность 160 т, колея 1 520 мм):
 * везёт контейнер лёжа на ложементе ({@code occupied} — вариант {@code _loaded}). Работа — ПКМ пустой рукой:
 * <ul>
 *   <li>пустая — поднять закрытый контейнер в пределах {@value #REACH} блоков (кран и стропы — за кадром);</li>
 *   <li>с полным контейнером — у пустого модуля сухого хранения в пределах {@value #REACH} блоков задвинуть пенал
 *   в модуль (контейнер остаётся на тележке пустым);</li>
 *   <li>с пустым контейнером — вытянуть пенал из модуля обратно; Shift — снять пустой контейнер (выдаётся предметом).</li>
 * </ul>
 * Пока контейнер с ТВС на тележке, он светит через свою стенку с оси ложемента. Перемещают тележку контрапции Create.
 */
public final class CaskCartBlockEntity extends DeviceBlockEntity implements SelfEmittingSource, FoundryDevice, RealScaleBlockEntity, ShieldedContents {
    /** Дальность работы с контейнером и модулем, блоков: ширина путей у здания хранилища [BALANCE]. */
    public static final int REACH = 8;

    private final ItemStackHandler cask = ShieldedLoad.slots(SpentFuelCask.ASSEMBLIES, this::setChanged);

    public CaskCartBlockEntity(BlockPos pos, BlockState state) {
        super(RadiationBlockEntities.CASK_CART.get(), pos, state);
    }

    public ItemStackHandler cask() {
        return cask;
    }

    public boolean loaded() {
        BlockState s = getBlockState();
        return s.hasProperty(BlockStateProperties.OCCUPIED) && s.getValue(BlockStateProperties.OCCUPIED);
    }

    /** Ось лежащего контейнера (8, 36.5, 8) px. */
    Vec3 axis() {
        return BoilerGeometry.world(worldPosition, wholeModelFacing(), 8, 36.5, 8);
    }

    @Override
    public void tickDevice() {
        if (level == null) return;
        if (level.isClientSide) {
            clientTick();
            return;
        }
        if (!(level instanceof ServerLevel s) || level.getGameTime() % 20 != 0) return;
        ShieldedLoad l = ShieldedLoad.of(cask, s.getGameTime());
        if (!l.source().empty()) RadiationService.source(s, axis(), SpentFuelCask.outside(l.source()));
    }

    /** Тёплый воздух над рёбрами лежащего контейнера, пока в нём ТВС. */
    private void clientTick() {
        if (!loaded()) return;
        MachineFx fx = MachineFx.of(this);
        if (fx != null && fx.chance(0.5)) fx.wisp(fx.at(8 + (fx.random().nextDouble() - 0.5) * 80, 58, 8), 0.3);
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
        if (level.isClientSide) return InteractionResult.SUCCESS;
        int n = ShieldedLoad.count(cask);
        if (!loaded()) {
            SpentFuelCaskBlockEntity c = nearest(SpentFuelCaskBlockEntity.class);
            if (c == null) {
                player.displayClientMessage(RadProtText.msg("cart.no_cask", REACH), true);
                return InteractionResult.SUCCESS;
            }
            c.handOver(cask);
            setLoaded(true);
            sound();
            player.displayClientMessage(RadProtText.msg("cart.picked").withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.SUCCESS;
        }
        if (n > 0) {
            DryStorageModuleBlockEntity m = nearest(DryStorageModuleBlockEntity.class, true);
            if (m == null) {
                player.displayClientMessage(RadProtText.msg("cart.no_module", REACH), true);
                status(player);
                return InteractionResult.SUCCESS;
            }
            m.receive(cask);
            sound();
            player.displayClientMessage(RadProtText.msg("cart.pushed").withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            setLoaded(false);
            ItemStack item = new ItemStack(PcBlocks.SPENT_FUEL_CASK_REAL.get());
            if (!player.addItem(item)) player.drop(item, false);
            sound();
            return InteractionResult.SUCCESS;
        }
        DryStorageModuleBlockEntity m = nearest(DryStorageModuleBlockEntity.class, false);
        if (m != null && m.release(cask) > 0) {
            sound();
            player.displayClientMessage(RadProtText.msg("cart.retrieved").withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.SUCCESS;
        }
        status(player);
        return InteractionResult.SUCCESS;
    }

    private void status(Player player) {
        ShieldedLoad l = ShieldedLoad.of(cask, level.getGameTime());
        player.displayClientMessage(RadProtText.msg("cart.status", loaded()
                ? RadProtText.msg("cart.loaded", l.items(), RadProtText.num(l.heatW())) : RadProtText.msg("cart.empty")), false);
    }

    private void sound() {
        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.5f);
        level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6f, 0.5f);
    }

    @Nullable
    private SpentFuelCaskBlockEntity nearest(Class<SpentFuelCaskBlockEntity> type) {
        for (BlockPos p : BlockPos.withinManhattan(worldPosition, REACH, REACH, REACH))
            if (level.getBlockEntity(p) instanceof SpentFuelCaskBlockEntity c && !c.open()) return c;
        return null;
    }

    /** Ближайший модуль: пустой ({@code wantEmpty}) или с пеналом. */
    @Nullable
    private DryStorageModuleBlockEntity nearest(Class<DryStorageModuleBlockEntity> type, boolean wantEmpty) {
        for (BlockPos p : BlockPos.withinManhattan(worldPosition, REACH, REACH, REACH)) {
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof DryStorageModuleBlockEntity m && m.empty() == wantEmpty) return m;
        }
        return null;
    }

    private void setLoaded(boolean on) {
        BlockState bs = getBlockState();
        if (bs.hasProperty(BlockStateProperties.OCCUPIED)) level.setBlock(worldPosition, bs.setValue(BlockStateProperties.OCCUPIED, on), Block.UPDATE_ALL);
        if (!on && level instanceof ServerLevel s) RadiationService.removeSource(s, axis());
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public ItemStack extraDrop() {
        return loaded() ? new ItemStack(PcBlocks.SPENT_FUEL_CASK_REAL.get()) : ItemStack.EMPTY;
    }

    @Override
    public ItemStackHandler contents() {
        return cask;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("cask", cask.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("cask")) cask.deserializeNBT(registries, tag.getCompound("cask"));
    }
}
