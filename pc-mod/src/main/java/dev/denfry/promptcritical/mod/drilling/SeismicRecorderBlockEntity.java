package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.drilling.SeismicSurvey;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Сейсмостанция ({@link SeismicSurvey}): сейсмоприёмники ({@code geophone}) расставляют вокруг на расстоянии до
 * {@link #SPREAD_BLOCKS} блоков, источник — сейсмический заряд в шурфе (правый клик зарядом). Чем больше
 * приёмников в расстановке, тем шире площадь съёмки. Запись идёт {@link #RECORD_TICKS} тиков, затем станция
 * выдаёт сейсмический разрез ближайшей структуры.
 */
public final class SeismicRecorderBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    /** Сейсмостанция, Вт: прибор питается от розетки, щита или ИБП рядом, без питания не пишет запись. */
    public static final double POWER_W = 500;
    public static final int SPREAD_BLOCKS = 24, MIN_GEOPHONES = 4, RECORD_TICKS = 200;

    private int recording = -1;
    private int geophones;

    public SeismicRecorderBlockEntity(BlockPos pos, BlockState state) {
        super(DrillingBlockEntities.SEISMIC_RECORDER.get(), pos, state);
    }

    /** Сейсмоприёмники расстановки вокруг станции. */
    public int countGeophones() {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-SPREAD_BLOCKS, -4, -SPREAD_BLOCKS),
                worldPosition.offset(SPREAD_BLOCKS, 4, SPREAD_BLOCKS))) {
            if (level.getBlockState(p).is(PcBlocks.GEOPHONE.get())) n++;
        }
        return n;
    }

    public boolean recording() {
        return recording >= 0;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s) || recording < 0 || !dev.denfry.promptcritical.mod.grid.MachinePower.powered(level, worldPosition, POWER_W)) return;
        if (++recording < RECORD_TICKS) return;
        recording = -1;
        setLit(false);
        int aperture = SeismicSurvey.apertureBlocks(geophones);
        SeismicSurvey.Reading r = SeismicSurvey.survey(PetroleumFields.get(s.getSeed()), s.getSeed(), worldPosition.getX(),
                worldPosition.getZ(), aperture);
        Player near = s.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, 16, false);
        if (r == null) {
            if (near != null) near.displayClientMessage(DrillingText.msg("no_structure", aperture), true);
            return;
        }
        ItemStack section = new ItemStack(PcItems.SEISMIC_SECTION.get());
        section.set(PcDataComponents.SEISMIC_SECTION.get(), new SeismicSectionData(new BlockPos(r.crestX(), 0, r.crestZ()),
                (int) Math.round(r.crestTwtMs()), (int) Math.round(r.crestDepthM()), (int) Math.round(r.closureM()),
                (int) Math.round(r.porePressureKgM3()), r.brightSpot()));
        Ironworks.spill(level, worldPosition.above(), section);
        level.playSound(null, worldPosition, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    private void setLit(boolean lit) {
        BlockState st = getBlockState();
        if (st.getValue(BlockStateProperties.LIT) != lit) level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_ALL);
    }

    /** Начать съёмку: нужен заряд и расстановка; возвращает {@code true}, если взрыв произведён. */
    public boolean shoot(Player player) {
        if (recording >= 0) {
            player.displayClientMessage(DrillingText.msg("recording", 100 * recording / RECORD_TICKS), true);
            return false;
        }
        geophones = countGeophones();
        if (geophones < MIN_GEOPHONES) {
            player.displayClientMessage(DrillingText.msg("geophones", MIN_GEOPHONES, SPREAD_BLOCKS, geophones), true);
            return false;
        }
        recording = 0;
        setLit(true);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.4f, 0.5f);
        player.displayClientMessage(DrillingText.msg("shot", geophones, SeismicSurvey.apertureBlocks(geophones)), true);
        setChanged();
        return true;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.SEISMIC_CHARGE.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!stack.is(PcItems.SEISMIC_CHARGE.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (shoot(player) && !player.getAbilities().instabuild) stack.shrink(1);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (recording >= 0) player.displayClientMessage(DrillingText.msg("recording", 100 * recording / RECORD_TICKS), true);
        else player.displayClientMessage(DrillingText.msg("no_charge"), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("recording", recording);
        tag.putInt("geophones", geophones);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        recording = tag.contains("recording") ? tag.getInt("recording") : -1;
        geophones = tag.getInt("geophones");
    }
}
