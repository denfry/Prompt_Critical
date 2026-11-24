package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.safety.PpeItem;
import dev.denfry.promptcritical.sim.radiation.DoseLedger;
import dev.denfry.promptcritical.sim.radiation.RadRespirator;
import dev.denfry.promptcritical.sim.radiation.RadZone;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Блоки зоны и санпропускника 04b §4b.7, §4b.12: контроллер зоны, стационарный блок детектирования, пост подачи
 * воздуха пневмокостюма, считыватель ТЛД, шкафы «чистый» и «грязный», дверь зоны с проверкой на входе и выходе.
 */
public final class SanitaryBlocks {
    private SanitaryBlocks() {
    }

    // ------------------------------------------------------------------ настенные приборы

    /** Прибор на стене: фасад на север, плита на юге (как у модели), ставится на кликнутую грань стены. */
    public static class WallDevice extends PcBlock {
        private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

        protected WallDevice(Properties props, Spec spec, double x0, double y0, double z0, double x1, double y1, double z1) {
            super(props, spec);
            for (Direction d : Direction.Plane.HORIZONTAL) shapes.put(d, rotated(d, x0, y0, z0, x1, y1, z1));
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            Direction face = ctx.getClickedFace();
            Direction f = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
            return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return shapes.get(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }
    }

    /** Коробка модели (фасад на север) повёрнутая к {@code facing}. */
    static VoxelShape rotated(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        double[] a = rot(facing, x0 - 8, z0 - 8), b = rot(facing, x1 - 8, z1 - 8);
        return Shapes.box((Math.min(a[0], b[0]) + 8) / 16, y0 / 16, (Math.min(a[1], b[1]) + 8) / 16,
                (Math.max(a[0], b[0]) + 8) / 16, y1 / 16, (Math.max(a[1], b[1]) + 8) / 16);
    }

    private static double[] rot(Direction facing, double x, double z) {
        return switch (facing) {
            case EAST -> new double[] {-z, x};
            case SOUTH -> new double[] {-x, -z};
            case WEST -> new double[] {z, -x};
            default -> new double[] {x, z};
        };
    }

    /** Стационарный блок детектирования γ (ионизационная камера, 0,1 мкЗв/ч – 10 Зв/ч): по нему меряет контроллер зоны. */
    public static final class DoseRateInstrument extends WallDevice {
        private DoseRateInstrument(Properties props, Spec spec) {
            super(props, spec, 2, 0, 10.5, 14, 15, 16);
        }

        public static DoseRateInstrument create(Properties props) {
            Spec spec = Spec.facing();
            return construct(spec, () -> new DoseRateInstrument(props, spec));
        }

        /** Показание у блока: поле источников, погружение в облако и природный фон, Зв/ч. */
        public static double readingSvH(ServerLevel level, BlockPos pos) {
            var c = pos.getCenter();
            return RadiationService.rateSvH(level, c) + RadiationService.cloudParts(level, c, RadRespirator.NONE)[0]
                    + RadiationService.BACKGROUND_USV_H * 1e-6;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel sl)
                player.displayClientMessage(RadGearText.msg("instrument", RadGearText.rate(readingSvH(sl, pos))).withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.SUCCESS;
        }
    }

    /** Пост подачи воздуха: ПКМ в куртке пневмокостюма пристёгивает шланг (ещё раз — отстёгивает). */
    public static final class AirSupplyPort extends WallDevice {
        private AirSupplyPort(Properties props, Spec spec) {
            super(props, spec, 3, 3, 11, 13, 16, 16);
        }

        public static AirSupplyPort create(Properties props) {
            Spec spec = Spec.facing();
            return construct(spec, () -> new AirSupplyPort(props, spec));
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            ItemStack jacket = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!PpeItem.is(jacket, PpeItem.Kind.PNEUMOSUIT_JACKET)) {
                player.displayClientMessage(RadGearText.msg("hose_no_suit").withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.SUCCESS;
            }
            GlobalPos here = GlobalPos.of(level.dimension(), pos), was = jacket.get(RadGear.AIR_HOSE.get());
            if (here.equals(was)) {
                jacket.remove(RadGear.AIR_HOSE.get());
                player.displayClientMessage(RadGearText.msg("hose_off"), true);
            } else {
                jacket.set(RadGear.AIR_HOSE.get(), here);
                player.displayClientMessage(RadGearText.msg("hose_on", (int) RadRespirator.HOSE_M).withStyle(ChatFormatting.GREEN), true);
            }
            level.playSound(null, pos, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }
    }

    /** Контроллер зоны: ПКМ — состояние, ПКМ с Shift — следующий класс помещения. */
    public static final class ZoneController extends WallDevice implements EntityBlock {
        private ZoneController(Properties props, Spec spec) {
            super(props, spec, 2, 0, 12.9, 14, 14, 16);
        }

        public static ZoneController create(Properties props) {
            Spec spec = Spec.facing();
            return construct(spec, () -> new ZoneController(props, spec));
        }

        @Override
        @Nullable
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new ZoneControllerBlockEntity(pos, state);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.isClientSide || !(level.getBlockEntity(pos) instanceof ZoneControllerBlockEntity z)) return InteractionResult.SUCCESS;
            if (player.isShiftKeyDown()) z.cycleZone();
            player.displayClientMessage(RadGearText.zoneLine(z), true);
            return InteractionResult.SUCCESS;
        }
    }

    // ------------------------------------------------------------------ санпропускник

    /** Считыватель ТЛД: ПКМ с дозиметром в руке — снять показание в официальный учёт и обнулить дозиметр. */
    public static final class TldReader extends PcBlock {
        private TldReader(Properties props, Spec spec) {
            super(props, spec);
        }

