package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.sim.ops.EnergyKind;
import dev.denfry.promptcritical.sim.ops.FireSuppression;
import dev.denfry.promptcritical.sim.ops.FireSuppression.Agent;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Устройства пожарной сигнализации и тушения (13 §13.5.2–13.5.3). Вид — по блоку:
 * <ul>
 * <li>извещатели: дымовой (горение в объёме дольше инерции 15 с), тепловой (элемент с RTI 50, 57 °C и каналом
 * скорости роста по потолочной струе Альперта), пламени (прямая видимость пламени до 30 м, 3 с);</li>
 * <li>ручной пожарный извещатель — ПКМ, сброс Shift+ПКМ;</li>
 * <li>ППК {@code fire_alarm_panel}: собирает извещатели в радиусе 16 блоков, «пожар» — два извещателя или ручной;
 * открывает дренчеры зоны и запускает газовое тушение после 30 с предупреждения сиреной; сброс — ПКМ без пожара;</li>
 * <li>спринклер: колба RTI 100 на 68 °C, вскрывшись, орошает 12 м² водой из трубы (6,1 мм/мин, NFPA 13 OH1);</li>
 * <li>дренчер: открывается по ППК, орошает свою группу (радиус 3) из трубы;</li>
 * <li>модуль газового тушения: по ППК после задержки выпускает газ в объём радиуса 8 — гасит очаги A, B, E и
 * удушает оставшихся (CO₂ ≥ 34 % смертелен); запертый LOTO не срабатывает (точка изоляции на время работ);</li>
 * <li>лафетный ствол: ПКМ — пуск/останов, бьёт водой из трубы по фасаду на 20 блоков.</li>
 * </ul>
 */
public class FireDeviceBlockEntity extends DeviceBlockEntity implements FoundryDevice, IsolationPoint {
    /** Извещатели и приёмно-контрольный прибор пожарной сигнализации, Вт; спринклеры и дренчеры питания не берут. Малая нагрузка 0,4 кВ: розетка, щит или ИБП рядом. */
    public static final double POWER_W = 20;

    /** Извещатели и прибор работают от питания; спринклер, дренчер и лафет — нет. */
    private boolean needsPower() {
        return switch (kind()) {
            case SPRINKLER, DELUGE, MONITOR -> false;
            default -> true;
        };
    }
    public enum Kind { SMOKE_DETECTOR, HEAT_DETECTOR, FLAME_DETECTOR, CALL_POINT, ALARM_PANEL, SPRINKLER, DELUGE, GAS_CYLINDER, MONITOR }

    static final int ZONE = 16, DETECT = 6, FLAME_RANGE = 30, SPRINKLER_RADIUS = 2, DELUGE_RADIUS = 3, GAS_RADIUS = 8, MONITOR_RANGE = 20;
    static final double SMOKE_DELAY_S = 15, FLAME_DELAY_S = 3, GAS_DELAY_S = 30, AMBIENT_K = 293.15;
    /** Спринклер: 6,1 мм/мин на 12 м² — 73 л/мин; дренчер группы — 10,2 л/(мин·м²) на 28 м²; ствол — 1000 л/мин. */
    static final double SPRINKLER_LPS = 6.1 * 12 / 60, DELUGE_LPS = 10.2 * 28 / 60, MONITOR_LPS = 1000.0 / 60;
    public static final BooleanProperty FAULT = BlockStateProperties.POWERED;

    private final FeedTank water = new FeedTank(Fluids.WATER, 2000);
    private FireSuppression.Element element;
    private double seenS;
    /** ППК: пожар объявлен; тик выпуска газа (−1 — нет). */
    private boolean fire, released;
    private long gasAt = -1;
    /** Дренчер/ствол открыт; газовый модуль заблокирован ручным запорным устройством. */
    private boolean running, blocked;
    private int ticks;

    public FireDeviceBlockEntity(BlockPos pos, BlockState state) {
        super(OpsBlockEntities.FIRE_DEVICE.get(), pos, state);
    }

