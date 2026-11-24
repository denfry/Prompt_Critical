package dev.denfry.promptcritical.mod.greenhouse;

import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.thermal.Greenhouse;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

/**
 * Рост в теплице и под парником (17 §17.3.6). Внутри теплицы и под парником холоднее 5 °C (или жарче 36 °C) рост
 * останавливается; при подходящей температуре мод сезонов, если он запретил рост зимой, не мешает — растение растёт
 * его же ванильным шансом (16 §16.10). Под парником рост ещё и ускоряется по кардинальной кривой: каждый удачный шаг
 * роста с вероятностью m − 1 даёт ещё одну стадию. Теплица ускоряет рост своими случайными тиками
 * ({@link GreenhouseCoilBlockEntity}). Вне теплиц и парников ванильный рост не трогается.
 */
public final class GreenhouseGrowth {
    private static final ThreadLocal<Boolean> EXTRA = ThreadLocal.withInitial(() -> false);

    private GreenhouseGrowth() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, GreenhouseGrowth::onPre);
        NeoForge.EVENT_BUS.addListener(GreenhouseGrowth::onPost);
    }

    /** Температура растения в {@code pos}, °C: в теплице — её воздух, под парником — наружная плюс дневной прирост; NaN — ни там, ни там. */
    public static double protectedTempC(Level level, BlockPos pos) {
        GreenhouseCoilBlockEntity coil = GreenhouseCoilBlockEntity.at(level, pos);
        if (coil != null) return coil.insideC();
        if (level instanceof ServerLevel sl && underColdFrame(level, pos))
            return Greenhouse.coldFrameC(EnvironmentService.sample(sl, pos, 0).airTempC(), sl.isDay());
        return Double.NaN;
    }

    static boolean underColdFrame(Level level, BlockPos pos) {
        return level.getBlockState(pos.above()).is(PcBlocks.COLD_FRAME.get());
    }

    private static boolean seasonMod() {
        return ModList.get().isLoaded("sereneseasons") || ModList.get().isLoaded("eclipticseasons");
    }

    private static void onPre(CropGrowEvent.Pre e) {
        if (!(e.getLevel() instanceof Level level) || level.isClientSide()) return;
        double t = protectedTempC(level, e.getPos());
        if (Double.isNaN(t)) return;
        if (Greenhouse.growthMultiplier(t) <= 0) e.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        else if (e.getResult() == CropGrowEvent.Pre.Result.DO_NOT_GROW && seasonMod()) e.setResult(CropGrowEvent.Pre.Result.DEFAULT);
    }

    private static void onPost(CropGrowEvent.Post e) {
        if (EXTRA.get() || !(e.getLevel() instanceof ServerLevel level) || !underColdFrame(level, e.getPos())) return;
        if (GreenhouseCoilBlockEntity.at(level, e.getPos()) != null) return; // в теплице ускоряет регистр
        double m = Greenhouse.growthMultiplier(Greenhouse.coldFrameC(EnvironmentService.sample(level, e.getPos(), 0).airTempC(), level.isDay()));
        if (m <= 1 || level.random.nextDouble() >= m - 1) return;
        BlockState s = level.getBlockState(e.getPos());
        if (!(s.getBlock() instanceof CropBlock crop) || crop.isMaxAge(s)) return;
        EXTRA.set(true);
        try {
            level.setBlock(e.getPos(), crop.getStateForAge(crop.getAge(s) + 1), Block.UPDATE_CLIENTS);
        } finally {
            EXTRA.set(false);
        }
    }
}
