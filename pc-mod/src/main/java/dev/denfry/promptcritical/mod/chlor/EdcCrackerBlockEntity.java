package dev.denfry.promptcritical.mod.chlor;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
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
import dev.denfry.promptcritical.sim.chlor.EdcCracker;
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
import dev.denfry.promptcritical.mod.fx.MachineFx;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Печь пиролиза дихлорэтана ({@link EdcCracker}) с закалкой и колоннами HCl и ВХ. ЭДХ и топливный газ — трубами к
 * боковым граням; винилхлорид (жидкий, л) — снизу, хлористый водород (нм³) — сверху, его ведут на оксихлорирование.
 * Непрореагировавший ЭДХ возвращается в змеевик сам. Кокс копится; когда змеевик зарастёт, печь встаёт на выжиг
 * (Shift + ПКМ на остановленной печи).
 */
public final class EdcCrackerBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    /** Насосы дихлорэтана, закалки и колонн HCl и ВХ, Вт. Розетка или силовой щит рядом; при недостатке установка работает медленнее. */
    public static final double POWER_W = 55e3;
    public static final int LIQ_MB = 8_000, GAS_MB = 4_000;
    /** Подача в змеевик (свежий ЭДХ и рецикл), моль/с [BALANCE]. */
    public static final double COIL_MOL_S = 8;
    static final double FUEL_J_NM3 = 36e6;

    private final FeedTank edcIn = new FeedTank(PcFluids.ETHYLENE_DICHLORIDE.source.get(), LIQ_MB);
    private final FeedTank fuel = new FeedTank(PcFluids.SALES_GAS.source.get(), GAS_MB);
    private final IFluidHandler inlet = new MultiInlet(edcIn, fuel);
    private final ProductTank vcm = new ProductTank(PcFluids.VINYL_CHLORIDE.source.get(), LIQ_MB);
    private final ProductTank hcl = new ProductTank(PcFluids.HYDROGEN_CHLORIDE.source.get(), GAS_MB);
    private final EdcCracker cracker = new EdcCracker();
    /** Рецикл ЭДХ из колонн, моль. */
    private double recycleMol;

    public EdcCrackerBlockEntity(BlockPos pos, BlockState state) {
        super(ChlorBlockEntities.EDC_CRACKER.get(), pos, state);
    }

    public EdcCracker cracker() {
        return cracker;
    }

    public ProductTank vcm() {
        return vcm;
    }

    public ProductTank hcl() {
        return hcl;
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
        double freshMol = edcIn.amount() * ChlorUnits.EDC_KG_L / ChlorUnits.EDC_KG_MOL;
        double power = dev.denfry.promptcritical.mod.grid.MachinePower.supply(s, worldPosition, recycleMol + freshMol > 0 ? POWER_W : 0);
        double feed = Math.min(COIL_MOL_S * dt * power, recycleMol + freshMol);
        // топливо и место под продукты
        double x = EdcCracker.conversion(cracker.cotK());
        double vcmRoomMol = vcm.room() * ChlorUnits.VCM_KG_L / ChlorUnits.VCM_KG_MOL, hclRoomMol = hcl.room() / ChlorUnits.NM3_PER_MOL;
        if (x > 0) feed = Math.min(feed, Math.min(vcmRoomMol, hclRoomMol) / x);
        double fuelPerMol = (EdcCracker.SENSIBLE_J_MOL + x * EdcCracker.HEAT_J_MOL) / EdcCracker.FURNACE_EFF;
        feed = Math.min(feed, fuel.amount() * FUEL_J_NM3 / fuelPerMol);
        cracker.step(dt, feed / dt);
        if (cracker.crackedMolS() > 0) {
            double fromRecycle = Math.min(recycleMol, feed);
            recycleMol -= fromRecycle;
            edcIn.take((feed - fromRecycle) * ChlorUnits.EDC_KG_MOL / ChlorUnits.EDC_KG_L);
            recycleMol += (feed - cracker.crackedMolS() * dt);
            fuel.take(cracker.fuelW() * dt / FUEL_J_NM3);
            vcm.add(cracker.vcmMolS() * dt * ChlorUnits.VCM_KG_MOL / ChlorUnits.VCM_KG_L);
            hcl.add(ChlorUnits.molToNm3(cracker.hclMolS() * dt));
        }
        if (level.getGameTime() % 10 == 0) {
            BlockState st = getBlockState();
            boolean lit = cracker.crackedMolS() > 0, alarm = cracker.cokeMm() >= EdcCracker.COKE_LIMIT_MM;
            if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT) != lit)
                level.setBlock(worldPosition, st = st.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
            if (st.hasProperty(PcProperties.ALARM) && st.getValue(PcProperties.ALARM) != alarm)
                level.setBlock(worldPosition, st.setValue(PcProperties.ALARM, alarm), Block.UPDATE_CLIENTS);
        }
        if (level.getGameTime() % 100 == 0) setChanged();
    }

    /**
     * Печь горит: дымок над трубой, пламя в гляделке северной стены; гул горелок — MachineSoundscape. Точки —
     * art/blockbench/chlor_alkali/edc_cracker.author.js.
     */
    private void clientTick() {
        BlockState st = getBlockState();
        if (!st.hasProperty(BlockStateProperties.LIT) || !st.getValue(BlockStateProperties.LIT)) return;
        MachineFx fx = MachineFx.of(level, worldPosition, st.getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (fx == null) return;
        if (fx.chance(3)) fx.smoke(fx.at(8, 32.3, 8), false, 0.15);
        if (fx.chance(1)) fx.flame(fx.at(8, 7, 1.2), 0.08);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == Direction.DOWN) return vcm;
        if (side == Direction.UP) return hcl;
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

    /** Shift + ПКМ: пуск или останов; на остановленной печи с коксом — выжиг. */
    @Override
    public InteractionResult use(Player player) {
        if (!cracker.on() && cracker.cokeMm() > 0.5) {
            cracker.decoke();
            player.displayClientMessage(ChlorText.msg("cracker.decoked"), true);
        } else {
            cracker.setOn(!cracker.on());
            player.displayClientMessage(ChlorText.msg(cracker.on() ? "cracker.on" : "cracker.off"), true);
        }
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6f, 0.8f);
        setChanged();
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return ChlorPanels.CRACKER;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[ChlorPanels.K_COT_C] = (int) Math.round(cracker.cotK() - 273.15);
        out[ChlorPanels.K_CONV_PERMIL] = (int) Math.round(EdcCracker.conversion(cracker.cotK()) * 1000);
        out[ChlorPanels.K_SEL_PERMIL] = (int) Math.round(EdcCracker.selectivity(EdcCracker.conversion(cracker.cotK())) * 1000);
        out[ChlorPanels.K_COKE_X100] = (int) Math.round(cracker.cokeMm() * 100);
        out[ChlorPanels.K_VCM_KGH] = (int) Math.round(cracker.vcmMolS() * ChlorUnits.VCM_KG_MOL * 3600);
        out[ChlorPanels.K_FUEL_KW] = (int) Math.round(cracker.fuelW() / 1e3);
        out[ChlorPanels.K_FLAGS] = (cracker.crackedMolS() > 0 ? 1 << ChlorPanels.KF_RUN : 0)
                | (cracker.cokeMm() >= EdcCracker.COKE_LIMIT_MM ? 1 << ChlorPanels.KF_COKED : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ChlorPanels.KB_RUN -> use(player);
            case ChlorPanels.KB_T_DOWN -> cracker.setCotK(cracker.cotK() - 5);
            case ChlorPanels.KB_T_UP -> cracker.setCotK(cracker.cotK() + 5);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        edcIn.save(tag, "edc");
        fuel.save(tag, "fuel");
        vcm.save(tag, "vcm");
        hcl.save(tag, "hcl");
        tag.putDouble("recycle", recycleMol);
        MutableStateTree tree = new MutableStateTree();
        cracker.save(new TreeStateWriter(tree));
        tag.put("cracker", NbtStateTrees.toNbt(tree));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        edcIn.load(tag, "edc");
        fuel.load(tag, "fuel");
        vcm.load(tag, "vcm");
        hcl.load(tag, "hcl");
        recycleMol = tag.getDouble("recycle");
        if (tag.contains("cracker")) cracker.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("cracker"))));
    }
}
