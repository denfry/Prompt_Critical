package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.content.SimAchievements;
import net.minecraft.server.level.ServerLevel;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.PipeKind;
import dev.denfry.promptcritical.sim.metallurgy.PipeMill;
import dev.denfry.promptcritical.sim.metallurgy.PipeSpec;
import dev.denfry.promptcritical.sim.metallurgy.SteelmakingConfig;
import dev.denfry.promptcritical.sim.metallurgy.StockShape;
import dev.denfry.promptcritical.sim.metallurgy.StockState;
import java.util.Arrays;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Прошивной стан Маннесмана {@code piercing_mill} 3×2×1 (08 §8.7.3) на валу Create через порт: нагретая заготовка
 * или слиток с рольганга слева (поперёк фасада) прошивается на оправке за {@value #PIERCE_S} с; пильгер и калибровка
 * свёрнуты в {@link PipeMill#seamless}. Окно 1200–1250 °C, холоднее 1150 °C оправка не идёт. Труба — по 1 м
 * ({@code seamless_pipe} с {@code pipe_spec}), сколько выйдет из массы заготовки; уходит на рольганг справа или в
 * выходы. Типоразмер и назначение — со щита.
 */
public final class PiercingMillBlockEntity extends IronworksBlockEntity implements ShaftConsumer, PanelHost, PieceReceiver {
    public static final double PIERCE_S = 3, RATED_W = 800_000, MIN_RPM = 16, EXIT_CHILL_K = 50;
    /** Типоразмеры, мм: наружный диаметр × стенка [BALANCE]. */
    public static final int[][] SIZES = {{57, 4}, {76, 5}, {89, 6}, {114, 7}, {159, 8}, {219, 8}};
    public static final PipeKind[] KINDS = {PipeKind.BOILER, PipeKind.LINE, PipeKind.STRUCTURAL, PipeKind.HEAT_EXCHANGER};
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.OUTPUT,
            MachineInventory.Role.OUTPUT, MachineInventory.Role.OUTPUT};

    private final MachineInventory inv = new MachineInventory(ROLES, (slot, s) -> slot == 0 && pierceable(s), this::setChanged);
    private int size = 1, kind;
    private ItemStack billet = ItemStack.EMPTY;
    private Direction travel = Direction.EAST;
    private double leftS, rpm;
    private boolean healthy;
    private int lastPipes;

    public PiercingMillBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.PIERCING_MILL.get(), pos, state);
    }

    static boolean pierceable(ItemStack s) {
        StockShape sh = Steelworks.shape(s);
        return (sh == StockShape.BILLET || sh == StockShape.INGOT) && Steelworks.stock(s) != null;
    }

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        this.rpm = rpm;
        this.healthy = healthy;
    }

    @Override
    public double shaftPowerW() {
        if (!billet.isEmpty()) return RATED_W;
        return Math.abs(rpm) >= MIN_RPM ? 0.02 * RATED_W : 0;
    }

    @Override
    public double ratedShaftW() {
        return RATED_W;
    }

    @Override
    public double nominalShaftRpm() {
        return 64;
    }

    public void setSize(int index, PipeKind k) {
        size = Math.floorMod(index, SIZES.length);
        for (int i = 0; i < KINDS.length; i++) if (KINDS[i] == k) kind = i;
        setChanged();
    }

    private boolean ready() {
        return billet.isEmpty() && healthy && Math.abs(rpm) >= MIN_RPM;
    }

    private boolean start(ItemStack in, Direction dir) {
        if (!ready() || !pierceable(in)) return false;
        if (Steelworks.tempK(in, level.getGameTime()) < PipeMill.PIERCE_MIN_K) {
            level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4f, 1.6f);
            return false;
        }
        billet = in.copyWithCount(1);
        travel = dir;
        leftS = PIERCE_S;
        setChanged();
        return true;
    }

    @Override
    public boolean acceptPiece(ItemStack in, Direction dir) {
        return dir.getAxis() == facing().getClockWise().getAxis() && start(in, dir);
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        ItemStack in = inv.getStackInSlot(0);
        if (!in.isEmpty() && ready() && start(in, facing().getClockWise())) in.shrink(1);
        if (!billet.isEmpty() && healthy && Math.abs(rpm) >= MIN_RPM) {
            leftS -= 0.05;
            if (leftS <= 0) finish();
        }
        setStateFlag(PcProperties.RUNNING, !billet.isEmpty());
    }

    private void finish() {
        long now = level.getGameTime();
        StockState s = Steelworks.state(billet, now);
        SteelworksComponents.SteelStock st = Steelworks.stock(billet);
        int[] sz = SIZES[size];
        double od = sz[0] / 1000.0, wall = sz[1] / 1000.0;
        PipeSpec p = s == null ? null : PipeMill.seamless(s, KINDS[kind], od, wall, st.heatNo(), SteelmakingConfig.defaults(),
                new SplittableRandom(level.random.nextLong()));
        billet = ItemStack.EMPTY;
        setChanged();
        if (p == null) return;
        int n = (int) Math.floor(s.massKg() / SteelworksComponents.PipeData.kgPerM(od, wall) + 1e-9);
        lastPipes = n;
        if (n <= 0) return;
        if (level instanceof ServerLevel sl) SimAchievements.nearby(sl, worldPosition, SimAchievements.SEAMLESS_PIPE, "pipes", n);
        ItemStack pipes = new ItemStack(PcItems.SEAMLESS_PIPE.get(), n);
        pipes.set(SteelworksComponents.PIPE_SPEC.get(), SteelworksComponents.PipeData.of(p));
        pipes.set(SteelworksComponents.STEEL_STOCK.get(), SteelworksComponents.SteelStock.of(s.grade(), wall, od, s.porous(), s.cracked(),
                st.heatNo(), s.tempK() - EXIT_CHILL_K, now));
        level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8f, 0.8f);
        if (Steelworks.handOff(level, worldPosition.relative(travel, 2), pipes, travel)) return;
        ItemStack rest = inv.output(pipes);
        if (!rest.isEmpty()) Ironworks.spill(level, worldPosition.above(2), rest);
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.RUNNING)) return;
        MachineFx fx = fx();
        if (fx.chance(2)) fx.sparks(fx.at(8, 10, 8), 2);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return pierceable(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (inv.giveOutputs(player)) return InteractionResult.SUCCESS;
        int[] sz = SIZES[size];
        player.displayClientMessage(SteelworksText.msg("piercing", sz[0], sz[1], SteelworksText.pipeKind(KINDS[kind]), lastPipes), true);
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return SteelworksPanels.PIERCING;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[SteelworksPanels.SHAFT_RPM10] = (int) Math.round(Math.abs(rpm) * 10);
        out[SteelworksPanels.P_OD_MM] = SIZES[size][0];
        out[SteelworksPanels.P_WALL_MM] = SIZES[size][1];
        out[SteelworksPanels.P_KIND] = KINDS[kind].ordinal();
        out[SteelworksPanels.P_PIPES] = lastPipes;
        out[SteelworksPanels.P_PROGRESS_PERMIL] = billet.isEmpty() ? 0 : (int) Math.round(1000 * (1 - leftS / PIERCE_S));
        out[SteelworksPanels.P_BILLET_C] = billet.isEmpty() ? 0 : (int) Math.round(Steelworks.tempK(billet, level.getGameTime()) - 273.15);
        out[SteelworksPanels.FLAGS] = billet.isEmpty() ? 0 : 1 << SteelworksPanels.F_RUNNING;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == SteelworksPanels.BTN_SIZE) size = (size + 1) % SIZES.length;
        else if (id == SteelworksPanels.BTN_KIND) kind = (kind + 1) % KINDS.length;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inv", inv.save(registries));
        tag.putInt("size", size);
        tag.putInt("kind", kind);
        if (!billet.isEmpty()) tag.put("billet", billet.save(registries));
        tag.putInt("travel", travel.get3DDataValue());
        tag.putDouble("left_s", leftS);
        tag.putInt("last_pipes", lastPipes);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inv")) inv.load(registries, tag.getCompound("inv"));
        size = Math.floorMod(tag.getInt("size"), SIZES.length);
        kind = Math.floorMod(tag.getInt("kind"), KINDS.length);
        billet = tag.contains("billet") ? ItemStack.parseOptional(registries, tag.getCompound("billet")) : ItemStack.EMPTY;
        travel = Direction.from3DDataValue(tag.getInt("travel"));
        leftS = tag.getDouble("left_s");
        lastPipes = tag.getInt("last_pipes");
    }
}
