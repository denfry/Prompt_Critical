package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnace;
import dev.denfry.promptcritical.sim.metallurgy.LadleState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Чугуновозный ковш {@code ladle} 2×2×2 (08 §8.5.5, §8.6.5): принимает чугун из жёлоба домны, держит его горячим
 * сам (без домена — BE), остывает; ниже солидуса {@value #SOLIDUS_K} К металл застывает «козлом». Ковш возят
 * контрапции Create — содержимое едет с блоком. Shift + ПКМ — разлить в чушки ({@code pig_iron}); козёл выбивается
 * так же, но с износом футеровки. Футеровка — {@link LadleState}: холодный ковш захолаживает первую порцию.
 */
public final class LadleBlockEntity extends IronworksBlockEntity {
    /** Вместимость, кг [BALANCE]; потери тепла полного ковша, Вт (≈ 0,5 К/мин физ. на 30 т) [BALANCE]. */
    public static final double CAPACITY_KG = 30_000, LOSS_W = 200_000;
    /** Солидус передельного чугуна (≈ 1150 °C), К; остывание пустой футеровки, с физ. */
    public static final double SOLIDUS_K = 1423.15, LINING_TAU_S = 6 * 3_600, SKULL_WEAR = 0.1;

    private double metalKg, tempK = 293.15, carbonFrac, siliconFrac, sulfurFrac;
    private LadleState lining = new LadleState(293.15, false, 0);
    private boolean frozen;

    public LadleBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.LADLE.get(), pos, state);
    }

    public double metalKg() {
        return metalKg;
    }

    public double tempK() {
        return tempK;
    }

    public boolean frozen() {
        return frozen;
    }

    public LadleState lining() {
        return lining;
    }

    public double roomKg() {
        return frozen ? 0 : Math.max(0, CAPACITY_KG - metalKg);
    }

    /** Принять чугун из жёлоба; вернуть принятую массу, кг. */
    public double receive(double kg, double inTempK, double c, double si, double s) {
        double take = Math.min(kg, roomKg());
        if (!(take > 0)) return 0;
        double t = metalKg <= 0 ? inTempK - lining.pourChillK() : inTempK;
        double m = metalKg + take;
        tempK = (metalKg * tempK + take * t) / m;
        carbonFrac = (metalKg * carbonFrac + take * c) / m;
        siliconFrac = (metalKg * siliconFrac + take * si) / m;
        sulfurFrac = (metalKg * sulfurFrac + take * s) / m;
        metalKg = m;
        setChanged();
        return take;
    }

    /** Забрать металл (разливка, конвертер — этап 10б), кг. */
    public double take(double kg) {
        if (frozen) return 0;
        double t = Math.min(kg, metalKg);
        metalKg -= t;
        if (metalKg <= 1e-6) empty();
        setChanged();
        return t;
    }

    private void empty() {
        metalKg = 0;
        lining = lining.afterHeat(tempK);
        frozen = false;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        double dtp = Ironworks.tickPhysS(b);
        if (metalKg > 0) {
            tempK -= LOSS_W * dtp / (metalKg * BlastFurnace.HM_CP);
            lining = new LadleState(Math.max(lining.preheatedK(), tempK - 300), lining.wet(), lining.refractoryWear());
            if (!frozen && tempK < SOLIDUS_K) {
                frozen = true;
                level.playSound(null, worldPosition, SoundEvents.BASALT_PLACE, SoundSource.BLOCKS, 1.0f, 0.5f);
            }
        } else if (lining.preheatedK() > 293.2) {
            double t = lining.preheatedK() - (lining.preheatedK() - 293.15) * Math.min(1, dtp / LINING_TAU_S);
            lining = new LadleState(t, lining.wet(), lining.refractoryWear());
        }
        setStateFlag(PcProperties.FILLED, metalKg > 0 && !frozen);
        setStateFlag(PcProperties.HOT, metalKg > 0);
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.FILLED)) return;
        MachineFx fx = fx();
        if (fx.chance(3)) fx.sparks(fx.at(16, 22, 16), 1);
        if (fx.chance(1)) fx.smoke(fx.at(16, 24, 16), false, 0.5);
    }

    @Override
    public InteractionResult use(Player player) {
        if (player.isShiftKeyDown() && metalKg > 0) {
            int n = Ironworks.items(metalKg);
            boolean skull = frozen;
            if (skull) lining = new LadleState(lining.preheatedK(), lining.wet(), lining.refractoryWear() + SKULL_WEAR);
            if (n > 0) Ironworks.spill(level, worldPosition.above(2), new ItemStack(PcItems.PIG_IRON.get(), n));
            empty();
            level.playSound(null, worldPosition, skull ? SoundEvents.ANVIL_LAND : SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0f, 0.8f);
            setChanged();
            return InteractionResult.SUCCESS;
        }
        if (metalKg <= 0) {
            player.displayClientMessage(IronworksText.msg("ladle_empty", IronworksText.num(lining.preheatedK() - 273.15),
                    IronworksText.num(lining.refractoryWear() * 100)), true);
        } else {
            player.displayClientMessage(IronworksText.msg(frozen ? "ladle_skull" : "ladle", IronworksText.num(metalKg / 1000),
                    IronworksText.num(tempK - 273.15), IronworksText.num(carbonFrac * 100), IronworksText.num(siliconFrac * 100),
                    IronworksText.num(sulfurFrac * 100)), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("metal_kg", metalKg);
        tag.putDouble("temp_k", tempK);
        tag.putDouble("c", carbonFrac);
        tag.putDouble("si", siliconFrac);
        tag.putDouble("s", sulfurFrac);
        tag.putBoolean("frozen", frozen);
        tag.putDouble("lining_k", lining.preheatedK());
        tag.putBoolean("lining_wet", lining.wet());
        tag.putDouble("lining_wear", lining.refractoryWear());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        metalKg = tag.getDouble("metal_kg");
        tempK = tag.contains("temp_k") ? tag.getDouble("temp_k") : 293.15;
        carbonFrac = tag.getDouble("c");
        siliconFrac = tag.getDouble("si");
        sulfurFrac = tag.getDouble("s");
        frozen = tag.getBoolean("frozen");
        if (tag.contains("lining_k"))
            lining = new LadleState(tag.getDouble("lining_k"), tag.getBoolean("lining_wet"), tag.getDouble("lining_wear"));
    }
}
