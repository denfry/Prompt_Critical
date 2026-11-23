package dev.denfry.promptcritical.mod.gas;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.gasprocessing.HeliumRecovery;
import java.util.Arrays;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Гелиевая установка ({@link HeliumRecovery}) 2×2×1: криоблок двух ступеней и адсорберы доочистки. Товарный газ (нм³)
 * и жидкий азот с воздухоразделительной установки (л) — трубами к боковым граням; гелий 99,995 % (нм³) — сверху,
 * газ без гелия обратно в сеть — снизу. Азотная фракция второй ступени сбрасывается в атмосферу.
 */
public final class HeliumRecoveryBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    /** Компрессор и детандер блока, Вт (розетка или силовой щит рядом; при недостатке установка работает медленнее). */
    public static final double POWER_W = 150e3;
    public static final int GAS_MB = 8_000, LN2_MB = 4_000, HE_MB = 4_000;
    static final double LN2_KG_L = 0.808;

    private final FeedTank feed = new FeedTank(PcFluids.SALES_GAS.source.get(), GAS_MB);
    private final FeedTank ln2 = new FeedTank(PcFluids.LIQUID_NITROGEN.source.get(), LN2_MB);
    private final IFluidHandler inlet = new MultiInlet(feed, ln2);
    private final ProductTank helium = new ProductTank(PcFluids.HELIUM.source.get(), HE_MB);
    private final ProductTank returned = new ProductTank(PcFluids.SALES_GAS.source.get(), GAS_MB);
    private final HeliumRecovery unit = new HeliumRecovery();

    public HeliumRecoveryBlockEntity(BlockPos pos, BlockState state) {
        super(GasBlockEntities.HELIUM_RECOVERY.get(), pos, state);
    }

    public HeliumRecovery unit() {
        return unit;
    }

    public ProductTank helium() {
        return helium;
    }

    public ProductTank returned() {
        return returned;
    }

    /**
     * Эффекты на клиенте (точки — art/blockbench/gas_processing/helium_recovery_unit.author.js): из продувочного
     * клапана холодного блока ползёт холодный пар, адсорберы КЦА переключаются со сбросом давления через глушитель.
     */
    private void clientTick() {
        BlockState st = getBlockState();
        if (!st.hasProperty(PcProperties.RUNNING) || !st.getValue(PcProperties.RUNNING)) return;
        dev.denfry.promptcritical.mod.fx.MachineFx fx = dev.denfry.promptcritical.mod.fx.MachineFx.of(level, worldPosition,
                st.getBlock() instanceof dev.denfry.promptcritical.mod.block.PcBlock pb ? pb.facing(st) : Direction.NORTH);
        if (fx == null) return;
        if (fx.chance(1.5)) fx.puff(fx.at(8, 32, 8), new net.minecraft.world.phys.Vec3(0, -0.01, 0), 1);
        if (fx.chance(0.2)) {
            var vent = fx.at(24.6, 30, 4.2);
            fx.puff(vent, new net.minecraft.world.phys.Vec3(0, 0.12, 0), 6);
            fx.sound(vent, dev.denfry.promptcritical.mod.sound.PcSfx.HOIST_BRAKE.get(), 0.5f, 1.3f);
        }
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s)) {
            clientTick();
            return;
        }
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        double dt = 0.05 * b.world().clock().kSlow();
        double q = Math.min(feed.amount(), Math.min(HeliumRecovery.CAPACITY_NM3_S * dt * dev.denfry.promptcritical.mod.grid.MachinePower.supply(s, worldPosition, POWER_W), returned.room()));
        unit.step(q / dt, ln2.amount() * LN2_KG_L / dt);
        double he = Math.min(unit.heliumNm3S() * dt, helium.room());
        feed.take(q);
        ln2.take(unit.ln2KgS() * dt / LN2_KG_L);
        helium.add(he);
        if (he > 0) dev.denfry.promptcritical.mod.content.SimAchievements.once(this,
                dev.denfry.promptcritical.mod.content.SimAchievements.HELIUM, "nm3", he);
        returned.add(unit.returnNm3S() * dt + (unit.heliumNm3S() * dt - he)); // не влезший гелий уходит с газом в сеть
        if (level.getGameTime() % 10 == 0) {
            BlockState st = getBlockState();
            boolean run = unit.heliumNm3S() > 0;
            if (st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING) != run)
                level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, run), Block.UPDATE_CLIENTS);
        }
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == Direction.UP) return helium;
        if (side == Direction.DOWN) return returned;
        return inlet;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(Player player) {
        unit.setOn(!unit.on());
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        setChanged();
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return HeliumPanels.HELIUM;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[HeliumPanels.T1_K] = (int) Math.round(unit.t1K());
        v[HeliumPanels.RECOVERY] = (int) Math.round(unit.recovery() * 1000);
        v[HeliumPanels.CRUDE] = (int) Math.round(unit.crudePurity() * 1000);
        v[HeliumPanels.HE_LH] = (int) Math.round(unit.heliumNm3S() * 3.6e6);
        v[HeliumPanels.LN2_KGH] = (int) Math.round(unit.ln2KgS() * 3600);
        v[HeliumPanels.TANK] = (int) Math.round(helium.fill() * 1000);
        v[HeliumPanels.FLAGS] = (unit.on() ? 1 << HeliumPanels.F_ON : 0) | (unit.heliumNm3S() > 0 ? 1 << HeliumPanels.F_RUN : 0)
                | (unit.on() && feed.amount() > 0 && unit.heliumNm3S() == 0 ? 1 << HeliumPanels.F_NO_LN2 : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == HeliumPanels.B_RUN) use(player);
        else if (id == HeliumPanels.B_T_DOWN) unit.setT1K(unit.t1K() - 5);
        else if (id == HeliumPanels.B_T_UP) unit.setT1K(unit.t1K() + 5);
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        feed.save(tag, "feed");
        ln2.save(tag, "ln2");
        helium.save(tag, "he");
        returned.save(tag, "ret");
        tag.putDouble("t1", unit.t1K());
        tag.putBoolean("on", unit.on());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        feed.load(tag, "feed");
        ln2.load(tag, "ln2");
        helium.load(tag, "he");
        returned.load(tag, "ret");
        if (tag.contains("t1")) unit.setT1K(tag.getDouble("t1"));
        unit.setOn(!tag.contains("on") || tag.getBoolean("on"));
    }
}
