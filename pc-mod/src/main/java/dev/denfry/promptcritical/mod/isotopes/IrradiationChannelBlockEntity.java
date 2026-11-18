package dev.denfry.promptcritical.mod.isotopes;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.npp.PowerReactorBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.isotopes.CapsuleThermal;
import dev.denfry.promptcritical.sim.isotopes.TargetMaterial;
import dev.denfry.promptcritical.sim.neutronics.PointKinetics;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.reactor.ChannelReactor;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Канал облучения {@code irradiation_channel} (12 §12.1.1): капсула с мишенью в потоке нейтронов реактора.
 *
 * <ul>
 *   <li>T0 — блок в кладке графитового котла вместо графита (обход кладки считает его графитом): поток узла приходит от
 *   пульта котла раз в секунду ({@link #push}); капсула остывает в воздухе кладки.</li>
 *   <li>T1 — блок вплотную к корпусу канального реактора (до {@value #T1_REACH} блоков): экспериментальный канал на
 *   радиусе {@link ChannelReactor#EXPERIMENTAL_CHANNEL_R_M} м, вода первого контура; поглощение мишени реактор
 *   вычитает из реактивности. Экспериментальных каналов у реактора — {@link PowerReactorBlockEntity#IRRADIATION_CHANNELS}.</li>
 * </ul>
 *
 * Раз в секунду (72 с физики) мишень облучается точным шагом Бейтмана, капсула греется распадом и γ-излучением зоны
 * (пропорционально местной плотности мощности); перегрев выше предела оболочки разгерметизирует капсулу — 10 %
 * продукта остаётся в канале, и канал сам становится источником. Капсула в слоте несёт активность продукта
 * ({@link ActivityData}): вынутая, она излучает в руках.
 */
public final class IrradiationChannelBlockEntity extends IronworksBlockEntity implements PanelHost {
    public static final int T1_REACH = 2, PERIOD = 20;
    /** Термическое сопротивление капсула → вода канала T1 и капсула → графит/воздух кладки T0, К/Вт. */
    public static final double R_TH_WATER = 0.05, R_TH_AIR = 0.5;
    /** Плотность мощности, к которой отнесён γ-нагрев {@code isotopes.gamma_heating_w_per_g} (PWR), Вт/см³. */
    public static final double PWR_POWER_DENSITY = 100;
    /** Доля продукта, остающаяся в канале при разгерметизации капсулы. */
    public static final double LEAK_FRACTION = 0.1;

    public enum Source { NONE, PILE, POWER_REACTOR, NO_SLOT }

    private final ItemStackHandler capsule = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isCapsule(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    // поток от пульта T0
    private double pushedFlux, pushedCoolantK, pushedPowerDensity;
    private long pushedUntil = Long.MIN_VALUE, lastStep = Long.MIN_VALUE;
    // показания
    private double flux, tempK = 293.15, powerW, worth;
    private Source source = Source.NONE;
    // загрязнение канала после разгерметизации
    private String leakNuclide = "";
    private double leakBq;
    private long leakTick;

    public IrradiationChannelBlockEntity(BlockPos pos, BlockState state) {
        super(IsotopeBlockEntities.IRRADIATION_CHANNEL.get(), pos, state);
    }

    public static boolean isCapsule(ItemStack s) {
        return s.is(PcItems.IRRADIATION_CAPSULE.get()) && s.has(IsotopeComponents.IRRADIATION_TARGET.get());
    }

    public ItemStack capsule() {
        return capsule.getStackInSlot(0);
    }

    public double flux() {
        return flux;
    }

    public double capsuleTempK() {
        return tempK;
    }

    public Source source() {
        return source;
    }

    /** Поток узла кладки T0 на ближайшие две секунды: тепловой, н/(см²·с), температура графита, К, мощность, Вт/см³. */
    public void push(double thermalFlux, double coolantK, double powerDensityWcm3, long now) {
        pushedFlux = Math.max(0, thermalFlux);
        pushedCoolantK = coolantK;
        pushedPowerDensity = Math.max(0, powerDensityWcm3);
        pushedUntil = now + 2L * PERIOD;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        if (!(level instanceof ServerLevel sl)) return;
        long now = level.getGameTime();
        if (Math.floorMod(now + worldPosition.hashCode(), PERIOD) != 0) return;
        double phi = 0, coolant = 293.15, q = 0, rTh = R_TH_AIR;
        ItemStack st = capsule();
        IsotopeComponents.IrradiationTarget t = st.get(IsotopeComponents.IRRADIATION_TARGET.get());
        TargetMaterial m = t == null ? null : t.materialOrNull();
        if (pushedUntil >= now) {
            source = Source.PILE;
            phi = pushedFlux;
            coolant = pushedCoolantK;
            q = pushedPowerDensity;
        } else {
            PowerReactorBlockEntity r = reactor(sl);
            source = Source.NONE;
            if (r != null) {
                double absorb = m == null ? 0 : m.absorptionCm2(t.massKg());
                if (r.irradiationSlot(worldPosition, absorb, now)) {
                    ChannelReactor c = r.reactor();
                    source = Source.POWER_REACTOR;
                    phi = c.fluxAt(ChannelReactor.EXPERIMENTAL_CHANNEL_R_M);
                    coolant = c.outletK();
                    q = c.fissionDensity(c.power()) * PointKinetics.FISSION_ENERGY;
                    rTh = R_TH_WATER;
                    worth = c.irradiationWorth();
                } else {
                    source = Source.NO_SLOT;
                }
            }
        }
        if (source != Source.POWER_REACTOR) worth = 0;
        flux = phi;
        if (m != null && t.atoms().size() == m.chain.size()) irradiate(sl, st, t, m, phi, coolant, q, rTh, now);
        else {
            tempK = coolant;
            powerW = 0;
        }
        lastStep = now;
        leak(sl, now);
        boolean loaded = !st.isEmpty();
        BlockState s = getBlockState();
        if (s.hasProperty(dev.denfry.promptcritical.mod.block.PcProperties.FILLED)
                && s.getValue(dev.denfry.promptcritical.mod.block.PcProperties.FILLED) != loaded)
            level.setBlock(worldPosition, s.setValue(dev.denfry.promptcritical.mod.block.PcProperties.FILLED, loaded), Block.UPDATE_ALL);
    }

    @Nullable
    private PowerReactorBlockEntity reactor(ServerLevel sl) {
        var list = Ironworks.blockEntities(sl, new AABB(worldPosition).inflate(T1_REACH), PowerReactorBlockEntity.class);
        return list.isEmpty() ? null : list.get(0);
    }

    private void irradiate(ServerLevel sl, ItemStack st, IsotopeComponents.IrradiationTarget t, TargetMaterial m, double phi, double coolantK,
            double powerDensity, double rTh, long now) {
        // распад вне канала — до последнего шага (или до загрузки), облучение — только за время в канале
        long from = Math.max(t.refTick(), lastStep == Long.MIN_VALUE ? now - PERIOD : lastStep);
        from = Math.min(from, now);
        double[] a = t.atomsAt(from);
        double dt = (now - from) * ActivityData.GAME_S_PER_TICK;
        m.step(a, phi, m.selfShielding(), dt, a);
        double fluence = t.fluence() + phi * dt;
        powerW = CapsuleThermal.powerW(m, a, t.massKg(), IsotopeComponents.gammaHeatingWPerG(), powerDensity / PWR_POWER_DENSITY);
        tempK = CapsuleThermal.tempK(coolantK, powerW, rTh);
        boolean sealed = t.sealed();
        double productBq = m.activityBq(a, m.productLink);
        if (sealed && tempK > m.capsuleLimitK) {
            sealed = false;
            double leaked = productBq * LEAK_FRACTION;
            ActivityInventory before = leakInventory(now);
            leakNuclide = m.product.name();
            leakBq = before.bq(m.product) + leaked;
            leakTick = now;
            a[m.productLink] *= 1 - LEAK_FRACTION;
            productBq *= 1 - LEAK_FRACTION;
            for (ServerPlayer p : sl.players())
                if (p.blockPosition().closerThan(worldPosition, 32))
                    p.displayClientMessage(IsotopeText.msg("capsule_failure", Math.round(tempK - 273.15)).withStyle(ChatFormatting.RED), false);
        }
        ItemStack out = st.copy();
        out.set(IsotopeComponents.IRRADIATION_TARGET.get(), t.with(a, fluence, now, sealed));
        out.set(dev.denfry.promptcritical.mod.environment.ActivityData.ACTIVITY.get(),
                new ActivityData(now, t.massKg(), m.densityGcc, Map.of(m.product.name(), productBq)));
        capsule.setStackInSlot(0, out);
    }

    private ActivityInventory leakInventory(long now) {
        if (leakBq <= 0 || leakNuclide.isEmpty()) return new ActivityInventory(Map.of(), 0, 1);
        return new ActivityData(leakTick, 0, 1, Map.of(leakNuclide, leakBq)).inventory(now);
    }

    /** Загрязнённый канал излучает сам по себе. */
    private void leak(ServerLevel sl, long now) {
        if (leakBq <= 0) return;
        ActivityInventory inv = leakInventory(now);
        if (inv.totalBq() < 1e6) {
            leakBq = 0;
            return;
        }
        RadiationService.source(sl, Vec3.atCenterOf(worldPosition), inv.source());
    }

    /** Активность загрязнения канала сейчас, Бк. */
    public double contaminationBq() {
        return level == null ? 0 : leakInventory(level.getGameTime()).totalBq();
    }

    // --- руки ---

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return capsule;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return isCapsule(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!isCapsule(stack) || !capsule().isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        capsule.setStackInSlot(0, stack.split(1));
        return ItemInteractionResult.SUCCESS;
    }

    /** Shift + ПКМ: вынуть капсулу в руки. */
    @Override
    public InteractionResult use(Player player) {
        ItemStack s = capsule();
        if (s.isEmpty()) return InteractionResult.PASS;
        capsule.setStackInSlot(0, ItemStack.EMPTY);
        if (!player.addItem(s)) player.drop(s, false);
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return IsotopePanels.CHANNEL;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return capsule;
    }

    @Override
    public void readPanel(int[] out) {
        ItemStack st = capsule();
        IsotopeComponents.IrradiationTarget t = st.get(IsotopeComponents.IRRADIATION_TARGET.get());
        ActivityData a = st.get(ActivityData.ACTIVITY.get());
        long now = level == null ? 0 : level.getGameTime();
        double bq = a == null ? 0 : a.inventory(now).totalBq();
        out[IsotopePanels.CH_FLUX_LOG] = IsotopePanels.log100(flux);
        out[IsotopePanels.CH_TEMP_C] = (int) Math.round(tempK - 273.15);
        out[IsotopePanels.CH_POWER_W] = (int) Math.round(powerW);
        out[IsotopePanels.CH_ACTIVITY_LOG] = IsotopePanels.log100(bq);
        out[IsotopePanels.CH_FLUENCE_LOG] = IsotopePanels.log100(t == null ? 0 : t.fluence());
        out[IsotopePanels.CH_WORTH_PCM] = (int) Math.round(worth * 1e5);
        out[IsotopePanels.CH_SOURCE] = source.ordinal();
        int flags = 0;
        if (!st.isEmpty()) flags |= 1 << IsotopePanels.F_LOADED;
        if (t != null && !t.sealed()) flags |= 1 << IsotopePanels.F_FAILED;
        if (flux > 0) flags |= 1 << IsotopePanels.F_FLUX;
        if (contaminationBq() > 0) flags |= 1 << IsotopePanels.F_CONTAMINATED;
        out[IsotopePanels.CH_FLAGS] = flags;
        TargetMaterial m = t == null ? null : t.materialOrNull();
        out[IsotopePanels.CH_LIMIT_C] = m == null ? 0 : (int) Math.round(m.capsuleLimitK - 273.15);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("capsule", capsule.serializeNBT(registries));
        if (leakBq > 0) {
            tag.putString("leak_nuclide", leakNuclide);
            tag.putDouble("leak_bq", leakBq);
            tag.putLong("leak_tick", leakTick);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("capsule")) capsule.deserializeNBT(registries, tag.getCompound("capsule"));
        leakNuclide = tag.getString("leak_nuclide");
        leakBq = tag.getDouble("leak_bq");
        leakTick = tag.getLong("leak_tick");
        try {
            if (!leakNuclide.isEmpty()) Emitter.valueOf(leakNuclide);
        } catch (IllegalArgumentException e) {
            leakBq = 0;
        }
    }
}
