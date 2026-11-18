package dev.denfry.promptcritical.mod.reprocessing;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import dev.denfry.promptcritical.sim.radiation.ShieldMaterial;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Хранилище остеклованных высокоактивных отходов (по типу хранилищ стекла в Селлафилде и на «Маяке»): ячейки в
 * бетонном массиве толщиной {@value #CONCRETE_CM} см, тепло снимает принудительная вентиляция — до {@value #VENT_W}
 * Вт на хранилище. Принимает стеклоблоки и любые предметы с активностью. Наружу проходит только то, что осталось за
 * бетоном: источник для {@link RadiationService} ослаблен им заранее (бетон с накоплением, нейтроны — выведением).
 */
public final class HlwVaultBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int SLOTS = 12;
    public static final double CONCRETE_CM = 100, VENT_W = 50_000;

    private final ItemStackHandler slots = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.has(ActivityData.ACTIVITY.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private double heatW, activityBq;

    public HlwVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ReprocessingBlockEntities.HLW_VAULT.get(), pos, state);
    }

    public ItemStackHandler slots() {
        return slots;
    }

    public double heatW() {
        return heatW;
    }

    /** Источник за бетонными стенами: γ с накоплением бетона, нейтроны — сечением выведения. */
    static PointSource behindConcrete(PointSource src) {
        double[] p = src.photons().clone();
        for (int i = 0; i < p.length; i++) {
            double mfp = ShieldMaterial.CONCRETE.mu(src.energyMeV()[i], ShieldMaterial.CONCRETE.density) * CONCRETE_CM;
            p[i] *= ShieldMaterial.CONCRETE.buildup(mfp) * Math.exp(-mfp);
        }
        double n = src.neutrons() * Math.exp(-ShieldMaterial.CONCRETE.removal(ShieldMaterial.CONCRETE.density) * CONCRETE_CM);
        return new PointSource(src.energyMeV(), p, n);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s) || level.getGameTime() % 20 != 0) return;
        long now = level.getGameTime();
        PointSource src = PointSource.NONE;
        double q = 0, a = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack st = slots.getStackInSlot(i);
            ActivityData d = st.get(ActivityData.ACTIVITY.get());
            if (d == null) continue;
            var inv = d.inventory(now);
            src = src.plus(inv.source().scaled(st.getCount()));
            q += inv.decayHeatW() * st.getCount();
            a += inv.totalBq() * st.getCount();
        }
        heatW = q;
        activityBq = a;
        if (!src.empty()) RadiationService.source(s, worldPosition.getCenter(), behindConcrete(src));
        if (now % 100 == 0) setChanged();
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return slots;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return slots;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.has(ActivityData.ACTIVITY.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack left = stack.copy();
        for (int i = 0; i < SLOTS && !left.isEmpty(); i++) left = slots.insertItem(i, left, false);
        int moved = stack.getCount() - left.getCount();
        if (moved == 0) return ItemInteractionResult.FAIL;
        if (!player.isCreative()) stack.shrink(moved);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public PanelLayout panelLayout() {
        return ReprocessingPanels.VAULT;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[ReprocessingPanels.V_HEAT_W] = (int) Math.round(Math.min(2e9, heatW));
        v[ReprocessingPanels.V_ACT_TBQ] = (int) Math.round(Math.min(2e9, activityBq / 1e12));
        v[ReprocessingPanels.V_FLAGS] = heatW > VENT_W ? 1 << ReprocessingPanels.VF_HOT : 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("slots", slots.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("slots")) slots.deserializeNBT(registries, tag.getCompound("slots"));
    }
}
