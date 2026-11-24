package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity;
import dev.denfry.promptcritical.mod.environment.DosimeterItem;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.environment.SelfEmittingSource;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.sim.radiation.SpentFuelCask;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Транспортно-упаковочный контейнер ОЯТ класса CASTOR V/19 ({@code spent_fuel_cask_real}, 02 §2.7.5): корзина на
 * {@value SpentFuelCask#ASSEMBLIES} ТВС (любые предметы с активностью). Открытый контейнер ({@code open}) загружают ПКМ
 * предметом — грузят его в бассейне выдержки, под водой: без крышек ТВС светят вверх без защиты, и источник стоит у
 * горловины. Закрыть (Shift, пустая рука) можно, только если загрузка в пределах IAEA SSR-6 и тепла 35 кВт
 * ({@link SpentFuelCask#transportable}); закрытый контейнер светит через стенку чугун — полиэтилен — чугун, над
 * рёбрами дрожит тёплый воздух ({@code lit}). ПКМ пустой рукой — показания. Закрытый контейнер забирает тележка
 * ({@link CaskCartBlockEntity}).
 */
public final class SpentFuelCaskBlockEntity extends DeviceBlockEntity implements SelfEmittingSource, FoundryDevice, RealScaleBlockEntity, ShieldedContents {
    /** Остаточное тепло, с которого над рёбрами виден тёплый воздух, Вт [BALANCE: порог показа]. */
    static final double VISIBLE_HEAT_W = 1_000;

    private final ItemStackHandler slots = ShieldedLoad.slots(SpentFuelCask.ASSEMBLIES, this::setChanged);

    public SpentFuelCaskBlockEntity(BlockPos pos, BlockState state) {
        super(RadiationBlockEntities.SPENT_FUEL_CASK.get(), pos, state);
    }

    public ItemStackHandler slots() {
        return slots;
    }

    public boolean open() {
        BlockState s = getBlockState();
        return !s.hasProperty(BlockStateProperties.OPEN) || s.getValue(BlockStateProperties.OPEN);
    }

    /** Середина корзины на оси контейнера (8, 48, 8) px. */
    Vec3 middle() {
        return BoilerGeometry.world(worldPosition, wholeModelFacing(), 8, 48, 8);
    }

    /** Горловина: открытый контейнер светит отсюда. */
    Vec3 mouth() {
        return BoilerGeometry.world(worldPosition, wholeModelFacing(), 8, 92, 8);
    }

    @Override
    public void tickDevice() {
        if (level == null) return;
        if (level.isClientSide) {
            clientTick();
            return;
        }
        if (!(level instanceof ServerLevel s) || level.getGameTime() % 20 != 0) return;
        ShieldedLoad l = ShieldedLoad.of(slots, s.getGameTime());
        if (!l.source().empty()) {
            if (open()) {
                RadiationService.removeSource(s, middle());
                RadiationService.source(s, mouth(), l.source());
            } else {
                RadiationService.removeSource(s, mouth());
                RadiationService.source(s, middle(), SpentFuelCask.outside(l.source()));
            }
        }
        boolean warm = l.heatW() > VISIBLE_HEAT_W;
        BlockState bs = getBlockState();
        if (bs.hasProperty(BlockStateProperties.LIT) && bs.getValue(BlockStateProperties.LIT) != warm)
            level.setBlock(worldPosition, bs.setValue(BlockStateProperties.LIT, warm), Block.UPDATE_CLIENTS);
    }

    private void clientTick() {
        BlockState bs = getBlockState();
        if (!bs.hasProperty(BlockStateProperties.LIT) || !bs.getValue(BlockStateProperties.LIT)) return;
        MachineFx fx = MachineFx.of(this);
        if (fx != null && fx.chance(2)) fx.wisp(fx.at(8 + (fx.random().nextDouble() - 0.5) * 36, 88, 8 + (fx.random().nextDouble() - 0.5) * 36), 0.3);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return open() && slots.isItemValid(0, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!slots.isItemValid(0, stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!open()) {
            player.displayClientMessage(RadProtText.msg("cask.sealed"), true);
            return ItemInteractionResult.FAIL;
        }
        int moved = ShieldedLoad.insert(slots, stack);
        if (moved == 0) return ItemInteractionResult.FAIL;
        if (!player.isCreative()) stack.shrink(moved);
        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 0.6f);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ShieldedLoad l = ShieldedLoad.of(slots, level.getGameTime());
        if (player.isShiftKeyDown()) {
            if (open()) {
                Component why = refusal(l);
                if (why != null) {
                    player.displayClientMessage(RadProtText.msg("cask.refused", why).withStyle(ChatFormatting.RED), false);
                    return InteractionResult.SUCCESS;
                }
                setOpen(false);
                player.displayClientMessage(RadProtText.msg("cask.closed_now").withStyle(ChatFormatting.GREEN), true);
            } else {
                setOpen(true);
                player.displayClientMessage(RadProtText.msg("cask.opened_now").withStyle(ChatFormatting.GOLD), true);
            }
            level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 0.6f);
            return InteractionResult.SUCCESS;
        }
        player.displayClientMessage(RadProtText.msg("cask.status", RadProtText.msg(open() ? "cask.open" : "cask.closed"), l.items(),
                SpentFuelCask.ASSEMBLIES, RadProtText.num(l.heatW()), DosimeterItem.rate(SpentFuelCask.surfaceSvH(l.source())),
                DosimeterItem.rate(SpentFuelCask.at2mSvH(l.source()))), false);
        return InteractionResult.SUCCESS;
    }

    /** Почему крышку закрыть нельзя; {@code null} — можно. */
    @Nullable
    static Component refusal(ShieldedLoad l) {
        if (l.heatW() > SpentFuelCask.HEAT_LIMIT_W) return RadProtText.msg("cask.reason.heat", RadProtText.num(l.heatW() / 1000));
        if (!SpentFuelCask.transportable(l.source(), l.heatW()))
            return RadProtText.msg("cask.reason.dose", DosimeterItem.rate(SpentFuelCask.surfaceSvH(l.source())));
        return null;
    }

    private void setOpen(boolean open) {
        BlockState bs = getBlockState();
        if (bs.hasProperty(BlockStateProperties.OPEN)) level.setBlock(worldPosition, bs.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
    }

    /** Тележка забирает контейнер: содержимое уходит к ней, а блок снимается без выпадения. */
    void handOver(ItemStackHandler to) {
        for (int i = 0; i < slots.getSlots(); i++) {
            ItemStack st = slots.extractItem(i, 64, false);
            if (!st.isEmpty()) ShieldedLoad.insert(to, st);
        }
        if (level instanceof ServerLevel s) {
            RadiationService.removeSource(s, middle());
            RadiationService.removeSource(s, mouth());
        }
        level.destroyBlock(worldPosition, false);
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return open() ? slots : null;
    }

    @Override
    public ItemStackHandler contents() {
        return slots;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("slots", slots.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("slots")) slots.deserializeNBT(registries, tag.getCompound("slots"));
    }
}
