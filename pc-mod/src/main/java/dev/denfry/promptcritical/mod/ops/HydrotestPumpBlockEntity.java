package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.boiler.SteamEngineBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.sim.ops.CheckInput;
import dev.denfry.promptcritical.sim.ops.CommissioningDefectKind;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.plant.BoilerUnit;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Ручной опрессовочный насос {@code hydrotest_pump} (13 §13.6.6, {@code PRESSURE_TEST}): ставится вплотную к котлу или
 * паровой машине; каждое качание рычага (ПКМ) поднимает давление воды на {@value #STROKE_PA} Па. Давление
 * испытания держат {@code ops.hydrotest_hold_phys_s} физики — проверка выполняется сама; течь шва
 * ({@code SEAM_WEEP}) или пористой отливки видна сразу каплями, и проверка проваливается. Испытание водой безопасно:
 * разрыв на холодной воде — не взрыв. Котёл должен быть холодным, без огня и залит водой до рабочего уровня.
 */
public final class HydrotestPumpBlockEntity extends IronworksBlockEntity {
    /** Подъём давления за качание, Па [BALANCE]. */
    public static final double STROKE_PA = 0.1e6;
    /** Котёл залит для испытания: уровень по стеклу не ниже рабочего, м. */
    public static final double FILLED_LEVEL_M = 0.6;
    /** p_раб машины = p_раб котла I0. */
    public static final double ENGINE_WORKING_PA = 1.2e6;

    private double pressurePa, heldPhysS;
    @Nullable
    private UUID operator;
    private int pumpingTicks;

    public HydrotestPumpBlockEntity(BlockPos pos, BlockState state) {
        super(OpsBlockEntities.HYDROTEST_PUMP.get(), pos, state);
    }

    public double pressurePa() {
        return pressurePa;
    }

    /** Котёл или машина вплотную. */
    @Nullable
    public OpsMachine target() {
        if (level == null) return null;
        for (Direction d : Direction.values()) {
            OpsMachine m = OpsWorld.machine(level, worldPosition.relative(d));
            if (m instanceof FireTubeBoilerBlockEntity || m instanceof SteamEngineBlockEntity) return m;
        }
        return null;
    }

    private static double workingPa(OpsMachine m) {
        return m instanceof FireTubeBoilerBlockEntity b && b.unit() != null ? b.unit().spec().workingPressurePa() : ENGINE_WORKING_PA;
    }

    /** Качнуть рычаг. */
    @Override
    public InteractionResult use(Player player) {
        OpsMachine m = target();
        if (m == null) {
            player.displayClientMessage(OpsText.msg("hydro_no_unit"), true);
            return InteractionResult.SUCCESS;
        }
        if (m instanceof FireTubeBoilerBlockEntity b) {
            BoilerUnit u = b.unit();
            if (u == null || u.gaugeGlassLevelM() < FILLED_LEVEL_M) {
                player.displayClientMessage(OpsText.msg("hydro", OpsText.num(0, 2), "—", "0", "—"), true);
                return InteractionResult.SUCCESS;
            }
        }
        operator = player.getUUID();
        pressurePa += STROKE_PA;
        pumpingTicks = 6;
        setStateFlag(PcProperties.PUMPING, true);
        level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.4f, 1.6f);
        OpsSubsystem ops = OpsWorld.ops(level);
        double test = ops == null ? 0 : ops.config().testPressurePa(workingPa(m));
        player.displayClientMessage(OpsText.msg("hydro", OpsText.num(pressurePa / 1e6, 2), OpsText.num(test / 1e6, 2),
                OpsText.num(heldPhysS, 0), OpsText.num(ops == null ? 0 : ops.config().hydrotestHoldPhysS(), 0)), true);
        setChanged();
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        if (pumpingTicks > 0 && --pumpingTicks == 0) setStateFlag(PcProperties.PUMPING, false);
        if (pressurePa <= 0) return;
        OpsMachine m = target();
        OpsSubsystem ops = OpsWorld.ops(level);
        if (m == null || ops == null || ops.unit(m.simId()) == null) {
            pressurePa = heldPhysS = 0;
            return;
        }
        double pw = workingPa(m), test = ops.config().testPressurePa(pw);
        boolean leak = ops.seamWeeping(m.simId(), pressurePa)
                || ops.commissioning().has(m.simId(), CommissioningDefectKind.CASTING_POROSITY) && pressurePa >= pw;
        if (leak) drip(m);
        if (pressurePa < test * 0.99) {
            heldPhysS = 0;
            return;
        }
        heldPhysS += Ironworks.tickPhysS(b);
        if (!leak && heldPhysS < ops.config().hydrotestHoldPhysS()) return;
        boolean fireOut = true;
        double metalK = OpsActions.AMBIENT_K;
        if (m instanceof FireTubeBoilerBlockEntity boiler && boiler.unit() != null) {
            fireOut = !boiler.unit().lit();
            metalK = boiler.unit().waterTempK();
        }
        Player p = operator == null ? null : level.getPlayerByUUID(operator);
        var r = ops.perform(m.simId(), new CheckInput.PressureTest(fireOut, metalK, pressurePa,
                Math.max(heldPhysS, leak ? ops.config().hydrotestHoldPhysS() : heldPhysS)), operator);
        if (p != null) {
            if (leak) p.displayClientMessage(OpsText.msg("hydro_leak"), false);
            OpsActions.report(p, r);
        }
        pressurePa = heldPhysS = 0;
        level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6f, 1.0f);
        setChanged();
    }

    private void drip(OpsMachine m) {
        if (!(level instanceof ServerLevel s) || level.getGameTime() % 4 != 0) return;
        Vec3 at = m.opsBearingPos();
        s.sendParticles(ParticleTypes.DRIPPING_WATER, at.x, at.y, at.z, 3, 0.6, 0.3, 0.6, 0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("pressure_pa", pressurePa);
        tag.putDouble("held_s", heldPhysS);
        if (operator != null) tag.putUUID("operator", operator);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pressurePa = tag.getDouble("pressure_pa");
        heldPhysS = tag.getDouble("held_s");
        operator = tag.hasUUID("operator") ? tag.getUUID("operator") : null;
    }
}
