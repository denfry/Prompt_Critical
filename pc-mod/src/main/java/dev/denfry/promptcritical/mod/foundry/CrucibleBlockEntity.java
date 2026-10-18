package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.metallurgy.CastAlloy;
import dev.denfry.promptcritical.sim.metallurgy.CrucibleFurnace;
import dev.denfry.promptcritical.sim.metallurgy.CrucibleUnit;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Тигельная печь {@code crucible_furnace} (08 §8.9): слиток меди, бронзы или алюминия (100 кг) в тигель, уголь в
 * горн; расплав выливается в заформованную форму перед печью (на уровне печи или на блок ниже). Сигнал красного
 * камня держит тигель.
 */
public final class CrucibleBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.INPUT};

    private final MachineInventory inv = new MachineInventory(ROLES,
            (slot, s) -> slot == 0 ? alloyOf(s) != null : s.is(FoundryTags.FURNACE_COAL), this::setChanged);

    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(FoundryBlockEntities.CRUCIBLE.get(), pos, state);
    }

    @Nullable
    static CastAlloy alloyOf(ItemStack s) {
        if (s.is(FoundryTags.COPPER_INGOTS)) return CastAlloy.COPPER;
        if (s.is(FoundryTags.BRONZE_INGOTS)) return CastAlloy.BRONZE;
        if (s.is(FoundryTags.ALUMINUM_INGOTS)) return CastAlloy.ALUMINIUM;
        return null;
    }

    @Nullable
    private CrucibleUnit unit() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null || simId().isNone()) return null;
        return b.world().subsystem(FoundrySubsystem.class).unit(b.world().topology(), simId()) instanceof CrucibleUnit u ? u : null;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) {
            clientTick();
            return;
        }
        SimLevelBinding b = SimBindings.get(s);
        CrucibleUnit u = unit();
        if (b == null || u == null) return;
        ItemStack metal = inv.getStackInSlot(0);
        CastAlloy a = alloyOf(metal);
        if (a != null && u.charge(a, ServerConfig.METALLURGY_INGOT_MASS_KG.get())) metal.shrink(1);
        ItemStack fuel = inv.getStackInSlot(1);
        if (!fuel.isEmpty() && u.addFuel(ServerConfig.METALLURGY_BULK_ITEM_MASS_KG.get())) fuel.shrink(1);
        if (level.getGameTime() % CupolaBlockEntity.POUR_PERIOD_TICKS == 0 && !level.hasNeighborSignal(worldPosition)) {
            FoundrySubsystem shop = b.world().subsystem(FoundrySubsystem.class);
            BlockPos front = worldPosition.relative(getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
            if (!SandMoldBlockEntity.pourInto(level, front, shop, u)) SandMoldBlockEntity.pourInto(level, front.below(), shop, u);
        }
        BlockState st = getBlockState();
        if (st.getValue(BlockStateProperties.LIT) != u.lit())
            level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, u.lit()), Block.UPDATE_ALL);
    }

    /** Пламя и дым над летком крышки горящей печи. */
    private void clientTick() {
        MachineFx fx = MachineFx.of(level, worldPosition, getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (fx == null || !getBlockState().getValue(BlockStateProperties.LIT)) return;
        if (fx.chance(5)) fx.flame(fx.at(8, 13.6, 8), 0.15);
        if (fx.chance(2)) fx.smoke(fx.at(8, 14, 8), false, 0.1);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return alloyOf(stack) != null || stack.is(FoundryTags.FURNACE_COAL);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        CrucibleUnit u = unit();
        CastAlloy a = alloyOf(stack);
        if (u != null && a != null && u.meltAlloy() != null && u.meltAlloy() != a) {
            player.displayClientMessage(FoundryText.msg("crucible_alloy", FoundryText.alloy(u.meltAlloy().id)), true);
            return ItemInteractionResult.CONSUME;
        }
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        CrucibleUnit u = unit();
        if (u == null) return InteractionResult.SUCCESS;
        CrucibleFurnace f = u.furnace();
        player.displayClientMessage(f.massKg() > 0
                ? FoundryText.msg("crucible", FoundryText.alloy(f.alloy().id), FoundryText.num(f.massKg()),
                        FoundryText.num(f.temperatureK() - 273.15), FoundryText.num(f.liquidFraction() * 100), FoundryText.num(u.fuelKg()))
                : FoundryText.msg("crucible_empty", FoundryText.num(u.fuelKg())), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return FoundryPanels.CRUCIBLE;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[FoundryPanels.T_ALLOY] = -1;
        out[FoundryPanels.FLAGS] = level.hasNeighborSignal(worldPosition) ? 1 << FoundryPanels.F_HELD : 0;
        CrucibleUnit u = unit();
        if (u == null) return;
        CrucibleFurnace f = u.furnace();
        out[FoundryPanels.T_TEMP_C] = (int) Math.round(f.temperatureK() - 273.15);
        out[FoundryPanels.T_FUEL_KG10] = (int) Math.round(u.fuelKg() * 10);
        if (f.massKg() > 0) {
            out[FoundryPanels.T_MASS_KG10] = (int) Math.round(f.massKg() * 10);
            out[FoundryPanels.T_LIQUID_PERMIL] = (int) Math.round(f.liquidFraction() * 1000);
            out[FoundryPanels.T_ALLOY] = f.alloy().ordinal();
        }
        if (u.lit()) out[FoundryPanels.FLAGS] |= 1 << FoundryPanels.F_RUNNING;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inv.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inventory")) inv.load(registries, tag.getCompound("inventory"));
    }
}
