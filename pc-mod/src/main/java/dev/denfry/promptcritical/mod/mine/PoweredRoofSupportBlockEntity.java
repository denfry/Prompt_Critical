package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.binding.SimConfigs;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
    /** Доля насосной станции мехкрепи на одну секцию; без давления стойки не двигаются, Вт (розетка или силовой щит рядом). */
    public static final double SECTION_W = 5e3;
    private final dev.denfry.promptcritical.mod.grid.MachinePower.Drive drive = new dev.denfry.promptcritical.mod.grid.MachinePower.Drive();
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
        if (!drive.advance(level, worldPosition, SECTION_W)) return;
        if (++pendingTicks < setTicks()) return;
        pendingTicks = 0;
        level.setBlock(worldPosition, getBlockState().setValue(BlockStateProperties.EXTENDED, want), Block.UPDATE_ALL);
        // Смена состояния будит перезаливку шахты, она же заново проверяет пролёты кровли.
        level.playSound(null, worldPosition, dev.denfry.promptcritical.mod.sound.PcSfx.ROOF_SUPPORT.get(), SoundSource.BLOCKS, 0.9f, want ? 1.0f : 0.85f);
        if (level instanceof ServerLevel server) effects(server, want);
    }

    /**
     * Видимая перестановка: разгруженное перекрытие отходит от кровли, и с неё сыплется порода; распёртое
     * прижимается к кровле и крошит её. Из гидростоек при этом брызжет эмульсия (слив из поршневой полости).
     */
    private void effects(ServerLevel server, boolean set) {
        if (!(getBlockState().getBlock() instanceof PoweredRoofSupportBlock b)) return;
        Direction back = b.facing(getBlockState()).getOpposite();
        BlockPos roof = worldPosition.above(2);
        BlockState roofState = server.getBlockState(roof);
        double x = worldPosition.getX() + 0.5 + back.getStepX() * 0.5, z = worldPosition.getZ() + 0.5 + back.getStepZ() * 0.5;
        double top = worldPosition.getY() + 1.9;
        if (!roofState.isAir()) {
            if (set) server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, roofState), x, top + 0.1, z, 12, 0.5, 0.05, 0.6, 0.05);
            else server.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, roofState), x, top - 0.1, z, 24, 0.5, 0.05, 0.7, 0);
        }
        server.sendParticles(ParticleTypes.SPLASH, worldPosition.getX() + 0.5, worldPosition.getY() + 0.1, worldPosition.getZ() + 0.5,
                set ? 4 : 10, 0.35, 0.1, 0.2, 0.05);
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
