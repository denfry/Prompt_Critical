package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.enrichment.Centrifuge;
import dev.denfry.promptcritical.sim.enrichment.CentrifugeHall;
import dev.denfry.promptcritical.sim.enrichment.Criticality;
import dev.denfry.promptcritical.sim.enrichment.IdealCascade;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Пульт каскада газовых центрифуг ({@link CentrifugeHall}): считает секции {@code gas_centrifuge} (по
 * {@value #MACHINES_PER_SECTION} машин) в радиусе {@value #RADIUS} блоков, собирает из них идеальный каскад под
 * заданные отбор и отвал и питает их от вала Create (сзади) через преобразователь частоты: 50 кВт·ч на ЕРР. Баллоны —
 * в слоты щита или воронкой: питание (любой баллон с UF₆), отбор (30B до 5 % или 5A), отвал (48Y). Отбор копится в
 * десублиматоре, пока его некуда слить. Ротор, разорвавшийся от перегрузки или усталости, разбивает свою секцию и
 * с вероятностью 30 % соседнюю. Вспышка СЦР в десублиматоре облучает всех вокруг: источник —
 * {@link dev.denfry.promptcritical.sim.radiation.FissionSource#solutionBurst}, доза с ослаблением стенами и лучевая
 * болезнь — {@link dev.denfry.promptcritical.mod.environment.RadiationService}.
 */
public final class CascadeBlockEntity extends KineticMachineBlockEntity<ShaftInput> implements PanelHost {
    public static final int RADIUS = 8, MACHINES_PER_SECTION = 20, SCAN_TICKS = 20;
    public static final double NOMINAL_RPM = 64, RATED_W = 400e3, J_PER_SWU = 50 * 3.6e6, DOMINO = 0.3;

    private final ShaftInput shaft = new ShaftInput();
    private final ItemStackHandler cylinders = new ItemStackHandler(3) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case EnrichmentPanels.SLOT_FEED -> stack.getItem() instanceof Uf6CylinderItem;
                case EnrichmentPanels.SLOT_PRODUCT -> EnrichmentPanels.productCylinder(stack);
                default -> stack.is(PcItems.UF6_CYLINDER_48Y.get());
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final CentrifugeHall hall = new CentrifugeHall();
    private final List<BlockPos> sections = new ArrayList<>();
    private int machineCount;
    /** Ближайший автоклав с баллоном: питание каскада, когда слот питания пуст. */
    @Nullable
    private BlockPos autoclave;
    private final Random rng = new Random();
    private long tick;
    private int powered;
    private double loadW;
    private boolean excursion;

    public CascadeBlockEntity(BlockPos pos, BlockState state) {
        super(NuclearBlockEntities.CENTRIFUGE_CASCADE.get(), pos, state);
    }

    public CentrifugeHall hall() {
        return hall;
    }

    public ItemStackHandler cylinders() {
        return cylinders;
    }

    public int machines() {
        return machineCount;
    }

    /**
     * Машин в блоке: секция старой серии — {@value #MACHINES_PER_SECTION}, секция каскада реального масштаба —
     * {@value CentrifugeRealBlockEntity#SECTION_MACHINES}, одиночная центрифуга — 1, если корпус не вскрыт; части
     * многоблочных машин не считаются.
     */
    static int machinesAt(BlockState st) {
        if (st.is(PcBlocks.GAS_CENTRIFUGE.get())) return MACHINES_PER_SECTION;
        if (!(st.getBlock() instanceof dev.denfry.promptcritical.mod.block.PcBlock b) || !b.isController(st)) return 0;
        if (st.is(PcBlocks.CENTRIFUGE_CASCADE_SECTION_REAL.get())) return CentrifugeRealBlockEntity.SECTION_MACHINES;
        if (st.is(PcBlocks.GAS_CENTRIFUGE_REAL.get()))
            return st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)
                    && st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN) ? 0 : 1;
        return 0;
    }

    public boolean excursion() {
        return excursion;
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

    /** Секции центрифуг вокруг пульта. */
    private void scan() {
        sections.clear();
        machineCount = 0;
        autoclave = null;
        boolean spinning = powered > 0;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-RADIUS, -2, -RADIUS), worldPosition.offset(RADIUS, 2, RADIUS))) {
            BlockState st = level.getBlockState(p);
            int n = machinesAt(st);
            if (n > 0) {
                sections.add(p.immutable());
                machineCount += n;
                // реальные машины гудят, пока пульт их питает (преобразователь частоты — за кадром)
                if (st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)
                        && st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) != spinning)
                    level.setBlock(p, st.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, spinning),
                            net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            } else if (autoclave == null && level.getBlockEntity(p) instanceof Uf6AutoclaveBlockEntity a && a.loaded()) autoclave = p.immutable();
        }
    }

    /** Баллон питания: из слота щита, а если там пусто — прогретый баллон в автоклаве рядом. */
    private ItemStack feedCylinder() {
        ItemStack s = cylinders.getStackInSlot(EnrichmentPanels.SLOT_FEED);
        if (!Uf6CylinderItem.charge(s).empty() || autoclave == null) return s;
        return level.getBlockEntity(autoclave) instanceof Uf6AutoclaveBlockEntity a ? a.feed() : s;
    }

    private static double uf6PerU(double u235, double u234) {
        double mu = Uf6Charge.uraniumMolarMass(u235, u234);
        return (mu + 6 * Uf6Charge.M_F) / mu;
    }

    @Override
    protected void serverTick(SimLevelBinding binding, ShaftInput u) {
        tick = binding.world().clock().tick();
        ServerLevel sl = (ServerLevel) level;
        if (level.getGameTime() % SCAN_TICKS == 0) scan();
        double dt = 0.05 * binding.world().clock().kSlow();
        ItemStack feedStack = feedCylinder();
        Uf6Charge feed = Uf6CylinderItem.charge(feedStack);
        // машины без питания стоят: на ЕРР — 50 кВт·ч
        double perMachineW = hall.machineSwuPerS() * J_PER_SWU;
        powered = perMachineW > 0 ? (int) Math.min(machines(), Math.floor(availableW() / perMachineW)) : 0;
        CentrifugeHall.Result r = hall.step(dt, powered, feed.uraniumKg(), feed.u235(), feed.u234(), rng);
        if (r.feedKgS() > 0) Uf6CylinderItem.take(feedStack, r.feedKgS() * dt * uf6PerU(feed.u235(), feed.u234()));
        loadW = r.swuPerS() * J_PER_SWU;
        drainProduct();
        drainTails();
        if (r.crashes() > 0) crash(sl, r.crashes());
        if (r.excursionFissions() > 0) excursion(sl, r.excursionFissions());
        if (level.getGameTime() % 10 == 0) setStateFlag(PcProperties.RUNNING, r.productKgS() > 0);
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    /** Слив отбора из десублиматора в баллон, если смесь в баллоне не выйдет за его предел обогащения. */
    private void drainProduct() {
        ItemStack s = cylinders.getStackInSlot(EnrichmentPanels.SLOT_PRODUCT);
        if (hall.trapKg() <= 0 || s.isEmpty()) return;
        double x = hall.trapX(), x234 = hall.trapX234(), factor = uf6PerU(x, x234);
        double kgU = Math.min(hall.trapKg(), Uf6CylinderItem.roomKg(s) / factor);
        if (kgU <= 0) return;
        Uf6Charge mixed = Uf6CylinderItem.charge(s).plus(new Uf6Charge(kgU * factor, x, x234));
        if (!Uf6CylinderItem.accepts(s, mixed.u235())) return;
        Uf6CylinderItem.fill(s, hall.drainProduct(kgU) * factor, x, x234);
        if (x >= 0.03) dev.denfry.promptcritical.mod.content.SimAchievements.once(this,
                dev.denfry.promptcritical.mod.content.SimAchievements.ENRICHED, "u235_pct", 100 * x);
    }

    private void drainTails() {
        ItemStack s = cylinders.getStackInSlot(EnrichmentPanels.SLOT_TAILS);
        if (hall.tailsKg() <= 0 || s.isEmpty()) return;
        double x = hall.tailsHeldX(), x234 = hall.tailsHeldX234(), factor = uf6PerU(x, x234);
        double uf6 = hall.drainTails(Math.min(hall.tailsKg(), Uf6CylinderItem.roomKg(s) / factor)) * factor;
        if (uf6 > 0) Uf6CylinderItem.fill(s, uf6, x, x234);
    }

    /** Разрыв роторов: секции разбиваются, осколки с вероятностью 30 % добивают соседнюю. */
    private void crash(ServerLevel sl, int count) {
        for (int i = 0; i < count && !sections.isEmpty(); i++) {
            BlockPos p = sections.remove(rng.nextInt(sections.size()));
            machineCount -= machinesAt(sl.getBlockState(p));
            wreck(sl, p);
            if (rng.nextDouble() < DOMINO)
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = p.relative(d);
                    if (sections.remove(n)) {
                        machineCount -= machinesAt(sl.getBlockState(n));
                        wreck(sl, n);
                        break;
                    }
                }
        }
        setChanged();
    }

    private void wreck(ServerLevel sl, BlockPos p) {
        BlockState st = sl.getBlockState(p);
        if (st.is(PcBlocks.GAS_CENTRIFUGE_REAL.get())) {
            // одиночная машина: корпус вскрыло, ротор лежит на виду — её чинят, а не строят заново
            sl.setBlock(p, st.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN, true)
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false), net.minecraft.world.level.block.Block.UPDATE_ALL);
        } else sl.destroyBlock(p, false);
        sl.playSound(null, p, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0f, 0.6f);
        sl.sendParticles(ParticleTypes.CRIT, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.3);
    }

    /** Вспышка СЦР: голубое свечение, доза всем вокруг — общей моделью излучения, со стенами. */
    private void excursion(ServerLevel sl, double fissions) {
        excursion = true;
        sl.sendParticles(ParticleTypes.GLOW, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, 60, 0.6, 0.6, 0.6, 0.1);
        sl.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5f, 1.4f);
        var doses = dev.denfry.promptcritical.mod.environment.RadiationService.burst(sl, worldPosition.getCenter(),
                dev.denfry.promptcritical.sim.radiation.FissionSource.solutionBurst(fissions), false);
        doses.forEach((p, sv) -> p.displayClientMessage(NuclearText.msg("cascade.excursion", NuclearText.num(sv, 2))
                .withStyle(ChatFormatting.RED), false));
        setChanged();
    }

    // --- руки ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return cylinders;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return cylinders;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof Uf6CylinderItem;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Shift + ПКМ: пуск или останов цеха. */
    @Override
    public InteractionResult use(Player player) {
        toggle(player);
        return InteractionResult.SUCCESS;
    }

    private void toggle(Player player) {
        hall.setRunning(!hall.running());
        if (hall.running()) excursion = false;
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4f, 1.6f);
        player.displayClientMessage(NuclearText.msg(hall.running() ? "cascade.start" : "cascade.stop"), true);
        setChanged();
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return EnrichmentPanels.CASCADE;
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
        CentrifugeHall.Result r = hall.last();
        Uf6Charge feed = Uf6CylinderItem.charge(cylinders.getStackInSlot(EnrichmentPanels.SLOT_FEED));
        double xf = feed.empty() ? Uf6Charge.NATURAL_U235 : feed.u235();
        double safe = Criticality.safeU235Kg(hall.productX());
        CentrifugeHall.Design d = hall.design();
        v[EnrichmentPanels.SPEED] = (int) Math.round(hall.speed());
        v[EnrichmentPanels.TRAP_G] = (int) Math.round(hall.trapU235Kg() * 1000);
        v[EnrichmentPanels.SAFE_G] = Double.isInfinite(safe) ? 0 : (int) Math.round(safe * 1000);
        v[EnrichmentPanels.TRAP_PCT] = Double.isInfinite(safe) ? 0 : (int) Math.round(Math.min(1, hall.trapU235Kg() / safe) * 1000);
        v[EnrichmentPanels.XF] = (int) Math.round(xf * 1e5);
        v[EnrichmentPanels.XP] = (int) Math.round(hall.productX() * 1e5);
        v[EnrichmentPanels.XW] = (int) Math.round(hall.tailsX() * 1e5);
        v[EnrichmentPanels.SWU_YEAR] = (int) Math.round(r.swuPerS() * Centrifuge.YEAR_S);
        v[EnrichmentPanels.KG_DAY_X10] = (int) Math.round(r.productKgS() * 86400 * 10);
        v[EnrichmentPanels.MACHINES] = machines();
        v[EnrichmentPanels.POWERED] = powered;
        double alpha = Centrifuge.alpha(hall.machineSwuPerS(), Centrifuge.feedPerS(d.lengthM));
        v[EnrichmentPanels.ALPHA_X1000] = (int) Math.round(alpha * 1000);
        if (xf > hall.tailsX() && xf < hall.productX()) {
            IdealCascade c = new IdealCascade(alpha, hall.productX(), xf, hall.tailsX());
            v[EnrichmentPanels.STAGES] = c.stages();
            v[EnrichmentPanels.X234_P] = (int) Math.round(c.productU234(feed.empty() ? Uf6Charge.NATURAL_U234 : feed.u234()) * 1e5);
        }
        v[EnrichmentPanels.POWER_KW] = (int) Math.round(loadW / 1e3);
        v[EnrichmentPanels.DESIGN] = d.ordinal();
        v[EnrichmentPanels.CIRC] = (int) Math.round(hall.circulation() * 100);
        boolean supercritical = Centrifuge.criticalRatio(d.rotor, hall.speed(), d.lengthM) > 0.8;
        v[EnrichmentPanels.FLAGS] = (r.productKgS() > 0 ? 1 << EnrichmentPanels.F_RUN : 0) | (r.interlocked() ? 1 << EnrichmentPanels.F_LOCKED : 0)
                | (excursion ? 1 << EnrichmentPanels.F_EXCURSION : 0) | (r.tooFewMachines() ? 1 << EnrichmentPanels.F_FEW : 0)
                | (!hall.interlock() ? 1 << EnrichmentPanels.F_NO_INTERLOCK : 0)
                | (hall.running() && feed.empty() ? 1 << EnrichmentPanels.F_NO_FEED : 0)
                | (supercritical ? 1 << EnrichmentPanels.F_SUPERCRITICAL : 0);
    }

    /** Шаг уставки обогащения отбора: 0,1 % до 5 %, 1 % выше. */
    private static double xpStep(double x, boolean up) {
        return (up ? x >= 0.05 - 1e-9 : x > 0.05 + 1e-9) ? 0.01 : 0.001;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case EnrichmentPanels.B_RUN -> toggle(player);
            case EnrichmentPanels.B_DESIGN -> {
                CentrifugeHall.Design[] all = CentrifugeHall.Design.values();
                hall.setDesign(all[(hall.design().ordinal() + 1) % all.length]);
            }
            case EnrichmentPanels.B_XP_DOWN -> hall.setProductX(hall.productX() - xpStep(hall.productX(), false));
            case EnrichmentPanels.B_XP_UP -> hall.setProductX(hall.productX() + xpStep(hall.productX(), true));
            case EnrichmentPanels.B_XW_DOWN -> hall.setTailsX(hall.tailsX() - 0.0005);
            case EnrichmentPanels.B_XW_UP -> hall.setTailsX(hall.tailsX() + 0.0005);
            case EnrichmentPanels.B_SPEED_DOWN -> hall.setSpeedFraction(hall.speedFraction() - 0.01);
            case EnrichmentPanels.B_SPEED_UP -> hall.setSpeedFraction(hall.speedFraction() + 0.01);
            case EnrichmentPanels.B_CIRC_DOWN -> hall.setCirculation(hall.circulation() - 0.1);
            case EnrichmentPanels.B_CIRC_UP -> hall.setCirculation(hall.circulation() + 0.1);
            case EnrichmentPanels.B_INTERLOCK -> hall.setInterlock(!hall.interlock());
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
        tag.put("cylinders", cylinders.serializeNBT(registries));
        MutableStateTree tree = new MutableStateTree();
        hall.save(new TreeStateWriter(tree));
        tag.put("hall", NbtStateTrees.toNbt(tree));
        tag.putBoolean("excursion", excursion);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        if (tag.contains("cylinders")) cylinders.deserializeNBT(registries, tag.getCompound("cylinders"));
        if (tag.contains("hall")) hall.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("hall"))));
        excursion = tag.getBoolean("excursion");
    }
}
