package dev.denfry.promptcritical.mod.electronics;

import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.steelworks.SteelworksComponents;
import dev.denfry.promptcritical.sim.electronics.ChipDesign;
import dev.denfry.promptcritical.sim.electronics.Cleanroom;
import dev.denfry.promptcritical.sim.electronics.PlanarProcess;
import dev.denfry.promptcritical.sim.electronics.WaferFab;
import dev.denfry.promptcritical.sim.grid.ConsumerUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.metallurgy.Ferroalloy;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Шлюз чистой комнаты {@code cleanroom_airlock} — пульт планарного процесса (11 §11.5.3). Комната — воздух за шлюзом,
 * замкнутый стенами {@code cleanroom_panel}, окнами {@code cleanroom_window} и потолочными фильтрами
 * {@code hepa_filter} (вентиляторный модуль, {@value #HEPA_M3H} м³/ч чистого воздуха, пока на шлюз подано
 * напряжение). Любой другой сплошной блок в оболочке — щель с подсосом {@value #LEAK_PER_FACE} м³/ч, открытая дверь
 * шлюза (Shift + ПКМ) — {@value #DOOR_LEAK} м³/ч, каждый игрок внутри — источник частиц. Концентрация частиц идёт к
 * равновесию {@link Cleanroom#concentration} с постоянной времени V/(L + Q·η): после открытой двери комнату надо
 * продуть.
 *
 * <p>Пластина в слоте проходит окисление, литографию, загонку фосфора и металлизацию алюминием ({@link WaferFab}) за
 * физическое время процесса; годные кристаллы — по чистоте комнаты в момент выхода пластины.
 */
public final class CleanroomAirlockBlockEntity extends GridDeviceBlockEntity<ConsumerUnit> implements PanelHost {
    public static final int DEMAND_KW = 150, MAX_VOLUME = 512, SCAN_PERIOD = 100;
    public static final double HEPA_M3H = 1500, LEAK_PER_FACE = 2, DOOR_LEAK = 300, SEALED_RESIDUAL = 0.1;
    /** Алюминия на пластины: один на {@value #WAFERS_PER_ALUMINIUM}. */
    public static final int WAFERS_PER_ALUMINIUM = 4;
    /** Легирование подложки бором (10 Ом·см), см⁻³. */
    public static final double SUBSTRATE_CM3 = 1.3e15;
    /** Физических часов за тик при K_slow = 72. */
    static final double PHYS_H_PER_TICK = 0.05 * 72 / 3600;

    private final ItemStackHandler inv = new ItemStackHandler(ElectronicsPanels.SLOT_OUT + ElectronicsPanels.OUT_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack s) {
            return switch (slot) {
                case ElectronicsPanels.SLOT_WAFER -> s.is(PcItems.SILICON_WAFER.get());
                case ElectronicsPanels.SLOT_ALUMINIUM -> isAluminium(s);
                case ElectronicsPanels.SLOT_LENS -> s.is(PcItems.STEPPER_LENS.get());
                default -> s.has(ElectronicsComponents.CHIP.get());
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == ElectronicsPanels.SLOT_LENS ? 1 : 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler external = new ElectronicsBlocks.SlotsIo(inv, ElectronicsPanels.SLOT_OUT, ElectronicsPanels.SLOT_OUT,
            ElectronicsPanels.OUT_SLOTS);
    // рецепт
    private int design, litho = WaferFab.Litho.PROXIMITY.ordinal(), gap = 20, ambient, oxT = 1000, oxMin = 60, diffT = 1000, diffMin = 30;
    // комната
    private Set<BlockPos> room = Set.of();
    private boolean enclosed;
    private int hepa;
    private double leakM3H, concentration = Cleanroom.OUTSIDE_PER_M3;
    // процесс
    private double processH;
    private int waferCount;
    private boolean demandSet, running;
    @Nullable
    private WaferFab.Result last;

    public CleanroomAirlockBlockEntity(BlockPos pos, BlockState state) {
        super(ElectronicsBlocks.CLEANROOM_AIRLOCK.get(), pos, state);
    }

    public static boolean isAluminium(ItemStack s) {
        return s.is(PcItems.FERROALLOY.get()) && Ferroalloy.ALUMINIUM.id.equals(s.get(SteelworksComponents.FERROALLOY.get()));
    }

    public double concentration() {
        return concentration;
    }

    public boolean enclosed() {
        return enclosed;
    }

    public int roomVolume() {
        return room.size();
    }

    @Nullable
    public WaferFab.Result lastRun() {
        return last;
    }

    /** Рецепт для тестов и автоматики. */
    public void setRecipe(ChipDesign d, WaferFab.Litho l, int gapUm, PlanarProcess.Ambient a, int oxTempC, int oxMinutes, int diffTempC,
            int diffMinutes) {
        design = d.ordinal();
        litho = l.ordinal();
        gap = gapUm;
        ambient = a.ordinal();
        oxT = oxTempC;
        oxMin = oxMinutes;
        diffT = diffTempC;
        diffMin = diffMinutes;
        setChanged();
    }

    WaferFab fab() {
        return new WaferFab(ChipDesign.values()[design], WaferFab.Litho.values()[litho], gap, PlanarProcess.Ambient.values()[ambient], oxT,
                oxMin / 60.0, diffT, diffMin / 60.0, SUBSTRATE_CM3);
    }

    private boolean doorOpen() {
        BlockState s = getBlockState();
        return s.hasProperty(BlockStateProperties.OPEN) && s.getValue(BlockStateProperties.OPEN);
    }

    /** Обход комнаты за шлюзом: воздух и несплошные блоки внутри, оболочка — стены, окна, фильтры и щели. */
    void scan() {
        if (level == null) return;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = worldPosition.relative(facing().getOpposite());
        int filters = 0, leaks = 0;
        boolean closed = true;
        if (isInterior(level.getBlockState(start), start)) {
            seen.add(start);
            queue.add(start);
        } else closed = false;
        Set<BlockPos> shell = new HashSet<>();
        while (!queue.isEmpty() && closed) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n) || n.equals(worldPosition)) continue;
                if (!level.isLoaded(n)) {
                    closed = false;
                    break;
                }
                BlockState s = level.getBlockState(n);
                if (isInterior(s, n)) {
                    if (seen.size() >= MAX_VOLUME) {
                        closed = false;
                        break;
                    }
                    seen.add(n);
                    queue.add(n);
                } else if (shell.add(n)) {
                    if (s.is(PcBlocks.HEPA_FILTER.get())) filters++;
                    else if (!s.is(PcBlocks.CLEANROOM_PANEL.get()) && !s.is(PcBlocks.CLEANROOM_WINDOW.get())) leaks++;
                }
            }
        }
        enclosed = closed && !seen.isEmpty();
        room = enclosed ? Set.copyOf(seen) : Set.of();
        hepa = enclosed ? filters : 0;
        leakM3H = SEALED_RESIDUAL + leaks * LEAK_PER_FACE;
    }

    private boolean isInterior(BlockState s, BlockPos p) {
        return s.isAir() || !s.isCollisionShapeFullBlock(level, p);
    }

    private int peopleInside() {
        if (!(level instanceof ServerLevel sl) || room.isEmpty()) return 0;
        int n = 0;
        for (ServerPlayer p : sl.players()) if (room.contains(p.blockPosition()) || room.contains(p.blockPosition().above())) n++;
        return n;
    }

    /** Подсос наружного воздуха сейчас, м³/ч. */
    double leakNow() {
        return leakM3H + (doorOpen() ? DOOR_LEAK : 0);
    }

    @Override
    protected ConsumerUnit unitOf(GridSubsystem grid) {
        return grid.consumer(simId());
    }

    @Override
    public List<List<GridDevice.Face>> terminals() {
        return List.of(GridDevice.allFaces(worldPosition));
    }

    @Override
    protected void serverTick(ConsumerUnit u) {
        if (!demandSet) {
            u.setDemandKw(DEMAND_KW);
            demandSet = true;
        }
        if (ticks % SCAN_PERIOD == 0) scan();
        boolean power = u.energized();
        if (ticks % 20 == 0) {
            if (!enclosed) concentration = Cleanroom.OUTSIDE_PER_M3;
            else {
                double q = power ? hepa * HEPA_M3H : 0, l = leakNow();
                double eq = Cleanroom.concentration(l, q, peopleInside());
                double tauH = room.size() / Math.max(1e-6, l + q * Cleanroom.HEPA_EFFICIENCY);
                concentration = eq + (concentration - eq) * Math.exp(-20 * PHYS_H_PER_TICK / tauH);
            }
        }
        WaferFab f = fab();
        boolean lens = f.litho() != WaferFab.Litho.G_LINE || !inv.getStackInSlot(ElectronicsPanels.SLOT_LENS).isEmpty();
        boolean aluminium = waferCount % WAFERS_PER_ALUMINIUM != 0 || !inv.getStackInSlot(ElectronicsPanels.SLOT_ALUMINIUM).isEmpty();
        running = power && enclosed && lens && aluminium && !inv.getStackInSlot(ElectronicsPanels.SLOT_WAFER).isEmpty() && hasRoom();
        if (running) {
            processH += PHYS_H_PER_TICK;
            if (processH >= f.processHours()) {
                processH = 0;
                finish(f);
            }
        }
        if (ticks % 10 == 0) setFlag(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING, running);
    }

    private boolean hasRoom() {
        for (int i = 0; i < ElectronicsPanels.OUT_SLOTS; i++) if (inv.getStackInSlot(ElectronicsPanels.SLOT_OUT + i).isEmpty()) return true;
        return false;
    }

    private void finish(WaferFab f) {
        inv.getStackInSlot(ElectronicsPanels.SLOT_WAFER).shrink(1);
        if (waferCount % WAFERS_PER_ALUMINIUM == 0) inv.getStackInSlot(ElectronicsPanels.SLOT_ALUMINIUM).shrink(1);
        waferCount++;
        WaferFab.Result r = f.run(concentration, ElectronicsComponents.INGOT_MM);
        last = r;
        int good = Math.min(64, r.good());
        if (good <= 0) return;
        ItemStack chips = new ItemStack(itemFor(f.design()), good);
        chips.set(ElectronicsComponents.CHIP.get(), new ElectronicsComponents.Chip(f.design().name(), r.cdUm(), r.clockMhz(), f.design().transistors,
                r.dieMm2()));
        for (int i = 0; i < ElectronicsPanels.OUT_SLOTS && !chips.isEmpty(); i++)
            chips = inv.insertItem(ElectronicsPanels.SLOT_OUT + i, chips, false);
    }

    static Item itemFor(ChipDesign d) {
        return switch (d) {
            case TRANSISTOR -> PcItems.TRANSISTOR.get();
            case LOGIC -> PcItems.INTEGRATED_CIRCUIT.get();
            case MEMORY -> PcItems.MEMORY_CHIP.get();
            case MICROPROCESSOR -> PcItems.MICROPROCESSOR.get();
        };
    }

    // --- руки ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return external;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        for (int i = 0; i < ElectronicsPanels.SLOT_OUT; i++) if (inv.isItemValid(i, stack)) return true;
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        ItemStack left = stack;
        for (int i = 0; i < ElectronicsPanels.SLOT_OUT && !left.isEmpty(); i++) left = inv.insertItem(i, left, false);
        player.setItemInHand(hand, left);
        return ItemInteractionResult.SUCCESS;
    }

    /** Shift + ПКМ: открыть или закрыть дверь шлюза. */
    @Override
    public InteractionResult use(Player player) {
        setFlag(BlockStateProperties.OPEN, !doorOpen());
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return ElectronicsPanels.FAB;
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
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] o) {
        Arrays.fill(o, 0);
        WaferFab f = fab();
        o[ElectronicsPanels.F_ISO10] = (int) Math.round(Math.min(9, Cleanroom.isoClass(concentration)) * 10);
        o[ElectronicsPanels.F_PROGRESS] = (int) Math.round(1000 * processH / f.processHours());
        o[ElectronicsPanels.F_DESIGN] = design;
        o[ElectronicsPanels.F_LITHO] = litho;
        o[ElectronicsPanels.F_GAP] = gap;
        o[ElectronicsPanels.F_AMBIENT] = ambient;
        o[ElectronicsPanels.F_OX_T] = oxT;
        o[ElectronicsPanels.F_OX_MIN] = oxMin;
        o[ElectronicsPanels.F_DIFF_T] = diffT;
        o[ElectronicsPanels.F_DIFF_MIN] = diffMin;
        o[ElectronicsPanels.F_CD100] = (int) Math.round(f.cdUm() * 100);
        o[ElectronicsPanels.F_OXIDE_NM] = (int) Math.round(PlanarProcess.oxideUm(f.ambient(), oxT, oxMin / 60.0, true) * 1000);
        o[ElectronicsPanels.F_XJ100] = (int) Math.round(PlanarProcess.junctionDepthUm(PlanarProcess.Dopant.PHOSPHORUS, diffT, diffMin / 60.0,
                SUBSTRATE_CM3) * 100);
        o[ElectronicsPanels.F_YIELD10] = last == null ? 0 : (int) Math.round(last.yield() * 1000);
        o[ElectronicsPanels.F_GOOD] = last == null ? 0 : last.good();
        o[ElectronicsPanels.F_REJECT] = ElectronicsPanels.rejectCode(last);
        o[ElectronicsPanels.F_HEPA] = (int) Math.round(hepa * HEPA_M3H);
        o[ElectronicsPanels.F_LEAK] = (int) Math.round(leakNow() * 10);
        o[ElectronicsPanels.F_VOLUME] = room.size();
        ConsumerUnit u = unit();
        int fl = 0;
        if (u != null) {
            o[ElectronicsPanels.F_POWER_KW] = (int) Math.round(u.powerW() / 1e3);
            if (u.energized()) fl |= 1 << ElectronicsPanels.FF_ENERGIZED;
            if (u.switchedOn()) fl |= 1 << ElectronicsPanels.FF_SWITCH;
        }
        if (enclosed) fl |= 1 << ElectronicsPanels.FF_ENCLOSED;
        if (doorOpen()) fl |= 1 << ElectronicsPanels.FF_DOOR;
        if (running) fl |= 1 << ElectronicsPanels.FF_RUNNING;
        if (!inv.getStackInSlot(ElectronicsPanels.SLOT_LENS).isEmpty()) fl |= 1 << ElectronicsPanels.FF_LENS;
        o[ElectronicsPanels.F_FLAGS] = fl;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ElectronicsPanels.B_DESIGN -> design = (design + 1) % ChipDesign.values().length;
            case ElectronicsPanels.B_LITHO -> litho = (litho + 1) % WaferFab.Litho.values().length;
            case ElectronicsPanels.B_GAP_DOWN -> gap = Math.max(0, gap - 10);
            case ElectronicsPanels.B_GAP_UP -> gap = Math.min(100, gap + 10);
            case ElectronicsPanels.B_AMBIENT -> ambient = 1 - ambient;
            case ElectronicsPanels.B_OX_T_DOWN -> oxT = Math.max(800, oxT - 25);
            case ElectronicsPanels.B_OX_T_UP -> oxT = Math.min(1200, oxT + 25);
            case ElectronicsPanels.B_OX_MIN_DOWN -> oxMin = Math.max(5, oxMin - 5);
            case ElectronicsPanels.B_OX_MIN_UP -> oxMin = Math.min(600, oxMin + 5);
            case ElectronicsPanels.B_DIFF_T_DOWN -> diffT = Math.max(800, diffT - 25);
            case ElectronicsPanels.B_DIFF_T_UP -> diffT = Math.min(1150, diffT + 25);
            case ElectronicsPanels.B_DIFF_MIN_DOWN -> diffMin = Math.max(5, diffMin - 5);
            case ElectronicsPanels.B_DIFF_MIN_UP -> diffMin = Math.min(600, diffMin + 5);
            case ElectronicsPanels.B_SWITCH -> {
                ConsumerUnit u = unit();
                if (u != null) u.setSwitchedOn(!u.switchedOn());
            }
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inv", inv.serializeNBT(registries));
        tag.putIntArray("recipe", new int[] {design, litho, gap, ambient, oxT, oxMin, diffT, diffMin});
        tag.putDouble("conc", concentration);
        tag.putDouble("process_h", processH);
        tag.putInt("wafers", waferCount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inv")) inv.deserializeNBT(registries, tag.getCompound("inv"));
        int[] r = tag.getIntArray("recipe");
        if (r.length == 8) {
            design = Math.floorMod(r[0], ChipDesign.values().length);
            litho = Math.floorMod(r[1], WaferFab.Litho.values().length);
            gap = r[2];
            ambient = Math.floorMod(r[3], 2);
            oxT = r[4];
            oxMin = r[5];
            diffT = r[6];
            diffMin = r[7];
        }
        concentration = tag.contains("conc") ? tag.getDouble("conc") : Cleanroom.OUTSIDE_PER_M3;
        processH = tag.getDouble("process_h");
        waferCount = tag.getInt("wafers");
    }
}
