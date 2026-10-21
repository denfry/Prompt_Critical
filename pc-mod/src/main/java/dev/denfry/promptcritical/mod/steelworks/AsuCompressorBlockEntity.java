package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.sim.utilities.AsuUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Основной воздушный компрессор ВРУ {@code asu_main_compressor} 3×2×2 (08 §8.11) на валу Create через порт сбоку:
 * мощность по закону подобия P = P₀·(n/n₀)³, P₀ = {@value #RATED_W} Вт при {@value #NOMINAL_RPM} об/мин (выше
 * номинала — антипомпажный сброс, не растёт). Концевые холодильники берут оборотную воду из своего бака; подпитка —
 * {@value #MAKEUP} расхода (испарение в градирне). Без воды блок разделения не получает охлаждения.
 */
public final class AsuCompressorBlockEntity extends IronworksBlockEntity implements ShaftConsumer {
    public static final double RATED_W = AsuUnit.SPEC.ratedCompressorW(), NOMINAL_RPM = 64, MIN_RPM = 8, MAKEUP = 0.02;

    private final GasBuffer water = new GasBuffer(4_000, s -> s.getFluid().is(FluidTags.WATER), this::setChanged);
    private double rpm;
    private boolean healthy, cooled;

    public AsuCompressorBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.ASU_MAIN_COMPRESSOR.get(), pos, state);
    }

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        this.rpm = rpm;
        this.healthy = healthy;
    }

    @Override
    public double shaftPowerW() {
        if (!healthy || Math.abs(rpm) < MIN_RPM) return 0;
        double s = Math.min(1, Math.abs(rpm) / NOMINAL_RPM);
        return RATED_W * s * s * s;
    }

    @Override
    public double ratedShaftW() {
        return RATED_W;
    }

    @Override
    public double nominalShaftRpm() {
        return NOMINAL_RPM;
    }

    /** Взять оборотную воду под расход {@code needKgS}; вернуть расход, который холодильники получили, кг/с. */
    public double coolingWater(double needKgS) {
        if (needKgS <= 0) return 0;
        cooled = water.amount() > 0;
        if (!cooled) return 0;
        water.draw(needKgS * MAKEUP * 0.05);
        return needKgS;
    }

    public GasBuffer water() {
        return water;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        setStateFlag(PcProperties.RUNNING, shaftPowerW() > 0);
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.RUNNING)) return;
        MachineFx fx = fx();
        if (fx.chance(1)) fx.sound(fx.at(8, 12, 8), SoundEvents.MINECART_INSIDE, 0.2f, 0.5f);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return water.input();
    }

    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(SteelworksText.msg("asu_compressor", SteelworksText.num(Math.abs(rpm)),
                SteelworksText.num(shaftPowerW() / 1e3), water.amount()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("water", water.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("water")) water.load(registries, tag.getCompound("water"));
    }
}
