package dev.denfry.promptcritical.mod.paperwork;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.grid.BreakerCellBlockEntity;
import dev.denfry.promptcritical.mod.grid.DisconnectorBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.grid.SwitchUnit;
import dev.denfry.promptcritical.sim.grid.WorkPermit;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Документы завода до компьютеров — от руки: оперативный журнал обхода ({@link ShiftLog}) и наряд-допуск на работы в
 * электроустановках ({@link WorkPermit}). Клик документом по машине перехватывается до действия блока: журнал не
 * должен переключать разъединитель, по которому им щёлкнули.
 */
public final class Paperwork {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ShiftLog>> SHIFT_LOG =
            COMPONENTS.registerComponentType("shift_log", b -> b.persistent(ShiftLog.CODEC).networkSynchronized(ShiftLog.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PermitRecord>> WORK_PERMIT =
            COMPONENTS.registerComponentType("work_permit", b -> b.persistent(PermitRecord.CODEC).networkSynchronized(PermitRecord.STREAM_CODEC));

    private Paperwork() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        modBus.addListener(Paperwork::payloads);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Paperwork::rightClickBlock);
    }

    // --- открыть документ на клиенте (с пюпитра) ---

    /** Сервер → клиент: показать документ. */
    public record OpenDocumentPayload(ItemStack stack) implements CustomPacketPayload {
        public static final Type<OpenDocumentPayload> TYPE = new Type<>(PromptCritical.id("open_document"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenDocumentPayload> STREAM_CODEC =
                ItemStack.STREAM_CODEC.map(OpenDocumentPayload::new, OpenDocumentPayload::stack);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PromptCritical.MOD_ID).versioned("1").playToClient(OpenDocumentPayload.TYPE, OpenDocumentPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (FMLEnvironment.dist.isClient())
                        ctx.enqueueWork(() -> dev.denfry.promptcritical.mod.paperwork.client.DocumentScreen.open(payload.stack()));
                });
    }

    /** Документ — журнал или наряд (их кладут на пюпитр и читают в окне документа). */
    public static boolean isDocument(ItemStack s) {
        return s.is(PcItems.SHIFT_LOG.get()) || s.is(PcItems.WORK_PERMIT.get());
    }

    public static void show(ServerPlayer p, ItemStack doc) {
        PacketDistributor.sendToPlayer(p, new OpenDocumentPayload(doc.copyWithCount(1)));
    }

    // --- клик документом по блоку ---

    @Nullable
    private static BlockEntity controller(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        BlockPos c = s.getBlock() instanceof MultiblockPartBlock ? PcBlock.structureController(level, MultiblockPartBlock.anchor(s, pos)) : pos;
        return c == null ? null : level.getBlockEntity(c);
    }

