package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.binding.SimConfigs;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Гидростойки секции мехкрепи: распор и разгрузка за {@code mining.longwall_support_set_s}. Секцию разгружает сигнал
 * редстоуна на любую клетку (ПЛК 03d и Lua — позже) или ручной рычаг пустой рукой; без них она распирается сама.
 * На клиенте — ход штоков для BER.
 */
public final class PoweredRoofSupportBlockEntity extends DeviceBlockEntity {
    /** Ход штоков в модели между распором и разгрузкой, px. */
    public static final float LEG_TRAVEL_PX = 2f;

    private boolean manualLowered;
    private int pendingTicks;
    private float legs = -1, legsPrev = -1;

    public PoweredRoofSupportBlockEntity(BlockPos pos, BlockState state) {
        super(MineBlockEntities.POWERED_ROOF_SUPPORT.get(), pos, state);
    }

    public void tick() {
        if (level == null) return;
        boolean set = PoweredRoofSupportBlock.set(getBlockState());
        if (level.isClientSide) {
            float target = set ? 1 : 0;
            if (legs < 0) legs = legsPrev = target;
            legsPrev = legs;
            float step = 1f / Math.max(1, setTicks());
            legs = legs < target ? Math.min(target, legs + step) : Math.max(target, legs - step);
            return;
        }
        boolean want = !(manualLowered || powered());
        if (want == set) {
            pendingTicks = 0;
            return;
        }
        if (++pendingTicks < setTicks()) return;
        pendingTicks = 0;
        level.setBlock(worldPosition, getBlockState().setValue(BlockStateProperties.EXTENDED, want), Block.UPDATE_ALL);
        // Смена состояния будит перезаливку шахты, она же заново проверяет пролёты кровли.
        level.playSound(null, worldPosition, want ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.6f, 0.6f);
    }

    private int setTicks() {
        return (int) Math.round(SimConfigs.longwall().supportSetS() * 20);
    }

    /** Сигнал редстоуна на любой клетке секции. */
    private boolean powered() {
        if (!(getBlockState().getBlock() instanceof PoweredRoofSupportBlock b)) return false;
        for (Footprint.Cell c : b.spec().footprint().cells(worldPosition, b.facing(getBlockState())))
            if (level.hasNeighborSignal(c.pos())) return true;
        return false;
    }

    /** Ручной рычаг: разгрузить или снова распереть. */
    public void toggleManual(Player player) {
        manualLowered = !manualLowered;
        setChanged();
        player.displayClientMessage(Component.translatable(manualLowered
                ? "promptcritical.mine.support.lowering" : "promptcritical.mine.support.setting"), true);
    }

    public boolean manualLowered() {
        return manualLowered;
    }

    /** Выдвижение штоков 0 (разгружена) … 1 (распёрта) с интерполяцией кадра. */
    public float legs(float partialTicks) {
        if (legs < 0) return PoweredRoofSupportBlock.set(getBlockState()) ? 1 : 0;
        return legsPrev + (legs - legsPrev) * partialTicks;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("manual_lowered", manualLowered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        manualLowered = tag.getBoolean("manual_lowered");
    }
}
