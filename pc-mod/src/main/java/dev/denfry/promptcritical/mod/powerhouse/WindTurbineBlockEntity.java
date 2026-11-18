package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.environment.Environment;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.WindTurbineUnit;
import dev.denfry.promptcritical.sim.wind.WindTurbine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ветроэлектростанция Д-30 ({@link WindTurbineUnit}): башня 1×9×1, контроллер — средний блок нижней секции, кабель
 * 0,4 кВ — к любой грани двух нижних блоков башни, кроме фасада со шкафом. Ветер у оси колеса — из слоя environment
 * на высоте ступицы {@link WindTurbineUnit#SPEC} (логарифмический профиль над шероховатостью биома, турбулентность
 * IEC 61400-1). Щит — правый клик, Shift — включить или отключить автоматику, на аварийном остановленном колесе —
 * сброс аварии.
 *
 * <p>Клиент получает обороты колеса, курс гондолы и угол лопастей для рендера; при работе колесо шумит лопастями
 * с частотой их прохода у башни, тормоз на ходу дымит у оголовка.
 */
public class WindTurbineBlockEntity extends GridDeviceBlockEntity<WindTurbineUnit> implements PanelHost {
    /** Ось колеса над контроллером, блоков: верх башни (y + 7) и ступица над оголовком. */
    public static final double HUB_ABOVE_CONTROLLER = 8.5;
    private static final int SYNC_TICKS = 10;

    private float rpm, yawDeg, pitchDeg;
    private float sentRpm = Float.NaN, sentYaw = Float.NaN, sentPitch = Float.NaN;
    private int nextSwishTick;
    /** Ветер аэродинамических испытаний, м/с; NaN — ветер мира. Не сохраняется. */
    private double testWindMs = Double.NaN;

