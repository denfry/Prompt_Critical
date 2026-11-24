package dev.denfry.promptcritical.mod.reprocessing;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.isotopes.IsotopeCraftRecipe;
import dev.denfry.promptcritical.mod.item.ExtractOnlyItemHandler;
import dev.denfry.promptcritical.mod.nuclear.FuelLot;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.enrichment.Criticality;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.radiation.FissionSource;
import dev.denfry.promptcritical.sim.reprocessing.ActinideBuildup;
import dev.denfry.promptcritical.sim.reprocessing.PurexFlowsheet;
import dev.denfry.promptcritical.sim.reprocessing.PurexPlant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Каньон радиохимического завода PUREX ({@link PurexPlant}): горячая камера управляет линией аппаратов в
 * {@value #REACH} блоках — рубка ({@code shearing_machine}), растворитель, не меньше двух батарей смесителей-отстойников
 * (два цикла экстракции), осадитель оксалата Pu, аппарат денитрации урана, печь остекловывания. Без аппарата стадия
 * стоит: нет рубки или растворителя — топливо не принимается; нет осадителя — нитрат плутония копится в буфере, и
 * выше минимальной критической массы — СЦР; нет печи — высокоактивный раствор копится до {@value #HLW_TANK_KG} кг
 * стекла, потом линия встаёт.
 *
 * <p>Топливо — предметы с партией ({@code fuel_lot}: выгорание и состав пишет реактор) или с активностью: тогда
 * выгорание оценивается по Cs-137, состав — {@link ActinideBuildup}. Азотная кислота и керосин (разбавитель ТБФ) —
 * трубами; уголь для иодного фильтра — в слот. Продукты: регенерат урана — мешки UO₃ по 1 кг с изотопией (U-236),
 * PuO₂ — партиями по {@value #PU_BATCH_KG} кг (меньше безопасной массы), стеклоблоки ВАО по {@value #CANISTER_KG} кг,
 * NpO₂ — мешками по {@link IsotopeCraftRecipe#NPO2_KG} кг (на одну мишень для наработки Pu-238): нептуний с нитритом
 * в питании идёт с ураном, во втором урановом цикле выводится и осаждается оксалатом в том же осадителе.
 * Криптон и непойманный иод уходят в трубу — облако по ветру. СЦР разбрызгивает раствор по полу вокруг камеры.
 */
public final class PurexCellBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    /** Ножницы, растворитель, насосы и эрлифты экстракции, вытяжная вентиляция каньона, Вт. Розетка или силовой щит рядом; при недостатке машина работает медленнее. */
    public static final double POWER_W = 160e3;
    public static final int REACH = 8, IN_SLOTS = 4, CHARCOAL_SLOT = 4, OUT_SLOTS = 6;
    public static final double PU_BATCH_KG = 0.1, CANISTER_KG = 50, HLW_TANK_KG = 500, KG_HM_PER_CHARCOAL = 25;
    /** HNO₃ в кислоте 3b (≈ 57 %, 1,345 кг/л), кг/л; керосин на кг ТМ, л. */
    /** NpO₂ на кг Np: (237 + 32) / 237. */
    static final double NPO2_PER_NP = 269.0 / 237.0;
    static final double HNO3_KG_L = 1.345 * 0.57, KEROSENE_L_PER_KG = 0.005, PU_PRECIPITATE_KG_S = 0.01;

    private final ItemStackHandler input = new ItemStackHandler(IN_SLOTS + 1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == CHARCOAL_SLOT ? stack.is(Items.CHARCOAL) : spentFuel(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ItemStackHandler output = new ItemStackHandler(OUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler outputView = new ExtractOnlyItemHandler(output);
    private final IItemHandlerModifiable panelItems = new CombinedInvWrapper(input, output);
    private final FeedTank acid = new FeedTank(PcFluids.NITRIC_ACID.source.get(), 16_000);
    private final FeedTank kerosene = new FeedTank(PcFluids.KEROSENE.source.get(), 2_000);
    private final IFluidHandler inlet = new MultiInlet(acid, kerosene);
    private final PurexPlant plant = new PurexPlant();
    private boolean on = true, shear, dissolver, extraction, oxalate, denitration, melter;
    /** Регенерат урана в работе: кг и изотопия. */
    private double uKg, u234, u235, u236;
    private PuLot puPending = new PuLot(0, 0, 0, 0, 0);
    /** Выделенный NpO₂, ещё не расфасованный, кг. */
    private double npO2Kg;
    /** Высокоактивные отходы: стекло, кг, и активность на {@link #hlwTick}. */
    private double glassKg, iodineCredits;
    private ActivityInventory hlw = new ActivityInventory(Map.of(), 0, 2.7);
    private long hlwTick;
    /** Текущая порция: остаток времени, с; выброс газа, Бк/с. */
    private double busyS, kr85BqS, i131BqS, lastDf = 1, puFlowKgS;

    public PurexCellBlockEntity(BlockPos pos, BlockState state) {
        super(ReprocessingBlockEntities.PUREX_CELL.get(), pos, state);
    }

    static boolean spentFuel(ItemStack s) {
        FuelLot l = s.get(FuelLot.TYPE.get());
        return l != null && l.burnup() > 0 || s.has(ActivityData.ACTIVITY.get()) && !s.is(PcItems.HLW_GLASS_CANISTER.get());
    }

    public PurexPlant plant() {
        return plant;
    }

    public ItemStackHandler input() {
        return input;
    }

    public ItemStackHandler output() {
        return output;
    }

    public FeedTank acid() {
        return acid;
    }

    public double glassKg() {
        return glassKg;
    }

    /** Состав и масса ТМ предмета: по партии реактора, иначе по Cs-137 активности. */
    static ActinideBuildup.Composition composition(ItemStack s, long now, double[] hmOut) {
        FuelLot l = s.get(FuelLot.TYPE.get());
        if (l != null && l.burnup() > 0) {
            double fp = ActinideBuildup.FP_PER_MWD * l.burnup();
            double hm0 = l.heavyMetalKg() / Math.max(1e-6, 1 - fp);
            hmOut[0] = hm0;
            return new ActinideBuildup.Composition(l.burnup(), l.u235(), l.u236(), l.pu() / hm0, l.pu239(), l.pu240(), l.pu241(), l.pu242(), fp,
                    ActinideBuildup.np237(l.burnup()));
        }
        ActivityData a = s.get(ActivityData.ACTIVITY.get());
        double hm = l != null ? l.heavyMetalKg() : a.massKg();
        hmOut[0] = hm;
        ActivityInventory inv = a.inventory(now);
        // атомов Cs-137 сейчас и на выгрузке → делений → выгорание
        double nNow = inv.bq(Emitter.CS137) / Emitter.CS137.lambda();
        double nDis = nNow * Math.pow(2, Math.max(0, now - a.refTick()) * ActivityData.GAME_S_PER_TICK / Emitter.CS137.halfLifeS);
        double fissions = nDis / dev.denfry.promptcritical.sim.radiation.SpentFuel.Y_CS137;
        double bu = fissions * dev.denfry.promptcritical.sim.radiation.SpentFuel.MEV_PER_FISSION * 1.602176634e-13
                / dev.denfry.promptcritical.sim.radiation.SpentFuel.J_PER_MWD / Math.max(1e-6, hm);
        double x0 = l != null ? l.u235() : FuelLot.of(s).u235();
        return ActinideBuildup.at(x0, bu);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        double power = dev.denfry.promptcritical.mod.grid.MachinePower.supply(s, worldPosition, on ? POWER_W : 0);
        double dt = 0.05 * b.world().clock().kSlow() * power;
        long now = level.getGameTime();
        if (now % 40 == 0) scanEquipment();
        // приём порции
        if (on && power > 0 && busyS <= 0 && shear && dissolver && extraction && glassKg < HLW_TANK_KG) intake(s, now);
        if (busyS > 0) {
            plant.meter(puFlowKgS * dt);
            busyS -= dt;
            Vec3 stack = worldPosition.getCenter().add(0, 6, 0); // труба над каньоном
            RadiationService.airRelease(s, stack, Emitter.KR85, kr85BqS);
            RadiationService.airRelease(s, stack, Emitter.I131, i131BqS);
        }
        // плутоний: осаждение оксалата и PuO₂ партиями
        if (oxalate && plant.puTankKg() > 0 && hasRoom(PcItems.PUO2_POWDER.get())) {
            double pu = plant.precipitate(PU_PRECIPITATE_KG_S * dt);
            puPending = puPending.plus(new PuLot(pu, puIso[0], puIso[1], puIso[2], puIso[3]));
            if (puPending.puKg() >= PU_BATCH_KG && emitPu()) puPending = new PuLot(0, 0, 0, 0, 0);
        }
        // нептуний: NpO₂ мешками на одну мишень
        while (oxalate && npO2Kg >= IsotopeCraftRecipe.NPO2_KG && hasRoom(PcItems.NPO2_POWDER.get())) {
            if (!emit(new ItemStack(PcItems.NPO2_POWDER.get()))) break;
            npO2Kg -= IsotopeCraftRecipe.NPO2_KG;
        }
        double fissions = plant.criticalityFissions();
        if (fissions > 0) criticality(s, fissions);
        // уран: денитрация в UO₃ по 1 кг
        while (denitration && uKg >= 1 && hasRoom(PcItems.UO3_POWDER.get())) {
            ItemStack u = new ItemStack(PcItems.UO3_POWDER.get());
            u.set(FuelLot.TYPE.get(), new FuelLot(1, u235, u234, 0, 0, "", "", 0, u236, 0, 0, 0, 0, 0));
            if (!emit(u)) break;
            uKg -= 1;
        }
        // стекло: стеклоблоки по 50 кг с долей активности
        while (melter && glassKg >= CANISTER_KG && hasRoom(PcItems.HLW_GLASS_CANISTER.get())) {
            ActivityInventory cur = hlw.decayed((now - hlwTick) * ActivityData.GAME_S_PER_TICK);
            double share = CANISTER_KG / glassKg;
            EnumMap<Emitter, Double> part = new EnumMap<>(Emitter.class), rest = new EnumMap<>(Emitter.class);
            for (Map.Entry<Emitter, Double> e : cur.activities().entrySet()) {
                part.put(e.getKey(), e.getValue() * share);
                rest.put(e.getKey(), e.getValue() * (1 - share));
            }
            ItemStack c = new ItemStack(PcItems.HLW_GLASS_CANISTER.get());
            c.set(ActivityData.ACTIVITY.get(), ActivityData.of(new ActivityInventory(part, CANISTER_KG, 2.7), now));
            if (!emit(c)) break;
            glassKg -= CANISTER_KG;
            hlw = new ActivityInventory(rest, glassKg, 2.7);
            hlwTick = now;
        }
        if (now % 10 == 0) setRunning(busyS > 0);
        if (now % 100 == 0) setChanged();
    }

    private final double[] puIso = {0, 0, 0, 0};

    private void intake(ServerLevel s, long now) {
        int slot = -1;
        for (int i = 0; i < IN_SLOTS; i++) if (spentFuel(input.getStackInSlot(i))) slot = i;
        if (slot < 0) return;
        ItemStack fuel = input.getStackInSlot(slot);
        double[] hm = {0};
        ActinideBuildup.Composition comp = composition(fuel, now, hm);
        double acidL = hm[0] * PurexPlant.ACID_KG_PER_KG_HM / HNO3_KG_L, kerL = hm[0] * KEROSENE_L_PER_KG;
        if (hm[0] <= 0 || acid.amount() < acidL || kerosene.amount() < kerL) return;
        ItemStack charcoal = input.getStackInSlot(CHARCOAL_SLOT);
        boolean filter = iodineCredits >= hm[0] || !charcoal.isEmpty();
        if (iodineCredits < hm[0] && !charcoal.isEmpty()) {
            input.extractItem(CHARCOAL_SLOT, 1, false);
            iodineCredits += KG_HM_PER_CHARCOAL;
        }
        if (filter) iodineCredits = Math.max(0, iodineCredits - hm[0]);
        ActivityData a = fuel.get(ActivityData.ACTIVITY.get());
        ActivityInventory act = a != null ? a.inventory(now) : new ActivityInventory(Map.of(), hm[0], 3);
        FuelLot lot = fuel.get(FuelLot.TYPE.get());
        PurexPlant.Batch r = plant.process(hm[0], comp, lot != null ? lot.u234() : 0.000054, act, filter);
        input.extractItem(slot, 1, false);
        acid.take(acidL);
        kerosene.take(kerL);
        // регенерат: взвешенная изотопия
        double m = uKg + r.uProductKg();
        if (m > 0) {
            u235 = (u235 * uKg + r.u235() * r.uProductKg()) / m;
            u236 = (u236 * uKg + r.u236() * r.uProductKg()) / m;
            u234 = (u234 * uKg + (lot != null ? lot.u234() : 0.000054) * r.uProductKg()) / m;
        }
        uKg = m;
        npO2Kg += r.npKg() * NPO2_PER_NP;
        puIso[0] = r.pu239();
        puIso[1] = r.pu240();
        puIso[2] = r.pu241();
        puIso[3] = r.pu242();
        // ВАО: сложить с накопленным (приведённым к текущему времени)
        ActivityInventory cur = hlw.decayed((now - hlwTick) * ActivityData.GAME_S_PER_TICK);
        EnumMap<Emitter, Double> sum = new EnumMap<>(cur.activities());
        r.hlw().activities().forEach((k, v) -> sum.merge(k, v, Double::sum));
        glassKg += r.glassKg();
        hlw = new ActivityInventory(sum, glassKg, 2.7);
        hlwTick = now;
        busyS = r.hmKg() / PurexPlant.CAPACITY_KG_S;
        kr85BqS = r.kr85Bq() / busyS;
        puFlowKgS = plant.puInFlowKg() / busyS;
        i131BqS = r.i131ReleasedBq() / busyS;
        lastDf = r.uProductDf();
        level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 0.6f);
        s.sendParticles(ParticleTypes.SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.01);
    }

    private boolean emitPu() {
        ItemStack p = new ItemStack(PcItems.PUO2_POWDER.get());
        p.set(PuLot.TYPE.get(), puPending);
        // Pu-241 партии: из него в выдержанном PuO₂ врастает Am-241 (12 §12.2.5); америций ОЯТ ушёл в рафинат
        double pu241Kg = puPending.puKg() * puPending.pu241();
        double pu241Bq = dev.denfry.promptcritical.sim.radiation.Emitter.PU241.lambda() * pu241Kg * 1000 / 241.06 * 6.02214076e23;
        p.set(ActivityData.ACTIVITY.get(), new ActivityData(level.getGameTime(), puPending.puKg() / 0.882, 11.5,
                java.util.Map.of(dev.denfry.promptcritical.sim.radiation.Emitter.PU241.name(), pu241Bq)));
        return emit(p);
    }

    /**
     * Брызги раствора при СЦР: импульс мощности вскипает раствор и выбрасывает часть его из аппарата (так было на
     * растворных СЦР — LA-13638, 2000). Принято около литра раствора нитрата Pu с 10 г/л: 10 г Pu-239 — 2,3·10¹⁰ Бк α,
     * и около 10¹¹ Бк β продуктов деления самой вспышки и остатка в растворе.
     */
    static final double CRITICALITY_SPILL_ALPHA_BQ = 2.3e10, CRITICALITY_SPILL_BETA_BQ = 1e11;
    static final int CRITICALITY_SPILL_RADIUS = 3;

    /** СЦР в буфере нитрата плутония: вспышка, доза всем вокруг через стены, раствор сливают в отходы. */
    private void criticality(ServerLevel s, double fissions) {
        RadiationService.burst(s, worldPosition.getCenter(), FissionSource.solutionBurst(fissions), true);
        s.sendParticles(ParticleTypes.GLOW, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, 60, 0.6, 0.6, 0.6, 0.1);
        s.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5f, 1.4f);
        for (ServerPlayer p : s.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter()) < 96 * 96))
            p.displayClientMessage(ReprocessingText.msg("criticality").withStyle(ChatFormatting.RED), false);
        // вскипевший раствор выбрасывает брызги из аппарата: плутоний и продукты деления на полу камеры
        dev.denfry.promptcritical.mod.radiation.ContaminationService.spill(s, worldPosition, CRITICALITY_SPILL_RADIUS,
                CRITICALITY_SPILL_ALPHA_BQ, CRITICALITY_SPILL_BETA_BQ);
        plant.dumpPuTank();
        setChanged();
    }

    private void scanEquipment() {
        shear = dissolver = oxalate = denitration = melter = false;
        int settlers = 0;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-REACH, -REACH, -REACH), worldPosition.offset(REACH, REACH, REACH))) {
            Block blk = level.getBlockState(p).getBlock();
            if (blk == PcBlocks.SHEARING_MACHINE.get()) shear = true;
            else if (blk == PcBlocks.DISSOLVER.get()) dissolver = true;
            else if (blk == PcBlocks.MIXER_SETTLER.get()) settlers++;
            else if (blk == PcBlocks.OXALATE_PRECIPITATOR.get()) oxalate = true;
            else if (blk == PcBlocks.DENITRATION_REACTOR.get()) denitration = true;
            else if (blk == PcBlocks.VITRIFICATION_MELTER.get()) melter = true;
        }
        extraction = settlers >= PurexFlowsheet.CYCLES;
    }

    private void setRunning(boolean run) {
        BlockState st = getBlockState();
        if (st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING) != run)
            level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, run), Block.UPDATE_CLIENTS);
    }

    private boolean hasRoom(net.minecraft.world.item.Item item) {
        ItemStack left = new ItemStack(item);
        for (int i = 0; i < output.getSlots() && !left.isEmpty(); i++) left = output.insertItem(i, left, true);
        return left.isEmpty();
    }

    private boolean emit(ItemStack stack) {
        ItemStack left = stack;
        for (int i = 0; i < output.getSlots() && !left.isEmpty(); i++) left = output.insertItem(i, left, false);
        return left.isEmpty();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return inlet;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return side == Direction.DOWN ? outputView : input;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return panelItems;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return spentFuel(stack) || stack.is(Items.CHARCOAL);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack left = stack.copy();
        for (int i = 0; i < input.getSlots() && !left.isEmpty(); i++) left = input.insertItem(i, left, false);
        int moved = stack.getCount() - left.getCount();
        if (moved == 0) return ItemInteractionResult.FAIL;
        if (!player.isCreative()) stack.shrink(moved);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        on = !on;
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        setChanged();
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return ReprocessingPanels.PUREX;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        double safe = Criticality.safePuKg(puIso[1]);
        v[ReprocessingPanels.ACID_X10] = (int) Math.round(plant.acidity() * 10);
        v[ReprocessingPanels.RED_X10] = (int) Math.round(plant.reductant() * 10);
        v[ReprocessingPanels.PU_TANK_G] = (int) Math.round(plant.puTankKg() * 1000);
        v[ReprocessingPanels.PU_SAFE_G] = (int) Math.round(safe * 1000);
        v[ReprocessingPanels.DF_LOG10] = (int) Math.round(Math.log10(Math.max(1, lastDf)) * 10);
        v[ReprocessingPanels.U_REC] = (int) Math.round(plant.ledger().recovery() * 10000);
        v[ReprocessingPanels.PU_REC] = (int) Math.round(plant.ledger().puRecovery() * 10000);
        v[ReprocessingPanels.GLASS] = (int) Math.round(glassKg);
        v[ReprocessingPanels.PUBLIC_NSV_H] = (int) Math.round(Math.min(2e9,
                RadiationService.cloudSvH((ServerLevel) level, worldPosition.getCenter().add(0, 6, 0).add(downwind(500))) * 1e9));
        v[ReprocessingPanels.FLAGS] = (on ? 1 << ReprocessingPanels.F_ON : 0) | (busyS > 0 ? 1 << ReprocessingPanels.F_RUN : 0)
                | (shear && dissolver ? 1 << ReprocessingPanels.F_HEAD : 0) | (extraction ? 1 << ReprocessingPanels.F_EXTRACT : 0)
                | (oxalate ? 1 << ReprocessingPanels.F_OXALATE : 0) | (denitration ? 1 << ReprocessingPanels.F_DENITR : 0)
                | (melter ? 1 << ReprocessingPanels.F_MELTER : 0) | (plant.puTankAboveSafe() ? 1 << ReprocessingPanels.F_PU_SAFE : 0)
                | (iodineCredits <= 0 && input.getStackInSlot(CHARCOAL_SLOT).isEmpty() ? 1 << ReprocessingPanels.F_NO_FILTER : 0);
    }

    /** Точка в {@code m} метрах по ветру от трубы. */
    private Vec3 downwind(double m) {
        var w = dev.denfry.promptcritical.mod.environment.EnvironmentService.sample((ServerLevel) level, worldPosition, 10);
        return new Vec3(-Math.sin(w.windDirRad()) * m, -6, Math.cos(w.windDirRad()) * m);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ReprocessingPanels.B_RUN -> use(player);
            case ReprocessingPanels.B_ACID_DOWN -> plant.setAcidity(plant.acidity() - 0.5);
            case ReprocessingPanels.B_ACID_UP -> plant.setAcidity(plant.acidity() + 0.5);
            case ReprocessingPanels.B_RED_DOWN -> plant.setReductant(plant.reductant() - 0.25);
            case ReprocessingPanels.B_RED_UP -> plant.setReductant(plant.reductant() + 0.25);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("in", input.serializeNBT(registries));
        tag.put("out", output.serializeNBT(registries));
        acid.save(tag, "acid");
        kerosene.save(tag, "ker");
        tag.putBoolean("on", on);
        tag.putDouble("u_kg", uKg);
        tag.putDouble("u234", u234);
        tag.putDouble("u235", u235);
        tag.putDouble("u236", u236);
        tag.putDouble("glass", glassKg);
        tag.putDouble("npo2", npO2Kg);
        tag.putDouble("iodine", iodineCredits);
        tag.putLong("hlw_tick", hlwTick);
        CompoundTag h = new CompoundTag();
        hlw.activities().forEach((k, val) -> h.putDouble(k.name(), val));
        tag.put("hlw", h);
        CompoundTag p = new CompoundTag();
        p.putDouble("kg", puPending.puKg());
        for (int i = 0; i < 4; i++) p.putDouble("iso" + i, puIso[i]);
        tag.put("pu", p);
        MutableStateTree t = new MutableStateTree();
        plant.save(new TreeStateWriter(t));
        tag.put("plant", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("in")) input.deserializeNBT(registries, tag.getCompound("in"));
        if (tag.contains("out")) output.deserializeNBT(registries, tag.getCompound("out"));
        acid.load(tag, "acid");
        kerosene.load(tag, "ker");
        on = !tag.contains("on") || tag.getBoolean("on");
        uKg = tag.getDouble("u_kg");
        u234 = tag.getDouble("u234");
        u235 = tag.getDouble("u235");
        u236 = tag.getDouble("u236");
        glassKg = tag.getDouble("glass");
        npO2Kg = tag.getDouble("npo2");
        iodineCredits = tag.getDouble("iodine");
        hlwTick = tag.getLong("hlw_tick");
        EnumMap<Emitter, Double> m = new EnumMap<>(Emitter.class);
        CompoundTag h = tag.getCompound("hlw");
        for (String k : h.getAllKeys()) {
            try {
                m.put(Emitter.valueOf(k), h.getDouble(k));
            } catch (IllegalArgumentException ignored) {
                // нуклид из другой версии — пропускаем
            }
        }
        hlw = new ActivityInventory(m, glassKg, 2.7);
        CompoundTag p = tag.getCompound("pu");
        for (int i = 0; i < 4; i++) puIso[i] = p.getDouble("iso" + i);
        puPending = new PuLot(p.getDouble("kg"), puIso[0], puIso[1], puIso[2], puIso[3]);
        if (tag.contains("plant")) plant.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("plant"))));
    }
}
