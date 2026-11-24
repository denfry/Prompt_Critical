package dev.denfry.promptcritical.mod.hazard;

import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.pipeline.CathodicProtection;
import dev.denfry.promptcritical.sim.pipeline.Pig;
import dev.denfry.promptcritical.sim.pipeline.Pipeline;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Камеры запуска и приёма скребка ({@code pig_launcher}, {@code pig_receiver}) ограничивают участок трубопровода из
 * труб Create (09 §9.8.2, объект {@code pc_pipeline}). Участок — кратчайший путь по трубам, насосам и арматуре Create
 * от средней клетки камеры запуска до средней клетки камеры приёма (до {@value #TRACE_LIMIT} блоков; 1 блок = 1 м).
 * Труба участка — Ø{@value #OD_M}×{@value #WALL_M} м из X52 [моё: калибр трубы Create]; колена труб Create считаются
 * отводами 3D и больше (поршень их проходит), «умная» труба и насос — неполнопроходные (поршень встаёт).
 * <ul>
 *   <li><b>Скребок:</b> в открытую камеру запуска (давление стравлено) — ПКМ поршнем {@code pig} или {@code smart_pig},
 *   закрыть, поднять давление, «Пуск». Трубы Create не знают скорости потока: поршень идёт с номинальной скоростью
 *   {@value #FLOW_MS} м/с минус 5 % проскальзывания [моё]. Очистной снимает парафин (масса — на щите), диагностический
 *   пишет отчёт: дефекты глубже 10 % стенки с вероятностью 0,9 и погрешностью ±10 %. Отложения больше 20 % диаметра или
 *   неполнопроходная арматура — поршень застревает посередине; вынуть его — «Вырезка» при стравленном давлении.</li>
 *   <li><b>Дверца</b> под давлением не открывается (механическая блокировка, {@code PIPE_TRAP_DOOR_PRESSURE}).</li>
 *   <li><b>Отложения</b> парафина растут на {@value #DEPOSIT_PER_DAY} диаметра за игровые сутки, пока участок собран
 *   [BALANCE].</li>
 *   <li><b>Коррозия и катодная защита</b> (§9.8.3, {@link CathodicProtection}): у участка {@value #DEFECTS} исходных
 *   дефекта стенки (глубина 5–35 % [моё]); они растут со скоростью коррозии (SLOW, ×72): без защиты 0,2 мм/год, при
 *   потенциале «выкл» не выше −0,85 В — ×0,02. Ток — от станций катодной защиты в {@value #CP_RADIUS} блоках и от
 *   протекторов, установленных на камеру (ПКМ протектором, {@value #ANODE_KG} кг магния, R_земли {@value #ANODE_EARTH_OHM} Ом
 *   [моё]).</li>
 * </ul>
 * Давления в трубах Create нет, поэтому разрыв по Барлоу здесь не наступает: щит показывает давление разрушения по
 * самому глубокому найденному дефекту — для решения о ремонте.
 */
public class PigTrapBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final double OD_M = 0.426, WALL_M = 0.008, FLOW_MS = 1.0;
    public static final Pipeline.Grade GRADE = Pipeline.Grade.X52;
    public static final int TRACE_LIMIT = 4096, CP_RADIUS = 64, DEFECTS = 3;
    public static final double DEPOSIT_PER_DAY = 0.02, ANODE_KG = 10, ANODE_EARTH_OHM = 10, SOIL_IR_OHM = 1;
    /** 1 с игры — 72 с физики (00 §0.7), сутки игры — 1200 с. */
    static final double K_SLOW = 72, GAME_DAY_S = 1200, SECONDS_PER_YEAR = 365.25 * 86_400;
    public static final int NONE = 0, CLEANING = 1, SMART = 2;

    // участок (у камеры запуска)
    @Nullable
    private BlockPos receiver;
    private int lengthM;
    private boolean fullBore = true, vented = true;
    private int loaded, running, held;
    private double pigPosM, depositFraction, anodeKg, waxKg, currentA, requiredA, offV = CathodicProtection.NATURAL_V;
    private boolean stuck;
    private final List<Pig.Defect> defects = new ArrayList<>();
    private List<Pig.Defect> report = List.of();
    private boolean reportReady;
    private int ticks;

    public PigTrapBlockEntity(BlockPos pos, BlockState state) {
        super(HazardBlockEntities.PIG_TRAP.get(), pos, state);
    }

    public boolean launcher() {
        return getBlockState().is(PcBlocks.PIG_LAUNCHER.get());
    }

    public int lengthM() {
        return lengthM;
    }

    @Nullable
    public BlockPos receiver() {
        return receiver;
    }

    public boolean stuck() {
        return stuck;
    }

    public boolean pigRunning() {
        return running != NONE;
    }

    public int held() {
        return held;
    }

    public double depositFraction() {
        return depositFraction;
    }

    public void setDepositFraction(double f) {
        depositFraction = Math.clamp(f, 0, 1);
    }

    public double offV() {
        return offV;
    }

    public double currentA() {
        return currentA;
    }

    public double requiredA() {
        return requiredA;
    }

    public double waxKg() {
        return waxKg;
    }

    public List<Pig.Defect> report() {
        return report;
    }

    public boolean reportReady() {
        return reportReady;
    }

    public List<Pig.Defect> defects() {
        return defects;
    }

    boolean doorOpen() {
        BlockState s = getBlockState();
        return s.hasProperty(BlockStateProperties.OPEN) && s.getValue(BlockStateProperties.OPEN);
    }

    // ------------------------------------------------------------------ участок

    public static boolean pipe(BlockState s) {
        var b = s.getBlock();
        return b instanceof FluidPipeBlock || b instanceof EncasedPipeBlock || b instanceof GlassFluidPipeBlock || b instanceof FluidValveBlock
                || b instanceof SmartFluidPipeBlock || b instanceof PumpBlock;
    }

    static boolean fullBore(BlockState s) {
        return !(s.getBlock() instanceof SmartFluidPipeBlock || s.getBlock() instanceof PumpBlock);
    }

    /** Кратчайший путь по трубам Create до камеры приёма. */
    void trace(ServerLevel sl) {
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        for (Direction d : Direction.values()) {
            BlockPos n = worldPosition.relative(d);
            if (sl.isLoaded(n) && pipe(sl.getBlockState(n))) {
                parent.put(n, worldPosition);
                q.add(n);
            }
        }
        BlockPos found = null, last = null;
        while (!q.isEmpty() && parent.size() < TRACE_LIMIT && found == null) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.equals(worldPosition) || parent.containsKey(n) || !sl.isLoaded(n)) continue;
                if (sl.getBlockEntity(n) instanceof PigTrapBlockEntity t && !t.launcher()) {
                    found = n;
                    last = p;
                    break;
                }
                if (pipe(sl.getBlockState(n))) {
                    parent.put(n, p);
                    q.add(n);
                }
            }
        }
        receiver = found;
        if (found == null) {
            lengthM = 0;
            return;
        }
        int len = 0;
        boolean bore = true;
        for (BlockPos p = last; p != null && !p.equals(worldPosition); p = parent.get(p)) {
            len++;
            bore &= fullBore(sl.getBlockState(p));
        }
        lengthM = len;
        fullBore = bore;
        if (defects.isEmpty()) {
            SplittableRandom rnd = new SplittableRandom(sl.getSeed() ^ worldPosition.asLong());
            for (int i = 0; i < DEFECTS; i++) defects.add(new Pig.Defect(rnd.nextDouble() * len, 0.05 + 0.30 * rnd.nextDouble()));
        }
    }

    @Nullable
    PigTrapBlockEntity pairedReceiver(ServerLevel sl) {
        return receiver != null && sl.getBlockEntity(receiver) instanceof PigTrapBlockEntity t ? t : null;
    }

    /** Камера запуска, чей участок кончается этой камерой приёма. */
    @Nullable
    PigTrapBlockEntity pairedLauncher(ServerLevel sl) {
        for (PigTrapBlockEntity t : Ironworks.blockEntities(sl, new AABB(worldPosition).inflate(TRACE_LIMIT / 16.0), PigTrapBlockEntity.class))
            if (t.launcher() && worldPosition.equals(t.receiver)) return t;
        return null;
    }

    // ------------------------------------------------------------------ тик

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || ++ticks % 20 != 0 || !launcher()) return;
        if (ticks % 100 == 20 || receiver == null) trace(sl);
        if (receiver != null) depositFraction = Math.min(1, depositFraction + DEPOSIT_PER_DAY / GAME_DAY_S);
        protection(sl);
        double rate = CathodicProtection.corrosionRateMYr(offV) / SECONDS_PER_YEAR * K_SLOW / WALL_M;
        defects.replaceAll(d -> new Pig.Defect(d.positionM(), Math.min(1, d.depthFraction() + rate)));
        if (running != NONE && !stuck) movePig(sl);
        if (ticks % 200 == 0) setChanged();
    }

    /** Ток защиты: станции рядом (поровну на участки в их радиусе) и протекторы камеры. */
    private void protection(ServerLevel sl) {
        double area = Math.PI * OD_M * Math.max(1, lengthM);
        requiredA = CathodicProtection.requiredCurrentA(area, CathodicProtection.COATING_EFFICIENCY);
        double i = 0;
        for (CpRectifierBlockEntity r : Ironworks.blockEntities(sl, new AABB(worldPosition).inflate(CP_RADIUS), CpRectifierBlockEntity.class))
            i += r.currentShareA(sl);
        double anodeA = 0;
        if (anodeKg > 0) {
            anodeA = CathodicProtection.anodeCurrentA(CathodicProtection.offPotentialV(i, requiredA), ANODE_EARTH_OHM);
            anodeKg = Math.max(0, anodeKg - anodeA * K_SLOW / 3600 / CathodicProtection.MAGNESIUM_AH_KG);
        }
        currentA = i + anodeA;
        offV = CathodicProtection.offPotentialV(currentA, requiredA);
    }

    private void movePig(ServerLevel sl) {
        double before = pigPosM;
        pigPosM += Pig.speedMs(FLOW_MS);
        if (Pig.sticks(depositFraction, Pig.MIN_BEND_RATIO, fullBore) && before < lengthM / 2.0 && pigPosM >= lengthM / 2.0) {
            pigPosM = lengthM / 2.0;
            stuck = true;
            HazardService.tell(sl, worldPosition, 32, HazardText.msg("pig.stuck", Math.round(pigPosM)));
            return;
        }
        if (pigPosM < lengthM) return;
        PigTrapBlockEntity rx = pairedReceiver(sl);
        if (running == CLEANING) {
            waxKg = Pig.waxKg(OD_M - 2 * WALL_M, depositFraction * (OD_M - 2 * WALL_M), lengthM);
            depositFraction = 0;
        } else {
            report = Pig.inspect(defects, new SplittableRandom(sl.getSeed() ^ sl.getGameTime()));
            reportReady = true;
        }
        if (rx != null) {
            rx.held = running;
            rx.setChanged();
        }
        HazardService.tell(sl, worldPosition, 32, running == CLEANING ? HazardText.msg("pig.cleaned", Math.round(waxKg))
                : HazardText.msg("pig.report", report.size(), Math.round(deepestReported() * 100)));
        running = NONE;
        pigPosM = 0;
        setChanged();
    }

    public double deepestReported() {
        double m = 0;
        for (Pig.Defect d : report) m = Math.max(m, d.depthFraction());
        return m;
    }

    /** Давление разрушения по самому глубокому найденному дефекту, Па. */
    public double burstPa() {
        return Pipeline.burstPressurePa(GRADE, WALL_M, OD_M, deepestReported());
    }

    // ------------------------------------------------------------------ действия

    /** Пуск: дверца закрыта, давление поднято, поршень в камере, участок собран. */
    public boolean launch(@Nullable Player who) {
        String err = doorOpen() ? "pig.door_open" : vented ? "pig.vented" : loaded == NONE ? "pig.empty" : receiver == null ? "pig.no_receiver"
                : running != NONE ? "pig.busy" : null;
        if (err != null) {
            if (who != null) who.displayClientMessage(HazardText.msg(err), true);
            return false;
        }
        running = loaded;
        loaded = NONE;
        pigPosM = 0;
        stuck = false;
        setChanged();
        return true;
    }

    /** Стравить или поднять давление в камере. */
    public void setVented(boolean v) {
        if (!v && doorOpen()) return;
        vented = v;
        setChanged();
    }

    /** Открыть или закрыть дверцу: под давлением — механическая блокировка. */
    public boolean setDoor(boolean open, @Nullable Player who) {
        if (open && !vented) {
            if (who != null) who.displayClientMessage(HazardText.msg("pig.door_pressure").withStyle(ChatFormatting.RED), true);
            return false;
        }
        BlockState s = getBlockState();
        if (s.hasProperty(BlockStateProperties.OPEN)) level.setBlock(worldPosition, s.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
        return true;
    }

    /** Вырезка участка с застрявшим поршнем: только при стравленном давлении; поршень — в камеру запуска. */
    public boolean cutOut(@Nullable Player who) {
        if (!stuck) return false;
        if (!vented) {
            if (who != null) who.displayClientMessage(HazardText.msg("pig.vented_first"), true);
            return false;
        }
        loaded = running;
        running = NONE;
        stuck = false;
        pigPosM = 0;
        setChanged();
        return true;
    }

    /** Стенд: поршень сразу в камере. */
    public void load(int kind) {
        loaded = kind;
    }

    @Override
    public InteractionResult use(Player player) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        if (!launcher()) {
            PigTrapBlockEntity l = pairedLauncher(sl);
            if (held != NONE) {
                if (l != null && !l.vented) {
                    player.displayClientMessage(HazardText.msg("pig.door_pressure").withStyle(ChatFormatting.RED), true);
                    return InteractionResult.SUCCESS;
                }
                ItemStack pig = new ItemStack(held == SMART ? PcItems.SMART_PIG.get() : PcItems.PIG.get());
                if (!player.getInventory().add(pig)) player.drop(pig, false);
                held = NONE;
                setChanged();
            } else player.displayClientMessage(HazardText.msg("pig.receiver_empty"), true);
            return InteractionResult.SUCCESS;
        }
        setDoor(!doorOpen(), player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return launcher() && (stack.is(PcItems.PIG.get()) || stack.is(PcItems.SMART_PIG.get()) || stack.is(PcItems.SACRIFICIAL_ANODE.get()));
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(PcItems.SACRIFICIAL_ANODE.get())) {
            anodeKg += ANODE_KG;
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.displayClientMessage(HazardText.msg("anode", Math.round(anodeKg)), true);
            setChanged();
            return ItemInteractionResult.SUCCESS;
        }
        if (!doorOpen() || loaded != NONE) {
            player.displayClientMessage(HazardText.msg(loaded != NONE ? "pig.busy" : "pig.door_closed"), true);
            return ItemInteractionResult.FAIL;
        }
        loaded = stack.is(PcItems.SMART_PIG.get()) ? SMART : CLEANING;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return HazardPanels.PIG_TRAP;
    }

    @Override
    public void readPanel(int[] v) {
        PigTrapBlockEntity src = this;
        if (!launcher() && level instanceof ServerLevel sl) {
            PigTrapBlockEntity l = pairedLauncher(sl);
            if (l != null) src = l;
        }
        src.fill(v, held);
    }

    private void fill(int[] v, int receiverHeld) {
        v[HazardPanels.P_LENGTH] = lengthM;
        v[HazardPanels.P_DEPOSIT] = (int) Math.round(depositFraction * 1000);
        v[HazardPanels.P_POS] = lengthM <= 0 ? 0 : (int) Math.round(pigPosM / lengthM * 1000);
        v[HazardPanels.P_POTENTIAL] = (int) Math.round(-offV * 1000);
        v[HazardPanels.P_DEFECTS] = report.size();
        v[HazardPanels.P_DEEPEST] = (int) Math.round(deepestReported() * 100);
        v[HazardPanels.P_BURST] = (int) Math.round(burstPa() / 1e5);
        v[HazardPanels.P_FLAGS] = (doorOpen() ? 1 << HazardPanels.PF_DOOR : 0) | (vented ? 1 << HazardPanels.PF_VENTED : 0)
                | (running != NONE ? 1 << HazardPanels.PF_RUNNING : 0) | (stuck ? 1 << HazardPanels.PF_STUCK : 0)
                | (CathodicProtection.protectedAt(offV) ? 1 << HazardPanels.PF_PROTECTED : 0)
                | (CathodicProtection.overprotected(offV) ? 1 << HazardPanels.PF_OVER : 0) | (receiver != null ? 1 << HazardPanels.PF_RECEIVER : 0)
                | (loaded != NONE || receiverHeld != NONE ? 1 << HazardPanels.PF_PIG : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        PigTrapBlockEntity t = this;
        if (!launcher() && level instanceof ServerLevel sl) t = pairedLauncher(sl);
        if (t == null) return;
        switch (id) {
            case HazardPanels.B_VENT -> t.setVented(!t.vented);
            case HazardPanels.B_DOOR -> t.setDoor(!t.doorOpen(), player);
            case HazardPanels.B_LAUNCH -> t.launch(player);
            case HazardPanels.B_CUT -> t.cutOut(player);
            default -> {
            }
        }
    }

    @Override
    @Nullable
    public IFluidHandler fluids(@Nullable Direction side) {
        return null;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("vented", vented);
        tag.putInt("loaded", loaded);
        tag.putInt("running", running);
        tag.putInt("held", held);
        tag.putDouble("pigPos", pigPosM);
        tag.putBoolean("stuck", stuck);
        tag.putDouble("deposit", depositFraction);
        tag.putDouble("anodeKg", anodeKg);
        tag.putDouble("wax", waxKg);
        tag.put("defects", defects(defects));
        tag.put("report", defects(report));
        tag.putBoolean("reportReady", reportReady);
    }

    private static ListTag defects(List<Pig.Defect> list) {
        ListTag l = new ListTag();
        for (Pig.Defect d : list) {
            CompoundTag c = new CompoundTag();
            c.putDouble("x", d.positionM());
            c.putDouble("d", d.depthFraction());
            l.add(c);
        }
        return l;
    }

    private static List<Pig.Defect> defects(ListTag l) {
        List<Pig.Defect> out = new ArrayList<>();
        for (Tag t : l) out.add(new Pig.Defect(((CompoundTag) t).getDouble("x"), ((CompoundTag) t).getDouble("d")));
        return out;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        vented = !tag.contains("vented") || tag.getBoolean("vented");
        loaded = tag.getInt("loaded");
        running = tag.getInt("running");
        held = tag.getInt("held");
        pigPosM = tag.getDouble("pigPos");
        stuck = tag.getBoolean("stuck");
        depositFraction = tag.getDouble("deposit");
        anodeKg = tag.getDouble("anodeKg");
        waxKg = tag.getDouble("wax");
        defects.clear();
        defects.addAll(defects(tag.getList("defects", Tag.TAG_COMPOUND)));
        report = defects(tag.getList("report", Tag.TAG_COMPOUND));
        reportReady = tag.getBoolean("reportReady");
    }
}
