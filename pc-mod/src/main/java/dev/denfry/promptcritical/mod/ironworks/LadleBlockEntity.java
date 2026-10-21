package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.AlloyElement;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnace;
import dev.denfry.promptcritical.sim.metallurgy.Converter;
import dev.denfry.promptcritical.sim.metallurgy.LadleState;
import dev.denfry.promptcritical.sim.metallurgy.SteelMelt;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Ковш {@code ladle} 2×2×2 (08 §8.5.5, §8.6.5): чугуновозный — принимает чугун из жёлоба домны, сталеразливочный —
 * сталь из конвертера (этап 10б). Держит расплав сам (без домена — BE): масса, температура, химия
 * ({@link SteelMelt}); остывает; ниже солидуса металл застывает «козлом». Ковш возят контрапции Create — содержимое
 * едет с блоком. Shift + ПКМ — разлить: чугун в чушки ({@code pig_iron}), сталь — в лом; козёл выбивается так же,
 * но с износом футеровки. Футеровка — {@link LadleState}: холодный ковш захолаживает первую порцию. Чугун и сталь
 * в одном ковше не смешиваются.
 */
public final class LadleBlockEntity extends IronworksBlockEntity {
    /** Вместимость, кг [BALANCE]; потери тепла полного ковша, Вт (≈ 0,5 К/мин физ. на 30 т) [BALANCE]. */
    public static final double CAPACITY_KG = 30_000, LOSS_W = 200_000;
    /** Солидус передельного чугуна (≈ 1150 °C), К; остывание пустой футеровки, с физ. */
    public static final double SOLIDUS_K = 1423.15, LINING_TAU_S = 6 * 3_600, SKULL_WEAR = 0.1;
    /** Сталь застывает ниже ликвидуса на столько, К [моё: интервал кристаллизации углеродистой стали 30–50 К]. */
    public static final double STEEL_FREEZE_BELOW_LIQUIDUS_K = 40;

    private double metalKg, tempK = 293.15;
    private final EnumMap<AlloyElement, Double> chem = new EnumMap<>(AlloyElement.class);
    private boolean steel;
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

    /** В ковше сталь (иначе чугун или пусто). */
    public boolean steel() {
        return steel && metalKg > 0;
    }

    public LadleState lining() {
        return lining;
    }

    public double roomKg() {
        return frozen ? 0 : Math.max(0, CAPACITY_KG - metalKg);
    }

    public double frac(AlloyElement e) {
        Double v = chem.get(e);
        return v == null ? 0 : v;
    }

    /** Расплав в ковше; {@code null} — пусто. */
    @Nullable
    public SteelMelt melt() {
        return metalKg > 0 ? new SteelMelt(metalKg, tempK, chem) : null;
    }

    /** Принять чугун из жёлоба; вернуть принятую массу, кг. Mn и P — передельного чугуна ({@link SteelMelt#hotMetal}). */
    public double receive(double kg, double inTempK, double c, double si, double s) {
        Map<AlloyElement, Double> hm = new EnumMap<>(AlloyElement.class);
        hm.put(AlloyElement.C, c);
        hm.put(AlloyElement.SI, si);
        hm.put(AlloyElement.S, s);
        hm.put(AlloyElement.MN, BlastFurnace.HM_MN);
        hm.put(AlloyElement.P, SteelMelt.HOT_METAL_P);
        return receive(new SteelMelt(Math.max(0, kg), inTempK, hm), false);
    }

    /** Принять расплав ({@code steelMelt} — сталь из конвертера); вернуть принятую массу, кг. */
    public double receive(SteelMelt in, boolean steelMelt) {
        if (metalKg > 0 && steelMelt != steel) return 0;
        double take = Math.min(in.massKg(), roomKg());
        if (!(take > 0)) return 0;
        double t = metalKg <= 0 ? in.tempK() - lining.pourChillK() : in.tempK();
        double m = metalKg + take;
        tempK = (metalKg * tempK + take * t) / m;
        for (AlloyElement e : AlloyElement.values()) {
            double v = (metalKg * frac(e) + take * in.frac(e)) / m;
            if (v > 0) chem.put(e, v);
            else chem.remove(e);
        }
        metalKg = m;
        steel = steelMelt;
        setChanged();
        return take;
    }

