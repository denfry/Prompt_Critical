package dev.denfry.promptcritical.mod.nitrogen;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.gas.PortHandler;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.nitrogen.AmmoniaSynthesis;
import dev.denfry.promptcritical.sim.nitrogen.Species;
import dev.denfry.promptcritical.sim.nitrogen.SynthesisLoop;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Колонна синтеза аммиака с циклом ({@link SynthesisLoop}) 1×3×1: компрессор свежего газа, циркуляционное колесо и
 * аммиачный холодильник на валу Create (вал сзади). Азотоводородная смесь — трубой к любой грани (1 мБ = 1 нм³);
 * жидкий аммиак — из той же грани (1 мБ = 1 л, 0,682 кг). Продувочный газ уходит топливом в ближайшую печь
 * риформинга в 24 блоках, без неё — на факел. Цикл считается раз в {@value #STEP_TICKS} тиков: колонна тяжела, а
 * запас газа в цикле велик, и шаг в 18 игровых секунд он не замечает.
 */
public final class AmmoniaConverterBlockEntity extends KineticMachineBlockEntity<ShaftInput> implements PanelHost {
    public static final double NOMINAL_RPM = 64, RATED_W = 700e3, AMMONIA_KG_L = 0.682;
    public static final int MB = 16_000, STEP_TICKS = 5, PURGE_RADIUS = 24;

    private final ShaftInput shaft = new ShaftInput();
    private final FeedTank syngas = new FeedTank(PcFluids.SYNGAS.source.get(), MB);
    private final ProductTank ammonia = new ProductTank(PcFluids.AMMONIA.source.get(), MB);
    private final IFluidHandler port = new PortHandler(ammonia, syngas);
    private final SynthesisLoop loop = new SynthesisLoop();
    private long tick;
    private double loadW, totalKg;

    public AmmoniaConverterBlockEntity(BlockPos pos, BlockState state) {
        super(NitrogenBlockEntities.AMMONIA_CONVERTER.get(), pos, state);
    }

    public SynthesisLoop loop() {
        return loop;
    }

    public FeedTank syngas() {
        return syngas;
    }

    public ProductTank ammonia() {
        return ammonia;
    }

    public double totalKg() {
        return totalKg;
    }

    private double availableW() {
        return RATED_W * Math.min(1, shaft.rpm(tick) / NOMINAL_RPM);
    }

    @Override
    protected ShaftInput unit(SimLevelBinding binding) {
        return shaft;
    }

    @Override
    protected ShaftInput shaft(ShaftInput u) {
        return u;
    }

    @Override
    protected double powerW(ShaftInput u) {
        return u.rpm(tick) > 0 ? loadW : 0;
    }

    @Override
    protected double ratedW() {
        return RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return NOMINAL_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding binding, ShaftInput u) {
        tick = binding.world().clock().tick();
        if (level.getGameTime() % STEP_TICKS != 0) return;
        double dt = 0.05 * binding.world().clock().kSlow() * STEP_TICKS;
        // продукт не влезает — сепаратор не сливают, цикл стоит
        double room = ammonia.room() * AMMONIA_KG_L / Species.NH3.molarMass();
        double shaftW = room > 0 ? availableW() : 0;
        SynthesisLoop.Result r = loop.step(dt, syngas.amount() * Species.MOL_PER_NM3, shaftW);
        syngas.take(r.makeupMolS() * dt / Species.MOL_PER_NM3);
        double kg = r.ammoniaMolS() * dt * Species.NH3.molarMass();
        ammonia.add(kg / AMMONIA_KG_L);
        totalKg += kg;
        loadW = r.shaftW();
        if (r.purgeFuelW() > 0) {
            SteamReformerBlockEntity f = SteamReformerBlockEntity.near(level, worldPosition, PURGE_RADIUS);
            if (f != null) f.acceptPurgeFuel(r.purgeFuelW() * dt);
        }
        setStateFlag(PcProperties.RUNNING, r.ammoniaMolS() > 0);
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    // --- руки ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return port;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Shift + ПКМ: пуск или останов цикла. */
    @Override
    public InteractionResult use(Player player) {
        toggle(player);
        return InteractionResult.SUCCESS;
    }

    private void toggle(Player player) {
        loop.setRunning(!loop.running());
        level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6f, 0.6f);
        player.displayClientMessage(NitrogenText.msg(loop.running() ? "converter.start" : "converter.stop"), true);
        setChanged();
    }

    // --- щит ---

    /**
     * Работает (арт — art/blockbench/petrochem/process_units.author.js): горячий газ после колонны синтеза уходит в
     * котёл-утилизатор; на стыке выходного штуцера с котлом тянет паром из дренажа. Чем быстрее циркуляционный
     * компрессор, тем гуще.
     */
    @Override
    protected void clientTick() {
        var fx = fx();
        if (fx == null || !runningState()) return;
        double n = Math.min(1.5, Math.abs(getSpeed()) / 64.0);
        if (fx.chance(1 + 2 * n)) fx.wisp(fx.at(15.6, -4.2, 7), 0.1);
    }

    @Override
    public PanelLayout panelLayout() {
        return NitrogenPanels.LOOP;
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
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        SynthesisLoop.Result r = loop.last();
        double toNm3h = 3600 / Species.MOL_PER_NM3;
        v[NitrogenPanels.S_P_BAR] = (int) Math.round(loop.pressureBar());
        v[NitrogenPanels.S_BED_C] = r.maxBedK() > 0 ? (int) Math.round(r.maxBedK() - 273.15) : 0;
        v[NitrogenPanels.S_IN_NH3] = (int) Math.round(r.inletNh3() * 1e4);
        v[NitrogenPanels.S_OUT_NH3] = (int) Math.round(r.outletNh3() * 1e4);
        v[NitrogenPanels.S_INERTS] = (int) Math.round(loop.fraction(Species.CH4) * 1e4 + loop.fraction(Species.AR) * 1e4);
        v[NitrogenPanels.S_NH3_KGH] = (int) Math.round(r.ammoniaMolS() * Species.NH3.molarMass() * 3600);
        v[NitrogenPanels.S_MAKEUP] = (int) Math.round(r.makeupMolS() * toNm3h);
        v[NitrogenPanels.S_PURGE] = (int) Math.round(loop.purgeMolS() * toNm3h);
        v[NitrogenPanels.S_CIRC] = (int) Math.round(loop.circulation() * 100);
        v[NitrogenPanels.S_SEP_C] = (int) Math.round(loop.separatorK() - 273.15);
        v[NitrogenPanels.S_SHAFT_KW] = (int) Math.round(loadW / 1e3);
        v[NitrogenPanels.S_CHILL_KW] = (int) Math.round(r.chillerW() / 1e3);
        v[NitrogenPanels.S_ACT] = (int) Math.round(loop.activity() * 1000);
        v[NitrogenPanels.S_SYN_TANK] = (int) Math.round(syngas.fill() * 1000);
        v[NitrogenPanels.S_NH3_TANK] = (int) Math.round(ammonia.fill() * 1000);
        v[NitrogenPanels.S_FLAGS] = (r.ammoniaMolS() > 0 ? 1 << NitrogenPanels.SF_RUN : 0)
                | (r.maxBedK() > AmmoniaSynthesis.CATALYST_LIMIT_K ? 1 << NitrogenPanels.SF_HOT : 0)
                | (loop.pressureBar() >= 0.98 * SynthesisLoop.MAX_BAR ? 1 << NitrogenPanels.SF_PMAX : 0)
                | (loop.running() && syngas.amount() < 1 ? 1 << NitrogenPanels.SF_NO_GAS : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        double purgeStep = 40 * Species.MOL_PER_NM3 / 3600;
        switch (id) {
            case NitrogenPanels.SB_RUN -> toggle(player);
            case NitrogenPanels.SB_PURGE_DOWN -> loop.setPurgeMolS(loop.purgeMolS() - purgeStep);
            case NitrogenPanels.SB_PURGE_UP -> loop.setPurgeMolS(loop.purgeMolS() + purgeStep);
            case NitrogenPanels.SB_SEP_DOWN -> loop.setSeparatorK(loop.separatorK() - 5);
            case NitrogenPanels.SB_SEP_UP -> loop.setSeparatorK(loop.separatorK() + 5);
            case NitrogenPanels.SB_BED_DOWN -> loop.setBedInK(loop.bedInK() - 5);
            case NitrogenPanels.SB_BED_UP -> loop.setBedInK(loop.bedInK() + 5);
            case NitrogenPanels.SB_CIRC_DOWN -> loop.setCirculation(loop.circulation() - 0.1);
            case NitrogenPanels.SB_CIRC_UP -> loop.setCirculation(loop.circulation() + 0.1);
            default -> {
            }
        }
        setChanged();
    }

    // --- сохранение ---

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) return;
        syngas.save(tag, "syngas");
        ammonia.save(tag, "ammonia");
        MutableStateTree tree = new MutableStateTree();
        loop.save(new TreeStateWriter(tree));
        tag.put("loop", NbtStateTrees.toNbt(tree));
        tag.putDouble("total", totalKg);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        syngas.load(tag, "syngas");
        ammonia.load(tag, "ammonia");
        if (tag.contains("loop")) loop.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("loop"))));
        totalKg = tag.getDouble("total");
    }
}
