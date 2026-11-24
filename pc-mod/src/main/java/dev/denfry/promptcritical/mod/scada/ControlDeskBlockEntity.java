package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.alarm.Alarm;
import dev.denfry.promptcritical.sim.alarm.AlarmState;
import dev.denfry.promptcritical.sim.alarm.AlarmStatistics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Секция пульта {@code control_desk} — рабочее место оператора блочного щита (БЩУ, 03c §3c.9.1) без компьютера: обзор
 * уровня L1 (03c §3c.5.1) по всем табло в {@value #RADIUS} блоках. Список сигнализаций станции по ISA-18.2: сначала
 * неквитированные, затем по приоритету и свежести, страницами по {@value #ROWS}; счётчики P1–P3, неквитированных,
 * шельфованных (шельфинг «не должен быть невидимым», 03c §3c.6.4) и подавленных, лавина и обитаемость щита по ближайшему
 * {@code control_room_core}. Кнопки «КВИТ ВСЁ» и «ЗВУК» действуют на все табло сразу.
 *
 * <p>Упрощение против 03c §3c.2: элементы (приборы, ключи, кнопки) на секцию не монтируются — они ставятся на любую
 * грань рядом, а секция — общий обзор; CC-периферии {@code pc_panel} у секции нет.
 */
public class ControlDeskBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    /** Мониторы и клавиатура пульта, Вт. Малая нагрузка 0,4 кВ: розетка, щит или ИБП рядом. */
    public static final double POWER_W = 500;
    public static final int RADIUS = 32, ROWS = 8;

    private int page;

    public ControlDeskBlockEntity(BlockPos pos, BlockState state) {
        super(ScadaBlockEntities.CONTROL_DESK.get(), pos, state);
    }

    @Override
    public void tickDevice() {
    }

    List<AnnunciatorBlockEntity> annunciators() {
        return Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), AnnunciatorBlockEntity.class);
    }

    @Nullable
    ControlRoomCoreBlockEntity core() {
        ControlRoomCoreBlockEntity best = null;
        double d = Double.MAX_VALUE;
        for (ControlRoomCoreBlockEntity c : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), ControlRoomCoreBlockEntity.class)) {
            double dd = c.getBlockPos().distSqr(worldPosition);
            if (dd < d) {
                d = dd;
                best = c;
            }
        }
        return best;
    }

    /** Сигнализация в списке пульта: табло и сигнализация. */
    public record Row(AnnunciatorBlockEntity ann, Alarm alarm) {
    }

    /** Показанные сигнализации всех табло: неквитированные, затем приоритет, затем новые выше. */
    public List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (AnnunciatorBlockEntity a : annunciators())
            for (Alarm al : a.engine().alarms()) if (al.state().annunciated()) out.add(new Row(a, al));
        out.sort(Comparator.<Row>comparingInt(r -> r.alarm().state() == AlarmState.UNACK || r.alarm().state() == AlarmState.RTNUN ? 0 : 1)
                .thenComparingInt(r -> r.alarm().definition().priority())
                .thenComparingDouble(r -> -r.alarm().tRaise()));
        return out;
    }

    public int acknowledgeAll() {
        int n = 0;
        for (AnnunciatorBlockEntity a : annunciators()) n += a.acknowledgeAll();
        return n;
    }

    public void silenceAll() {
        for (AnnunciatorBlockEntity a : annunciators()) a.silence();
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return ControlRoomPanels.DESK;
    }

    @Override
    public Component panelTitle() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public boolean panelValid(Player player) {
        return PanelHost.near(this, player);
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        List<Row> rows = rows();
        int pages = Math.max(1, (rows.size() + ROWS - 1) / ROWS);
        page = Math.floorMod(page, pages);
        for (int i = 0; i < ROWS; i++) {
            int k = page * ROWS + i;
            if (k >= rows.size()) break;
            Row r = rows.get(k);
            int ref = r.ann().refOf(r.alarm().id());
            out[ControlRoomPanels.R0 + 2 * i] = ref >= 0 ? ref + 1 : -r.alarm().id();
            int f = r.alarm().state().ordinal() | r.alarm().definition().priority() << 4 | AnnunciatorPanels.F_PRESENT;
            if (r.alarm().firstOut()) f |= AnnunciatorPanels.F_FIRST_OUT;
            out[ControlRoomPanels.R0 + 2 * i + 1] = f;
        }
        int[] byPrio = new int[5];
        for (Row r : rows) byPrio[r.alarm().definition().priority()]++;
        int unacked = 0, shelved = 0, suppressed = 0, rate = 0, horn = 0, flood = 0;
        List<AnnunciatorBlockEntity> anns = annunciators();
        for (AnnunciatorBlockEntity a : anns) {
            AlarmStatistics s = a.engine().statistics();
            unacked += s.unacked();
            shelved += s.shelved();
            suppressed += s.suppressed();
            rate += s.rate10min();
            if (s.flood()) flood = 1;
            int h = a.engine().hornPriority();
            if (h > 0 && (horn == 0 || h < horn)) horn = h;
        }
        out[ControlRoomPanels.D_P1] = byPrio[1];
        out[ControlRoomPanels.D_P2] = byPrio[2];
        out[ControlRoomPanels.D_P3] = byPrio[3];
        out[ControlRoomPanels.D_UNACKED] = unacked;
        out[ControlRoomPanels.D_SHELVED] = shelved;
        out[ControlRoomPanels.D_SUPPRESSED] = suppressed;
        // лавина — по оператору: все табло пульта вместе (EEMUA 191 считает сигнализации на оператора)
        out[ControlRoomPanels.D_RATE] = rate;
        out[ControlRoomPanels.D_FLAGS] = (flood != 0 || rate > dev.denfry.promptcritical.sim.alarm.AlarmEngine.FLOOD_ENTER ? 1 : 0) | horn << 1;
        out[ControlRoomPanels.D_ANNS] = anns.size();
        ControlRoomCoreBlockEntity core = core();
        out[ControlRoomPanels.D_MCR] = core == null || !core.enclosed() ? -1 : core.habitability().state().ordinal();
        out[ControlRoomPanels.D_MCR_CAUSE] = core == null ? 0 : core.habitability().cause().ordinal();
        out[ControlRoomPanels.D_IN_ROOM] = core != null && core.contains(worldPosition.above()) ? 1 : 0;
        out[ControlRoomPanels.D_PAGE] = page;
        out[ControlRoomPanels.D_PAGES] = pages;
        out[ControlRoomPanels.D_TOTAL] = rows.size();
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ControlRoomPanels.B_ACK_ALL -> acknowledgeAll();
            case ControlRoomPanels.B_SILENCE_ALL -> silenceAll();
            case ControlRoomPanels.B_PREV -> page--;
            case ControlRoomPanels.B_NEXT -> page++;
            default -> {
            }
        }
        setChanged();
    }

    // ------------------------------------------------------------------ руки и сохранение

    @Override
    public InteractionResult use(Player player) {
        if (!level.isClientSide && !dev.denfry.promptcritical.mod.grid.MachinePower.powered(level, worldPosition, POWER_W)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(dev.denfry.promptcritical.mod.grid.GridText.NO_POWER), true);
            return InteractionResult.SUCCESS;
        }
        int n = acknowledgeAll();
        player.displayClientMessage(ControlRoomPanels.DESK_ACKED.get(n, annunciators().size()), true);
        return InteractionResult.SUCCESS;
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("page", page);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        page = tag.getInt("page");
    }
}
