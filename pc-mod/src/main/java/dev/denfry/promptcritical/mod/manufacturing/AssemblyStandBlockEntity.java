package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.sim.manufacturing.PartSpec;
import dev.denfry.promptcritical.sim.manufacturing.Stator;
import dev.denfry.promptcritical.sim.manufacturing.assembly.AssemblyGate;
import dev.denfry.promptcritical.sim.manufacturing.assembly.AssemblyScorer;
import dev.denfry.promptcritical.sim.manufacturing.assembly.MachinePassport;
import dev.denfry.promptcritical.sim.manufacturing.assembly.MachineSpec;
import dev.denfry.promptcritical.sim.manufacturing.assembly.PartRequirement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Сборочный стенд (18 §18.8.2): игрок выбирает спецификацию машины, раскладывает детали по позициям, стенд проверяет
 * ворота ({@link AssemblyGate}), оценивает посадки, балансировку и уплотнения ({@link AssemblyScorer}) и после
 * обкатки (§18.6.11) выдаёт машину с паспортом {@code machine_passport}. Ротор стенд балансирует статически на ножах:
 * ручная ступень, класс G16 (ISO 21940-11).
 */
public final class AssemblyStandBlockEntity extends SmithyDevice {
    public static final int POSITIONS = MachineSpec.MAX_POSITIONS, OUTPUT_SLOT = POSITIONS;
    /** Обкатка на холостом ходу: 20 с реального времени [BALANCE]. */
    public static final int RUN_IN_TICKS = 400;
    /** Номинальный КПД двигателя 4А112М4 для пересчёта КПД статора в множитель паспорта (MF4, ГОСТ 19523). */
    public static final double NOMINAL_MOTOR_EFFICIENCY = MachinePassport.NOMINAL_WINDING_EFFICIENCY;
    public static final AssemblyScorer.Balancing BALANCING = AssemblyScorer.Balancing.STATIC;

    /** Состояние позиции на щите. */
    public enum PositionState { UNUSED, EMPTY, OK, WRONG_KIND, WRONG_MATERIAL, GATE_DEFECT, SHORT }

    private static MachineInventory.Role[] roles() {
        MachineInventory.Role[] r = new MachineInventory.Role[POSITIONS + 1];
        Arrays.fill(r, MachineInventory.Role.INPUT);
        r[OUTPUT_SLOT] = MachineInventory.Role.OUTPUT;
        return r;
    }

    private String specId = "";
    private int runTicks = -1;
    @Nullable private ItemStack pending;

    public AssemblyStandBlockEntity(BlockPos pos, BlockState state) {
        super(SmithyBlockEntities.ASSEMBLY_STAND.get(), pos, state, roles(), (slot, s) -> slot < POSITIONS);
    }

    @Nullable
    public MachineSpec spec() {
        return MachineSpecManager.data().byId().get(specId);
    }

    /** Спецификации в порядке перебора кнопками: по id. */
    private static List<String> specIds() {
        List<String> ids = new ArrayList<>(MachineSpecManager.data().byId().keySet());
        ids.sort(null);
        return ids;
    }

    public void selectSpec(String id) {
        specId = id;
        setChanged();
    }

    public boolean running() {
        return runTicks >= 0;
    }

    /** Предмет позиции: id в реестре для сверки с {@link PartRequirement#kinds()}. */
    static String kindOf(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    public PositionState state(int i) {
        MachineSpec spec = spec();
        if (spec == null || i >= spec.requirements().size()) return PositionState.UNUSED;
        PartRequirement req = spec.requirements().get(i);
        ItemStack s = inv.getStackInSlot(i);
        if (s.isEmpty()) return PositionState.EMPTY;
        return switch (AssemblyGate.check(req, kindOf(s), s.get(ManufacturingComponents.PART_SPEC.get()))) {
            case WRONG_KIND -> PositionState.WRONG_KIND;
            case WRONG_MATERIAL -> PositionState.WRONG_MATERIAL;
            case GATE_DEFECT -> PositionState.GATE_DEFECT;
            case OK -> s.getCount() < req.count() ? PositionState.SHORT : PositionState.OK;
        };
    }

    /** Паспорт, который даст сборка сейчас; null — не все позиции в порядке. */
    @Nullable
    public MachinePassport preview() {
        MachineSpec spec = spec();
        if (spec == null) return null;
        List<AssemblyScorer.Placed> placed = new ArrayList<>();
        AssemblyScorer.Winding winding = null;
        for (int i = 0; i < spec.requirements().size(); i++) {
            if (state(i) != PositionState.OK) return null;
            PartRequirement req = spec.requirements().get(i);
            ItemStack s = inv.getStackInSlot(i);
            placed.add(new AssemblyScorer.Placed(req.role(), s.get(ManufacturingComponents.PART_SPEC.get())));
            if (req.has(PartRequirement.Aspect.WINDING)) {
                ElectricalData.Wound w = s.get(ManufacturingComponents.STATOR.get());
                if (w != null) {
                    Stator st = w.stator();
                    winding = new AssemblyScorer.Winding(st.insulationStartMOhm(), st.efficiency(), NOMINAL_MOTOR_EFFICIENCY);
                }
            }
        }
        return AssemblyScorer.score(spec, placed, BALANCING, winding);
    }

    /** Собрать: детали уходят со стенда, начинается обкатка; машина появится в выходном слоте. */
    public boolean assemble(@Nullable Player player) {
        MachineSpec spec = spec();
        if (running() || spec == null) return false;
        if (!inv.getStackInSlot(OUTPUT_SLOT).isEmpty()) {
            tell(player, "assembly.output_busy");
            return false;
        }
        MachinePassport p = preview();
        Item out = BuiltInRegistries.ITEM.get(ResourceLocation.parse(spec.output()));
        if (p == null || out == null || out == net.minecraft.world.item.Items.AIR) {
            tell(player, "assembly.not_ready");
            return false;
        }
        for (int i = 0; i < spec.requirements().size(); i++) inv.extractItem(i, spec.requirements().get(i).count(), false);
        ItemStack result = new ItemStack(out);
        result.set(ManufacturingComponents.MACHINE_PASSPORT.get(), p);
        pending = result;
        runTicks = spec.runIn() ? 0 : RUN_IN_TICKS;
        if (level != null) level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 0.8f);
        setChanged();
        return true;
    }

