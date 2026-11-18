package dev.denfry.promptcritical.mod.acoustics;

import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimConfigs;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.acoustics.BearingGeometry;
import dev.denfry.promptcritical.sim.acoustics.MachineClass;
import dev.denfry.promptcritical.sim.acoustics.RollingBearing;
import dev.denfry.promptcritical.sim.acoustics.RotatingMachine;
import dev.denfry.promptcritical.sim.acoustics.SoundLevel;
import dev.denfry.promptcritical.sim.acoustics.VibrationSpectrum;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Вибродиагностика вращающихся машин мира (04a §4a.6–4a.7) без привязки к конкретной машине: любой блок Create с
 * вращением ({@link KineticBlockEntity}) — двигатели, насосы, вентиляторы, прессы, станки мода, генераторы.
 *
 * <ul>
 *   <li>Скорость машины — вал Create через редуктор: 256 об/мин Create → 1000 об/мин машины (03a, передаточное
 *       {@code pumps.primary.gear_ratio} = 3,906); номинал — 1000 об/мин.</li>
 *   <li>Мощность на валу: потребитель — W_PER_SU·impact·|n|; источник — его доля фактической нагрузки сети,
 *       W_PER_SU·min(S, C)·C_own/C (как у порта паровой машины, 03a §3a.7.3). По мощности — класс ISO 10816.</li>
 *   <li>Лопатки: насос Create — 6 (центробежное колесо 5–7 лопаток), вентилятор — 8 [BALANCE]; зубья: малая
 *       шестерня — 8, большая — 16 (передаточное Create 2:1) [BALANCE].</li>
 *   <li>Состояние экземпляра (дисбаланс, центровка, u_b и D_b подшипника) хранится в {@code SavedData} измерения по
 *       клетке; монтажный разброс — от зерна мира и клетки, поэтому повторим.</li>
 * </ul>
 */
public final class MachineAcoustics {
    /** Об/мин машины на 1 об/мин вала Create. */
    public static final double GEAR_RATIO = 1000.0 / 256;
    public static final double NOMINAL_RPM = 1000;
    public static final int PUMP_BLADES = 6, FAN_BLADES = 8, SMALL_COG_TEETH = 8, LARGE_COG_TEETH = 16;

    private MachineAcoustics() {
    }

    /**
     * Машина в клетке на этот момент.
     *
     * @param rpm       скорость вала машины, об/мин (≥ 0)
     * @param powerW    мощность на валу, Вт
     * @param generator источник вращения (податливый фундамент для класса IV)
     * @param machine   потребитель или источник (вал и шестерни сами по себе — не машины, не шумят и не изнашиваются)
     */
    public record Probe(KineticBlockEntity be, double rpm, double powerW, boolean generator, int blades, int teeth,
            boolean machine) {
        public boolean running() {
            return rpm > 0;
        }

        public MachineClass machineClass() {
            return MachineClass.forPower(powerW, generator);
        }

        public BearingGeometry geometry() {
            return machineClass() == MachineClass.I ? BearingGeometry.SKF_6205 : BearingGeometry.LARGE;
        }
    }

