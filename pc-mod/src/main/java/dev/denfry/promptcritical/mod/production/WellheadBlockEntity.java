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
        return stack.is(PcItems.SEAMLESS_PIPE.get()) || stack.is(PcItems.SEISMIC_CHARGE.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
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
                : ProducingReservoir.perforate(b.prospect().trap(), b.prospect().topM());
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
        open = masterOpen && tree && sep != null && sep.hasRoom();
        double dt = 0.05 * b.world().clock().kSlow();
        well.step(dt, open, spm, ThreePhaseSeparator.STANDARD.pressurePa());
        if (open && sep != null) sep.accept(well.oilM3s() * dt, well.waterM3s() * dt, well.gasStdM3s() * dt, dt);
        if (open && sep != null && well.oilM3s() > 0)
            dev.denfry.promptcritical.mod.content.SimAchievements.once(this, dev.denfry.promptcritical.mod.content.SimAchievements.FIRST_OIL,
                    "oil_m3s", well.oilM3s());
        if (jack != null) jack.load(well.card() == null ? 0 : well.card().shaftPowerW());
        if (tree && ticks % 10 == 0 && above.getValue(BlockStateProperties.OPEN) != open)
            level.setBlock(worldPosition.above(), above.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
        if (ticks % 100 == 0) setChanged();
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
        ProducingReservoir r = ProducingReservoir.restore(b.prospect().trap(), st);
        if (r == null) return;
        well = new ProductionWell(r);
        well.setChoke64(pendingChoke);
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
        BeamPump.Card c = well.card();
        if (c != null) {
            out[ProductionPanels.W_PEAK_KN] = (int) Math.round(c.peakN() / 1000);
            for (int i = 0; i < BeamPump.CARD_POINTS; i++) {
                out[ProductionPanels.W_CARD + 2 * i] = (int) Math.round(c.positionM()[i] * 1000);
                out[ProductionPanels.W_CARD + 2 * i + 1] = (int) Math.round(c.loadN()[i] / 100);
            }
        }
        int f = 0;
        if (open) f |= 1 << ProductionPanels.WF_OPEN;
        if (c != null) f |= 1 << ProductionPanels.WF_PUMPING;
        if (tree) f |= 1 << ProductionPanels.WF_TREE;
        if (separator) f |= 1 << ProductionPanels.WF_SEPARATOR;
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
        }
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
        }
    }
}
