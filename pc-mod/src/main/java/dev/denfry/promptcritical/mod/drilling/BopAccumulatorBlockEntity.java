package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.drilling.BopAccumulator;
import dev.denfry.promptcritical.sim.drilling.BopStack;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Аккумуляторная установка ПВО «Koomey» (09 §9.5.7, {@link BopAccumulator}): {@link #BOTTLES} баллонов по 41,6 л,
 * насос подзарядки на валу Create (64 об/мин — полная подача). Рычаги пульта закрывают и открывают элементы стека
 * буровой, к которой установка относится ({@link DrillersConsoleBlockEntity#near}); там же опрессовка. Сухой блок
 * ламинарии (резина) на установке — замена уплотнителя кольцевого превентора.
 */
public final class BopAccumulatorBlockEntity extends DeviceBlockEntity implements ShaftConsumer, FoundryDevice, PanelHost {
    /** Баллонов в установке: 8 × 11 гал — достаточно для кольцевого и трёх плашечных 13⅝″ по API Std 53. */
    public static final int BOTTLES = 8;
    public static final double NOMINAL_RPM = 64;

    private final BopAccumulator acc = new BopAccumulator(BOTTLES);
    private final ShaftInput shaft = new ShaftInput();
    private double pumpW;

    public BopAccumulatorBlockEntity(BlockPos pos, BlockState state) {
        super(DrillingBlockEntities.BOP_ACCUMULATOR.get(), pos, state);
    }

    public BopAccumulator accumulator() {
        return acc;
    }

    private long tick() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        return b == null ? 0 : b.world().clock().tick();
    }

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        shaft.sample(rpm, healthy, tick);
    }

    @Override
    public double shaftPowerW() {
        return pumpW;
    }

    @Override
    public double ratedShaftW() {
        // подача полного запаса за 15 минут против 21 МПа
        return acc.pumpRateM3s() * BopAccumulator.MAX_PA / BopAccumulator.PUMP_EFFICIENCY;
    }

    @Override
    public double nominalShaftRpm() {
        return NOMINAL_RPM;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        double dt = 0.05 * b.world().clock().kSlow();
        double drive = Math.min(1, shaft.rpm(tick()) / NOMINAL_RPM);
        pumpW = acc.recharge(dt, drive);
        if (level.getGameTime() % 10 == 0) {
            BlockState st = getBlockState();
            boolean charged = acc.pressurePa() >= 0.9 * BopAccumulator.MAX_PA;
            if (st.getValue(PcProperties.CHARGED) != charged)
                level.setBlock(worldPosition, st.setValue(PcProperties.CHARGED, charged), Block.UPDATE_ALL);
        }
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    @Nullable
    private DrillersConsoleBlockEntity console() {
        return DrillersConsoleBlockEntity.near(level, worldPosition);
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(Items.DRIED_KELP_BLOCK);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        DrillersConsoleBlockEntity c = console();
        if (c == null || !c.replacePacker()) return ItemInteractionResult.CONSUME;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(DrillingText.msg("bop_packer"), true);
        level.playSound(null, worldPosition, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 1.0f, 0.8f);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        DrillersConsoleBlockEntity c = console();
        if (c == null) {
            player.displayClientMessage(DrillingText.msg("barite_no_rig"), true);
            return;
        }
        switch (id) {
            case DrillingPanels.AB_ANNULAR -> c.toggleComponent(BopStack.Component.ANNULAR, player);
            case DrillingPanels.AB_PIPE -> c.toggleComponent(BopStack.Component.PIPE_RAM, player);
            case DrillingPanels.AB_BLIND -> c.toggleComponent(BopStack.Component.BLIND_RAM, player);
            case DrillingPanels.AB_SHEAR -> c.toggleComponent(BopStack.Component.SHEAR_RAM, player);
            case DrillingPanels.AB_TEST -> c.pressureTest(player);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    public PanelLayout panelLayout() {
        return DrillingPanels.ACCUMULATOR;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[DrillingPanels.A_P_X100] = (int) Math.round(acc.pressurePa() / 1e4);
        out[DrillingPanels.A_KW10] = (int) Math.round(pumpW / 100);
        out[DrillingPanels.A_BOTTLES] = acc.bottles();
        out[DrillingPanels.A_TEST_AGE_D] = -1;
        DrillersConsoleBlockEntity c = console();
        BopStack st = c == null ? null : c.bopStack();
        int f = 0;
        if (st != null) {
            f |= 1 << DrillingPanels.AF_RIG;
            BopStack.Fault worst = BopStack.Fault.NONE;
            double oldest = Double.NEGATIVE_INFINITY;
            for (BopStack.Element e : st.elements()) {
                if (e.closed()) f |= 1 << switch (e.kind) {
                    case ANNULAR -> DrillingPanels.AF_ANNULAR;
                    case PIPE_RAM -> DrillingPanels.AF_PIPE;
                    case BLIND_RAM -> DrillingPanels.AF_BLIND;
                    case SHEAR_RAM -> DrillingPanels.AF_SHEAR;
                };
                if (e.fault() != BopStack.Fault.NONE) worst = e.fault();
                if (e.kind == BopStack.Component.ANNULAR) out[DrillingPanels.A_CLOSURES] = e.closures();
                oldest = Math.max(oldest, c.day() - e.lastTestDay());
            }
            if (st.sealed()) f |= 1 << DrillingPanels.AF_SEALED;
            if (acc.meetsApi53(st.requiredVolumeM3())) f |= 1 << DrillingPanels.AF_API53;
            if (worst != BopStack.Fault.NONE) f |= 1 << DrillingPanels.AF_FAULT;
            if (st.pipeSheared()) f |= 1 << DrillingPanels.AF_SHEARED;
            out[DrillingPanels.A_FAULT] = worst.ordinal();
            if (Double.isFinite(oldest)) out[DrillingPanels.A_TEST_AGE_D] = (int) Math.floor(oldest);
        }
        out[DrillingPanels.A_FLAGS] = f;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("fluid_m3", acc.fluidM3());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fluid_m3")) acc.restoreFluid(tag.getDouble("fluid_m3"));
    }
}