        public static TldReader create(Properties props) {
            Spec spec = Spec.facing();
            return construct(spec, () -> new TldReader(props, spec));
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            ItemStack badge = player.getMainHandItem().is(PcItems.TLD_BADGE.get()) ? player.getMainHandItem() : player.getOffhandItem();
            if (!badge.is(PcItems.TLD_BADGE.get())) {
                sp.displayClientMessage(RadGearText.officialLine(sp, 0, false), true);
                return InteractionResult.SUCCESS;
            }
            RadGear.Tld t = badge.getOrDefault(RadGear.TLD.get(), RadGear.Tld.FRESH);
            RadiationService.recordOfficial(sp, t.doseSv());
            badge.set(RadGear.TLD.get(), new RadGear.Tld(0, RadiationService.day(sp)));
            level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.6f, 1.6f);
            sp.displayClientMessage(RadGearText.officialLine(sp, t.doseSv(), true), false);
            return InteractionResult.SUCCESS;
        }
    }

    /** Шкаф санпропускника: «чистый» меняет одежду игрока с его ячейкой, «грязный» принимает зонные вещи. */
    public static final class Locker extends PcBlock implements EntityBlock {
        private final boolean dirty;

        private Locker(Properties props, Spec spec, boolean dirty) {
            super(props, spec);
            this.dirty = dirty;
        }

        public static Locker clean(Properties props) {
            Spec spec = Spec.facing();
            return construct(spec, () -> new Locker(props, spec, false));
        }

        public static Locker dirty(Properties props) {
            Spec spec = Spec.facing();
            return construct(spec, () -> new Locker(props, spec, true));
        }

        @Override
        @Nullable
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new LockerBlockEntity(pos, state);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.isClientSide || !(level.getBlockEntity(pos) instanceof LockerBlockEntity l)) return InteractionResult.SUCCESS;
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f);
            if (!dirty) {
                l.swapOutfit(player);
                player.displayClientMessage(RadGearText.msg("locker_swapped"), true);
            } else if (player.isShiftKeyDown()) {
                ItemStack back = l.takeLast();
                if (!back.isEmpty()) player.setItemInHand(InteractionHand.MAIN_HAND, back);
                player.displayClientMessage(RadGearText.msg("locker_dirty_left", l.dirtyCount()), true);
            } else {
                int n = l.depositZoneClothing(player);
                player.displayClientMessage(RadGearText.msg(n > 0 ? "locker_dirty_in" : "locker_dirty_none", n, l.dirtyCount()), true);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LockerBlockEntity l)
                for (ItemStack s : l.contents()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    // ------------------------------------------------------------------ дверь зоны

    /**
     * Дверь зоны контролируемого доступа (04b §4b.12.2). На входе в зону — ЭПД при себе и годовая доза ниже предела; на
     * выходе — руки и подошвы чище уровня монитора. С {@code radiation.enforce_zones} дверь не откроется, без — откроется
     * с предупреждением. Сторону определяет контроллер зоны: какая из клеток перед и за дверью лежит в его помещении.
     */
    public static final class ZoneAccessDoor extends DoorBlock {
        public ZoneAccessDoor(Properties props) {
            super(BlockSetType.IRON, props);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            boolean opening = !state.getValue(OPEN);
            if (opening && player instanceof ServerPlayer sp) {
                BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
                Check c = check((ServerLevel) level, lower, state.getValue(FACING), sp);
                if (!c.verdict().ok()) {
                    boolean enforce = ServerConfig.RADIATION_ENFORCE_ZONES.get();
                    sp.displayClientMessage(RadGearText.doorLine(c.verdict(), c.zone(), enforce).withStyle(enforce ? ChatFormatting.RED : ChatFormatting.GOLD), true);
                    if (enforce) {
                        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0f, 0.5f);
                        return InteractionResult.SUCCESS;
                    }
                }
            }
            setOpen(player, level, state, pos, opening);
            return InteractionResult.SUCCESS;
        }
    }

    /** Итог проверки двери: решение и класс зоны, о которой шла речь. */
    public record Check(RadZone.Verdict verdict, RadZone zone) {
    }

    /** Проверка игрока у двери с нижней половиной {@code lower}, смотрящей в {@code facing}. */
    public static Check check(ServerLevel level, BlockPos lower, Direction facing, ServerPlayer p) {
        BlockPos a = lower.relative(facing), b = lower.relative(facing.getOpposite());
        boolean onA = p.position().distanceToSqr(a.getCenter()) <= p.position().distanceToSqr(b.getCenter());
        BlockPos here = onA ? a : b, there = onA ? b : a;
        ZoneControllerBlockEntity dest = ZoneControllerBlockEntity.zoneAt(level, there), from = ZoneControllerBlockEntity.zoneAt(level, here);
        if (dest != null && dest.zone().controlled()) {
            double limit = ServerConfig.RADIATION_WORKER_ANNUAL_SV.get();
            return new Check(dest.zone().entry(RadGearService.carries(p, PcItems.PERSONAL_DOSIMETER_EPD.get()),
                    RadiationService.annualSv(p), limit), dest.zone());
        }
        if (from != null && from.zone().controlled())
            return new Check(RadZone.exit(ContaminationService.hands(p).total(), ContaminationService.shoes(p).total(),
                    RadZone.HAND_FOOT_LIMIT_BQ_CM2), from.zone());
        return new Check(RadZone.Verdict.OK, RadZone.FREE);
    }

    /** Предел, который показывают тексты, если в конфигурации его нет (вне сервера). */
    static double limitSv() {
        try {
            return ServerConfig.RADIATION_WORKER_ANNUAL_SV.get();
        } catch (IllegalStateException e) {
            return DoseLedger.WORKER_ANNUAL_SV;
        }
    }
}
