package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnace;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnaceState;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnaceUnit;
import dev.denfry.promptcritical.sim.metallurgy.HotBlastStoveUnit;
import dev.denfry.promptcritical.sim.metallurgy.IronmakingLog;
import dev.denfry.promptcritical.sim.metallurgy.IronmakingSubsystem;
import dev.denfry.promptcritical.sim.metallurgy.TapResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Контроллер домны {@code blast_furnace_controller} (08 §8.5): касается кожуха снаружи, находит печь
 * ({@link BlastFurnaceShape}) и держит её двойник {@link BlastFurnaceUnit}. Обвязка — по близости
 * ({@value #LINK_RANGE} блоков от печи): воздуходувки, воздухонагреватели, пылеуловители.
 *
 * <p>Шихта — в засыпной аппарат ({@link BlastFurnaceTopBlockEntity}: воронки, контрапции) или в слоты щита. Выпуск:
 * бур открывает лётку ({@link CastHouseMachineBlockEntity}), чугун течёт по жёлобу {@code iron_runner} в ковш
 * ({@link LadleBlockEntity}) или на песок (чушки); шлак — в грануляцию у жёлоба. Пушка забивает лётку глиной.
 */
public final class BlastFurnaceControllerBlockEntity extends IronworksDeviceBlockEntity<BlastFurnaceUnit> implements PanelHost {
    public static final int LINK_RANGE = 12;
    private static final int VALIDATE_PERIOD = 40, LOOK_PERIOD = 20, RUNNER_MAX = 64, FILL_HOLD = 40;
    /** Выпуск — в реальном времени (08 §8.5.5): секунд на тик. */
    private static final double TAP_DT_REAL = 0.05;
    /** Прорыв горна: взрыв ≈ 10 кг ТНТ, радиус 5 м [BALANCE]. */
    private static final double BURNTHROUGH_J = 4e7, BURNTHROUGH_R = 5;
    private static final int SLOT_ORE = 0, SLOT_COKE = 1, SLOT_FLUX = 2;

    @Nullable
    private BlastFurnaceShape shape;
    private String problem = "";
    private final MachineInventory inv = new MachineInventory(new MachineInventory.Role[] {MachineInventory.Role.INPUT,
            MachineInventory.Role.INPUT, MachineInventory.Role.INPUT}, BlastFurnaceControllerBlockEntity::slotValid, this::setChanged);
    private List<BlockPos> blowers = List.of(), stoves = List.of(), catchers = List.of();
    private boolean autoCycle = true, blowingWarned, spillWarned;
    private int bleedTicks, fillTicks;
    /** Фурмы, отмеченные прогоревшими на блоках. */
    private final Set<BlockPos> burnt = new LinkedHashSet<>();
    private List<BlockPos> runner = List.of();
    private double pigKg;

    public BlastFurnaceControllerBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.BLAST_FURNACE_CONTROLLER.get(), pos, state, BlastFurnaceUnit.class);
    }

    private static boolean slotValid(int slot, ItemStack s) {
        return switch (slot) {
            case SLOT_ORE -> s.is(IronworksTags.FURNACE_ORE);
            case SLOT_COKE -> isColdCoke(s);
            case SLOT_FLUX -> s.is(IronworksTags.FLUX);
            default -> false;
        };
    }

    static boolean isColdCoke(ItemStack s) {
        IronworksComponents.CokeAssay a = s.get(IronworksComponents.COKE_ASSAY.get());
        return s.is(PcItems.COKE.get()) && (a == null || !a.hot());
    }

    @Nullable
    public BlastFurnaceShape shape() {
        return shape;
    }

    /** Контроллер печи, к которой относится клетка {@code near} (условие {@code which} — по форме). */
    @Nullable
    public static BlastFurnaceControllerBlockEntity find(Level level, BlockPos near, Predicate<BlastFurnaceShape> which) {
        AABB box = new AABB(near).inflate(8, BlastFurnaceShape.MAX_HEIGHT + 2, 8);
        for (BlastFurnaceControllerBlockEntity c : Ironworks.blockEntities(level, box, BlastFurnaceControllerBlockEntity.class))
            if (c.shape != null && which.test(c.shape)) return c;
        return null;
    }

    // --- тик ---

    @Override
    protected void serverTick(SimLevelBinding b, BlastFurnaceUnit u) {
        long now = level.getGameTime();
        if (now % VALIDATE_PERIOD == Math.floorMod(worldPosition.asLong(), VALIDATE_PERIOD)) validate(b, u);
        if (shape == null || u.furnace() == null) {
            setStateFlag(PcProperties.ALARM, false);
            return;
        }
        BlastFurnace f = u.furnace();
        feedHopper(u);
        double cold = 0;
        for (BlockPos p : blowers) if (level.getBlockEntity(p) instanceof BlastBlowerBlockEntity bl) cold += bl.coldNm3S();
        List<HotBlastStoveUnit> su = new ArrayList<>();
        for (BlockPos p : stoves) {
            if (level.getBlockEntity(p) instanceof HotBlastStoveBlockEntity s && s.unit() != null) su.add(s.unit());
        }
        u.couple(cold, su, autoCycle);
        topGas(u);
        tap(f);
        for (IronmakingLog.Entry e; (e = u.log().poll()) != null; ) event(b, u, e);
        if (now % LOOK_PERIOD == 0) looks(f);
        if (fillTicks > 0 && --fillTicks == 0) fillRunner(false);
        setStateFlag(PcProperties.ALARM, f.state() == BlastFurnaceState.CHILLED || f.state() == BlastFurnaceState.HANGING
                || f.state() == BlastFurnaceState.BURNTHROUGH || f.tuyereLeak());
    }

    private void validate(SimLevelBinding b, BlastFurnaceUnit u) {
        BlastFurnaceShape.Scan scan = BlastFurnaceShape.scan(level, worldPosition);
        BlastFurnaceShape s = scan.shape();
        if (s == null) {
            if (shape != null) fillRunner(false);
            shape = null;
            problem = scan.problem();
            return;
        }
        if (!s.equals(shape)) burnt.clear();
        shape = s;
        problem = "";
        u.form(b.world().subsystem(IronmakingSubsystem.class).config(), s.spec());
        AABB near = s.bounds().inflate(LINK_RANGE);
        blowers = positions(Ironworks.blockEntities(level, near, BlastBlowerBlockEntity.class));
        stoves = positions(Ironworks.blockEntities(level, near, HotBlastStoveBlockEntity.class));
        catchers = positions(Ironworks.blockEntities(level, near, DustCatcherBlockEntity.class));
        runner = runnerPath(s);
    }

    private static List<BlockPos> positions(List<? extends net.minecraft.world.level.block.entity.BlockEntity> list) {
        List<BlockPos> out = new ArrayList<>(list.size());
        for (var be : list) out.add(be.getBlockPos());
        return List.copyOf(out);
    }

    private void feedHopper(BlastFurnaceUnit u) {
        for (int slot = 0; slot < 3; slot++) {
            ItemStack s = inv.getStackInSlot(slot);
            if (!s.isEmpty() && charge(u, s)) s.shrink(1);
        }
    }

    /** Загрузить один предмет шихты в бункер колошника; {@code false} — не тот предмет или нет места. */
    boolean charge(BlastFurnaceUnit u, ItemStack s) {
        return charge(u, s, false);
    }

    /** Есть ли в бункере место под предмет. */
    boolean canCharge(BlastFurnaceUnit u, ItemStack s) {
        return charge(u, s, true);
    }

    private boolean charge(BlastFurnaceUnit u, ItemStack s, boolean simulate) {
        double kg = Ironworks.bulkKg();
        if (s.is(IronworksTags.FLUX)) {
            if (u.limeRoomKg() < kg) return false;
            if (!simulate) u.addLimestone(kg);
            return true;
        }
        if (isColdCoke(s)) {
            if (u.cokeRoomKg() < kg) return false;
            if (!simulate) u.addCoke(kg, s.getOrDefault(IronworksComponents.COKE_ASSAY.get(), IronworksComponents.CokeAssay.DEFAULT).toAssay());
            return true;
        }
        OreChem c = s.is(IronworksTags.FURNACE_ORE) ? OreChem.of(s) : null;
        if (c == null || u.oreRoomKg() < kg) return false;
        if (!simulate) u.addOre(kg, c.fe(), c.sio2(), c.cao());
        return true;
    }

    private void topGas(BlastFurnaceUnit u) {
        List<DustCatcherBlockEntity> dc = new ArrayList<>();
        double room = 0;
        for (BlockPos p : catchers) {
            if (level.getBlockEntity(p) instanceof DustCatcherBlockEntity d) {
                dc.add(d);
                room += d.outlet().room();
            }
        }
        double g = u.takeTopGas(room);
        for (DustCatcherBlockEntity d : dc) if (g > 0) g -= d.accept(g * d.outlet().room() / Math.max(room, 1e-9));
        double vent = u.ventExcessTopGas();
        if (vent > 0) bleedTicks = 40;
        if (bleedTicks > 0) {
            bleedTicks--;
            if (level instanceof ServerLevel sl && level.getGameTime() % 5 == 0) {
                Vec3 t = Vec3.atCenterOf(shape.top()).add(0, 2.5, 0);
                sl.sendParticles(ParticleTypes.FLAME, t.x, t.y, t.z, 4, 0.6, 0.3, 0.6, 0.02);
            }
        }
    }

    // --- выпуск ---

    private void tap(BlastFurnace f) {
        setTaphole(f.tapholeOpen());
        if (!f.tapholeOpen()) {
            blowingWarned = false;
            return;
        }
        TapResult r = f.tap(TAP_DT_REAL);
        if (r.blowing() && !blowingWarned) {
            blowingWarned = true;
            tell(IronworksText.msg("bf_blowing"));
        }
        if (r.ironKg() <= 0 && r.slagKg() <= 0) return;
        if (runner.isEmpty() || level.getGameTime() % 20 == 0) runner = runnerPath(shape);
        fillRunner(true);
        fillTicks = FILL_HOLD;
        deliverIron(r);
        deliverSlag(r.slagKg());
        if (level instanceof ServerLevel sl && level.getGameTime() % 4 == 0) {
            Vec3 at = Vec3.atCenterOf(shape.taphole().relative(shape.outward(shape.taphole())));
            sl.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 2, 0.2, 0.1, 0.2, 0.0);
        }
    }

    /** Жёлоб от лётки: связные {@code iron_runner} по горизонтали и на клетку вниз, по порядку от лётки. */
    private List<BlockPos> runnerPath(BlastFurnaceShape s) {
        BlockPos start = s.taphole().relative(s.outward(s.taphole()));
        List<BlockPos> out = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new LinkedHashSet<>();
        for (BlockPos p : new BlockPos[] {start, start.below()}) {
            if (isRunner(p)) {
                queue.add(p);
                seen.add(p);
            }
        }
        while (!queue.isEmpty() && out.size() < RUNNER_MAX) {
            BlockPos p = queue.poll();
            out.add(p);
            for (Direction d : Direction.Plane.HORIZONTAL)
                for (BlockPos n : new BlockPos[] {p.relative(d), p.relative(d).below()})
                    if (!seen.contains(n) && isRunner(n)) {
                        seen.add(n);
                        queue.add(n);
                    }
        }
        return List.copyOf(out);
    }

    private boolean isRunner(BlockPos p) {
        return level.isLoaded(p) && level.getBlockState(p).is(PcBlocks.IRON_RUNNER.get());
    }

    private void fillRunner(boolean filled) {
        for (BlockPos p : runner) {
            BlockState s = level.getBlockState(p);
            if (s.hasProperty(PcProperties.FILLED) && s.getValue(PcProperties.FILLED) != filled)
                level.setBlock(p, s.setValue(PcProperties.FILLED, filled), Block.UPDATE_CLIENTS);
        }
    }

    /** Ковши под жёлобом и рядом с ним (на клетку ниже), по порядку жёлоба. */
    private List<LadleBlockEntity> ladles() {
        List<LadleBlockEntity> out = new ArrayList<>();
        for (BlockPos r : runner) {
            for (BlockPos p : new BlockPos[] {r.below(), r.north().below(), r.south().below(), r.east().below(), r.west().below()}) {
                if (!level.isLoaded(p)) continue;
                if (level.getBlockEntity(Ironworks.controllerOf(level, p)) instanceof LadleBlockEntity l && !out.contains(l)) out.add(l);
            }
        }
        return out;
    }

    private void deliverIron(TapResult r) {
        double kg = r.ironKg();
        if (kg <= 0) return;
        for (LadleBlockEntity l : ladles()) {
            kg -= l.receive(kg, r.ironTempK(), r.carbonFrac(), r.siliconFrac(), r.sulfurFrac());
            if (kg <= 1e-9) return;
        }
        BlockPos sand = null;
        for (BlockPos p : runner) if (level.getBlockState(p.below()).is(BlockTags.SAND)) sand = p;
        if (sand != null) {
            pigKg += kg;
            int n = Ironworks.items(pigKg);
            if (n > 0) {
                Ironworks.spill(level, sand, new ItemStack(PcItems.PIG_IRON.get(), n));
                pigKg -= n * Ironworks.bulkKg();
            }
            return;
        }
        // некуда: чугун растекается по площадке
        BlockPos end = runner.isEmpty() ? shape.taphole().relative(shape.outward(shape.taphole())) : runner.get(runner.size() - 1);
        BlockPos fire = end.relative(runner.isEmpty() ? shape.outward(shape.taphole()) : Direction.UP);
        if (level.getBlockState(fire).isAir() && BaseFireBlock.canBePlacedAt(level, fire, Direction.UP))
            level.setBlock(fire, BaseFireBlock.getState(level, fire), Block.UPDATE_ALL);
        if (!spillWarned) {
            spillWarned = true;
            tell(IronworksText.msg("bf_spill"));
        }
    }

    private void deliverSlag(double kg) {
        if (kg <= 0) return;
        List<BlockPos> around = new ArrayList<>(runner);
        around.add(shape.taphole().relative(shape.outward(shape.taphole())));
        Set<SlagGranulatorBlockEntity> gs = new LinkedHashSet<>();
        for (BlockPos r : around)
            for (Direction d : Direction.values()) {
                BlockPos p = r.relative(d);
                if (level.isLoaded(p) && level.getBlockEntity(Ironworks.controllerOf(level, p)) instanceof SlagGranulatorBlockEntity g) gs.add(g);
            }
        for (SlagGranulatorBlockEntity g : gs) {
            kg -= g.accept(kg);
            if (kg <= 1e-9) return;
        }
    }

    // --- лётка и пушка (вызывают машины литейного двора) ---

    /** Бур вскрыл лётку. */
    public boolean drill() {
        BlastFurnaceUnit u = unit();
        if (u == null || u.furnace() == null || !u.furnace().drillTaphole()) return false;
        spillWarned = false;
        runner = runnerPath(shape);
        level.playSound(null, shape.taphole(), SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0f, 0.6f);
        return true;
    }

    /** Пушка забила лётку. */
    public boolean plug() {
        BlastFurnaceUnit u = unit();
        if (u == null || u.furnace() == null || !u.furnace().tapholeOpen()) return false;
        u.furnace().plugTaphole();
        level.playSound(null, shape.taphole(), SoundEvents.MUD_PLACE, SoundSource.BLOCKS, 1.0f, 0.6f);
        return true;
    }

    /** Заменить прогоревшую фурму запасной. */
    public boolean replaceTuyere(BlockPos tuyere) {
        BlastFurnaceUnit u = unit();
        if (u == null || u.furnace() == null || !burnt.remove(tuyere)) return false;
        if (burnt.isEmpty()) u.furnace().replaceTuyere();
        BlockState s = level.getBlockState(tuyere);
        if (s.hasProperty(PcProperties.BURNT)) level.setBlock(tuyere, s.setValue(PcProperties.BURNT, false), Block.UPDATE_ALL);
        setChanged();
        return true;
    }

    private void setTaphole(boolean open) {
        BlockState s = level.getBlockState(shape.taphole());
        if (s.hasProperty(BlockStateProperties.OPEN) && s.getValue(BlockStateProperties.OPEN) != open)
            level.setBlock(shape.taphole(), s.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_CLIENTS);
    }

    // --- события и вид ---

    private void event(SimLevelBinding b, BlastFurnaceUnit u, IronmakingLog.Entry e) {
        switch (e.kind()) {
            case FURNACE_STATE -> tell(IronworksText.msg("bf_state", state(BlastFurnaceState.values()[e.index()])));
            case SLIP -> {
                level.playSound(null, shape.top(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5f, 0.6f);
                bleedTicks = 80;
                tell(IronworksText.msg("bf_slip"));
            }
            case TUYERE_BURNT -> {
                for (BlockPos t : shape.tuyeres()) {
                    if (burnt.add(t)) {
                        BlockState s = level.getBlockState(t);
                        if (s.hasProperty(PcProperties.BURNT)) level.setBlock(t, s.setValue(PcProperties.BURNT, true), Block.UPDATE_ALL);
                        break;
                    }
                }
                tell(IronworksText.msg("bf_tuyere_burnt", e.index()));
            }
            case BURNTHROUGH -> {
                tell(IronworksText.msg("bf_burnthrough", IronworksText.num(e.value() / 1000)));
                BlockPos at = shape.taphole().relative(shape.outward(shape.taphole()));
                Ironworks.blast(b, at, BURNTHROUGH_J, BURNTHROUGH_R, 4);
            }
            default -> {
            }
        }
    }

    public static Component state(BlastFurnaceState s) {
        return IronworksText.msg("bf_state." + s.name().toLowerCase(Locale.ROOT));
    }

    private void looks(BlastFurnace f) {
        // фурмы, отмеченные прогоревшими, но заменённые в домене (перефутеровка), — снять отметку
        if (f.burntTuyeres() == 0 && !burnt.isEmpty()) {
            for (BlockPos t : burnt) {
                BlockState s = level.getBlockState(t);
                if (s.hasProperty(PcProperties.BURNT)) level.setBlock(t, s.setValue(PcProperties.BURNT, false), Block.UPDATE_ALL);
            }
            burnt.clear();
        }
    }

    private void tell(Component c) {
        Vec3 at = Vec3.atCenterOf(worldPosition);
        for (Player p : level.players()) if (p.distanceToSqr(at) < 48 * 48) p.displayClientMessage(c, false);
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.ALARM)) return;
        MachineFx fx = fx();
        if (fx.chance(1)) fx.sound(fx.at(8, 12, 0), SoundEvents.NOTE_BLOCK_BELL.value(), 0.4f, 0.5f);
    }

    // --- руки, склады, щит ---

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return slotValid(SLOT_ORE, stack) || slotValid(SLOT_COKE, stack) || slotValid(SLOT_FLUX, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        BlastFurnaceUnit u = unit();
        if (shape == null || u == null || u.furnace() == null) {
            String why = problem.isEmpty() ? BlastFurnaceShape.scan(level, worldPosition).problem() : problem;
            player.displayClientMessage(IronworksText.msg("bf_unformed", IronworksText.msg("problem." + (why.isEmpty() ? "forming" : why))), true);
            return InteractionResult.SUCCESS;
        }
        BlastFurnace f = u.furnace();
        player.displayClientMessage(IronworksText.msg("bf", state(f.state()), IronworksText.num(f.hearthTempK() - 273.15),
                IronworksText.num(f.ironKg() / 1000), IronworksText.num(u.blastNm3S() * 3600), IronworksText.num(u.blastK() - 273.15)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return IronworksPanels.BLAST_FURNACE;
    }

    @Override
    public boolean panelValid(Player player) {
        if (isRemoved()) return false;
        AABB box = shape == null ? new AABB(worldPosition) : shape.bounds().minmax(new AABB(worldPosition));
        return box.inflate(8).contains(player.position());
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        BlastFurnaceUnit u = unit();
        BlastFurnace f = u == null ? null : u.furnace();
        if (f == null || shape == null) {
            out[IronworksPanels.F_STATE] = -1;
            return;
        }
        out[IronworksPanels.F_STATE] = f.state().ordinal();
        out[IronworksPanels.F_HEARTH_C] = (int) Math.round(f.hearthTempK() - 273.15);
        out[IronworksPanels.F_BLAST_NM3H] = (int) Math.round(u.blastNm3S() * 3600);
        out[IronworksPanels.F_BLAST_C] = (int) Math.round(u.blastK() - 273.15);
        out[IronworksPanels.F_TOP_C] = (int) Math.round(f.topGasTempK() - 273.15);
        out[IronworksPanels.F_IRON_KG] = (int) Math.round(f.ironKg());
        out[IronworksPanels.F_SLAG_KG] = (int) Math.round(f.slagKg());
        out[IronworksPanels.F_RAFT_C] = (int) Math.round(f.raftK() - 273.15);
        out[IronworksPanels.F_HANG_PERMIL] = (int) Math.round(f.hangRisk() * 1000);
        out[IronworksPanels.F_ETA_PERMIL] = (int) Math.round(f.etaCO() * 1000);
        out[IronworksPanels.F_WEAR_PERMIL] = (int) Math.round(f.liningWear() * 1000);
        out[IronworksPanels.F_PROD_100] = (int) Math.round(f.productivityTM3Day() * 100);
        out[IronworksPanels.F_LAYERS] = f.layerCount();
        out[IronworksPanels.F_VU] = (int) Math.round(shape.usefulVolumeM3());
        out[IronworksPanels.F_ORE_KG] = (int) Math.round(u.hopperOreKg());
        out[IronworksPanels.F_COKE_KG] = (int) Math.round(u.hopperCokeKg());
        out[IronworksPanels.F_LIME_KG] = (int) Math.round(u.hopperLimestoneKg());
        out[IronworksPanels.F_ORE_LOAD100] = (int) Math.round(u.oreLoad() * 100);
        out[IronworksPanels.F_LIME100] = (int) Math.round(u.limestonePerOre() * 100);
        out[IronworksPanels.F_HOTSET_C] = (int) Math.round(u.hotBlastSetK() - 273.15);
        out[IronworksPanels.F_TUYERES] = shape.tuyeres().size();
        int flags = 0;
        if (f.tapholeOpen()) flags |= 1 << IronworksPanels.FF_TAP;
        if (u.blankCharges()) flags |= 1 << IronworksPanels.FF_BLANKS;
        if (autoCycle) flags |= 1 << IronworksPanels.FF_AUTO;
        if (bleedTicks > 0) flags |= 1 << IronworksPanels.FF_BLEED;
        if (f.tuyereLeak()) flags |= 1 << IronworksPanels.FF_TUYERE;
        if (u.cooling()) flags |= 1 << IronworksPanels.FF_COOLING;
        out[IronworksPanels.F_FLAGS] = flags;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        BlastFurnaceUnit u = unit();
        if (u == null) return;
        double load = u.oreLoad(), lime = u.limestonePerOre();
        boolean blanks = u.blankCharges();
        switch (id) {
            case IronworksPanels.BTN_LOAD_DOWN -> load -= 0.25;
            case IronworksPanels.BTN_LOAD_UP -> load += 0.25;
            case IronworksPanels.BTN_LIME_DOWN -> lime -= 0.05;
            case IronworksPanels.BTN_LIME_UP -> lime += 0.05;
            case IronworksPanels.BTN_BLANKS -> blanks = !blanks;
            case IronworksPanels.BTN_HOT_DOWN -> u.setHotBlast(u.hotBlastSetK() - 50);
            case IronworksPanels.BTN_HOT_UP -> u.setHotBlast(u.hotBlastSetK() + 50);
            case IronworksPanels.BTN_AUTO -> autoCycle = !autoCycle;
            case IronworksPanels.BTN_COOLING -> u.setCooling(!u.cooling());
            default -> {
                return;
            }
        }
        u.setBurden(load, Math.max(0, lime), blanks);
        setChanged();
    }

    public void setAutoCycle(boolean on) {
        autoCycle = on;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inv", inv.save(registries));
        tag.putBoolean("auto_cycle", autoCycle);
        tag.putDouble("pig_kg", pigKg);
        tag.putLongArray("burnt", burnt.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inv")) inv.load(registries, tag.getCompound("inv"));
        autoCycle = !tag.contains("auto_cycle") || tag.getBoolean("auto_cycle");
        pigKg = tag.getDouble("pig_kg");
        burnt.clear();
        for (long l : tag.getLongArray("burnt")) burnt.add(BlockPos.of(l));
    }
}
