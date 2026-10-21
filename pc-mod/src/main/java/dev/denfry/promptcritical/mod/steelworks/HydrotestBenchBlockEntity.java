package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.PipeSpec;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Гидропресс труб {@code hydrotest_bench} 3×1×1 (08 §8.7.4): труба (остывшая, ниже 100 °C) из входа зажимается
 * заглушками, заполняется водой ({@value #WATER_MB} мБ на испытание, трубы Create) и нагружается до p_исп =
 * 0,9·p_y (API 5L) за {@value #TEST_TICKS} тиков: лопнувшая (p_f &lt; p_исп) идёт в лом ({@code steel_scrap} по
 * 100 кг), выдержавшая — в выходы с отметкой HYDRO. На воде безопасно — расплава и газа нет.
 */
public final class HydrotestBenchBlockEntity extends IronworksBlockEntity implements PanelHost {
    public static final int TEST_TICKS = 60, WATER_MB = 20, BURST_TICKS = 40;
    public static final double MAX_PIPE_K = 373.15;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.OUTPUT,
            MachineInventory.Role.OUTPUT, MachineInventory.Role.OUTPUT, MachineInventory.Role.OUTPUT};

    private final MachineInventory inv = new MachineInventory(ROLES, (slot, s) -> slot == 0 && testable(s), this::setChanged);
    private final GasBuffer water = new GasBuffer(4_000, s -> s.getFluid().is(FluidTags.WATER), this::setChanged);
    private ItemStack pipe = ItemStack.EMPTY;
    private int ticks, burstTicks, tested, burst;
    private double scrapKg, lastTestPa;
    private boolean lastBurst;

    public HydrotestBenchBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.HYDROTEST_BENCH.get(), pos, state);
    }

    static boolean testable(ItemStack s) {
        return Steelworks.isPipe(s) && s.get(SteelworksComponents.PIPE_SPEC.get()) != null;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        long now = level.getGameTime();
        ItemStack in = inv.getStackInSlot(0);
        if (pipe.isEmpty() && !in.isEmpty() && testable(in) && Steelworks.tempK(in, now) < MAX_PIPE_K && water.amount() >= WATER_MB
                && inv.outputFits(in.copyWithCount(1))) {
            water.draw(WATER_MB);
            pipe = in.split(1);
            ticks = 0;
            setChanged();
        }
        if (!pipe.isEmpty() && ++ticks >= TEST_TICKS) finish();
        if (burstTicks > 0 && --burstTicks == 0) setStateFlag(PcProperties.BURST, false);
        setStateFlag(PcProperties.RUNNING, !pipe.isEmpty());
    }

    private void finish() {
        SteelworksComponents.PipeData data = pipe.get(SteelworksComponents.PIPE_SPEC.get());
        PipeSpec.HydrotestResult r = data.spec().hydrotest();
        tested++;
        lastTestPa = data.testPressurePa();
        lastBurst = r.burst();
        if (r.burst()) {
            burst++;
            scrapKg += data.kgPerM();
            int n = Ironworks.items(scrapKg);
            if (n > 0) {
                scrapKg -= n * Ironworks.bulkKg();
                ItemStack rest = inv.output(new ItemStack(PcItems.STEEL_SCRAP.get(), n));
                if (!rest.isEmpty()) Ironworks.spill(level, worldPosition.above(), rest);
            }
            burstTicks = BURST_TICKS;
            setStateFlag(PcProperties.BURST, true);
            level.playSound(null, worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.2f, 0.7f);
        } else {
            ItemStack ok = pipe.copy();
            ok.set(SteelworksComponents.PIPE_SPEC.get(), SteelworksComponents.PipeData.of(r.pipe()));
            ItemStack rest = inv.output(ok);
            if (!rest.isEmpty()) Ironworks.spill(level, worldPosition.above(), rest);
        }
        pipe = ItemStack.EMPTY;
        ticks = 0;
        setChanged();
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.BURST)) return;
        MachineFx fx = fx();
        if (fx.chance(1)) fx.smoke(fx.at(8, 10, 8), false, 0.4);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return water.input();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return testable(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (inv.giveOutputs(player)) return InteractionResult.SUCCESS;
        player.displayClientMessage(SteelworksText.msg("hydrotest", tested, burst, water.amount()), true);
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return SteelworksPanels.HYDROTEST;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        SteelworksComponents.PipeData d = pipe.get(SteelworksComponents.PIPE_SPEC.get());
        double target = d == null ? 0 : d.testPressurePa();
        out[SteelworksPanels.H_PRESSURE_KPA] = (int) Math.round(target * Math.min(1, ticks / (double) TEST_TICKS) / 1e3);
        out[SteelworksPanels.H_TEST_KPA] = (int) Math.round((d == null ? lastTestPa : target) / 1e3);
        out[SteelworksPanels.H_TESTED] = tested;
        out[SteelworksPanels.H_BURST] = burst;
        out[SteelworksPanels.H_WATER_MB] = water.amount();
        int flags = 0;
        if (!pipe.isEmpty()) flags |= 1 << SteelworksPanels.F_RUNNING;
        if (lastBurst) flags |= 1 << SteelworksPanels.F_ALARM;
        out[SteelworksPanels.FLAGS] = flags;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inv", inv.save(registries));
        tag.put("water", water.save(registries));
        if (!pipe.isEmpty()) tag.put("pipe", pipe.save(registries));
        tag.putInt("ticks", ticks);
        tag.putInt("tested", tested);
        tag.putInt("burst", burst);
        tag.putDouble("scrap_kg", scrapKg);
        tag.putDouble("last_test_pa", lastTestPa);
        tag.putBoolean("last_burst", lastBurst);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inv")) inv.load(registries, tag.getCompound("inv"));
        if (tag.contains("water")) water.load(registries, tag.getCompound("water"));
        pipe = tag.contains("pipe") ? ItemStack.parseOptional(registries, tag.getCompound("pipe")) : ItemStack.EMPTY;
        ticks = tag.getInt("ticks");
        tested = tag.getInt("tested");
        burst = tag.getInt("burst");
        scrapKg = tag.getDouble("scrap_kg");
        lastTestPa = tag.getDouble("last_test_pa");
        lastBurst = tag.getBoolean("last_burst");
    }
}
