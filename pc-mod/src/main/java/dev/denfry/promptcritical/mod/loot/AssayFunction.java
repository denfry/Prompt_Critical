package dev.denfry.promptcritical.mod.loot;

import com.mojang.serialization.MapCodec;
import dev.denfry.promptcritical.mod.item.CoalAssayData;
import dev.denfry.promptcritical.mod.item.OreAssayData;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcLootFunctions;
import dev.denfry.promptcritical.mod.worldgen.VeinFields;
import dev.denfry.promptcritical.sim.mining.OreAssay;
import dev.denfry.promptcritical.sim.mining.OreGrade;
import dev.denfry.promptcritical.sim.mining.VeinCatalog;
import dev.denfry.promptcritical.sim.mining.VeinField;
import dev.denfry.promptcritical.sim.mining.VeinHit;
import dev.denfry.promptcritical.sim.mining.VeinType;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Лут-функция {@code promptcritical:assay}: анализ добытого блока по сиду и позиции (08 §8.1.3, §8.2.1) —
 * {@code coal_assay} у угля, {@code ore_assay} у руд. Блок, поставленный игроком вне жилы, получает медиану закона
 * своей руды.
 */
public final class AssayFunction extends LootItemConditionalFunction {
    public static final MapCodec<AssayFunction> CODEC =
            com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> commonFields(i).apply(i, AssayFunction::new));

    private AssayFunction(List<LootItemCondition> conditions) {
        super(conditions);
    }

    public static Builder<?> assay() {
        return simpleBuilder(AssayFunction::new);
    }

    @Override
    public LootItemFunctionType<AssayFunction> getType() {
        return PcLootFunctions.ASSAY.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext ctx) {
        Vec3 origin = ctx.getParamOrNull(LootContextParams.ORIGIN);
        BlockState state = ctx.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (origin == null || state == null) return stack;
        ServerLevel level = ctx.getLevel();
        int x = Mth.floor(origin.x), y = Mth.floor(origin.y), z = Mth.floor(origin.z);
        VeinField field = VeinFields.get(level.getSeed());
        String mineral = mineral(state);
        if (mineral.equals(VeinField.COAL_BLOCK)) {
            stack.set(PcDataComponents.COAL_ASSAY.get(), CoalAssayData.of(field.coalAssay(x, y, z)));
            return stack;
        }
        VeinHit hit = field.sample(x, y, z, VeinFields.biomes(level));
        OreAssay a = hit != null && hit.mineral().equals(mineral) ? field.assay(hit, x, y, z) : null;
        if (a == null) a = median(mineral);
        if (a != null) stack.set(PcDataComponents.ORE_ASSAY.get(), OreAssayData.of(a));
        return stack;
    }

    /** ID руды блока: {@code deepslate_pyrite_ore} → {@code pyrite_ore}. */
    public static String mineral(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return id.startsWith("deepslate_") ? id.substring("deepslate_".length()) : id;
    }

    @Nullable
    private static OreAssay median(String mineral) {
        for (VeinType t : VeinCatalog.M2) {
            for (OreGrade g : t.roles()) {
                if (g == null || !g.mineral().equals(mineral)) continue;
                double purity = "SiO2".equals(g.component()) ? g.gMed() : Double.NaN;
                return new OreAssay(t.id(), mineral, (byte) g.gradeClass(g.gMed()), g.gMed(), Map.of(),
                        Math.min(1, g.gMed() * g.sulfurPerGrade()), null, purity);
            }
        }
        return null;
    }
}