    /** Слом стенда во время обкатки: собранная машина выпадает, а не пропадает ({@code PanelHost.dropContents}). */
    @Override
    public ItemStack takeHeldItem() {
        ItemStack p = pending == null ? ItemStack.EMPTY : pending;
        pending = null;
        runTicks = -1;
        return p;
    }

    private static void tell(@Nullable Player player, String key) {
        if (player != null) player.displayClientMessage(ManufacturingText.msg(key), true);
    }

    @Override
    public void tickDevice() {
        if (level == null || level.isClientSide || !running()) return;
        if (++runTicks < RUN_IN_TICKS) {
            if (runTicks % 40 == 0) level.playSound(null, worldPosition, SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, 0.25f, 1.6f);
            if (runTicks % 20 == 0) setChanged();
            return;
        }
        if (pending != null && inv.getStackInSlot(OUTPUT_SLOT).isEmpty()) {
            inv.setStackInSlot(OUTPUT_SLOT, pending);
            pending = null;
            level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.4f, 1.4f);
        }
        runTicks = -1;
        setChanged();
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case AssemblyPanels.BUTTON_PREV, AssemblyPanels.BUTTON_NEXT -> {
                if (running()) return;
                List<String> ids = specIds();
                if (ids.isEmpty()) return;
                int at = ids.indexOf(specId);
                int step = id == AssemblyPanels.BUTTON_NEXT ? 1 : -1;
                at = at < 0 ? 0 : Math.floorMod(at + step, ids.size());
                selectSpec(ids.get(at));
            }
            case AssemblyPanels.BUTTON_ASSEMBLE -> assemble(player);
            default -> { }
        }
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public dev.denfry.promptcritical.mod.panel.PanelLayout panelLayout() {
        return AssemblyPanels.STAND;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        MachineSpec spec = spec();
        List<String> ids = specIds();
        out[AssemblyPanels.C_SPEC_INDEX] = ids.indexOf(specId) + 1;
        out[AssemblyPanels.C_SPEC_COUNT] = ids.size();
        out[AssemblyPanels.C_OUTPUT] = spec == null ? -1 : BuiltInRegistries.ITEM.getId(
                BuiltInRegistries.ITEM.get(ResourceLocation.parse(spec.output())));
        for (int i = 0; i < POSITIONS; i++) {
            PositionState st = state(i);
            out[AssemblyPanels.P_STATE + i] = st.ordinal();
            if (st == PositionState.UNUSED) {
                out[AssemblyPanels.P_ITEM + i] = -1;
                continue;
            }
            PartRequirement req = spec.requirements().get(i);
            out[AssemblyPanels.P_ITEM + i] = BuiltInRegistries.ITEM.getId(
                    BuiltInRegistries.ITEM.get(ResourceLocation.parse(req.kinds().iterator().next())));
            out[AssemblyPanels.P_COUNT + i] = req.count();
        }
        MachinePassport p = running() && pending != null ? pending.get(ManufacturingComponents.MACHINE_PASSPORT.get()) : preview();
        out[AssemblyPanels.C_SCORE] = p == null ? -1 : p.score();
        out[AssemblyPanels.C_IMBALANCE_X10] = p == null ? 0 : (int) Math.round(p.imbalanceMmS() * 10);
        out[AssemblyPanels.C_WEAR_X100] = p == null ? 100 : (int) Math.round(p.bearingWearFactor() * 100);
        out[AssemblyPanels.C_RUN_PCT] = running() ? 100 * runTicks / RUN_IN_TICKS : 0;
        int f = running() ? 1 << AssemblyPanels.F_RUNNING : 0;
        if (p != null) f |= 1 << AssemblyPanels.F_READY;
        if (spec != null && fastenersTight(spec)) f |= 1 << AssemblyPanels.F_TIGHT;
        if (p != null && p.imbalanceMmS() < dev.denfry.promptcritical.sim.ops.MaintenanceFaultKind.IMBALANCE.threshold(1)) {
            f |= 1 << AssemblyPanels.F_BALANCED;
        }
        out[AssemblyPanels.FLAGS] = f;
    }

    /** Крепёж на месте и годен: позиции с {@code fastener_kit} заполнены (затяжка по моменту — дело стенда). */
    private boolean fastenersTight(MachineSpec spec) {
        boolean any = false;
        for (int i = 0; i < spec.requirements().size(); i++) {
            if (!spec.requirements().get(i).kinds().contains(FASTENERS)) continue;
            any = true;
            if (state(i) != PositionState.OK) return false;
        }
        return any;
    }

    private static final String FASTENERS = "promptcritical:fastener_kit";

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("spec", specId);
        tag.putInt("run", runTicks);
        if (pending != null && !pending.isEmpty()) tag.put("pending", pending.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        specId = tag.getString("spec");
        runTicks = tag.contains("run") ? tag.getInt("run") : -1;
        pending = tag.contains("pending") ? ItemStack.parse(registries, tag.getCompound("pending")).orElse(null) : null;
        if (pending == null) runTicks = -1;
    }
}
