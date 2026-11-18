package dev.denfry.promptcritical.mod.ops;

import com.simibubi.create.AllBlocks;
import dev.denfry.promptcritical.sim.ops.HotWork;
import dev.denfry.promptcritical.sim.ops.OpsLogEntry;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.PermitEnforcement;
import dev.denfry.promptcritical.sim.ops.PermitRegistry;
import dev.denfry.promptcritical.sim.ops.PermitType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Огневые работы в мире (13 §13.2.5): огниво и огненный заряд по блоку, установка факела или костра в 11 м от горючей
 * среды и «сварка» — установка трубы Create вплотную к блоку с горючей средой. Разрешение — {@link HotWork#verdict} по
 * наряду {@code HOT} и {@code ops.permit_enforcement}; воспламенение — {@link HotWork#ignites} по 27 точкам атмосферы,
 * независимо от бумаг.
 */
public final class HotWorks {
    /** Сила вспышки облака у места огневых работ [BALANCE]: как взрыв метана в выработке малого объёма. */
    static final float FLASH_POWER = 2.0f;

    private HotWorks() {
    }

    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        ItemStack s = e.getItemStack();
        if (!s.is(Items.FLINT_AND_STEEL) && !s.is(Items.FIRE_CHARGE)) return;
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        BlockPos at = e.getPos().relative(e.getFace() == null ? Direction.UP : e.getFace());
        if (!attempt(p, at, Permits.enforcement(), false)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockState s = e.getPlacedBlock();
        boolean flame = s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH)
                || s.is(BlockTags.CAMPFIRES);
        boolean weld = AllBlocks.FLUID_PIPE.has(s) && weldsToFlammable(level, e.getPos());
        if (!flame && !weld) return;
        if (!attempt(p, e.getPos(), Permits.enforcement(), weld)) e.setCanceled(true);
    }

    /** Труба встаёт вплотную к блоку с горючей средой: врезка в действующую сеть — огневые работы. */
    static boolean weldsToFlammable(Level level, BlockPos pipe) {
        for (Direction d : Direction.values()) if (Permits.holdsFlammable(level, pipe.relative(d))) return true;
        return false;
    }

    /**
     * Огневое действие игрока у места: разрешено ли оно и что с атмосферой.
     *
     * @param weld «сварка» трубы к сети с горючей средой — горючая среда рядом по определению
     * @return {@code false} — действие запрещено ({@code ENFORCED} без наряда)
     */
    public static boolean attempt(ServerPlayer p, BlockPos at, PermitEnforcement mode, boolean weld) {
        ServerLevel level = p.serverLevel();
        boolean near = weld || Permits.flammableNear(level, at, PermitRegistry.HOT_WORK_RADIUS_M);
        PermitRegistry r = Permits.registry(level);
        PermitRegistry.Permit permit = r == null ? null : r.activeCovering(PermitType.HOT, Permits.key(at));
        HotWork.Verdict v = HotWork.verdict(mode, near, permit != null);
        OpsSubsystem ops = OpsWorld.ops(level);
        switch (v) {
            case REFUSE -> {
                p.displayClientMessage(PermitText.msg("hot.refused").withStyle(ChatFormatting.RED), false);
                if (ops != null) ops.journal(dev.denfry.promptcritical.sim.core.SimId.NONE, OpsLogEntry.Kind.UNAUTHORIZED_WORK,
                        "HOT refused " + p.getGameProfile().getName() + " @" + at.toShortString());
                return false;
            }
            case WARN -> {
                p.displayClientMessage(PermitText.msg("hot.warned").withStyle(ChatFormatting.GOLD), false);
                if (ops != null) ops.journal(dev.denfry.promptcritical.sim.core.SimId.NONE, OpsLogEntry.Kind.UNAUTHORIZED_WORK,
                        "HOT " + p.getGameProfile().getName() + " @" + at.toShortString());
            }
            case ALLOW -> {
                if (permit != null) Permits.hotWorkUnderPermit(p, permit);
            }
        }
        double[] lel = new double[HotWork.SAMPLE_POINTS];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) lel[i++] = Permits.air(level, at.offset(dx, dy, dz)).lel();
        if (HotWork.ignites(lel)) {
            p.displayClientMessage(PermitText.msg("hot.ignition").withStyle(ChatFormatting.RED), false);
            if (ops != null) ops.journal(dev.denfry.promptcritical.sim.core.SimId.NONE, OpsLogEntry.Kind.UNAUTHORIZED_WORK,
                    "IGNITION @" + at.toShortString());
            level.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, FLASH_POWER, true, Level.ExplosionInteraction.NONE);
        }
        return true;
    }
}
