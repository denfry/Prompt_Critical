package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.binding.DeviceHost;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.item.CoalAssayData;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.DeviceMode;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.metallurgy.CokeBattery;
import dev.denfry.promptcritical.sim.metallurgy.CokeBatteryUnit;
import dev.denfry.promptcritical.sim.metallurgy.CokeByproduct;
import dev.denfry.promptcritical.sim.metallurgy.CyclicMachine;
import dev.denfry.promptcritical.sim.metallurgy.GasMain;
import dev.denfry.promptcritical.sim.metallurgy.IronmakingLog;
import dev.denfry.promptcritical.sim.metallurgy.IronmakingSubsystem;
import dev.denfry.promptcritical.sim.metallurgy.OvenState;
import dev.denfry.promptcritical.sim.metallurgy.ProcessGas;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Клетка коксовой батареи {@code coke_oven} (08 §8.4). Батарея — свободная сборка ({@link CokeBatteryShape}); её
 * контроллер — одна клетка, зарегистрированная в домене ({@link CokeBatteryUnit}): та, что уже была контроллером, или
 * первая клетка первой камеры. Остальные клетки отдают руки, щит, склад и бак контроллеру.
 *
 * <p>Машины — контрапции Create: загрузочный вагон над камерой (склад контрапции) отдаёт шихту в люки,
 * выталкиватель — {@link CokePusherRamMovementBehaviour}, кокс уходит в склад контрапции у коксовой стороны
 * (тушильный вагон) или в склад-блок у двери; иначе — горящий кокс на землю. Воронкой можно грузить и в люк
 * (верх камеры). Газ обогрева — в любую клетку трубой Create; топки разогрева — слот щита или боковые грани.
 */
public final class CokeOvenBlockEntity extends BlockEntity implements DeviceHost, FoundryDevice, PanelHost {
    private static final int VALIDATE_PERIOD = 40, ORPHAN_TICKS = 100, CHARGE_PERIOD = 5, SLOW_PERIOD = 20;
    private static final int CHARGE_ITEMS_PER_PASS = 4;
    /** Шихта без анализа — типичная Ж+К+ОС (08 §8.4.2). */
    private static final CoalAssay DEFAULT_BLEND = CoalAssay.typical(0.27, 0.08, 0.08, 0.008, 17);
    private static final double HOT_WALL_K = 773.15;
    /** Доли смолы, которые снимают холодильник и электрофильтр (моё). */
    private static final double COOLER_TAR = 0.6;
    /** Взрыв в газосборнике: ≈ 5 кг ТНТ, радиус 6 м [BALANCE]. */
    private static final double MAIN_BLAST_J = 2e7, MAIN_BLAST_R = 6;

    public enum PushOutcome { NONE, PUSHED, OVERLOAD }

    private SimId simId = SimId.NONE;
    private boolean chunkUnloading;
    @Nullable
    private BlockPos master;
    private long claimedAt = Long.MIN_VALUE / 2;
    // контроллер
    @Nullable
    private CokeBatteryShape shape;
    private long[] ovenKeys = new long[0];
    private String problem = "";
    private PcProperties.OvenLook[] looks = new PcProperties.OvenLook[0];
    private boolean wallsHot;
    private final GasBuffer gas = new GasBuffer(8_000, GasBuffer::isProcessGas, this::setChanged);
    private final MachineInventory firebox = new MachineInventory(new MachineInventory.Role[] {MachineInventory.Role.INPUT},
            (slot, s) -> s.is(IronworksTags.FIREBOX_FUEL), this::setChanged);

