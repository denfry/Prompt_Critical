package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.InverterUnit;
import dev.denfry.promptcritical.mod.safety.PortableVentilatorBlockEntity;
import dev.denfry.promptcritical.sim.ops.ConfinedSpaceAtmosphere;
import dev.denfry.promptcritical.sim.storage.BatteryRoom;
import dev.denfry.promptcritical.sim.storage.GridBattery;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Батарейный инвертор {@code battery_inverter} (15 §15.7.1, §15.9): инвертор 500 кВт на шине 6,3 кВ за стеллажами
 * {@code grid_battery_rack} (модуль 250 кВт·ч) в {@value #RANGE} блоках вокруг, до 40 модулей. Режимы: отключён, заряд,
 * разряд и регулирование частоты — статизм {@value #DROOP} и синтетическая инерция H = {@value #SYNTHETIC_H_S} с;
 * в регулировании батарея медленно тянется к половине заряда. Инвертор реагирует за 0,2 с — быстрее любого регулятора
 * турбины. ПКМ — следующий режим, Shift + ПКМ — состояние.
 *
 * <p>Водород при заряде выше 90 % выходит в помещение ({@link BatteryRoom}): объём — воздух, связанный с инвертором
 * (до {@value #ROOM_MAX_M3} м³), воздухообмен — {@value BatteryRoom#ACH_SEALED} 1/ч в закрытом помещении,
 * {@value BatteryRoom#ACH_OPEN} 1/ч при проёме к небу или в большом зале, плюс работающие переносные вентиляторы
 * в помещении. С {@value BatteryRoom#ALARM_LEL} НКПР звенит газоанализатор, при смеси выше НКПР искра контактора
 * во время заряда её поджигает.
 */
public class BatteryInverterBlockEntity extends GridDeviceBlockEntity<InverterUnit> {
    public static final double RATED_W = 500e3, EFFICIENCY = 0.97;
    public static final int RANGE = 4;
    /** Статизм регулирования частоты: полная мощность при отклонении на 4 % (2 Гц). */
    public static final double DROOP = 0.04;
    public static final double SYNTHETIC_H_S = 2;
    static final int RESCAN_TICKS = 100;
    static final double DT = 0.05;
    public static final int ROOM_MAX_M3 = 4096;
    /** Сила вспышки на 1 кг водорода и её предел (взрыв ТНТ = 4). */
    static final float BLAST_PER_KG = 6, BLAST_MAX = 8;

    public enum Mode { OFF, CHARGE, DISCHARGE, REGULATE }

    private Mode mode = Mode.REGULATE;
    private GridBattery battery;
    private final BatteryRoom room = new BatteryRoom(ROOM_MAX_M3, BatteryRoom.ACH_OPEN);

    public BatteryInverterBlockEntity(BlockPos pos, BlockState state) {
        super(GridBlockEntities.BATTERY_INVERTER.get(), pos, state);
    }

    @Override
    protected InverterUnit unitOf(GridSubsystem grid) {
        InverterUnit u = grid.inverter(simId());
        u.configure(RATED_W, EFFICIENCY);
        return u;
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        for (Direction d : Direction.values()) if (d != facing()) out.add(new Face(worldPosition, d));
        return List.of(out);
    }

    public Mode dispatchMode() {
        return mode;
    }

    public void setMode(Mode m) {
        mode = m;
        setChanged();
    }

    public GridBattery battery() {
        return battery;
    }

    public BatteryRoom room() {
        return room;
    }

    /**
     * Помещение инвертора: воздух, связанный с ним по граням, до {@value #ROOM_MAX_M3} блоков. Выход к небу или
     * переполнение — открытое помещение.
     */
    public static void measureRoom(Level level, BlockPos origin, BatteryRoom room) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction d : Direction.values()) queue.add(origin.relative(d));
        boolean open = false;
        int fans = 0;
        while (!queue.isEmpty() && seen.size() < ROOM_MAX_M3) {
            BlockPos p = queue.poll();
            if (seen.contains(p) || !level.isLoaded(p)) continue;
            BlockState st = level.getBlockState(p);
            if (level.getBlockEntity(p) instanceof PortableVentilatorBlockEntity v && v.running()) fans++;
            if (!st.isAir()) continue;
            seen.add(p);
            // карта высот, а не небесный свет: свет пересчитывается с запаздыванием после стройки
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) <= p.getY()) open = true;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.contains(n) && n.distManhattan(origin) <= 64) queue.add(n);
            }
        }
        if (seen.size() >= ROOM_MAX_M3) open = true;
        double volume = Math.max(1, seen.size());
        double ach = (open ? BatteryRoom.ACH_OPEN : BatteryRoom.ACH_SEALED) + fans * ConfinedSpaceAtmosphere.VENTILATOR_M3S * 3600 / volume;
        room.resize(volume, ach);
    }

    private int countModules(ServerLevel sl) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-RANGE, -RANGE, -RANGE), worldPosition.offset(RANGE, RANGE, RANGE)))
            if (sl.isLoaded(p) && sl.getBlockState(p).is(PcBlocks.GRID_BATTERY_RACK.get())) n++;
        return Math.min(GridBattery.MAX_MODULES, n);
    }

    @Override
    protected void serverTick(InverterUnit u) {
        if (!(level instanceof ServerLevel sl)) return;
        u.configure(RATED_W, EFFICIENCY);
        if (ticks % RESCAN_TICKS == 1 || battery == null && ticks % 20 == 1) {
            int n = countModules(sl);
            if (n == 0) battery = null;
            else if (battery == null) battery = new GridBattery(n);
            else if (battery.modules() != n) battery = battery.withModules(n);
            measureRoom(sl, worldPosition, room);
        }
        if (battery == null) {
            u.setDispatch(0, 0, 0);
            if (ticks % 20 == 0) setFlag(BlockStateProperties.LIT, false);
            return;
        }
        if (u.source() != null) u.source().setSyntheticH(mode == Mode.REGULATE ? SYNTHETIC_H_S : 0);
        double maxOut = battery.maxDischargeW() * EFFICIENCY, maxIn = battery.maxChargeW() / EFFICIENCY;
        double f = u.frequencyHz();
        double demand = switch (mode) {
            case OFF -> 0;
            case CHARGE -> -RATED_W;
            case DISCHARGE -> RATED_W;
            case REGULATE -> f <= 0 ? 0 : -(f - 50) / (DROOP * 50) * RATED_W - 0.05 * RATED_W * (battery.soc() - 0.5) / 0.5;
        };
        u.setDispatch(demand, mode == Mode.OFF ? 0 : maxOut, mode == Mode.OFF ? 0 : maxIn);
        double ac = u.powerW();
        battery.step(DT, ac >= 0 ? ac / EFFICIENCY : ac * EFFICIENCY);
        room.step(DT, battery.takeHydrogenKg());
        if (room.explosive() && ac < -1e3) {
            float power = (float) Math.min(BLAST_MAX, 1 + BLAST_PER_KG * room.burn());
            sl.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, power,
                    true, Level.ExplosionInteraction.BLOCK);
            return;
        }
        if (room.alarm() && ticks % 40 == 0)
            sl.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1f, 1.6f);
        if (ticks % 20 == 0) {
            setFlag(BlockStateProperties.LIT, Math.abs(ac) > 1e3);
            setChanged();
        }
    }

    private String modeKey() {
        return switch (mode) {
            case OFF -> GridText.BATTERY_OFF;
            case CHARGE -> GridText.BATTERY_CHARGE;
            case DISCHARGE -> GridText.BATTERY_DISCHARGE;
            case REGULATE -> GridText.BATTERY_REGULATE;
        };
    }

    @Override
    public InteractionResult use(Player player) {
        InverterUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            GridBattery b = battery;
            player.displayClientMessage(GridText.msg(GridText.BATTERY_STATUS, b == null ? 0 : b.modules(),
                    b == null ? 0 : Math.round(b.soc() * 100), b == null ? 0 : Math.round(b.health() * 100),
                    b == null ? "0" : String.format("%.1f", b.cycles()), String.format("%.1f", u.powerW() / 1e3),
                    GridText.msg(modeKey()), Math.round(room.lelFraction() * 100), Math.round(room.volumeM3()),
                    String.format("%.1f", room.airChangesPerHour())), true);
            return InteractionResult.SUCCESS;
        }
        setMode(Mode.values()[(mode.ordinal() + 1) % Mode.values().length]);
        player.displayClientMessage(GridText.msg(GridText.BATTERY_MODE, GridText.msg(modeKey())), true);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, 1.1f);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("mode", mode.name());
        tag.putDouble("room_h2_m3", room.h2M3());
        if (battery != null) {
            tag.putInt("modules", battery.modules());
            tag.putDouble("soc", battery.soc());
            tag.putDouble("cycles", battery.cycles());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            mode = Mode.valueOf(tag.getString("mode"));
        } catch (IllegalArgumentException e) {
            mode = Mode.REGULATE;
        }
        room.setH2M3(tag.getDouble("room_h2_m3"));
        battery = tag.contains("modules") ? GridBattery.restore(tag.getInt("modules"), tag.getDouble("soc"), tag.getDouble("cycles")) : null;
    }
}
