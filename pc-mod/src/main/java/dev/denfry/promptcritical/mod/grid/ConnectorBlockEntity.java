package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.sim.grid.ConnectorUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.LineUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Коннектор провода ВЛ (07 §7.4.2): {@code wire_connector_lv} на грани блока (пролёт до 16 м), {@code wire_connector_mv}
 * на верхе опоры (до 32 м). Узел коннектора — кабели и выводы вокруг него. Пролёты хранят оба конца; рисует пролёт
 * конец с меньшей позицией, стрела провеса — по температуре провода из домена (обновление не чаще 1 Гц и при изменении
 * больше 5 см).
 */
public class ConnectorBlockEntity extends GridDeviceBlockEntity<ConnectorUnit> {
    /** Стрела провеса при 20 °C — доля пролёта (64 м → 1,5 м, тест T7.29). */
    public static final double SAG_FRACTION = 1.5 / 64;

    /** Пролёт к коннектору {@code other}: провод каталога, изолированный НН или голый; стрела, м. */
    public record Span(BlockPos other, String conductor, boolean insulated, float sagM) {
    }

    private final boolean mv;
    private final List<Span> spans = new ArrayList<>();

    public ConnectorBlockEntity(BlockPos pos, BlockState state, boolean mv) {
        super(mv ? GridBlockEntities.WIRE_CONNECTOR_MV.get() : GridBlockEntities.WIRE_CONNECTOR_LV.get(), pos, state);
        this.mv = mv;
    }

    public static ConnectorBlockEntity lv(BlockPos pos, BlockState state) {
        return new ConnectorBlockEntity(pos, state, false);
    }

    public static ConnectorBlockEntity mv(BlockPos pos, BlockState state) {
        return new ConnectorBlockEntity(pos, state, true);
    }

    public boolean mediumVoltage() {
        return mv;
    }

    /** Наибольший пролёт класса, м. */
    public int maxSpan() {
        return mv ? 32 : 16;
    }

    @Override
    protected ConnectorUnit unitOf(GridSubsystem grid) {
        return grid.connector(simId(), mv);
    }

    @Override
    public List<List<Face>> terminals() {
        return List.of(GridDevice.allFaces(worldPosition));
    }

    /** Точка подвеса провода (мировые координаты). */
    public static Vec3 attachPoint(BlockPos pos, boolean mv) {
        return Vec3.atCenterOf(pos).add(0, mv ? 0.25 : 0.05, 0);
    }

    public Vec3 attachPoint() {
        return attachPoint(worldPosition, mv);
    }

    public List<Span> spans() {
        return spans;
    }

    public boolean linkedTo(BlockPos other) {
        for (Span s : spans) if (s.other.equals(other)) return true;
        return false;
    }

    void addSpan(BlockPos other, String conductor, boolean insulated, double lengthM) {
        spans.add(new Span(other.immutable(), conductor, insulated, (float) (lengthM * SAG_FRACTION)));
        changed();
    }

    /** Убрать пролёт к {@code other}; {@code true} — он был. */
    boolean removeSpan(BlockPos other) {
        boolean removed = spans.removeIf(s -> s.other.equals(other));
        if (removed) changed();
        return removed;
    }

    void setSag(int index, float sagM) {
        Span s = spans.get(index);
        if (Math.abs(s.sagM - sagM) < 0.05f) return;
        spans.set(index, new Span(s.other, s.conductor, s.insulated, sagM));
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public InteractionResult use(Player player) {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        GridSubsystem g = b == null ? null : b.world().subsystem(GridSubsystem.class);
        if (g == null) return InteractionResult.PASS;
        if (spans.isEmpty()) player.displayClientMessage(GridPanels.SPAN_NONE.get(), true);
        for (Span s : spans) {
            long key = GridWorld.lineKey(worldPosition, s.other);
            LineUnit l = g.line(key);
            player.displayClientMessage(GridPanels.SPAN_INFO.get(String.format(Locale.ROOT, "%.0f", Math.sqrt(worldPosition.distSqr(s.other))),
                    s.conductor.toUpperCase(Locale.ROOT), String.format(Locale.ROOT, "%.0f", g.lineCurrentA(key)),
                    String.format(Locale.ROOT, "%.0f", g.lineTemperatureC(key)),
                    l != null && l.touching() && !l.insulated() ? GridPanels.SPAN_TOUCH.get() : Component.empty()), false);
        }
        return InteractionResult.SUCCESS;
    }

    /** Область рендера пролётов (для {@code BlockEntityRenderer#getRenderBoundingBox}). */
    public AABB spanBounds() {
        AABB box = new AABB(worldPosition);
        for (Span s : spans) box = box.minmax(new AABB(s.other)).inflate(0, s.sagM + 1, 0);
        return box;
    }

    // --- сохранение и клиент ---

    private ListTag spansTag() {
        ListTag list = new ListTag();
        for (Span s : spans) {
            CompoundTag t = new CompoundTag();
            t.putLong("other", s.other.asLong());
            t.putString("conductor", s.conductor);
            t.putBoolean("insulated", s.insulated);
            t.putFloat("sag", s.sagM);
            list.add(t);
        }
        return list;
    }

    private void readSpans(CompoundTag tag) {
        spans.clear();
        for (Tag e : tag.getList("spans", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            spans.add(new Span(BlockPos.of(t.getLong("other")), t.getString("conductor"), t.getBoolean("insulated"), t.getFloat("sag")));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("spans", spansTag());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readSpans(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("spans", spansTag());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
