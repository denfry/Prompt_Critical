package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.sim.scada.WatchdogTimer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Сторожевое реле {@code watchdog_relay} (03c §3c.7.2): {@link WatchdogTimer} на тике сервера. Программа CC взводит его
 * и кормит через периферию {@code pc_watchdog}; без корма дольше таймаута реле срабатывает в тот же тик: даёт редстоун 15
 * в стену (выход реле) и зажигает красную лампу «сработало» — табло в 16 блоках само заводит по ней сигнализацию
 * приоритета 2, это действие {@code RAISE_ALARM} («ОТКАЗ АСУ»). Действия над механизмами ({@code SET_POSITION},
 * {@code OPEN}, {@code CLOSE}, {@code STOP}, {@code REQUEST_TRIP} …) пока не реализованы: у механизмов мода нет общего
 * интерфейса команд с источником {@code watchdog} — механизм, управляемый редстоуном, ведёт выход реле.
 *
 * <p>На щите реле — кнопка «Сброс» (местный сброс без владельца, как кнопка на реальном реле) и таймаут ±.
 */
public class WatchdogRelayBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    /** Отказ команды реле: сообщение — ошибка Lua. */
    public static final class WatchdogException extends RuntimeException {
        public WatchdogException(String message) {
            super(message);
        }
    }

    static final int STATE = 0, TIMEOUT10 = 1, OWNER = 2, SINCE10 = 3, TRIPS = 4, FLAGS = 5, CHANNELS = 6;
    static final int F_TRIPPED = 0, F_ARMED = 1;
    static final int B_RESET = 0, B_SLOWER = 1, B_FASTER = 2;
    private static final double[] STEPS = {0.5, 1, 2, 5, 10, 30, 60};

    private static final Tr[] STATE_TEXT = {
            PanelText.tr("watchdog.state.disarmed", "Disarmed", "Не взведено"),
            PanelText.tr("watchdog.state.armed", "Armed", "Взведено"),
            PanelText.tr("watchdog.state.tripped", "TRIPPED: control system failure", "СРАБОТАЛО: отказ АСУ")};
    private static final Tr LINE = PanelText.tr("watchdog.line", "%s · timeout %s s · owner computer %s",
                    "%s · таймаут %s с · владелец — компьютер %s"),
            KICK = PanelText.tr("watchdog.kick", "Last kick %s s ago · trips %s", "Последний корм %s с назад · срабатываний %s"),
            NONE = PanelText.tr("watchdog.none", "none", "нет"),
            TRIPPED = PanelText.tr("watchdog.tripped", "Tripped", "Сработало"),
            ARMED = PanelText.tr("watchdog.armed", "Armed", "Взведено"),
            RESET = PanelText.tr("watchdog.reset", "Reset", "Сброс"),
            TIP = PanelText.tr("watchdog.tip",
                    "A controller program calls kick() after every good loop; without a kick for the timeout the relay trips, powers its wall and lights the alarm lamp",
                    "Программа регулятора вызывает kick() после каждой удачной итерации; без корма дольше таймаута реле срабатывает, даёт редстоун в стену и зажигает лампу сигнализации");

    public static final PanelLayout LAYOUT = PanelLayout.builder("watchdog_relay", 232, 110, CHANNELS)
            .readout(8, 22, 216, v -> LINE.get(stateText(v.raw(STATE)), v.num(TIMEOUT10, 10, 1),
                    v.raw(OWNER) < 0 ? NONE.get() : Component.literal(Integer.toString(v.raw(OWNER)))), v -> TIP.get())
            .readout(8, 38, 216, v -> KICK.get(v.raw(SINCE10) < 0 ? "—" : v.num(SINCE10, 10, 1), v.raw(TRIPS)), null)
            .lamp(8, 56, FLAGS, F_TRIPPED, LampColor.RED, TRIPPED.get())
            .lamp(110, 56, FLAGS, F_ARMED, LampColor.GREEN, ARMED.get())
            .button(8, 80, 60, B_RESET, RESET.get(), null)
            .button(150, 80, 34, B_SLOWER, Component.literal("T −"), null)
            .button(190, 80, 34, B_FASTER, Component.literal("T +"), null)
            .noInventory().build();

    private final WatchdogTimer timer = new WatchdogTimer();

    public WatchdogRelayBlockEntity(BlockPos pos, BlockState state) {
        super(ScadaBlockEntities.WATCHDOG_RELAY.get(), pos, state);
    }

    public static List<PanelLayout> all() {
        return List.of(LAYOUT);
    }

    private static Component stateText(int s) {
        Component c = STATE_TEXT[Math.clamp(s, 0, 2)].get();
        return s == 2 ? c.copy().withStyle(ChatFormatting.RED) : c;
    }

    public WatchdogTimer timer() {
        return timer;
    }

    double nowS() {
        return level == null ? 0 : level.getGameTime() / 20.0;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel)) return;
        if (timer.step(nowS())) setChanged();
        output();
    }

    private void output() {
        BlockState s = getBlockState();
        boolean on = timer.tripped();
        if (s.hasProperty(BlockStateProperties.POWERED) && s.getValue(BlockStateProperties.POWERED) != on)
            level.setBlock(worldPosition, s.setValue(BlockStateProperties.POWERED, on), Block.UPDATE_ALL);
    }

    private <T> T guard(java.util.function.Supplier<T> f) {
        try {
            T r = f.get();
            setChanged();
            return r;
        } catch (WatchdogTimer.AccessException | IllegalArgumentException e) {
            throw new WatchdogException(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ pc_watchdog (03c §3c.7.2)

    public void arm(int computer) {
        guard(() -> {
            timer.arm(computer, nowS());
            return null;
        });
    }

    public void kick(int computer) {
        guard(() -> {
            timer.kick(computer, nowS());
            return null;
        });
    }

    public void reset(int computer) {
        guard(() -> {
            timer.reset(computer);
            return null;
        });
        output();
    }

    public void takeover(int computer) {
        guard(() -> {
            timer.takeover(computer);
            return null;
        });
    }

    public void release(int computer) {
        guard(() -> {
            timer.release(computer);
            return null;
        });
    }

    public void configure(double timeout) {
        guard(() -> {
            timer.configure(timeout);
            return null;
        });
    }

    public Map<String, Object> state(int computer) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("state", timer.state().name());
        m.put("armed", timer.state() == WatchdogTimer.State.ARMED);
        m.put("tripped", timer.tripped());
        m.put("timeout", timer.timeoutS());
        m.put("owner", timer.owner());
        m.put("isOwner", timer.owner() == computer);
        double since = timer.sinceKick(nowS());
        if (!Double.isNaN(since)) m.put("sinceKick", since);
        if (timer.tripped()) m.put("trippedAt", timer.trippedAt());
        m.put("trips", timer.trips());
        return m;
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return LAYOUT;
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
        out[STATE] = timer.state().ordinal();
        out[TIMEOUT10] = (int) Math.round(timer.timeoutS() * 10);
        out[OWNER] = timer.owner();
        double since = timer.sinceKick(nowS());
        out[SINCE10] = Double.isNaN(since) ? -1 : (int) Math.min(Integer.MAX_VALUE, Math.round(since * 10));
        out[TRIPS] = timer.trips();
        out[FLAGS] = (timer.tripped() ? 1 << F_TRIPPED : 0) | (timer.state() == WatchdogTimer.State.ARMED ? 1 << F_ARMED : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case B_RESET -> {
                timer.reset(timer.owner());
                output();
            }
            case B_SLOWER, B_FASTER -> {
                int i = 0;
                while (i < STEPS.length - 1 && STEPS[i] < timer.timeoutS() - 1e-9) i++;
                i = Math.clamp(i + (id == B_FASTER ? 1 : -1), 0, STEPS.length - 1);
                timer.configure(STEPS[i]);
            }
            default -> {
            }
        }
        setChanged();
    }

    // ------------------------------------------------------------------ руки и сохранение

    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(stateText(timer.state().ordinal()), true);
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
        tag.putString("state", timer.state().name());
        tag.putDouble("timeout", timer.timeoutS());
        tag.putDouble("lastKick", timer.lastKick());
        tag.putDouble("trippedAt", timer.trippedAt());
        tag.putInt("owner", timer.owner());
        tag.putInt("trips", timer.trips());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.contains("state")) return;
        WatchdogTimer.State s;
        try {
            s = WatchdogTimer.State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            s = WatchdogTimer.State.DISARMED;
        }
        double timeout = Math.clamp(tag.contains("timeout") ? tag.getDouble("timeout") : WatchdogTimer.DEFAULT_TIMEOUT_S,
                WatchdogTimer.MIN_TIMEOUT_S, WatchdogTimer.MAX_TIMEOUT_S);
        timer.load(s, timeout, tag.getDouble("lastKick"), tag.getDouble("trippedAt"), tag.getInt("owner"), tag.getInt("trips"));
    }
}
