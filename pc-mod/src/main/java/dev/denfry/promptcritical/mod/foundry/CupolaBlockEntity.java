package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.metallurgy.CupolaFurnace;
import dev.denfry.promptcritical.sim.metallurgy.CupolaUnit;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Вагранка {@code cupola_furnace} (08 §8.9): колоша — металл и кокс по предмету в 100 кг, дутьё — воздуходувкой на
 * валу Create, выпуск — сам, в заформованную форму у подножия (рядом с нижней клеткой или ниже на блок). Сигнал
 * красного камня держит лётку закрытой.
 */
public final class CupolaBlockEntity extends KineticMachineBlockEntity<CupolaUnit> {
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
