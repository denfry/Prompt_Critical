package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.metallurgy.ConverterUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Кислородная фурма {@code oxygen_lance} (08 §8.6.1) над горловиной конвертера: каретка с буфером кислорода (трубы
 * Create, газ {@code oxygen}) и высотой фурмы над ванной. Высоту и дутьё задаёт щит конвертера; фурма идёт к уставке
 * со скоростью лебёдки {@value #TRAVEL_M_S} м/с. Клиент рисует ход фурмы.
 */
public final class OxygenLanceBlockEntity extends IronworksBlockEntity {
    public static final double TRAVEL_M_S = 0.5;
    private final GasBuffer oxygen = new GasBuffer(8_000, s -> s.getFluid().isSame(PcFluids.OXYGEN.source.get()), this::setChanged);
    private double heightM = ConverterUnit.MAX_LANCE_M, setM = ConverterUnit.MAX_LANCE_M;
    private double clientHeight = ConverterUnit.MAX_LANCE_M;

    public OxygenLanceBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.OXYGEN_LANCE.get(), pos, state);
    }

    public GasBuffer oxygen() {
        return oxygen;
    }

    public double heightM() {
        return heightM;
    }

    public double setpointM() {
        return setM;
    }

    public void setSetpoint(double m) {
        setM = Math.max(0, Math.min(ConverterUnit.MAX_LANCE_M, m));
        setChanged();
    }

    /** Фурма поднята из горловины. */
    public boolean parked() {
        return heightM >= ConverterUnit.LANCE_PARKED_M;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        double step = TRAVEL_M_S * 0.05;
        double before = heightM;
        heightM += Math.max(-step, Math.min(step, setM - heightM));
        if (Math.abs(heightM - clientHeight) > 0.05 || before != heightM && heightM == setM) {
            clientHeight = heightM;
            syncClient();
        }
        setStateFlag(PcProperties.LOWERED, !parked());
    }

    /** Ход фурмы вниз от верхнего положения, м (для рендера). */
    public double renderDropM() {
        return ConverterUnit.MAX_LANCE_M - clientHeight;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return oxygen.input();
    }

    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(SteelworksText.msg("lance", SteelworksText.num(heightM), SteelworksText.num(setM), oxygen.amount()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void writeClient(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("height", heightM);
    }

    @Override
    protected void readClient(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("height")) clientHeight = tag.getDouble("height");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("oxygen", oxygen.save(registries));
        tag.putDouble("height_m", heightM);
        tag.putDouble("set_m", setM);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("oxygen")) oxygen.load(registries, tag.getCompound("oxygen"));
        if (tag.contains("height_m")) heightM = clientHeight = tag.getDouble("height_m");
        if (tag.contains("set_m")) setM = tag.getDouble("set_m");
    }
}
