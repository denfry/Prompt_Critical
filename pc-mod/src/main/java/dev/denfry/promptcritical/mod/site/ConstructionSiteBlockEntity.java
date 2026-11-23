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
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Стройплощадка (спецификация real-scale machines, §3a): держит проект, склад материалов и номер этапа. Этап
 * начинается, когда на складе есть весь его перечень (материалы списываются), и идёт своё время; после последнего
 * этапа площадка уступает место готовой машине, которая сразу встаёт на пусконаладку.
 */
public class ConstructionSiteBlockEntity extends BlockEntity {
    public static final int SLOTS = 27;

    private String planId = "";
    private int stage;
    private int erectLeft;
    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public ConstructionSiteBlockEntity(BlockPos pos, BlockState state) {
        super(SiteRegistry.SITE_BE.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
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

    /** Принять предмет со склада игрока, если он нужен на текущем этапе; возвращает остаток. */
    public ItemStack offer(ItemStack stack) {
        SitePlans.Plan p = plan();
        if (p == null || stage >= p.stages().size()) return stack;
        for (SitePlans.Need n : p.stages().get(stage).needs()) {
            if (!stack.is(n.item().get())) continue;
            int lack = n.count() - count(n.item().get());
            if (lack <= 0) return stack;
            ItemStack part = stack.split(Math.min(lack, stack.getCount()));
            for (int i = 0; i < SLOTS && !part.isEmpty(); i++) part = items.insertItem(i, part, false);
            if (!part.isEmpty()) stack.grow(part.getCount());
            return stack;
        }
        return stack;
    }

    private int count(Item item) {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) if (items.getStackInSlot(i).is(item)) n += items.getStackInSlot(i).getCount();
        return n;
    }

    /** Чего не хватает на текущем этапе. */
    public List<MutableComponent> missing() {
        List<MutableComponent> out = new ArrayList<>();
        SitePlans.Plan p = plan();
        if (p == null || stage >= p.stages().size()) return out;
        for (SitePlans.Need n : p.stages().get(stage).needs()) {
            int lack = n.count() - count(n.item().get());
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
        for (SitePlans.Need n : p.stages().get(stage).needs()) take(n.item().get(), n.count());
        erectLeft = Math.max(1, p.stages().get(stage).erectTicks());
        sync();
    }

    private void take(Item item, int count) {
        for (int i = 0; i < SLOTS && count > 0; i++) {
            if (!items.getStackInSlot(i).is(item)) continue;
            count -= items.extractItem(i, count, false).getCount();
        }
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
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("plan", planId);
        tag.putInt("stage", stage);
        tag.putInt("erect", erectLeft);
        tag.put("items", items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        planId = tag.getString("plan");
        stage = tag.getInt("stage");
        erectLeft = tag.getInt("erect");
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
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
