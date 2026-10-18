package dev.denfry.promptcritical.mod.panel;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Машина с местным щитом: раскладка, показания каналами, склад под слоты раскладки и кнопки. Только сервер; на
 * клиенте меню живёт без хоста.
 */
public interface PanelHost {
    /** Дальше этого игрок от контроллера (квадрат расстояния) щит закрывается; мультиблоки — до 6 блоков в сторону. */
    double REACH_SQ = 10 * 10;

    PanelLayout panelLayout();

    Component panelTitle();

    BlockPos panelPos();

    /** Показания в каналы раскладки ({@code out.length == channels}). */
    void readPanel(int[] out);

    /** Склад под слоты раскладки; {@code null} — слотов нет. */
    @Nullable
    default IItemHandlerModifiable panelItems() {
        return null;
    }

    default void onPanelButton(ServerPlayer player, int id) {
    }

    boolean panelValid(Player player);

    /** Обычная проверка для BE: не снят и игрок рядом. */
    static boolean near(BlockEntity be, Player player) {
        return !be.isRemoved() && player.distanceToSqr(be.getBlockPos().getCenter()) <= REACH_SQ;
    }

    static void open(ServerPlayer player, PanelHost host) {
        PanelLayout layout = host.panelLayout();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new PanelMenu(id, inv, host), host.panelTitle()), buf -> {
            buf.writeUtf(layout.id);
            buf.writeBlockPos(host.panelPos());
        });
    }

    /** Выбросить склад щита при разрушении машины. */
    static void dropItems(Level level, BlockPos pos, @Nullable Object be) {
        if (!(be instanceof PanelHost h) || h.panelItems() == null) return;
        IItemHandlerModifiable items = h.panelItems();
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack s = items.getStackInSlot(i);
            if (s.isEmpty()) continue;
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s.copy());
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
    }
}
