package dev.denfry.promptcritical.mod.powerhouse;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.environment.TideService;
import dev.denfry.promptcritical.mod.environment.WaterBodies;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.hydraulics.TidalPlant;
import java.util.Arrays;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Приливная электростанция ({@link TidalPlant}): капсульный агрегат 3×2×3 в плотине — фасадом к морю, тылом к
 * бассейну, вал Create вверх (генератор ставится на вал, как у гидротурбины). Уровень моря — из {@link TideService} у
 * фасада; бассейн — зеркало воды за тылом, найденное заливкой по поверхности (не больше {@value #MAX_BASIN} блоков,
 * 1 блок — 1 м²); если заливка дошла до воды перед фасадом или упёрлась в предел — залив не перекрыт, станция стоит.
 * Агрегат подбирается под площадь бассейна и величину прилива ({@link TidalPlant.Spec#sizedFor}).
 *
 * <p>Время станции — игровое, как у прилива: тик — 3,6 с (сутки Minecraft — 24 ч). Ключом (Shift + ПКМ) и кнопками
 * щита меняется режим: затворы открыты, одностороннее на отливе, двустороннее.
 */
public class TidalUnitBlockEntity extends GeneratingKineticBlockEntity implements FoundryDevice, PanelHost {
    public static final int[] GEARS = {64, 128, 256};
    static final int MAX_BASIN = 40_000, RESCAN = 1200;
    static final double SECONDS_PER_TICK = 86_400.0 / 24_000;

    @Nullable
    private TidalPlant plant;
    private int area, gear = 1, ticks;
    private boolean open;
    private float capacityPerRpm, lastRpm;
    private double portLoadW;
    private TidalPlant.Mode mode = TidalPlant.Mode.EBB;
    @Nullable
    private CompoundTag savedState;

    public TidalUnitBlockEntity(BlockPos pos, BlockState state) {
        super(PowerhouseBlockEntities.TIDAL_UNIT.get(), pos, state);
    }

    @Nullable
    public TidalPlant plant() {
        return plant;
    }

    /** Площадь бассейна, м² (0 — не найден или залив не перекрыт). */
    public int basinArea() {
        return area;
    }

    private Direction facing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock pc ? pc.facing(s) : Direction.NORTH;
    }

    /** Точка у фасада, где меряется море. */
    BlockPos seaSide() {
        return worldPosition.relative(facing(), 2);
    }

    /** Агрегат вращается, пока автоматика держит его в генерации; мощность берёт нагрузка вала. */
    public float outputRpm() {
        return plant != null && generating(plant) ? GEARS[gear] : 0;
    }

    private static boolean generating(TidalPlant p) {
        return p.state() == TidalPlant.State.GENERATING_EBB || p.state() == TidalPlant.State.GENERATING_FLOOD;
    }

    @Override
    public float getGeneratedSpeed() {
        return level != null && level.isClientSide ? lastRpm : outputRpm();
    }

    @Override
    public float calculateAddedStressCapacity() {
        lastCapacityProvided = capacityPerRpm;
        return capacityPerRpm;
    }

    @Override
    public void tickDevice() {
        tick();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel sl)) return;
        ticks++;
        if (ticks % RESCAN == 1) rescan(sl);
        TidalPlant p = plant;
        if (p == null) {
            updateRotation(0);
            return;
        }
        p.setMode(mode);
        double sea = TideService.seaLevelM(sl, seaSide());
        // через агрегат идёт столько воды, сколько берёт нагрузка вала (генератор); без нагрузки бассейн держит воду
        p.step(SECONDS_PER_TICK, sea, portLoadW);
        updateRotation(generating(p) ? Math.min(p.availableW(), 1.1 * p.spec().ratedPowerW()) : 0);
        if (ticks % 5 == 0) {
            BlockState s = getBlockState();
            boolean running = generating(p);
            if (s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING) != running)
                level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, running), Block.UPDATE_CLIENTS);
        }
        if (ticks % 100 == 0) setChanged();
    }

    private void updateRotation(double capacityW) {
        float rpm = outputRpm();
        double wPerSu = ServerConfig.W_PER_SU.get();
        float cap = (float) (capacityW / wPerSu / GEARS[gear]);
        if (rpm != lastRpm) {
            lastRpm = rpm;
            capacityPerRpm = cap;
            updateGeneratedRotation();
        } else if (Math.abs(cap - capacityPerRpm) > 0.02 * Math.max(capacityPerRpm, 0.01f) && ticks % 20 == 0) {
            capacityPerRpm = cap;
            if (hasNetwork() && getOrCreateNetwork() != null) notifyStressCapacityChange(calculateAddedStressCapacity());
        }
        double load = 0;
        float speed = Math.abs(getSpeed());
        if (speed > 0 && !isOverStressed() && capacity > 0) {
            double own = lastCapacityProvided * speed;
            load = wPerSu * Math.min(stress, capacity) * own / capacity;
        }
        portLoadW = load;
    }

    /** Найти бассейн за тылом и подобрать агрегат; при прежней площади состояние сохраняется. */
    private void rescan(ServerLevel sl) {
        double m2 = TideService.m2AmplitudeM(sl, seaSide());
        int found = basin(sl);
        if (found <= 0 || m2 <= 0) {
            area = 0;
            plant = null;
            return;
        }
        if (plant != null && found == area) return;
        TidalPlant fresh = new TidalPlant(TidalPlant.Spec.sizedFor(found, 2 * m2));
        if (savedState != null) {
            fresh.load(new TreeStateReader(NbtStateTrees.fromNbt(savedState)));
            savedState = null;
        } else if (plant != null) {
            fresh.setBasinLevelM(plant.basinLevelM());
        } else {
            fresh.setBasinLevelM(TideService.seaLevelM(sl, seaSide()));
        }
        area = found;
        plant = fresh;
    }

    /** Зеркало бассейна за тылом; 0 — нет воды, залив открыт в море или слишком велик. */
    private int basin(ServerLevel sl) {
        BlockPos start = WaterBodies.surface(sl, worldPosition.relative(facing().getOpposite(), 2));
        return start == null ? 0 : WaterBodies.area(sl, start, MAX_BASIN, WaterBodies.surface(sl, seaSide()));
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
        if (stack.is(com.simibubi.create.AllItems.WRENCH.get())) {
            gear = (gear + 1) % GEARS.length;
            setChanged();
            player.displayClientMessage(PowerhouseText.msg("turbine.gear", GEARS[gear]), true);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Shift + ПКМ: следующий режим. */
    @Override
    public InteractionResult use(Player player) {
        setMode(TidalPlant.Mode.values()[(mode.ordinal() + 1) % TidalPlant.Mode.values().length], player);
        return InteractionResult.SUCCESS;
    }

    private void setMode(TidalPlant.Mode m, Player player) {
        mode = m;
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        player.displayClientMessage(PowerhouseText.msg("tidal.mode." + m.name().toLowerCase(java.util.Locale.ROOT)), true);
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return TidalPanels.UNIT;
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
        if (!(level instanceof ServerLevel sl)) return;
        double m2 = TideService.m2AmplitudeM(sl, seaSide());
        double span = Math.max(0.5, 1.6 * m2); // шкала стёкол — сизигийный размах с запасом
        double sea = TideService.seaLevelM(sl, seaSide());
        out[TidalPanels.SEA_PERMIL] = TidalPanels.glass(sea, span);
        out[TidalPanels.SEA_CM] = (int) Math.round(sea * 100);
        out[TidalPanels.M2_CM] = (int) Math.round(m2 * 100);
        out[TidalPanels.MOON] = TideService.moonPhase(sl);
        out[TidalPanels.AREA] = area;
        out[TidalPanels.MODE] = mode.ordinal();
        TidalPlant p = plant;
        if (p == null) return;
        out[TidalPanels.BASIN_PERMIL] = TidalPanels.glass(p.basinLevelM(), span);
        out[TidalPanels.BASIN_CM] = (int) Math.round(p.basinLevelM() * 100);
        out[TidalPanels.HEAD_CM] = (int) Math.round(Math.abs(p.basinLevelM() - sea) * 100);
        out[TidalPanels.KW10] = (int) Math.round(p.powerW() / 100);
        out[TidalPanels.RATED_KW] = (int) Math.round(p.spec().ratedPowerW() / 1e3);
        out[TidalPanels.KWH] = (int) Math.round(p.energyJ() / 3.6e6);
        out[TidalPanels.FLAGS] = 1 << TidalPanels.F_CLOSED | switch (p.state()) {
            case GENERATING_EBB -> 1 << TidalPanels.F_EBB;
            case GENERATING_FLOOD -> 1 << TidalPanels.F_FLOOD;
            case SLUICING -> 1 << TidalPanels.F_SLUICE;
            case HOLDING -> 1 << TidalPanels.F_HOLD;
        };
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id >= 0 && id < TidalPlant.Mode.values().length) setMode(TidalPlant.Mode.values()[id], player);
    }

    // --- сохранение ---

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) {
            tag.putInt("gear", gear);
            tag.putString("mode", mode.name());
            if (plant != null) {
                MutableStateTree tree = new MutableStateTree();
                plant.save(new TreeStateWriter(tree));
                tag.put("tidal", NbtStateTrees.toNbt(tree));
            }
        }
        tag.putFloat("rpm", lastRpm);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket) {
            if (tag.contains("gear")) gear = Math.floorMod(tag.getInt("gear"), GEARS.length);
            if (tag.contains("mode")) {
                try {
                    mode = TidalPlant.Mode.valueOf(tag.getString("mode"));
                } catch (IllegalArgumentException e) {
                    mode = TidalPlant.Mode.EBB;
                }
            }
            if (tag.contains("tidal")) savedState = tag.getCompound("tidal");
        }
        lastRpm = tag.getFloat("rpm");
    }

}
