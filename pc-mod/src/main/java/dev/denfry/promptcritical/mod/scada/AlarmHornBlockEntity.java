package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.sound.PcSfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Сирена сигнализации {@code alarm_horn} (03c §3c.2.1, звуки 04a §4a.4.13): звучит, пока у табло в
 * {@value #RADIUS} блоках есть неквитированная сигнализация P1–P3 после гашения. Тон по приоритету, чтобы оператор
 * различал на слух: P1 — двухтональная сирена каждые 2 с, P2 — звонок раз в секунду, P3 — короткий сигнал раз в 2 с.
 * Кнопка «ЗВУК» табло глушит и сирену. ПКМ — какой приоритет звучит.
 */
public class AlarmHornBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    /** Сирена с усилителем, Вт: прибор питается от розетки, щита или ИБП рядом, без питания молчит. */
    public static final double POWER_W = 100;
    public static final int RADIUS = 16;
    private int ticks, priority;

    public AlarmHornBlockEntity(BlockPos pos, BlockState state) {
        super(ScadaBlockEntities.ALARM_HORN.get(), pos, state);
    }

    /** Приоритет, которым сирена звучит сейчас; 0 — молчит. */
    public int priority() {
        return priority;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        if (!dev.denfry.promptcritical.mod.grid.MachinePower.powered(level, worldPosition, POWER_W)) return;
        ticks++;
        if (ticks % 20 == 0) {
            int p = 0;
            for (AnnunciatorBlockEntity a : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), AnnunciatorBlockEntity.class)) {
                int h = a.engine().hornPriority();
                if (h > 0 && (p == 0 || h < p)) p = h;
            }
            priority = p;
            BlockState s = getBlockState();
            if (s.hasProperty(BlockStateProperties.POWERED) && s.getValue(BlockStateProperties.POWERED) != (p > 0))
                level.setBlock(worldPosition, s.setValue(BlockStateProperties.POWERED, p > 0), Block.UPDATE_ALL);
        }
        if (priority == 0) return;
        switch (priority) {
            case 1 -> {
                if (ticks % 40 == 0) sl.playSound(null, worldPosition, PcSfx.AGZ_SIREN.get(), SoundSource.BLOCKS, 2.5f, 1.15f);
            }
            case 2 -> {
                if (ticks % 20 == 0) sl.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 2.0f, 0.8f);
            }
            default -> {
                if (ticks % 40 == 0) sl.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.5f, 1.0f);
            }
        }
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(ScadaText.msg(priority > 0 ? "horn_on" : "horn_off", priority), true);
        return InteractionResult.SUCCESS;
    }
}
