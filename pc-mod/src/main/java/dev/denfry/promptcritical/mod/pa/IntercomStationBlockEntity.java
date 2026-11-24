package dev.denfry.promptcritical.mod.pa;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Пульт ГГС {@code intercom_station} (13 §13.3.4, 16 §16.4.1): выбор канала и зоны, предзаписанные сообщения, тангента.
 * Тангента — Shift+ПКМ: с Simple Voice Chat речь стоящего у пульта (до 2 м) идёт из громкоговорителей зоны, пока тангента
 * нажата (не дольше минуты). Без питания пульт молчит и горит «неисправность».
 */
public class IntercomStationBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost, PaSystem.PaDevice {
    public static final double POWER_W = 20;

    private static final Map<Level, Set<BlockPos>> TALKING = new HashMap<>();

    private int channel = PaChannel.CONTROL.ordinal(), zone = 1, ticks;
    private boolean powered = true;
    @Nullable
    private UUID team, talker;
    private long talkUntil;

    public IntercomStationBlockEntity(BlockPos pos, BlockState state) {
        super(PaBlockEntities.INTERCOM_STATION.get(), pos, state);
    }

    /** Пульты уровня с нажатой тангентой. */
    static synchronized List<IntercomStationBlockEntity> talking(ServerLevel level) {
        List<IntercomStationBlockEntity> out = new ArrayList<>();
        Set<BlockPos> s = TALKING.get(level);
        if (s == null) return out;
        for (BlockPos p : List.copyOf(s))
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof IntercomStationBlockEntity ic && ic.talker != null) out.add(ic);
            else s.remove(p);
        return out;
    }

    private static synchronized void markTalking(Level level, BlockPos pos, boolean on) {
        Set<BlockPos> s = TALKING.computeIfAbsent(level, k -> new HashSet<>());
        if (on) s.add(pos.immutable());
        else s.remove(pos);
    }

    @Override
    public void setRemoved() {
        if (level != null) markTalking(level, worldPosition, false);
        super.setRemoved();
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || ++ticks % 10 != 0) return;
        if (ticks % 20 == 0) powered = dev.denfry.promptcritical.mod.grid.MachinePower.powered(level, worldPosition, POWER_W);
        if (talker != null) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(talker);
            if (!powered || p == null || p.level() != level || p.distanceToSqr(worldPosition.getCenter()) > PaSystem.TALK_REACH_SQ
                    || level.getGameTime() > talkUntil) release();
        }
        BlockState s = getBlockState(), next = s;
        if (s.hasProperty(BlockStateProperties.POWERED)) next = next.setValue(BlockStateProperties.POWERED, powered);
        if (s.hasProperty(PcProperties.RUNNING)) next = next.setValue(PcProperties.RUNNING, talker != null);
        if (next != s) level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
    }

    public PaChannel channel() {
        return PaChannel.of(channel);
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

    @Nullable
    UUID talker() {
        return talker;
    }

    /** Пульт станции — только своей команде (04d §4d.5); без FTB Teams пульт общий. */
    private boolean mayUse(ServerPlayer p) {
        return team == null || Objects.equals(team, PaSystem.teamOf(p));
    }

    private void release() {
        talker = null;
        markTalking(level, worldPosition, false);
        setChanged();
    }

    // ------------------------------------------------------------------ тангента

    @Override
    public InteractionResult use(Player player) {
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel)) return InteractionResult.PASS;
        if (!powered) {
            sp.displayClientMessage(PaPanels.FAULT.get().withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (!mayUse(sp)) {
            sp.displayClientMessage(PaPanels.NOT_YOURS.get().withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (sp.getUUID().equals(talker)) {
            release();
            sp.displayClientMessage(PaPanels.TALK_OFF.get(), true);
        } else if (talker == null) {
            talker = sp.getUUID();
            talkUntil = level.getGameTime() + PaSystem.TALK_MAX_TICKS;
            markTalking(level, worldPosition, true);
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.5f, 0.7f);
            Component where = channel().allZones ? PaPanels.ALL_ZONES.get() : Component.literal(String.valueOf(zone));
            sp.displayClientMessage((ModList.get().isLoaded("voicechat") ? PaPanels.TALK_ON : PaPanels.TALK_NO_VOICE)
                    .get(channel().label.get(), where), true);
        } else {
            sp.displayClientMessage(PaPanels.BUSY.get(), true);
        }
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return PaPanels.INTERCOM;
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
        out[PaPanels.I_CHANNEL] = channel;
        out[PaPanels.I_ZONE] = zone;
        out[PaPanels.I_FLAGS] = (powered ? PaPanels.F_POWERED : 0) | (talker != null ? PaPanels.F_ACTIVE : 0)
                | (ModList.get().isLoaded("voicechat") ? PaPanels.F_VOICE : 0);
        out[PaPanels.I_SPEAKERS] = level instanceof ServerLevel sl ? PaSystem.speakers(sl, team, channel().allZones ? 0 : zone).size() : 0;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!mayUse(player)) {
            player.displayClientMessage(PaPanels.NOT_YOURS.get().withStyle(ChatFormatting.RED), true);
            return;
        }
        if (id >= PaPanels.B_CHANNEL0 && id < PaPanels.B_CHANNEL0 + PaChannel.values().length) {
            channel = id - PaPanels.B_CHANNEL0;
        } else if (id == PaPanels.B_PREV || id == PaPanels.B_NEXT) {
            zone = Math.floorMod(zone - 1 + (id == PaPanels.B_NEXT ? 1 : -1), PlantLoudspeakerBlockEntity.MAX_ZONE) + 1;
        } else if (id >= PaPanels.B_MESSAGE0 && id < PaPanels.B_MESSAGE0 + PaMessage.values().length) {
            if (!powered) {
                player.displayClientMessage(PaPanels.FAULT.get().withStyle(ChatFormatting.RED), true);
                return;
            }
            PaMessage msg = PaMessage.values()[id - PaPanels.B_MESSAGE0];
            int sent = PaSystem.announce(sl, team, zone, msg);
            player.displayClientMessage(sent > 0 ? PaPanels.SENT.get(sent) : PaPanels.NOT_SENT.get(), true);
        }
        setChanged();
    }

    // ------------------------------------------------------------------ руки и сохранение

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
        tag.putInt("channel", channel);
        tag.putInt("zone", zone);
        if (team != null) tag.putUUID("team", team);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channel = Math.floorMod(tag.getInt("channel"), PaChannel.values().length);
        zone = Math.max(1, Math.min(PlantLoudspeakerBlockEntity.MAX_ZONE, tag.contains("zone") ? tag.getInt("zone") : 1));
        team = tag.hasUUID("team") ? tag.getUUID("team") : null;
    }
}