    public Kind kind() {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).getPath();
        return switch (id) {
            case "smoke_detector" -> Kind.SMOKE_DETECTOR;
            case "heat_detector" -> Kind.HEAT_DETECTOR;
            case "flame_detector" -> Kind.FLAME_DETECTOR;
            case "manual_call_point" -> Kind.CALL_POINT;
            case "fire_alarm_panel" -> Kind.ALARM_PANEL;
            case "sprinkler_head" -> Kind.SPRINKLER;
            case "deluge_nozzle" -> Kind.DELUGE;
            case "gas_suppression_cylinder" -> Kind.GAS_CYLINDER;
            default -> Kind.MONITOR;
        };
    }

    private boolean flag(BooleanProperty p) {
        BlockState s = getBlockState();
        return s.hasProperty(p) && s.getValue(p);
    }

    private void setFlag(BooleanProperty p, boolean v) {
        BlockState s = getBlockState();
        if (s.hasProperty(p) && s.getValue(p) != v) level.setBlock(worldPosition, s.setValue(p, v), Block.UPDATE_CLIENTS);
    }

    /** Извещатель в тревоге / спринклер вскрыт / модуль разряжен. */
    public boolean alarm() {
        return flag(PcProperties.ALARM);
    }

    public boolean fire() {
        return fire;
    }

    public boolean running() {
        return running;
    }

    public FeedTank water() {
        return water;
    }

    @Override
    @Nullable
    public IFluidHandler fluids(@Nullable Direction side) {
        Kind k = kind();
        return k == Kind.SPRINKLER || k == Kind.DELUGE || k == Kind.MONITOR ? water : null;
    }

    // ------------------------------------------------------------------ тик

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || ++ticks % 20 != 0) return;
        if (needsPower() && !dev.denfry.promptcritical.mod.grid.MachinePower.powered(sl, worldPosition, POWER_W)) return;
        double dt = 1;
        switch (kind()) {
            case SMOKE_DETECTOR -> {
                boolean smoke = !FireService.fires(sl, worldPosition, DETECT).isEmpty();
                seenS = smoke ? seenS + dt : 0;
                if (seenS >= SMOKE_DELAY_S) setFlag(PcProperties.ALARM, true);
            }
            case HEAT_DETECTOR -> {
                if (element == null) element = new FireSuppression.Element(50, 330.15, true, AMBIENT_K);
                List<BlockPos> f = FireService.fires(sl, worldPosition, DETECT);
                double h = f.isEmpty() ? 3 : Math.max(1, worldPosition.getY() - f.get(0).getY());
                if (element.step(dt, f.size() * FireService.KW_PER_FIRE_BLOCK, h, AMBIENT_K)) setFlag(PcProperties.ALARM, true);
            }
            case FLAME_DETECTOR -> {
                boolean seen = flameInSight(sl);
                seenS = seen ? seenS + dt : 0;
                if (seenS >= FLAME_DELAY_S) setFlag(PcProperties.ALARM, true);
            }
            case ALARM_PANEL -> panelTick(sl);
            case SPRINKLER -> {
                if (!alarm()) {
                    if (element == null) element = new FireSuppression.Element(100, 341.15, false, AMBIENT_K);
                    List<BlockPos> f = FireService.fires(sl, worldPosition.below(DETECT / 2), DETECT / 2 + 1);
                    double h = f.isEmpty() ? 3 : Math.max(1, worldPosition.getY() - f.get(0).getY());
                    if (element.step(dt, f.size() * FireService.KW_PER_FIRE_BLOCK, h, AMBIENT_K)) setFlag(PcProperties.ALARM, true);
                }
                if (alarm()) spray(sl, worldPosition.below(SPRINKLER_RADIUS), SPRINKLER_RADIUS, SPRINKLER_LPS);
            }
            case DELUGE -> {
                if (running) spray(sl, worldPosition.below(DELUGE_RADIUS), DELUGE_RADIUS, DELUGE_LPS);
            }
            case MONITOR -> {
                if (running) monitor(sl);
            }
            case GAS_CYLINDER, CALL_POINT -> {
            }
        }
        if (ticks % 100 == 0) setChanged();
    }

    private boolean flameInSight(ServerLevel sl) {
        Vec3 eye = worldPosition.getCenter();
        for (BlockPos p : FireService.fires(sl, worldPosition, 8)) {
            BlockHitResult hit = sl.clip(new ClipContext(eye, p.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(p) || hit.getBlockPos().equals(worldPosition)) return true;
        }
        // дальняя зона — без луча: открытое пламя крупного очага видно и через щели
        return !FireService.fires(sl, worldPosition, FLAME_RANGE / 3).isEmpty() && FireService.powerKw(sl, worldPosition, FLAME_RANGE / 3) >= 3000;
    }

    /** Ороситель: вода из трубы, расход за секунду; гасит очаги класса A (на D и E — по таблице классов). */
    private void spray(ServerLevel sl, BlockPos center, int r, double litresPerS) {
        topUp(sl, litresPerS);
        if (water.take(litresPerS) < litresPerS * 0.99) return; // сухая труба: ороситель не работает
        sl.sendParticles(ParticleTypes.FALLING_WATER, center.getX() + 0.5, center.getY() + r, center.getZ() + 0.5, 20, r, 0.5, r, 0);
        FireService.apply(sl, center, r, Agent.WATER, null);
    }

    /** Своя труба пуста — вода из пожарной сети (насос в 24 блоках), см. {@link FireWaterBlockEntity}. */
    private void topUp(ServerLevel sl, double litresPerS) {
        double need = litresPerS - water.amount();
        if (need <= 0) return;
        double got = FireWaterBlockEntity.draw(sl, worldPosition, need);
        if (got > 0) water.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,
                (int) Math.ceil(got)), IFluidHandler.FluidAction.EXECUTE);
    }

    private void monitor(ServerLevel sl) {
        topUp(sl, MONITOR_LPS);
        if (water.take(MONITOR_LPS) < MONITOR_LPS * 0.99) {
            running = false;
            setFlag(PcProperties.RUNNING, false);
            return;
        }
        Direction f = getBlockState().getBlock() instanceof PcBlock pc ? pc.facing(getBlockState()) : Direction.NORTH;
        // с дозатором пенообразователя рядом ствол бьёт пеной (13 §13.5.3)
        boolean foam = FireWaterBlockEntity.foam(sl, worldPosition, MONITOR_LPS);
        for (int i = 2; i <= MONITOR_RANGE; i += 2) {
            BlockPos p = worldPosition.relative(f, i);
            sl.sendParticles(foam ? ParticleTypes.CLOUD : ParticleTypes.SPLASH, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
            if (foam) FireService.foam(sl, p, 1, MONITOR_LPS * 60 / (MONITOR_RANGE / 2.0));
            else FireService.apply(sl, p, 1, Agent.WATER, null);
        }
    }

    // ------------------------------------------------------------------ ППК

    private List<FireDeviceBlockEntity> zone(ServerLevel sl) {
        return Ironworks.blockEntities(sl, new AABB(worldPosition).inflate(ZONE), FireDeviceBlockEntity.class);
    }

    private void panelTick(ServerLevel sl) {
        int detectors = 0, alarms = 0;
        boolean manual = false;
        List<FireDeviceBlockEntity> zone = zone(sl);
        for (FireDeviceBlockEntity d : zone) {
            switch (d.kind()) {
                case SMOKE_DETECTOR, HEAT_DETECTOR, FLAME_DETECTOR -> {
                    detectors++;
                    if (d.alarm()) alarms++;
                }
                case CALL_POINT -> manual |= d.alarm();
                default -> {
                }
            }
        }
        setFlag(FAULT, detectors == 0);
        // «два извещателя в зоне» — защита от ложного пуска; ручной извещатель — сразу
        if (!fire && (alarms >= 2 || manual)) {
            fire = true;
            released = false;
            gasAt = sl.getGameTime() + (long) (GAS_DELAY_S * 20);
            setFlag(PcProperties.ALARM, true);
            Component c = OpsFireText.msg("fire", worldPosition.toShortString()).withStyle(ChatFormatting.RED);
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 64 * 64)) p.displayClientMessage(c, false);
            for (FireDeviceBlockEntity d : zone) if (d.kind() == Kind.DELUGE) d.running = true;
        }
        if (!fire) return;
        sl.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.5f, released ? 0.5f : 1.2f);
        if (!released && sl.getGameTime() >= gasAt) {
            released = true;
            for (FireDeviceBlockEntity d : zone) if (d.kind() == Kind.GAS_CYLINDER) d.discharge(sl);
        }
    }

    /** Выпуск газа: гасит очаги в объёме, удушает оставшихся без дыхательного аппарата. */
    void discharge(ServerLevel sl) {
        if (alarm() || blocked || Lockouts.locked(sl, worldPosition)) return;
        setFlag(PcProperties.ALARM, true);
        sl.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 2f, 0.4f);
        sl.sendParticles(ParticleTypes.POOF, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, 60, GAS_RADIUS / 2.0, 1, GAS_RADIUS / 2.0, 0.02);
        FireService.apply(sl, worldPosition, GAS_RADIUS, Agent.CO2, null);
        for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(GAS_RADIUS))) {
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 30));
            p.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 20, 1));
            p.displayClientMessage(OpsFireText.msg("asphyxiant").withStyle(ChatFormatting.DARK_RED), false);
        }
    }

    // ------------------------------------------------------------------ руки

    @Override
    public InteractionResult use(Player player) {
        Kind k = kind();
        switch (k) {
            case CALL_POINT -> {
                boolean on = !player.isShiftKeyDown();
                setFlag(PcProperties.ALARM, on);
                level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1f, on ? 1f : 0.6f);
            }
            case ALARM_PANEL -> resetPanel(player);
            case SPRINKLER, SMOKE_DETECTOR, HEAT_DETECTOR, FLAME_DETECTOR -> {
                if (!FireService.fires(level, worldPosition, DETECT).isEmpty()) {
                    player.displayClientMessage(OpsFireText.msg("still_burning").withStyle(ChatFormatting.RED), true);
                } else {
                    setFlag(PcProperties.ALARM, false);
                    if (element != null) element.reset(AMBIENT_K);
                    seenS = 0;
                    player.displayClientMessage(OpsFireText.msg(k == Kind.SPRINKLER ? "bulb_replaced" : "detector_reset"), true);
                }
            }
            case DELUGE -> player.displayClientMessage(OpsFireText.msg(running ? "deluge_open" : "deluge_closed"), true);
            case MONITOR -> {
                running = !running;
                setFlag(PcProperties.RUNNING, running);
                player.displayClientMessage(OpsFireText.msg(running ? "monitor_on" : "monitor_off"), true);
            }
            case GAS_CYLINDER -> {
                if (Lockouts.locked(level, worldPosition)) {
                    player.displayClientMessage(Lockouts.lockedMessage(level, worldPosition), true);
                } else if (alarm() && player.isShiftKeyDown()) {
                    setFlag(PcProperties.ALARM, false); // перезарядка модуля
                    player.displayClientMessage(OpsFireText.msg("recharged"), true);
                } else {
                    blocked = !blocked;
                    player.displayClientMessage(OpsFireText.msg(blocked ? "gas_blocked" : "gas_armed"), true);
                }
            }
        }
        setChanged();
        return InteractionResult.SUCCESS;
    }

    private void resetPanel(Player player) {
        if (!(level instanceof ServerLevel sl)) return;
        for (FireDeviceBlockEntity d : zone(sl))
            if ((d.kind() == Kind.SMOKE_DETECTOR || d.kind() == Kind.HEAT_DETECTOR || d.kind() == Kind.FLAME_DETECTOR || d.kind() == Kind.CALL_POINT)
                    && d.alarm() && !FireService.fires(sl, d.worldPosition, DETECT).isEmpty()) {
                player.displayClientMessage(OpsFireText.msg("still_burning").withStyle(ChatFormatting.RED), true);
                return;
            }
        fire = false;
        released = false;
        gasAt = -1;
        setFlag(PcProperties.ALARM, false);
        for (FireDeviceBlockEntity d : zone(sl)) if (d.kind() == Kind.DELUGE) d.running = false;
        player.displayClientMessage(OpsFireText.msg("panel_reset"), true);
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

    // ------------------------------------------------------------------ LOTO: газовый модуль на время работ запирают

    @Override
    public EnergyKind energy() {
        return EnergyKind.CHEMICAL;
    }

    @Override
    public boolean isolatedPosition() {
        return kind() == Kind.GAS_CYLINDER && blocked;
    }

    @Override
    public boolean noEnergy() {
        return isolatedPosition();
    }

    // ------------------------------------------------------------------ сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        water.save(tag, "water");
        tag.putBoolean("fire", fire);
        tag.putBoolean("released", released);
        tag.putLong("gas_at", gasAt);
        tag.putBoolean("running", running);
        tag.putBoolean("blocked", blocked);
        tag.putDouble("seen", seenS);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        water.load(tag, "water");
        fire = tag.getBoolean("fire");
        released = tag.getBoolean("released");
        gasAt = tag.contains("gas_at") ? tag.getLong("gas_at") : -1;
        running = tag.getBoolean("running");
        blocked = tag.getBoolean("blocked");
        seenS = tag.getDouble("seen");
    }

    static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }
}
