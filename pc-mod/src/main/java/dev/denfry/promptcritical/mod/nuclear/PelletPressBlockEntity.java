package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.reprocessing.PuLot;
import dev.denfry.promptcritical.sim.fuel.MaterialBalance;
import dev.denfry.promptcritical.sim.fuel.MoxBlend;
import dev.denfry.promptcritical.sim.fuel.PelletProcess;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Роторный пресс таблеток на валу Create (вал сзади): порошок UO₂ или возвратный шлам — в бункер (слот или воронка),
 * сырые таблетки — сразу в печь спекания, которая стоит вплотную. Давление прессования задаёт сырую плотность
 * (Хеккель) и брак прессования; бракованные сырые таблетки дробят и возвращают в бункер — уран не теряется. Партии
 * не смешиваются: мешок с другим обогащением ждёт, пока бункер опустеет.
 *
 * <p>МОКС: PuO₂ из PUREX (партия {@code pu_lot}) — во второй бункер (воронкой или рукой); пресс забирает его в
 * смеситель не больше безопасной массы плутония и подмешивает к каждому мешку урана до {@value #MOX_PU_FRACTION} Pu/ТМ
 * ({@link MoxBlend}). Плутония на мешок не хватает — мешок ждёт; бункер PuO₂ пуст — пресс идёт на урановом топливе.
 * Мешок, уже несущий плутоний (шлам МОКС), идёт со своей долей.
 */
public final class PelletPressBlockEntity extends dev.denfry.promptcritical.mod.foundry.PortKineticMachineBlockEntity<ShaftInput>
        implements PanelHost {
    public static final double NOMINAL_RPM = 64, RATED_W = 60e3, RATED_KG_U_S = 0.01;
    /** Доля Pu в ТМ теплового МОКС, середина 5–8 % ({@link MoxBlend}). */
    public static final double MOX_PU_FRACTION = 0.07;

    private final ShaftInput shaft = new ShaftInput();
    private final ItemStackHandler hopper = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return FuelPanels.powder(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ItemStackHandler puHopper = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(PcItems.PUO2_POWDER.get()) && stack.has(PuLot.TYPE.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler feed = new CombinedInvWrapper(hopper, puHopper);
    /** Плутоний в смесителе: кг и изотопия; доля Pu в буфере пресса. */
    private double puStockKg;
    private MoxShare puStock = MoxShare.NONE, share = MoxShare.NONE;
    private final MaterialBalance balance = new MaterialBalance();
    private double pressureMpa = 300, bufferKg, x, x234, loadW, lastKgS;
    private FuelDesign design = FuelDesign.VVER_1000;
    private boolean running, noFurnace, limited;
    private long tick;

    public PelletPressBlockEntity(BlockPos pos, BlockState state) {
        super(NuclearBlockEntities.PELLET_PRESS.get(), pos, state);
    }

    public ItemStackHandler hopper() {
        return hopper;
    }

    public MaterialBalance balance() {
        return balance;
    }

    public boolean running() {
        return running;
    }

    public void setRunning(boolean r) {
        running = r;
        setChanged();
    }

    public FuelDesign design() {
        return design;
    }

    public void setDesign(FuelDesign d) {
        if (bufferKg <= 1e-9) design = d;
    }

    public double pressureMpa() {
        return pressureMpa;
    }

    public void setPressureMpa(double p) {
        pressureMpa = Math.max(100, Math.min(600, p));
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

    @Nullable
    private SinteringFurnaceBlockEntity furnace() {
        for (Direction d : Direction.values()) {
            SinteringFurnaceBlockEntity f = SinteringFurnaceBlockEntity.at(level, worldPosition.relative(d));
            if (f != null) return f;
        }
        return null;
    }

    /** Роторный пресс реального масштаба: ритмичный стук пуансонов и тёмная пыль у башмака питателя (fuelcycle §3a). */
    @Override
    protected void clientTick() {
        BlockState st = getBlockState();
        if (!real() || !st.hasProperty(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING)
                || !st.getValue(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING)) return;
        dev.denfry.promptcritical.mod.fx.MachineFx fx = fx();
        if (fx == null) return;
        if (fx.chance(3)) fx.sound(fx.at(8, 26, 8), net.minecraft.sounds.SoundEvents.STONE_HIT, 0.35f, 1.5f);
        if (fx.chance(1)) fx.dust(fx.at(8, 26, -2), 0.1, 2);
    }

    @Override
    protected void serverTick(SimLevelBinding binding, ShaftInput u) {
        tick = binding.world().clock().tick();
        double dt = 0.05 * binding.world().clock().kSlow();
        // бункер: следующий мешок той же партии
        fillPuStock();
        ItemStack s = hopper.getStackInSlot(0);
        if (!s.isEmpty() && bufferKg < 2) {
            FuelLot l = FuelLot.of(s);
            MoxShare m = MoxShare.of(l);
            double puUsed = 0;
            if (!m.isMox() && puStockKg > 0) {
                MoxBlend.Mix mix = MoxBlend.blend(puStockKg, puStock.pu239(), puStock.pu240(), puStock.pu241(), puStock.pu242(), l.uKg(),
                        l.u235(), MOX_PU_FRACTION);
                // плутония не хватает на весь мешок — ждём следующую партию PuO₂
                m = mix.uKg() >= l.uKg() - 1e-9 ? new MoxShare(mix.puKg() / l.uKg(), puStock.pu239(), puStock.pu240(), puStock.pu241(),
                        puStock.pu242()) : null;
                if (m != null) puUsed = mix.puKg();
            }
            if (m != null && (bufferKg <= 1e-9 || Math.abs(l.u235() - x) < 1e-9 && m.same(share))) {
                puStockKg -= puUsed;
                share = m;
                x = bufferKg <= 1e-9 ? l.u235() : x;
                x234 = bufferKg <= 1e-9 ? l.u234() : (x234 * bufferKg + l.u234() * l.uKg()) / (bufferKg + l.uKg());
                bufferKg += l.uKg();
                balance.in(l.uKg(), l.u235());
                s.shrink(1);
                hopper.setStackInSlot(0, s);
            }
        }
        SinteringFurnaceBlockEntity f = furnace();
        noFurnace = f == null;
        lastKgS = 0;
        loadW = 0;
        if (running && f != null && bufferKg > 0) {
            double kg = Math.min(bufferKg, RATED_KG_U_S * dt * Math.min(1, availableW() / RATED_W));
            // брак прессования дробят и возвращают в бункер: в печь идёт только годное
            double good = kg * (1 - PelletProcess.pressingDefects(pressureMpa));
            double taken = f.acceptGreen(good, x, x234, share, PelletProcess.greenDensity(pressureMpa), design);
            limited = taken < good - 1e-12;
            bufferKg -= taken;
            balance.out(taken, x);
            lastKgS = taken / dt;
            loadW = RATED_W * Math.min(1, lastKgS / RATED_KG_U_S) * pressureMpa / 300;
        }
        if (level.getGameTime() % 10 == 0) setStateFlag(PcProperties.RUNNING, lastKgS > 0);
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    /** Забрать PuO₂ из бункера в смеситель, пока плутоний в нём ниже безопасной массы. */
    private void fillPuStock() {
        ItemStack p = puHopper.getStackInSlot(0);
        PuLot lot = p.get(PuLot.TYPE.get());
        if (lot == null || lot.puKg() <= 0) return;
        if (!MoxBlend.vesselSafe(puStockKg + lot.puKg(), lot.pu240())) return;
        double sum = puStockKg + lot.puKg();
        puStock = MoxShare.mix(puStock, puStockKg, new MoxShare(1, lot.pu239(), lot.pu240(), lot.pu241(), lot.pu242()), lot.puKg());
        puStock = new MoxShare(1, puStock.pu239(), puStock.pu240(), puStock.pu241(), puStock.pu242());
        puStockKg = sum;
        p.shrink(1);
        puHopper.setStackInSlot(0, p);
    }

    // --- руки ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return feed;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return hopper;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return FuelPanels.powder(stack) || puHopper.isItemValid(0, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack left = (FuelPanels.powder(stack) ? hopper : puHopper).insertItem(0, stack.copy(), false);
        int moved = stack.getCount() - left.getCount();
        if (moved == 0) return ItemInteractionResult.FAIL;
        if (!player.isCreative()) stack.shrink(moved);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        toggle(player);
        return InteractionResult.SUCCESS;
    }

    private void toggle(Player player) {
        running = !running;
        level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5f, 1.2f);
        player.displayClientMessage(NuclearText.msg(running ? "press.start" : "press.stop"), true);
        setChanged();
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return FuelPanels.PRESS;
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
        v[FuelPanels.L_IN] = (int) Math.round(balance.u235().inKg() * 1000);
        v[FuelPanels.L_OUT] = (int) Math.round(balance.u235().outKg() * 1000);
        v[FuelPanels.L_LOSS] = (int) Math.round(balance.u235().lossKg() * 1000);
        v[FuelPanels.L_HOLD] = (int) Math.round(balance.holdupU235Kg() * 1000);
        v[FuelPanels.P_MPA] = (int) Math.round(pressureMpa);
        v[FuelPanels.P_GREEN] = (int) Math.round(PelletProcess.greenDensity(pressureMpa) * 1000);
        v[FuelPanels.P_DEFECT] = (int) Math.round(PelletProcess.pressingDefects(pressureMpa) * 1000);
        v[FuelPanels.P_DESIGN] = design.ordinal();
        v[FuelPanels.P_BUFFER] = (int) Math.round(bufferKg * 1000);
        v[FuelPanels.FLAGS] = (lastKgS > 0 ? 1 << FuelPanels.F_RUN : 0) | (limited ? 1 << FuelPanels.F_LIMIT : 0)
                | (noFurnace ? 1 << FuelPanels.PF_NO_FURNACE : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case FuelPanels.B_RUN -> toggle(player);
            case FuelPanels.PB_P_DOWN -> setPressureMpa(pressureMpa - 25);
            case FuelPanels.PB_P_UP -> setPressureMpa(pressureMpa + 25);
            case FuelPanels.PB_DESIGN -> setDesign(FuelDesign.values()[(design.ordinal() + 1) % FuelDesign.values().length]);
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
        tag.put("hopper", hopper.serializeNBT(registries));
        tag.put("pu_hopper", puHopper.serializeNBT(registries));
        MutableStateTree tree = new MutableStateTree();
        TreeStateWriter w = new TreeStateWriter(tree);
        balance.save(w);
        w.putDouble("p", pressureMpa);
        w.putDouble("buffer", bufferKg);
        w.putDouble("x", x);
        w.putDouble("x234", x234);
        w.putString("design", design.name());
        w.putBoolean("run", running);
        w.putDouble("pu_stock", puStockKg);
        puStock.save(w, "stock_");
        share.save(w, "mox_");
        tag.put("state", NbtStateTrees.toNbt(tree));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        if (tag.contains("hopper")) hopper.deserializeNBT(registries, tag.getCompound("hopper"));
        if (tag.contains("pu_hopper")) puHopper.deserializeNBT(registries, tag.getCompound("pu_hopper"));
        if (tag.contains("state")) {
            TreeStateReader r = new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("state")));
            balance.load(r);
            setPressureMpa(r.optDouble("p", 300));
            bufferKg = r.optDouble("buffer", 0);
            x = r.optDouble("x", 0);
            x234 = r.optDouble("x234", 0);
            String d = r.optString("design", FuelDesign.VVER_1000.name());
            for (FuelDesign fd : FuelDesign.values()) if (fd.name().equals(d)) design = fd;
            running = r.has("run") && r.getBoolean("run");
            puStockKg = r.optDouble("pu_stock", 0);
            puStock = MoxShare.load(r, "stock_");
            share = MoxShare.load(r, "mox_");
        }
    }
}
