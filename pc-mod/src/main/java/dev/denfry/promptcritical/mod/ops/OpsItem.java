package dev.denfry.promptcritical.mod.ops;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Инструмент или запчасть среза обслуживания (13 §13.6.7). Применяется ПКМ по машине, её щиту обслуживания или
 * манометру ({@link OpsActions}); долгие работы (ремонт, смазка, чистка) идут, пока игрок держит ПКМ, —
 * {@link #seconds()} реального времени.
 */
public final class OpsItem extends Item {
    public enum Kind {
        STETHOSCOPE(0), TORQUE_WRENCH(0), TEST_GAUGE(0), CAULKING_HAMMER(0), SAFETY_VALVE(0), OIL_CAN(10),
        BOILER_SCRAPER(60), BEARING_SHELL(60), IMPELLER(120), GLAND_PACKING(30);

        /** Длительность работ, с реального времени (как в {@link dev.denfry.promptcritical.sim.ops.RepairSpec#slice}). */
        public final int seconds;

        Kind(int seconds) {
            this.seconds = seconds;
        }

        public boolean lasting() {
            return seconds > 0;
        }
    }

    private final Kind kind;

    public OpsItem(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public int seconds() {
        return kind.seconds;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return kind.seconds * 20;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return kind.lasting() ? UseAnim.BRUSH : UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide && entity instanceof Player p && remaining % 20 == 0 && !OpsActions.stillValid(p))
            p.releaseUsingItem();
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player p) OpsActions.finish(p, stack);
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide && entity instanceof Player p) OpsActions.cancel(p);
    }
}
