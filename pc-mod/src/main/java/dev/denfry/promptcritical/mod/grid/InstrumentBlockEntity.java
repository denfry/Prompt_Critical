package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.grid.BreakerResponse;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.GridUnit;
import dev.denfry.promptcritical.sim.grid.SwitchUnit;
import dev.denfry.promptcritical.sim.grid.SyncScope;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Щитовой прибор на ячейке КРУ, щите или щитке (07 §7.3.5, §7.6): синхроноскоп {@code instrument_synchroscope}
 * (стрелка по скольжению, «генератор» — присоединение, «сеть» — шина; на щите — кнопка «вкл» и ручки вариатора и
 * уставки напряжения генератора присоединения) или счётчик {@code energy_meter} (кВт·ч в обе стороны).
 */
public class InstrumentBlockEntity extends BlockEntity implements FoundryDevice, PanelHost {
    public enum Kind { SYNCHROSCOPE, METER }

    private final Kind kind;
    /** Угол синхроноскопа, рад (NaN — нет напряжения с одной стороны); видит клиент. */
    private float angle = Float.NaN, sentAngle = Float.NaN;
    private int ticks;

    public InstrumentBlockEntity(BlockPos pos, BlockState state, Kind kind) {
        super(kind == Kind.SYNCHROSCOPE ? GridBlockEntities.SYNCHROSCOPE.get() : GridBlockEntities.ENERGY_METER.get(), pos, state);
        this.kind = kind;
    }

    public static InstrumentBlockEntity synchroscope(BlockPos pos, BlockState state) {
        return new InstrumentBlockEntity(pos, state, Kind.SYNCHROSCOPE);
    }

    public static InstrumentBlockEntity meter(BlockPos pos, BlockState state) {
        return new InstrumentBlockEntity(pos, state, Kind.METER);
    }

    public Kind kind() {
        return kind;
    }

    /** Угол стрелки для рендера, рад; NaN — стрелка в покое. */
    public float angle() {
        return angle;
    }