    /** Заменить расплав после доводки на установке ({@code ladle_station}); масса может вырасти на добавки. */
    public void replaceMelt(SteelMelt m) {
        if (metalKg <= 0 || frozen) return;
        metalKg = Math.min(CAPACITY_KG, m.massKg());
        tempK = m.tempK();
        chem.clear();
        for (AlloyElement e : AlloyElement.values()) if (m.frac(e) > 0) chem.put(e, m.frac(e));
        setChanged();
    }

    /** Забрать металл (разливка, завалка в конвертер), кг. */
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
        chem.clear();
        steel = false;
        lining = lining.afterHeat(tempK);
        frozen = false;
    }

    private double freezeK() {
        return steel ? new SteelMelt(metalKg, tempK, chem).liquidusK() - STEEL_FREEZE_BELOW_LIQUIDUS_K : SOLIDUS_K;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        double dtp = Ironworks.tickPhysS(b);
        if (metalKg > 0) {
            tempK -= LOSS_W * dtp / (metalKg * (steel ? Converter.STEEL_CP : BlastFurnace.HM_CP));
            lining = new LadleState(Math.max(lining.preheatedK(), tempK - 300), lining.wet(), lining.refractoryWear());
            if (!frozen && tempK < freezeK()) {
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
            if (n > 0) Ironworks.spill(level, worldPosition.above(2), new ItemStack(steel ? PcItems.STEEL_SCRAP.get() : PcItems.PIG_IRON.get(), n));
            empty();
            level.playSound(null, worldPosition, skull ? SoundEvents.ANVIL_LAND : SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0f, 0.8f);
            setChanged();
            return InteractionResult.SUCCESS;
        }
        if (metalKg <= 0) {
            player.displayClientMessage(IronworksText.msg("ladle_empty", IronworksText.num(lining.preheatedK() - 273.15),
                    IronworksText.num(lining.refractoryWear() * 100)), true);
        } else if (steel) {
            player.displayClientMessage(IronworksText.msg(frozen ? "ladle_steel_skull" : "ladle_steel", IronworksText.num(metalKg / 1000),
                    IronworksText.num(tempK - 273.15), IronworksText.num(frac(AlloyElement.C) * 100),
                    IronworksText.num(frac(AlloyElement.MN) * 100), IronworksText.num(frac(AlloyElement.O) * 1e6)), true);
        } else {
            player.displayClientMessage(IronworksText.msg(frozen ? "ladle_skull" : "ladle", IronworksText.num(metalKg / 1000),
                    IronworksText.num(tempK - 273.15), IronworksText.num(frac(AlloyElement.C) * 100),
                    IronworksText.num(frac(AlloyElement.SI) * 100), IronworksText.num(frac(AlloyElement.S) * 100)), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("metal_kg", metalKg);
        tag.putDouble("temp_k", tempK);
        CompoundTag c = new CompoundTag();
        for (Map.Entry<AlloyElement, Double> e : chem.entrySet()) c.putDouble(e.getKey().symbol, e.getValue());
        tag.put("chem", c);
        tag.putBoolean("steel", steel);
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
        chem.clear();
        if (tag.contains("chem")) {
            CompoundTag c = tag.getCompound("chem");
            for (String k : c.getAllKeys()) {
                AlloyElement e = AlloyElement.bySymbol(k);
                if (e != null && c.getDouble(k) > 0) chem.put(e, c.getDouble(k));
            }
        } else if (metalKg > 0) {
            // ковш этапа 10а: чугун C, Si, S
            chem.put(AlloyElement.C, tag.getDouble("c"));
            chem.put(AlloyElement.SI, tag.getDouble("si"));
            chem.put(AlloyElement.S, tag.getDouble("s"));
            chem.put(AlloyElement.MN, BlastFurnace.HM_MN);
            chem.put(AlloyElement.P, SteelMelt.HOT_METAL_P);
        }
        steel = tag.getBoolean("steel");
        frozen = tag.getBoolean("frozen");
        if (tag.contains("lining_k"))
            lining = new LadleState(tag.getDouble("lining_k"), tag.getBoolean("lining_wet"), tag.getDouble("lining_wear"));
    }
}