    public CokeOvenBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.COKE_OVEN.get(), pos, state);
    }

    // --- контроллер и форма ---

    private boolean isMaster() {
        return !simId.isNone();
    }

    /** Контроллер батареи этой клетки (сама клетка, если она контроллер); {@code null} — батареи нет. */
    @Nullable
    public CokeOvenBlockEntity master() {
        if (isMaster()) return this;
        if (master == null || level == null || !level.isLoaded(master)) return null;
        return level.getBlockEntity(master) instanceof CokeOvenBlockEntity m && m.isMaster() ? m : null;
    }

    @Nullable
    public CokeBatteryShape shape() {
        return shape;
    }

    @Nullable
    CokeBatteryUnit unit() {
        return Ironworks.unit(Ironworks.binding(level), simId, CokeBatteryUnit.class);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel)) return;
        SimLevelBinding b = Ironworks.binding(level);
        if (b == null) return;
        long now = level.getGameTime();
        boolean due = now % VALIDATE_PERIOD == Math.floorMod(worldPosition.asLong(), VALIDATE_PERIOD);
        if (isMaster()) {
            if (due) validate(b, now);
            if (shape != null) work(b, now);
        } else if (due && now - claimedAt > ORPHAN_TICKS && scanner()) {
            validate(b, now);
        }
    }

    /** Ищут батарею только первые нижние клетки камер (иначе строящаяся батарея сканируется сотнями клеток). */
    private boolean scanner() {
        BlockState oven = PcBlocks.COKE_OVEN.get().defaultBlockState();
        return !level.getBlockState(worldPosition.below()).is(oven.getBlock())
                && !level.getBlockState(worldPosition.west()).is(oven.getBlock())
                && !level.getBlockState(worldPosition.north()).is(oven.getBlock());
    }

    private void validate(SimLevelBinding b, long now) {
        CokeBatteryShape.Scan scan = CokeBatteryShape.scan(level, worldPosition);
        CokeBatteryShape s = scan.shape();
        if (s == null) {
            if (isMaster()) {
                problem = scan.problem();
                shape = null;
            }
            return;
        }
        BlockPos chosen = null;
        for (int i = 0; i < s.ovens(); i++)
            for (int l = 0; l < s.length(); l++) {
                BlockPos p = s.oven(i, l, 0);
                if (level.getBlockEntity(p) instanceof CokeOvenBlockEntity c && c.isMaster()
                        && (chosen == null || p.compareTo(chosen) < 0)) chosen = p;
            }
        if (chosen == null) chosen = s.origin();
        if (!chosen.equals(worldPosition)) {
            if (isMaster()) b.detach(this, true);
            simId = SimId.NONE;
            return;
        }
        if (!isMaster()) b.attach(this);
        CokeBatteryUnit u = unit();
        if (u == null) return;
        long[] keys = new long[s.ovens()];
        for (int i = 0; i < keys.length; i++) keys[i] = s.ovenKey(i);
        int[] map = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            map[i] = -1;
            for (int j = 0; j < ovenKeys.length; j++) if (ovenKeys[j] == keys[i]) map[i] = j;
        }
        double width = b.world().subsystem(IronmakingSubsystem.class).config().cokeChamberWidthM();
        double item = Ironworks.bulkKg();
        double charge = Math.max(item, Math.round(CokeBatteryUnit.chargeKgFor(s.length(), width) / item) * item);
        u.form(b.world().subsystem(IronmakingSubsystem.class).config(), s.ovens(), charge, map);
        if (looks.length != s.ovens() || !Arrays.equals(keys, ovenKeys)) looks = new PcProperties.OvenLook[s.ovens()];
        ovenKeys = keys;
        shape = s;
        problem = "";
        for (int i = 0; i < s.ovens(); i++)
            for (int l = 0; l < s.length(); l++)
                for (int dy = 0; dy < CokeBatteryShape.HEIGHT; dy++)
                    if (level.getBlockEntity(s.oven(i, l, dy)) instanceof CokeOvenBlockEntity c && c != this) {
                        c.master = worldPosition;
                        c.claimedAt = now;
                    }
        claimedAt = now;
        setChanged();
    }

    private void work(SimLevelBinding b, long now) {
        CokeBatteryUnit u = unit();
        CokeBattery bat = u == null ? null : u.battery();
        if (bat == null || bat.ovenCount() != shape.ovens()) return;
        double dtp = Ironworks.tickPhysS(b);
        ProcessGas g = gas.gas();
        if (g != null) u.setGasSupply(g, gas.draw(u.gasDemandNm3S(g) * dtp) / dtp);
        else u.setGasSupply(null, 0);
        if (u.fireboxHungry()) {
            ItemStack fuel = firebox.getStackInSlot(0);
            if (!fuel.isEmpty()) {
                fuel.shrink(1);
                u.addFireboxHeat(Ironworks.bulkKg() * Ironworks.SOLID_FUEL_J_KG * Ironworks.FIREBOX_EFFICIENCY);
            }
        }
        exhausters(b, u, dtp);
        if (now % CHARGE_PERIOD == 0) chargeFromCars(bat);
        if (now % SLOW_PERIOD == 0) {
            byproducts(bat);
            looks(bat);
        }
        for (IronmakingLog.Entry e; (e = u.log().poll()) != null; ) event(b, e);
    }

    private void exhausters(SimLevelBinding b, CokeBatteryUnit u, double dtp) {
        List<GasExhausterBlockEntity> ex = new ArrayList<>();
        for (BlockPos p : shape.devices()) if (level.getBlockEntity(p) instanceof GasExhausterBlockEntity e) ex.add(e);
        double cap = 0, sum = 0, room = 0;
        int running = 0;
        double[] s = new double[ex.size()];
        for (int i = 0; i < ex.size(); i++) {
            CyclicMachine m = ex.get(i).machine();
            s[i] = m == null ? 0 : m.speedRatio();
            if (s[i] > 0) {
                cap += GasMain.EXHAUSTER_NM3S * s[i];
                sum += s[i];
                running++;
                room += ex.get(i).outlet().room();
            }
        }
        double delivered = u.main().deliveredNm3S() * dtp;
        double total = 0;
        for (double v : s) total += v;
        for (int i = 0; i < ex.size() && total > 0; i++)
            if (s[i] > 0) ex.get(i).outlet().put(GasBuffer.fluid(ProcessGas.COKE_OVEN.fluidId), delivered * s[i] / total);
        u.setExhausters(cap, running > 0 ? sum / running : 0, room / dtp);
    }

    // --- загрузка ---

    private AABB carZone(int oven) {
        BlockPos a = shape.oven(oven, 0, CokeBatteryShape.HEIGHT), z = shape.oven(oven, shape.length() - 1, CokeBatteryShape.HEIGHT + 3);
        return AABB.encapsulatingFullBlocks(a, z);
    }

    private void chargeFromCars(CokeBattery bat) {
        for (int i = 0; i < shape.ovens(); i++) {
            OvenState st = bat.oven(i);
            if (st != OvenState.EMPTY && st != OvenState.CHARGING) continue;
            for (IItemHandlerModifiable store : Ironworks.contraptionStores(level, carZone(i))) {
                int moved = 0;
                for (int slot = 0; slot < store.getSlots() && moved < CHARGE_ITEMS_PER_PASS; slot++) {
                    while (moved < CHARGE_ITEMS_PER_PASS) {
                        ItemStack s = store.getStackInSlot(slot);
                        if (s.isEmpty() || chargeOne(bat, i, s) <= 0) break;
                        store.extractItem(slot, 1, false);
                        moved++;
                    }
                }
            }
        }
    }

    /** Загрузить один предмет шихты в камеру; принятая масса, кг. */
    double chargeOne(CokeBattery bat, int oven, ItemStack s) {
        double kg = Ironworks.bulkKg();
        if (s.is(PcItems.COAL_TAR_PITCH.get())) return bat.chargePitch(oven, kg);
        if (!s.is(IronworksTags.COKING_COAL)) return 0;
        CoalAssayData a = s.get(PcDataComponents.COAL_ASSAY.get());
        return bat.charge(oven, kg, a == null ? DEFAULT_BLEND : a.toAssay());
    }

    /** Свободно в камере под шихту, предметов. */
    int roomItems(CokeBattery bat, int oven) {
        OvenState st = bat.oven(oven);
        if (st != OvenState.EMPTY && st != OvenState.CHARGING) return 0;
        return (int) Math.ceil((bat.designChargeKg() - bat.chargeKg(oven)) / Ironworks.bulkKg() - 1e-9);
    }

    // --- выталкивание ---

    /**
     * Штанга выталкивателя вошла в торец камеры со стороны {@code ramFacing.getOpposite()}.
     *
     * @param visited клетка камеры, в которую вошла штанга
     */
    public PushOutcome ramPush(BlockPos visited, Direction ramFacing) {
        CokeBatteryUnit u = unit();
        CokeBattery bat = u == null ? null : u.battery();
        if (shape == null || bat == null || ramFacing.getAxis() != shape.axis()) return PushOutcome.NONE;
        int i = shape.ovenAt(visited);
        if (i < 0) return PushOutcome.NONE;
        boolean fromLow = ramFacing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        int along = shape.along(visited);
        if (along != (fromLow ? shape.l0() : shape.l0() + shape.length() - 1)) return PushOutcome.NONE;
        OvenState before = bat.oven(i);
        CokeBattery.PushResult r = bat.push(i, true);
        if (r == null) return before == OvenState.COKING || before == OvenState.STICKER ? PushOutcome.OVERLOAD : PushOutcome.NONE;
        BlockPos door = shape.oven(i, fromLow ? shape.length() : -1, 0);
        deliver(r, door, ramFacing);
        if (level instanceof ServerLevel sl)
            SimAchievements.nearby(sl, door, SimAchievements.COKE_PUSHED, r.pitchCoke() ? "pitch_coke" : "coke", r.cokeKg());
        looks(bat);
        level.playSound(null, door, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.6f);
        return PushOutcome.PUSHED;
    }

    private void deliver(CokeBattery.PushResult r, BlockPos door, Direction out) {
        List<IItemHandler> stores = new ArrayList<>(Ironworks.contraptionStores(level,
                AABB.encapsulatingFullBlocks(door, door.relative(out, 2).above(CokeBatteryShape.HEIGHT))));
        IItemHandler block = level.getCapability(Capabilities.ItemHandler.BLOCK, door, out.getOpposite());
        if (block != null) stores.add(block);
        ItemStack coke;
        if (r.pitchCoke()) {
            coke = new ItemStack(PcItems.PITCH_COKE.get(), Ironworks.items(r.cokeKg()));
        } else {
            coke = new ItemStack(PcItems.COKE.get(), Ironworks.items(r.cokeKg()));
            coke.set(IronworksComponents.COKE_ASSAY.get(), IronworksComponents.CokeAssay.of(r.assay(), true));
        }
        ItemStack breeze = new ItemStack(PcItems.COKE_BREEZE.get(), Ironworks.items(r.breezeKg()));
        boolean spilled = false;
        for (ItemStack s : new ItemStack[] {coke, breeze}) {
            ItemStack rest = Ironworks.insert(stores, s);
            if (!rest.isEmpty()) {
                spilled = true;
                Ironworks.spill(level, door, rest);
            }
        }
        if (spilled && level.getBlockState(door).isAir() && BaseFireBlock.canBePlacedAt(level, door, out))
            level.setBlock(door, BaseFireBlock.getState(level, door), Block.UPDATE_ALL);
    }

    // --- улавливание, вид, события ---

    private void byproducts(CokeBattery bat) {
        double tar = bat.takeByproduct(CokeByproduct.TAR), nh3 = bat.takeByproduct(CokeByproduct.AMMONIA),
                benzol = bat.takeByproduct(CokeByproduct.CRUDE_BENZOL);
        bat.takeByproduct(CokeByproduct.HYDROGEN_SULFIDE);
        bat.takeByproduct(CokeByproduct.AMMONIA_LIQUOR);
        List<ByproductPlantBlockEntity> plants = new ArrayList<>();
        for (BlockPos p : shape.devices()) if (level.getBlockEntity(p) instanceof ByproductPlantBlockEntity e) plants.add(e);
        share(plants, PcProperties.ByproductKind.COOLER, tar * COOLER_TAR);
        share(plants, PcProperties.ByproductKind.PRECIPITATOR, tar * (1 - COOLER_TAR));
        share(plants, PcProperties.ByproductKind.SATURATOR, nh3);
        share(plants, PcProperties.ByproductKind.SCRUBBER, benzol);
    }

    private static void share(List<ByproductPlantBlockEntity> plants, PcProperties.ByproductKind kind, double kg) {
        List<ByproductPlantBlockEntity> of = new ArrayList<>();
        for (ByproductPlantBlockEntity p : plants) if (p.kind() == kind) of.add(p);
        for (ByproductPlantBlockEntity p : of) p.capture(kg / of.size());
    }

    private void looks(CokeBattery bat) {
        for (int i = 0; i < shape.ovens(); i++) {
            PcProperties.OvenLook look = switch (bat.oven(i)) {
                case EMPTY, CHARGING -> PcProperties.OvenLook.EMPTY;
                case COKING, STICKER -> PcProperties.OvenLook.COKING;
                case READY -> PcProperties.OvenLook.READY;
            };
            if (looks[i] == look) continue;
            looks[i] = look;
            for (int l = 0; l < shape.length(); l++)
                for (int dy = 0; dy < CokeBatteryShape.HEIGHT; dy++) {
                    BlockPos p = shape.oven(i, l, dy);
                    BlockState s = level.getBlockState(p);
                    if (s.hasProperty(PcProperties.OVEN_STATE) && s.getValue(PcProperties.OVEN_STATE) != look)
                        level.setBlock(p, s.setValue(PcProperties.OVEN_STATE, look), Block.UPDATE_CLIENTS);
                }
        }
        boolean hot = bat.wallTempK() > HOT_WALL_K;
        if (hot == wallsHot) return;
        wallsHot = hot;
        for (int k = 0; k <= shape.ovens(); k++)
            for (int l = 0; l < shape.length(); l++)
                for (int dy = 0; dy < CokeBatteryShape.HEIGHT; dy++) {
                    BlockPos p = shape.at(shape.wall0() + 2 * k, shape.l0() + l, shape.y0() + dy);
                    BlockState s = level.getBlockState(p);
                    if (s.hasProperty(PcProperties.HOT)) level.setBlock(p, s.setValue(PcProperties.HOT, hot), Block.UPDATE_CLIENTS);
                }
    }

    private void event(SimLevelBinding b, IronmakingLog.Entry e) {
        switch (e.kind()) {
            case PUSH_OVERLOAD -> {
                level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0f, 0.5f);
                tell(IronworksText.msg("push_overload", e.index() + 1));
            }
            case REFRACTORY_FAILED -> tell(IronworksText.msg("refractory_failed"));
            case GAS_MAIN_EXPLOSION -> {
                BlockPos at = shape.mains().iterator().next();
                tell(IronworksText.msg("gas_main_explosion"));
                Ironworks.blast(b, at, MAIN_BLAST_J, MAIN_BLAST_R, 3);
            }
            default -> {
            }
        }
    }

    private void tell(Component c) {
        Vec3 at = Vec3.atCenterOf(worldPosition);
        for (Player p : level.players()) if (p.distanceToSqr(at) < 48 * 48) p.displayClientMessage(c, false);
    }

    // --- руки, склады, щит ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        if (side == Direction.UP && !level.getBlockState(worldPosition.above()).is(getBlockState().getBlock())) return new Hatch();
        CokeOvenBlockEntity m = master();
        return m == null ? null : m.firebox.external();
    }

    @Override
    @Nullable
    public IFluidHandler fluids(@Nullable Direction side) {
        CokeOvenBlockEntity m = master();
        return m == null ? null : m.gas.input();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(IronworksTags.FIREBOX_FUEL);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        CokeOvenBlockEntity m = master();
        if (m == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        player.setItemInHand(hand, m.firebox.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        CokeOvenBlockEntity m = master();
        if (m != null && player instanceof ServerPlayer sp) {
            PanelHost.open(sp, m);
            return InteractionResult.CONSUME;
        }
        String why = problem.isEmpty() ? CokeBatteryShape.scan(level, worldPosition).problem() : problem;
        player.displayClientMessage(IronworksText.msg("battery_unformed", IronworksText.msg("problem." + (why.isEmpty() ? "forming" : why))), true);
        return InteractionResult.SUCCESS;
    }

    /** Люк камеры: шихта воронкой сверху. */
    private final class Hatch implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            CokeOvenBlockEntity m = master();
            CokeBatteryUnit u = m == null ? null : m.unit();
            CokeBattery bat = u == null ? null : u.battery();
            if (bat == null || m.shape == null || stack.isEmpty()) return stack;
            int oven = m.shape.ovenAt(worldPosition);
            if (oven < 0 || !(stack.is(IronworksTags.COKING_COAL) || stack.is(PcItems.COAL_TAR_PITCH.get()))) return stack;
            int n = Math.min(stack.getCount(), m.roomItems(bat, oven));
            if (n <= 0) return stack;
            if (!simulate) {
                int done = 0;
                while (done < n && m.chargeOne(bat, oven, stack) > 0) done++;
                n = done;
            }
            return stack.copyWithCount(stack.getCount() - n);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(IronworksTags.COKING_COAL) || stack.is(PcItems.COAL_TAR_PITCH.get());
        }
    }

    @Override
    public PanelLayout panelLayout() {
        return IronworksPanels.COKE_BATTERY;
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
        if (isRemoved() || shape == null) return false;
        BlockPos a = shape.oven(0, 0, 0), z = shape.oven(shape.ovens() - 1, shape.length() - 1, CokeBatteryShape.HEIGHT);
        return AABB.encapsulatingFullBlocks(a, z).inflate(8).contains(player.position());
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return firebox;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        CokeBatteryUnit u = unit();
        CokeBattery bat = u == null ? null : u.battery();
        if (bat == null) return;
        out[IronworksPanels.B_WALL_C] = (int) Math.round(bat.wallTempK() - 273.15);
        out[IronworksPanels.B_SET_C] = (int) Math.round(u.setpointK() - 273.15);
        out[IronworksPanels.B_RAMP_KH10] = (int) Math.round(u.rampKH() * 10);
        out[IronworksPanels.B_MAIN_PA] = (int) Math.round(u.main().pressurePa());
        out[IronworksPanels.B_GAS_NM3H] = (int) Math.round(u.gasSupplyNm3S() * 3600);
        out[IronworksPanels.B_MAKE_NM3H] = (int) Math.round(bat.gasMakeNm3S() * 3600);
        out[IronworksPanels.B_FIREBOX_KW] = (int) Math.round(u.fireboxW() / 1000);
        out[IronworksPanels.B_DAMAGE_PERMIL] = (int) Math.round(bat.refractoryDamage() * 1000);
        out[IronworksPanels.B_OVENS] = bat.ovenCount();
        out[IronworksPanels.B_GAS_MB] = gas.amount();
        int empty = 0, coking = 0, ready = 0;
        for (int i = 0; i < bat.ovenCount(); i++) {
            switch (bat.oven(i)) {
                case EMPTY, CHARGING -> empty++;
                case COKING, STICKER -> coking++;
                case READY -> ready++;
            }
        }
        out[IronworksPanels.B_EMPTY] = empty;
        out[IronworksPanels.B_COKING] = coking;
        out[IronworksPanels.B_READY] = ready;
        int flags = 0;
        if (bat.heatedUp()) flags |= 1 << IronworksPanels.BF_HOT;
        if (u.main().ventedNm3S() > 0) flags |= 1 << IronworksPanels.BF_BLEED;
        if (u.main().pressurePa() < GasMain.VACUUM_PA) flags |= 1 << IronworksPanels.BF_VACUUM;
        if (bat.refractoryFailed()) flags |= 1 << IronworksPanels.BF_FAILED;
        out[IronworksPanels.B_FLAGS] = flags;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        CokeBatteryUnit u = unit();
        if (u == null) return;
        double set = u.setpointK(), ramp = u.rampKH();
        switch (id) {
            case IronworksPanels.BTN_SET_DOWN -> set -= 50;
            case IronworksPanels.BTN_SET_UP -> set += 50;
            case IronworksPanels.BTN_RAMP_DOWN -> ramp -= ramp > 10 ? 5 : 1;
            case IronworksPanels.BTN_RAMP_UP -> ramp += ramp >= 10 ? 5 : 1;
            default -> {
                return;
            }
        }
        u.setSetpoint(set, ramp);
    }

    // --- DeviceHost ---

    @Override
    public SimId simId() {
        return simId;
    }

    @Override
    public void bindSimId(SimId id) {
        if (!id.equals(simId)) {
            simId = id;
            setChanged();
        }
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel) level;
    }

    @Override
    public BlockPos pos() {
        return worldPosition;
    }

    @Override
    public String deviceType() {
        return IronmakingSubsystem.COKE_OVEN;
    }

    @Override
    public DeviceMode mode() {
        SimLevelBinding b = Ironworks.binding(level);
        if (b == null || simId.isNone() || !b.world().topology().exists(simId)) return DeviceMode.LOCAL;
        return b.world().topology().deviceMode(simId);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        chunkUnloading = false;
        SimLevelBinding b = Ironworks.binding(level);
        if (b != null && isMaster()) b.attach(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        chunkUnloading = true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        SimLevelBinding b = Ironworks.binding(level);
        if (b != null && isMaster()) b.detach(this, !chunkUnloading);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("sim_id", simId.raw());
        if (!isMaster()) return;
        tag.putLongArray("oven_keys", ovenKeys);
        tag.put("gas", gas.save(registries));
        tag.put("firebox", firebox.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        simId = new SimId(tag.getLong("sim_id"));
        ovenKeys = tag.getLongArray("oven_keys");
        if (tag.contains("gas")) gas.load(registries, tag.getCompound("gas"));
        if (tag.contains("firebox")) firebox.load(registries, tag.getCompound("firebox"));
    }
}
