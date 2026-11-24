package dev.denfry.promptcritical.mod.pa;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Рупорный громкоговоритель ГГС {@code plant_loudspeaker} (13 §13.3.4): зона оповещения назначается в щите. */
public class PlantLoudspeakerBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost, PaSystem.PaDevice {
    /** Усилитель рупора, Вт. */
    public static final double POWER_W = 30;
    public static final int MAX_ZONE = 16;

    private int zone = 1, ticks, activePriority = Integer.MAX_VALUE;
    private long activeUntil;
    private boolean powered = true;
    @Nullable
    private UUID team;

    public PlantLoudspeakerBlockEntity(BlockPos pos, BlockState state) {
        super(PaBlockEntities.PLANT_LOUDSPEAKER.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) PaSystem.add(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel) PaSystem.remove(level, worldPosition);
        super.setRemoved();
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel) || ++ticks % 10 != 0) return;
        if (ticks % 20 == 0) powered = dev.denfry.promptcritical.mod.grid.MachinePower.powered(level, worldPosition, POWER_W);
        boolean active = powered && level.getGameTime() < activeUntil;
        BlockState s = getBlockState();
        if (s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING) != active)
            level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, active), Block.UPDATE_CLIENTS);
    }

    public int zone() {
        return zone;
    }

    @Nullable
    public UUID team() {
        return team;
    }

    @Override
    public void setTeam(@Nullable UUID team) {
        this.team = team;
        setChanged();
    }

    public boolean powered() {
        return powered;
    }

    /** Идёт сообщение важнее {@code priority} (меньший номер). */
    boolean busyAbove(int priority, long now) {
        return now < activeUntil && activePriority < priority;
    }

    void play(int priority, long until) {
        long now = level == null ? 0 : level.getGameTime();
        if (now >= activeUntil || priority <= activePriority) activePriority = priority;
        activeUntil = Math.max(activeUntil, until);
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return PaPanels.SPEAKER;
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
        out[PaPanels.S_ZONE] = zone;
        out[PaPanels.S_FLAGS] = (powered ? PaPanels.F_POWERED : 0) | (level != null && level.getGameTime() < activeUntil ? PaPanels.F_ACTIVE : 0);
        out[PaPanels.S_RANGE10] = level instanceof ServerLevel sl ? (int) Math.round(PaSystem.rangeM(sl, worldPosition) * 10) : 0;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (!(level instanceof ServerLevel sl) || id != PaPanels.B_PREV && id != PaPanels.B_NEXT) return;
        // зону рупора меняет только своя станция, как и пульт (04d §4d.5)
        if (team != null && !java.util.Objects.equals(team, PaSystem.teamOf(player))) {
            player.displayClientMessage(PaPanels.NOT_YOURS.get().withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        int next = Math.floorMod(zone - 1 + (id == PaPanels.B_NEXT ? 1 : -1), MAX_ZONE) + 1;
        // не больше ops.pa_max_speakers_per_zone громкоговорителей на зону
        if (PaSystem.speakers(sl, team, next).size() >= ServerConfig.PA_MAX_SPEAKERS_PER_ZONE.get()) {
            player.displayClientMessage(PaPanels.ZONE_FULL.get(next), true);
            return;
        }
        zone = next;
        setChanged();
    }

    // ------------------------------------------------------------------ руки и сохранение

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("zone", zone);
        if (team != null) tag.putUUID("team", team);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        zone = Math.max(1, Math.min(MAX_ZONE, tag.contains("zone") ? tag.getInt("zone") : 1));
        team = tag.hasUUID("team") ? tag.getUUID("team") : null;
    }
}