    private static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        boolean log = stack.is(PcItems.SHIFT_LOG.get()), permit = stack.is(PcItems.WORK_PERMIT.get());
        if (!log && !permit) return;
        BlockEntity be = controller(event.getLevel(), event.getPos());
        boolean target = log ? be instanceof PanelHost : be instanceof DisconnectorBlockEntity || be instanceof BreakerCellBlockEntity;
        if (!target) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (log) writeReadings(p, stack, (PanelHost) be, be);
        else usePermit(p, stack, (GridDeviceBlockEntity<?>) be);
    }

    // --- оперативный журнал ---

    /** Записать показания щита машины в журнал. */
    public static void writeReadings(ServerPlayer p, ItemStack stack, PanelHost host, BlockEntity be) {
        ShiftLog journal = stack.getOrDefault(SHIFT_LOG.get(), ShiftLog.EMPTY);
        if (journal.full()) {
            p.displayClientMessage(PaperworkText.msg(PaperworkText.LOG_FULL).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        List<Component> values = ShiftLog.readings(host);
        if (values.isEmpty()) {
            p.displayClientMessage(PaperworkText.msg(PaperworkText.LOG_NO_READINGS), true);
            return;
        }
        Level level = p.level();
        Component machine = be.getBlockState().getBlock().getName();
        stack.set(SHIFT_LOG.get(), journal.with(new ShiftLog.Entry(false, PlantClock.day(level), PlantClock.minute(level),
                p.getGameProfile().getName(), machine, values)));
        level.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6f, 1.2f);
        p.displayClientMessage(PaperworkText.msg(PaperworkText.LOG_WRITTEN, machine, values.size()), true);
    }

    /** Сдать смену: подпись под записями вахты. */
    public static void handOver(ServerPlayer p, ItemStack stack) {
        ShiftLog journal = stack.getOrDefault(SHIFT_LOG.get(), ShiftLog.EMPTY);
        int n = journal.currentShiftEntries();
        if (n == 0) {
            p.displayClientMessage(PaperworkText.msg(PaperworkText.LOG_NOTHING_TO_SIGN), true);
            return;
        }
        if (journal.full()) {
            p.displayClientMessage(PaperworkText.msg(PaperworkText.LOG_FULL).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        Level level = p.level();
        stack.set(SHIFT_LOG.get(), journal.with(new ShiftLog.Entry(true, PlantClock.day(level), PlantClock.minute(level),
                p.getGameProfile().getName(), Component.empty(), List.of())));
        level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.6f, 1.0f);
        p.displayClientMessage(PaperworkText.msg(PaperworkText.LOG_SIGNED, n), true);
    }

    // --- наряд-допуск ---

    /** Наряд по аппарату: бланк — выписать, открытый — допуск, Shift — закрыть; Shift бланком по запертому — закрыть по второму экземпляру. */
    public static void usePermit(ServerPlayer p, ItemStack stack, GridDeviceBlockEntity<?> be) {
        if (!(be.unit() instanceof SwitchUnit u)) {
            p.displayClientMessage(PaperworkText.msg(PaperworkText.PERMIT_NO_TWIN), true);
            return;
        }
        PermitRecord rec = stack.get(WORK_PERMIT.get());
        GlobalPos here = GlobalPos.of(p.level().dimension(), be.getBlockPos());
        if (rec == null) {
            if (p.isSecondaryUseActive() && u.openPermits() > 0) closeByDuplicate(p, stack, be, u, here);
            else issue(p, stack, be, u, here);
            return;
        }
        if (!rec.target().equals(here)) {
            p.displayClientMessage(PaperworkText.msg(PaperworkText.PERMIT_OTHER, rec.apparatus()), true);
            return;
        }
        WorkPermit permit = WorkPermit.restore(rec.number(), rec.stage());
        switch (rec.stage()) {
            case CLOSED -> p.displayClientMessage(PaperworkText.msg(PaperworkText.PERMIT_ALREADY_CLOSED), true);
            case PREPARING, ADMITTED -> {
                if (p.isSecondaryUseActive()) {
                    permit.close(u);
                    stack.set(WORK_PERMIT.get(), rec.closed(p.getGameProfile().getName()));
                    p.level().playSound(null, be.getBlockPos(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 0.7f, 1.0f);
                    p.displayClientMessage(PaperworkText.msg(PaperworkText.PERMIT_CLOSED, rec.number()), true);
                } else if (rec.stage() == WorkPermit.Stage.ADMITTED) {
                    p.displayClientMessage(PaperworkText.msg(PaperworkText.PERMIT_CLOSE_HINT), true);
                } else {
                    Set<WorkPermit.Measure> missing = permit.admit(u);
                    if (missing.isEmpty()) {
                        stack.set(WORK_PERMIT.get(), rec.admitted(p.getGameProfile().getName()));
                        p.displayClientMessage(PaperworkText.msg(PaperworkText.PERMIT_ADMITTED).withStyle(ChatFormatting.GREEN), true);
                    } else {
                        p.displayClientMessage(PaperworkText.msg(PaperworkText.PERMIT_UNMET, measures(missing)).withStyle(ChatFormatting.GOLD), true);
                    }
                }
            }
        }
    }

    private static void issue(ServerPlayer p, ItemStack stack, GridDeviceBlockEntity<?> be, SwitchUnit u, GlobalPos here) {
        if (u.kind() == SwitchUnit.Kind.FUSE) {
            p.displayClientMessage(PaperworkText.msg(PaperworkText.PERMIT_NOT_SWITCH), true);
            return;
        }
        if (!WorkPermit.canIssue(u)) {
            p.displayClientMessage(PaperworkText.msg(PaperworkText.PERMIT_NOT_OPEN).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        int number = Numbers.of(p.serverLevel()).next();
        WorkPermit.issue(u, number);
        Level level = p.level();
        ItemStack written = stack.split(1);
        written.set(WORK_PERMIT.get(), new PermitRecord(number, WorkPermit.Stage.PREPARING, here,
                be.getBlockState().getBlock().getName(), p.getGameProfile().getName(), PlantClock.day(level), PlantClock.minute(level),
                "", "", false));
        if (!p.addItem(written)) p.drop(written, false);
        level.playSound(null, be.getBlockPos(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 0.7f, 1.2f);
        p.displayClientMessage(PaperworkText.msg(PaperworkText.PERMIT_ISSUED, number), true);
    }

    /** Первый экземпляр утерян: наряд закрывают по второму — бланк становится закрытым нарядом, аппарат отпускается. */
    private static void closeByDuplicate(ServerPlayer p, ItemStack stack, GridDeviceBlockEntity<?> be, SwitchUnit u, GlobalPos here) {
        WorkPermit.restore(0, WorkPermit.Stage.PREPARING).close(u);
        Level level = p.level();
        String name = p.getGameProfile().getName();
        ItemStack copy = stack.split(1);
        copy.set(WORK_PERMIT.get(), new PermitRecord(Numbers.of(p.serverLevel()).next(), WorkPermit.Stage.CLOSED, here,
                be.getBlockState().getBlock().getName(), name, PlantClock.day(level), PlantClock.minute(level), "", name, true));
        if (!p.addItem(copy)) p.drop(copy, false);
        p.displayClientMessage(PaperworkText.msg(PaperworkText.PERMIT_DUPLICATE_CLOSED), true);
    }

    private static MutableComponent measures(Set<WorkPermit.Measure> missing) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (WorkPermit.Measure m : missing) {
            if (!first) out.append("; ");
            first = false;
            out.append(Component.translatable(switch (m) {
                case SWITCHED_OFF -> PaperworkText.MEASURE_SWITCHED_OFF;
                case POSTER -> PaperworkText.MEASURE_POSTER;
                case NO_VOLTAGE -> PaperworkText.MEASURE_NO_VOLTAGE;
                case EARTHED -> PaperworkText.MEASURE_EARTHED;
            }));
        }
        return out;
    }

    /** Журнал учёта нарядов: сквозная нумерация на мир. */
    public static final class Numbers extends SavedData {
        private int last;

        static Numbers of(ServerLevel level) {
            return level.getServer().overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(Numbers::new, Numbers::load, null), PromptCritical.MOD_ID + "_work_permits");
        }

        private static Numbers load(CompoundTag tag, HolderLookup.Provider registries) {
            Numbers n = new Numbers();
            n.last = tag.getInt("last");
            return n;
        }

        int next() {
            last++;
            setDirty();
            return last;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("last", last);
            return tag;
        }
    }

    /** Для подсказки и окна документа: строка стадии наряда. */
    public static Component stage(WorkPermit.Stage s) {
        return Component.translatable(switch (s) {
            case PREPARING -> PaperworkText.STAGE_PREPARING;
            case ADMITTED -> PaperworkText.STAGE_ADMITTED;
            case CLOSED -> PaperworkText.STAGE_CLOSED;
        });
    }

}
