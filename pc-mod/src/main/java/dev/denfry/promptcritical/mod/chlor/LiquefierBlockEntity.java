package dev.denfry.promptcritical.mod.chlor;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.environment.ToxicReleaseService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.chlor.ChlorineLiquefier;
import dev.denfry.promptcritical.sim.consequence.ToxicGas;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Отделение осушки и сжижения хлора ({@link ChlorineLiquefier}) 1×2×1: сушильные башни с серной кислотой, компрессор
 * и конденсатор. Газообразный хлор и электролитическая щёлочь для санитарной колонны — трубами к боковым граням;
 * жидкий хлор — снизу (1 мБ = 1 л). Абгаз поглощается щёлочью (2 NaOH + Cl₂ → NaOCl + NaCl + H₂O); нет щёлочи —
 * абгаз уходит в атмосферу облаком. Жидкому хлору некуда уходить — предохранительный клапан сбрасывает его наружу.
 * Водород в хлоре берётся с ближайшей серии ванн в {@value #CELLS_REACH} блоках.
 */
public final class LiquefierBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    /** Компрессор хлора и холодильная машина, Вт (розетка или силовой щит рядом; при недостатке установка работает медленнее). */
    public static final double POWER_W = 250e3;
    public static final int GAS_MB = 4_000, LIQUOR_MB = 8_000, LIQUID_MB = 16_000, CELLS_REACH = 16;
    /** Производительность по хлору, кг/с [BALANCE]. */
    public static final double CAPACITY_KG_S = 0.4;
    /** Щёлочь на кг хлора абгаза: 2·40/71 кг NaOH. */
    static final double NAOH_PER_CL2 = 2 * 0.039997 / 0.070906;

    private final FeedTank chlorine = new FeedTank(PcFluids.CHLORINE.source.get(), GAS_MB);
    private final FeedTank naoh = new FeedTank(PcFluids.SODIUM_HYDROXIDE_SOLUTION.source.get(), LIQUOR_MB);
    private final IFluidHandler inlet = new MultiInlet(chlorine, naoh);
    private final ProductTank liquid = new ProductTank(PcFluids.LIQUID_CHLORINE.source.get(), LIQUID_MB);
    private final ChlorineLiquefier unit = new ChlorineLiquefier();
    private double ventKgS, h2;

    public LiquefierBlockEntity(BlockPos pos, BlockState state) {
        super(ChlorBlockEntities.LIQUEFIER.get(), pos, state);
    }

    public ChlorineLiquefier unit() {
        return unit;
    }

    public FeedTank chlorine() {
        return chlorine;
    }

    public FeedTank naoh() {
        return naoh;
    }

    public ProductTank liquid() {
        return liquid;
    }

    public double ventKgS() {
        return ventKgS;
    }

    /** Работающий ожижитель (арт art/blockbench/chlor_alkali/chlorine_liquefier.author.js): с заиндевевшего конденсатора и сборника стекает холодный туман. */
    private void clientFx() {
        MachineFx fx = MachineFx.of(level, worldPosition, getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (fx == null || !getBlockState().getValue(PcProperties.RUNNING)) return;
        if (fx.chance(4)) fx.puff(fx.at(2.4 + fx.random().nextDouble() * 11.2, 17, 8), new Vec3(0, -0.03, 0), 1);
        if (fx.chance(2)) fx.puff(fx.at(8, 1, 8), new Vec3(0, -0.03, 0), 1);
    }

    @Override
    public void tickDevice() {
        if (level != null && level.isClientSide) {
            clientFx();
            return;
        }
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        double dt = 0.05 * b.world().clock().kSlow();
        if (level.getGameTime() % 40 == 0) {
            CellRoomBlockEntity cells = CellRoomBlockEntity.near(level, worldPosition, CELLS_REACH);
            h2 = cells != null && cells.cells().cl2KgS() > 0 ? cells.cells().h2InCl2() : ChlorineLiquefier.H2_NORMAL;
        }
        double power = dev.denfry.promptcritical.mod.grid.MachinePower.supply(s, worldPosition, unit.on() && !unit.tripped() ? POWER_W : 0);
        double inKg = unit.on() && !unit.tripped() ? Math.min(CAPACITY_KG_S * dt * power, chlorine.amount() * ChlorUnits.CL2_KG_NM3) : 0;
        unit.step(inKg / dt, h2);
        chlorine.take(inKg / ChlorUnits.CL2_KG_NM3);
        // жидкость в хранилище; не влезло — сброс с предохранительного клапана
        double liqL = unit.liquidKgS() * dt / ChlorUnits.LIQUID_CL2_KG_L;
        double stored = Math.min(liqL, liquid.room());
        liquid.add(stored);
        double vent = (liqL - stored) * ChlorUnits.LIQUID_CL2_KG_L;
        // абгаз — в щелочную колонну
        double tail = unit.tailCl2KgS() * dt;
        double naohL = ChlorUnits.liquorLPerNaohKg(tail * NAOH_PER_CL2);
        double absorbed = naohL > 0 ? Math.min(1, naoh.amount() / naohL) : 1;
        naoh.take(naohL * absorbed);
        vent += tail * (1 - absorbed);
        ventKgS = vent / dt;
        if (ventKgS > 1e-6) ToxicReleaseService.vent(s, worldPosition.above(2), ToxicGas.CHLORINE, ventKgS);
        if (level.getGameTime() % 10 == 0) {
            BlockState st = getBlockState();
            boolean run = unit.liquidKgS() > 0;
            if (st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING) != run)
                level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, run), Block.UPDATE_CLIENTS);
        }
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return side == Direction.DOWN ? liquid : inlet;
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
    public InteractionResult use(Player player) {
        unit.setOn(!unit.on());
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        player.displayClientMessage(ChlorText.msg(unit.on() ? "liquefier.on" : "liquefier.off"), true);
        setChanged();
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return ChlorPanels.LIQUEFIER;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[ChlorPanels.L_COND_C] = (int) Math.round(unit.condK() - 273.15);
        out[ChlorPanels.L_P_X100] = (int) Math.round(unit.pressureMpa() * 100);
        out[ChlorPanels.L_DEGREE_PERMIL] = (int) Math.round(unit.degree() * 1000);
        out[ChlorPanels.L_TAIL_H2_PERMIL] = (int) Math.round(unit.tailH2Fraction() * 1000);
        out[ChlorPanels.L_KGH] = (int) Math.round(unit.liquidKgS() * 3600);
        out[ChlorPanels.L_TANK] = (int) Math.round(liquid.fill() * 1000);
        out[ChlorPanels.L_VENT_GH] = (int) Math.round(ventKgS * 3.6e6);
        out[ChlorPanels.L_FLAGS] = (unit.liquidKgS() > 0 ? 1 << ChlorPanels.LF_RUN : 0) | (unit.tripped() ? 1 << ChlorPanels.LF_TRIP : 0)
                | (ventKgS > 0 ? 1 << ChlorPanels.LF_VENT : 0)
                | (unit.tailH2Fraction() > ChlorineLiquefier.H2_LIMIT * 0.8 ? 1 << ChlorPanels.LF_H2 : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ChlorPanels.LB_RUN -> use(player);
            case ChlorPanels.LB_T_DOWN -> unit.setCondK(unit.condK() - 5);
            case ChlorPanels.LB_T_UP -> unit.setCondK(unit.condK() + 5);
            case ChlorPanels.LB_P_DOWN -> unit.setPressureMpa(unit.pressureMpa() - 0.1);
            case ChlorPanels.LB_P_UP -> unit.setPressureMpa(unit.pressureMpa() + 0.1);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        chlorine.save(tag, "cl2");
        naoh.save(tag, "naoh");
        liquid.save(tag, "liquid");
        MutableStateTree tree = new MutableStateTree();
        unit.save(new TreeStateWriter(tree));
        tag.put("unit", NbtStateTrees.toNbt(tree));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        chlorine.load(tag, "cl2");
        naoh.load(tag, "naoh");
        liquid.load(tag, "liquid");
        if (tag.contains("unit")) unit.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("unit"))));
    }
}
