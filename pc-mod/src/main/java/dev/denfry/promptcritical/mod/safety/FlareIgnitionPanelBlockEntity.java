package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.powerhouse.WaterTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.ops.FlareSystem;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Щит розжига факела {@code flare_ignition_panel} (13 §13.4.3): берёт на себя оголовок {@code flare_tip} в
 * {@value #TIP_RANGE} блоках и считает его горение ({@link FlareSystem}) вместо сепаратора, который сбрасывает туда газ.
 *
 * <ul>
 *   <li>Три дежурные горелки на топливном газе (товарный газ трубой в щит, {@value #PILOT_NM3S} нм³/с каждая
 *   [ориентир: 1,5 нм³/ч на горелку]); кнопка розжига зажигает их генератором фронта пламени. Нет топлива — гаснут;
 *   без горелок сброс уходит несгоревшим (P1).</li>
 *   <li>Пар на кольцо оголовка — из барабана водотрубного котла в {@value #BOILER_RANGE} блоках, отношение пар/газ
 *   задаётся кнопками; нет пара — дымное горение.</li>
 *   <li>Сепаратор капель {@code flare_knockout_drum} в {@value #DRUM_RANGE} блоках: ловит
 *   {@value #CONDENSATE_L_PER_NM3} л конденсата на нм³ сброса [BALANCE]; его нет или он полон — унос жидкости в факел,
 *   «горящий дождь»: пожар класса B в радиусе {@value #RAIN_RADIUS_M} м ({@code ops.flare_rain_radius_m}).</li>
 *   <li>Гидрозатвор {@code flare_seal_drum} в {@value #DRUM_RANGE} блоках: вода расходуется
 *   {@value #SEAL_LOSS_MB_S} мБ/с при сбросе [BALANCE]; уровень ниже {@value #SEAL_LOW_MB} мБ при горящем факеле —
 *   проскок пламени в коллектор (взрыв у затвора, горелки гаснут). Затвора нет — воздух в стволе, сигнал.</li>
 *   <li>Излучение на игроков в радиусе D(1,58 кВт/м²) от центра пламени ({@value #FLAME_HEIGHT_M} м над оголовком):
 *   урон по таблице §13.4.3, с огнезащитным костюмом — поток ×0,1.</li>
 * </ul>
 * Газ сброса — попутный ({@link FlareSystem.Gas#ASSOCIATED}), {@value #KG_PER_NM3} кг/нм³. Воспламенение горючих
 * блоков от излучения ≥ 12,5 кВт/м² не сделано: нет общего бюджета разрушений (04d).
 */
public final class FlareIgnitionPanelBlockEntity extends UraniumProcessBlockEntity {
    /** Автоматика розжига и запальники, Вт. */
    @Override
    protected double ratedPowerW() {
        return 500;
    }

    public static final int TIP_RANGE = 8, DRUM_RANGE = 8, BOILER_RANGE = 16, FUEL_MB = 1000;
    public static final double PILOT_NM3S = 4.2e-4, KG_PER_NM3 = 0.8, CONDENSATE_L_PER_NM3 = 0.05, RAIN_RADIUS_M = 40,
            SEAL_LOSS_MB_S = 1.0 / 60, SEAL_LOW_MB = 250, FLAME_HEIGHT_M = 3, RATIO_STEP = 0.05, RATIO_MAX = 1.5;

    /** Горящий дождь ставит огонь на землю; испытания отключают это, чтобы не поджечь соседние испытания общего мира. */
    public static boolean rainFire = true;

    private final FluidTank fuel = new FluidTank(FUEL_MB, s -> s.getFluid().isSame(PcFluids.SALES_GAS.source.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final FlareSystem flare = FlareSystem.create();
    private int pilots;
    private double steamRatio = FlareSystem.Gas.ASSOCIATED.steamRequired(), fuelDebt, nm3s, steamKgS;
    private boolean noTip, noKo, noSeal, raining, flashback, noSteam;
    @Nullable
    private BlockPos tip;

    public FlareIgnitionPanelBlockEntity(BlockPos pos, BlockState state) {
        super(SafetyBlockEntities.FLARE_IGNITION_PANEL.get(), pos, state, 0, 0, s -> false);
    }

    public int pilots() {
        return pilots;
    }

    public FlareSystem.FlareState state() {
        return flare.last();
    }

    public boolean raining() {
        return raining;
    }

    public double flowNm3s() {
        return nm3s;
    }

    public void setSteamRatio(double r) {
        steamRatio = Math.clamp(r, 0, RATIO_MAX);
        setChanged();
    }

    /** Розжиг дежурных горелок: нужен топливный газ. */
    public boolean ignite() {
        if (fuel.getFluidAmount() <= 0) return false;
        pilots = FlareSystem.PILOTS;
        if (level != null) level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8f, 1.0f);
        setChanged();
        return true;
    }

    @Nullable
    private BlockPos findTip(Level level) {
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-TIP_RANGE, -2, -TIP_RANGE), worldPosition.offset(TIP_RANGE, 32, TIP_RANGE)))
            if (level.getBlockState(p).is(PcBlocks.FLARE_TIP.get())) return p.immutable();
        return null;
    }

    @Nullable
    private <T extends net.minecraft.world.level.block.entity.BlockEntity> T near(ServerLevel level, int r, Class<T> type) {
        for (T t : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(r), type)) return t;
        return null;
    }

    @Override
    protected void process(ServerLevel level, double dtPhysS) {
        long now = level.getGameTime();
        if ((now + worldPosition.hashCode()) % 20 != 0) return;
        if (tip == null || !level.getBlockState(tip).is(PcBlocks.FLARE_TIP.get())) tip = findTip(level);
        noTip = tip == null;
        if (noTip) return;
        Flares.manage(level, tip);
        nm3s = Flares.take(level, tip);
        // дежурные горелки на топливном газе
        if (pilots > 0) {
            fuelDebt += pilots * PILOT_NM3S;
            while (fuelDebt >= 1 && fuel.getFluidAmount() > 0) {
                fuel.drain(1, IFluidHandler.FluidAction.EXECUTE);
                fuelDebt -= 1;
            }
            if (fuelDebt >= 1) pilots = 0; // топливо кончилось — горелки погасли
        }
        double kgS = nm3s * KG_PER_NM3;
        // пар на кольцо оголовка
        WaterTubeBoilerBlockEntity boiler = near(level, BOILER_RANGE, WaterTubeBoilerBlockEntity.class);
        steamKgS = boiler == null || kgS <= 0 ? 0 : boiler.drawDeaeratorSteam(kgS * steamRatio);
        noSteam = kgS > 0 && steamRatio > 0 && steamKgS <= 0;
        // сепаратор капель и гидрозатвор
        FlareKnockoutDrumBlockEntity ko = near(level, DRUM_RANGE, FlareKnockoutDrumBlockEntity.class);
        FlareSealDrumBlockEntity seal = near(level, DRUM_RANGE, FlareSealDrumBlockEntity.class);
        noKo = ko == null;
        noSeal = seal == null;
        double liquidL = nm3s * CONDENSATE_L_PER_NM3;
        boolean caught = ko != null && ko.catchLiquid(liquidL);
        FlareSystem.FlareState s = flare.step(kgS, steamKgS, FlareSystem.Gas.ASSOCIATED, pilots);
        boolean burning = kgS > 0 && !s.flameOut();
        raining = burning && !caught;
        flashback = false;
        if (seal != null && kgS > 0) {
            seal.lose(SEAL_LOSS_MB_S);
            if (burning && seal.waterMb() < SEAL_LOW_MB && seal.arm()) {
                flashback = true;
                pilots = 0;
                BlockPos at = seal.getBlockPos();
                level.explode(null, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 2, Level.ExplosionInteraction.NONE);
            }
        }
        BlockState t = level.getBlockState(tip);
        if (t.hasProperty(BlockStateProperties.LIT) && t.getValue(BlockStateProperties.LIT) != burning)
            level.setBlock(tip, t.setValue(BlockStateProperties.LIT, burning), Block.UPDATE_ALL);
        BlockState self = getBlockState();
        if (self.hasProperty(PcProperties.SEVERITY) && self.getValue(PcProperties.SEVERITY) != pilots)
            level.setBlock(worldPosition, self.setValue(PcProperties.SEVERITY, pilots), Block.UPDATE_CLIENTS);
        if (kgS > 0) emit(level, s, kgS);
        if (burning) radiate(level, s.heatReleaseW());
        if (raining) rain(level);
        if (s.smokeIndex() > 0.2 && burning)
            level.sendParticles(ParticleTypes.LARGE_SMOKE, tip.getX() + 0.5, tip.getY() + 2, tip.getZ() + 0.5,
                    (int) Math.ceil(8 * s.smokeIndex()), 0.6, 0.6, 0.6, 0.02);
    }

    private void emit(ServerLevel level, FlareSystem.FlareState s, double kgS) {
        double gj = s.heatReleaseW() * 1e-9;
        // сажа дымного факела: до 1 % углерода сброса [BALANCE, шкала Рингельмана — визуально]
        double pm = s.smokeIndex() * kgS * 0.01;
        dev.denfry.promptcritical.mod.environment.AirQualityService.emit(level, tip, tip.getY() - worldPosition.getY() + 10, 1200,
                kgS * 18, s.so2KgS(), 0.03 * gj, pm, s.co2KgS(), s.ch4UnburntKgS(), "flare@" + tip.toShortString(),
                dev.denfry.promptcritical.sim.environment.EmissionLedger.Category.FLARE);
    }

    private void radiate(ServerLevel level, double heatW) {
        Vec3 flame = Vec3.atCenterOf(tip).add(0, FLAME_HEIGHT_M, 0);
        double r = FlareSystem.distanceForFlux(heatW, FlareSystem.K_CONTINUOUS);
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator() || p.distanceToSqr(flame) > r * r) continue;
            double k = FlareSystem.radiantFluxWm2(heatW, p.position().distanceTo(flame)) * PpeService.radiantFactor(p);
            double dmg = FlareSystem.damagePerSecond(k);
            if (dmg > 0) p.hurt(thermal(level), (float) dmg);
            if (k >= FlareSystem.K_SECONDS) p.igniteForSeconds(2);
            if (k >= FlareSystem.K_EMERGENCY_MIN) p.removeEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
        }
    }

    /** Унос жидкости: горящие капли падают на землю вокруг ствола. */
    private void rain(ServerLevel level) {
        double a = level.random.nextDouble() * Math.PI * 2, d = level.random.nextDouble() * RAIN_RADIUS_M;
        int x = tip.getX() + (int) Math.round(Math.cos(a) * d), z = tip.getZ() + (int) Math.round(Math.sin(a) * d);
        if (!level.isLoaded(new BlockPos(x, tip.getY(), z))) return;
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        if (rainFire && level.getBlockState(ground).isAir() && BaseFireBlock.canBePlacedAt(level, ground, Direction.UP))
            level.setBlockAndUpdate(ground, BaseFireBlock.getState(level, ground));
        level.sendParticles(ParticleTypes.FLAME, x + 0.5, ground.getY() + 1.5, z + 0.5, 6, 0.3, 0.6, 0.3, 0.01);
    }

    static DamageSource thermal(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(THERMAL_RADIATION));
    }

    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> THERMAL_RADIATION =
            net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE, dev.denfry.promptcritical.mod.PromptCritical.id("thermal_radiation"));

    @Override
    protected boolean running() {
        return pilots > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return fuel;
    }

    @Override
    public PanelLayout panelLayout() {
        return SafetyPanels.FLARE;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        FlareSystem.FlareState s = flare.last();
        v[SafetyPanels.FL_FLOW] = (int) Math.round(nm3s * 3600);
        v[SafetyPanels.FL_HEAT_X10] = (int) Math.round(s.heatReleaseW() / 1e5);
        v[SafetyPanels.FL_SMOKE] = (int) Math.round(s.smokeIndex() * 100);
        v[SafetyPanels.FL_ETA_X10] = (int) Math.round(s.combustionEfficiency() * 1000);
        v[SafetyPanels.FL_RATIO_X100] = (int) Math.round(steamRatio * 100);
        v[SafetyPanels.FL_PILOTS] = pilots;
        v[SafetyPanels.FL_FUEL] = fuel.getFluidAmount();
        v[SafetyPanels.FL_SAFE_M] = (int) Math.round(FlareSystem.distanceForFlux(s.heatReleaseW(), FlareSystem.K_CONTINUOUS));
        v[SafetyPanels.FL_FLAGS] = (nm3s > 0 && !s.flameOut() ? 1 << SafetyPanels.F_BURNING : 0)
                | (nm3s > 0 && s.flameOut() ? 1 << SafetyPanels.F_UNBURNT : 0) | (raining ? 1 << SafetyPanels.F_RAIN : 0)
                | (noSeal ? 1 << SafetyPanels.F_NO_SEAL : 0) | (noKo ? 1 << SafetyPanels.F_NO_KO : 0)
                | (noTip ? 1 << SafetyPanels.F_NO_TIP : 0) | (noSteam ? 1 << SafetyPanels.F_NO_STEAM : 0)
                | (flashback ? 1 << SafetyPanels.F_FLASHBACK : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case SafetyPanels.B_ACTION -> {
                if (!ignite()) player.displayClientMessage(SafetyText.msg("flare_no_fuel"), true);
            }
            case SafetyPanels.B_DOWN -> setSteamRatio(steamRatio - RATIO_STEP);
            case SafetyPanels.B_UP -> setSteamRatio(steamRatio + RATIO_STEP);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("fuel", fuel.writeToNBT(registries, new CompoundTag()));
        tag.putInt("pilots", pilots);
        tag.putDouble("ratio", steamRatio);
        tag.putDouble("fuel_debt", fuelDebt);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("fuel")) fuel.readFromNBT(registries, tag.getCompound("fuel"));
        pilots = tag.getInt("pilots");
        if (tag.contains("ratio")) steamRatio = tag.getDouble("ratio");
        fuelDebt = tag.getDouble("fuel_debt");
    }
}
