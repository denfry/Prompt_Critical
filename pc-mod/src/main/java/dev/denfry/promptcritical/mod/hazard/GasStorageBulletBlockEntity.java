package dev.denfry.promptcritical.mod.hazard;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ops.FireDeviceBlockEntity;
import dev.denfry.promptcritical.mod.ops.FireService;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.hazard.BleveVessel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Ёмкость СУГ {@code gas_storage_bullet} (09 §9.8.4, §9.11.6): горизонтальный сосуд {@value #VOLUME_M3} м³ с пропаном
 * под собственным давлением, предохранительный клапан на расчётном давлении {@value #DESIGN_PA} Па (абс., 1,7 МПа
 * изб. — стандарт подземных и наземных ёмкостей СУГ [моё]), стенка {@value #WALL_M} м. Модель — {@link BleveVessel}.
 * <ul>
 *   <li>Огонь вплотную к ёмкости (горящие блоки в {@value #ENGULF_RADIUS} блоках) — охват пожаром,
 *   {@link HazardService#ENGULFMENT_KW_M2} кВт/м²; иначе — поток от струйных пожаров, горящих проливов и огненных
 *   шаров ({@link HazardService#heatFluxAt}).</li>
 *   <li>Дренчер или лафетный ствол в работе в {@value #DELUGE_RADIUS} блоках — орошение (подача воды по трубе здесь не
 *   проверяется).</li>
 *   <li>Клапан сбрасывает пар в атмосферу — утечку пропана; в пламени она сразу горит факелом (§9.11.5).</li>
 *   <li>Разрушение — BLEVE: огненный шар всей массы и ударная волна (0,4 энергии расширения по Броуду) через
 *   {@link HazardService#bleve}; ёмкость уничтожена. В режиме {@code creative_safe} ёмкость «держит», содержимое
 *   теряется.</li>
 * </ul>
 */
public class GasStorageBulletBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final double VOLUME_M3 = 50, DESIGN_PA = 1.8e6, WALL_M = 0.02;
    public static final int ENGULF_RADIUS = 2, DELUGE_RADIUS = 6;
    /** Литров жидкого пропана в килограмме. */
    static final double KG_PER_L = BleveVessel.Liquefied.PROPANE.liquidDensity / 1000;

    private final FeedTank propane = new FeedTank(PcFluids.PROPANE.source.get(), (int) (VOLUME_M3 * 1000));
    private BleveVessel vessel = newVessel();
    private double fireKwM2;
    private boolean deluge;
    private int ticks;

    public GasStorageBulletBlockEntity(BlockPos pos, BlockState state) {
        super(HazardBlockEntities.GAS_STORAGE_BULLET.get(), pos, state);
    }

    private static BleveVessel newVessel() {
        return new BleveVessel(BleveVessel.Liquefied.PROPANE, VOLUME_M3, DESIGN_PA, WALL_M);
    }

    public BleveVessel vessel() {
        return vessel;
    }

    public FeedTank propane() {
        return propane;
    }

    public double fireKwM2() {
        return fireKwM2;
    }

    String releaseId() {
        return "bullet@" + worldPosition.toShortString();
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || ++ticks % 20 != 0) return;
        vessel.setLiquidKg(propane.amount() * KG_PER_L);
        Vec3 c = Vec3.atCenterOf(worldPosition).add(0, 0.5, 0);
        fireKwM2 = FireService.fires(sl, worldPosition.above(), ENGULF_RADIUS).isEmpty()
                ? HazardService.heatFluxAt(sl, c) : HazardService.ENGULFMENT_KW_M2;
        deluge = false;
        for (FireDeviceBlockEntity d : Ironworks.blockEntities(sl, new AABB(worldPosition).inflate(DELUGE_RADIUS), FireDeviceBlockEntity.class))
            deluge |= (d.kind() == FireDeviceBlockEntity.Kind.DELUGE || d.kind() == FireDeviceBlockEntity.Kind.MONITOR) && d.running();
        vessel.step(1, fireKwM2, deluge);
        if (vessel.reliefOpen()) {
            propane.take(vessel.reliefKgS() / KG_PER_L);
            HazardService.vent(sl, releaseId(), worldPosition.above(2), BleveVessel.Liquefied.PROPANE.gas, vessel.reliefKgS(), 0, fireKwM2 > 0);
            sl.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 2.2, worldPosition.getZ() + 0.5, 6, 0.1, 0.4, 0.1, 0.05);
        }
        if (vessel.failed()) {
            fail(sl);
            return;
        }
        BlockState s = getBlockState();
        boolean alarm = vessel.wallK() - 273.15 > 300 || vessel.reliefOpen();
        if (s.hasProperty(PcProperties.ALARM) && s.getValue(PcProperties.ALARM) != alarm)
            sl.setBlock(worldPosition, s.setValue(PcProperties.ALARM, alarm), Block.UPDATE_CLIENTS);
        if (ticks % 200 == 0) setChanged();
    }

    /** BLEVE: огненный шар, ударная волна, ёмкость разрушена (в creative_safe — только потеря содержимого). */
    private void fail(ServerLevel sl) {
        double mass = vessel.fireballMassKg(), blast = vessel.blastEnergyJ();
        HazardService.bleve(sl, worldPosition, mass, blast, releaseId());
        propane.take(propane.amount());
        vessel = newVessel();
        if (HazardService.harmful()) sl.destroyBlock(worldPosition, false);
        else setChanged();
    }

    /** Стенд (GameTest): разогреть стенку парового пространства. */
    public void heatWall(double kelvin) {
        vessel.setWallK(kelvin);
    }

    /** Стенд: жидкость при температуре {@code kelvin} (давление насыщения). */
    public void heatLiquid(double kelvin) {
        vessel.setLiquidK(kelvin);
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return HazardPanels.BULLET;
    }

    @Override
    public void readPanel(int[] v) {
        v[HazardPanels.WALL_C] = (int) Math.round(vessel.wallK() - 273.15);
        v[HazardPanels.PRESSURE] = (int) Math.round(vessel.pressurePa() / 1e4);
        v[HazardPanels.FILL] = (int) Math.round(propane.fill() * 1000);
        v[HazardPanels.FIRE] = (int) Math.round(fireKwM2);
        v[HazardPanels.FLAGS] = (vessel.reliefOpen() ? 1 << HazardPanels.F_RELIEF : 0) | (deluge ? 1 << HazardPanels.F_DELUGE : 0)
                | (fireKwM2 > 0 ? 1 << HazardPanels.F_FIRE : 0) | (vessel.wallK() - 273.15 > 300 ? 1 << HazardPanels.F_HOT : 0);
        v[HazardPanels.MASS_T10] = (int) Math.round(vessel.liquidKg() / 100);
        v[HazardPanels.LIQUID_C] = (int) Math.round(vessel.liquidK() - 273.15);
        v[HazardPanels.FAIL_BAR] = (int) Math.round(vessel.failurePa() / 1e4);
    }

    // ------------------------------------------------------------------ руки

    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(HazardText.msg("bullet", Math.round(vessel.liquidKg() / 100) / 10.0, Math.round(vessel.wallK() - 273.15),
                Math.round(vessel.pressurePa() / 1e4) / 10.0, vessel.reliefOpen() ? HazardText.msg("bullet.relief").getString() : ""), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public IFluidHandler fluids(@Nullable Direction side) {
        return propane;
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
        propane.save(tag, "propane");
        tag.putDouble("wallK", vessel.wallK());
        tag.putDouble("liquidK", vessel.liquidK());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        propane.load(tag, "propane");
        if (tag.contains("wallK")) vessel.setWallK(tag.getDouble("wallK"));
        if (tag.contains("liquidK")) vessel.setLiquidK(tag.getDouble("liquidK"));
    }
}
