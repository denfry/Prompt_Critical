package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.metallurgy.MillRollKind;
import dev.denfry.promptcritical.sim.metallurgy.PassResult;
import dev.denfry.promptcritical.sim.metallurgy.RollingMill;
import dev.denfry.promptcritical.sim.metallurgy.StockState;
import java.util.Arrays;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Клеть дуо {@code rolling_mill_stand} 3×3×1 (08 §8.7.2): валки R {@value #ROLL_RADIUS_M} м на валу Create через
 * порт слева (60 об/мин — 1,12 МВт при обжатии 30 мм заготовки 150 мм, T8.12). Раскат с рольганга (вдоль фасада)
 * проходит через валки с зазором со щита — {@link RollingMill#pass}; пропуск длится L/(ωR) реальных секунд, всё это
 * время вал несёт момент пропуска (не короче {@value #HOLD_TICKS} тиков — фильтр стресса Create), затем раскат
 * уходит на рольганг с другой стороны. Не захватил — раскат остаётся на рольганге; момент выше
 * {@value #MAX_TORQUE_NM} Н·м — срезной штифт (железный самородок — новый штифт). Реверсивная прокатка — реверс
 * вала рольгангов и новый зазор.
 */
public final class MillStandBlockEntity extends IronworksBlockEntity implements ShaftConsumer, PanelHost, PieceReceiver {
    public static final double ROLL_RADIUS_M = 0.3, MU = 0.35, MAX_TORQUE_NM = 300_000, RATED_W = 1_200_000, MIN_RPM = 8;
    public static final int HOLD_TICKS = 30, MIN_GAP_MM = 2, MAX_GAP_MM = 300;

    private MillRollKind rolls = MillRollKind.FLAT;
    private int gapMm = 120;
    private boolean sheared, healthy;
    private ItemStack piece = ItemStack.EMPTY;
    private Direction travel = Direction.NORTH;
    private double passLeftS, rpm, holdW;
    private int holdTicks;
    private double lastForceN, lastTorqueNm, lastPowerW;
    private int lastOutcome = -1, passes;

    public MillStandBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.ROLLING_MILL_STAND.get(), pos, state);
    }

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        this.rpm = rpm;
        this.healthy = healthy;
    }

    @Override
    public double shaftPowerW() {
        if (holdTicks > 0) return holdW;
        return Math.abs(rpm) >= MIN_RPM ? 0.02 * RATED_W * Math.min(2, Math.abs(rpm) / 60) : 0;
    }

    @Override
    public double ratedShaftW() {
        return RATED_W;
    }

    @Override
    public double nominalShaftRpm() {
        return 60;
    }

    public double omega() {
        return Math.abs(rpm) * 2 * Math.PI / 60;
    }

    public void setGapMm(int mm) {
        gapMm = Math.max(MIN_GAP_MM, Math.min(MAX_GAP_MM, mm));
        setChanged();
    }

    public int gapMm() {
        return gapMm;
    }

    public void setRolls(MillRollKind k) {
        rolls = k;
        setChanged();
    }

    public boolean sheared() {
        return sheared;
    }

    public ItemStack piece() {
        return piece;
    }

    /** Последний пропуск: исход (ordinal {@link PassResult.Outcome}, −1 — не было), усилие, Н, момент, Н·м, мощность, Вт. */
    public int lastOutcome() {
        return lastOutcome;
    }

    public double lastForceN() {
        return lastForceN;
    }

    public double lastTorqueNm() {
        return lastTorqueNm;
    }

    public double lastPowerW() {
        return lastPowerW;
    }

    @Override
    public boolean acceptPiece(ItemStack in, Direction dir) {
        if (sheared || !piece.isEmpty() || !healthy || Math.abs(rpm) < MIN_RPM || dir.getAxis() != facing().getAxis()) return false;
        long now = level.getGameTime();
        StockState s = Steelworks.state(in, now);
        if (s == null) return false;
        PassResult r = new RollingMill(rolls, ROLL_RADIUS_M, MU, MAX_TORQUE_NM).pass(s, gapMm / 1000.0, omega());
        lastOutcome = r.outcome().ordinal();
        lastForceN = r.forceN();
        lastTorqueNm = r.torqueNm();
        lastPowerW = r.powerW();
        setChanged();
        switch (r.outcome()) {
            case NOT_BITTEN -> {
                level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4f, 1.6f);
                return false;
            }
            case SHEAR_PIN -> {
                sheared = true;
                holdW = r.powerW();
                holdTicks = HOLD_TICKS;
                level.playSound(null, worldPosition, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1.5f, 0.5f);
                return false;
            }
            default -> {
            }
        }
        piece = Steelworks.piece(r.out(), Steelworks.stock(in).heatNo(), now);
        travel = dir;
        passLeftS = Math.max(0.3, r.out().lengthM() / Math.max(1e-3, omega() * ROLL_RADIUS_M));
        holdW = r.powerW();
        holdTicks = Math.max(HOLD_TICKS, (int) Math.ceil(passLeftS * 20));
        passes++;
        level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7f, 0.6f);
        return true;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        if (holdTicks > 0) holdTicks--;
        if (!piece.isEmpty() && healthy && Math.abs(rpm) >= MIN_RPM) {
            passLeftS -= 0.05;
            if (passLeftS <= 0 && Steelworks.handOff(level, worldPosition.relative(travel), piece, travel)) {
                piece = ItemStack.EMPTY;
                setChanged();
            }
        }
        setStateFlag(PcProperties.RUNNING, Math.abs(rpm) >= MIN_RPM && !sheared);
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.RUNNING)) return;
        MachineFx fx = fx();
        if (fx.chance(8)) fx.smoke(fx.at(8, 10, 8), false, 0.3);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return sheared && stack.is(Items.IRON_NUGGET);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!sheared || !stack.is(Items.IRON_NUGGET)) return ItemInteractionResult.FAIL;
        sheared = false;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(SteelworksText.msg("stand_pin_replaced"), true);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(SteelworksText.msg(sheared ? "stand_sheared" : "stand", gapMm, SteelworksText.rolls(rolls),
                SteelworksText.num(lastForceN / 1e6), SteelworksText.num(lastTorqueNm / 1e3), SteelworksText.num(lastPowerW / 1e3)), true);
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return SteelworksPanels.STAND;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[SteelworksPanels.SHAFT_RPM10] = (int) Math.round(Math.abs(rpm) * 10);
        out[SteelworksPanels.M_GAP_MM] = gapMm;
        out[SteelworksPanels.M_ROLLS] = rolls.ordinal();
        out[SteelworksPanels.M_FORCE_KN] = (int) Math.round(lastForceN / 1e3);
        out[SteelworksPanels.M_TORQUE_KNM] = (int) Math.round(lastTorqueNm / 1e3);
        out[SteelworksPanels.M_POWER_KW] = (int) Math.round(lastPowerW / 1e3);
        out[SteelworksPanels.M_OUTCOME] = lastOutcome;
        out[SteelworksPanels.M_PASSES] = passes;
        long now = level.getGameTime();
        SteelworksComponents.SteelStock st = Steelworks.stock(piece);
        if (st != null) {
            out[SteelworksPanels.M_PIECE_C] = (int) Math.round(st.tempK(now) - 273.15);
            out[SteelworksPanels.M_PIECE_H_MM] = st.thicknessMm();
            out[SteelworksPanels.M_PIECE_W_MM] = st.widthMm();
        }
        int flags = 0;
        if (Math.abs(rpm) >= MIN_RPM && !sheared) flags |= 1 << SteelworksPanels.F_RUNNING;
        if (sheared) flags |= 1 << SteelworksPanels.F_ALARM;
        out[SteelworksPanels.FLAGS] = flags;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case SteelworksPanels.BTN_GAP_DOWN -> setGapMm(gapMm - 1);
            case SteelworksPanels.BTN_GAP_UP -> setGapMm(gapMm + 1);
            case SteelworksPanels.BTN_GAP_DOWN_BIG -> setGapMm(gapMm - 10);
            case SteelworksPanels.BTN_GAP_UP_BIG -> setGapMm(gapMm + 10);
            case SteelworksPanels.BTN_KIND -> setRolls(rolls == MillRollKind.FLAT ? MillRollKind.SECTION : MillRollKind.FLAT);
            default -> {
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("rolls", rolls.ordinal());
        tag.putInt("gap_mm", gapMm);
        tag.putBoolean("sheared", sheared);
        if (!piece.isEmpty()) tag.put("piece", piece.save(registries));
        tag.putInt("travel", travel.get3DDataValue());
        tag.putDouble("pass_left_s", passLeftS);
        tag.putInt("passes", passes);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rolls = MillRollKind.values()[Math.floorMod(tag.getInt("rolls"), MillRollKind.values().length)];
        if (rolls != MillRollKind.FLAT && rolls != MillRollKind.SECTION) rolls = MillRollKind.FLAT;
        if (tag.contains("gap_mm")) gapMm = tag.getInt("gap_mm");
        sheared = tag.getBoolean("sheared");
        piece = tag.contains("piece") ? ItemStack.parseOptional(registries, tag.getCompound("piece")) : ItemStack.EMPTY;
        travel = Direction.from3DDataValue(tag.getInt("travel"));
        passLeftS = tag.getDouble("pass_left_s");
        passes = tag.getInt("passes");
    }
}
