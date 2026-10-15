package dev.denfry.promptcritical.mod.item;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Ручные средства шахтёра (08 §8.2.5, §8.2.6): метанометр, изолирующий самоспасатель, ручной распылитель инертной
 * пыли. Действие выполняет шахта ({@code MineWorld}); лампа ведёт себя в руке и отдельного действия не имеет.
 */
public class MineInstrumentItem extends Item {
    public enum Kind { METHANOMETER, SELF_RESCUER, STONE_DUSTER }

    /** Пауза между замерами и распылениями, тиков. */
    public static final int COOLDOWN_TICKS = 20;

    private final Kind kind;

    public MineInstrumentItem(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        SimLevelBinding b = SimBindings.get((ServerLevel) level);
        if (b == null || !b.mines().useInstrument(kind, player, stack)) return InteractionResultHolder.pass(stack);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.consume(stack);
    }
}
