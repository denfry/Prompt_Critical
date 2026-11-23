package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.scada.OperatorLicenseItem.LicenseSpec;
import dev.denfry.promptcritical.sim.automation.twin.Fault;
import dev.denfry.promptcritical.sim.automation.twin.TwinPlants;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Пульт тренажёра (11 §11.3.2): место инструктора {@code instructor_station} — урок, пуск, скорость, ввод отказов,
 * останов, выдача допуска; пульт оператора {@code simulator_console} — показания приборов копии и механизмы, снятие
 * форсировок. Оба работают с ближайшим шкафом {@code training_simulator} в
 * {@value TrainingSimulatorBlockEntity#LINK_RADIUS} блоках, а не с реальной установкой. Лампа «активен» — шкаф ведёт урок.
 */
public class SimulatorDeskBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    /** Пульт тренажёра, Вт: прибор питается от розетки, щита или ИБП рядом, без питания не работает. */
    public static final double POWER_W = 1e3;
    /** Событие {@code sim_event}: выдано удостоверение; тег {@code level}, значение — уровень (0 стажёр, 1 оператор, 2 НСС). */
    public static final String OPERATOR_LICENSE = "promptcritical:operator_license";

    private static final Tr NO_SIM = PanelText.tr("twin.msg.no_sim", "No training simulator cabinet within 8 blocks",
            "В 8 блоках нет шкафа тренажёра"),
            NO_PASS = PanelText.tr("twin.msg.no_pass", "No lesson passed with a score of %s or more yet",
                    "Пока нет уроков, сданных с баллом не ниже %s"),
            ISSUED = PanelText.tr("twin.msg.license", "License issued: %s", "Выдано удостоверение: %s");

    private final boolean instructor;
    private int ticks;

    public SimulatorDeskBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean instructor) {
        super(type, pos, state);
        this.instructor = instructor;
    }

    public static SimulatorDeskBlockEntity instructor(BlockPos pos, BlockState state) {
        return new SimulatorDeskBlockEntity(ScadaBlockEntities.INSTRUCTOR_STATION.get(), pos, state, true);
    }

    public static SimulatorDeskBlockEntity console(BlockPos pos, BlockState state) {
        return new SimulatorDeskBlockEntity(ScadaBlockEntities.SIMULATOR_CONSOLE.get(), pos, state, false);
    }

    @Nullable
    public TrainingSimulatorBlockEntity simulator() {
        return level == null ? null : TrainingSimulatorBlockEntity.nearest(level, worldPosition);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || !dev.denfry.promptcritical.mod.grid.MachinePower.powered(level, worldPosition, POWER_W) || ++ticks % 10 != 0) return;
        TrainingSimulatorBlockEntity sim = simulator();
        boolean active = sim != null && sim.run() != null;
        BlockState s = getBlockState();
        if (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) != active)
            sl.setBlock(worldPosition, s.setValue(BlockStateProperties.LIT, active), Block.UPDATE_CLIENTS);
    }

    // ------------------------------------------------------------------ допуск (11 §11.3.5)

    /**
     * Уровень по журналу шкафа: все уроки — начальник смены; все уроки одной установки — оператор этой установки;
     * хоть один — стажёр. {@code null} — ни одного урока с проходным баллом.
     */
    @Nullable
    static LicenseSpec license(Map<String, Double> best, double pass, long day, String holder, String station) {
        List<String> lessons = TrainingSimulatorBlockEntity.lessons();
        List<String> passed = lessons.stream().filter(l -> best.getOrDefault(l, 0.0) >= pass).toList();
        if (passed.isEmpty()) return null;
        if (passed.size() == lessons.size()) return new LicenseSpec(OperatorLicenseItem.Level.SHIFT_SUPERVISOR.ordinal(), "", day, holder, station);
        for (String plant : TwinPlants.IDS) {
            List<String> of = lessons.stream().filter(l -> TrainingSimulatorBlockEntity.plantOf(l).equals(plant)).toList();
            if (!of.isEmpty() && passed.containsAll(of))
                return new LicenseSpec(OperatorLicenseItem.Level.OPERATOR.ordinal(), plant, day, holder, station);
        }
        return new LicenseSpec(OperatorLicenseItem.Level.TRAINEE.ordinal(), TrainingSimulatorBlockEntity.plantOf(passed.getFirst()), day,
                holder, station);
    }

    public boolean issueLicense(ServerPlayer player, TrainingSimulatorBlockEntity sim) {
        double pass = ServerConfig.CONTENT_LICENSE_PASS_SCORE.get();
        BlockPos p = sim.getBlockPos();
        LicenseSpec spec = license(sim.bestOf(player.getUUID()), pass, player.level().getDayTime() / 24000L,
                player.getGameProfile().getName(), p.getX() + " " + p.getY() + " " + p.getZ());
        if (spec == null) {
            player.displayClientMessage(NO_PASS.get(PanelText.num(pass, 2)), false);
            return false;
        }
        ItemStack card = new ItemStack(PcItems.OPERATOR_LICENSE.get());
        card.set(OperatorLicenseItem.LICENSE_SPEC.get(), spec);
        if (!player.getInventory().add(card)) player.drop(card, false);
        player.displayClientMessage(ISSUED.get(OperatorLicenseItem.LEVEL_TEXT[spec.levelEnum().ordinal()].get()), false);
        SimAchievements.direct(player, OPERATOR_LICENSE, "level", spec.level());
        return true;
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return instructor ? TwinPanels.INSTRUCTOR : TwinPanels.CONSOLE;
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
        TrainingSimulatorBlockEntity sim = simulator();
        if (sim == null) java.util.Arrays.fill(out, 0);
        else sim.fill(out);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        TrainingSimulatorBlockEntity sim = simulator();
        if (sim == null) {
            player.displayClientMessage(NO_SIM.get(), false);
            return;
        }
        if (!instructor) {
            if (id == TwinPanels.C_FORCES) sim.clearForces(player);
            else if (id >= 0 && id < 2 * TwinPanels.ACTUATORS) sim.operate(player, id / 2, id % 2 == 1);
            return;
        }
        switch (id) {
            case TwinPanels.I_LESSON -> sim.nextLesson();
            case TwinPanels.I_START -> sim.start(player);
            case TwinPanels.I_SPEED -> sim.cycleSpeed();
            case TwinPanels.I_FAULT -> {
                Fault f = sim.injectNext();
                if (f != null) player.displayClientMessage(TwinWorkstationBlockEntity.FAULT.get(f.label()), false);
            }
            case TwinPanels.I_STOP -> sim.abort();
            case TwinPanels.I_LICENSE -> issueLicense(player, sim);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ руки

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
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
}
