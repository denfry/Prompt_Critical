package dev.denfry.promptcritical.mod.production;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.drilling.DrilledWells;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.production.BeamPump;
import dev.denfry.promptcritical.sim.production.ProducingReservoir;
import dev.denfry.promptcritical.sim.production.ProductionWell;
import dev.denfry.promptcritical.sim.production.RodPumpDynamics;
import dev.denfry.promptcritical.sim.production.ThreePhaseSeparator;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Колонная головка добывающей скважины на пробуренном стволе ({@link DrilledWells}): заканчивание — спуск
 * эксплуатационной колонны (бесшовные трубы, по одной на 12-метровую трубу до забоя) и перфорация нефтенасыщенной
 * части пласта (сейсмические заряды как кумулятивные: один на метр перфоратора). Сверху ставят фонтанную арматуру
 * ({@code christmas_tree}), продукция идёт в сепаратор в {@link #SEPARATOR_RADIUS} блоках. Пока пласт фонтанирует —
 * дебит задаёт штуцер, потом нужен станок-качалка в {@link PumpjackBlockEntity#RADIUS} блоках
 * ({@link ProductionWell}). Время — медленный домен симуляции.
 */
public final class WellheadBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final double JOINT_M = 12;
    public static final int SEPARATOR_RADIUS = 8, CHOKE_STEP = 2;

    private int casingJoints;
    private boolean masterOpen = true;
    @Nullable
    private ProductionWell well;
    private double[] pendingReservoir;
    private int pendingChoke = 16;
    private boolean tree, separator, open;
    private double spm;
    private int ticks;
    private double[] pendingPump;
    /** Последний замер эхолотом: время эха, с, уровень, м, и номер замера (для события {@code pc_well_fluid_level}). */
    private double echoS, echoLevelM;
    private int echoCount, rodPartCount;
    public static final double CB_STEP_N = 2e3;
    /** Шаг закачки газлифтного газа, нм³/с; не больше шести клапанов в НКТ. */
    public static final double GL_STEP_STD_M3S = 0.05;
    public static final int MAX_VALVES = 6;
    private final java.util.List<dev.denfry.promptcritical.sim.production.GasLift.Valve> valves = new java.util.ArrayList<>();
    private double glSetStdM3s, glActualStdM3s;
    /** ППД 09 §9.4.6: нагнетательная скважина, давление закачки на устье, бак воды (вода для ППД или пластовая). */
    public static final double INJ_STEP_PA = 0.5e6, INJ_MAX_PA = 25e6, INJ_DEFAULT_PA = 5e6;
    /** Взвесь, мг/л: подготовленная вода для ППД и неочищенная пластовая [BALANCE]. */
    public static final double TSS_INJECTION_WATER = 2, TSS_PRODUCED_WATER = 50;
    public static final int WATER_MB = 16_000;
    private boolean injector;
    private double injSetPa = INJ_DEFAULT_PA;
    private double pendingSkin = Double.NaN;
    private final net.neoforged.neoforge.fluids.capability.templates.FluidTank water = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(
            WATER_MB, fs -> fs.is(dev.denfry.promptcritical.mod.registry.PcFluids.INJECTION_WATER.source.get())
                    || fs.is(dev.denfry.promptcritical.mod.registry.PcFluids.PRODUCED_WATER.source.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    public boolean injector() {
        return injector;
    }

    /** Перевести под закачку или обратно в добывающие. */
    public void setInjector(boolean on) {
        injector = on;
        setChanged();
    }

    public void setInjectionPa(double pa) {
        injSetPa = Math.clamp(pa, 0, INJ_MAX_PA);
        setChanged();
    }

    public net.neoforged.neoforge.fluids.capability.templates.FluidTank waterTank() {
        return water;
    }

    @Override
    @Nullable
    public net.neoforged.neoforge.fluids.capability.IFluidHandler fluids(@Nullable Direction side) {
        return water;
    }

    public WellheadBlockEntity(BlockPos pos, BlockState state) {
        super(ProductionBlockEntities.WELLHEAD.get(), pos, state);
    }

    @Nullable
    public ProductionWell well() {
        return well;
    }

    public boolean open() {
        return open;
    }

    @Nullable
    private DrilledWells.Bore bore() {
        return level instanceof ServerLevel s ? DrilledWells.of(s).get(worldPosition.getX(), worldPosition.getZ()) : null;
    }

    public int jointsNeeded() {
        DrilledWells.Bore b = bore();
        return b == null ? 0 : (int) Math.ceil(b.depthM() / JOINT_M);
    }

    public boolean cased() {
        return casingJoints > 0 && casingJoints >= jointsNeeded();
    }

    // --- заканчивание ---

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.SEAMLESS_PIPE.get()) || stack.is(PcItems.SEISMIC_CHARGE.get()) || stack.is(PcItems.SUCKER_ROD.get())
                || stack.is(PcItems.DOWNHOLE_ROD_PUMP.get()) || stack.is(com.simibubi.create.AllBlocks.LARGE_COGWHEEL.get().asItem())
                || stack.is(PcItems.GAS_LIFT_VALVE.get());
    }

    /** Газлифтный клапан — в НКТ; с Shift — подъём НКТ: все клапаны обратно игроку. */
    private ItemInteractionResult installValve(Player player, ItemStack stack) {
        if (player.isShiftKeyDown()) {
            int n = valves.size();
            for (var v : valves) {
                ItemStack back = new ItemStack(PcItems.GAS_LIFT_VALVE.get());
                back.set(dev.denfry.promptcritical.mod.registry.PcDataComponents.GAS_LIFT_VALVE.get(),
                        new GasLiftValveItem.Data((int) v.depthM(), (int) Math.round(v.ptroPa() / 1000)));
                if (!player.getInventory().add(back)) player.drop(back, false);
            }
            valves.clear();
            if (well != null) well.setGasLiftValves(valves);
            player.displayClientMessage(ProductionText.msg("valves_pulled", n), true);
            setChanged();
            return ItemInteractionResult.SUCCESS;
        }
        if (valves.size() >= MAX_VALVES) {
            player.displayClientMessage(ProductionText.msg("valve_full", MAX_VALVES), true);
            return ItemInteractionResult.CONSUME;
        }
        GasLiftValveItem.Data d = GasLiftValveItem.data(stack);
        valves.add(new dev.denfry.promptcritical.sim.production.GasLift.Valve(d.depthM(), d.ptroKpa() * 1000.0));
        if (well != null) well.setGasLiftValves(valves);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(ProductionText.msg("valve_installed", valves.size(), d.depthM(),
                ProductionText.num(d.ptroKpa() / 1000.0, 1)), true);
        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 1.2f);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    public java.util.List<dev.denfry.promptcritical.sim.production.GasLift.Valve> gasLiftValves() {
        return java.util.List.copyOf(valves);
    }

    /** Уставка закачки газлифтного газа, нм³/с. */
    public void setGasLiftStdM3s(double q) {
        glSetStdM3s = Math.clamp(q, 0, dev.denfry.promptcritical.sim.production.GasLift.MAX_INJECTION_STD_M3S);
        setChanged();
    }

    public double gasLiftSetStdM3s() {
        return glSetStdM3s;
    }

    /** Ремонт ШГН (09 §9.6.5): штанга — после обрыва, насос — новый клапан, большая шестерня — зубья редуктора. */
    private ItemInteractionResult repair(Player player, ItemStack stack) {
        if (well == null) return ItemInteractionResult.CONSUME;
        String done = null;
        if (stack.is(PcItems.SUCKER_ROD.get()) && !Double.isNaN(well.rodPartDepthM())) {
            well.repairRods();
            done = "rods_fixed";
        } else if (stack.is(PcItems.DOWNHOLE_ROD_PUMP.get())) {
            well.replacePump();
            done = "pump_replaced";
        } else if (stack.is(com.simibubi.create.AllBlocks.LARGE_COGWHEEL.get().asItem()) && well.gearboxBroken()) {
            well.repairGearbox();
            done = "gearbox_fixed";
        }
        if (done == null) return ItemInteractionResult.CONSUME;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
        player.displayClientMessage(ProductionText.msg(done), true);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(PcItems.GAS_LIFT_VALVE.get())) return installValve(player, stack);
        if (stack.is(PcItems.SUCKER_ROD.get()) || stack.is(PcItems.DOWNHOLE_ROD_PUMP.get())
                || stack.is(com.simibubi.create.AllBlocks.LARGE_COGWHEEL.get().asItem())) return repair(player, stack);
        DrilledWells.Bore b = bore();
        if (b == null) {
            player.displayClientMessage(ProductionText.msg("no_bore"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (stack.is(PcItems.SEAMLESS_PIPE.get())) {
            int need = jointsNeeded() - casingJoints;
            int n = Math.min(need, stack.getCount());
            if (n > 0) {
                casingJoints += n;
                if (!player.getAbilities().instabuild) stack.shrink(n);
                level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 0.5f);
                setChanged();
            }
            player.displayClientMessage(ProductionText.msg("cased", casingJoints, jointsNeeded()), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (well != null) return ItemInteractionResult.CONSUME;
        if (!cased()) {
            player.displayClientMessage(ProductionText.msg("needs_casing", jointsNeeded() - casingJoints), true);
            return ItemInteractionResult.CONSUME;
        }
        ProducingReservoir r = b.prospect() == null || b.depthM() <= b.prospect().topM() ? null
                : ProducingReservoir.perforate(b.prospect().trap(), b.prospect().topM(),
                        OilFields.of((ServerLevel) level).attach((ServerLevel) level, b.prospect().trap(), worldPosition, true, null));
        if (r == null) {
            player.displayClientMessage(ProductionText.msg("no_oil"), true);
            return ItemInteractionResult.CONSUME;
        }
        int charges = (int) Math.ceil(r.perforatedM());
        if (!player.getAbilities().instabuild && stack.getCount() < charges) {
            player.displayClientMessage(ProductionText.msg("needs_charges", charges), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) stack.shrink(charges);
        well = new ProductionWell(r);
        well.setGasLiftValves(valves);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.3f, 1.6f);
        player.displayClientMessage(ProductionText.msg("perforated", ProductionText.num(r.perforatedM(), 1),
                ProductionText.num(r.perfTopM(), 0), ProductionText.num(r.pressurePa() / 1e6, 2)), true);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    // --- добыча ---

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        if (pendingReservoir != null) restore();
        if (well == null) return;
        ticks++;
        BlockState above = level.getBlockState(worldPosition.above());
        tree = above.is(PcBlocks.CHRISTMAS_TREE.get());
        SeparatorBlockEntity sep = nearest(SeparatorBlockEntity.class, SEPARATOR_RADIUS);
        separator = sep != null;
        PumpjackBlockEntity jack = nearest(PumpjackBlockEntity.class, PumpjackBlockEntity.RADIUS);
        spm = jack == null ? 0 : jack.spm();
        double dt = 0.05 * b.world().clock().kSlow();
        if (injector) {
            // нагнетательная: сепаратор не нужен, вода — из бака устья
            open = masterOpen && tree;
            boolean produced = water.getFluid().is(dev.denfry.promptcritical.mod.registry.PcFluids.PRODUCED_WATER.source.get());
            double rho = produced ? dev.denfry.promptcritical.sim.production.ProducingReservoir.WATER_KGM3 : 1010;
            double v = well.inject(dt, open, injSetPa, rho, produced ? TSS_PRODUCED_WATER : TSS_INJECTION_WATER, water.getFluidAmount() / 1000.0);
            int mb = (int) Math.ceil(v * 1000 - 1e-9);
            if (mb > 0) water.drain(Math.min(mb, water.getFluidAmount()), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            if (tree && ticks % 10 == 0 && above.getValue(BlockStateProperties.OPEN) != open)
                level.setBlock(worldPosition.above(), above.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
            if (ticks % 100 == 0) setChanged();
            return;
        }
        open = masterOpen && tree && sep != null && sep.hasRoom();
        // газлифтный газ — из газа сепаратора (замкнутый цикл: возвращается с продукцией)
        glActualStdM3s = 0;
        if (glSetStdM3s > 0 && sep != null && !valves.isEmpty() && spm <= 0) {
            double want = glSetStdM3s * dt;
            double got = sep.gas().drain((int) Math.floor(Math.min(want, sep.gas().amount())),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).getAmount();
            glActualStdM3s = got / dt;
        }
        well.setGasInjectionStdM3s(glActualStdM3s);
        well.step(dt, open, spm, ThreePhaseSeparator.STANDARD.pressurePa());
        if (open && sep != null) sep.accept(well.oilM3s() * dt, well.waterM3s() * dt, well.gasStdM3s() * dt, dt);
        if (open && sep != null && well.oilM3s() > 0)
            dev.denfry.promptcritical.mod.content.SimAchievements.once(this, dev.denfry.promptcritical.mod.content.SimAchievements.FIRST_OIL,
                    "oil_m3s", well.oilM3s());
        if (jack != null) jack.load(shaftLoadW());
        if (well.takeRodPartEvent()) {
            rodPartCount++;
            Player p = s.getNearestPlayer(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 32, false);
            if (p != null) p.displayClientMessage(ProductionText.msg("rod_part", ProductionText.num(well.rodPartDepthM(), 0))
                    .withStyle(ChatFormatting.RED), false);
            level.playSound(null, worldPosition, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
        }
        if (tree && ticks % 10 == 0 && above.getValue(BlockStateProperties.OPEN) != open)
            level.setBlock(worldPosition.above(), above.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
        if (ticks % 100 == 0) setChanged();
    }

    /**
     * Нагрузка на вал качалки, Вт: редуктор должен дать пиковый момент цикла — у неуравновешенной качалки он
     * вдвое-втрое выше (09 §9.6.5); КПД редуктора 0,9.
     */
    private double shaftLoadW() {
        RodPumpDynamics.DynoCard c = well.card();
        if (c == null || spm <= 0) return 0;
        return Math.max(c.rodPowerW(), well.peakTorqueNm() * spm * 2 * Math.PI / 60) / 0.9;
    }

    /** Замер эхолотом (09 §9.6.5); {@code false} — скважина не на насосе. */
    public boolean echo() {
        if (well == null || well.mode() != ProductionWell.Mode.PUMPING) return false;
        double casing = ThreePhaseSeparator.STANDARD.pressurePa();
        echoLevelM = well.fluidLevelM(casing);
        echoS = well.echoTimeS(casing);
        echoCount++;
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 1.0f, 0.5f);
        setChanged();
        return true;
    }

    public double echoLevelM() {
        return echoLevelM;
    }

    public double echoTimeS() {
        return echoS;
    }

    public int echoCount() {
        return echoCount;
    }

    public int rodPartCount() {
        return rodPartCount;
    }

    @Nullable
    private <T extends net.minecraft.world.level.block.entity.BlockEntity> T nearest(Class<T> cls, double radius) {
        List<T> all = Ironworks.blockEntities(level, new AABB(worldPosition).inflate(radius), cls);
        T best = null;
        double bestD = Double.MAX_VALUE;
        for (T t : all) {
            double d = t.getBlockPos().distSqr(worldPosition);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    private void restore() {
        DrilledWells.Bore b = bore();
        double[] st = pendingReservoir;
        pendingReservoir = null;
        if (b == null || b.prospect() == null) return;
        // залежь общая для всех скважин ловушки: сохранённое здесь состояние — только для залежи, которой нет в журнале
        ProducingReservoir r = st == null || st.length != 5 ? null : ProducingReservoir.perforate(b.prospect().trap(), st[4],
                OilFields.of((ServerLevel) level).attach((ServerLevel) level, b.prospect().trap(), worldPosition, false, st));
        if (r == null) return;
        well = new ProductionWell(r);
        well.setChoke64(pendingChoke);
        well.setGasLiftValves(valves);
        well.restorePumpState(pendingPump);
        if (!Double.isNaN(pendingSkin)) well.setInjectionSkin(pendingSkin);
        pendingPump = null;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public InteractionResult use(Player player) {
        if (well == null) {
            DrilledWells.Bore b = bore();
            ProducingReservoir r = b == null || b.prospect() == null || b.depthM() <= b.prospect().topM() ? null
                    : ProducingReservoir.perforate(b.prospect().trap(), b.prospect().topM());
            player.displayClientMessage(b == null ? ProductionText.msg("no_bore")
                    : !cased() ? ProductionText.msg("needs_casing", jointsNeeded() - casingJoints)
                    : r == null ? ProductionText.msg("no_oil") : ProductionText.msg("needs_charges", (int) Math.ceil(r.perforatedM())), true);
            return InteractionResult.SUCCESS;
        }
        var mode = !tree ? ProductionText.msg("missing_tree") : !separator ? ProductionText.msg("missing_separator")
                : ProductionText.msg("mode." + well.mode().name().toLowerCase(Locale.ROOT));
        player.displayClientMessage(ProductionText.msg("status", mode, ProductionText.num(well.oilM3s() * 86_400, 1),
                ProductionText.num(well.reservoir().waterCut() * 100, 0), ProductionText.num(well.reservoir().gasOilRatio(), 0),
                ProductionText.num(well.wellheadPa() / 1e6, 2), ProductionText.num(well.reservoir().pressurePa() / 1e6, 2))
                .withStyle(open ? ChatFormatting.WHITE : ChatFormatting.GRAY), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (well == null) return;
        switch (id) {
            case ProductionPanels.WB_CHOKE_DOWN -> well.setChoke64(well.choke64() - CHOKE_STEP);
            case ProductionPanels.WB_CHOKE_UP -> well.setChoke64(well.choke64() + CHOKE_STEP);
            case ProductionPanels.WB_VALVE -> masterOpen = !masterOpen;
            case ProductionPanels.WB_INJECT -> {
                setInjector(!injector);
                player.displayClientMessage(injector ? ProductionText.msg("injector_on", ProductionText.num(injSetPa / 1e6, 1))
                        : ProductionText.msg("injector_off"), true);
            }
            case ProductionPanels.WB_INJ_DOWN -> setInjectionPa(injSetPa - INJ_STEP_PA);
            case ProductionPanels.WB_INJ_UP -> setInjectionPa(injSetPa + INJ_STEP_PA);
            case ProductionPanels.WB_GL_DOWN -> setGasLiftStdM3s(glSetStdM3s - GL_STEP_STD_M3S);
            case ProductionPanels.WB_GL_UP -> setGasLiftStdM3s(glSetStdM3s + GL_STEP_STD_M3S);
            case ProductionPanels.WB_CB_DOWN -> well.setCounterbalanceN(well.counterbalanceN() - CB_STEP_N);
            case ProductionPanels.WB_CB_UP -> well.setCounterbalanceN(well.counterbalanceN() + CB_STEP_N);
            case ProductionPanels.WB_ECHO -> {
                if (echo()) player.displayClientMessage(ProductionText.msg("echo", ProductionText.num(echoS, 2),
                        ProductionText.num(echoLevelM, 0)), true);
            }
            default -> {
            }
        }
        setChanged();
    }

    public void setMasterOpen(boolean v) {
        masterOpen = v;
    }

    @Override
    public PanelLayout panelLayout() {
        return ProductionPanels.WELLHEAD;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        if (well == null) return;
        out[ProductionPanels.W_THP_X100] = (int) Math.round(well.wellheadPa() / 1e4);
        out[ProductionPanels.W_BHP_X100] = (int) Math.round(well.bottomholePa() / 1e4);
        out[ProductionPanels.W_PR_X100] = (int) Math.round(well.reservoir().pressurePa() / 1e4);
        out[ProductionPanels.W_OIL_M3D10] = (int) Math.round(well.oilM3s() * 86_400 * 10);
        out[ProductionPanels.W_GOR] = (int) Math.round(well.reservoir().gasOilRatio());
        out[ProductionPanels.W_WC_PERMIL] = (int) Math.round(well.reservoir().waterCut() * 1000);
        out[ProductionPanels.W_CHOKE] = well.choke64();
        out[ProductionPanels.W_MODE] = well.mode().ordinal();
        out[ProductionPanels.W_FILL_PERMIL] = (int) Math.round(well.fillage() * 1000);
        out[ProductionPanels.W_SPM10] = (int) Math.round(spm * 10);
        RodPumpDynamics.DynoCard c = well.card();
        if (c != null) {
            out[ProductionPanels.W_PEAK_KN] = (int) Math.round(c.peakN() / 1000);
            for (int i = 0; i < BeamPump.CARD_POINTS; i++) {
                int j = i * RodPumpDynamics.POINTS / BeamPump.CARD_POINTS;
                out[ProductionPanels.W_CARD + 2 * i] = (int) Math.round(c.positionM()[j] * 1000);
                out[ProductionPanels.W_CARD + 2 * i + 1] = (int) Math.round(c.loadN()[j] / 100);
            }
        }
        out[ProductionPanels.W_CB_KN10] = (int) Math.round(well.counterbalanceN() / 100);
        out[ProductionPanels.W_TORQUE_KNM10] = (int) Math.round(well.peakTorqueNm() / 100);
        out[ProductionPanels.W_GOODMAN_PCT] = (int) Math.round(well.goodmanRatio() * 100);
        out[ProductionPanels.W_LEVEL_M] = (int) Math.round(echoLevelM);
        out[ProductionPanels.W_ECHO_MS] = (int) Math.round(echoS * 1000);
        out[ProductionPanels.W_LEAK_PCT] = (int) Math.round(well.valveLeak() * 100);
        out[ProductionPanels.W_GAS_PCT] = (int) Math.round(well.gasFraction() * 100);
        var gl = well.gasLiftPoint();
        boolean lifting = well.mode() == ProductionWell.Mode.GAS_LIFT;
        out[ProductionPanels.W_GL_INJ_X100] = (int) Math.round((lifting ? glActualStdM3s : glSetStdM3s) * 100);
        out[ProductionPanels.W_GL_VALVES] = valves.size();
        out[ProductionPanels.W_GL_DEPTH_M] = lifting ? (int) Math.round(gl.injDepthM()) : 0;
        out[ProductionPanels.W_GL_CSG_X100] = lifting ? (int) Math.round(gl.casingSurfacePa() / 1e4) : 0;
        int f = 0;
        if (open) f |= 1 << ProductionPanels.WF_OPEN;
        if (c != null) f |= 1 << ProductionPanels.WF_PUMPING;
        if (tree) f |= 1 << ProductionPanels.WF_TREE;
        if (separator) f |= 1 << ProductionPanels.WF_SEPARATOR;
        if (!Double.isNaN(well.rodPartDepthM())) f |= 1 << ProductionPanels.WF_ROD_PART;
        if (well.gearboxBroken()) f |= 1 << ProductionPanels.WF_GEARBOX;
        if (well.mode() == ProductionWell.Mode.GAS_LIFT) f |= 1 << ProductionPanels.WF_GAS_LIFT;
        if (injector) f |= 1 << ProductionPanels.WF_INJECTOR;
        if (injector && well.fracturing()) f |= 1 << ProductionPanels.WF_FRACTURING;
        out[ProductionPanels.W_INJ_SET_X10] = (int) Math.round(injSetPa / 1e5);
        out[ProductionPanels.W_INJ_M3D10] = (int) Math.round(well.injectionM3s() * 86_400 * 10);
        out[ProductionPanels.W_VRR_X100] = (int) Math.round(well.reservoir().voidageReplacementRatio() * 100);
        out[ProductionPanels.W_SKIN_X10] = (int) Math.round(well.injectionSkin() * 10);
        out[ProductionPanels.W_WATER_PERMIL] = (int) Math.round(1000.0 * water.getFluidAmount() / WATER_MB);
        if (well.mode() == ProductionWell.Mode.GAS_LIFT && well.gasLiftPoint().stuckHigh()) f |= 1 << ProductionPanels.WF_GL_STUCK;
        out[ProductionPanels.W_FLAGS] = f;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("casing", casingJoints);
        tag.putBoolean("master", masterOpen);
        if (well != null) {
            double[] st = well.reservoir().exportState();
            long[] bits = new long[st.length];
            for (int i = 0; i < st.length; i++) bits[i] = Double.doubleToRawLongBits(st[i]);
            tag.putLongArray("reservoir", bits);
            tag.putInt("choke", well.choke64());
            tag.putLongArray("pump", toBits(well.exportPumpState()));
        }
        tag.putDouble("echo_s", echoS);
        tag.putDouble("gl_set", glSetStdM3s);
        tag.putBoolean("injector", injector);
        tag.putDouble("inj_pa", injSetPa);
        tag.put("water", water.writeToNBT(registries, new CompoundTag()));
        if (well != null) tag.putDouble("inj_skin", well.injectionSkin());
        long[] vb = new long[2 * valves.size()];
        for (int i = 0; i < valves.size(); i++) {
            vb[2 * i] = Double.doubleToRawLongBits(valves.get(i).depthM());
            vb[2 * i + 1] = Double.doubleToRawLongBits(valves.get(i).ptroPa());
        }
        tag.putLongArray("gl_valves", vb);
        tag.putDouble("echo_m", echoLevelM);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        casingJoints = tag.getInt("casing");
        masterOpen = !tag.contains("master") || tag.getBoolean("master");
        if (tag.contains("reservoir")) {
            long[] bits = tag.getLongArray("reservoir");
            pendingReservoir = new double[bits.length];
            for (int i = 0; i < bits.length; i++) pendingReservoir[i] = Double.longBitsToDouble(bits[i]);
            pendingChoke = tag.contains("choke") ? tag.getInt("choke") : 16;
            if (tag.contains("pump")) {
                long[] pb = tag.getLongArray("pump");
                pendingPump = new double[pb.length];
                for (int i = 0; i < pb.length; i++) pendingPump[i] = Double.longBitsToDouble(pb[i]);
            }
        }
        echoS = tag.getDouble("echo_s");
        glSetStdM3s = tag.getDouble("gl_set");
        injector = tag.getBoolean("injector");
        injSetPa = tag.contains("inj_pa") ? tag.getDouble("inj_pa") : INJ_DEFAULT_PA;
        if (tag.contains("water")) water.readFromNBT(registries, tag.getCompound("water"));
        pendingSkin = tag.contains("inj_skin") ? tag.getDouble("inj_skin") : Double.NaN;
        valves.clear();
        long[] vb = tag.getLongArray("gl_valves");
        for (int i = 0; i + 1 < vb.length && valves.size() < MAX_VALVES; i += 2)
            valves.add(new dev.denfry.promptcritical.sim.production.GasLift.Valve(Double.longBitsToDouble(vb[i]), Double.longBitsToDouble(vb[i + 1])));
        echoLevelM = tag.getDouble("echo_m");
    }

    private static long[] toBits(double[] v) {
        long[] out = new long[v.length];
        for (int i = 0; i < v.length; i++) out[i] = Double.doubleToRawLongBits(v[i]);
        return out;
    }
}
