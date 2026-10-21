package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.LadleBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.AlloyElement;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.IngotMold;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import dev.denfry.promptcritical.sim.metallurgy.SteelIngot;
import dev.denfry.promptcritical.sim.metallurgy.SteelMelt;
import dev.denfry.promptcritical.sim.metallurgy.StockState;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Изложница {@code ingot_mold} 1×2×1 (08 §8.7.1, I0–I1): сталеразливочный ковш, поставленный над ней (до
 * {@value #POUR_REACH} блоков выше верха), разливает сталь сверху, {@value #TEEM_KG_TICK} кг за тик, пока изложница
 * не полна ({@value #CAPACITY_KG} кг). Без ковша металл застывает: {@value #SOLIDIFY_S} с физ. на тонну^⅔
 * [BALANCE]; затем слиток выбивается в выходы — горячие {@code steel_ingot} по 100 кг (≈ 160 мм) и обрезь
 * головной части ломом. Огнеупорный кирпич на пустую изложницу — прибыльная надставка (обрезь 12 % вместо 17 %).
 */
public final class IngotMoldBlockEntity extends IronworksBlockEntity {
    public static final double CAPACITY_KG = 1_000, TEEM_KG_TICK = 50, SOLIDIFY_S = 3_600, STRIP_K = 1373.15;
    public static final int POUR_REACH = 4;
    private static final MachineInventory.Role[] ROLES = new MachineInventory.Role[12];

    static {
        Arrays.fill(ROLES, MachineInventory.Role.OUTPUT);
    }

    private final MachineInventory out = new MachineInventory(ROLES, (slot, s) -> false, this::setChanged);
    private final EnumMap<AlloyElement, Double> chem = new EnumMap<>(AlloyElement.class);
    private double meltKg, tempK, solidS;
    private boolean hotTop, pouring;
    private long heatNo;

    public IngotMoldBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.INGOT_MOLD.get(), pos, state);
    }

    public double meltKg() {
        return meltKg;
    }

    public boolean hotTop() {
        return hotTop;
    }

    @Nullable
    private LadleBlockEntity ladleAbove() {
        AABB box = new AABB(worldPosition.above(2)).expandTowards(0, POUR_REACH, 0).inflate(1, 0, 1);
        List<LadleBlockEntity> found = Ironworks.blockEntities(level, box, LadleBlockEntity.class);
        for (LadleBlockEntity l : found) if (l.steel() && !l.frozen()) return l;
        return null;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        pouring = false;
        boolean empty = !hasOutput();
        if (solidS == 0 && meltKg < CAPACITY_KG && empty) {
            LadleBlockEntity l = ladleAbove();
            SteelMelt m = l == null ? null : l.melt();
            if (m != null) {
                double kg = l.take(Math.min(TEEM_KG_TICK, CAPACITY_KG - meltKg));
                if (kg > 0) {
                    if (meltKg <= 0) heatNo = level.getGameTime();
                    double total = meltKg + kg;
                    tempK = (meltKg * tempK + kg * m.tempK()) / total;
                    for (AlloyElement e : AlloyElement.values()) {
                        double v = (meltKg * frac(e) + kg * m.frac(e)) / total;
                        if (v > 0) chem.put(e, v);
                        else chem.remove(e);
                    }
                    meltKg = total;
                    pouring = true;
                    setChanged();
                }
            }
        }
        if (meltKg > 0 && !pouring) {
            solidS += Ironworks.tickPhysS(b);
            if (solidS >= SOLIDIFY_S * Math.pow(meltKg / 1000, 2.0 / 3)) strip();
            setChanged();
        }
        setStateFlag(PcProperties.FILLED, meltKg > 0);
    }

    private double frac(AlloyElement e) {
        Double v = chem.get(e);
        return v == null ? 0 : v;
    }

    private boolean hasOutput() {
        for (int i = 0; i < out.getSlots(); i++) if (!out.getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    /** Выбить застывший слиток в выходы. */
    private void strip() {
        SteelIngot ingot = IngotMold.cast(new SteelMelt(meltKg, tempK, chem), hotTop);
        Map<AlloyElement, Double> c = ingot.chem();
        SteelGrade g = SteelGrade.classify(c, HeatTreatState.AS_ROLLED);
        double item = Ironworks.bulkKg();
        double side = Math.cbrt(item / StockState.DENSITY / 3);
        int n = (int) Math.floor(ingot.massKg() / item + 1e-9), crop = (int) Math.floor(ingot.cropKg() / item + 1e-9);
        long now = level.getGameTime();
        ItemStack ingots = new ItemStack(PcItems.STEEL_INGOT.get(), n);
        ingots.set(SteelworksComponents.STEEL_STOCK.get(),
                SteelworksComponents.SteelStock.of(g, side, side, ingot.porous() || ingot.laps(), false, heatNo, STRIP_K, now));
        while (!ingots.isEmpty()) {
            ItemStack rest = out.output(ingots.split(ingots.getMaxStackSize()));
            if (!rest.isEmpty()) Ironworks.spill(level, worldPosition.above(2), rest);
        }
        if (crop > 0) {
            ItemStack rest = out.output(new ItemStack(PcItems.STEEL_SCRAP.get(), crop));
            if (!rest.isEmpty()) Ironworks.spill(level, worldPosition.above(2), rest);
        }
        meltKg = solidS = 0;
        chem.clear();
        hotTop = false;
        level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6f, 0.6f);
        setChanged();
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.FILLED)) return;
        MachineFx fx = fx();
        if (fx.chance(4)) fx.sparks(fx.at(8, 31, 8), 1);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return out.external();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.REFRACTORY_BRICK.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (meltKg > 0 || hotTop || hasOutput()) return ItemInteractionResult.FAIL;
        hotTop = true;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(SteelworksText.msg("mold_hot_top"), true);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (out.giveOutputs(player)) return InteractionResult.SUCCESS;
        if (meltKg > 0) player.displayClientMessage(SteelworksText.msg("mold", SteelworksText.num(meltKg),
                SteelworksText.num(tempK - 273.15), SteelworksText.num(100 * solidS / (SOLIDIFY_S * Math.pow(meltKg / 1000, 2.0 / 3)))), true);
        else player.displayClientMessage(SteelworksText.msg(hotTop ? "mold_empty_hot_top" : "mold_empty"), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("out", out.save(registries));
        tag.putDouble("melt_kg", meltKg);
        tag.putDouble("temp_k", tempK);
        tag.putDouble("solid_s", solidS);
        tag.putBoolean("hot_top", hotTop);
        tag.putLong("heat", heatNo);
        CompoundTag c = new CompoundTag();
        for (Map.Entry<AlloyElement, Double> e : chem.entrySet()) c.putDouble(e.getKey().symbol, e.getValue());
        tag.put("chem", c);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("out")) out.load(registries, tag.getCompound("out"));
        meltKg = tag.getDouble("melt_kg");
        tempK = tag.getDouble("temp_k");
        solidS = tag.getDouble("solid_s");
        hotTop = tag.getBoolean("hot_top");
        heatNo = tag.getLong("heat");
        chem.clear();
        CompoundTag c = tag.getCompound("chem");
        for (String k : c.getAllKeys()) {
            AlloyElement e = AlloyElement.bySymbol(k);
            if (e != null) chem.put(e, c.getDouble(k));
        }
    }
}
