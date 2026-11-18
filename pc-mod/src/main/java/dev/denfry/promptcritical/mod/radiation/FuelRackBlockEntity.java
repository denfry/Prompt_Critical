package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import dev.denfry.promptcritical.sim.radiation.SurfaceContamination;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Стеллаж бассейна выдержки: {@value #SLOTS} гнёзд под облучённые предметы (с {@link ActivityData}). Сам не
 * защищает — защищает вода над ним: три метра воды снижают γ выдержанной ТВС в сотни тысяч раз. Под водой
 * остаточное тепло уходит в воду бассейна; на воздухе стеллаж греется — T = 20 °C + Q/(h·A), h·A = {@value #HA_W_K} Вт/К
 * (естественная конвекция и излучение). Выше {@value #CLAD_LIMIT_C} °C оболочки окисляются и рвутся (циркониевый
 * пожар бассейна без воды — сценарий NUREG-1738); здесь это аварийная лампа, дым и осевший на пол вокруг стеллажа
 * аэрозоль топлива ({@link ContaminationService#spill}).
 */
public final class FuelRackBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int SLOTS = 9;
    public static final double HA_W_K = 25, CLAD_LIMIT_C = 800, WARN_C = 400;
    /**
     * Доля активности, оседающая на пол вокруг при разрушении оболочек на воздухе. Игровое допущение порядка выхода
     * нелетучих аэрозолей топлива (10⁻⁶–10⁻⁴ инвентаря); сам сценарий и его выбросы в атмосферу разобраны в NUREG-1738
     * (2001), летучий Cs там уходит в выброс, а не на пол.
     */
    public static final double CLAD_RELEASE_FRACTION = 1e-5;
    public static final int CLAD_SPILL_RADIUS = 4;

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
    private double heatW, activityBq, tempC = 20, surfaceSvH;
    private boolean submerged, cladFailed;

    public FuelRackBlockEntity(BlockPos pos, BlockState state) {
        super(RadiationBlockEntities.FUEL_RACK.get(), pos, state);
    }

    public ItemStackHandler slots() {
        return slots;
    }

    public double heatW() {
        return heatW;
    }

    public boolean submerged() {
        return submerged;
    }

    public double tempC() {
        return tempC;
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
        Vec3 center = worldPosition.getCenter();
        if (!src.empty()) RadiationService.source(s, center, src);
        submerged = level.getFluidState(worldPosition.above()).is(FluidTags.WATER);
        // на воздухе: равновесная температура с тепловой инерцией ≈ минуты
        double target = submerged ? 20 + Math.min(80, q / 2000) : 20 + q / HA_W_K;
        tempC += (target - tempC) * 0.05;
        if (!submerged && tempC > WARN_C)
            s.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 0.6, center.z, 3, 0.3, 0.1, 0.3, 0.01);
        if (submerged && q > 1000)
            s.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, center.x, center.y + 0.6, center.z, 2, 0.3, 0.1, 0.3, 0.05);
        if (!cladFailed && tempC > CLAD_LIMIT_C) {
            cladFailed = true;
            // разрушенные оболочки: аэрозоль топлива и продуктов деления оседает на пол вокруг стеллажа
            double beta = activityBq * CLAD_RELEASE_FRACTION;
            ContaminationService.spill(s, worldPosition, CLAD_SPILL_RADIUS, beta * SurfaceContamination.FUEL_ALPHA_PER_BETA, beta);
            for (ServerPlayer p : s.getPlayers(p -> p.distanceToSqr(center) < 64 * 64))
                p.displayClientMessage(RadiationBlocksText.msg("rack.clad").withStyle(ChatFormatting.RED), false);
        }
        surfaceSvH = RadiationService.rateSvH(s, center.add(0, submerged ? 4 : 1.5, 0));
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
        return RackPanels.RACK;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[RackPanels.HEAT_W] = (int) Math.round(Math.min(2e9, heatW));
        v[RackPanels.TEMP_C] = (int) Math.round(tempC);
        v[RackPanels.ACT_GBQ] = (int) Math.round(Math.min(2e9, activityBq / 1e9));
        v[RackPanels.DOSE_LOG] = surfaceSvH > 0 ? (int) Math.round((Math.log10(surfaceSvH) + 10) * 100) : 0;
        v[RackPanels.FLAGS] = (submerged ? 1 << RackPanels.F_WATER : 0) | (!submerged && heatW > 0 ? 1 << RackPanels.F_DRY : 0)
                | (tempC > WARN_C ? 1 << RackPanels.F_HOT : 0) | (cladFailed ? 1 << RackPanels.F_CLAD : 0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("slots", slots.serializeNBT(registries));
        tag.putDouble("t", tempC);
        tag.putBoolean("clad", cladFailed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("slots")) slots.deserializeNBT(registries, tag.getCompound("slots"));
        tempC = tag.contains("t") ? tag.getDouble("t") : 20;
        cladFailed = tag.getBoolean("clad");
    }
}
