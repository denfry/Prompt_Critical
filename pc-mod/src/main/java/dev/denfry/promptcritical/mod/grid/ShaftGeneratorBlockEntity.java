package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.GridUnit;
import dev.denfry.promptcritical.sim.grid.MachineState;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * {@code shaft_generator} на валу Create (07 §7.3.1): вал входит сзади, выводы — остальные грани. Возбудитель
 * {@code generator_exciter} ставится вплотную. Несинхронное включение даёт разовый скачок стресса сети Create (§7.3.5);
 * муфта {@code generator_coupling} со срезным штифтом ставится ПКМ.
 */
public class ShaftGeneratorBlockEntity extends KineticMachineBlockEntity<GeneratorUnit> implements GridDevice, PanelHost {
    private int ticks;
    private boolean registered;
    /** Тик, когда сеть Create была перегружена в последний раз (журнал и тесты). */
    private long overstressedAt = Long.MIN_VALUE;

    public ShaftGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(GridBlockEntities.SHAFT_GENERATOR.get(), pos, state);
    }

    @Override
    @Nullable
    protected GeneratorUnit unit(SimLevelBinding binding) {
        GridSubsystem g = binding.world().subsystem(GridSubsystem.class);
        return g == null || simId().isNone() || !binding.world().topology().exists(simId()) ? null : g.generator(simId());
    }

    @Nullable
    public GeneratorUnit unit() {
        return super.unit();
    }

    @Override
    @Nullable
    public GridUnit gridUnit(GridSubsystem grid) {
        return simId().isNone() ? null : grid.generator(simId());
    }

    @Override
    protected ShaftInput shaft(GeneratorUnit unit) {
        return unit.shaft;
    }

    @Override
    protected double powerW(GeneratorUnit unit) {
        return unit.shaftPowerW();
    }

    @Override
    protected double ratedW() {
        return GeneratorUnit.RATED_POWER_W;
    }

    @Override
    protected double nominalRpm() {
        GeneratorUnit u = unit();
        return u == null ? 64 : u.gearRpm();
    }

    private Direction facing() {
        return getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        GridDevice.faces(out, worldPosition, facing(), 0, 0, 0, Direction.NORTH, Direction.WEST, Direction.EAST, Direction.UP,
                Direction.DOWN);
        return List.of(out);
    }

    private boolean exciterAdjacent() {
        for (Direction d : Direction.values())
            if (level.getBlockState(worldPosition.relative(d)).is(PcBlocks.GENERATOR_EXCITER.get())) return true;
        return false;
    }

    @Override
    protected void serverTick(SimLevelBinding binding, GeneratorUnit u) {
        ticks++;
        if (ticks % 20 == 1) u.setExciterPresent(exciterAdjacent());
        double shock = u.takeShockW();
        if (shock > 0) {
            spikeStress(shock);
            level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.2f, 0.5f);
        }
        if (isOverStressed()) overstressedAt = level.getGameTime();
        if (ticks % 5 == 0) setStateFlag(PcProperties.RUNNING, u.speedPu() > GeneratorUnit.ONLINE_SPEED);
    }

    /** Сеть Create была перегружена на тике {@code gameTime} или позже. */
    public boolean overstressedSince(long gameTime) {
        return overstressedAt >= gameTime;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) {
                b.grids().register(worldPosition);
                registered = true;
            }
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (registered && level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) b.grids().unregister(worldPosition);
            registered = false;
        }
    }

    // --- руки ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.GENERATOR_COUPLING.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        GeneratorUnit u = unit();
        if (u == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (u.shaftDamage() >= 1) {
            player.displayClientMessage(GridText.msg(GridText.SHAFT_BROKEN), true);
            return ItemInteractionResult.CONSUME;
        }
        if (u.shearPinInstalled() && !u.couplingSheared()) return ItemInteractionResult.CONSUME;
        u.installShearPin();
        stack.consume(1, player);
        player.displayClientMessage(GridText.msg(GridText.COUPLING_FITTED), true);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        GeneratorUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        u.setFieldOn(!u.fieldOn());
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return GridPanels.GENERATOR;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[GridPanels.G_SHAFT_RPM10] = shaftRpm10();
        GeneratorUnit u = unit();
        if (u == null) return;
        out[GridPanels.G_FREQ_HZ100] = (int) Math.round(u.frequencyHz() * 100);
        out[GridPanels.G_VOLT_V] = (int) Math.round(u.terminalVoltageV());
        out[GridPanels.G_P_KW10] = (int) Math.round(u.electricalPowerW() / 100);
        out[GridPanels.G_Q_KVAR10] = (int) Math.round(u.reactivePowerVar() / 100);
        out[GridPanels.G_GEAR] = u.gearRpm();
        out[GridPanels.G_TRIM] = u.trimPermil();
        out[GridPanels.G_VREF_PERMIL] = (int) Math.round(u.voltageReferencePu() * 1000);
        out[GridPanels.G_CLASS_V] = (int) u.voltageClass().volts;
        out[GridPanels.G_STATE] = u.state().ordinal();
        out[GridPanels.G_FLAGS] = (u.exciterPresent() ? 1 : 0) | (u.fieldOn() ? 2 : 0)
                | (u.state() == MachineState.SYNCHRONIZED || u.state() == MachineState.LOADED ? 4 : 0)
                | (u.couplingSheared() ? 8 : 0) | (u.shaftDamage() >= 1 || u.statorDamage() >= 1 ? 16 : 0)
                | (u.shearPinInstalled() ? 32 : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        GeneratorUnit u = unit();
        if (u == null) return;
        switch (id) {
            case GridPanels.BUTTON_FIELD -> u.setFieldOn(!u.fieldOn());
            case GridPanels.BUTTON_VREF_DOWN -> u.setVoltageReferencePu(u.voltageReferencePu() - 0.005);
            case GridPanels.BUTTON_VREF_UP -> u.setVoltageReferencePu(u.voltageReferencePu() + 0.005);
            case GridPanels.BUTTON_TRIM_DOWN -> u.setTrimPermil(u.trimPermil() - 1);
            case GridPanels.BUTTON_TRIM_UP -> u.setTrimPermil(u.trimPermil() + 1);
            case GridPanels.BUTTON_GEAR -> {
                u.cycleGear();
                player.displayClientMessage(GridText.msg(GridText.GEAR, u.gearRpm()), true);
            }
            case GridPanels.BUTTON_CLASS -> {
                if (u.speedPu() > 0.05) {
                    player.displayClientMessage(GridText.msg(GridText.STOP_FIRST), true);
                    return;
                }
                u.setVoltageClass(u.voltageClass() == VoltageClass.LV_0_4 ? VoltageClass.MV_6_3 : VoltageClass.LV_0_4);
                player.displayClientMessage(GridText.msg(GridText.VOLTAGE_CLASS, u.voltageClass().volts / 1000), true);
            }
            default -> {
            }
        }
    }
}
