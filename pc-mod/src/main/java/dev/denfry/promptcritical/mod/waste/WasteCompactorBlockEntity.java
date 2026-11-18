package dev.denfry.promptcritical.mod.waste;

import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.nuclear.FuelLot;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.waste.ScalingFactors;
import dev.denfry.promptcritical.sim.waste.Supercompactor;
import dev.denfry.promptcritical.sim.waste.WasteForm;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Суперпресс твёрдых РАО ({@link Supercompactor}): загрузка — отработанные фильтрующие патроны и любые загрязнённые
 * предметы с активностью (кроме топлива, стеклоблоков, смол и готовых упаковок), плюс пустые бочки. Отходы набирают в
 * бочку 200 л, бочку прессуют усилием 2000 т в таблетку, таблетки укладывают в бочку-контейнер; полный контейнер
 * (или по кнопке «Закрыть») уходит в склад продукта упаковкой {@code compacted_waste_drum}. На таблетку уходит одна бочка,
 * на контейнер — ещё одна.
 *
 * <p>Объём загрузки: фильтрующий патрон — {@value #FILTER_L} л (материал «фильтры»), прочий предмет — {@value #ITEM_L} л
 * (бумага, пластик, спецодежда — «DAW»). Коэффициент сокращения — средний по объёму.
 */
public final class WasteCompactorBlockEntity extends UraniumProcessBlockEntity {
    public static final int IN_SLOTS = 8, OUT_SLOTS = 4;
    public static final double DRUM_L = 200, FILTER_L = 20, ITEM_L = 10, STROKE_S = 600, OVERPACK_FILL = 0.9, DRUM_STEEL_KG = 20;

    private double rawL, rawFilterL, rawKg;
    private ActivityInventory raw = new ActivityInventory(Map.of(), 0, 1);
    private double pucksL, pucksKg;
    private int pucks;
    private ActivityInventory overpack = new ActivityInventory(Map.of(), 0, 1);
    private double busyS, lastVrf;
    private boolean closeRequested;

    public WasteCompactorBlockEntity(BlockPos pos, BlockState state) {
        super(WasteBlockEntities.WASTE_COMPACTOR.get(), pos, state, IN_SLOTS, OUT_SLOTS, WasteCompactorBlockEntity::loadable);
    }

    static boolean emptyDrum(ItemStack s) {
        return s.is(PcItems.WASTE_DRUM.get()) && !s.has(ActivityData.ACTIVITY.get());
    }

    /** Прессуемые отходы: всё с активностью, кроме топлива, стекла, смолы и готовых упаковок. */
    static boolean compactable(ItemStack s) {
        return s.has(ActivityData.ACTIVITY.get()) && !s.has(FuelLot.TYPE.get()) && !WastePackage.isPackage(s)
                && !s.is(PcItems.SPENT_ION_EXCHANGE_RESIN.get()) && !s.is(PcItems.HLW_GLASS_CANISTER.get());
    }

    static boolean loadable(ItemStack s) {
        return emptyDrum(s) || compactable(s);
    }

    public int pucks() {
        return pucks;
    }

    public double pucksL() {
        return pucksL;
    }

    public double lastVolumeReduction() {
        return lastVrf;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        raw = raw.decayed(dt);
        overpack = overpack.decayed(dt);
        if (!on) return;
        busyS -= dt;
        if (busyS > 0) return;
        long now = level.getGameTime();
        // набрать бочку
        while (rawL < DRUM_L) {
            ItemStack s = takeOne(WasteCompactorBlockEntity::compactable);
            if (s.isEmpty()) break;
            ActivityData a = s.get(ActivityData.ACTIVITY.get());
            ActivityInventory inv = a.inventory(now);
            double l = s.is(PcItems.FILTER_CARTRIDGE.get()) ? FILTER_L : ITEM_L;
            rawL += l;
            if (s.is(PcItems.FILTER_CARTRIDGE.get())) rawFilterL += l;
            rawKg += inv.massKg();
            raw = add(raw, inv, 1);
        }
        double p = Supercompactor.pressureMPa(Supercompactor.FORCE_T);
        boolean full = rawL >= DRUM_L;
        // таблетка: полная бочка или остаток перед закрытием контейнера
        if ((full || closeRequested && rawL > 0) && count(WasteCompactorBlockEntity::emptyDrum) >= 1) {
            double f = rawFilterL / rawL;
            double vrf = f * Supercompactor.volumeReduction(Supercompactor.Material.FILTERS, p)
                    + (1 - f) * Supercompactor.volumeReduction(Supercompactor.Material.DAW, p);
            double puck = Math.min(rawL, DRUM_L) / vrf;
            if (pucksL + puck > DRUM_L * OVERPACK_FILL && pucks > 0) {
                closeRequested = true;
            } else {
                takeOne(WasteCompactorBlockEntity::emptyDrum);
                pucksL += puck;
                pucksKg += rawKg + DRUM_STEEL_KG;
                overpack = add(overpack, raw, 1);
                pucks++;
                lastVrf = vrf;
                rawL = rawFilterL = rawKg = 0;
                raw = new ActivityInventory(Map.of(), 0, 1);
                busyS = STROKE_S;
                level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5f, 0.6f);
                return;
            }
        }
        if (closeRequested && pucks > 0 && count(WasteCompactorBlockEntity::emptyDrum) >= 1 && hasRoom(PcItems.COMPACTED_WASTE_DRUM.get())) {
            takeOne(WasteCompactorBlockEntity::emptyDrum);
            double mass = pucksKg + DRUM_STEEL_KG;
            ActivityInventory g = new ActivityInventory(overpack.activities(), mass, mass / DRUM_L);
            emit(WastePackage.make(PcItems.COMPACTED_WASTE_DRUM.get(), g, now, WasteForm.COMPACTED, ScalingFactors.Stream.REACTOR_OPERATIONAL,
                    true, DRUM_L));
            pucks = 0;
            pucksL = pucksKg = 0;
            overpack = new ActivityInventory(Map.of(), 0, 1);
            closeRequested = false;
            busyS = STROKE_S / 2;
        }
    }

    private static ActivityInventory add(ActivityInventory a, ActivityInventory b, double k) {
        EnumMap<Emitter, Double> m = new EnumMap<>(a.activities());
        b.activities().forEach((n, v) -> m.merge(n, v * k, Double::sum));
        return new ActivityInventory(m, a.massKg() + b.massKg() * k, 1);
    }

    /** Закрыть контейнер с тем, что уже спрессовано. */
    public void close() {
        if (pucks > 0 || rawL > 0) closeRequested = true;
        setChanged();
    }

    @Override
    protected boolean running() {
        return on && busyS > 0;
    }

    @Override
    public PanelLayout panelLayout() {
        return WastePanels.COMPACTOR;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[WastePanels.C_RAW_L] = (int) Math.round(rawL);
        v[WastePanels.C_PUCKS] = pucks;
        v[WastePanels.C_PUCKS_L] = (int) Math.round(pucksL);
        v[WastePanels.C_VRF] = (int) Math.round(lastVrf * 10);
        v[WastePanels.C_PRESSURE] = (int) Math.round(Supercompactor.pressureMPa(Supercompactor.FORCE_T));
        v[WastePanels.C_FLAGS] = (on ? 1 << WastePanels.CF_ON : 0) | (running() ? 1 << WastePanels.CF_RUN : 0)
                | (count(WasteCompactorBlockEntity::emptyDrum) == 0 ? 1 << WastePanels.CF_NO_DRUM : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == WastePanels.B_RUN) use(player);
        else if (id == WastePanels.B_CLOSE) close();
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("raw_l", rawL);
        tag.putDouble("raw_filter_l", rawFilterL);
        tag.putDouble("raw_kg", rawKg);
        tag.put("raw", WasteNbt.activities(raw));
        tag.putDouble("pucks_l", pucksL);
        tag.putDouble("pucks_kg", pucksKg);
        tag.putInt("pucks", pucks);
        tag.put("overpack", WasteNbt.activities(overpack));
        tag.putDouble("busy", busyS);
        tag.putDouble("vrf", lastVrf);
        tag.putBoolean("close", closeRequested);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        rawL = tag.getDouble("raw_l");
        rawFilterL = tag.getDouble("raw_filter_l");
        rawKg = tag.getDouble("raw_kg");
        raw = WasteNbt.activities(tag.getCompound("raw"));
        pucksL = tag.getDouble("pucks_l");
        pucksKg = tag.getDouble("pucks_kg");
        pucks = tag.getInt("pucks");
        overpack = WasteNbt.activities(tag.getCompound("overpack"));
        busyS = tag.getDouble("busy");
        lastVrf = tag.getDouble("vrf");
        closeRequested = tag.getBoolean("close");
    }
}
