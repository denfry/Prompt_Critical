package dev.denfry.promptcritical.mod.hazard;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.hazard.OilSpill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Олеофильный барабанный нефтесборщик {@code oil_skimmer} (14 §14.5.4): барабан крутит вал Create с любой стороны;
 * снимает плёнку пятна в радиусе {@value #RADIUS} блоков с производительностью Q = Q_ном·E, E = h/(h + 2 мм), где h —
 * толщина плёнки (тонкое пятно сначала стягивают бонами). Q_ном = {@value #NOMINAL_M3_H} м³/ч нефти при
 * {@value #NOMINAL_RPM} об/мин и выше [BALANCE]. Собранное — эмульсия {@code recovered_oil} с долей воды пятна
 * (до 70 % через сутки) — в бак на {@value #TANK_L} л, отбор трубой Create.
 */
public class OilSkimmerBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    public static final int RADIUS = 4, TANK_L = 8000;
    public static final double NOMINAL_M3_H = 10, NOMINAL_RPM = 64;

    private final FeedTank tank = new FeedTank(PcFluids.RECOVERED_OIL.source.get(), TANK_L);
    private double rpm, thicknessM, efficiency, remainderL, totalL;
    private int ticks;

    public OilSkimmerBlockEntity(BlockPos pos, BlockState state) {
        super(HazardBlockEntities.OIL_SKIMMER.get(), pos, state);
    }

    public FeedTank tank() {
        return tank;
    }

    public double totalL() {
        return totalL;
    }

    /** Стенд (GameTest): вращение без вала. */
    private double benchRpm;

    public void setBenchRpm(double r) {
        benchRpm = r;
    }

    private double shaftRpm() {
        if (benchRpm != 0) return benchRpm;
        for (Direction d : Direction.values())
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof KineticBlockEntity k && k.getSpeed() != 0) return Math.abs(k.getSpeed());
        return 0;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || ++ticks % 20 != 0) return;
        rpm = shaftRpm();
        OilSpills.Spill s = rpm > 0 ? OilSpills.spillNear(sl, worldPosition, RADIUS) : null;
        boolean on = s != null && tank.amount() < TANK_L - 1;
        thicknessM = s == null ? 0 : s.thicknessM();
        efficiency = OilSpill.skimmerEfficiency(thicknessM);
        if (on) {
            double m3 = NOMINAL_M3_H / 3600 * efficiency * Math.min(1, rpm / NOMINAL_RPM);
            double kg = OilSpills.recover(sl, s, m3 * s.model().oilDensity());
            double oilL = kg / s.model().oilDensity() * 1000;
            double litres = oilL / (1 - s.model().waterFraction()) + remainderL;
            int mb = (int) litres;
            remainderL = litres - mb;
            tank.fill(new net.neoforged.neoforge.fluids.FluidStack(tank.fluid(), mb), IFluidHandler.FluidAction.EXECUTE);
            totalL += mb;
        }
        BlockState st = getBlockState();
        if (st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING) != on)
            sl.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, on), Block.UPDATE_CLIENTS);
        if (ticks % 200 == 0) setChanged();
    }

    @Override
    public InteractionResult use(Player player) {
        String state = rpm <= 0 ? HazardText.msg("skimmer.off").getString()
                : thicknessM <= 0 ? HazardText.msg("skimmer.dry").getString() : HazardText.msg("skimmer.on").getString();
        player.displayClientMessage(HazardText.msg("skimmer", state, Math.round(thicknessM * 1e4) / 10.0, Math.round(efficiency * 100),
                Math.round(totalL)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public IFluidHandler fluids(@Nullable Direction side) {
        return tank;
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
        tank.save(tag, "tank");
        tag.putDouble("total", totalL);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.load(tag, "tank");
        totalL = tag.getDouble("total");
    }
}
