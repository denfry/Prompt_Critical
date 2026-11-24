package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.grid.MachinePower;
import dev.denfry.promptcritical.mod.radiation.ContaminationService;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.npp.SpentFuelPool;
import dev.denfry.promptcritical.sim.radiation.DoseRate;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import dev.denfry.promptcritical.sim.radiation.ShieldMaterial;
import dev.denfry.promptcritical.sim.radiation.SurfaceContamination;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Бассейн выдержки ВВЭР-70 ({@code vver70_fuel_pool_real}): {@value #RACKS} гнёзд стеллажей под ТВС (у АЭС Райнсберг
 * к останову в бассейне стояло 246 сборок). Вода, её нагрев, выкипание и оголение — {@link SpentFuelPool}; насосы
 * охлаждения и подпитки — нагрузка {@value #PUMPS_W} Вт от щита площадки. Остаточное тепло и излучение — по
 * {@link ActivityData} облучённых сборок; излучение выходит наверх через слой воды над головками ТВС
 * ({@link DoseRate#behind}), бетон стен считается тем же слоем — сбоку оценка с запасом. ТВС приходят и уходят
 * через проём в северной стене (перегрузочная машина, контейнер); свежую сборку можно поставить рукой.
 */
public final class SpentFuelPoolBlockEntity extends DeviceBlockEntity implements FoundryDevice, RealScaleBlockEntity {
    public static final int RACKS = 256;
    /** Насосы охлаждения и подпитки с теплообменником, Вт [est]. */
    public static final double PUMPS_W = 30e3;
    /** Воздух реакторного зала, °C. */
    public static final double HALL_C = 20;
    /** Центр топливного столба над низом модели, м. */
    public static final double FUEL_MID_M = 2.0;
    public static final int CLAD_SPILL_RADIUS = 6;

    private final ItemStackHandler racks = new ItemStackHandler(RACKS) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(PcItems.FUEL_ASSEMBLY.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final SpentFuelPool pool = new SpentFuelPool(SpentFuelPool.VVER70);
    private double decayW, activityBq, surfaceSvH;
    private int assemblies;
    private boolean powered;
    // клиент: уровень воды для отрисовки, затвор проёма
    private float levelM = (float) SpentFuelPool.VVER70.nominalLevelM(), levelSent = -1;
    private boolean gateOpen, boiling, boilingSent;

    public SpentFuelPoolBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.FUEL_POOL.get(), pos, state);
    }

    public ItemStackHandler racks() {
        return racks;
    }

    public SpentFuelPool pool() {
        return pool;
    }

    public double decayW() {
        return decayW;
    }

    public int assemblies() {
        return assemblies;
    }

    /** Уровень воды от низа модели, м (на клиенте — последний присланный). */
    public float levelM() {
        return levelM;
    }

    public boolean gateOpen() {
        return gateOpen;
    }

    /** Клиент: подъём затвора проёма, 0…1. */
    public float gate01() {
        return gate01;
    }

    private float gate01;

    public boolean boiling() {
        return boiling;
    }

    /** Затвор проёма поднимает перегрузочная машина, пока ТВС идёт через проём. */
    public void setGate(boolean open) {
        gateOpen = open;
        sync();
    }

    /** Поставить ТВС в первое свободное гнездо; false — мест нет. */
    public boolean store(ItemStack s) {
        for (int i = 0; i < RACKS; i++)
            if (racks.getStackInSlot(i).isEmpty()) {
                racks.setStackInSlot(i, s.copyWithCount(1));
                return true;
            }
        return false;
    }

    /** Забрать свежую (необлучённую) ТВС; пусто — свежих нет. */
    public ItemStack takeFresh() {
        for (int i = 0; i < RACKS; i++) {
            ItemStack s = racks.getStackInSlot(i);
            if (!s.isEmpty() && !s.has(ActivityData.ACTIVITY.get())) {
                racks.setStackInSlot(i, ItemStack.EMPTY);
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean hasFresh() {
        for (int i = 0; i < RACKS; i++) {
            ItemStack s = racks.getStackInSlot(i);
            if (!s.isEmpty() && !s.has(ActivityData.ACTIVITY.get())) return true;
        }
        return false;
    }

    public boolean hasFreeRack() {
        for (int i = 0; i < RACKS; i++) if (racks.getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    @Override
    public void tickDevice() {
        if (level.isClientSide) {
            // затвор проёма ходит ≈ 2,5 с
            gate01 = (float) Math.max(0, Math.min(1, gate01 + (gateOpen ? 0.02f : -0.02f)));
            return;
        }
        if (!(level instanceof ServerLevel s) || level.getGameTime() % 20 != 0) return;
        long now = level.getGameTime();
        PointSource src = PointSource.NONE;
        double q = 0, a = 0;
        int n = 0;
        for (int i = 0; i < RACKS; i++) {
            ItemStack st = racks.getStackInSlot(i);
            if (st.isEmpty()) continue;
            n++;
            ActivityData d = st.get(ActivityData.ACTIVITY.get());
            if (d == null) continue;
            var inv = d.inventory(now);
            src = src.plus(inv.source());
            q += inv.decayHeatW();
            a += inv.totalBq();
        }
        decayW = q;
        activityBq = a;
        assemblies = n;
        powered = MachinePower.powered(level, worldPosition, PUMPS_W);
        pool.step(1, q, n, powered, powered, HALL_C);
        Vec3 fuel = Vec3.atBottomCenterOf(worldPosition).add(0, FUEL_MID_M, 0);
        // источник — середина топливного столба, защита — вода от неё до зеркала
        double waterCm = Math.max(0, pool.levelM() - FUEL_MID_M) * 100;
        if (!src.empty()) RadiationService.source(s, fuel, waterCm > 0 ? DoseRate.behind(src, List.of(new DoseRate.Layer(ShieldMaterial.WATER, waterCm))) : src);
        Vec3 surface = Vec3.atBottomCenterOf(worldPosition).add(0, pool.levelM() + 0.2, 0);
        if (pool.boilKgS() > 0)
            s.sendParticles(ParticleTypes.CLOUD, surface.x, surface.y, surface.z, 6, 2, 0.1, 2, 0.02);
        if (!pool.cladFailed() && pool.cladC() > dev.denfry.promptcritical.mod.radiation.FuelRackBlockEntity.WARN_C)
            s.sendParticles(ParticleTypes.LARGE_SMOKE, fuel.x, fuel.y + 1.5, fuel.z, 4, 1.5, 0.3, 1.5, 0.01);
        boolean wasFailed = cladFailedSeen;
        if (pool.cladFailed() && !wasFailed) {
            cladFailedSeen = true;
            // разрушенные оболочки на воздухе: аэрозоль топлива оседает вокруг бассейна (NUREG-1738)
            double beta = activityBq * dev.denfry.promptcritical.mod.radiation.FuelRackBlockEntity.CLAD_RELEASE_FRACTION;
            ContaminationService.spill(s, worldPosition.above(4), CLAD_SPILL_RADIUS, beta * SurfaceContamination.FUEL_ALPHA_PER_BETA, beta);
            for (ServerPlayer p : s.getPlayers(p -> p.distanceToSqr(fuel) < 64 * 64))
                p.displayClientMessage(NppText.msg("pool_clad").withStyle(ChatFormatting.RED), false);
        }
        surfaceSvH = RadiationService.rateSvH(s, surface.add(0, 0.8, 0));
        levelM = (float) pool.levelM();
        boiling = pool.boilKgS() > 0;
        BlockState st = getBlockState();
        if (st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING) != (powered && pool.coolingW() > 0))
            level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, powered && pool.coolingW() > 0), 3);
        if (Math.abs(levelM - levelSent) > 0.02f || boiling != boilingSent) sync();
        if (now % 100 == 0) setChanged();
    }

    private boolean cladFailedSeen;

    private void sync() {
        if (level instanceof ServerLevel sl) {
            levelSent = levelM;
            boilingSent = boiling;
            setChanged();
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return racks;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.FUEL_ASSEMBLY.get());
    }

    /** Свежую ТВС ставят в стеллаж рукой (через проём, крюком крана). */
    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!store(stack)) {
            player.displayClientMessage(NppText.msg("pool_full"), true);
            return ItemInteractionResult.FAIL;
        }
        if (!player.isCreative()) stack.shrink(1);
        return ItemInteractionResult.SUCCESS;
    }

    /** Пустой рукой — сводка бассейна; Shift — забрать свежую ТВС (облучённые — только машиной или контейнером). */
    @Override
    public InteractionResult use(Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            ItemStack s = takeFresh();
            if (!s.isEmpty() && !player.addItem(s)) player.drop(s, false);
            return InteractionResult.SUCCESS;
        }
        player.displayClientMessage(NppText.msg("pool_status", assemblies, RACKS, fmt(pool.levelM(), 2), fmt(pool.waterC(), 1),
                fmt(decayW / 1e3, 1), fmt(pool.coolingW() / 1e3, 1), fmt(pool.shieldWaterCm() / 100, 2), fmt(surfaceSvH * 1e6, 2),
                NppText.msg(powered ? (pool.coolingW() > 0 || pool.levelM() >= pool.spec().suctionM() ? "pool_cooling" : "pool_no_suction") : "pool_no_power")), false);
        return InteractionResult.SUCCESS;
    }

    private static String fmt(double v, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", v);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("racks", racks.serializeNBT(registries));
        tag.putDouble("water_kg", pool.waterKg());
        tag.putDouble("water_c", pool.waterC());
        tag.putDouble("clad_c", pool.cladC());
        tag.putBoolean("clad_failed", pool.cladFailed());
        tag.putFloat("view_level", levelM);
        tag.putBoolean("view_gate", gateOpen);
        tag.putBoolean("view_boil", boiling);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("racks")) racks.deserializeNBT(registries, tag.getCompound("racks"));
        if (tag.contains("water_kg")) {
            pool.restore(tag.getDouble("water_kg"), tag.getDouble("water_c"), tag.getDouble("clad_c"), tag.getBoolean("clad_failed"));
            cladFailedSeen = pool.cladFailed();
        }
        if (tag.contains("view_level")) levelM = tag.getFloat("view_level");
        gateOpen = tag.getBoolean("view_gate");
        boiling = tag.getBoolean("view_boil");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putFloat("view_level", levelM);
        tag.putBoolean("view_gate", gateOpen);
        tag.putBoolean("view_boil", boiling);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
