package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.metallurgy.CupolaFurnace;
import dev.denfry.promptcritical.sim.metallurgy.CupolaUnit;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Общее у двух вагранок (08 §8.9): старой {@code cupola_furnace} и реального масштаба {@code cupola_furnace_real}.
 * Обе — один {@link CupolaUnit}; различаются местом вала, лётки и моделью.
 */
final class CupolaLogic {
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.INPUT};

    private CupolaLogic() {
    }

    /** Склад колоши: металл (слот 0) и кокс (слот 1). */
    static MachineInventory inventory(Runnable changed) {
        return new MachineInventory(ROLES, (slot, s) -> slot == 0 ? s.is(FoundryTags.CUPOLA_IRON) : s.is(FoundryTags.CUPOLA_COKE), changed);
    }

    static boolean accepts(ItemStack stack) {
        return stack.is(FoundryTags.CUPOLA_IRON) || stack.is(FoundryTags.CUPOLA_COKE);
    }

    /** Колоша: по предмету металла и кокса за тик, пока в шахте есть место. */
    static void charge(MachineInventory inv, CupolaUnit u) {
        ItemStack iron = inv.getStackInSlot(0);
        if (!iron.isEmpty() && u.charge(ServerConfig.METALLURGY_INGOT_MASS_KG.get(), 0)) iron.shrink(1);
        ItemStack coke = inv.getStackInSlot(1);
        if (!coke.isEmpty() && u.charge(0, ServerConfig.METALLURGY_BULK_ITEM_MASS_KG.get())) coke.shrink(1);
    }

    /** Выпуск в форму в клетке {@code at} или на блок ниже; {@code true} — форма нашлась. */
    static boolean pour(Level level, BlockPos at, FoundrySubsystem shop, CupolaUnit u) {
        return SandMoldBlockEntity.pourInto(level, at, shop, u) || SandMoldBlockEntity.pourInto(level, at.below(), shop, u);
    }

    static void status(Player player, @Nullable CupolaUnit u) {
        if (u == null) return;
        CupolaFurnace f = u.furnace();
        player.displayClientMessage(FoundryText.msg("cupola", FoundryText.num(f.stackIronKg()), FoundryText.num(f.stackCokeKg()),
                FoundryText.num(f.wellKg()), FoundryText.num(f.wellTemperatureK() - 273.15), FoundryText.num(u.airNm3S())), true);
    }

    static void readPanel(int[] out, @Nullable CupolaUnit u, int shaftRpm10, boolean held) {
        Arrays.fill(out, 0);
        out[FoundryPanels.SHAFT_RPM10] = shaftRpm10;
        out[FoundryPanels.FLAGS] = held ? 1 << FoundryPanels.F_HELD : 0;
        if (u == null) return;
        CupolaFurnace f = u.furnace();
        out[FoundryPanels.C_WELL_C] = (int) Math.round(f.wellTemperatureK() - 273.15);
        out[FoundryPanels.C_WELL_KG10] = (int) Math.round(f.wellKg() * 10);
        out[FoundryPanels.C_IRON_KG] = (int) Math.round(f.stackIronKg());
        out[FoundryPanels.C_COKE_KG] = (int) Math.round(f.stackCokeKg());
        out[FoundryPanels.C_AIR_NM3H] = (int) Math.round(u.airNm3S() * 3600);
        out[FoundryPanels.C_MELT_KGH] = (int) Math.round(f.meltRateKgS() * 3600);
        if (u.lit()) out[FoundryPanels.FLAGS] |= 1 << FoundryPanels.F_RUNNING;
    }
}
