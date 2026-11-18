package dev.denfry.promptcritical.mod.gas;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.sound.PcSfx;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Поршневой газовый компрессор установки подготовки газа
 * ({@link dev.denfry.promptcritical.sim.gasprocessing.GasCompressor}) на валу Create, вал слева. Номинал — 400 кВт при
 * 64 об/мин; доступная мощность пропорциональна оборотам. Нагрузку — сжатие газа и холодильный цикл — сообщает
 * установка НТС.
 */
public final class GasCompressorBlockEntity extends KineticMachineBlockEntity<ShaftInput> {
    public static final double NOMINAL_RPM = 64, RATED_W = 400e3;

    private final ShaftInput shaft = new ShaftInput();
    private long tick;
    private double loadW;

    public GasCompressorBlockEntity(BlockPos pos, BlockState state) {
        super(GasBlockEntities.GAS_COMPRESSOR.get(), pos, state);
    }

    /** Мощность, которую может отдать вал, Вт. */
    public double availableW() {
        return RATED_W * Math.min(1, shaft.rpm(tick) / NOMINAL_RPM);
    }

    public void load(double watts) {
        loadW = Math.clamp(watts, 0, availableW());
    }

    @Override
    protected ShaftInput unit(SimLevelBinding binding) {
        return shaft;
    }

    @Override
    protected ShaftInput shaft(ShaftInput u) {
        return u;
    }

    @Override
    protected double powerW(ShaftInput u) {
        return u.rpm(tick) > 0 ? loadW : 0;
    }

    @Override
    protected double ratedW() {
        return RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return NOMINAL_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding binding, ShaftInput u) {
        tick = binding.world().clock().tick();
        if (level.getGameTime() % 10 == 0) setStateFlag(PcProperties.RUNNING, u.rpm(tick) > 0 && loadW > 0);
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
        return InteractionResult.PASS;
    }

    /**
     * Работающий компрессор (арт art/blockbench/gas_processing/gas_compressor.author.js): тупой стук клапанов
     * и такт поршней, чаще и выше с оборотами вала; маховик крутит рендер.
     */
    @Override
    protected void clientTick() {
        if (!runningState()) return;
        MachineFx fx = fx();
        if (fx == null) return;
        float k = Math.min(2f, Math.abs(getSpeed()) / 64f);
        if (fx.chance(0.6)) fx.sound(fx.at(16, 8, 16), PcSfx.PUMP.get(), 0.35f + 0.2f * k, 0.6f + 0.3f * k);
        if (fx.chance(0.8 * k)) fx.sound(fx.at(12, 13, 2), SoundEvents.IRON_TRAPDOOR_CLOSE, 0.15f, 1.6f);
    }
}
