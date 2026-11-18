package dev.denfry.promptcritical.mod.chlor;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.chlor.EdcUnit;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Установка получения дихлорэтана ({@link EdcUnit}) 1×2×1: реактор прямого хлорирования и оксихлоратор с кипящим
 * слоем. Этилен, хлор (газ, нм³, или жидкий, л), хлористый водород с пиролиза ЭДХ и кислород — трубами к боковым
 * граням; ЭДХ — снизу (л). Свободный хлор при прямом хлорировании расходуется полностью, избыток хлора стоит в баке.
 */
public final class EdcUnitBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int GAS_MB = 4_000, LIQ_MB = 8_000;

    private final FeedTank ethylene = new FeedTank(PcFluids.ETHYLENE.source.get(), GAS_MB);
    private final FeedTank chlorine = new FeedTank(PcFluids.CHLORINE.source.get(), GAS_MB);
    private final FeedTank liquidChlorine = new FeedTank(PcFluids.LIQUID_CHLORINE.source.get(), LIQ_MB / 4);
    private final FeedTank hcl = new FeedTank(PcFluids.HYDROGEN_CHLORIDE.source.get(), GAS_MB);
    private final FeedTank oxygen = new FeedTank(PcFluids.OXYGEN.source.get(), GAS_MB);
    private final IFluidHandler inlet = new MultiInlet(ethylene, chlorine, liquidChlorine, hcl, oxygen);
    private final ProductTank edc = new ProductTank(PcFluids.ETHYLENE_DICHLORIDE.source.get(), LIQ_MB);
    private final EdcUnit unit = new EdcUnit();

    public EdcUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ChlorBlockEntities.EDC_UNIT.get(), pos, state);
    }

    public EdcUnit unit() {
        return unit;
    }

    public ProductTank edc() {
        return edc;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        double dt = 0.05 * b.world().clock().kSlow();
        // доступно на шаг, моль/с; ЭДХ не влезет — реакторы на себя
        double roomMol = edc.room() * ChlorUnits.EDC_KG_L / ChlorUnits.EDC_KG_MOL / dt;
        double cl2Mol = (chlorine.amount() / ChlorUnits.NM3_PER_MOL + liquidChlorine.amount() * ChlorUnits.LIQUID_CL2_KG_L / EdcUnit.M_CL2) / dt;
        unit.step(ethylene.amount() / ChlorUnits.NM3_PER_MOL / dt, Math.min(cl2Mol, roomMol), hcl.amount() / ChlorUnits.NM3_PER_MOL / dt,
                oxygen.amount() / ChlorUnits.NM3_PER_MOL / dt);
        if (unit.edcMolS() > roomMol) unit.step(0, 0, 0, 0); // нет места — стоим целиком
        ethylene.take(ChlorUnits.molToNm3(unit.c2h4UsedMolS() * dt));
        hcl.take(ChlorUnits.molToNm3(unit.hclUsedMolS() * dt));
        oxygen.take(ChlorUnits.molToNm3(unit.o2UsedMolS() * dt));
        double cl2 = unit.cl2UsedMolS() * dt, fromGas = Math.min(cl2, chlorine.amount() / ChlorUnits.NM3_PER_MOL);
        chlorine.take(ChlorUnits.molToNm3(fromGas));
        liquidChlorine.take((cl2 - fromGas) * EdcUnit.M_CL2 / ChlorUnits.LIQUID_CL2_KG_L);
        edc.add(unit.edcMolS() * dt * ChlorUnits.EDC_KG_MOL / ChlorUnits.EDC_KG_L);
        if (level.getGameTime() % 10 == 0) {
            BlockState st = getBlockState();
            boolean run = unit.edcMolS() > 0;
            if (st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING) != run)
                level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, run), Block.UPDATE_CLIENTS);
        }
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return side == Direction.DOWN ? edc : inlet;
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
        unit.setOn(!unit.on());
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        player.displayClientMessage(ChlorText.msg(unit.on() ? "edc.on" : "edc.off"), true);
        setChanged();
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return ChlorPanels.EDC;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[ChlorPanels.E_OXY_C] = (int) Math.round(unit.oxyK() - 273.15);
        out[ChlorPanels.E_HCL_PERMIL] = (int) Math.round(EdcUnit.hclConversion(unit.oxyK()) * 1000);
        out[ChlorPanels.E_SEL_PERMIL] = (int) Math.round(EdcUnit.oxySelectivity(unit.oxyK()) * 1000);
        out[ChlorPanels.E_DIRECT_KGH] = (int) Math.round(unit.edcDirectMolS() * ChlorUnits.EDC_KG_MOL * 3600);
        out[ChlorPanels.E_OXY_KGH] = (int) Math.round(unit.edcOxyMolS() * ChlorUnits.EDC_KG_MOL * 3600);
        out[ChlorPanels.E_TANK] = (int) Math.round(edc.fill() * 1000);
        out[ChlorPanels.E_FLAGS] = (unit.on() ? 1 << ChlorPanels.EF_ON : 0) | (unit.edcOxyMolS() > 0 ? 1 << ChlorPanels.EF_OXY : 0)
                | (unit.edcDirectMolS() > 0 ? 1 << ChlorPanels.EF_DIRECT : 0) | (unit.oxyK() > 533.15 ? 1 << ChlorPanels.EF_HOT : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ChlorPanels.EB_RUN -> use(player);
            case ChlorPanels.EB_T_DOWN -> unit.setOxyK(unit.oxyK() - 5);
            case ChlorPanels.EB_T_UP -> unit.setOxyK(unit.oxyK() + 5);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ethylene.save(tag, "c2h4");
        chlorine.save(tag, "cl2");
        liquidChlorine.save(tag, "cl2l");
        hcl.save(tag, "hcl");
        oxygen.save(tag, "o2");
        edc.save(tag, "edc");
        MutableStateTree tree = new MutableStateTree();
        unit.save(new TreeStateWriter(tree));
        tag.put("unit", NbtStateTrees.toNbt(tree));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ethylene.load(tag, "c2h4");
        chlorine.load(tag, "cl2");
        liquidChlorine.load(tag, "cl2l");
        hcl.load(tag, "hcl");
        oxygen.load(tag, "o2");
        edc.load(tag, "edc");
        if (tag.contains("unit")) unit.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("unit"))));
    }
}