    /** Машина в клетке; null — там нет вращающегося блока Create. */
    @Nullable
    public static Probe probe(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof KineticBlockEntity k)) return null;
        double createRpm = Math.abs(k.getSpeed());
        double wPerSu = ServerConfig.W_PER_SU.get();
        // только чтение из сети: calculateStressApplied()/calculateAddedStressCapacity() Create меняют поля BE,
        // по которым машины мода решают, сообщать ли сети новую нагрузку
        KineticNetwork net = !level.isClientSide && k.hasNetwork() ? k.getOrCreateNetwork() : null;
        Float impact = net == null ? null : net.members.get(k);
        Float own = net == null ? null : net.sources.get(k);
        boolean source = own != null && own > 0;
        boolean consumer = impact != null && impact > 0;
        double power = 0;
        if (source && createRpm > 0) {
            double cap = net.calculateCapacity(), stress = net.calculateStress();
            if (cap > 0) power = wPerSu * Math.min(stress, cap) * net.getActualCapacityOf(k) / cap;
        } else if (consumer) {
            power = wPerSu * net.getActualStressOf(k);
        }
        int blades = be instanceof PumpBlockEntity ? PUMP_BLADES : be instanceof EncasedFanBlockEntity ? FAN_BLADES : 0;
        var state = be.getBlockState();
        int teeth = ICogWheel.isLargeCog(state) ? LARGE_COG_TEETH : ICogWheel.isSmallCog(state) ? SMALL_COG_TEETH : 0;
        return new Probe(k, k.isOverStressed() ? 0 : createRpm * GEAR_RATIO, power, source, blades, teeth, source || consumer);
    }

    // --- состояние экземпляров ---

    /** Что хранится по машине. */
    static final class State {
        double imbalance, misalignmentMm, lifeFactor, damage;
        /** Тик мира последнего шага износа. */
        long lastTick;
    }

    static final class Data extends SavedData {
        final Map<Long, State> machines = new HashMap<>();

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            ListTag list = tag.getList("machines", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                State s = new State();
                s.imbalance = c.getDouble("u");
                s.misalignmentMm = c.getDouble("delta");
                s.lifeFactor = c.getDouble("ub");
                s.damage = c.getDouble("d");
                s.lastTick = c.getLong("t");
                if (s.lifeFactor > 0) d.machines.put(c.getLong("pos"), s);
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Map.Entry<Long, State> e : machines.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putLong("pos", e.getKey());
                c.putDouble("u", e.getValue().imbalance);
                c.putDouble("delta", e.getValue().misalignmentMm);
                c.putDouble("ub", e.getValue().lifeFactor);
                c.putDouble("d", e.getValue().damage);
                c.putLong("t", e.getValue().lastTick);
                list.add(c);
            }
            tag.put("machines", list);
            return tag;
        }
    }

    static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null),
                PromptCritical.MOD_ID + "_machine_vibration");
    }

    private static SplittableRandom random(ServerLevel level, BlockPos pos) {
        return new SplittableRandom(level.getSeed() ^ pos.asLong() * 0x9E3779B97F4A7C15L);
    }

    static State state(ServerLevel level, BlockPos pos) {
        Data d = data(level);
        State s = d.machines.get(pos.asLong());
        if (s != null) return s;
        // монтаж: тот же разброс, что у RotatingMachine.installed
        RotatingMachine fresh = RotatingMachine.installed(MachineClass.I, BearingGeometry.SKF_6205, NOMINAL_RPM, 0, 0,
                random(level, pos));
        s = new State();
        s.imbalance = fresh.imbalance();
        s.misalignmentMm = fresh.misalignmentMm();
        s.lifeFactor = fresh.bearing().lifeFactor();
        s.lastTick = level.getGameTime();
        d.machines.put(pos.asLong(), s);
        d.setDirty();
        return s;
    }

    /** Машину убрали или она разрушена: новая на этом месте ставится с новым подшипником. */
    public static void forget(ServerLevel level, BlockPos pos) {
        Data d = data(level);
        if (d.machines.remove(pos.asLong()) != null) d.setDirty();
    }

    /** Модель машины из сохранённого состояния и текущего вида (класс по мощности). */
    static RotatingMachine model(Probe p, State s) {
        return new RotatingMachine(p.machineClass(), p.geometry(), NOMINAL_RPM, p.blades(), p.teeth(), s.imbalance,
                s.misalignmentMm, new RollingBearing(s.lifeFactor, s.damage));
    }

    private static void store(ServerLevel level, BlockPos pos, RotatingMachine m) {
        State s = state(level, pos);
        s.imbalance = m.imbalance();
        s.misalignmentMm = m.misalignmentMm();
        s.lifeFactor = m.bearing().lifeFactor();
        s.damage = m.bearing().damage();
        data(level).setDirty();
    }

    /** Спектр машины в клетке; null — там нет вращающегося блока. */
    @Nullable
    public static VibrationSpectrum spectrum(ServerLevel level, BlockPos pos) {
        Probe p = probe(level, pos);
        if (p == null) return null;
        return model(p, state(level, pos)).spectrum(p.rpm(), 1);
    }

    /** Повреждение подшипника D_b машины (для тестов и обслуживания); новая — 0. */
    public static double bearingDamage(ServerLevel level, BlockPos pos) {
        return state(level, pos).damage;
    }

    /** Задать D_b (сценарии и тесты). */
    public static void setBearingDamage(ServerLevel level, BlockPos pos, double damage) {
        state(level, pos).damage = Math.clamp(damage, 0, 1);
        data(level).setDirty();
    }

    public static double imbalance(ServerLevel level, BlockPos pos) {
        return state(level, pos).imbalance;
    }

    public static double misalignmentMm(ServerLevel level, BlockPos pos) {
        return state(level, pos).misalignmentMm;
    }

    /**
     * Шаг износа машины от прошлого шага: физическое время — реальное × k_slow, ускорение — {@code
     * acoustics.bearing_wear_multiplier}. Пропуск дольше минуты (чанк выгружен, сервер стоял) не засчитывается.
     *
     * @return true, если подшипник заклинило
     */
    public static boolean stepWear(ServerLevel level, BlockPos pos, Probe p) {
        State s = state(level, pos);
        long now = level.getGameTime();
        long dtTicks = Math.min(now - s.lastTick, 20 * 60);
        s.lastTick = now;
        if (dtTicks <= 0 || !p.running()) return false;
        RotatingMachine m = model(p, s);
        double dtPhys = dtTicks / 20.0 * ServerConfig.K_SLOW.get();
        boolean seized = m.stepWear(dtPhys, p.rpm(), SimConfigs.ops().bearingWearMultiplier());
        store(level, pos, m);
        return seized;
    }

    /** Шаг износа машины в клетке (служба шума делает его раз в секунду; тестам — напрямую). */
    public static boolean stepWear(ServerLevel level, BlockPos pos) {
        Probe p = probe(level, pos);
        return p != null && p.machine() && stepWear(level, pos, p);
    }

    /** L_WA машины, дБ(А): по мощности и состоянию подшипника; стоящая — −∞. */
    public static double soundPowerDb(ServerLevel level, BlockPos pos, Probe p) {
        if (!p.machine() || !p.running()) return Double.NEGATIVE_INFINITY;
        State s = state(level, pos);
        return SoundLevel.machineLw(p.powerW(), new RollingBearing(s.lifeFactor, s.damage));
    }

    // --- обслуживание (04a §4a.7.5) ---

    public enum Service {
        /** Замена подшипника: вал стоит. */
        BEARING(false),
        /** Балансировка пробным грузом: машина в работе. */
        BALANCE(true),
        /** Центровка: вал стоит. */
        ALIGN(false);

        /** Нужна ли работа на ходу (иначе — вал должен стоять). */
        public final boolean running;

        Service(boolean running) {
            this.running = running;
        }
    }

    /** Можно ли выполнить работы на машине сейчас. */
    public static boolean canService(Level level, BlockPos pos, Service kind) {
        return level.getBlockEntity(pos) instanceof KineticBlockEntity k && (k.getSpeed() != 0 && !k.isOverStressed()) == kind.running;
    }

    /**
     * Выполнить работы.
     *
     * @return false — условие не выполнено (машины нет, вал не в том состоянии)
     */
    public static boolean service(ServerLevel level, BlockPos pos, Service kind) {
        Probe p = probe(level, pos);
        if (p == null || p.running() != kind.running) return false;
        RotatingMachine m = model(p, state(level, pos));
        switch (kind) {
            case BEARING -> m.replaceBearing(new SplittableRandom(level.getSeed() ^ level.getGameTime() ^ pos.asLong()));
            case BALANCE -> m.balance();
            case ALIGN -> m.align();
        }
        store(level, pos, m);
        return true;
    }
}
