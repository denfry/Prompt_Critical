package dev.denfry.promptcritical.mod.sound.client;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.foundry.CupolaBlockEntity;
import dev.denfry.promptcritical.mod.foundry.MachineToolBlockEntity;
import dev.denfry.promptcritical.mod.foundry.ProductionToolBlockEntity;
import dev.denfry.promptcritical.mod.foundry.SandMoldBlockEntity;
import dev.denfry.promptcritical.mod.foundry.ShakeoutGrateBlockEntity;
import dev.denfry.promptcritical.mod.grid.ElectricMotorBlockEntity;
import dev.denfry.promptcritical.mod.grid.TransformerBlockEntity;
import dev.denfry.promptcritical.mod.mine.DegasificationPumpBlockEntity;
import dev.denfry.promptcritical.mod.mine.LongwallShearerBlockEntity;
import dev.denfry.promptcritical.mod.mine.MainMineFanBlockEntity;
import dev.denfry.promptcritical.mod.mine.MineDrainagePumpBlockEntity;
import dev.denfry.promptcritical.mod.mine.MineHoistBlockEntity;
import dev.denfry.promptcritical.mod.sound.PcSfx;
import dev.denfry.promptcritical.sim.metallurgy.ProductionToolUnit;
import dev.denfry.promptcritical.sim.mining.Hoist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Звуковой фон цеха на клиенте: раз в {@link #PERIOD} тика обходит машины в ближних чанках и держит петлю работы у
 * каждой работающей — по тому, что клиент видит: свойство {@code running}/{@code lit} блока, обороты вала Create.
 * Громкость и высота — от оборотов, где они есть (насос на 32 об/мин гудит ниже и тише, чем на 64). Разовые звуки
 * (такт выхлопа, ход резца, удар стола, щелчок аппарата) играют сами машины.
 */
public final class MachineSoundscape {
    private static final int PERIOD = 4, CHUNKS = 2;
    /** Номинальные обороты вала машин на Create, об/мин. */
    private static final float NOMINAL_RPM = 64;
    private static final Map<Long, MachineLoopSound> PLAYING = new HashMap<>();

    /** Что звучит у машины сейчас: петля, громкость, высота; {@code null} — молчит. */
    private record Loop(PcSfx sfx, float volume, float pitch) {
    }

    private MachineSoundscape() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MachineSoundscape::tick);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        PLAYING.values().removeIf(MachineLoopSound::done);
        if (level == null || mc.player == null || mc.isPaused() || level.getGameTime() % PERIOD != 0) return;
        ChunkPos c = mc.player.chunkPosition();
        for (int dx = -CHUNKS; dx <= CHUNKS; dx++) {
            for (int dz = -CHUNKS; dz <= CHUNKS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(c.x + dx, c.z + dz, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    Loop l = loop(be);
                    if (l != null) keep(mc, be.getBlockPos(), l);
                }
            }
        }
    }

    private static void keep(Minecraft mc, BlockPos pos, Loop l) {
        long key = pos.asLong() * 31 + l.sfx().ordinal();
        MachineLoopSound s = PLAYING.get(key);
        if (s != null && !s.done()) {
            s.keep(l.volume(), l.pitch());
            return;
        }
        s = new MachineLoopSound(l.sfx(), pos, l.volume(), l.pitch());
        PLAYING.put(key, s);
        mc.getSoundManager().play(s);
    }

    private static boolean flag(BlockState s, net.minecraft.world.level.block.state.properties.BooleanProperty p) {
        return s.hasProperty(p) && s.getValue(p);
    }

    /** Доля номинальных оборотов вала Create (0 — стоит). */
    private static float shaft(BlockEntity be) {
        return be instanceof KineticBlockEntity k ? Math.abs(k.getSpeed()) / NOMINAL_RPM : 0;
    }

    @Nullable
    private static Loop loop(BlockEntity be) {
        BlockState s = be.getBlockState();
        boolean running = flag(s, PcProperties.RUNNING);
        float n = shaft(be);
        return switch (be) {
            case FireTubeBoilerBlockEntity b -> flag(s, BlockStateProperties.LIT) ? new Loop(PcSfx.BOILER_FIRE, 0.8f, 1) : null;
            case CupolaBlockEntity b -> flag(s, BlockStateProperties.LIT) ? new Loop(PcSfx.CUPOLA_BLAST, 0.9f, 0.8f + 0.2f * n) : null;
            case ElectricMotorBlockEntity m -> running || n > 0 ? new Loop(PcSfx.ELECTRIC_MOTOR, 0.5f, 1) : null;
            case TransformerBlockEntity t -> t.hum() > 0 ? new Loop(PcSfx.TRANSFORMER_HUM, 0.25f + 0.05f * t.hum(), 1) : null;
            case MainMineFanBlockEntity f -> running ? new Loop(PcSfx.MINE_FAN, 1.2f, 1) : null;
            case MineDrainagePumpBlockEntity p -> running ? new Loop(PcSfx.PUMP, 0.4f + 0.4f * n, 0.6f + 0.4f * n) : null;
            case DegasificationPumpBlockEntity p -> running ? new Loop(PcSfx.PUMP, 0.3f + 0.3f * n, 0.7f + 0.4f * n) : null;
            // лебёдка: громче и выше с ростом скорости каната до предельной Hoist.MAX_SPEED
            case MineHoistBlockEntity h -> Math.abs(h.speedMs()) > 0.05
                    ? new Loop(PcSfx.HOIST_WINCH, 0.5f + (float) (0.6 * Math.abs(h.speedMs()) / Hoist.MAX_SPEED),
                    0.7f + (float) (0.5 * Math.abs(h.speedMs()) / Hoist.MAX_SPEED)) : null;
            case LongwallShearerBlockEntity sh -> running ? new Loop(PcSfx.SHEARER_CUTTING, 1.0f, 1) : null;
            case MachineToolBlockEntity t -> running ? new Loop(PcSfx.LATHE, 0.5f, t.isDrillPress() ? 1.3f : 0.8f + 0.2f * n) : null;
            case ProductionToolBlockEntity t -> running && t.kind() != ProductionToolUnit.Kind.SHAPER
                    ? new Loop(t.kind() == ProductionToolUnit.Kind.MILLING ? PcSfx.MILLING : PcSfx.GRINDER, 0.5f, 0.8f + 0.2f * n)
                    : null;
            case ShakeoutGrateBlockEntity g -> running ? new Loop(PcSfx.SHAKEOUT_RATTLE, 0.6f, 0.8f + 0.2f * n) : null;
            case SandMoldBlockEntity m -> s.hasProperty(PcProperties.MOLD_STAGE) && s.getValue(PcProperties.MOLD_STAGE) == PcProperties.MoldStage.POURED
                    ? new Loop(PcSfx.METAL_POUR, 0.5f, 1) : null;
            // печь пиролиза: подовые горелки гудят, пока печь в работе или на выжиге кокса
            case dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity f -> flag(s, BlockStateProperties.LIT) ? new Loop(PcSfx.BOILER_FIRE, 1.0f, 0.8f) : null;
            // гиперкомпрессор ПЭВД: тяжёлый поршневой стук, чаще с оборотами вала
            case dev.denfry.promptcritical.mod.chem.LdpeReactorBlockEntity r -> running || n > 0 ? new Loop(PcSfx.PUMP, 0.5f + 0.4f * Math.min(1, n), 0.5f + 0.3f * Math.min(1, n)) : null;
            // гидроочистка: сырьевой насос и циркуляционный компрессор водорода за стеной
            case dev.denfry.promptcritical.mod.refinery.HydrotreaterBlockEntity h -> running ? new Loop(PcSfx.PUMP, 0.3f, 1.1f) : null;
            // воздушный компрессор агрегата азотной кислоты: гул улитки, выше с оборотами
            case dev.denfry.promptcritical.mod.nitrogen.NitricAcidBlockEntity a -> running || n > 0 ? new Loop(PcSfx.MINE_FAN, 0.6f, 0.8f + 0.4f * Math.min(1, n)) : null;
            // электролизёр: гудит выпрямленный ток в шинах и выпрямитель
            case dev.denfry.promptcritical.mod.chlor.CellRoomBlockEntity c -> running ? new Loop(PcSfx.TRANSFORMER_HUM, 0.3f, 1) : null;
            default -> null;
        };
    }
}
