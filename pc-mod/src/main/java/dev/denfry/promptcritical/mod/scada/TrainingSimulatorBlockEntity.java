package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.sim.automation.twin.Fault;
import dev.denfry.promptcritical.sim.automation.twin.Scenario;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioCatalog;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioRun;
import dev.denfry.promptcritical.sim.automation.twin.TwinSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Вычислительный шкаф тренажёра {@code training_simulator} (11 §11.3.2): изолированная копия эталонной установки, на
 * которой идёт урок из каталога 11 §11.3.4. Управляют им место инструктора (урок, пуск, скорость, отказы, допуск) и
 * пульт оператора (механизмы, снятие форсировок) в {@value #LINK_RADIUS} блоках. Мир не меняется (C4, T11.6).
 *
 * <p>Обучаемые — игроки, работавшие за пультом во время урока (иначе — запустивший инструктор). Пройденный урок —
 * событие {@code promptcritical:scenario_passed} (задания маршрута {@code scenario}, 11 §11.1.5) и лучший балл игрока
 * в журнале шкафа; по нему место инструктора выдаёт удостоверение {@code operator_license} (11 §11.3.5). Разбор после
 * урока — журнал двойника в чат обучаемым. Одновременно на сервере — не больше {@code content.max_simulators_running}
 * шкафов (T11.8).
 */
public class TrainingSimulatorBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    /** Вычислительный шкаф тренажёра, Вт: прибор питается от розетки, щита или ИБП рядом, без питания останавливает сценарий. */
    public static final double POWER_W = 2e3;
    public static final int LINK_RADIUS = 8;
    public static final String SCENARIO_PASSED = "promptcritical:scenario_passed";

    static final Tr LIMIT = PanelText.tr("twin.msg.sim_limit", "All the server's simulators are busy (%s): try later",
            "Все тренажёры сервера заняты (%s): попробуйте позже"),
            STARTED = PanelText.tr("twin.msg.lesson_started", "Lesson started: %s", "Урок начат: %s"),
            LESSON_END = PanelText.tr("twin.msg.lesson_end", "Lesson %s: %s, score %s. %s", "Урок %s: %s, балл %s. %s"),
            DEBRIEF = PanelText.tr("twin.msg.debrief", "Debrief:", "Разбор:");

    private int lesson, speed = 1, status, faultIdx;
    private double score;
    @Nullable
    private ScenarioRun run;
    private long startedTick;
    @Nullable
    private UUID instructor;
    private final Set<UUID> trainees = new LinkedHashSet<>();
    /** Лучший балл игрока по урокам. */
    private final Map<UUID, Map<String, Double>> best = new HashMap<>();

    public TrainingSimulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ScadaBlockEntities.TRAINING_SIMULATOR.get(), pos, state);
    }

    static List<String> lessons() {
        return new ArrayList<>(ScenarioCatalog.LESSONS.keySet());
    }

    public String lessonId() {
        return lessons().get(lesson);
    }

    @Nullable
    public ScenarioRun run() {
        return run;
    }

    public int status() {
        return status;
    }

    /** Лучшие баллы игрока: урок → балл. */
    public Map<String, Double> bestOf(UUID player) {
        return best.getOrDefault(player, Map.of());
    }

    @Nullable
    static TrainingSimulatorBlockEntity nearest(Level level, BlockPos pos) {
        TrainingSimulatorBlockEntity found = null;
        double bestD = Double.MAX_VALUE;
        for (TrainingSimulatorBlockEntity t : Ironworks.blockEntities(level, new AABB(pos).inflate(LINK_RADIUS), TrainingSimulatorBlockEntity.class)) {
            double d = t.getBlockPos().distSqr(pos);
            if (d < bestD) {
                bestD = d;
                found = t;
            }
        }
        return found;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        if (run != null && !dev.denfry.promptcritical.mod.grid.MachinePower.powered(level, worldPosition, POWER_W)) return;
        if (run != null) {
            TwinSessions.advance(sl.getServer(), run, speed);
            if (run.status() != ScenarioRun.Status.RUNNING) finish(sl);
            else if (sl.getGameTime() - startedTick > TwinSessions.maxTicks()) {
                TwinSessions.say(sl, worldPosition, TwinWorkstationBlockEntity.TIMEOUT.get());
                stop();
            }
        }
        BlockState s = getBlockState();
        boolean running = run != null;
        if (s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING) != running)
            sl.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, running), Block.UPDATE_CLIENTS);
    }

    // ------------------------------------------------------------------ инструктор

    public void nextLesson() {
        if (run != null) return;
        lesson = (lesson + 1) % lessons().size();
        status = TwinPanels.IDLE;
        score = 0;
        setChanged();
    }

    public boolean start(@Nullable ServerPlayer by) {
        if (!(level instanceof ServerLevel sl)) return false;
        stop();
        if (!TwinSessions.acquire(this, TwinSessions.Kind.SIMULATOR)) {
            if (by != null) by.displayClientMessage(LIMIT.get(TwinSessions.running(TwinSessions.Kind.SIMULATOR)), false);
            return false;
        }
        Scenario s = ScenarioCatalog.LESSONS.get(lessonId());
        run = ScenarioRun.start(s, null, sl.getSeed() ^ worldPosition.asLong() ^ sl.getGameTime());
        run.session().setSpeed(speed);
        startedTick = sl.getGameTime();
        status = TwinPanels.RUN;
        faultIdx = 0;
        trainees.clear();
        instructor = by == null ? null : by.getUUID();
        TwinSessions.say(sl, worldPosition, STARTED.get(TwinPanels.LESSON_TEXT[lesson].get()));
        setChanged();
        return true;
    }

    public void cycleSpeed() {
        speed = TwinPanels.nextSpeed(speed);
        if (run != null) run.session().setSpeed(speed);
        setChanged();
    }

    /** Отказ от инструктора во время урока (11 §11.3.3). */
    @Nullable
    public Fault injectNext() {
        if (run == null) return null;
        List<Fault> faults = TwinSessions.manualFaults(run.session().plant().template());
        Fault f = faults.get(faultIdx++ % faults.size());
        run.session().inject(f);
        return f;
    }

    public void abort() {
        stop();
        status = TwinPanels.IDLE;
        setChanged();
    }

    // ------------------------------------------------------------------ оператор

    /**
     * Кнопка пульта: механизм {@code index}, {@code up} — «+». Дискретный механизм — включить/выключить, задатчик —
     * импульс, остальные — шаг 10 % диапазона.
     */
    public void operate(ServerPlayer player, int index, boolean up) {
        if (run == null) return;
        TwinSession s = run.session();
        List<String> act = s.plant().actuators();
        if (index < 0 || index >= act.size()) return;
        trainees.add(player.getUUID());
        String a = act.get(index);
        double[] r = s.plant().range(a);
        if (r[0] < 0) s.pulse(a, up ? 1 : -1);
        else if (s.plant().discrete(a)) s.command(a, up ? r[1] : r[0]);
        else s.command(a, s.operatorCommand(a) + (up ? 0.1 : -0.1) * (r[1] - r[0]));
    }

    public void clearForces(ServerPlayer player) {
        if (run == null) return;
        trainees.add(player.getUUID());
        run.session().clearForces();
    }

    // ------------------------------------------------------------------ итог

    private void finish(ServerLevel sl) {
        ScenarioRun r = run;
        if (r == null) return;
        boolean ok = r.status() == ScenarioRun.Status.PASSED;
        status = ok ? TwinPanels.PASSED : TwinPanels.FAILED;
        score = r.score();
        String id = r.scenario().id();
        TwinSessions.say(sl, worldPosition, LESSON_END.get(TwinPanels.LESSON_TEXT[lesson].get(), TwinPanels.STATUS_TEXT[status].get(),
                PanelText.num(score, 2), Component.literal(r.reason())));
        Set<UUID> credited = new LinkedHashSet<>(trainees);
        if (credited.isEmpty() && instructor != null) credited.add(instructor);
        for (UUID u : credited) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(u);
            if (ok) best.computeIfAbsent(u, k -> new HashMap<>()).merge(id, score, Math::max);
            if (p == null) continue;
            p.displayClientMessage(DEBRIEF.get(), false);
            for (String line : r.session().log()) p.displayClientMessage(Component.literal(line), false);
            if (ok) SimAchievements.direct(p, SCENARIO_PASSED, id, score);
        }
        stop();
        setChanged();
    }

    private void stop() {
        if (run != null) run.close();
        run = null;
        TwinSessions.release(this);
    }

    // ------------------------------------------------------------------ щит

    void fill(int[] out) {
        TwinPanels.fill(out, run, run == null ? plantIndex() : 0, status, score);
        out[TwinPanels.SPEED] = speed;
        out[TwinPanels.LESSON] = lesson;
        if (run == null) out[TwinPanels.REMAIN] = (int) ScenarioCatalog.LESSONS.get(lessonId()).durationS();
    }

    private int plantIndex() {
        return Math.max(0, dev.denfry.promptcritical.sim.automation.twin.TwinPlants.IDS.indexOf(ScenarioCatalog.LESSONS.get(lessonId()).plant()));
    }

    @Override
    public PanelLayout panelLayout() {
        return TwinPanels.SIMULATOR;
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
        fill(out);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
    }

    // ------------------------------------------------------------------ руки и сохранение

    @Override
    public void setRemoved() {
        super.setRemoved();
        stop();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        stop();
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
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
        tag.putInt("lesson", lesson);
        tag.putInt("speed", speed);
        tag.putInt("status", status == TwinPanels.RUN ? TwinPanels.IDLE : status);
        tag.putDouble("score", score);
        CompoundTag records = new CompoundTag();
        best.forEach((u, m) -> {
            CompoundTag one = new CompoundTag();
            m.forEach(one::putDouble);
            records.put(u.toString(), one);
        });
        tag.put("records", records);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lesson = Math.floorMod(tag.getInt("lesson"), lessons().size());
        speed = tag.contains("speed") ? tag.getInt("speed") : 1;
        status = tag.getInt("status");
        score = tag.getDouble("score");
        best.clear();
        CompoundTag records = tag.getCompound("records");
        for (String k : records.getAllKeys()) {
            Map<String, Double> m = new HashMap<>();
            CompoundTag one = records.getCompound(k);
            for (String l : one.getAllKeys()) m.put(l, one.getDouble(l));
            try {
                best.put(UUID.fromString(k), m);
            } catch (IllegalArgumentException ignored) {
                // повреждённая запись журнала — пропустить
            }
        }
    }

    /** Установка урока — для проверки допуска. */
    static String plantOf(String lessonId) {
        Scenario s = ScenarioCatalog.LESSONS.get(lessonId);
        return s == null ? "" : s.plant();
    }
}
