package dev.denfry.promptcritical.mod.mine;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Механизированная лава на контрапциях Create (08 §8.2.3): комбайн едет на {@code gantry} и режет
 * ({@link ShearerMovementBehaviour}), секции мехкрепи двигает поршень. Обе конструкции контрапция берёт целиком
 * (клетки держатся друг за друга); распёртую секцию двигать нельзя.
 */
public final class MineLongwall {
    private MineLongwall() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(MineLongwall::setup);
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            MovementBehaviour.REGISTRY.register(PcBlocks.LONGWALL_SHEARER.get(), new ShearerMovementBehaviour());
            BlockMovementChecks.registerMovementAllowedCheck(MineLongwall::movementAllowed);
            BlockMovementChecks.registerAttachedCheck(MineLongwall::attached);
        });
    }

    /** Контроллер лавной конструкции (комбайн или секция), к которой относится клетка; иначе {@code null}. */
    @Nullable
    static BlockPos longwallController(Level level, BlockPos pos, BlockState state) {
        if (isLongwall(state)) return pos;
        if (!(state.getBlock() instanceof MultiblockPartBlock)) return null;
        BlockPos c = PcBlock.structureController(level, MultiblockPartBlock.anchor(state, pos));
        return c != null && isLongwall(level.getBlockState(c)) ? c : null;
    }

    private static boolean isLongwall(BlockState s) {
        return s.is(PcBlocks.LONGWALL_SHEARER.get()) || s.is(PcBlocks.POWERED_ROOF_SUPPORT.get());
    }

    private static CheckResult movementAllowed(BlockState state, Level level, BlockPos pos) {
        BlockPos c = longwallController(level, pos, state);
        if (c == null) return CheckResult.PASS;
        BlockState cs = level.getBlockState(c);
        if (cs.is(PcBlocks.POWERED_ROOF_SUPPORT.get())) return CheckResult.of(!PoweredRoofSupportBlock.set(cs));
        return CheckResult.SUCCESS;
    }

    private static CheckResult attached(BlockState state, Level level, BlockPos pos, Direction direction) {
        BlockPos c = longwallController(level, pos, state);
        if (c == null) return CheckResult.PASS;
        BlockPos n = pos.relative(direction);
        return Objects.equals(c, longwallController(level, n, level.getBlockState(n))) ? CheckResult.SUCCESS : CheckResult.PASS;
    }
}
