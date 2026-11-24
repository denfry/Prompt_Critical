package dev.denfry.promptcritical.mod.greenhouse;

import com.mojang.logging.LogUtils;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.HeatedWater;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.sim.thermal.FishCage;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Садок {@code fish_cage} на воде водоёма со сбросным теплом (17 §17.3.6): рыба растёт по температуре воды под
 * садком ({@link HeatedWater}) и корму; выше 36 °C гибнет вся — событие {@code FISH_KILL} в журнал сервера и игрокам
 * рядом. Корм — пшеница, семена пшеницы или ламинария, 1 шт. = 1 кг [оценка]; улов — сырая треска, 1 шт. на кг. Садок
 * ставится зарыблённым (в рецепте — ведро трески); после замора — снова ведром трески.
 *
 * <p>Активность воды (04b §4b.8.5) рыба пока не накапливает: у водоёмов мира нет поля активности воды.
 */
public final class FishCageBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    private static final Logger LOG = LogUtils.getLogger();
    public static final int FEED_MAX_KG = 64;

    private final FishCage cage = new FishCage(FishCage.STOCK_KG);
    private double feedKg, waterC = Double.NaN;
    private int ticks;

    public FishCageBlockEntity(BlockPos pos, BlockState state) {
        super(GreenhouseBlockEntities.FISH_CAGE.get(), pos, state);
    }

    public FishCage cage() {
        return cage;
    }

    public double feedKg() {
        return feedKg;
    }

    /** Температура воды под садком, °C; NaN — садок не на воде. */
    public double waterC() {
        return waterC;
    }

    /** Вода под садком: блок ниже. */
    public boolean onWater() {
        return level != null && level.getFluidState(worldPosition.below()).is(FluidTags.WATER);
    }

    static boolean feed(ItemStack s) {
        return s.is(Items.WHEAT) || s.is(Items.WHEAT_SEEDS) || s.is(Items.KELP);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || ++ticks % 20 != 0) return;
        if (!onWater()) {
            waterC = Double.NaN;
            return;
        }
        waterC = HeatedWater.waterTempC(sl, worldPosition.below());
        boolean alive = cage.biomassKg() > 0;
        feedKg -= cage.step(1.0, waterC, feedKg);
        if (alive && cage.killed()) fishKill(sl);
        if (ticks % 200 == 0) setChanged();
    }

    private void fishKill(ServerLevel sl) {
        LOG.warn("[{}] FISH_KILL at {}: water {} C", sl.dimension().location(), worldPosition, String.format(Locale.ROOT, "%.1f", waterC));
        for (ServerPlayer p : sl.players())
            if (p.blockPosition().closerThan(worldPosition, 64))
                p.displayClientMessage(GreenhouseText.msg("fish_kill", String.format(Locale.ROOT, "%.1f", waterC)).withStyle(ChatFormatting.RED), false);
        setChanged();
    }

    @Override
    public InteractionResult use(Player player) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        if (!onWater()) {
            player.displayClientMessage(GreenhouseText.msg("cage_dry").withStyle(ChatFormatting.GOLD), false);
            return InteractionResult.SUCCESS;
        }
        double t = HeatedWater.waterTempC(sl, worldPosition.below());
        int fish = (int) Math.floor(cage.harvest());
        if (fish > 0) Block.popResource(sl, worldPosition.above(), new ItemStack(Items.COD, Math.min(fish, 64)));
        player.displayClientMessage(GreenhouseText.msg("cage_status", String.format(Locale.ROOT, "%.1f", t),
                String.format(Locale.ROOT, "%.1f", HeatedWater.riseK(sl, worldPosition.below())), Math.round(cage.biomassKg()),
                Math.round(feedKg), fish), false);
        if (cage.killed()) player.displayClientMessage(GreenhouseText.msg("cage_dead").withStyle(ChatFormatting.RED), false);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return feed(stack) || stack.is(Items.COD_BUCKET);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(Items.COD_BUCKET)) {
            if (!cage.killed() && cage.biomassKg() > 0) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            cage.restock(FishCage.STOCK_KG);
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            setChanged();
            return ItemInteractionResult.SUCCESS;
        }
        if (!feed(stack) || feedKg >= FEED_MAX_KG) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        int n = (int) Math.min(stack.getCount(), FEED_MAX_KG - feedKg);
        feedKg += n;
        if (!player.getAbilities().instabuild) stack.shrink(n);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    /** Корм воронкой: только вставка. */
    @Override
    public IItemHandler items(@Nullable Direction side) {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return 1;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!feed(stack)) return stack;
                int n = (int) Math.min(stack.getCount(), Math.max(0, FEED_MAX_KG - feedKg));
                if (n <= 0) return stack;
                if (!simulate) {
                    feedKg += n;
                    setChanged();
                }
                return stack.copyWithCount(stack.getCount() - n);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return FEED_MAX_KG;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return feed(stack);
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("fish_kg", cage.biomassKg());
        tag.putBoolean("killed", cage.killed());
        tag.putDouble("feed_kg", feedKg);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fish_kg")) cage.restore(tag.getDouble("fish_kg"), tag.getBoolean("killed"));
        feedKg = tag.getDouble("feed_kg");
    }
}
