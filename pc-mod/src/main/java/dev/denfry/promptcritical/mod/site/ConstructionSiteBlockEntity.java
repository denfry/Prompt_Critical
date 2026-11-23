package dev.denfry.promptcritical.mod.site;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Стройплощадка (спецификация real-scale machines, §3a): держит проект, склад материалов и номер этапа. Этап
 * начинается, когда на складе есть весь его перечень (материалы списываются), и идёт своё время; после последнего
 * этапа площадка уступает место готовой машине, которая сразу встаёт на пусконаладку.
 */
public class ConstructionSiteBlockEntity extends BlockEntity {
    /** Ячеек у приёма материалов: одна, принятое сразу засчитывается в стройку и не хранится в ячейках. */
    public static final int SLOTS = 1;

    private String planId = "";
    /** Игрок, приложивший чертёж: ему засчитывается постройка ({@link #complete}). */
    @Nullable
    private java.util.UUID owner;
    private int stage;
    private int erectLeft;
    /**
     * Сколько уже привезли на текущий этап. При реальной массе этап — тысячи предметов: площадка не держит их на складе,
     * а засчитывает (воронки, Create и руки идут через тот же приём {@link #items()}).
     */
    private final java.util.Map<Item, Integer> delivered = new java.util.HashMap<>();
    private final net.neoforged.neoforge.items.IItemHandler items = new net.neoforged.neoforge.items.IItemHandler() {
        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int take = Math.min(stack.getCount(), lack(stack.getItem()));
            if (take <= 0) return stack;
            if (!simulate) {
                delivered.merge(stack.getItem(), take, Integer::sum);
                setChanged();
            }
            return stack.copyWithCount(stack.getCount() - take);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return lack(stack.getItem()) > 0;
        }
    };

    public ConstructionSiteBlockEntity(BlockPos pos, BlockState state) {
        super(SiteRegistry.SITE_BE.get(), pos, state);
    }

    public net.neoforged.neoforge.items.IItemHandler items() {
        return items;
    }

    /** Сколько предмета ещё нужно на текущий и оставшиеся этапы сверх привезённого; 0 — не нужен или уже всё есть. */
    private int lack(Item item) {
        SitePlans.Plan p = plan();
        if (p == null) return 0;
        int need = 0;
        for (int i = stage; i < p.stages().size(); i++)
            for (SitePlans.Need n : p.stages().get(i).needs()) if (n.item().get() == item) need += n.count();
        return Math.max(0, need - delivered.getOrDefault(item, 0));
    }

    @Nullable
    public SitePlans.Plan plan() {
        return planId.isEmpty() ? null : SitePlans.get(planId);
    }

    public int stage() {
        return stage;
    }

    public int erectLeft() {
        return erectLeft;
    }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    /**
     * Принять проект: весь объём машины свободен и стоит на твёрдом основании. Возвращает null или первое
     * препятствие (для сообщения игроку).
     */
    @Nullable
    public MutableComponent accept(String id) {
        SitePlans.Plan p = SitePlans.get(id);
        if (p == null) return Component.translatable(SiteText.UNKNOWN);
        MutableComponent why = obstruction(p);
        if (why != null) return why;
        planId = id;
        stage = 0;
        erectLeft = 0;
        delivered.clear();
        sync();
        return null;
    }

    /** Первая занятая клетка объёма машины или первая клетка основания без опоры; null — место готово. */
    @Nullable
    public MutableComponent obstruction(SitePlans.Plan p) {
        if (level == null || !(p.unit().get() instanceof PcBlock pc)) return Component.translatable(SiteText.UNKNOWN);
        Footprint fp = pc.spec().footprint();
        List<Footprint.Cell> cells = fp.cells(worldPosition, facing());
        java.util.Set<BlockPos> inside = new java.util.HashSet<>();
        for (Footprint.Cell c : cells) inside.add(c.pos());
        for (Footprint.Cell c : cells) {
            if (c.pos().equals(worldPosition)) continue;
            BlockState s = level.getBlockState(c.pos());
            if (!s.canBeReplaced()) return Component.translatable(SiteText.BLOCKED, s.getBlock().getName(), pos(c.pos()));
        }
        int minY = Integer.MAX_VALUE;
        for (BlockPos q : inside) minY = Math.min(minY, q.getY());
        for (BlockPos q : inside) {
            if (q.getY() != minY) continue;
            BlockPos below = q.below();
            if (inside.contains(below)) continue;
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP))
                return Component.translatable(SiteText.NO_GROUND, pos(below));
        }
        return null;
    }

    private static String pos(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    /** Принять предмет из рук игрока, если он нужен на текущем этапе: стопка уменьшается на принятое. */
    public ItemStack offer(ItemStack stack) {
        int take = Math.min(stack.getCount(), lack(stack.getItem()));
        if (take <= 0) return stack;
        items.insertItem(0, stack.copyWithCount(take), false);
        stack.shrink(take);
        return stack;
    }

    /** Чего не хватает на текущем этапе. */
    public List<MutableComponent> missing() {
        List<MutableComponent> out = new ArrayList<>();
        SitePlans.Plan p = plan();
        if (p == null || stage >= p.stages().size()) return out;
        for (SitePlans.Need n : p.stages().get(stage).needs()) {
            int lack = n.count() - delivered.getOrDefault(n.item().get(), 0);
            if (lack > 0) out.add(Component.literal(lack + " × ").append(n.item().get().getDescription()));
        }
        return out;
    }

    /** Сообщение о состоянии площадки. */
    public void report(Player player) {
        SitePlans.Plan p = plan();
        if (p == null) {
            player.displayClientMessage(Component.translatable(SiteText.EMPTY), false);
            return;
        }
        SitePlans.Stage st = p.stages().get(Math.min(stage, p.stages().size() - 1));
        MutableComponent head = Component.translatable(SiteText.STAGE, stage + 1, p.stages().size(),
                Component.translatable(SiteText.stageKey(st.key())), p.unit().get().getName());
        player.displayClientMessage(head.withStyle(ChatFormatting.GOLD), false);
        if (erectLeft > 0) {
            player.displayClientMessage(Component.translatable(SiteText.ERECTING, (erectLeft + 19) / 20), false);
            return;
        }
        List<MutableComponent> lack = missing();
        if (lack.isEmpty()) return;
        MutableComponent line = Component.translatable(SiteText.NEED);
        for (int i = 0; i < lack.size(); i++) line.append(i == 0 ? " " : ", ").append(lack.get(i));
        player.displayClientMessage(line, false);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConstructionSiteBlockEntity be) {
        be.tick();
    }

    private void tick() {
        SitePlans.Plan p = plan();
        if (p == null || level == null) return;
        if (erectLeft > 0) {
            if (--erectLeft == 0) {
                stage++;
                sync();
                if (stage >= p.stages().size()) complete(p);
            }
            return;
        }
        if (stage >= p.stages().size() || level.getGameTime() % 20 != 0) return;
        if (!missing().isEmpty()) return;
        // этап забирает свою долю привезённого, остальное ждёт следующих этапов
        for (SitePlans.Need n : p.stages().get(stage).needs()) delivered.merge(n.item().get(), -n.count(), Integer::sum);
        delivered.values().removeIf(v -> v <= 0);
        erectLeft = Math.max(1, p.stages().get(stage).erectTicks());
        sync();
    }

    /** Последний этап: машина встаёт на место площадки; если объём заняли во время стройки — ждёт. */
    private void complete(SitePlans.Plan p) {
        if (level == null) return;
        if (obstruction(p) != null) {
            stage = p.stages().size() - 1;
            erectLeft = 200;
            return;
        }
        Block unit = p.unit().get();
        BlockState target = unit.defaultBlockState();
        if (target.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) target = target.setValue(BlockStateProperties.HORIZONTAL_FACING, facing());
        level.setBlock(worldPosition, target, Block.UPDATE_ALL);
        credit(p, unit);
    }

    /**
     * Постройка засчитывается игроку, приложившему чертёж, как постановка машины и её двойника старой серии (статистика
     * «использован предмет»): по ней идут задания маршрута и главы справочника; плюс инструкция по эксплуатации.
     */
    private void credit(SitePlans.Plan p, Block unit) {
        if (owner == null || !(level instanceof net.minecraft.server.level.ServerLevel sl)
                || !(sl.getPlayerByUUID(owner) instanceof net.minecraft.server.level.ServerPlayer player)) return;
        credit(player, p.id(), unit);
    }

    /** Засчитать игроку достроенную по плану {@code planId} машину {@code unit} и её двойника старой серии. */
    public static void credit(net.minecraft.server.level.ServerPlayer player, String planId, Block unit) {
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(unit.asItem()));
        dev.denfry.promptcritical.mod.content.HandbookService.issueProcedure(player, unit);
        SitePlans.Twin twin = SitePlans.twin(planId);
        if (twin == null) return;
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(twin.block().get().asItem()), twin.count());
        dev.denfry.promptcritical.mod.content.HandbookService.issueProcedure(player, twin.block().get());
    }

    /** Запомнить игрока, приложившего чертёж. */
    public void setOwner(net.minecraft.world.entity.player.Player player) {
        owner = player.getUUID();
        setChanged();
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("plan", planId);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putInt("stage", stage);
        tag.putInt("erect", erectLeft);
        CompoundTag d = new CompoundTag();
        delivered.forEach((item, n) -> d.putInt(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(), n));
        tag.put("delivered", d);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        planId = tag.getString("plan");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        stage = tag.getInt("stage");
        erectLeft = tag.getInt("erect");
        delivered.clear();
        CompoundTag d = tag.getCompound("delivered");
        for (String k : d.getAllKeys()) {
            var rl = net.minecraft.resources.ResourceLocation.tryParse(k);
            if (rl != null) net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(rl).ifPresent(i -> delivered.put(i, d.getInt(k)));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = super.getUpdateTag(registries);
        t.putString("plan", planId);
        t.putInt("stage", stage);
        t.putInt("erect", erectLeft);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
