package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.metallurgy.CupolaFurnace;
import dev.denfry.promptcritical.sim.metallurgy.CupolaUnit;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Вагранка {@code cupola_furnace} (08 §8.9): колоша — металл и кокс по предмету в 100 кг, дутьё — воздуходувкой на
 * валу Create, выпуск — сам, в заформованную форму у подножия (рядом с нижней клеткой или ниже на блок). Сигнал
 * красного камня держит лётку закрытой.
 */
public final class CupolaBlockEntity extends KineticMachineBlockEntity<CupolaUnit> implements PanelHost {
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.INPUT};
    static final int POUR_PERIOD_TICKS = 10;

    private final MachineInventory inv = new MachineInventory(ROLES,
            (slot, s) -> slot == 0 ? s.is(FoundryTags.CUPOLA_IRON) : s.is(FoundryTags.CUPOLA_COKE), this::setChanged);

    public CupolaBlockEntity(BlockPos pos, BlockState state) {
        super(FoundryBlockEntities.CUPOLA.get(), pos, state);
    }

    @Override
    @Nullable
    protected CupolaUnit unit(SimLevelBinding b) {
        return b.world().subsystem(FoundrySubsystem.class).unit(b.world().topology(), simId()) instanceof CupolaUnit u ? u : null;
    }

    @Override
    protected ShaftInput shaft(CupolaUnit u) {
        return u.shaft;
    }

    @Override
    protected double powerW(CupolaUnit u) {
        return u.powerW();
    }

    @Override
    protected double ratedW() {
        return CupolaUnit.BLOWER_NOMINAL_W;
    }

    @Override
    protected double nominalRpm() {
        return CupolaUnit.NOMINAL_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding b, CupolaUnit u) {
        double kg = ServerConfig.METALLURGY_INGOT_MASS_KG.get();
        ItemStack iron = inv.getStackInSlot(0);
        if (!iron.isEmpty() && u.charge(kg, 0)) iron.shrink(1);
        ItemStack coke = inv.getStackInSlot(1);
        if (!coke.isEmpty() && u.charge(0, ServerConfig.METALLURGY_BULK_ITEM_MASS_KG.get())) coke.shrink(1);
        if (level.getGameTime() % POUR_PERIOD_TICKS == 0 && !level.hasNeighborSignal(worldPosition)
                && !level.hasNeighborSignal(worldPosition.below())) {
            FoundrySubsystem shop = b.world().subsystem(FoundrySubsystem.class);
            BlockPos bottom = worldPosition.below();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (SandMoldBlockEntity.pourInto(level, bottom.relative(d), shop, u)
                        || SandMoldBlockEntity.pourInto(level, bottom.relative(d).below(), shop, u)) break;
            }
        }
        setStateFlag(BlockStateProperties.LIT, u.lit());
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(FoundryTags.CUPOLA_IRON) || stack.is(FoundryTags.CUPOLA_COKE);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        CupolaUnit u = unit();
        if (u != null) {
            CupolaFurnace f = u.furnace();
            player.displayClientMessage(FoundryText.msg("cupola", FoundryText.num(f.stackIronKg()), FoundryText.num(f.stackCokeKg()),
                    FoundryText.num(f.wellKg()), FoundryText.num(f.wellTemperatureK() - 273.15), FoundryText.num(u.airNm3S())), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void clientTick() {
        MachineFx fx = fx();
        if (fx == null || !getBlockState().getValue(BlockStateProperties.LIT)) return;
        if (fx.chance(3)) fx.sparks(fx.at(8, 32, 8), 1);
        if (fx.chance(4)) fx.smoke(fx.at(8, 32, 8), false, 0.3);
        if (fx.chance(3)) fx.flame(fx.at(8, -4, 0.6), 0.1);
        if (fx.chance(0.6)) fx.drip(fx.at(8, -6, -2.5));
        if (fx.chance(0.4)) fx.sound(fx.at(8, 20, 8), SoundEvents.FIRE_AMBIENT, 0.8f, 0.8f);
    }

    @Override
    public PanelLayout panelLayout() {
        return FoundryPanels.CUPOLA;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[FoundryPanels.SHAFT_RPM10] = shaftRpm10();
        boolean held = level.hasNeighborSignal(worldPosition) || level.hasNeighborSignal(worldPosition.below());
        CupolaUnit u = unit();
        out[FoundryPanels.FLAGS] = held ? 1 << FoundryPanels.F_HELD : 0;
        if (u == null) return;
        CupolaFurnace f = u.furnace();
        out[FoundryPanels.C_WELL_C] = (int) Math.round(f.wellTemperatureK() - 273.15);
        out[FoundryPanels.C_WELL_KG10] = (int) Math.round(f.wellKg() * 10);
        out[FoundryPanels.C_IRON_KG] = (int) Math.round(f.stackIronKg());
        out[FoundryPanels.C_COKE_KG] = (int) Math.round(f.stackCokeKg());
        out[FoundryPanels.C_AIR_NM3H] = (int) Math.round(u.airNm3S() * 3600);
        out[FoundryPanels.C_MELT_KGH] = (int) Math.round(f.meltRateKgS() * 3600);
        if (u.lit()) out[FoundryPanels.FLAGS] |= 1 << FoundryPanels.F_RUNNING;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) tag.put("inventory", inv.save(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket && tag.contains("inventory")) inv.load(registries, tag.getCompound("inventory"));
    }
}
