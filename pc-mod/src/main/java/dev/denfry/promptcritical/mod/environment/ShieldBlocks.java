package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.sim.radiation.DoseRate;
import dev.denfry.promptcritical.sim.radiation.ShieldMaterial;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Защитные свойства блоков мира: материал {@link ShieldMaterial} и плотность. Блок — куб 1 м; вода — по жидкости.
 * Сопоставление по идентификатору: свинец, железо и сталь, графит (кладка котла и каналы), бетон и камень (по плотности
 * породы), кирпич и стекло — как бетон с их плотностью, грунт, дерево. Прочие твёрдые блоки — бетоном плотности 1,5
 * (смесь всего подряд); проходимые — воздух.
 */
public final class ShieldBlocks {
    /** Шаг трассировки луча, м. */
    static final double STEP_M = 0.2;

    private ShieldBlocks() {
    }

    /** Материал блока: {@code null} — воздух (ничего не ослабляет). */
    @Nullable
    public static DoseRate.Layer of(BlockState s, double cm) {
        if (s.getFluidState().is(FluidTags.WATER)) return new DoseRate.Layer(ShieldMaterial.WATER, cm, 1.0);
        if (s.isAir()) return null;
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        if (id.contains("lead")) return new DoseRate.Layer(ShieldMaterial.LEAD, cm, 11.35);
        if (id.contains("graphite") || id.contains("fuel_channel")) return new DoseRate.Layer(ShieldMaterial.GRAPHITE, cm, 1.70);
        if (id.contains("iron_block") || id.contains("steel") || id.equals("anvil") || id.contains("iron_door"))
            return new DoseRate.Layer(ShieldMaterial.IRON, cm, 7.87);
        if (id.contains("concrete")) return new DoseRate.Layer(ShieldMaterial.CONCRETE, cm, 2.30);
        if (id.contains("deepslate") || id.contains("obsidian") || id.contains("basalt"))
            return new DoseRate.Layer(ShieldMaterial.CONCRETE, cm, 2.80);
        if (id.contains("stone") || id.contains("cobble") || id.contains("granite") || id.contains("diorite") || id.contains("andesite")
                || id.contains("tuff") || id.contains("ore")) return new DoseRate.Layer(ShieldMaterial.CONCRETE, cm, 2.60);
        if (id.contains("brick") || id.contains("terracotta")) return new DoseRate.Layer(ShieldMaterial.CONCRETE, cm, 1.90);
        if (id.contains("glass")) return new DoseRate.Layer(ShieldMaterial.CONCRETE, cm, 2.50);
        if (id.contains("dirt") || id.contains("grass_block") || id.contains("sand") || id.contains("gravel") || id.contains("clay")
                || id.contains("mud") || id.contains("podzol") || id.contains("mycelium") || id.contains("soul_soil"))
            return new DoseRate.Layer(ShieldMaterial.SOIL, cm, 1.60);
        if (id.contains("log") || id.contains("planks") || id.contains("wood") || id.contains("wool"))
            return new DoseRate.Layer(ShieldMaterial.WOOD, cm, id.contains("wool") ? 0.2 : 0.6);
        if (!s.blocksMotion()) return null;
        return new DoseRate.Layer(ShieldMaterial.CONCRETE, cm, 1.5);
    }

    /**
     * Слои на прямой от источника к точке, по порядку от источника (последний — ближний к точке). Соседние отрезки
     * одного материала и плотности сливаются; блок самого источника не считается.
     */
    public static List<DoseRate.Layer> between(ServerLevel level, Vec3 from, Vec3 to) {
        List<DoseRate.Layer> out = new ArrayList<>();
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1e-6) return out;
        int n = (int) Math.ceil(len / STEP_M);
        double stepCm = len / n * 100;
        BlockPos srcBlock = BlockPos.containing(from), dstBlock = BlockPos.containing(to);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            Vec3 at = from.add(d.scale((i + 0.5) / n));
            p.set(at.x, at.y, at.z);
            if (p.equals(srcBlock) || p.equals(dstBlock)) continue;
            if (!level.isLoaded(p)) continue;
            DoseRate.Layer l = of(level.getBlockState(p), stepCm);
            if (l == null) continue;
            int last = out.size() - 1;
            if (last >= 0 && out.get(last).material() == l.material() && out.get(last).density() == l.density())
                out.set(last, new DoseRate.Layer(l.material(), out.get(last).cm() + stepCm, l.density()));
            else out.add(l);
        }
        return out;
    }
}
