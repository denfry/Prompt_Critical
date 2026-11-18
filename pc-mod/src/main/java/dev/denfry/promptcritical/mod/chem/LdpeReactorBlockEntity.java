package dev.denfry.promptcritical.mod.chem;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.consequence.BlastScaling;
import dev.denfry.promptcritical.sim.consequence.DamageBudgetConfig;
import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.sim.consequence.DamageRequest;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.petrochem.LdpeReactor;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Установка ПЭВД ({@link LdpeReactor}) 3×1×1: гиперкомпрессор на валу Create (вал слева) и трубчатый реактор. Этилен —
 * трубой к любой грани (1 мБ = 1 нм³, 1,25 кг); гранулы полиэтилена — мешками по {@value #BAG_KG} кг в выходной склад,
 * его забирают воронкой снизу. Мощность гиперкомпрессора — {@link LdpeReactor#COMPRESSION_J_KG} на кг циркулирующего
 * этилена; если вал её не даёт, циркуляция снижается. Разложение этилена при перегреве — взрыв через бюджет
 * разрушений мира (как взрыв метана в шахте); установку чинят Shift + ПКМ по ней.
 */
public final class LdpeReactorBlockEntity extends KineticMachineBlockEntity<ShaftInput> implements PanelHost {
    public static final double NOMINAL_RPM = 64, RATED_W = 400e3, KG_PER_NM3 = 28.054 / 22.414, BAG_KG = 25;
    /** Циркуляция этилена в реакторе, кг/с [BALANCE: опытная установка]. */
    public static final double CIRCULATION_KG_S = 0.4;
    public static final int MB = 16_000;

    private final ShaftInput shaft = new ShaftInput();
    private final FeedTank ethylene = new FeedTank(PcFluids.ETHYLENE.source.get(), MB);
    /** Склад мешков: аппарат кладёт сам, воронка только забирает. */
    private final ItemStackHandler out = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler outView = new dev.denfry.promptcritical.mod.item.ExtractOnlyItemHandler(out);
    private final LdpeReactor reactor = new LdpeReactor();
    private long tick;
    private double loadW, polymerKg;
    private static long requestSeq;

    public LdpeReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ChemBlockEntities.LDPE_REACTOR.get(), pos, state);
    }

    public LdpeReactor reactor() {
        return reactor;
    }

    public FeedTank ethylene() {
        return ethylene;
    }

    public ItemStackHandler output() {
        return out;
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
        double dt = 0.05 * binding.world().clock().kSlow();
        // циркуляция — сколько тянет вал; подпитка этиленом — сколько превратится
        double circ = Math.min(CIRCULATION_KG_S, availableW() / LdpeReactor.COMPRESSION_J_KG);
        double conv = reactor.running() ? LdpeReactor.peakK(reactor.oxygenPpm(), reactor.pressureMpa()) : 0;
        double makeupKg = ethylene.amount() * KG_PER_NM3;
        if (makeupKg <= 0) circ = 0;
        double energy = reactor.step(dt, circ);
        if (energy > 0) {
            decompose((ServerLevel) level, binding, energy);
            return;
        }
        double polymer = Math.min(reactor.polymerKgS() * dt, makeupKg);
        ethylene.take(polymer / KG_PER_NM3);
        polymerKg += polymer;
        loadW = reactor.compressorW();
        while (polymerKg >= BAG_KG) {
            ItemStack bag = new ItemStack(PcItems.POLYETHYLENE.get());
            ItemStack left = bag;
            for (int i = 0; i < out.getSlots() && !left.isEmpty(); i++) left = out.insertItem(i, left, false);
            if (!left.isEmpty()) break; // склад полон — гранулы копятся в бункере
            polymerKg -= BAG_KG;
        }
        if (level.getGameTime() % 10 == 0) setStateFlag(PcProperties.RUNNING, reactor.polymerKgS() > 0 && conv > 0);
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    /** Разложение этилена: мембраны вскрыты, выброс горячего газа вспыхивает у установки. */
    private void decompose(ServerLevel sl, SimLevelBinding b, double energyJ) {
        GridPos o = new GridPos(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        b.damage().submit(new DamageRequest(requestSeq++, simId(), null, DamageKind.BLAST, o, energyJ, 4, 2));
        DamageBudgetConfig dc = b.damage().config();
        double power = Math.max(1, Math.min(4, BlastScaling.power(energyJ, dc.tntReferenceKg(), dc.maxExplosionPower())));
        sl.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5, (float) power,
                true, Level.ExplosionInteraction.NONE);
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 64 * 64))
            p.displayClientMessage(ChemText.msg("ldpe.decomposed").withStyle(ChatFormatting.RED), false);
        loadW = 0;
        setChanged();
    }

    // --- руки ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return outView;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return ethylene;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Shift + ПКМ: пуск или останов; после разложения — ремонт. */
    @Override
    public InteractionResult use(Player player) {
        if (reactor.decomposed()) {
            reactor.repair();
            player.displayClientMessage(ChemText.msg("ldpe.repaired"), true);
        } else {
            reactor.setRunning(!reactor.running());
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6f, 0.7f);
            player.displayClientMessage(ChemText.msg(reactor.running() ? "ldpe.start" : "ldpe.stop"), true);
        }
        setChanged();
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return ChemPanels.LDPE;
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
        v[ChemPanels.L_PRESSURE] = (int) Math.round(reactor.pressureMpa());
        v[ChemPanels.L_PEAK_C] = reactor.peakK() > 0 ? (int) Math.round(reactor.peakK() - 273.15) : 0;
        v[ChemPanels.L_O2_PPM] = (int) Math.round(reactor.oxygenPpm());
        v[ChemPanels.L_CONV_PERMIL] = (int) Math.round(reactor.conversion() * 1000);
        v[ChemPanels.L_DENSITY] = (int) Math.round(reactor.density() * 1000);
        v[ChemPanels.L_KGH] = (int) Math.round(reactor.polymerKgS() * 3600);
        v[ChemPanels.L_SHAFT_KW] = (int) Math.round(loadW / 1e3);
        v[ChemPanels.L_LIMIT_C] = (int) Math.round(LdpeReactor.decompositionK(reactor.pressureMpa()) - 273.15);
        v[ChemPanels.L_TANK] = (int) Math.round(ethylene.fill() * 1000);
        v[ChemPanels.L_TOTAL_KG] = (int) Math.round(reactor.totalPolymerKg());
        v[ChemPanels.L_FLAGS] = (reactor.running() ? 1 << ChemPanels.LF_RUN : 0) | (reactor.decomposed() ? 1 << ChemPanels.LF_DECOMPOSED : 0)
                | (reactor.peakK() > LdpeReactor.decompositionK(reactor.pressureMpa()) - 15 ? 1 << ChemPanels.LF_HOT : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ChemPanels.LB_RUN -> use(player);
            case ChemPanels.LB_O2_DOWN -> reactor.setOxygenPpm(reactor.oxygenPpm() - 5);
            case ChemPanels.LB_O2_UP -> reactor.setOxygenPpm(reactor.oxygenPpm() + 5);
            case ChemPanels.LB_P_DOWN -> reactor.setPressureMpa(reactor.pressureMpa() - 10);
            case ChemPanels.LB_P_UP -> reactor.setPressureMpa(reactor.pressureMpa() + 10);
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
        ethylene.save(tag, "ethylene");
        tag.put("out", out.serializeNBT(registries));
        MutableStateTree tree = new MutableStateTree();
        reactor.save(new TreeStateWriter(tree));
        tag.put("reactor", dev.denfry.promptcritical.mod.binding.NbtStateTrees.toNbt(tree));
        tag.putDouble("polymer", polymerKg);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        ethylene.load(tag, "ethylene");
        if (tag.contains("out")) out.deserializeNBT(registries, tag.getCompound("out"));
        if (tag.contains("reactor"))
            reactor.load(new TreeStateReader(dev.denfry.promptcritical.mod.binding.NbtStateTrees.fromNbt(tag.getCompound("reactor"))));
        polymerKg = tag.getDouble("polymer");
    }
}
