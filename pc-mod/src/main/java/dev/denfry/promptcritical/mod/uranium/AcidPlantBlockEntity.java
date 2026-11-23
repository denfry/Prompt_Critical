package dev.denfry.promptcritical.mod.uranium;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.uranium.ContactAcidPlant;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Сернокислотная установка ({@link ContactAcidPlant}) на колчедане: печь КС, очистка газа, контактный аппарат с
 * ванадиевой массой, абсорбер. Пирит — кусками руды воронкой или рукой (сера — по пробе), вода — трубой к боковым
 * граням; 98 %-я кислота — снизу (1 мБ = 1 л).
 */
public final class AcidPlantBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 8_000;

    private final FeedTank water = new FeedTank(Fluids.WATER, MB);
    private final ProductTank acid = new ProductTank(PcFluids.SULFURIC_ACID.source.get(), MB);
    private final ContactAcidPlant plant = new ContactAcidPlant();
    private double sulfurKg;

    public AcidPlantBlockEntity(BlockPos pos, BlockState state) {
        super(UraniumBlockEntities.ACID_PLANT.get(), pos, state, 4, 0, AcidPlantBlockEntity::pyrite);
    }

    static boolean pyrite(ItemStack s) {
        return s.is(PcBlocks.PYRITE_ORE.get().asItem()) || s.is(PcBlocks.DEEPSLATE_PYRITE_ORE.get().asItem())
                || s.is(dev.denfry.promptcritical.mod.registry.PcItems.SULFUR.get());
    }

    public ContactAcidPlant plant() {
        return plant;
    }

    public ProductTank acid() {
        return acid;
    }

    /** Буфер серы печи КС: сколько секунд паспортной производительности он держит. */
    public static final double SULFUR_BUFFER_S = 600;

    /**
     * Сернистый газ плавки и конвертирования меди (08 §8.8.3): сера SO₂ идёт в контактный аппарат вместо колчедана.
     * Возвращает принятое, кг S; выключенная установка и полный буфер не берут ничего.
     */
    public double acceptSulfurKg(double kg) {
        if (!on || kg <= 0) return 0;
        double take = Math.min(kg, Math.max(0, ContactAcidPlant.CAPACITY_S_KG_S * SULFUR_BUFFER_S - sulfurKg));
        sulfurKg += take;
        if (take > 0) setChanged();
        return take;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        double want = ContactAcidPlant.CAPACITY_S_KG_S * dt;
        while (on && sulfurKg < want) {
            ItemStack ore = takeOne(AcidPlantBlockEntity::pyrite);
            if (ore.isEmpty()) break;
            sulfurKg += ore.is(dev.denfry.promptcritical.mod.registry.PcItems.SULFUR.get())
                    ? dev.denfry.promptcritical.mod.refinery.ClausUnitBlockEntity.SULFUR_KG_PER_ITEM
                    : UraniumUnits.MINERAL_KG * UraniumUnits.assay(ore, UraniumUnits.PYRITE_S_DEFAULT);
        }
        plant.setOn(on);
        plant.step(Math.min(want, sulfurKg) / dt);
        // вода и место под кислоту ограничивают подачу колчедана
        double f = 1;
        if (plant.waterKgS() > 0) f = Math.min(f, water.amount() / (plant.waterKgS() * dt));
        if (plant.acidKgS() > 0) f = Math.min(f, acid.room() * UraniumUnits.ACID_KG_L / (plant.acidKgS() * dt));
        if (f < 1) plant.step(plant.sulfurKgS() * Math.max(0, f));
        sulfurKg -= plant.sulfurKgS() * dt;
        water.take(plant.waterKgS() * dt);
        acid.add(plant.acidKgS() * dt / UraniumUnits.ACID_KG_L);
    }

    /** Воздуходувка контактного аппарата, Вт. */
    @Override
    protected double ratedPowerW() {
        return 55e3;
    }

    @Override
    protected boolean running() {
        return plant.acidKgS() > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return side == Direction.DOWN ? acid : water;
    }

    @Override
    public PanelLayout panelLayout() {
        return UraniumPanels.ACID;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[UraniumPanels.T_C] = (int) Math.round(plant.lastBedK() - 273.15);
        v[UraniumPanels.A] = (int) Math.round(ContactAcidPlant.conversion(plant.lastBedK()) * 1000);
        v[UraniumPanels.B] = (int) Math.round(plant.acidKgS() * 3600);
        v[UraniumPanels.C] = (int) Math.round(plant.so2StackKgS() * 3600 * 10);
        v[UraniumPanels.TANK] = (int) Math.round(acid.fill() * 1000);
        v[UraniumPanels.FLAGS] = (on ? 1 << UraniumPanels.F_ON : 0) | (running() ? 1 << UraniumPanels.F_RUN : 0)
                | (ContactAcidPlant.activity(plant.lastBedK()) < 1 ? 1 << UraniumPanels.F_WARN : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == UraniumPanels.B_RUN) use(player);
        else if (id == UraniumPanels.B_T_DOWN) plant.setLastBedK(plant.lastBedK() - 5);
        else if (id == UraniumPanels.B_T_UP) plant.setLastBedK(plant.lastBedK() + 5);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        water.save(tag, "water");
        acid.save(tag, "acid");
        tag.putDouble("s", sulfurKg);
        MutableStateTree t = new MutableStateTree();
        plant.save(new TreeStateWriter(t));
        tag.put("plant", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        water.load(tag, "water");
        acid.load(tag, "acid");
        sulfurKg = tag.getDouble("s");
        if (tag.contains("plant")) plant.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("plant"))));
    }
}
