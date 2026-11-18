package dev.denfry.promptcritical.mod.hazard;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.grid.GridLoad;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.GridUnit;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Станция катодной защиты {@code cp_rectifier} (09 §9.8.3): выпрямитель от сети 07 с анодным заземлением. Ток задаёт
 * игрок (кнопки щита, шаг {@value #STEP_A} А, до {@value #MAX_A} А); он делится поровну между участками трубопровода
 * (камерами запуска) в {@value PigTrapBlockEntity#CP_RADIUS} блоках. Мощность из сети — I·U при
 * U = {@value #VOLTS} В [моё: выход типовой СКЗ до 48 В].
 */
public class CpRectifierBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost, GridDevice {
    public static final double STEP_A = 0.1, MAX_A = 10, VOLTS = 24;

    private double setA = 0.5;
    private boolean powered, benchPower;
    private int sections = 1, ticks;
    private double offV;

    public CpRectifierBlockEntity(BlockPos pos, BlockState state) {
        super(HazardBlockEntities.CP_RECTIFIER.get(), pos, state);
    }

    public double setA() {
        return setA;
    }

    public void setCurrentA(double a) {
        setA = Math.clamp(a, 0, MAX_A);
        setChanged();
    }

    public boolean powered() {
        return powered;
    }

    /** Стенд (GameTest): питание без сети. */
    public void setBenchPower(boolean on) {
        benchPower = on;
    }

    /** Ток, приходящийся на один участок, А. */
    public double currentShareA(ServerLevel sl) {
        return powered ? setA / Math.max(1, sections) : 0;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || ++ticks % 20 != 0) return;
        powered = setA > 0 && (benchPower || GridLoad.draw(level, simId(), setA * VOLTS));
        int n = 0;
        double v = 0;
        for (PigTrapBlockEntity t : Ironworks.blockEntities(sl, new AABB(worldPosition).inflate(PigTrapBlockEntity.CP_RADIUS), PigTrapBlockEntity.class))
            if (t.launcher()) {
                n++;
                v += t.offV();
            }
        sections = n;
        offV = n == 0 ? 0 : v / n;
        BlockState s = getBlockState();
        if (s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING) != powered)
            sl.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, powered), Block.UPDATE_CLIENTS);
        if (ticks % 200 == 0) setChanged();
    }

    @Override
    public PanelLayout panelLayout() {
        return HazardPanels.CP_RECTIFIER;
    }

    @Override
    public void readPanel(int[] v) {
        v[HazardPanels.C_SET] = (int) Math.round(setA * 1000);
        v[HazardPanels.C_POTENTIAL] = (int) Math.round(-offV * 1000);
        v[HazardPanels.C_SECTIONS] = sections;
        v[HazardPanels.C_FLAGS] = powered ? 1 : 0;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == HazardPanels.B_DOWN) setCurrentA(setA - STEP_A);
        else if (id == HazardPanels.B_UP) setCurrentA(setA + STEP_A);
    }

    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(HazardText.msg("rectifier", Math.round(setA * 10) / 10.0, sections), true);
        return InteractionResult.SUCCESS;
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
    public List<List<GridDevice.Face>> terminals() {
        return List.of(GridDevice.allFaces(worldPosition));
    }

    @Override
    @Nullable
    public GridUnit gridUnit(GridSubsystem grid) {
        return simId().isNone() ? null : grid.consumer(simId());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        GridLoad.register(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        GridLoad.unregister(level, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("setA", setA);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("setA")) setA = tag.getDouble("setA");
    }
}