    public WindTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(PowerhouseBlockEntities.WIND_TURBINE.get(), pos, state);
    }

    @Override
    protected WindTurbineUnit unitOf(GridSubsystem grid) {
        return grid.windTurbine(simId());
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        Direction front = facing();
        for (int dy = -1; dy <= 0; dy++) {
            BlockPos cell = worldPosition.offset(0, dy, 0);
            for (Direction d : Direction.Plane.HORIZONTAL) if (d != front) out.add(new Face(cell, d));
        }
        out.add(new Face(worldPosition.below(), Direction.DOWN));
        return List.of(out);
    }

    /**
     * Ровный ветер заданной скорости вместо ветра мира (направление — мировое, чтобы колесо не ждало поворота) — для
     * испытаний агрегата (game test); NaN возвращает ветер мира. В сохранение не попадает.
     */
    public void setTestWind(double ms) {
        testWindMs = ms;
    }

    // --- клиент ---

    /** Обороты колеса, об/мин (на клиенте — последние присланные). */
    public float rotorRpm() {
        return rpm;
    }

    /** Курс гондолы: куда смотрит колесо (откуда дует ветер), градусы по часовой от севера. */
    public float yawDeg() {
        return yawDeg;
    }

    public float pitchDeg() {
        return pitchDeg;
    }

    // --- тик ---

    @Override
    protected void serverTick(WindTurbineUnit u) {
        ServerLevel sl = (ServerLevel) level;
        BlockPos hub = worldPosition.above((int) Math.round(HUB_ABOVE_CONTROLLER));
        Environment.Sample e = EnvironmentService.sample(sl, hub, WindTurbineUnit.SPEC.hubHeightM());
        if (Double.isNaN(testWindMs)) u.setWind(e.windMs(), e.windDirRad(), e.densityKgM3());
        else u.setWind(testWindMs, e.windDirRad(), e.densityKgM3());

        WindTurbine t = u.turbine();
        rpm = (float) t.rotorRpm();
        yawDeg = (float) Math.toDegrees(t.yawRad());
        pitchDeg = (float) t.pitchDeg();
        if (ticks % SYNC_TICKS == 0 && changed()) {
            sentRpm = rpm;
            sentYaw = yawDeg;
            sentPitch = pitchDeg;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (ticks % 5 == 0) setFlag(PcProperties.RUNNING, u.contactorClosed());
        if (ticks % 100 == 0) setChanged();
        effects(sl, t, hub);
    }

    private boolean changed() {
        return Float.isNaN(sentRpm) || Math.abs(rpm - sentRpm) > 0.3 || Math.abs(pitchDeg - sentPitch) > 1
                || Math.abs(Math.IEEEremainder(yawDeg - sentYaw, 360)) > 1;
    }

    /** Шум лопастей с частотой прохода лопасти у башни (B·n/60), громкость ∝ квадрату окружной скорости; дым тормоза. */
    private void effects(ServerLevel sl, WindTurbine t, BlockPos hub) {
        double n = t.rotorRpm();
        if (n > 5 && ticks >= nextSwishTick) {
            double passHz = WindTurbineUnit.SPEC.blades() * n / 60;
            nextSwishTick = ticks + Math.max(4, (int) Math.round(20 / passHz));
            float volume = (float) Math.min(0.8, 0.5 * (n / 30) * (n / 30));
            sl.playSound(null, hub, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS, volume, 0.45f);
        }
        if (t.brakeApplied() && n > 3 && ticks % 4 == 0)
            sl.sendParticles(ParticleTypes.SMOKE, hub.getX() + 0.5, hub.getY() - 0.8, hub.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.01);
    }

    // --- руки: Shift — автоматика или сброс ---

    @Override
    public InteractionResult use(Player player) {
        WindTurbineUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        if (u.turbine().state() == WindTurbine.State.FAULT) {
            boolean ok = u.resetFault();
            player.displayClientMessage(PowerhouseText.msg(ok ? "wind.reset" : "wind.not_reset"), true);
        } else {
            setEnabled(u, !u.enabled(), player);
        }
        return InteractionResult.SUCCESS;
    }

    private void setEnabled(WindTurbineUnit u, boolean on, Player player) {
        u.setEnabled(on);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, on ? 0.9f : 0.6f);
        player.displayClientMessage(on ? PowerhouseText.msg("wind.enabled", PowerhouseText.num(WindTurbineUnit.SPEC.cutInMs(), 0))
                : PowerhouseText.msg("wind.disabled"), true);
        setChanged();
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return WindPanels.WIND;
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
        WindTurbineUnit u = unit();
        if (u == null) return;
        WindTurbine t = u.turbine();
        out[WindPanels.WIND_MS10] = (int) Math.round(t.windMs() * 10);
        out[WindPanels.MEAN_MS10] = (int) Math.round(t.meanLongMs() * 10);
        out[WindPanels.ROTOR_RPM10] = (int) Math.round(t.rotorRpm() * 10);
        out[WindPanels.KW10] = (int) Math.round(u.exportW() / 100);
        out[WindPanels.KVAR10] = (int) Math.round(u.reactiveVar() / 100);
        out[WindPanels.PITCH10] = (int) Math.round(t.pitchDeg() * 10);
        out[WindPanels.THRUST_KN10] = (int) Math.round(t.thrustN() / 100);
        out[WindPanels.YAW_ERR] = (int) Math.round(Math.toDegrees(t.yawErrorRad()));
        out[WindPanels.CP_PERMIL] = (int) Math.round(t.powerCoefficient() * 1000);
        out[WindPanels.ENERGY_KWH] = (int) Math.round(u.energyKWh());
        out[WindPanels.STATE] = t.state().ordinal();
        out[WindPanels.FAULT] = t.fault().ordinal();
        out[WindPanels.FLAGS] = (u.contactorClosed() ? 1 << WindPanels.F_GRID : 0)
                | (u.enabled() ? 1 << WindPanels.F_AUTO : 0)
                | (t.stormHold() ? 1 << WindPanels.F_STORM : 0)
                | (t.state() == WindTurbine.State.FAULT ? 1 << WindPanels.F_FAULT : 0)
                | (t.brakeApplied() ? 1 << WindPanels.F_BRAKE : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        WindTurbineUnit u = unit();
        if (u == null) return;
        switch (id) {
            case WindPanels.BUTTON_START -> setEnabled(u, true, player);
            case WindPanels.BUTTON_STOP -> setEnabled(u, false, player);
            case WindPanels.BUTTON_RESET -> player.displayClientMessage(
                    PowerhouseText.msg(u.resetFault() ? "wind.reset" : "wind.not_reset"), true);
            default -> {
            }
        }
    }

    // --- синхронизация ---

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("rpm", rpm);
        tag.putFloat("yaw", yawDeg);
        tag.putFloat("pitch", pitchDeg);
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("rpm")) rpm = tag.getFloat("rpm");
        if (tag.contains("yaw")) yawDeg = tag.getFloat("yaw");
        if (tag.contains("pitch")) pitchDeg = tag.getFloat("pitch");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
