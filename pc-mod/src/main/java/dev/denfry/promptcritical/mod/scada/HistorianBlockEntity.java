package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity.Reading;
import dev.denfry.promptcritical.sim.automation.historian.TagArchive;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Историан {@code historian_server} (03d §3d.9.5): сам собирает аналоговые телеизмерения шлюзов RTU в радиусе
 * {@value #RADIUS} блоков раз в секунду (без расхода токенов Lua), тег — «машина/точка», и хранит их сжатыми
 * ({@link TagArchive}: фильтр исключений и «вращающаяся дверь», E_c — 0,25 % шкалы, но не меньше 0,01). Время архива —
 * секунды игрового времени сервера (тик — 1/20 с). Запросы — CC-периферия {@code pc_historian}: сырые точки,
 * интерполяция с шагом, агрегаты. Пределы [BALANCE]: {@value #MAX_TAGS} тегов на сервер, {@value #MAX_POINTS} точек на
 * тег (вместо 2000 и 20 000 GDD — архив живёт в NBT блока).
 */
public class HistorianBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int RADIUS = 16, MAX_TAGS = 256, MAX_POINTS = 2048, QUERY_MAX = 4096;
    /** E_c — доля величины значения (как 0,25 % шкалы при шкале ≈ значению), не меньше 0,01. */
    static final double EC_SHARE = 0.0025, EC_MIN = 0.01;

    private final Map<String, TagArchive> tags = new LinkedHashMap<>();
    private int ticks, selected;

    public HistorianBlockEntity(BlockPos pos, BlockState state) {
        super(ScadaBlockEntities.HISTORIAN.get(), pos, state);
    }

    double nowS() {
        return level == null ? 0 : level.getGameTime() / 20.0;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel) || ++ticks % 20 != 0) return;
        double t = nowS();
        int seen = 0;
        for (TelemetryGatewayBlockEntity rtu : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), TelemetryGatewayBlockEntity.class))
            for (Reading r : rtu.analogReadings()) {
                String tag = r.machine() + "/" + r.point().name();
                TagArchive a = tags.get(tag);
                if (a == null) {
                    if (tags.size() >= MAX_TAGS) continue;
                    a = new TagArchive(Math.max(EC_MIN, Math.abs(r.value()) * EC_SHARE), MAX_POINTS);
                    tags.put(tag, a);
                }
                a.add(t, r.value());
                seen++;
            }
        // достижения 11 §11.2.2: «Память завода» — 100 тегов, каждый за игровые сутки (1200 с архива); «Телемеханика» —
        // 10 КП на одном мастере
        int rtus = Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), TelemetryGatewayBlockEntity.class).size();
        if (rtus >= 10) dev.denfry.promptcritical.mod.content.SimAchievements.once(this, dev.denfry.promptcritical.mod.content.SimAchievements.TELEMETRY, "rtus", rtus);
        if (ticks % 1200 == 0 && tags.size() >= 100) {
            long day = tags.values().stream().filter(a -> a.size() > 0 && a.lastT() - a.firstT() >= 1200).count();
            if (day >= 100) dev.denfry.promptcritical.mod.content.SimAchievements.once(this, dev.denfry.promptcritical.mod.content.SimAchievements.HISTORIAN_DAY, "tags", day);
        }
        BlockState s = getBlockState();
        boolean busy = seen > 0;
        if (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) != busy)
            level.setBlock(worldPosition, s.setValue(BlockStateProperties.LIT, busy), Block.UPDATE_CLIENTS);
        if (ticks % 200 == 0) setChanged();
    }

    // ------------------------------------------------------------------ pc_historian (§3d.14.4)

    public List<Map<String, Object>> listTags() {
        List<Map<String, Object>> out = new ArrayList<>();
        tags.forEach((k, a) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tag", k);
            m.put("rateHz", 1);
            m.put("ec", a.ec());
            m.put("points", a.size());
            m.put("firstT", a.firstT());
            m.put("lastT", a.lastT());
            out.add(m);
        });
        return out;
    }

    @Nullable
    public TagArchive archive(String tag) {
        return tags.get(tag);
    }

    public static Map<String, Object> series(List<double[]> pts) {
        List<Double> t = new ArrayList<>(), v = new ArrayList<>();
        for (double[] p : pts) {
            t.add(p[0]);
            v.add(p[1]);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", t);
        m.put("v", v);
        return m;
    }

    public Map<String, Object> stats() {
        long points = 0, received = 0;
        double oldest = Double.NaN;
        for (TagArchive a : tags.values()) {
            points += a.size();
            received += a.received();
            if (!Double.isNaN(a.firstT()) && (Double.isNaN(oldest) || a.firstT() < oldest)) oldest = a.firstT();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tags", tags.size());
        m.put("points", points);
        m.put("bytes", points * 12);
        m.put("compression", points > 0 ? (double) received / points : 1.0);
        m.put("oldestT", oldest);
        m.put("now", nowS());
        return m;
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return HistorianPanels.HISTORIAN;
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
        long points = 0, received = 0;
        for (TagArchive a : tags.values()) {
            points += a.size();
            received += a.received();
        }
        out[HistorianPanels.TAGS] = tags.size();
        out[HistorianPanels.POINTS] = (int) Math.min(Integer.MAX_VALUE, points);
        out[HistorianPanels.RATIO10] = points > 0 ? (int) Math.round(10.0 * received / points) : 10;
        out[HistorianPanels.SELECTED] = selected;
        List<String> names = new ArrayList<>(tags.keySet());
        if (!names.isEmpty()) {
            TagArchive a = tags.get(names.get(Math.floorMod(selected, names.size())));
            double now = nowS();
            out[HistorianPanels.VALUE1000] = (int) Math.round(a.valueAt(a.lastT()) * 1000);
            out[HistorianPanels.AVG1000] = (int) Math.round(a.aggregate(now - 3600, now, "avg") * 1000);
            out[HistorianPanels.MIN1000] = (int) Math.round(a.aggregate(now - 3600, now, "min") * 1000);
            out[HistorianPanels.MAX1000] = (int) Math.round(a.aggregate(now - 3600, now, "max") * 1000);
        }
    }

    /** Имя выбранного тега — для подписи щита. */
    public String selectedTag() {
        List<String> names = new ArrayList<>(tags.keySet());
        return names.isEmpty() ? "" : names.get(Math.floorMod(selected, names.size()));
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (tags.isEmpty()) return;
        selected = Math.floorMod(selected + (id == HistorianPanels.B_NEXT ? 1 : -1), tags.size());
        player.displayClientMessage(Component.literal(selectedTag()), true);
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
        CompoundTag all = new CompoundTag();
        tags.forEach((k, a) -> {
            MutableStateTree tree = new MutableStateTree();
            a.save(new TreeStateWriter(tree));
            CompoundTag one = NbtStateTrees.toNbt(tree);
            one.putDouble("ec", a.ec());
            all.put(k, one);
        });
        tag.put("tags", all);
        tag.putInt("selected", selected);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tags.clear();
        CompoundTag all = tag.getCompound("tags");
        for (String k : all.getAllKeys()) {
            CompoundTag one = all.getCompound(k);
            TagArchive a = new TagArchive(one.getDouble("ec"), MAX_POINTS);
            a.load(new TreeStateReader(NbtStateTrees.fromNbt(one)));
            tags.put(k, a);
        }
        selected = tag.getInt("selected");
    }
}
