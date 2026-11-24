package dev.denfry.promptcritical.mod.chlor;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.environment.ToxicReleaseService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.chlor.HclSynthesis;
import dev.denfry.promptcritical.sim.consequence.ToxicGas;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Печь синтеза хлороводорода ({@link HclSynthesis}, 10 §10.3.4): графитовая горелка внизу колонны, над ней —
 * абсорбер. Водород, хлор и вода на абсорбцию — трубами к боковым граням. Снизу забирают продукт текущего режима:
 * соляную кислоту (л) или сухой хлороводород (нм³) — его ведут на оксихлорирование и трихлорсилан. Пустая рука:
 * горелка гаснет → кислота → сухой газ → гаснет. С Shift — избыток водорода 7 → 10 → 15 → 0 → 3 → 5 %. Хлор, прошедший
 * горелку при избытке водорода меньше 5 %, уходит в воздух с хвостовым газом.
 */
public final class HclFurnaceBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    /** Насосы охлаждающей воды и абсорбера, вентилятор, Вт. Розетка или силовой щит рядом; при недостатке установка работает медленнее. */
    public static final double POWER_W = 15e3;
    public static final int GAS_MB = 4_000, LIQ_MB = 8_000;
    /** Ступени избытка водорода, которые перебирает Shift + ПКМ. */
    private static final double[] EXCESS_STEPS = {1.07, 1.10, 1.15, 1.00, 1.03, 1.05};
    /** Соляная кислота 31–36 %, кг/л (по номинальной крепости 33 %). */
    public static final double ACID_KG_L = 1.16;

    private final FeedTank hydrogen = new FeedTank(PcFluids.HYDROGEN.source.get(), GAS_MB);
    private final FeedTank chlorine = new FeedTank(PcFluids.CHLORINE.source.get(), GAS_MB);
    private final FeedTank water = new FeedTank(Fluids.WATER, LIQ_MB);
    private final IFluidHandler inlet = new MultiInlet(hydrogen, chlorine, water);
    private final ProductTank acid = new ProductTank(PcFluids.HYDROCHLORIC_ACID.source.get(), LIQ_MB);
    private final ProductTank dryHcl = new ProductTank(PcFluids.HYDROGEN_CHLORIDE.source.get(), GAS_MB);
    private final HclSynthesis unit = new HclSynthesis();

    public HclFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ChlorBlockEntities.HCL_FURNACE.get(), pos, state);
        unit.setOn(false);
    }

    public HclSynthesis unit() {
        return unit;
    }

    public ProductTank acid() {
        return acid;
    }

    public ProductTank dryHcl() {
        return dryHcl;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        double dt = 0.05 * b.world().clock().kSlow();
        boolean acidMode = unit.mode() == HclSynthesis.Mode.ACID;
        double roomMol = acidMode
                ? acid.room() * ACID_KG_L * unit.strength() / HclSynthesis.M_HCL / dt
                : dryHcl.room() / ChlorUnits.NM3_PER_MOL / dt;
        double power = dev.denfry.promptcritical.mod.grid.MachinePower.supply(s, worldPosition, hydrogen.amount() > 0 && chlorine.amount() > 0 ? POWER_W : 0);
        unit.step(hydrogen.amount() / ChlorUnits.NM3_PER_MOL / dt * power, chlorine.amount() / ChlorUnits.NM3_PER_MOL / dt * power,
                water.amount() / dt, roomMol);
        hydrogen.take(ChlorUnits.molToNm3(unit.h2UsedMolS() * dt));
        chlorine.take(ChlorUnits.molToNm3(unit.cl2UsedMolS() * dt));
        water.take(unit.waterUsedKgS() * dt);
        if (acidMode) acid.add(unit.acidKgS() * dt / ACID_KG_L);
        else dryHcl.add(ChlorUnits.molToNm3(unit.hclMolS() * dt));
        double slipKgS = unit.cl2SlipMolS() * HclSynthesis.M_CL2;
        if (slipKgS > 1e-6) ToxicReleaseService.vent(s, worldPosition.above(3), ToxicGas.CHLORINE, slipKgS);
        if (level.getGameTime() % 10 == 0) {
            BlockState st = getBlockState();
            boolean lit = unit.cl2UsedMolS() > 0, alarm = lit && unit.cl2SlipMolS() > 0;
            if (st.getValue(BlockStateProperties.LIT) != lit || st.getValue(PcProperties.ALARM) != alarm)
                level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, lit).setValue(PcProperties.ALARM, alarm), Block.UPDATE_CLIENTS);
        }
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side != Direction.DOWN) return inlet;
        return unit.mode() == HclSynthesis.Mode.ACID ? acid : dryHcl;
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
        if (player.isShiftKeyDown()) {
            int i = 0;
            while (i < EXCESS_STEPS.length && Math.abs(EXCESS_STEPS[i] - unit.excess()) > 1e-6) i++;
            unit.setExcess(EXCESS_STEPS[(i + 1) % EXCESS_STEPS.length]);
            String pct = Long.toString(Math.round((unit.excess() - 1) * 100));
            player.displayClientMessage(ChlorText.msg(unit.excess() < HclSynthesis.SAFE_EXCESS ? "hcl.slip" : "hcl.excess", pct), true);
        } else {
            cycleMode();
            if (!unit.on()) player.displayClientMessage(ChlorText.msg("hcl.off"), true);
            else if (unit.mode() == HclSynthesis.Mode.ACID)
                player.displayClientMessage(ChlorText.msg("hcl.acid", Long.toString(Math.round(unit.strength() * 100))), true);
            else player.displayClientMessage(ChlorText.msg("hcl.dry"), true);
        }
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        setChanged();
        return InteractionResult.SUCCESS;
    }

    /** Погашена → кислота → сухой газ → погашена. */
    public void cycleMode() {
        if (!unit.on()) {
            unit.setOn(true);
            unit.setMode(HclSynthesis.Mode.ACID);
        } else if (unit.mode() == HclSynthesis.Mode.ACID) {
            unit.setMode(HclSynthesis.Mode.DRY);
        } else {
            unit.setOn(false);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        hydrogen.save(tag, "h2");
        chlorine.save(tag, "cl2");
        water.save(tag, "water");
        acid.save(tag, "acid");
        dryHcl.save(tag, "hcl");
        MutableStateTree tree = new MutableStateTree();
        unit.save(new TreeStateWriter(tree));
        tag.put("unit", NbtStateTrees.toNbt(tree));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hydrogen.load(tag, "h2");
        chlorine.load(tag, "cl2");
        water.load(tag, "water");
        acid.load(tag, "acid");
        dryHcl.load(tag, "hcl");
        if (tag.contains("unit")) unit.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("unit"))));
    }
}
