package dev.denfry.promptcritical.mod.chlor;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.chlor.PvcReactor;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import dev.denfry.promptcritical.mod.sound.PcSfx;

/**
 * Автоклав суспензионной полимеризации ВХ ({@link PvcReactor}) 1×3×1 с мешалкой. Винилхлорид (л) и вода — трубами к
 * любой грани; загрузка, пуск и выгрузка идут сами по циклу: набрать {@value PvcReactor#CHARGE_KG} кг ВХ и воду,
 * пуск с инициатором, полимеризация до падения давления (≈ 6 ч игрового времени при 57 °C), сдувка остаточного ВХ
 * обратно в бак, смола — мешками по {@value ChlorUnits#PVC_BAG_KG} кг в выходной склад (воронкой снизу). Температуру —
 * она задаёт марку K — меняют между загрузками.
 */
public final class PvcReactorBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost, dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity {
    /** Мешалка автоклава и насос охлаждающей воды рубашки, Вт: без мешалки суспензия не идёт. Розетка или силовой щит рядом; при недостатке машина работает медленнее. */
    public static final double POWER_W = 75e3;
    public static final int VCM_MB = 4_000, WATER_MB = 4_000;
    public static final double GAME_S_PER_TICK = 3.6;

    private final FeedTank vcm = new FeedTank(PcFluids.VINYL_CHLORIDE.source.get(), VCM_MB);
    private final FeedTank water = new FeedTank(Fluids.WATER, WATER_MB);
    private final IFluidHandler inlet = new MultiInlet(vcm, water);
    /** Склад мешков: аппарат кладёт сам, воронка только забирает. */
    private final ItemStackHandler out = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler outView = new dev.denfry.promptcritical.mod.item.ExtractOnlyItemHandler(out);
    private final PvcReactor reactor = new PvcReactor();
    private boolean auto = true;
    private double resinKg, heatW;

    public PvcReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ChlorBlockEntities.PVC_REACTOR.get(), pos, state);
    }

    public PvcReactor reactor() {
        return reactor;
    }

    public ItemStackHandler output() {
        return out;
    }

    public FeedTank vcm() {
        return vcm;
    }

    /** Работающий автоклав (арт art/blockbench/polymers/pvc_reactor.author.js): гудит электродвигатель мешалки. */
    private void clientFx() {
        MachineFx fx = MachineFx.of(level, worldPosition, getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (fx == null || !getBlockState().getValue(PcProperties.RUNNING)) return;
        if (fx.chance(0.5)) fx.sound(real() ? fx.at(8, 120, 8) : fx.at(13.6, 30, 8), PcSfx.ELECTRIC_MOTOR.get(), 0.35f, 1.1f);
    }

    @Override
    public void tickDevice() {
        if (level != null && level.isClientSide) {
            clientFx();
            return;
        }
        if (!(level instanceof ServerLevel)) return;
        heatW = 0;
        switch (reactor.phase()) {
            case IDLE, CHARGING -> {
                if (!auto) break;
                double[] took = reactor.charge(vcm.amount() * ChlorUnits.VCM_KG_L, water.amount());
                vcm.take(took[0] / ChlorUnits.VCM_KG_L);
                water.take(took[1]);
                if (reactor.start()) level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6f, 0.6f);
            }
            case REACTING -> heatW = reactor.step(GAME_S_PER_TICK * dev.denfry.promptcritical.mod.grid.MachinePower.supply(level, worldPosition, POWER_W));
            case DONE -> {
                double[] r = reactor.discharge();
                resinKg += r[0];
                // сдувка — в рекуперацию: обратно в бак, лишнее теряется на свече
                double back = Math.min(r[1] / ChlorUnits.VCM_KG_L, VCM_MB - vcm.amount());
                vcm.fill(new net.neoforged.neoforge.fluids.FluidStack(PcFluids.VINYL_CHLORIDE.source.get(), (int) Math.floor(back)),
                        IFluidHandler.FluidAction.EXECUTE);
            }
        }
        while (resinKg >= ChlorUnits.PVC_BAG_KG) {
            ItemStack left = new ItemStack(PcItems.PVC.get());
            for (int i = 0; i < out.getSlots() && !left.isEmpty(); i++) left = out.insertItem(i, left, false);
            if (!left.isEmpty()) break;
            resinKg -= ChlorUnits.PVC_BAG_KG;
        }
        if (level.getGameTime() % 10 == 0) {
            BlockState st = getBlockState();
            boolean run = reactor.phase() == PvcReactor.Phase.REACTING;
            if (st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING) != run)
                level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, run), Block.UPDATE_CLIENTS);
        }
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing(), real() ? 3 : 1);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return inlet;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return outView;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Shift + ПКМ: автоматический цикл вкл./выкл. */
    @Override
    public InteractionResult use(Player player) {
        auto = !auto;
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        player.displayClientMessage(ChlorText.msg(auto ? "pvc.auto_on" : "pvc.auto_off"), true);
        setChanged();
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return ChlorPanels.PVC;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[ChlorPanels.P_TEMP_C] = (int) Math.round(reactor.tempK() - 273.15);
        v[ChlorPanels.P_P_X100] = (int) Math.round(reactor.pressureMpa() * 100);
        v[ChlorPanels.P_CONV_PERMIL] = (int) Math.round(reactor.conversion() * 1000);
        v[ChlorPanels.P_K] = (int) Math.round(PvcReactor.kValue(reactor.tempK()));
        v[ChlorPanels.P_TIME_MIN] = (int) Math.round(reactor.batchTimeS() / 60);
        v[ChlorPanels.P_HEAT_KW] = (int) Math.round(heatW / 1e3);
        v[ChlorPanels.P_CHARGE_PERMIL] = (int) Math.round(reactor.vcmKg() / PvcReactor.CHARGE_KG * 1000);
        v[ChlorPanels.P_PHASE] = reactor.phase().ordinal();
        v[ChlorPanels.P_FLAGS] = (auto ? 1 << ChlorPanels.PF_AUTO : 0) | (reactor.phase() == PvcReactor.Phase.REACTING ? 1 << ChlorPanels.PF_RUN : 0)
                | (reactor.phase() == PvcReactor.Phase.REACTING && reactor.conversion() > PvcReactor.PRESSURE_DROP_AT ? 1 << ChlorPanels.PF_DROP : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ChlorPanels.PB_AUTO -> use(player);
            case ChlorPanels.PB_T_DOWN -> reactor.setTempK(reactor.tempK() - 1);
            case ChlorPanels.PB_T_UP -> reactor.setTempK(reactor.tempK() + 1);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        vcm.save(tag, "vcm");
        water.save(tag, "water");
        tag.put("out", out.serializeNBT(registries));
        tag.putBoolean("auto", auto);
        tag.putDouble("resin", resinKg);
        MutableStateTree tree = new MutableStateTree();
        reactor.save(new TreeStateWriter(tree));
        tag.put("reactor", NbtStateTrees.toNbt(tree));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        vcm.load(tag, "vcm");
        water.load(tag, "water");
        if (tag.contains("out")) out.deserializeNBT(registries, tag.getCompound("out"));
        auto = !tag.contains("auto") || tag.getBoolean("auto");
        resinKg = tag.getDouble("resin");
        if (tag.contains("reactor")) reactor.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("reactor"))));
    }
}
