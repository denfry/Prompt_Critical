package dev.denfry.promptcritical.mod.hydrogen;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.hydrogen.HydrogenGas;
import dev.denfry.promptcritical.sim.hydrogen.HydrogenStorage;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Баллонная рампа 200 бар или газгольдер-ресивер ({@link HydrogenStorage}): газ приходит и уходит по газовым линиям
 * ({@link GasLines}) — компрессор и топливный элемент сами берут и отдают. Две рампы (или два газгольдера), связанные
 * линией, выравнивают давление. Сброс — на свечу (порт сверху); ПКМ — щит, Shift + ПКМ — заменить разорванную
 * мембрану (если давление сброшено).
 */
public class H2StorageBlockEntity extends DeviceBlockEntity implements H2Machine, FoundryDevice, PanelHost, WholeModelHost {
    /** Сечение линии между сосудами, м² (труба DN 15). */
    static final double LINE_M2 = 1.8e-4;
    static final double AMBIENT_K = 293.15;
    private HydrogenStorage gas;
    private int ticks;
    /** Давление для стрелки манометра на клиенте, бар. */
    private float shownBar;
    private float lastSentBar = -1;

    public H2StorageBlockEntity(BlockPos pos, BlockState state) {
        super(HydrogenBlockEntities.H2_STORAGE.get(), pos, state);
    }

    public boolean tubeBank() {
        return getBlockState().is(PcBlocks.H2_TUBE_BANK_REAL.get());
    }

    @Override
    public HydrogenStorage storage() {
        if (gas == null) gas = new HydrogenStorage(tubeBank() ? HydrogenStorage.TUBE_BANK : HydrogenStorage.GASHOLDER);
        return gas;
    }

    public float shownBar() {
        return shownBar;
    }

    @Override
    public void tickDevice() {
        ticks++;
        if (!(level instanceof ServerLevel sl)) return;
        HydrogenStorage g = storage();
        double dt = 0.05;
        // выравнивание с другим сосудом на линии: течёт от большего давления, считает сторона с большим
        if (ticks % 20 == 3) {
            for (int port : tubeBank() ? new int[] {0} : new int[] {0, 1}) {
                for (GasLines.End e : GasLines.trace(sl, this, port)) {
                    HydrogenStorage other = e.machine().storage();
                    if (other == null || other == g || other.pressurePa() >= g.pressurePa()) continue;
                    // за секунду не больше, чем до равного давления (доли по объёмам сосудов)
                    double mol = HydrogenGas.orificeMolS(LINE_M2, g.pressurePa(), other.pressurePa(), g.tempK()) * 1.0;
                    double vg = g.spec().volumeM3(), vo = other.spec().volumeM3();
                    double toEqual = g.mol() - (g.mol() + other.mol()) * vg / (vg + vo);
                    double moved = g.take(Math.max(0, Math.min(mol, toEqual)), other.pressurePa());
                    other.add(moved, g.tempK());
                }
            }
        }
        g.step(dt, AMBIENT_K);
        if (g.ventMolS() > 0 && ticks % 4 == 0) {
            BlockPos v = portCell(tubeBank() ? 1 : 2).relative(Direction.UP);
            sl.sendParticles(ParticleTypes.CLOUD, v.getX() + 0.5, v.getY() + 0.2, v.getZ() + 0.5, 2, 0.05, 0.2, 0.05, 0.05);
            if (ticks % 20 == 0) sl.playSound(null, v, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 0.6f);
        }
        float bar = (float) (g.pressurePa() / 1e5);
        if (ticks % 10 == 0 && Math.abs(bar - lastSentBar) > Math.max(0.01f, lastSentBar * 0.01f)) {
            shownBar = bar;
            lastSentBar = bar;
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (ticks % 100 == 0) setChanged();
    }

    // --- порты, щит ---

    @Override
    public String h2ModelId() {
        String id = wholeModelId();
        return id != null ? id : "h2_gasholder_real";
    }

    @Override
    public Direction h2Facing() {
        return wholeModelFacing();
    }

    @Override
    public BlockPos h2Controller() {
        return worldPosition;
    }

    @Override
    public PanelLayout panelLayout() {
        return HydrogenPanels.STORAGE;
    }

    @Override
    public Component panelTitle() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public boolean panelValid(Player player) {
        return PanelHost.near(this, player);
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        HydrogenStorage g = storage();
        out[HydrogenPanels.S_BAR100] = (int) Math.round(g.pressurePa() / 1e3);
        out[HydrogenPanels.S_NM3] = (int) Math.round(g.nm3());
        out[HydrogenPanels.S_MAX_NM3] = (int) Math.round(HydrogenGas.nm3(HydrogenGas.molarDensity(g.spec().maxPa(), AMBIENT_K) * g.spec().volumeM3()));
        out[HydrogenPanels.S_TEMP_C] = (int) Math.round(g.tempK() - 273.15);
        out[HydrogenPanels.S_KG10] = (int) Math.round(g.massKg() * 10);
        out[HydrogenPanels.S_MAX_BAR100] = (int) Math.round(g.spec().maxPa() / 1e3);
        out[HydrogenPanels.S_FLAGS] = (g.reliefOpen() ? 1 << HydrogenPanels.F_RELIEF : 0) | (g.burst() ? 1 << HydrogenPanels.F_BURST : 0)
                | (g.ventOpen() ? 1 << HydrogenPanels.F_VENT : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id != HydrogenPanels.BUTTON_VENT) return;
        storage().setVentOpen(!storage().ventOpen());
        level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.5f, 1.4f);
        setChanged();
    }

    /** Shift + ПКМ: заменить разорванную мембрану, когда давление уже сброшено. */
    @Override
    public InteractionResult use(Player player) {
        HydrogenStorage g = storage();
        if (!g.burst()) return InteractionResult.PASS;
        if (g.pressurePa() > 1.5e5) {
            player.displayClientMessage(HydrogenText.msg("disc.pressurised"), true);
            return InteractionResult.FAIL;
        }
        g.replaceDisc();
        player.displayClientMessage(HydrogenText.msg("disc.replaced"), true);
        setChanged();
        return InteractionResult.SUCCESS;
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

    // --- цельная модель ---

    @Override
    @Nullable
    public String wholeModelId() {
        return WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock b ? b.facing(s) : Direction.NORTH;
    }

    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing());
    }

    // --- сохранение и клиент ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("gas", H2Nbt.save(storage()::save));
        tag.putFloat("bar", shownBar);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("gas")) storage().load(H2Nbt.reader(tag.getCompound("gas")));
        shownBar = tag.getFloat("bar");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putFloat("bar", shownBar);
        return t;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
