package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.grid.BreakerResponse;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.SwitchUnit;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * Разъединитель ({@code disconnector}, 3×2×1, 07 §7.4.6): видимый разрыв без дугогашения. ПКМ — операция ножами,
 * Shift + ПКМ — заземляющие ножи на стороне присоединения (правый вывод). Отключение под нагрузкой тянет дугу: ожог
 * рядом и половина шансов на междуфазное КЗ. {@code interlock_module} запрещает операцию при включённом соседнем
 * выключателе.
 */
public class DisconnectorBlockEntity extends GridDeviceBlockEntity<SwitchUnit> {
    /** Радиус ожога дугой, блоков; урон — полсердца на блок ближе. */
    static final double ARC_RADIUS = 4;
    /** Угол ножа на клиенте, градусы (плавный поворот рендера). */
    public float bladeAngle = Float.NaN;

    public DisconnectorBlockEntity(BlockPos pos, BlockState state) {
        super(GridBlockEntities.DISCONNECTOR.get(), pos, state);
    }

    @Override
    protected SwitchUnit unitOf(GridSubsystem grid) {
        return grid.switchUnit(simId(), SwitchUnit.Kind.DISCONNECTOR);
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> a = new ArrayList<>(), b = new ArrayList<>();
        Direction f = facing();
        for (int y = 0; y <= 1; y++) {
            GridDevice.faces(a, worldPosition, f, -1, y, 0, Direction.WEST);
            GridDevice.faces(b, worldPosition, f, 1, y, 0, Direction.EAST);
        }
        GridDevice.faces(a, worldPosition, f, -1, 0, 0, Direction.DOWN);
        GridDevice.faces(b, worldPosition, f, 1, 0, 0, Direction.DOWN);
        return List.of(a, b);
    }

    @Override
    protected void serverTick(SwitchUnit u) {
        if (ticks % 5 == 0) {
            setFlag(BlockStateProperties.OPEN, !u.closed());
            setFlag(PcProperties.EARTHED, u.earthed());
        }
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.INTERLOCK_MODULE.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        SwitchUnit u = unit();
        if (u == null || u.interlocked()) return ItemInteractionResult.CONSUME;
        u.setInterlock(true);
        stack.consume(1, player);
        player.displayClientMessage(GridText.msg(GridText.INTERLOCK_FITTED), true);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        SwitchUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            boolean on = !u.earthed();
            if (!u.setEarthed(on)) player.displayClientMessage(GridText.msg(GridText.EARTH_REFUSED), true);
            else player.displayClientMessage(GridText.msg(on ? GridText.EARTHED : GridText.UNEARTHED), true);
            return InteractionResult.SUCCESS;
        }
        BreakerResponse r = u.operate(!u.closed());
        if (r != BreakerResponse.ACCEPTED) {
            player.displayClientMessage(GridText.msg(GridText.BREAKER_RESPONSE, BreakerCellBlockEntity.response(r)), true);
            return InteractionResult.SUCCESS;
        }
        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1, 0.6f);
        if (u.lastOperationArced()) arc(player);
        return InteractionResult.SUCCESS;
    }

    /** Дуга при отключении тока нагрузки: вспышка, треск, ожоги в радиусе. */
    private void arc(Player operator) {
        ServerLevel s = (ServerLevel) level;
        operator.displayClientMessage(GridText.msg(GridText.ARC), true);
        s.playSound(null, worldPosition, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 1.5f, 1.4f);
        s.sendParticles(ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2,
                worldPosition.getZ() + 0.5, 40, 1.0, 0.4, 0.4, 0.2);
        s.sendParticles(ParticleTypes.FLASH, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5,
                1, 0, 0, 0, 0);
        for (Player p : s.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(ARC_RADIUS))) {
            double d = Math.sqrt(p.distanceToSqr(worldPosition.getCenter()));
            float dmg = (float) Math.max(1, ARC_RADIUS + 2 - d);
            p.hurt(s.damageSources().inFire(), dmg);
            p.igniteForSeconds(3);
        }
    }
}
