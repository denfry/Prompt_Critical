package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.automation.PlcBlockEntity;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.automation.plc.PlcCpu;
import dev.denfry.promptcritical.sim.automation.twin.Challenge;
import dev.denfry.promptcritical.sim.automation.twin.ChallengeCatalog;
import dev.denfry.promptcritical.sim.automation.twin.ChallengeRun;
import dev.denfry.promptcritical.sim.automation.twin.Fault;
import dev.denfry.promptcritical.sim.automation.twin.Scenario;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioCatalog;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioRun;
import dev.denfry.promptcritical.sim.automation.twin.TwinPlants;
import java.util.List;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Инженерная станция {@code simulation_workstation} (03d §3d.9.1, A5): цифровой двойник для заводских испытаний (FAT)
 * программы ПЛК. Кнопка FAT берёт программу ближайшего ПЛК в {@value #RADIUS} блоках и прогоняет её на копии выбранной
 * эталонной установки по испытанию {@link ScenarioCatalog#FAT}: те же {@link PlcCpu} и домен, возмущения и отказы по
 * времени, показатели контура. Инструктор может добавить отказ кнопкой. Двойник не трогает мир. Прошедшую FAT
 * программу кнопка «В ПЛК» загружает в реальный ПЛК и ставит его в RUN — достижение {@code digital_twin}
 * (11 §11.2.2). Изменённая после испытаний программа требует нового FAT.
 *
 * <p>Кнопка «Задача» — задача наладчика 03d §3d.11 на выбранной установке ({@link ChallengeCatalog#forPlant}): та же
 * программа ближайшего ПЛК, эталонное зерно, жёсткие условия и метрики; прошедшая попытка сразу идёт в таблицу лидеров
 * сервера и в достижения ({@link TunerChallenges}).
 */
public class TwinWorkstationBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    /** Рабочая станция двойника, Вт: прибор питается от розетки, щита или ИБП рядом, без питания останавливает сценарий. */
    public static final double POWER_W = 1e3;
    public static final int RADIUS = 16;
    /** Событие {@code sim_event}: прошедшая FAT программа загружена в ПЛК; тег — шаблон установки. */
    public static final String DIGITAL_TWIN = "promptcritical:digital_twin";

    static final Tr NO_PLC = PanelText.tr("twin.msg.no_plc", "No PLC with a program within 16 blocks",
            "В 16 блоках нет ПЛК с программой"),
            LIMIT = PanelText.tr("twin.msg.limit", "The server's twin limit is reached: try later",
                    "Предел двойников на сервере исчерпан: попробуйте позже"),
            STARTED = PanelText.tr("twin.msg.fat_started", "FAT started: %s", "FAT начат: %s"),
            RESULT = PanelText.tr("twin.msg.result", "%s: %s, score %s. %s", "%s: %s, балл %s. %s"),
            NOT_PASSED = PanelText.tr("twin.msg.not_passed", "Deploy refused: the PLC's program has not passed FAT on this plant",
                    "Загрузка отклонена: программа ПЛК не прошла FAT на этой установке"),
            DEPLOYED = PanelText.tr("twin.msg.deployed", "Tested program loaded into the PLC, key to RUN",
                    "Испытанная программа загружена в ПЛК, ключ в RUN"),
            FAULT = PanelText.tr("twin.msg.fault", "Fault injected: %s", "Введён отказ: %s"),
            TIMEOUT = PanelText.tr("twin.msg.timeout", "Twin session closed: time limit", "Сеанс двойника закрыт: предел времени");
    // сообщения задачи наладчика объявлены в TwinPanels: так они попадают в языковые файлы datagen
    private static final Tr NO_CHALLENGE = TwinPanels.MSG_NO_CHALLENGE, CH_STARTED = TwinPanels.MSG_STARTED,
            CH_METRIC_MSG = TwinPanels.MSG_METRIC, CH_TOP = TwinPanels.MSG_TOP;

    private int plant, speed = 1, status, faultIdx;
    private double score;
    @Nullable
    private ScenarioRun run;
    private long startedTick;
    /** Программа, прошедшая FAT, и шаблон, на котором прошла. */
    private String passedSource = "", passedTemplate = "";
    /** Попытка задачи наладчика ({@link #run} — её прогон) и кто её начал. */
    @Nullable
    private ChallengeRun challenge;
    @Nullable
    private ServerPlayer challenger;
    /** Итог последней задачи: место и участников по первой метрике, её значение; 0 — не было. */
    private int rank, total;
    private double metric0;

    public TwinWorkstationBlockEntity(BlockPos pos, BlockState state) {
        super(ScadaBlockEntities.TWIN_WORKSTATION.get(), pos, state);
    }

    public String template() {
        return TwinPlants.IDS.get(plant);
    }

    @Nullable
    public ScenarioRun run() {
        return run;
    }

    public int status() {
        return status;
    }

    public boolean deployable() {
        PlcBlockEntity plc = nearestPlc();
        return plc != null && !passedSource.isEmpty() && passedSource.equals(source(plc));
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || run == null || !dev.denfry.promptcritical.mod.grid.MachinePower.powered(level, worldPosition, POWER_W)) return;
        TwinSessions.advance(sl.getServer(), run, speed);
        if (run.status() != ScenarioRun.Status.RUNNING) finish(null);
        else if (sl.getGameTime() - startedTick > TwinSessions.maxTicks()) {
            TwinSessions.say(sl, worldPosition, TIMEOUT.get());
            stop();
        }
    }

    private void finish(@Nullable Component extra) {
        ScenarioRun r = run;
        if (r == null) return;
        boolean ok = r.status() == ScenarioRun.Status.PASSED;
        status = ok ? TwinPanels.PASSED : TwinPanels.FAILED;
        score = r.score();
        ChallengeRun ch = challenge;
        if (ch != null) {
            if (level instanceof ServerLevel sl) finishChallenge(sl, ch);
        } else if (ok && r.program() != null) {
            passedSource = r.program().source();
            passedTemplate = r.scenario().plant();
        }
        if (level instanceof ServerLevel sl)
            TwinSessions.say(sl, worldPosition, RESULT.get(r.scenario().id(), TwinPanels.STATUS_TEXT[status].get(),
                    PanelText.num(score, 2), Component.literal(r.reason())));
        stop();
        setChanged();
    }

    private void finishChallenge(ServerLevel sl, ChallengeRun ch) {
        ServerPlayer p = challenger;
        challenger = null;
        if (p == null || p.hasDisconnected() || ch.status() != ScenarioRun.Status.PASSED) return;
        List<TunerChallenges.Result> results = TunerChallenges.submit(p, ch);
        for (int i = 0; i < results.size(); i++) {
            TunerChallenges.Result res = results.get(i);
            if (i == 0) {
                rank = res.placement().rank();
                total = res.placement().total();
                metric0 = res.value();
            }
            tell(p, CH_METRIC_MSG.get(res.metric().id(), PanelText.num(res.value(), 3), res.placement().rank(), res.placement().total(),
                    res.placement().topTenth() ? CH_TOP.get() : Component.empty()));
        }
    }

    private void stop() {
        if (run != null) run.close();
        run = null;
        challenge = null;
        challenger = null;
        TwinSessions.release(this);
    }

    @Nullable
    PlcBlockEntity nearestPlc() {
        if (level == null) return null;
        PlcBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (PlcBlockEntity p : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), PlcBlockEntity.class)) {
            AutomationUnit u = p.unit();
            if (u == null || u.plc() == null) continue;
            double d = p.getBlockPos().distSqr(worldPosition);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static String source(PlcBlockEntity p) {
        AutomationUnit u = p.unit();
        return u == null || u.plc() == null ? "" : u.plc().program().source();
    }

    /** Запустить FAT программы ближайшего ПЛК; сообщение — игроку. */
    public void startFat(@Nullable ServerPlayer player) {
        if (!(level instanceof ServerLevel sl)) return;
        PlcBlockEntity plc = nearestPlc();
        String src = plc == null ? "" : source(plc);
        if (src.isBlank()) {
            tell(player, NO_PLC.get());
            return;
        }
        stop();
        if (!TwinSessions.acquire(this, TwinSessions.Kind.TWIN)) {
            tell(player, LIMIT.get());
            return;
        }
        Scenario s = ScenarioCatalog.FAT.get(template());
        run = ScenarioRun.start(s, src, sl.getSeed() ^ worldPosition.asLong() ^ sl.getGameTime());
        run.session().setSpeed(speed);
        startedTick = sl.getGameTime();
        status = TwinPanels.RUN;
        faultIdx = 0;
        tell(player, STARTED.get(s.id()));
        if (run.status() != ScenarioRun.Status.RUNNING) finish(null);
    }

    /** Начать задачу наладчика выбранной установки с программой ближайшего ПЛК. */
    public void startChallenge(@Nullable ServerPlayer player) {
        if (!(level instanceof ServerLevel)) return;
        Challenge c = ChallengeCatalog.forPlant(template());
        if (c == null) {
            tell(player, NO_CHALLENGE.get());
            return;
        }
        PlcBlockEntity plc = nearestPlc();
        String src = plc == null ? "" : source(plc);
        if (src.isBlank()) {
            tell(player, NO_PLC.get());
            return;
        }
        stop();
        if (!TwinSessions.acquire(this, TwinSessions.Kind.TWIN)) {
            tell(player, LIMIT.get());
            return;
        }
        challenge = ChallengeRun.start(c, src);
        challenger = player;
        run = challenge.run();
        run.session().setSpeed(speed);
        startedTick = level.getGameTime();
        status = TwinPanels.RUN;
        rank = total = 0;
        faultIdx = 0;
        tell(player, CH_STARTED.get(c.id()));
        if (run.status() != ScenarioRun.Status.RUNNING) finish(null);
    }

    /** Попытка задачи наладчика идёт. */
    public boolean challengeRunning() {
        return challenge != null;
    }

    /** Итог последней задачи: место по первой метрике (0 — не было) и её значение. */
    public int challengeRank() {
        return rank;
    }

    public double challengeMetric() {
        return metric0;
    }

    /** Загрузить прошедшую FAT программу в реальный ПЛК. */
    public boolean deploy(@Nullable ServerPlayer player) {
        PlcBlockEntity plc = nearestPlc();
        if (plc == null || passedSource.isEmpty() || !passedSource.equals(source(plc)) || !passedTemplate.equals(template())) {
            tell(player, NOT_PASSED.get());
            return false;
        }
        PlcCpu cpu = plc.unit().plc();
        cpu.load(passedSource);
        cpu.setRunKey(true);
        plc.setChanged();
        tell(player, DEPLOYED.get());
        if (player != null) SimAchievements.direct(player, DIGITAL_TWIN, passedTemplate, 1);
        return true;
    }

    private void tell(@Nullable ServerPlayer player, Component c) {
        if (player != null) player.displayClientMessage(c, false);
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return TwinPanels.WORKSTATION;
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
        TwinPanels.fill(out, run, plant, status, score);
        out[TwinPanels.SPEED] = speed;
        out[TwinPanels.CH_RANK] = rank;
        out[TwinPanels.CH_TOTAL] = total;
        out[TwinPanels.CH_METRIC] = (int) Math.round(Math.max(-2e6, Math.min(2e6, metric0)) * 1000);
        if (deployable() && passedTemplate.equals(template())) out[TwinPanels.FLAGS] |= 1 << TwinPanels.F_DEPLOYABLE;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case TwinPanels.W_PLANT -> {
                if (run != null) return;
                plant = (plant + 1) % TwinPlants.IDS.size();
                status = TwinPanels.IDLE;
                score = 0;
            }
            case TwinPanels.W_FAT -> startFat(player);
            case TwinPanels.W_CHALLENGE -> startChallenge(player);
            case TwinPanels.W_SPEED -> {
                speed = TwinPanels.nextSpeed(speed);
                if (run != null) run.session().setSpeed(speed);
            }
            case TwinPanels.W_FAULT -> {
                // в задаче наладчика возмущения задаёт сценарий, а не инструктор
                if (run == null || challenge != null) return;
                List<Fault> faults = TwinSessions.manualFaults(template());
                Fault f = faults.get(faultIdx++ % faults.size());
                run.session().inject(f);
                tell(player, FAULT.get(f.label()));
            }
            case TwinPanels.W_STOP -> {
                stop();
                status = TwinPanels.IDLE;
            }
            case TwinPanels.W_DEPLOY -> deploy(player);
            default -> {
            }
        }
        setChanged();
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
        tag.putInt("plant", plant);
        tag.putInt("speed", speed);
        // прогон в памяти не сохраняется: после загрузки — итог прошлого прогона или «нет прогона»
        tag.putInt("status", status == TwinPanels.RUN ? TwinPanels.IDLE : status);
        tag.putDouble("score", score);
        tag.putString("passed_source", passedSource);
        tag.putString("passed_template", passedTemplate);
        tag.putInt("challenge_rank", rank);
        tag.putInt("challenge_total", total);
        tag.putDouble("challenge_metric", metric0);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        plant = Math.floorMod(tag.getInt("plant"), TwinPlants.IDS.size());
        speed = tag.contains("speed") ? tag.getInt("speed") : 1;
        status = tag.getInt("status");
        score = tag.getDouble("score");
        passedSource = tag.getString("passed_source");
        passedTemplate = tag.getString("passed_template");
        rank = tag.getInt("challenge_rank");
        total = tag.getInt("challenge_total");
        metric0 = tag.getDouble("challenge_metric");
    }
}
