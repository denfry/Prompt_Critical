package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.PipeKind;
import dev.denfry.promptcritical.sim.metallurgy.PipeMill;
import dev.denfry.promptcritical.sim.metallurgy.PipeSpec;
import dev.denfry.promptcritical.sim.metallurgy.SteelmakingConfig;
import dev.denfry.promptcritical.sim.metallurgy.StockShape;
import dev.denfry.promptcritical.sim.metallurgy.StockState;
import java.util.Arrays;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Трубоэлектросварочный стан {@code pipe_welder} 3×2×1 (08 §8.7.3): формовка листа и сварка шва ТВЧ; генератор ТВЧ —
 * машинный, на том же валу Create (порт слева), поэтому вал несёт формовку {@value #FORMING_W} Вт и мощность ТВЧ
 * / {@value #HF_EFFICIENCY}. Стенка — толщина листа, диаметр — со щита; скорость и мощность — со щита, скорость
 * линии не выше номинальной при 64 об/мин (ниже — пропорционально). Оптимум: {@value #V_OPT_M_MIN} м/мин и
 * {@value #KW_PER_MM} кВт на мм стенки при v_opt (пропорционально скорости) [моё]. Отклонение увеличивает
 * вероятность скрытого дефекта шва ({@link PipeMill#weldDefectPerM}). Трубы по 1 м — {@code welded_pipe}.
 */
public final class PipeWelderBlockEntity extends IronworksBlockEntity implements ShaftConsumer, PanelHost, PieceReceiver {
    public static final double FORMING_W = 30_000, HF_EFFICIENCY = 0.85, V_OPT_M_MIN = 20, KW_PER_MM = 50, MIN_RPM = 16;
    public static final int[] ODS_MM = {114, 159, 219, 273, 325};
    public static final PipeKind[] KINDS = {PipeKind.LINE, PipeKind.STRUCTURAL, PipeKind.BOILER};
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.OUTPUT,
            MachineInventory.Role.OUTPUT, MachineInventory.Role.OUTPUT};

    private final MachineInventory inv = new MachineInventory(ROLES, (slot, s) -> slot == 0 && isPlate(s), this::setChanged);
    private int od = 2, kind, speedMMin = 20, powerKw = 400;
    private ItemStack plate = ItemStack.EMPTY;
    private double leftS, rpm, lengthM;
    private boolean healthy;
    private int lastPipes;
    private double lastDefectP;

    public PipeWelderBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.PIPE_WELDER.get(), pos, state);
    }

    static boolean isPlate(ItemStack s) {
        return Steelworks.shape(s) == StockShape.PLATE && Steelworks.stock(s) != null;
    }

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        this.rpm = rpm;
        this.healthy = healthy;
    }

    @Override
    public double shaftPowerW() {
        if (!plate.isEmpty()) return FORMING_W + powerKw * 1000.0 / HF_EFFICIENCY;
        return Math.abs(rpm) >= MIN_RPM ? 0.1 * FORMING_W : 0;
    }

    @Override
    public double ratedShaftW() {
        return FORMING_W + 800_000 / HF_EFFICIENCY;
    }

    @Override
    public double nominalShaftRpm() {
        return 64;
    }

    /** Скорость линии сейчас, м/мин. */
    public double lineSpeed() {
        return speedMMin * Math.min(1, Math.abs(rpm) / 64);
    }

    /** Оптимальная мощность ТВЧ при стенке {@code wallMm} и скорости {@code v}, кВт. */
    public static double optimumKw(double wallMm, double v) {
        return KW_PER_MM * wallMm * v / V_OPT_M_MIN;
    }

    public void setSettings(int odIndex, int speed, int kw) {
        od = Math.floorMod(odIndex, ODS_MM.length);
        speedMMin = Math.max(5, Math.min(40, speed));
        powerKw = Math.max(100, Math.min(800, kw));
        setChanged();
    }

    private boolean start(ItemStack in) {
        if (!plate.isEmpty() || !healthy || Math.abs(rpm) < MIN_RPM || !isPlate(in)) return false;
        SteelworksComponents.SteelStock st = Steelworks.stock(in);
        double wall = st.thicknessM(), d = ODS_MM[od] / 1000.0;
        if (wall * 8 > d) return false;
        plate = in.copyWithCount(1);
        lengthM = Ironworks.bulkKg() / SteelworksComponents.PipeData.kgPerM(d, wall);
        leftS = lengthM / Math.max(0.1, lineSpeed() / 60);
        setChanged();
        return true;
    }

    @Override
    public boolean acceptPiece(ItemStack in, Direction travel) {
        return start(in);
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        ItemStack in = inv.getStackInSlot(0);
        if (!in.isEmpty() && start(in)) in.shrink(1);
        if (!plate.isEmpty() && healthy && Math.abs(rpm) >= MIN_RPM) {
            leftS -= 0.05;
            if (leftS <= 0) finish();
        }
        setStateFlag(PcProperties.RUNNING, !plate.isEmpty());
    }

    private void finish() {
        long now = level.getGameTime();
        StockState s = Steelworks.state(plate, now);
        SteelworksComponents.SteelStock st = Steelworks.stock(plate);
        plate = ItemStack.EMPTY;
        setChanged();
        if (s == null) return;
        double d = ODS_MM[od] / 1000.0, v = lineSpeed(), wallMm = s.thicknessM() * 1000;
        double optKw = optimumKw(wallMm, v);
        lastDefectP = PipeMill.weldDefectPerM(v, V_OPT_M_MIN, powerKw, optKw, s.grade().carbonEq(),
                SteelmakingConfig.defaults().weldDefectBasePerM());
        PipeSpec p = PipeMill.welded(s, KINDS[kind], d, lengthM, v, V_OPT_M_MIN, powerKw * 1000.0, optKw * 1000.0, st.heatNo(),
                SteelmakingConfig.defaults(), new SplittableRandom(level.random.nextLong()));
        int n = (int) Math.floor(lengthM + 1e-9);
        lastPipes = n;
        if (n <= 0) return;
        ItemStack pipes = new ItemStack(PcItems.WELDED_PIPE.get(), n);
        pipes.set(SteelworksComponents.PIPE_SPEC.get(), SteelworksComponents.PipeData.of(p));
        pipes.set(SteelworksComponents.STEEL_STOCK.get(), SteelworksComponents.SteelStock.of(s.grade(), s.thicknessM(), d, s.porous(),
                s.cracked(), st.heatNo(), s.tempK(), now));
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.4f);
        ItemStack rest = inv.output(pipes);
        if (!rest.isEmpty()) Ironworks.spill(level, worldPosition.above(2), rest);
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.RUNNING)) return;
        MachineFx fx = fx();
        if (fx.chance(1)) fx.sparks(fx.at(18, 10, 8), 1);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return isPlate(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (inv.giveOutputs(player)) return InteractionResult.SUCCESS;
        player.displayClientMessage(SteelworksText.msg("welder", ODS_MM[od], SteelworksText.num(lineSpeed()), powerKw, lastPipes), true);
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return SteelworksPanels.WELDER;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[SteelworksPanels.SHAFT_RPM10] = (int) Math.round(Math.abs(rpm) * 10);
        out[SteelworksPanels.W_OD_MM] = ODS_MM[od];
        out[SteelworksPanels.W_SPEED10] = (int) Math.round(lineSpeed() * 10);
        out[SteelworksPanels.W_SPEED_SET] = speedMMin;
        out[SteelworksPanels.W_POWER_KW] = powerKw;
        SteelworksComponents.SteelStock st = Steelworks.stock(plate.isEmpty() ? inv.getStackInSlot(0) : plate);
        double wall = st == null ? 0 : st.thicknessMm();
        out[SteelworksPanels.W_WALL_MM] = (int) Math.round(wall);
        out[SteelworksPanels.W_OPT_KW] = (int) Math.round(optimumKw(wall, lineSpeed()));
        out[SteelworksPanels.W_KIND] = KINDS[kind].ordinal();
        out[SteelworksPanels.W_PIPES] = lastPipes;
        out[SteelworksPanels.W_DEFECT_PPM] = (int) Math.round(lastDefectP * 1e6);
        out[SteelworksPanels.W_PROGRESS_PERMIL] = plate.isEmpty() || lengthM <= 0 ? 0
                : (int) Math.round(1000 * Math.max(0, 1 - leftS * lineSpeed() / 60 / lengthM));
        out[SteelworksPanels.FLAGS] = plate.isEmpty() ? 0 : 1 << SteelworksPanels.F_RUNNING;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case SteelworksPanels.BTN_SIZE -> od = (od + 1) % ODS_MM.length;
            case SteelworksPanels.BTN_KIND -> kind = (kind + 1) % KINDS.length;
            case SteelworksPanels.BTN_SPEED_DOWN -> speedMMin = Math.max(5, speedMMin - 1);
            case SteelworksPanels.BTN_SPEED_UP -> speedMMin = Math.min(40, speedMMin + 1);
            case SteelworksPanels.BTN_POWER_DOWN -> powerKw = Math.max(100, powerKw - 10);
            case SteelworksPanels.BTN_POWER_UP -> powerKw = Math.min(800, powerKw + 10);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inv", inv.save(registries));
        tag.putInt("od", od);
        tag.putInt("kind", kind);
        tag.putInt("speed", speedMMin);
        tag.putInt("power_kw", powerKw);
        if (!plate.isEmpty()) tag.put("plate", plate.save(registries));
        tag.putDouble("left_s", leftS);
        tag.putDouble("length_m", lengthM);
        tag.putInt("last_pipes", lastPipes);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inv")) inv.load(registries, tag.getCompound("inv"));
        od = Math.floorMod(tag.getInt("od"), ODS_MM.length);
        kind = Math.floorMod(tag.getInt("kind"), KINDS.length);
        if (tag.contains("speed")) speedMMin = tag.getInt("speed");
        if (tag.contains("power_kw")) powerKw = tag.getInt("power_kw");
        plate = tag.contains("plate") ? ItemStack.parseOptional(registries, tag.getCompound("plate")) : ItemStack.EMPTY;
        leftS = tag.getDouble("left_s");
        lengthM = tag.getDouble("length_m");
        lastPipes = tag.getInt("last_pipes");
    }
}