    @Nullable
    private GridSubsystem grid() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        return b == null ? null : b.world().subsystem(GridSubsystem.class);
    }

    /** Аппарат, на котором висит прибор. */
    @Nullable
    public SwitchUnit target() {
        BlockPos m = GridMountedBlock.mountPos(getBlockState(), worldPosition);
        BlockState s = level.getBlockState(m);
        BlockPos c = s.getBlock() instanceof MultiblockPartBlock ? PcBlock.structureController(level, MultiblockPartBlock.anchor(s, m))
                : s.getBlock() instanceof PcBlock pc ? pc.controllerPos(s, m) : null;
        if (c == null) return null;
        BlockEntity be = level.getBlockEntity(c);
        return be instanceof BreakerCellBlockEntity cell ? cell.unit()
                : be instanceof DistributionBoxBlockEntity box ? box.unit()
                : be instanceof DisconnectorBlockEntity d ? d.unit() : null;
    }

    /** Генератор на стороне присоединения аппарата {@code s}. */
    @Nullable
    GeneratorUnit generatorOf(@Nullable SwitchUnit s) {
        GridSubsystem g = grid();
        if (s == null || g == null) return null;
        for (GridUnit u : g.units())
            if (u instanceof GeneratorUnit gen && gen.node(0) != GridUnit.NO_NODE && gen.node(0) == s.node(1)) return gen;
        return null;
    }

    @Override
    public void tickDevice() {
        if (level == null || level.isClientSide || kind != Kind.SYNCHROSCOPE) return;
        if (++ticks % 2 != 0) return;
        SwitchUnit s = target();
        SyncScope sc = s == null ? null : s.syncScope();
        angle = sc == null ? Float.NaN : (float) sc.angleRad();
        boolean changed = Float.isNaN(angle) != Float.isNaN(sentAngle)
                || !Float.isNaN(angle) && Math.abs(Math.IEEEremainder(angle - sentAngle, 2 * Math.PI)) > 0.02;
        if (changed) {
            sentAngle = angle;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- руки ---

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
        SwitchUnit s = target();
        if (s == null) {
            player.displayClientMessage(GridText.msg(GridText.NO_SWITCH), true);
            return InteractionResult.SUCCESS;
        }
        player.displayClientMessage(GridText.msg(GridText.METER, fmt(s.energyForwardKwh()), fmt(s.energyReverseKwh()),
                fmt(s.powerW() / 1e3)), false);
        return InteractionResult.SUCCESS;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return kind == Kind.SYNCHROSCOPE ? GridPanels.SYNCHROSCOPE : GridPanels.METER;
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
        SwitchUnit s = target();
        if (kind == Kind.METER) {
            if (s == null) return;
            out[GridPanels.E_FWD_KWH10] = (int) Math.min(Integer.MAX_VALUE, Math.round(s.energyForwardKwh() * 10));
            out[GridPanels.E_REV_KWH10] = (int) Math.min(Integer.MAX_VALUE, Math.round(s.energyReverseKwh() * 10));
            out[GridPanels.E_P_KW10] = (int) Math.round(s.powerW() / 100);
            out[GridPanels.E_FLAGS] = 1;
            return;
        }
        out[GridPanels.S_ANGLE10] = scopeAngle(s);
        if (s == null) return;
        out[GridPanels.S_GEN_V] = (int) Math.round(s.voltageV(1));
        out[GridPanels.S_BUS_V] = (int) Math.round(s.voltageV(0));
        out[GridPanels.S_GEN_HZ100] = (int) Math.round(s.frequencyHz(1) * 100);
        out[GridPanels.S_BUS_HZ100] = (int) Math.round(s.frequencyHz(0) * 100);
        SyncScope sc = s.syncScope();
        if (sc != null) {
            out[GridPanels.S_SLIP_MHZ] = (int) Math.round(sc.slipHz() * 1000);
            out[GridPanels.S_DV_PERMIL] = (int) Math.round(sc.dvPu() * 1000);
        }
        GeneratorUnit g = generatorOf(s);
        out[GridPanels.S_FLAGS] = 1 | (s.closed() ? 2 : 0) | (sc != null ? 4 : 0) | (g != null ? 8 : 0);
        if (g != null) {
            out[GridPanels.S_TRIM] = g.trimPermil();
            out[GridPanels.S_VREF_PERMIL] = (int) Math.round(g.voltageReferencePu() * 1000);
        }
    }

    private static int scopeAngle(@Nullable SwitchUnit s) {
        SyncScope sc = s == null ? null : s.syncScope();
        if (sc == null) return PanelLayout.Scope.OFF;
        return (int) Math.round(Math.toDegrees(sc.angleRad()) * 10);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        BreakerResponse r = press(id);
        if (r != null) player.displayClientMessage(GridText.msg(GridText.BREAKER_RESPONSE, BreakerCellBlockEntity.response(r)), true);
    }

    /** Кнопка панели прибора; ответ привода — для ВКЛ/ОТКЛ, иначе {@code null}. */
    @Nullable
    public BreakerResponse press(int id) {
        SwitchUnit s = target();
        if (s == null) return null;
        GeneratorUnit g = generatorOf(s);
        switch (id) {
            case GridPanels.BUTTON_CLOSE -> {
                return s.operate(true);
            }
            case GridPanels.BUTTON_OPEN -> {
                return s.operate(false);
            }
            case GridPanels.BUTTON_TRIM_DOWN -> {
                if (g != null) g.setTrimPermil(g.trimPermil() - 1);
            }
            case GridPanels.BUTTON_TRIM_UP -> {
                if (g != null) g.setTrimPermil(g.trimPermil() + 1);
            }
            case GridPanels.BUTTON_VREF_DOWN -> {
                if (g != null) g.setVoltageReferencePu(g.voltageReferencePu() - 0.005);
            }
            case GridPanels.BUTTON_VREF_UP -> {
                if (g != null) g.setVoltageReferencePu(g.voltageReferencePu() + 0.005);
            }
            default -> {
            }
        }
        return null;
    }

    // --- клиент ---

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("angle", angle);
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("angle")) angle = tag.getFloat("angle");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
